package no.hal.risk.ai;

import java.util.Comparator;
import java.util.List;
import no.hal.risk.Territory;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * A copy of GreedyBot as it was before it became a setting of {@link ParametrizedBot}, kept to check that the greedy bot still chooses
 * exactly the same actions. It is not used for anything else.
 */
public class ReferenceGreedyBot implements Bot {

  private final int minAdvantage;

  public ReferenceGreedyBot(int minAdvantage) {
    this.minAdvantage = minAdvantage;
  }

  public ReferenceGreedyBot() {
    this(2);
  }

  @Override
  public String name() {
    return "reference greedy";
  }

  private static boolean bordersEnemy(RiskEnv env, Territory territory) {
    return ! env.risk().connectedTerritories(territory, false).isEmpty();
  }

  private static Territory territory(RiskEnv env, int index) {
    return env.territories().get(index);
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
    // prefer territories bordering an enemy, then the one with the most armies, then the lowest index
    return legalActions.stream()
        .map(action -> (Action.Reinforce) action)
        .max(Comparator
            .comparing((Action.Reinforce action) -> bordersEnemy(env, territory(env, action.territory())))
            .thenComparing(action -> territory(env, action.territory()).numArmies())
            .thenComparing(action -> -action.territory()))
        .orElseThrow();
  }

  private Action attack(RiskEnv env, List<Action> legalActions) {
    return legalActions.stream()
        .filter(action -> action instanceof Action.Attack)
        .map(action -> (Action.Attack) action)
        .filter(attack -> advantage(env, attack) >= minAdvantage)
        .max(Comparator
            .comparing((Action.Attack attack) -> advantage(env, attack))
            .thenComparing(attack -> -attack.from())
            .thenComparing(attack -> -attack.to()))
        .map(attack -> (Action) attack)
        .orElseGet(Action.EndAttack::new);
  }

  private int advantage(RiskEnv env, Action.Attack attack) {
    return territory(env, attack.from()).numArmies() - territory(env, attack.to()).numArmies();
  }

  private Action fortify(RiskEnv env, List<Action> legalActions) {
    return legalActions.stream()
        .filter(action -> action instanceof Action.Fortify)
        .map(action -> (Action.Fortify) action)
        // from the interior to the front
        .filter(fortify -> ! bordersEnemy(env, territory(env, fortify.from())) && bordersEnemy(env, territory(env, fortify.to())))
        .max(Comparator
            .comparing((Action.Fortify fortify) -> fortify.numArmies())
            .thenComparing(fortify -> -fortify.from())
            .thenComparing(fortify -> -fortify.to()))
        .map(fortify -> (Action) fortify)
        .orElseGet(Action.SkipFortify::new);
  }
}
