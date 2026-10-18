package no.hal.risk;

/**
 * Rules that can be switched on, which are off by default, since the AI was trained without them,
 * and the numbers of armies they use, which are by default those of the constants in {@link Risk}.
 *
 * @param keepArmiesBack a player who conquers a territory must move in at least as many armies as dice were rolled,
 *   and may move in up to all but one, which {@link Risk#occupy(int)} does. If off, all armies but one always move in.
 * @param conquestBonus if on, the armies of a turn are split in parts: {@code alwaysArmies} always, the continent bonuses always,
 *   and {@code conquestArmies} only if a territory was conquered the turn before (in the first round, everyone is counted as having conquered one).
 *   This is a simplification of the cards in the original game. If off, a turn gives {@code baseArmies} and the continent bonuses.
 * @param eliminationBonus if on, {@code eliminationArmies} more armies for each player eliminated the turn before, whether or not conquestBonus is on
 */
public record Rules(
    boolean keepArmiesBack,
    boolean conquestBonus,
    boolean eliminationBonus,
    int baseArmies,
    int alwaysArmies,
    int conquestArmies,
    int eliminationArmies
  ) {

  public static final Rules DEFAULT = new Rules(false, false, false);

  /** The rules as chosen, with the default numbers of armies. */
  public Rules(boolean keepArmiesBack, boolean conquestBonus, boolean eliminationBonus) {
    this(keepArmiesBack, conquestBonus, eliminationBonus, Risk.BASE_REINFORCEMENT, Risk.ALWAYS_REINFORCEMENT, Risk.CONQUEST_BONUS, Risk.ELIMINATION_BONUS);
  }

  /** Without the elimination bonus. */
  public Rules(boolean keepArmiesBack, boolean conquestBonus) {
    this(keepArmiesBack, conquestBonus, false);
  }
}
