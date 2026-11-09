package no.hal.risk.ai;

import java.util.Comparator;
import java.util.List;
import no.hal.risk.Player;
import no.hal.risk.Territory;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * A rule-based player whose style is set by a few parameters, {@link Params}, so one bot can be many: {@link GreedyBot} and
 * {@link CautiousBot} are particular settings of it. Like them it is deterministic given the state, and remembers nothing,
 * so one bot can play many games.
 *
 * <p>The <em>threat</em> to a territory is the most armies an adjacent enemy territory can attack with, which is its armies less
 * the one that stays behind. A territory is safe if it has {@code defenseRatio} of the threat, and the margin. Its <em>deficit</em>
 * is how much that exceeds its armies, so a territory with a deficit is weak compared to what could attack it. With a defense ratio
 * of 0, nothing is ever weak.
 * <ul>
 * <li>reinforce: either on the strongest border territory, or on the one with the largest deficit, and, when no border has a
 *   deficit, next to the weakest enemy, to prepare an advance, see {@link Reinforce}</li>
 * <li>attack: with a lead of at least {@code minAdvantage} armies, where it is the biggest, or what is worth most when it prefers
 *   to complete or break continents, or eliminate an opponent. The attacks of a turn, or the conquests, can be limited. It can also require that the territory
 *   attacked from is not left next to a strong enemy ({@code maxThreatToOrigin}), and that the conquered territory is safe against
 *   the enemies next to it ({@code holdConquered}), since all armies but one move in.</li>
 * <li>fortify: from a territory with no enemy next to it, to any border territory, or to the one with the largest deficit,
 *   see {@link Fortify}</li>
 * </ul>
 */
public class ParametrizedBot implements Bot {

  /** How armies are placed when reinforcing. */
  public enum Reinforce {
    /** On the border territory with the most armies: concentrate them where they already are. */
    STRONGEST_BORDER,
    /** On the border territory with the largest deficit, so the weakest border gets stronger first. */
    WEAKEST_BORDER
  }

  /** Where armies are moved when fortifying: from a territory with no enemy next to it. */
  public enum Fortify {
    /** To any border territory, most armies first. */
    ANY_BORDER,
    /** Only to a border territory with a deficit, the largest first. */
    WEAK_BORDER
  }

  /**
   * @param minAdvantage the fewest armies an attacker must have more than the defender
   * @param defenseRatio the share of the threat that a territory should have armies enough to meet, 0 for none
   * @param margin the armies a territory should have more than {@code defenseRatio} of the threat
   * @param maxThreatToOrigin the enemy next to a territory that is attacked from, which is left with one army, may have at most
   *   this many armies, {@link Integer#MAX_VALUE} for no limit
   * @param holdConquered whether the conquered territory must be able to hold against the enemies next to it
   * @param maxAttacksPerTurn the attacks in a turn, after which the attack is ended, {@link Integer#MAX_VALUE} for no limit
   * @param maxConquestsPerTurn the territories conquered in a turn, after which the attack is ended, {@link Integer#MAX_VALUE} for no limit.
   *   With 1, it is satisfied with one conquest each turn, which with the rule of a bonus after a conquest is what it takes to get it.
   * @param continentBonus how much more an attack is worth, in armies of lead, for each point of bonus of a continent that it completes
   * @param denyBonus how much more an attack is worth for each point of bonus of a continent that it breaks, which an opponent held
   * @param eliminationBonus how much more an attack is worth if it takes the last territory of an opponent
   * @param noise the size, in armies of lead, of a random number that is added to what an attack is worth, so that the choice is less
   *   predictable: 0 for none. A bot with noise is not deterministic.
   */
  public record Params(int minAdvantage, double defenseRatio, int margin, int maxThreatToOrigin, boolean holdConquered,
      Reinforce reinforce, Fortify fortify, int maxAttacksPerTurn, int maxConquestsPerTurn,
      double continentBonus, double denyBonus, double eliminationBonus, double noise) {

    /** With no limits on the attacks, and no preference for what is attacked. */
    public Params(int minAdvantage, double defenseRatio, int margin, int maxThreatToOrigin, boolean holdConquered,
        Reinforce reinforce, Fortify fortify) {
      this(minAdvantage, defenseRatio, margin, maxThreatToOrigin, holdConquered, reinforce, fortify,
          Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0, 0, 0);
    }

    public Params withLimits(int maxAttacksPerTurn, int maxConquestsPerTurn) {
      return new Params(minAdvantage, defenseRatio, margin, maxThreatToOrigin, holdConquered, reinforce, fortify,
          maxAttacksPerTurn, maxConquestsPerTurn, continentBonus, denyBonus, eliminationBonus, noise);
    }

    public Params withTargets(double continentBonus, double denyBonus, double eliminationBonus) {
      return new Params(minAdvantage, defenseRatio, margin, maxThreatToOrigin, holdConquered, reinforce, fortify,
          maxAttacksPerTurn, maxConquestsPerTurn, continentBonus, denyBonus, eliminationBonus, noise);
    }

    public Params withNoise(double noise) {
      return new Params(minAdvantage, defenseRatio, margin, maxThreatToOrigin, holdConquered, reinforce, fortify,
          maxAttacksPerTurn, maxConquestsPerTurn, continentBonus, denyBonus, eliminationBonus, noise);
    }

    private boolean hasTargets() {
      return continentBonus != 0 || denyBonus != 0 || eliminationBonus != 0;
    }
  }

  private final String name;
  private final Params params;

  public ParametrizedBot(String name, Params params) {
    this.name = name;
    this.params = params;
  }

  public Params params() {
    return params;
  }

  @Override
  public String name() {
    return name;
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
    return threat == 0 ? 0 : (int) Math.ceil(params.defenseRatio() * threat) + params.margin();
  }

  /** How much the armies needed to be safe exceed the armies of the territory. Negative when it is safe. */
  private int deficit(RiskEnv env, Territory territory) {
    int threat = threat(env, territory);
    return threat == 0 ? Integer.MIN_VALUE / 2 : required(threat) - territory.numArmies();
  }

  /** The smallest army of an enemy next to the territory, {@link Integer#MAX_VALUE} if there is none. */
  private static int weakestEnemy(RiskEnv env, Territory territory) {
    int weakest = Integer.MAX_VALUE;
    for (var neighbour : env.risk().world().neighbours(territory)) {
      if (neighbour.occupant() != territory.occupant()) {
        weakest = Math.min(weakest, neighbour.numArmies());
      }
    }
    return weakest;
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
    if (params.reinforce() == Reinforce.STRONGEST_BORDER) {
      // prefer territories bordering an enemy, then the one with the most armies, then the lowest index
      return reinforcements.stream()
          .max(Comparator
              .comparing((Action.Reinforce action) -> bordersEnemy(env, territory(env, action.territory())))
              .thenComparing(action -> territory(env, action.territory()).numArmies())
              .thenComparing(action -> -action.territory()))
          .orElseThrow();
    }
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

  private boolean isAcceptableAttack(RiskEnv env, Action.Attack attack) {
    var from = territory(env, attack.from());
    var to = territory(env, attack.to());
    var me = from.occupant();
    if (from.numArmies() - to.numArmies() < params.minAdvantage()) {
      return false;
    }
    // the territory attacked from is left with one army
    if (threat(env, from, me, to) > params.maxThreatToOrigin()) {
      return false;
    }
    if (! params.holdConquered()) {
      return true;
    }
    // the conquered territory gets what is left, at best all but one, and at worst all but one less what the defender can take
    int armiesAfter = from.numArmies() - 1 - to.numArmies();
    return armiesAfter >= required(threat(env, to, me, from));
  }

  private record Scored(Action.Attack attack, double score) {
  }

  /**
   * What an attack is worth: the lead, which is what the attacker has more than the defender, and what the preferences of the bot add:
   * the continents it completes or breaks, and an opponent it eliminates, and the noise.
   */
  private double score(RiskEnv env, Action.Attack attack) {
    var from = territory(env, attack.from());
    var to = territory(env, attack.to());
    double score = from.numArmies() - to.numArmies();
    if (params.hasTargets()) {
      var me = from.occupant();
      var defender = to.occupant();
      for (var continent : env.risk().continents()) {
        if (! continent.territories().contains(to)) {
          continue;
        }
        if (continent.territories().stream().allMatch(other -> other == to || other.occupant() == me)) {
          score += params.continentBonus() * continent.bonusPoints();
        }
        if (env.risk().continentOccupant(continent) == defender) {
          score += params.denyBonus() * continent.bonusPoints();
        }
      }
      if (env.risk().playerTerritories(defender).size() == 1) {
        score += params.eliminationBonus();
      }
    }
    if (params.noise() > 0) {
      score += params.noise() * java.util.concurrent.ThreadLocalRandom.current().nextGaussian();
    }
    return score;
  }

  private Action attack(RiskEnv env, List<Action> legalActions) {
    if (env.attacksThisTurn() >= params.maxAttacksPerTurn() || env.conquestsThisTurn() >= params.maxConquestsPerTurn()) {
      return new Action.EndAttack();
    }
    return legalActions.stream()
        .filter(action -> action instanceof Action.Attack)
        .map(action -> (Action.Attack) action)
        .filter(attack -> isAcceptableAttack(env, attack))
        // the score is made once for each, since with noise it is not the same each time
        .map(attack -> new Scored(attack, score(env, attack)))
        .max(Comparator
            .comparingDouble(Scored::score)
            .thenComparing(scored -> -scored.attack().from())
            .thenComparing(scored -> -scored.attack().to()))
        .map(scored -> (Action) scored.attack())
        .orElseGet(Action.EndAttack::new);
  }

  private Action fortify(RiskEnv env, List<Action> legalActions) {
    var fortifications = legalActions.stream()
        .filter(action -> action instanceof Action.Fortify)
        .map(action -> (Action.Fortify) action)
        // from the interior
        .filter(fortify -> ! bordersEnemy(env, territory(env, fortify.from())));
    if (params.fortify() == Fortify.ANY_BORDER) {
      return fortifications
          .filter(fortify -> bordersEnemy(env, territory(env, fortify.to())))
          .max(Comparator
              .comparing((Action.Fortify fortify) -> fortify.numArmies())
              .thenComparing(fortify -> -fortify.from())
              .thenComparing(fortify -> -fortify.to()))
          .map(fortify -> (Action) fortify)
          .orElseGet(Action.SkipFortify::new);
    }
    return fortifications
        .filter(fortify -> deficit(env, territory(env, fortify.to())) > 0)
        .max(Comparator
            .comparing((Action.Fortify fortify) -> deficit(env, territory(env, fortify.to())))
            .thenComparing(fortify -> fortify.numArmies())
            .thenComparing(fortify -> -fortify.from())
            .thenComparing(fortify -> -fortify.to()))
        .map(fortify -> (Action) fortify)
        .orElseGet(Action.SkipFortify::new);
  }
}
