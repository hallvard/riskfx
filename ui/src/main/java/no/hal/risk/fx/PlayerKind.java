package no.hal.risk.fx;

/** What controls a player. */
public enum PlayerKind {
  HUMAN("Human"), GREEDY("Greedy bot"), CAUTIOUS("Cautious bot"), AI("AI");

  private final String label;

  PlayerKind(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  @Override
  public String toString() {
    return label;
  }
}
