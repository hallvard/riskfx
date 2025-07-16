package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

public class BattleTest {
  
  private void testBattle(int numAttackerDies, int numDefenderDies, int attacherLosses, int defenderLosses, Integer... diceValues) {
    Dice dice = new DeterministicDice(List.of(diceValues));
    var battle = new Battle(numAttackerDies, numDefenderDies);
    var result = battle.getResult(dice);
    assertEquals(new Battle.Result(attacherLosses, defenderLosses), result);
  }

  @Test
  public void testBattle() {
    testBattle(3, 2, 0, 2,
        1, 2, 3, 1, 2);
    testBattle(3, 2, 1, 1,
        1, 2, 3, 1, 3);
    testBattle(3, 2, 2, 0,
        1, 2, 3, 2, 3);
  }
}
