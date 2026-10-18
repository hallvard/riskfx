package no.hal.risk.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import no.hal.risk.FortifyMode;
import no.hal.risk.RiskConfig;
import no.hal.risk.Rules;
import no.hal.risk.ai.ActionSpace;
import no.hal.risk.ai.Bots;
import no.hal.risk.ai.JavaPolicyNetwork;
import no.hal.risk.ai.ObservationEncoder;
import no.hal.risk.env.RiskEnv;

/**
 * Plays the games for the learner, which is a program in Python. It runs as a process of its own, which reads commands
 * from its standard input, one on each line, and answers each with one line, {@code OK} and some JSON, or {@code ERROR} and a message.
 * It plays with the network in a file with weights, which is read for each command, so the learner can write new weights at any time.
 * <pre>
 * INFO out=file.npz                     the map: which territories are adjacent, and the sizes of the network
 * ROLLOUT players=4 games=8 seed=1 weights=w.weights out=rollout.npz [reward=default] [botSeats=0.25] [lambda=0.95] [parallel=8]
 *                                       [opponents=greedy,cautious]
 *                                       play with the network, and write the decisions, see {@link RolloutWriter}
 * EVAL players=4 games=100 weights=w.weights [boardSeed=987654321] [reward=default] [parallel=16] [opponents=greedy]
 *                                       how often the network beats greedy bots
 * IMITATION players=2 games=300 seed=1 out=imitation.npz [reward=default]
 *                                       the decisions of greedy bots, to learn from, see {@link RolloutWriter#writeImitation}
 * QUIT
 * </pre>
 * The games are in the rules of the AI: armies are moved to adjacent territories only.
 */
public class Actor {

  private static final ObjectMapper JSON = new ObjectMapper();

  public static void main(String[] args) throws IOException {
    // standard output is for the answers, so anything else that is written, is written to standard error
    var answers = System.out;
    System.setOut(System.err);
    new Actor().run(System.in, answers);
  }

  public void run(InputStream in, PrintStream out) throws IOException {
    var reader = new BufferedReader(new InputStreamReader(in));
    String line;
    while ((line = reader.readLine()) != null) {
      line = line.strip();
      if (line.isEmpty()) {
        continue;
      }
      if (line.equals("QUIT")) {
        out.println("OK {}");
        out.flush();
        return;
      }
      try {
        out.println("OK " + JSON.writeValueAsString(handle(line)));
      } catch (Exception | StackOverflowError e) {
        out.println("ERROR " + String.valueOf(e).replace('\n', ' '));
      }
      out.flush();
    }
  }

  private static class Arguments {
    private final Map<String, String> values = new LinkedHashMap<>();

    Arguments(String[] tokens) {
      for (int i = 1; i < tokens.length; i++) {
        var parts = tokens[i].split("=", 2);
        if (parts.length != 2) {
          throw new IllegalArgumentException("Not key=value: " + tokens[i]);
        }
        values.put(parts[0], parts[1]);
      }
    }

    String string(String key) {
      var value = values.get(key);
      if (value == null) {
        throw new IllegalArgumentException("Missing " + key);
      }
      return value;
    }

    String string(String key, String defaultValue) {
      return values.getOrDefault(key, defaultValue);
    }

    int integer(String key) {
      return Integer.parseInt(string(key));
    }

    int integer(String key, int defaultValue) {
      return values.containsKey(key) ? integer(key) : defaultValue;
    }

    long longInteger(String key, long defaultValue) {
      return values.containsKey(key) ? Long.parseLong(string(key)) : defaultValue;
    }

    double real(String key, double defaultValue) {
      return values.containsKey(key) ? Double.parseDouble(string(key)) : defaultValue;
    }
  }

  private static RiskConfig riskConfig(int players) {
    return new RiskConfig(players).withFortifyMode(FortifyMode.ADJACENT);
  }

  /** With {@code bonus=1}, the game has the conquest and the elimination bonus. */
  private static RiskConfig riskConfig(int players, Arguments arguments) {
    return riskConfig(players).withRules(new Rules(false, arguments.integer("bonus", 0) != 0, arguments.integer("bonus", 0) != 0));
  }

  private static boolean[][] borders() {
    return new ObservationEncoder(new RiskEnv(riskConfig(2), 10)).adjacency();
  }

  private static JavaPolicyNetwork loadNetwork(String file) throws IOException {
    try (var in = Files.newInputStream(Path.of(file))) {
      return JavaPolicyNetwork.load(in, borders());
    }
  }

  /** NaN can't be written in JSON. */
  private static Double number(double value) {
    return Double.isNaN(value) ? null : value;
  }

  Map<String, Object> handle(String line) throws IOException {
    var tokens = line.split("\\s+");
    var arguments = new Arguments(tokens);
    long started = System.nanoTime();
    var result = new LinkedHashMap<String, Object>();
    switch (tokens[0]) {
      case "INFO" -> info(arguments, result);
      case "ROLLOUT" -> rollout(arguments, result);
      case "EVAL" -> evaluate(arguments, result);
      case "IMITATION" -> imitation(arguments, result);
      default -> throw new IllegalArgumentException("Unknown command " + tokens[0]);
    }
    result.put("seconds", (System.nanoTime() - started) / 1e9);
    return result;
  }

  private void info(Arguments arguments, Map<String, Object> result) throws IOException {
    var env = new RiskEnv(riskConfig(2), 10);
    var encoder = new ObservationEncoder(env);
    var adjacency = encoder.adjacency();
    int n = adjacency.length;
    var cells = new byte[n * n];
    for (int i = 0; i < n; i++) {
      for (int j = 0; j < n; j++) {
        cells[i * n + j] = (byte) (adjacency[i][j] ? 1 : 0);
      }
    }
    int features = encoder.territoryFeatureNames().size();
    int globals = encoder.globalFeatureNames().size();
    int actions = new ActionSpace(n).size();
    try (var npz = new NpzWriter(Files.newOutputStream(Path.of(arguments.string("out"))))) {
      npz.bytes("adjacency", cells, n, n);
      npz.ints("dims", new int[] {n, features, globals, actions}, 4);
    }
    result.put("numTerritories", n);
    result.put("nodeFeatures", features);
    result.put("globalFeatures", globals);
    result.put("actions", actions);
  }

  private void rollout(Arguments arguments, Map<String, Object> result) throws IOException {
    int players = arguments.integer("players");
    int games = arguments.integer("games");
    var network = loadNetwork(arguments.string("weights"));
    var reward = Reward.named(arguments.string("reward", "default"));
    var config = new SelfPlay.Config(riskConfig(players, arguments), RiskEnv.defaultMaxTurns(players), arguments.integer("parallel", games),
        arguments.real("botSeats", 0.25), arguments.real("lambda", 0.95), arguments.longInteger("seed", 1));
    var rollout = new SelfPlay(network, reward, config).opponents(Bots.byNames(arguments.string("opponents", "greedy"))).collect(games);
    var weights = network.config();
    RolloutWriter.write(Path.of(arguments.string("out")), rollout, network.numTerritories(), weights.nodeFeatures(), weights.globalFeatures());
    result.put("games", rollout.games());
    result.put("steps", rollout.size());
    result.put("truncated", rollout.truncatedGames());
    result.put("averageTurns", rollout.averageTurns());
    result.put("winRateAgainstBots", number(rollout.winRateAgainstBots()));
    result.put("eliminationsPerGame", rollout.eliminationsPerGame());
  }

  private void evaluate(Arguments arguments, Map<String, Object> result) throws IOException {
    int players = arguments.integer("players");
    int games = arguments.integer("games");
    var network = loadNetwork(arguments.string("weights"));
    var reward = Reward.named(arguments.string("reward", "default"));
    var config = new SelfPlay.Config(riskConfig(players, arguments), RiskEnv.defaultMaxTurns(players), arguments.integer("parallel", Math.min(games, 16)),
        0, 0.95, 1);
    var evaluation = new SelfPlay(network, reward, config).opponents(Bots.byNames(arguments.string("opponents", "greedy")))
        .evaluate(games, arguments.longInteger("boardSeed", 987_654_321L));
    result.put("games", evaluation.games());
    result.put("wins", evaluation.wins());
    result.put("winRate", evaluation.winRate());
    result.put("truncated", evaluation.truncated());
    result.put("averageTurns", evaluation.averageTurns());
    result.put("eliminationsPerGame", evaluation.eliminationsPerGame());
  }

  private void imitation(Arguments arguments, Map<String, Object> result) throws IOException {
    int players = arguments.integer("players");
    var reward = Reward.named(arguments.string("reward", "default"));
    var samples = Imitation.collect(riskConfig(players), arguments.integer("games"), arguments.longInteger("seed", 1), reward);
    var encoder = new ObservationEncoder(new RiskEnv(riskConfig(players), 10));
    int n = new RiskEnv(riskConfig(players), 10).territories().size();
    RolloutWriter.writeImitation(Path.of(arguments.string("out")), samples, n, encoder.territoryFeatureNames().size(), encoder.globalFeatureNames().size());
    result.put("samples", samples.size());
  }
}
