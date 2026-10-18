package no.hal.risk.training;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The commands of the actor, as the learner gives them, and the files it writes. */
public class ActorTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir
  Path directory;
  private Path weights;

  @BeforeEach
  public void setUp() throws IOException {
    weights = directory.resolve("w.weights");
    try (var out = Files.newOutputStream(weights)) {
      RandomWeights.create(RandomWeights.config(16, 2, 8), 1, 0.3).write(out);
    }
  }

  /** Gives the commands, and returns the answers, one for each. */
  private String[] run(String... commands) throws IOException {
    var answers = new ByteArrayOutputStream();
    new Actor().run(new ByteArrayInputStream((String.join("\n", commands) + "\n").getBytes(StandardCharsets.UTF_8)), new PrintStream(answers, true, StandardCharsets.UTF_8));
    return answers.toString(StandardCharsets.UTF_8).strip().split("\n");
  }

  private JsonNode ok(String answer) throws IOException {
    assertTrue(answer.startsWith("OK "), answer);
    return JSON.readTree(answer.substring(3));
  }

  @Test
  public void testInfoGivesTheMapAndTheSizesOfTheNetwork() throws IOException {
    var file = directory.resolve("info.npz");
    var answers = run("INFO out=" + file, "QUIT");
    var info = ok(answers[0]);
    assertEquals(42, info.get("numTerritories").asInt());
    assertEquals(3572, info.get("actions").asInt());
    assertEquals("OK {}", answers[1]);
    var arrays = NpzReader.read(file);
    assertArrayEquals(new int[] {42, 42}, arrays.get("adjacency").shape());
    int borders = 0;
    var cells = arrays.get("adjacency").bytes();
    for (int i = 0; i < 42; i++) {
      for (int j = 0; j < 42; j++) {
        assertEquals(cells[i * 42 + j], cells[j * 42 + i], "symmetric");
        borders += cells[i * 42 + j];
      }
    }
    assertEquals(166, borders);
    assertArrayEquals(new int[] {42, info.get("nodeFeatures").asInt(), info.get("globalFeatures").asInt(), 3572}, arrays.get("dims").ints());
  }

  @Test
  public void testARolloutIsPlayedAndWrittenForTheLearner() throws IOException {
    var file = directory.resolve("rollout.npz");
    var answers = run("ROLLOUT players=2 games=3 seed=5 weights=" + weights + " out=" + file, "QUIT");
    var result = ok(answers[0]);
    assertEquals(3, result.get("games").asInt());
    int steps = result.get("steps").asInt();
    assertTrue(steps > 50);
    var arrays = NpzReader.read(file);
    assertArrayEquals(new int[] {steps, 42, 13}, arrays.get("obs").shape());
    assertArrayEquals(new int[] {steps, 14}, arrays.get("globals").shape());
    var offsets = arrays.get("legal_offsets").ints();
    var legal = arrays.get("legal_indices").ints();
    var actions = arrays.get("action").ints();
    var logProbs = arrays.get("log_prob").floats();
    assertEquals(steps + 1, offsets.length);
    assertEquals(legal.length, offsets[steps]);
    for (int i = 0; i < steps; i++) {
      assertTrue(offsets[i] < offsets[i + 1], "a decision has legal actions");
      boolean found = false;
      for (int k = offsets[i]; k < offsets[i + 1]; k++) {
        found |= legal[k] == actions[i];
      }
      assertTrue(found, "the action is one of the legal");
      assertTrue(logProbs[i] <= 0 && Float.isFinite(logProbs[i]));
    }
    for (var name : new String[] {"value", "reward", "advantage", "value_target"}) {
      for (float value : arrays.get(name).floats()) {
        assertTrue(Float.isFinite(value), name);
      }
    }
    assertEquals(3, arrays.get("stats").doubles()[0]);
  }

  @Test
  public void testTheSameSeedGivesTheSameRolloutAndAnotherSeedAnother() throws IOException {
    var first = directory.resolve("a.npz");
    var second = directory.resolve("b.npz");
    var other = directory.resolve("c.npz");
    run("ROLLOUT players=2 games=2 seed=9 weights=" + weights + " out=" + first,
        "ROLLOUT players=2 games=2 seed=9 weights=" + weights + " out=" + second,
        "ROLLOUT players=2 games=2 seed=10 weights=" + weights + " out=" + other);
    var a = NpzReader.read(first).get("action").ints();
    var b = NpzReader.read(second).get("action").ints();
    var c = NpzReader.read(other).get("action").ints();
    assertArrayEquals(a, b);
    assertFalse(java.util.Arrays.equals(a, c));
  }

  @Test
  public void testTheNetworkIsReadForEachCommandSoTheLearnerCanChangeIt() throws IOException {
    var other = directory.resolve("other.weights");
    try (var out = Files.newOutputStream(other)) {
      RandomWeights.create(RandomWeights.config(16, 2, 8), 2, 0.3).write(out);
    }
    var first = directory.resolve("a.npz");
    var second = directory.resolve("b.npz");
    run("ROLLOUT players=2 games=2 seed=3 weights=" + weights + " out=" + first,
        "ROLLOUT players=2 games=2 seed=3 weights=" + other + " out=" + second);
    assertFalse(java.util.Arrays.equals(NpzReader.read(first).get("action").ints(), NpzReader.read(second).get("action").ints()));
  }

  @Test
  public void testTheNetworkIsEvaluatedAgainstGreedyBots() throws IOException {
    var result = ok(run("EVAL players=2 games=6 weights=" + weights, "QUIT")[0]);
    assertEquals(6, result.get("games").asInt());
    assertTrue(result.get("wins").asInt() >= 0 && result.get("wins").asInt() <= 6);
    assertEquals(result.get("wins").asInt() / 6.0, result.get("winRate").asDouble(), 1e-12);
    // with two players, the network eliminates the opponent when it wins
    assertEquals(result.get("wins").asInt() / 6.0, result.get("eliminationsPerGame").asDouble(), 1e-12);
    // the same boards, so the same result, apart from the time it took
    var again = (com.fasterxml.jackson.databind.node.ObjectNode) ok(run("EVAL players=2 games=6 weights=" + weights, "QUIT")[0]);
    var first = (com.fasterxml.jackson.databind.node.ObjectNode) result;
    first.remove("seconds");
    again.remove("seconds");
    assertEquals(first, again);
  }

  @Test
  public void testTheDecisionsOfGreedyBotsAreWrittenForImitation() throws IOException {
    var file = directory.resolve("imitation.npz");
    var result = ok(run("IMITATION players=2 games=2 seed=1 out=" + file, "QUIT")[0]);
    int samples = result.get("samples").asInt();
    assertTrue(samples > 100);
    var arrays = NpzReader.read(file);
    assertArrayEquals(new int[] {samples, 42, 13}, arrays.get("obs").shape());
    assertEquals(samples, arrays.get("kind").ints().length);
    var kinds = arrays.get("kind").ints();
    assertTrue(java.util.Arrays.stream(kinds).allMatch(kind -> kind >= 0 && kind < Imitation.Kind.values().length));
    assertEquals(samples + 1, arrays.get("acceptable_offsets").ints().length);
  }

  @Test
  public void testMistakesAreAnsweredWithAnErrorAndTheActorGoesOn() throws IOException {
    var answers = run("DANCE", "ROLLOUT players=2", "ROLLOUT players=2 games=1 weights=" + directory.resolve("missing") + " out=x.npz",
        "ROLLOUT nonsense", "INFO out=" + directory.resolve("i.npz"), "QUIT");
    assertTrue(answers[0].startsWith("ERROR ") && answers[0].contains("DANCE"), answers[0]);
    assertTrue(answers[1].startsWith("ERROR ") && answers[1].contains("Missing"), answers[1]);
    assertTrue(answers[2].startsWith("ERROR "), answers[2]);
    assertTrue(answers[3].startsWith("ERROR "), answers[3]);
    assertNotNull(ok(answers[4]));
    assertEquals("OK {}", answers[5]);
  }

  @Test
  public void testTheOpponentsCanBeChosen() throws IOException {
    var greedy = ok(run("EVAL players=2 games=12 weights=" + weights + " opponents=greedy", "QUIT")[0]);
    var cautious = ok(run("EVAL players=2 games=12 weights=" + weights + " opponents=cautious", "QUIT")[0]);
    var both = ok(run("EVAL players=2 games=12 weights=" + weights + " opponents=greedy,cautious", "QUIT")[0]);
    assertEquals(12, both.get("games").asInt());
    assertTrue(greedy.get("averageTurns").asDouble() != cautious.get("averageTurns").asDouble()
        || greedy.get("wins").asInt() != cautious.get("wins").asInt());
    // training games too
    var file = directory.resolve("rollout.npz");
    var rollout = ok(run("ROLLOUT players=2 games=2 seed=5 botSeats=1.0 opponents=cautious weights=" + weights + " out=" + file, "QUIT")[0]);
    assertEquals(2, rollout.get("games").asInt());
    var answers = run("EVAL players=2 games=2 weights=" + weights + " opponents=reckless", "QUIT");
    assertTrue(answers[0].startsWith("ERROR ") && answers[0].contains("reckless"), answers[0]);
  }
}
