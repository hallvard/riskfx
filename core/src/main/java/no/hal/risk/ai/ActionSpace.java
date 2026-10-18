package no.hal.risk.ai;

import java.util.List;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * Maps actions to positions in a flat vector, so a network can output a score for every possible action
 * and a mask can say which of them are legal. With {@code n} territories the layout is:
 * <pre>
 * [0, n)                reinforce(territory)
 * [n, n + n*n)          attack(from, to), at n + from * n + to
 * n + n*n               end attack
 * [.., .. + n*n)        fortify(from, to), at from * n + to
 * last                  skip fortify
 * </pre>
 * Fortify always moves all movable armies, as in {@link RiskEnv#legalActions()}.
 */
public class ActionSpace {

  private final int n;

  public ActionSpace(int numTerritories) {
    this.n = numTerritories;
  }

  private int attackStart() {
    return n;
  }

  /** The position of ending the attack. */
  public int endAttackIndex() {
    return n + n * n;
  }

  private int fortifyStart() {
    return endAttackIndex() + 1;
  }

  /** The position of skipping the fortification. */
  public int skipFortifyIndex() {
    return fortifyStart() + n * n;
  }

  public int size() {
    return skipFortifyIndex() + 1;
  }

  public int index(Action action) {
    return switch (action) {
      case Action.Reinforce(var territory) -> territory;
      case Action.Attack(var from, var to) -> attackStart() + from * n + to;
      case Action.EndAttack _ -> endAttackIndex();
      case Action.Fortify(var from, var to, var _) -> fortifyStart() + from * n + to;
      case Action.SkipFortify _ -> skipFortifyIndex();
    };
  }

  /** The action at a position, which needs the environment to know how many armies a fortification moves. */
  public Action action(int index, RiskEnv env) {
    if (index < 0 || index >= size()) {
      throw new IllegalArgumentException("No action with index " + index);
    }
    if (index < attackStart()) {
      return new Action.Reinforce(index);
    } else if (index < endAttackIndex()) {
      int pair = index - attackStart();
      return new Action.Attack(pair / n, pair % n);
    } else if (index == endAttackIndex()) {
      return new Action.EndAttack();
    } else if (index < skipFortifyIndex()) {
      int pair = index - fortifyStart();
      int from = pair / n;
      return new Action.Fortify(from, pair % n, env.risk().maxFortifyNum(env.territories().get(from)));
    } else {
      return new Action.SkipFortify();
    }
  }

  /** True for the positions of the legal actions. */
  public boolean[] mask(List<Action> legalActions) {
    var mask = new boolean[size()];
    for (var action : legalActions) {
      mask[index(action)] = true;
    }
    return mask;
  }
}
