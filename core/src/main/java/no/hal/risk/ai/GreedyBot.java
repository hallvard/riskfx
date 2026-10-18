package no.hal.risk.ai;

import java.util.Comparator;
import java.util.List;
import no.hal.risk.Territory;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * A simple rule-based player, deterministic given the state:
 * <ul>
 * <li>reinforce: pile armies onto the strongest territory bordering an enemy</li>
 * <li>attack: attack where the attacker has the biggest advantage, as long as it has at least {@code minAdvantage} more armies</li>
 * <li>fortify: move the armies of the strongest interior territory to a territory bordering an enemy</li>
 * </ul>
 */
public class GreedyBot implements Bot {

  private final int minAdvantage;

  public GreedyBot(int minAdvantage) {
    this.minAdvantage = minAdvantage;
  }

  public GreedyBot() {
    this(2);
  }

  @Override
  public String name() {
    return "greedy";
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
