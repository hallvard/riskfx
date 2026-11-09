package no.hal.risk.ai;

import java.util.List;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * A rule-based player that is in some sense the opposite of {@link GreedyBot}: it advances slowly, with strong borders. It is the
 * setting of {@link ParametrizedBot} with a defense ratio, so a territory can be too weak for what could attack it, and the checks
 * that an attack doesn't leave the attacker or the conquered territory weak. Like greedy, it is deterministic given the state,
 * and remembers nothing, so one bot can play many games.
 * <ul>
 * <li>reinforce: the border territory with the largest deficit, so the weakest border gets stronger first. When no border has a
 *   deficit, a border territory next to the weakest enemy, to prepare an advance.</li>
 * <li>attack: only with a lead of at least {@code minAdvantage} armies, and only if what is left is safe. When a territory is
 *   conquered, all armies but one move in, so the territory attacked from is left with one army, which is safe only if no strong
 *   enemy is next to it, and the conquered territory must be able to hold against the enemies next to it.</li>
 * <li>fortify: from a territory with no enemy next to it, to the border territory with the largest deficit. Armies are not
 *   moved from the borders, which are to stay strong.</li>
 * </ul>
 */
public class CautiousBot implements Bot {

  /** The settings of the cautious bot. */
  public static ParametrizedBot.Params params(int minAdvantage, int maxThreatToOrigin, int margin, double defenseRatio) {
    return new ParametrizedBot.Params(minAdvantage, defenseRatio, margin, maxThreatToOrigin, true,
        ParametrizedBot.Reinforce.WEAKEST_BORDER, ParametrizedBot.Fortify.WEAK_BORDER);
  }

  private final ParametrizedBot bot;

  public CautiousBot(int minAdvantage, int maxThreatToOrigin, int margin, double defenseRatio) {
    this.bot = new ParametrizedBot("cautious", params(minAdvantage, maxThreatToOrigin, margin, defenseRatio));
  }

  public CautiousBot() {
    this(2, 8, 0, 0.7);
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
