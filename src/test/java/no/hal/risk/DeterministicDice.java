package no.hal.risk;

import java.util.Iterator;
import java.util.List;

public class DeterministicDice implements Dice {

  private final Iterator<Integer> values;

  public DeterministicDice(Iterator<Integer> values) {
    this.values = values;
  }

  public DeterministicDice(Iterable<Integer> values) {
    this(values.iterator());
  }

  @Override
  public List<Integer> roll(int numDies) {
    return Dice.roll(numDies, () -> values.next());
  }
}
