package no.hal.risk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

public interface Dice {
  
  /**
   * Rolls the die and returns the result.
   *
   * @return the result of the die roll, typically a number between 1 and 6.
   */
  List<Integer> roll(int numDies);

  public static List<Integer> roll(int numDies, Supplier<Integer> valueSupplier) {
    var dice = new ArrayList<Integer>(numDies);
    for (int i = 0; i < numDies; i++) {
      dice.add(valueSupplier.get());
    }
    Collections.sort(dice, Comparator.reverseOrder());
    return dice;
  }
}
