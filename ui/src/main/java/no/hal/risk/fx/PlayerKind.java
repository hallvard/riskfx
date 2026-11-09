package no.hal.risk.fx;

/**
 * What controls a player: a human, the trained AI, or one of the rule-based bots, which are {@link no.hal.risk.ai.Bots} by name.
 * The order is the order in the choices, with the human and the AI first.
 */
public enum PlayerKind {
  HUMAN("Human", null), AI("AI", null),
  GREEDY("Greedy", "greedy"), CAUTIOUS("Cautious", "cautious"), DENIER("Denier", "denier"), ERRATIC("Erratic", "erratic");

  private final String label;
  private final String botName;

  PlayerKind(String label, String botName) {
    this.label = label;
    this.botName = botName;
  }

  public String label() {
    return label;
  }

  /** The name of the rule-based bot, in {@link no.hal.risk.ai.Bots}, or null for a human and for the AI. */
  public String botName() {
    return botName;
  }

  @Override
  public String toString() {
    return label;
  }
}
