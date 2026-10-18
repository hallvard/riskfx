package no.hal.risk;

import java.util.List;

public record Battle(Territory attacker, Territory defender, int numAttackerDies, int numDefenderDies) {

  public Battle {
    if (attacker != null && numAttackerDies >= attacker.numArmies()) {
      throw new IllegalArgumentException("Too many attackers: " + numAttackerDies + " >= " + attacker.numArmies());
    }
    if (defender != null && numDefenderDies > defender.numArmies()) {
      throw new IllegalArgumentException("Too many defenders: " + numDefenderDies + " >= " + defender.numArmies());
    }
  }

  public Battle(int numAttackerDies, int numDefenderDies) {
    this(null, null, numAttackerDies, numDefenderDies);
  }

  public record Result(List<Integer> attackerDies, List<Integer> defenderDies, int attackerLoss, int defenderLoss) {
  }

  public Result getResult(Dice dice) {
    List<Integer> attackerDies = dice.roll(numAttackerDies);
    List<Integer> defenderDies = dice.roll(numDefenderDies);
    int attackerLoss = 0;
    int defenderLoss = 0;
    for (int i = 0; i < Math.min(attackerDies.size(), defenderDies.size()); i++) {
      if (attackerDies.get(i) > defenderDies.get(i)) {
        defenderLoss++;
      } else {
        attackerLoss++;
      }
    }
    return new Result(attackerDies, defenderDies, attackerLoss, defenderLoss);
  }
}
