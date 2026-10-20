package no.hal.risk.fx;

import javafx.scene.paint.Color;

/**
 * The colors for the outlines of the two territories of an attack, by how many armies each lost.
 */
public class BattleColors {

  public static final Color NO_LOSS = Color.LIMEGREEN;
  public static final Color ONLY_LOSS = Color.RED;
  public static final Color MOST_LOSS = Color.ORANGE;
  public static final Color LEAST_LOSS = Color.GOLD;
  /** For moves that are not attacks. */
  public static final Color OTHER_MOVE = Color.DARKVIOLET;

  /**
   * The color for a territory that lost {@code lost} armies, in a battle where the other territory lost {@code otherLost}:
   * green if it lost nothing, red if only it lost armies, otherwise orange for the one that lost more and yellow for the one
   * that lost fewer, or both yellow if they lost as many.
   */
  public static Color forLosses(int lost, int otherLost) {
    if (lost == 0 && otherLost == 0) {
      return OTHER_MOVE;
    } else if (lost == 0) {
      return NO_LOSS;
    } else if (otherLost == 0) {
      return ONLY_LOSS;
    } else if (lost > otherLost) {
      return MOST_LOSS;
    } else {
      return LEAST_LOSS;
    }
  }
}
