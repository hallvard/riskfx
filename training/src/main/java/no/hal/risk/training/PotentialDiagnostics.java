package no.hal.risk.training;

import no.hal.risk.ai.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.Phase;
import no.hal.risk.env.RiskEnv;

/**
 * Measures how well a board evaluation predicts who wins, to judge candidates for the shaping potential.
 * At the start of each turn in games between greedy bots, the potential of the player to move is recorded.
 * Afterwards each sample is labeled with whether that player won, and we compute the AUC:
 * the probability that a random winner sample has a higher potential than a random loser sample (0.5 is no information).
 */
public class PotentialDiagnostics {

  /** The probability that a random positive sample has a higher value than a random negative one, ties counting half. */
  public static double auc(double[] values, boolean[] positive) {
    int n = values.length;
    var order = new Integer[n];
    for (int i = 0; i < n; i++) {
      order[i] = i;
    }
    java.util.Arrays.sort(order, Comparator.comparingDouble(i -> values[i]));
    double rankSumPositive = 0;
    long positives = 0;
    int i = 0;
    while (i < n) {
      int j = i;
      while (j + 1 < n && values[order[j + 1]] == values[order[i]]) {
        j++;
      }
      double averageRank = (i + j) / 2.0 + 1;
      for (int k = i; k <= j; k++) {
        if (positive[order[k]]) {
          rankSumPositive += averageRank;
          positives++;
        }
      }
      i = j + 1;
    }
    long negatives = n - positives;
    if (positives == 0 || negatives == 0) {
      return Double.NaN;
    }
    return (rankSumPositive - positives * (positives + 1) / 2.0) / ((double) positives * negatives);
  }

  private record Sample(double[] potentials, int round, boolean won) {
  }

  private static List<Sample> collect(RiskConfig config, List<BoardEvaluation> evaluations, int games, long seed) {
    int players = config.playerCount();
    var env = new RiskEnv(config);
    var bot = new GreedyBot();
    var samples = new ArrayList<Sample>();
    for (int game = 0; game < games; game++) {
      env.reset(seed + game);
      var gameSamples = new ArrayList<Sample>();
      var gamePlayers = new ArrayList<Integer>();
      int lastTurns = -1;
      while (! env.isDone()) {
        if (env.phase() == Phase.REINFORCING && env.turns() != lastTurns) {
          lastTurns = env.turns();
          int player = env.playerIndex(env.currentPlayer());
          var potentials = new double[evaluations.size()];
          for (int e = 0; e < potentials.length; e++) {
            potentials[e] = evaluations.get(e).potential(env, player);
          }
          gameSamples.add(new Sample(potentials, env.turns() / players, false));
          gamePlayers.add(player);
        }
        env.step(bot.chooseAction(env, env.legalActions()));
      }
      if (env.winner() == null) {
        continue; // no outcome to compare with
      }
      int winner = env.playerIndex(env.winner());
      for (int s = 0; s < gameSamples.size(); s++) {
        var sample = gameSamples.get(s);
        samples.add(new Sample(sample.potentials(), sample.round(), gamePlayers.get(s) == winner));
      }
    }
    return samples;
  }

  public static Map<String, BoardEvaluation> candidates() {
    var candidates = new LinkedHashMap<String, BoardEvaluation>();
    candidates.put("territories", new BoardEvaluation(BoardEvaluation.Weights.only(1, 0, 0, 0)));
    candidates.put("armies", new BoardEvaluation(BoardEvaluation.Weights.only(0, 1, 0, 0)));
    candidates.put("continents", new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 1, 0)));
    candidates.put("-exposure", new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 0, 1)));
    candidates.put("terr+armies", new BoardEvaluation(BoardEvaluation.Weights.only(1, 1, 0, 0)));
    candidates.put("terr+arm+cont", new BoardEvaluation(BoardEvaluation.Weights.only(1, 1, 1, 0)));
    candidates.put("advantage", new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 0, 1, 0, 0)));
    candidates.put("adv capped", new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 0, 0, 1, 0)));
    candidates.put("adv count", new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 0, 0, 0, 1)));
    candidates.put("tac+adv", new BoardEvaluation(BoardEvaluation.Weights.only(1, 1, 1, 1, 0, 0)));
    candidates.put("tac+capped", new BoardEvaluation(BoardEvaluation.Weights.only(1, 1, 1, 0, 1, 0)));
    candidates.put("tac+count", new BoardEvaluation(BoardEvaluation.Weights.only(1, 1, 1, 0, 0, 1)));
    candidates.put("tac+cap+cnt", new BoardEvaluation(BoardEvaluation.Weights.only(1, 1, 1, 0, 1, 1)));
    candidates.put("default", new BoardEvaluation());
    return candidates;
  }

  public static void main(String[] args) {
    int games = args.length > 0 ? Integer.parseInt(args[0]) : 200;
    var candidates = candidates();
    var evaluations = List.copyOf(candidates.values());
    int[][] stages = {{0, 3}, {3, 10}, {10, Integer.MAX_VALUE}};
    String[] stageNames = {"rounds 0-2", "rounds 3-9", "rounds 10+"};
    for (int players : new int[] {2, 4}) {
      var samples = collect(new RiskConfig(players), evaluations, games, 5000);
      System.out.println("== %d players, %d greedy games, %d turn-start samples".formatted(players, games, samples.size()));
      System.out.println("  %-14s %10s %10s %10s %10s".formatted("potential", "all", stageNames[0], stageNames[1], stageNames[2]));
      int e = 0;
      for (var name : candidates.keySet()) {
        var line = new StringBuilder("  %-14s".formatted(name));
        for (int stage = -1; stage < stages.length; stage++) {
          int from = stage < 0 ? 0 : stages[stage][0];
          int to = stage < 0 ? Integer.MAX_VALUE : stages[stage][1];
          int index = e;
          var selected = samples.stream().filter(sample -> sample.round() >= from && sample.round() < to).toList();
          double[] values = selected.stream().mapToDouble(sample -> sample.potentials()[index]).toArray();
          boolean[] won = new boolean[selected.size()];
          for (int s = 0; s < won.length; s++) {
            won[s] = selected.get(s).won();
          }
          line.append(" %10.3f".formatted(auc(values, won)));
        }
        System.out.println(line);
        e++;
      }
    }
  }
}
