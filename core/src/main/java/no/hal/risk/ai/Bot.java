package no.hal.risk.ai;

import java.util.List;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * Chooses the next action for the current player of an environment.
 */
public interface Bot {

  String name();

  /**
   * @param legalActions the legal actions of the current player, never empty
   * @return one of the legal actions
   */
  Action chooseAction(RiskEnv env, List<Action> legalActions);
}
