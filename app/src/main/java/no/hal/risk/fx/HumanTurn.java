package no.hal.risk.fx;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import no.hal.risk.Territory;
import no.hal.risk.TurnState;
import no.hal.risk.env.Action;
import no.hal.risk.env.Phase;
import no.hal.risk.env.RiskEnv;

/**
 * What a human player's presses on territories mean, in each phase of a turn, without any user interface widgets.
 * A quick press does the default action, which is the last of the steps below, and the steps are also what a menu gives variants of:
 * <ul>
 * <li>reinforcing: a press on one of your territories places an army, and the default is to place all the armies that are left</li>
 * <li>attacking: a press on one of your territories (with at least 2 armies) selects the attacker, and a press on an adjacent enemy territory attacks it.
 *   The default is the same attack, followed by moving in all the armies that can move</li>
 * <li>fortifying: a press on one of your territories selects where to move armies from, and a press on another one where to,
 *   and the number of armies is chosen separately. The default is to move as many armies as possible to the target</li>
 * </ul>
 * When the reinforcements have been placed, the territory that got the last one is selected to attack from.
 * A press either gives an action to perform, changes the selection, or gives a message about why it didn't do anything.
 */
public class HumanTurn {

  /** @param action the action to perform, or null @param message why nothing happened, or null */
  public record Click(Action action, String message) {
  }

  public enum Selection {
    SOURCE, TARGET
  }

  private final RiskEnv env;
  private Territory from;
  private Territory to;
  private Territory lastReinforced;
  private long stateKey = -1;

  public HumanTurn(RiskEnv env) {
    this.env = env;
  }

  /** The selection is cleared when the phase or the player changes. */
  private void clearSelectionIfStateChanged() {
    long key = (long) env.turns() * 10 + env.phase().ordinal();
    if (key != stateKey) {
      boolean afterReinforcing = stateKey == (long) env.turns() * 10 + Phase.REINFORCING.ordinal();
      stateKey = key;
      from = null;
      to = null;
      // the territory that got the last army is the one to attack from, unless it can't
      if (afterReinforcing && env.phase() == Phase.ATTACKING && lastReinforced != null
          && lastReinforced.occupant() == env.currentPlayer() && lastReinforced.numArmies() >= 2) {
        from = lastReinforced;
      }
    }
  }

  /**
   * To be called after an action has been performed, to drop a selection that is no longer valid.
   * After a conquest, the armies have moved into the conquered territory, so that is selected to attack from next.
   */
  public void afterAction(Action performed) {
    if (performed instanceof Action.Reinforce(var index)) {
      lastReinforced = env.territories().get(index);
    }
    clearSelectionIfStateChanged();
    if (performed instanceof Action.Attack(var attackerIndex, var defenderIndex) && env.phase() == Phase.ATTACKING) {
      var defender = env.territories().get(defenderIndex);
      if (defender.occupant() == env.currentPlayer()) {
        from = defender.numArmies() >= 2 ? defender : null;
        to = null;
        return;
      }
    }
    if (from != null && (from.occupant() != env.currentPlayer() || from.numArmies() < 2 && env.phase() != Phase.REINFORCING)) {
      from = null;
      to = null;
    }
    if (to != null && to.occupant() != env.currentPlayer()) {
      to = null;
    }
  }

  public void clearSelection() {
    from = null;
    to = null;
  }

  public Territory selectedFrom() {
    clearSelectionIfStateChanged();
    return from;
  }

  public Territory selectedTo() {
    clearSelectionIfStateChanged();
    return to;
  }

  /** The territories that are selected, for emphasizing them. */
  public Map<Territory, Selection> selection() {
    clearSelectionIfStateChanged();
    var selection = new LinkedHashMap<Territory, Selection>();
    if (from != null) {
      selection.put(from, Selection.SOURCE);
    }
    if (to != null) {
      selection.put(to, Selection.TARGET);
    }
    return selection;
  }

  /** The most armies that can be moved from the selected territory when fortifying, 0 if none is selected. */
  public int maxFortifyArmies() {
    clearSelectionIfStateChanged();
    return env.phase() == Phase.FORTIFYING && from != null ? env.risk().maxFortifyNum(from) : 0;
  }

  /** The fortification of the given number of armies between the selected territories, or null if they aren't selected or the number is wrong. */
  public Action fortify(int numArmies) {
    clearSelectionIfStateChanged();
    if (env.phase() != Phase.FORTIFYING || from == null || to == null || numArmies < 1 || numArmies > maxFortifyArmies()) {
      return null;
    }
    return new Action.Fortify(env.territoryIndex(from), env.territoryIndex(to), numArmies);
  }

  // the rest of the default actions, after the step

  /** All the armies that are left to place, on the territory. Nothing if it isn't one of yours, or it isn't the time to reinforce. */
  public List<Action> reinforceAll(Territory territory) {
    clearSelectionIfStateChanged();
    if (env.isDone() || env.phase() != Phase.REINFORCING || ! isMine(territory)
        || ! (env.risk().currentTurnState() instanceof TurnState.Reinforcing reinforcing)) {
      return List.of();
    }
    return Collections.nCopies(reinforcing.totalArmies(), new Action.Reinforce(env.territoryIndex(territory)));
  }

  /** Whether the territory is one to move the armies of the selected source to. */
  public boolean canFortifyTo(Territory territory) {
    clearSelectionIfStateChanged();
    return env.phase() == Phase.FORTIFYING && from != null && territory != from
        && env.risk().connectedTerritories(from, true).contains(territory);
  }

  /** Moves as many armies as possible to the territory, if it is the selected target, or null. */
  public Action fortifyMax(Territory target) {
    clearSelectionIfStateChanged();
    return to == target ? fortify(maxFortifyArmies()) : null;
  }

  private boolean isMine(Territory territory) {
    return territory.occupant() == env.currentPlayer();
  }

  public Click click(Territory territory) {
    clearSelectionIfStateChanged();
    if (env.isDone()) {
      return new Click(null, "The game is over");
    }
    return switch (env.phase()) {
      case REINFORCING -> clickWhenReinforcing(territory);
      case ATTACKING -> clickWhenAttacking(territory);
      case FORTIFYING -> clickWhenFortifying(territory);
      case GAME_OVER -> new Click(null, "The game is over");
    };
  }

  private Click clickWhenReinforcing(Territory territory) {
    if (! isMine(territory)) {
      return new Click(null, "Place armies on your own territories");
    }
    return new Click(new Action.Reinforce(env.territoryIndex(territory)), null);
  }

  private Click clickWhenAttacking(Territory territory) {
    if (isMine(territory)) {
      if (territory == from) {
        from = null;
        return new Click(null, null);
      }
      if (territory.numArmies() < 2) {
        return new Click(null, "%s has only one army, and needs at least two to attack".formatted(territory.name()));
      }
      from = territory;
      return new Click(null, null);
    }
    if (from == null) {
      return new Click(null, "Press one of your territories to attack from first");
    }
    if (! env.risk().areAdjacent(from, territory)) {
      return new Click(null, "%s does not border %s".formatted(from.name(), territory.name()));
    }
    return new Click(new Action.Attack(env.territoryIndex(from), env.territoryIndex(territory)), null);
  }

  private Click clickWhenFortifying(Territory territory) {
    if (! isMine(territory)) {
      return new Click(null, "Armies can only be moved between your own territories");
    }
    if (territory == from) {
      from = null;
      to = null;
      return new Click(null, null);
    }
    if (from != null && env.risk().connectedTerritories(from, true).contains(territory)) {
      to = territory;
      return new Click(null, null);
    }
    if (territory.numArmies() < 2) {
      return new Click(null, "%s has only one army, which has to stay".formatted(territory.name()));
    }
    if (env.risk().connectedTerritories(territory, true).isEmpty()) {
      return new Click(null, "%s has no territory of yours to move armies to".formatted(territory.name()));
    }
    from = territory;
    to = null;
    return new Click(null, null);
  }

  /** What the player can do now. */
  public String instructions() {
    clearSelectionIfStateChanged();
    if (env.isDone()) {
      return "The game is over";
    }
    return switch (env.phase()) {
      case REINFORCING -> {
        int left = env.risk().currentTurnState() instanceof TurnState.Reinforcing reinforcing ? reinforcing.totalArmies() : 0;
        yield "Reinforce: %d armies left. Press your territories to place all of them, or hold to choose how many.".formatted(left);
      }
      case ATTACKING -> from == null
          ? "Attack: press one of your territories with 2 or more armies, then an adjacent enemy territory. Press Enter, or hold on a territory and choose End attack, when done."
          : "Attack from %s (%d armies): press an adjacent enemy territory to attack, or hold on it to also choose how many armies to move in after a conquest (if the rule is on). Press another territory of yours to attack from there instead.".formatted(from.name(), from.numArmies());
      case FORTIFYING -> from == null
          ? "Fortify: press a territory to move armies from, then one to move them to. Or press Enter, or hold on a territory and choose Skip fortify."
          : to == null
              ? "Move armies from %s (up to %d): press the territory to move them to.".formatted(from.name(), env.risk().maxFortifyNum(from))
              : "Move armies from %s to %s: press %2$s again to move as many as possible, or hold on it to choose how many.".formatted(from.name(), to.name());
      case GAME_OVER -> "The game is over";
    };
  }
}
