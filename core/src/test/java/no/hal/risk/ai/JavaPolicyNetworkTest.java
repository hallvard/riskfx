package no.hal.risk.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JavaPolicyNetworkTest {

  private RiskEnv env;
  private ObservationEncoder encoder;
  private NetworkWeights.Config config;

  @BeforeEach
  public void setUp() {
    env = new RiskEnv(new RiskConfig(2), 100);
    env.reset(1);
    encoder = new ObservationEncoder(env);
    config = new NetworkWeights.Config(encoder.territoryFeatureNames().size(), encoder.globalFeatureNames().size(), 16, 2, 8);
  }

  /** Weights of the shapes a network of this size has, with random values. */
  private NetworkWeights randomWeights(long seed) {
    var random = new Random(seed);
    var shapes = new LinkedHashMap<String, int[]>();
    int f = config.nodeFeatures(), g = config.globalFeatures(), h = config.hidden(), d = config.attention();
    shapes.put("Wx", new int[] {f, h});
    shapes.put("Wg", new int[] {g, h});
    shapes.put("b0", new int[] {h});
    for (int l = 0; l < config.layers(); l++) {
      shapes.put("Wn" + l, new int[] {h, h});
      shapes.put("Ws" + l, new int[] {h, h});
      shapes.put("b" + (l + 1), new int[] {h});
    }
    shapes.put("Wrn", new int[] {h, h});
    shapes.put("Wrg", new int[] {h, h});
    shapes.put("br", new int[] {h});
    shapes.put("Wro", new int[] {h, 1});
    for (var name : List.of("Wqa", "Wka", "Wqf", "Wkf")) {
      shapes.put(name, new int[] {h, d});
    }
    shapes.put("We", new int[] {h, 2});
    shapes.put("be", new int[] {2});
    shapes.put("Wv1", new int[] {h, h});
    shapes.put("bv1", new int[] {h});
    shapes.put("Wv2", new int[] {h, 1});
    shapes.put("bv2", new int[] {1});
    var tensors = new LinkedHashMap<String, NetworkWeights.Tensor>();
    shapes.forEach((name, shape) -> {
      int size = 1;
      for (int dimension : shape) {
        size *= dimension;
      }
      var values = new float[size];
      for (int i = 0; i < size; i++) {
        values[i] = (float) (random.nextGaussian() * 0.3);
      }
      tensors.put(name, new NetworkWeights.Tensor(shape, values));
    });
    return new NetworkWeights(config, tensors);
  }

  private List<Observation> observations(int count) {
    var observations = new ArrayList<Observation>();
    var bot = new GreedyBot();
    while (observations.size() < count && ! env.isDone()) {
      observations.add(encoder.encode(env.currentPlayer()));
      env.step(bot.chooseAction(env, env.legalActions()));
    }
    return observations;
  }

  @Test
  public void testTheOutputIsOneScoreForEachActionAndOneValueForEachState() {
    var network = new JavaPolicyNetwork(randomWeights(1), encoder.adjacency());
    var evaluation = network.evaluate(observations(5));
    assertEquals(5, evaluation.logits().length);
    assertEquals(5, evaluation.values().length);
    assertEquals(new ActionSpace(42).size(), evaluation.logits()[0].length);
    assertEquals(42, network.numTerritories());
    for (var row : evaluation.logits()) {
      for (float logit : row) {
        assertTrue(Float.isFinite(logit));
      }
    }
    for (float value : evaluation.values()) {
      assertTrue(Float.isFinite(value));
    }
  }

  @Test
  public void testTheSameStateGivesTheSameResultWhateverTheBatch() {
    var network = new JavaPolicyNetwork(randomWeights(2), encoder.adjacency());
    var observations = observations(4);
    var batch = network.evaluate(observations);
    for (int i = 0; i < observations.size(); i++) {
      var single = network.evaluate(List.of(observations.get(i)));
      assertArrayEquals(single.logits()[0], batch.logits()[i]);
      assertEquals(single.values()[0], batch.values()[i]);
    }
  }

  @Test
  public void testTheResultDependsOnTheStateAndOnTheWeights() {
    var observations = observations(30);
    var first = new JavaPolicyNetwork(randomWeights(3), encoder.adjacency()).evaluate(List.of(observations.getFirst(), observations.getLast()));
    assertFalse(java.util.Arrays.equals(first.logits()[0], first.logits()[1]));
    assertNotEquals(first.values()[0], first.values()[1]);
    var other = new JavaPolicyNetwork(randomWeights(4), encoder.adjacency()).evaluate(List.of(observations.getFirst()));
    assertFalse(java.util.Arrays.equals(first.logits()[0], other.logits()[0]));
  }

  @Test
  public void testWeightsOfAnotherShapeAreRejected() {
    var weights = randomWeights(5);
    var missing = new LinkedHashMap<>(weights.tensors());
    missing.remove("Wv2");
    assertThrows(IllegalArgumentException.class, () -> new JavaPolicyNetwork(new NetworkWeights(config, missing), encoder.adjacency()));
    var wrongShape = new LinkedHashMap<>(weights.tensors());
    wrongShape.put("be", new NetworkWeights.Tensor(new int[] {3}, new float[3]));
    assertThrows(IllegalArgumentException.class, () -> new JavaPolicyNetwork(new NetworkWeights(config, wrongShape), encoder.adjacency()));
  }

  @Test
  public void testTheSameWeightsRunOnAMapOfAnotherSize() {
    var ring = new boolean[10][10];
    for (int i = 0; i < 10; i++) {
      ring[i][(i + 1) % 10] = true;
      ring[(i + 1) % 10][i] = true;
    }
    var network = new JavaPolicyNetwork(randomWeights(5), ring);
    var observation = new Observation(new float[10][config.nodeFeatures()], new float[config.globalFeatures()]);
    var evaluation = network.evaluate(List.of(observation));
    assertEquals(10, network.numTerritories());
    assertEquals(new ActionSpace(10).size(), evaluation.logits()[0].length);
  }

  // the file

  @Test
  public void testWeightsSurviveBeingWrittenAndRead() throws IOException {
    var weights = randomWeights(6);
    var out = new ByteArrayOutputStream();
    weights.write(out);
    var bytes = out.toByteArray();
    assertTrue(NetworkWeights.isWeights(bytes));
    var read = NetworkWeights.read(new ByteArrayInputStream(bytes));
    assertEquals(weights.config(), read.config());
    assertEquals(weights.tensors().keySet(), read.tensors().keySet());
    for (var name : weights.tensors().keySet()) {
      assertArrayEquals(weights.tensors().get(name).shape(), read.tensors().get(name).shape());
      assertArrayEquals(weights.tensors().get(name).data(), read.tensors().get(name).data());
    }
    // and the network from the file computes the same as the one from the memory
    var observations = observations(3);
    var fromMemory = new JavaPolicyNetwork(weights, encoder.adjacency()).evaluate(observations);
    var fromFile = JavaPolicyNetwork.load(new ByteArrayInputStream(bytes), encoder.adjacency()).evaluate(observations);
    assertArrayEquals(fromMemory.values(), fromFile.values());
    assertArrayEquals(fromMemory.logits()[2], fromFile.logits()[2]);
  }

  @Test
  public void testSomethingElseThanWeightsIsRecognized() {
    assertFalse(NetworkWeights.isWeights(new byte[] {1, 2, 3, 4, 5}));
    assertFalse(NetworkWeights.isWeights(new byte[] {1}));
    assertThrows(IOException.class, () -> NetworkWeights.read(new ByteArrayInputStream(new byte[] {1, 2, 3, 4, 5, 6, 7, 8})));
  }

  @Test
  public void testATensorMustHaveAsManyValuesAsItsShape() {
    assertThrows(IllegalArgumentException.class, () -> new NetworkWeights.Tensor(new int[] {2, 3}, new float[5]));
  }

  @Test
  public void testNetworkBotPlaysLegalMovesWithTheJavaNetwork() {
    var network = new JavaPolicyNetwork(randomWeights(7), encoder.adjacency());
    for (boolean sample : new boolean[] {false, true}) {
      var bot = new NetworkBot(network, "AI", sample, 3);
      var game = new RiskEnv(new RiskConfig(2), 100);
      game.reset(4);
      while (! game.isDone()) {
        var legal = game.legalActions();
        var action = bot.chooseAction(game, legal);
        assertTrue(legal.contains(action));
        game.step(action);
      }
    }
  }
}
