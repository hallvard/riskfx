package no.hal.risk;

import java.util.List;
import java.util.Random;

public class UniformDice implements Dice {

  private final Random random;

  public UniformDice(Random random) {
    this.random = random;
  }

  public UniformDice() {
    this(new Random());
  }

  /**
   * Rolls the die and returns the result.
   *
   * @return the result of the die roll, typically a number between 1 and 6.
   */
  @Override
  public List<Integer> roll(int numDies) {
    return Dice.roll(numDies, () -> random.nextInt(6) + 1);
  }
}
