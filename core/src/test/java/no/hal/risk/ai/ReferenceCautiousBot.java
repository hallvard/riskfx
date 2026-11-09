package no.hal.risk.ai;

import java.util.Comparator;
import java.util.List;
import no.hal.risk.Player;
import no.hal.risk.Territory;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * A copy of CautiousBot as it was before it became a setting of {@link ParametrizedBot}, kept to check that the cautious bot still chooses
 * exactly the same actions. It is not used for anything else.
 */
public class ReferenceCautiousBot implements Bot {

  private final int minAdvantage;
  /** The enemy next to a territory that is attacked from, which is left with one army, may have at most this many armies. */
  private final int maxThreatToOrigin;
  /** The armies a territory should have more than {@code defenseRatio} of the threat. */
  private final int margin;
  /** The share of the threat that a territory should have armies enough to meet. */
  private final double defenseRatio;

  public ReferenceCautiousBot(int minAdvantage, int maxThreatToOrigin, int margin, double defenseRatio) {
    this.minAdvantage = minAdvantage;
    this.maxThreatToOrigin = maxThreatToOrigin;
    this.margin = margin;
    this.defenseRatio = defenseRatio;
  }

  public ReferenceCautiousBot() {
    this(2, 8, 0, 0.7);
  }

  @Override
  public String name() {
    return "reference cautious";
  }

  private static Territory territory(RiskEnv env, int index) {
    return env.territories().get(index);
  }

  /** The most armies an enemy next to the territory can attack with, not counting the territory {@code except}, 0 if there are none. */
  private static int threat(RiskEnv env, Territory territory, Player me, Territory except) {
    int threat = 0;
    for (var neighbour : env.risk().world().neighbours(territory)) {
      if (neighbour.occupant() != me && neighbour != except) {
        threat = Math.max(threat, neighbour.numArmies() - 1);
      }
    }
    return threat;
  }

  private int threat(RiskEnv env, Territory territory) {
    return threat(env, territory, territory.occupant(), null);
  }

  /** The armies needed to be safe from this threat. */
  private int required(int threat) {
    return threat == 0 ? 0 : (int) Math.ceil(defenseRatio * threat) + margin;
  }

  /** How much the armies needed to be safe exceed the armies of the territory. Negative when it is safe. */
  private int deficit(RiskEnv env, Territory territory) {
    int threat = threat(env, territory);
    return threat == 0 ? Integer.MIN_VALUE / 2 : required(threat) - territory.numArmies();
  }

  /** Whether an enemy is next to the territory, whether or not it can attack. */
  private static boolean bordersEnemy(RiskEnv env, Territory territory) {
    return weakestEnemy(env, territory) != Integer.MAX_VALUE;
  }

  @Override
  public Action chooseAction(RiskEnv env, List<Action> legalActions) {
    return switch (env.phase()) {
      case REINFORCING -> reinforce(env, legalActions);
      case ATTACKING -> attack(env, legalActions);
      case FORTIFYING -> fortify(env, legalActions);
      case GAME_OVER -> throw new IllegalStateException("The game is over");
    };
  }

  private Action reinforce(RiskEnv env, List<Action> legalActions) {
    var reinforcements = legalActions.stream().map(action -> (Action.Reinforce) action).toList();
    int worstDeficit = reinforcements.stream().mapToInt(action -> deficit(env, territory(env, action.territory()))).max().orElseThrow();
    if (worstDeficit > 0) {
      // strengthen the weakest border: the largest deficit, then the stronger enemy, then the lowest index
      return reinforcements.stream().max(Comparator
          .comparing((Action.Reinforce action) -> deficit(env, territory(env, action.territory())))
          .thenComparing(action -> threat(env, territory(env, action.territory())))
          .thenComparing(action -> -action.territory())).orElseThrow();
    }
    // all borders are safe: prepare an advance, next to the weakest enemy, where there already are most armies
    return reinforcements.stream().filter(action -> bordersEnemy(env, territory(env, action.territory()))).max(Comparator
        .comparing((Action.Reinforce action) -> -weakestEnemy(env, territory(env, action.territory())))
        .thenComparing(action -> territory(env, action.territory()).numArmies())
        .thenComparing(action -> -action.territory())).map(action -> (Action) action).orElse(reinforcements.getFirst());
  }

  /** The smallest army of an enemy next to the territory. */
  private static int weakestEnemy(RiskEnv env, Territory territory) {
    int weakest = Integer.MAX_VALUE;
    for (var neighbour : env.risk().world().neighbours(territory)) {
      if (neighbour.occupant() != territory.occupant()) {
        weakest = Math.min(weakest, neighbour.numArmies());
      }
    }
    return weakest;
  }

  private boolean isSafeAttack(RiskEnv env, Action.Attack attack) {
    var from = territory(env, attack.from());
    var to = territory(env, attack.to());
    var me = from.occupant();
    if (from.numArmies() - to.numArmies() < minAdvantage) {
      return false;
    }
    // the territory attacked from is left with one army
    if (threat(env, from, me, to) > maxThreatToOrigin) {
      return false;
    }
    // the conquered territory gets what is left, at best all but one, and at worst all but one less what the defender can take
    int armiesAfter = from.numArmies() - 1 - to.numArmies();
    return armiesAfter >= required(threat(env, to, me, from));
  }

  private Action attack(RiskEnv env, List<Action> legalActions) {
    return legalActions.stream()
        .filter(action -> action instanceof Action.Attack)
        .map(action -> (Action.Attack) action)
        .filter(attack -> isSafeAttack(env, attack))
        .max(Comparator
            .comparing((Action.Attack attack) -> territory(env, attack.from()).numArmies() - territory(env, attack.to()).numArmies())
            .thenComparing(attack -> -attack.from())
            .thenComparing(attack -> -attack.to()))
        .map(attack -> (Action) attack)
        .orElseGet(Action.EndAttack::new);
  }

  private Action fortify(RiskEnv env, List<Action> legalActions) {
    return legalActions.stream()
        .filter(action -> action instanceof Action.Fortify)
        .map(action -> (Action.Fortify) action)
        // from the interior, to a border with a deficit
        .filter(fortify -> ! bordersEnemy(env, territory(env, fortify.from())) && deficit(env, territory(env, fortify.to())) > 0)
        .max(Comparator
            .comparing((Action.Fortify fortify) -> deficit(env, territory(env, fortify.to())))
            .thenComparing(fortify -> fortify.numArmies())
            .thenComparing(fortify -> -fortify.from())
            .thenComparing(fortify -> -fortify.to()))
        .map(fortify -> (Action) fortify)
        .orElseGet(Action.SkipFortify::new);
  }
}
