package no.hal.risk.ai;

/** Choosing among the legal actions by the scores of a network. */
public class Policies {

  /**
   * The probabilities of the legal actions: a softmax of their scores only, which is the same as masking the others.
   *
   * @param logits the scores of all actions in the {@link ActionSpace}
   * @param legal the positions of the legal actions
   */
  public static double[] probabilities(float[] logits, int[] legal) {
    double max = Double.NEGATIVE_INFINITY;
    for (int index : legal) {
      max = Math.max(max, logits[index]);
    }
    var probabilities = new double[legal.length];
    double sum = 0;
    for (int i = 0; i < legal.length; i++) {
      probabilities[i] = Math.exp(logits[legal[i]] - max);
      sum += probabilities[i];
    }
    for (int i = 0; i < legal.length; i++) {
      probabilities[i] /= sum;
    }
    return probabilities;
  }
}
