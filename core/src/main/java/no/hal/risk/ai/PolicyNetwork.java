package no.hal.risk.ai;

import java.util.List;

/**
 * A network that scores the actions of a state, and values the state, for the player to move.
 * It can be run in Java only ({@link JavaPolicyNetwork}), or while training, with PyTorch.
 */
public interface PolicyNetwork {

  /**
   * @param logits one row for each state, with a score for each position in the {@link ActionSpace}, not masked
   * @param values the value of each state
   */
  record Evaluation(float[][] logits, float[] values) {
  }

  /** The number of territories of the map, which decides the size of the {@link ActionSpace}. */
  int numTerritories();

  Evaluation evaluate(List<Observation> observations);
}
