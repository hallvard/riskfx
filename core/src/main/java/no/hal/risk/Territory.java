package no.hal.risk;

public class Territory {
  
  private final String name;
  private Player occupant;
  private int numArmies;

  public Territory(String name) {
      this.name = name;
  }

  @Override
  public String toString() {
    return "[Territory %s occupied by %s with %d armies]".formatted(
        name, occupant != null ? occupant.name() : "none", numArmies);
  }

  public String name() {
      return name;
  }

  public Player occupant() {
      return occupant;
  }

  public int numArmies() {
      return numArmies;
  }

  void setOccupant(Player occupant, int numArmies) {
    this.occupant = occupant;
    this.numArmies = numArmies;
  }

  private void updateNumArmies(int numArmies) {
    if (numArmies < 1) {
      throw new IllegalArgumentException("Cannot remove all armies (or more)");
    }
    this.numArmies = numArmies;
  }

  public void acceptLoss(int loss) {
    if (loss < 0 || loss > numArmies) {
      throw new IllegalArgumentException("Cannot lose %s armies".formatted(loss));
    }
    updateNumArmies(numArmies - loss);
  }

  public void transferFrom(int transfer) {
    if (transfer < 0) {
      throw new IllegalArgumentException("Cannot transfer %s armies".formatted(transfer));
    }
    updateNumArmies(numArmies - transfer);
  }

  public void transferTo(int transfer) {
    if (transfer < 0) {
      throw new IllegalArgumentException("Cannot transfer %s armies".formatted(transfer));
    }
    updateNumArmies(numArmies + transfer);
  }
}
