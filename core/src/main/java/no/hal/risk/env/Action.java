package no.hal.risk.env;

/**
 * An action of the current player. Territories are referred to by their index in {@link RiskEnv#territories()}.
 */
public sealed interface Action {

  /** Places one army on a territory (REINFORCING). */
  record Reinforce(int territory) implements Action {
  }

  /** Attacks with the maximum number of dice (ATTACKING). */
  record Attack(int from, int to) implements Action {
  }

  /** Ends the attack phase (ATTACKING). */
  record EndAttack() implements Action {
  }

  /** Moves armies between adjacent territories, and ends the turn (FORTIFYING). */
  record Fortify(int from, int to, int numArmies) implements Action {
  }

  /** Ends the turn without fortifying (FORTIFYING). */
  record SkipFortify() implements Action {
  }
}
