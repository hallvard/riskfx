package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class BattleColorsTest {

  @Test
  public void testGreenWhenItLostNothing() {
    assertEquals(BattleColors.NO_LOSS, BattleColors.forLosses(0, 1));
    assertEquals(BattleColors.NO_LOSS, BattleColors.forLosses(0, 2));
    // a conquered territory loses everything, the attacker nothing
    assertEquals(BattleColors.NO_LOSS, BattleColors.forLosses(0, 7));
  }

  @Test
  public void testRedWhenOnlyItLostArmies() {
    assertEquals(BattleColors.ONLY_LOSS, BattleColors.forLosses(1, 0));
    assertEquals(BattleColors.ONLY_LOSS, BattleColors.forLosses(2, 0));
  }

  @Test
  public void testOrangeForTheOneThatLostMostAndYellowForTheOneThatLostLeast() {
    assertEquals(BattleColors.MOST_LOSS, BattleColors.forLosses(2, 1));
    assertEquals(BattleColors.LEAST_LOSS, BattleColors.forLosses(1, 2));
    // a conquest where the attacker lost something: the defender lost everything
    assertEquals(BattleColors.MOST_LOSS, BattleColors.forLosses(5, 2));
    assertEquals(BattleColors.LEAST_LOSS, BattleColors.forLosses(2, 5));
  }

  @Test
  public void testEvenTradeGivesBothTheSameColor() {
    assertEquals(BattleColors.forLosses(1, 1), BattleColors.forLosses(1, 1));
    assertEquals(BattleColors.LEAST_LOSS, BattleColors.forLosses(2, 2));
  }

  @Test
  public void testTheTwoSidesOfABattleGetDifferentColorsUnlessEven() {
    for (int attacker = 0; attacker <= 3; attacker++) {
      for (int defender = 0; defender <= 3; defender++) {
        if (attacker == defender) {
          continue;
        }
        assertNotEquals(BattleColors.forLosses(attacker, defender), BattleColors.forLosses(defender, attacker),
            "attacker lost %d, defender lost %d".formatted(attacker, defender));
      }
    }
  }

  @Test
  public void testColorsAreDistinct() {
    var colors = java.util.Set.of(BattleColors.NO_LOSS, BattleColors.ONLY_LOSS, BattleColors.MOST_LOSS,
        BattleColors.LEAST_LOSS, BattleColors.OTHER_MOVE);
    assertEquals(5, colors.size());
  }
}
