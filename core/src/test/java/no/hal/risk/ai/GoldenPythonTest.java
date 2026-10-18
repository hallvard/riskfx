package no.hal.risk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

/**
 * The network that runs in Java must compute what the one that is trained in Python computes. The files in {@code golden} are
 * written by the learner ({@code python -m risk_learner.make_golden}): weights, in the format that Python writes and Java reads,
 * and some states, with the scores and values that PyTorch gives them.
 */
public class GoldenPythonTest {

  private static JsonNode golden() throws IOException {
    try (var in = GoldenPythonTest.class.getResourceAsStream("/golden/golden.json")) {
      return new ObjectMapper().readTree(in);
    }
  }

  private static float[][] matrix(JsonNode node) {
    var result = new float[node.size()][];
    for (int i = 0; i < result.length; i++) {
      result[i] = new float[node.get(i).size()];
      for (int j = 0; j < result[i].length; j++) {
        result[i][j] = (float) node.get(i).get(j).asDouble();
      }
    }
    return result;
  }

  @Test
  public void testTheMapIsTheSameInPythonAndJava() throws IOException {
    var golden = golden();
    var borders = new ObservationEncoder(new RiskEnv(new RiskConfig(2), 10)).adjacency();
    assertEquals(borders.length, golden.get("borders").size());
    for (int i = 0; i < borders.length; i++) {
      for (int j = 0; j < borders.length; j++) {
        assertEquals(borders[i][j] ? 1 : 0, golden.get("borders").get(i).get(j).asInt(), "border %d-%d".formatted(i, j));
      }
    }
  }

  @Test
  public void testTheWeightsWrittenByPythonAreReadAndGiveTheScoresOfPyTorch() throws IOException {
    var golden = golden();
    var borders = new ObservationEncoder(new RiskEnv(new RiskConfig(2), 10)).adjacency();
    JavaPolicyNetwork network;
    try (var in = GoldenPythonTest.class.getResourceAsStream("/golden/golden.weights")) {
      network = JavaPolicyNetwork.load(in, borders);
    }
    // the size of the network is as written in Python
    assertEquals(golden.get("borders").size(), network.numTerritories());
    assertEquals(golden.get("config").get(2).asInt(), network.config().hidden());
    assertEquals(golden.get("config").get(3).asInt(), network.config().layers());
    assertEquals(golden.get("config").get(4).asInt(), network.config().attention());

    var observations = new ArrayList<Observation>();
    var globals = matrix(golden.get("globals"));
    for (int state = 0; state < globals.length; state++) {
      observations.add(new Observation(matrix(golden.get("territories").get(state)), globals[state]));
    }
    var expectedLogits = matrix(golden.get("logits"));
    var expectedValues = golden.get("values");
    var evaluation = network.evaluate(observations);
    assertTrue(observations.size() >= 3);
    double largest = 0;
    for (int state = 0; state < observations.size(); state++) {
      largest = Math.max(largest, relativeDifference(expectedValues.get(state).asDouble(), evaluation.values()[state]));
      assertEquals(expectedLogits[state].length, evaluation.logits()[state].length);
      for (int action = 0; action < expectedLogits[state].length; action++) {
        largest = Math.max(largest, relativeDifference(expectedLogits[state][action], evaluation.logits()[state][action]));
      }
    }
    assertTrue(largest < 1e-4, "largest relative difference between PyTorch and Java: " + largest);
  }

  private static double relativeDifference(double expected, double actual) {
    return Math.abs(expected - actual) / Math.max(1, Math.abs(expected));
  }
}
