package no.hal.risk.training;

import no.hal.risk.ai.*;

import java.util.List;
import java.util.Random;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * Picks a uniformly random legal action. Ending the attack is as likely as any single attack,
 * so it attacks only a little, and is the weakest sensible baseline.
 */
public class RandomBot implements Bot {

  private final Random random;

  public RandomBot(long seed) {
    this.random = new Random(seed);
  }

  @Override
  public String name() {
    return "random";
  }

  @Override
  public Action chooseAction(RiskEnv env, List<Action> legalActions) {
    return legalActions.get(random.nextInt(legalActions.size()));
  }
}
