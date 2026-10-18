package no.hal.risk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PoliciesTest {

  @Test
  public void testProbabilitiesOfLegalActionsOnly() {
    float[] logits = {0f, 1000f, 2f, -3f, 500f};
    // the huge logits of positions 1 and 4 are illegal, and must not matter
    var probabilities = Policies.probabilities(logits, new int[] {0, 2, 3});
    assertEquals(3, probabilities.length);
    assertEquals(1.0, probabilities[0] + probabilities[1] + probabilities[2], 1e-12);
    assertTrue(probabilities[1] > probabilities[0] && probabilities[0] > probabilities[2]);
    assertEquals(Math.exp(2) / (1 + Math.exp(2) + Math.exp(-3)), probabilities[1], 1e-12);
  }
}
