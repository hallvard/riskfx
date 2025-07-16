package no.hal.risk;

import java.util.List;

public class UniformDice implements Dice {

  /**
   * Rolls the die and returns the result.
   *
   * @return the result of the die roll, typically a number between 1 and 6.
   */
  @Override
  public List<Integer> roll(int numDies) {
    return Dice.roll(numDies, () -> (int) (Math.random() * 6) + 1);
  }
}
