package no.hal.risk.ai;

import java.util.List;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * A simple rule-based player, deterministic given the state: the setting of {@link ParametrizedBot} where nothing is ever too weak,
 * and every attack with a lead of at least {@code minAdvantage} is fine:
 * <ul>
 * <li>reinforce: pile armies onto the strongest territory bordering an enemy</li>
 * <li>attack: attack where the attacker has the biggest advantage, as long as it has at least {@code minAdvantage} more armies</li>
 * <li>fortify: move the armies of the strongest interior territory to a territory bordering an enemy</li>
 * </ul>
 */
public class GreedyBot implements Bot {

  /** The settings of the greedy bot, with the given smallest lead for an attack. */
  public static ParametrizedBot.Params params(int minAdvantage) {
    return new ParametrizedBot.Params(minAdvantage, 0.0, 0, Integer.MAX_VALUE, false,
        ParametrizedBot.Reinforce.STRONGEST_BORDER, ParametrizedBot.Fortify.ANY_BORDER);
  }

  private final ParametrizedBot bot;

  public GreedyBot(int minAdvantage) {
    this.bot = new ParametrizedBot("greedy", params(minAdvantage));
  }

  public GreedyBot() {
    this(2);
  }

  @Override
  public String name() {
    return bot.name();
  }

  @Override
  public Action chooseAction(RiskEnv env, List<Action> legalActions) {
    return bot.chooseAction(env, legalActions);
  }
}
