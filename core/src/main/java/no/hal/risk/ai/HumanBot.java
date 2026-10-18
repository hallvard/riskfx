package no.hal.risk.ai;

import java.util.List;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * Stands for a human player in a {@link BotMatch}. A human chooses actions in the user interface,
 * which are then performed with {@link BotMatch#perform(Action)}, so this bot is never asked to choose.
 */
public class HumanBot implements Bot {

  @Override
  public String name() {
    return "Human";
  }

  @Override
  public Action chooseAction(RiskEnv env, List<Action> legalActions) {
    throw new UnsupportedOperationException("A human chooses actions in the user interface");
  }
}
