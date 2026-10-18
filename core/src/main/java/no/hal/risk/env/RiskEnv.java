package no.hal.risk.env;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import no.hal.risk.Player;
import no.hal.risk.Risk;
import no.hal.risk.RiskConfig;
import no.hal.risk.Territory;
import no.hal.risk.TurnState;

/**
 * A headless, seedable environment around {@link Risk}, e.g. for training players.
 * Actions are performed through the same {@link TurnState} methods as in the UI,
 * so there is only one set of rules. The caller asks for the current player and provides its action,
 * so the environment works for self-play.
 */
public class RiskEnv {

  public static int defaultMaxTurns(int playerCount) {
    return 60 * playerCount;
  }

  private final RiskConfig config;
  private final int maxTurns;
  private final Risk risk;
  private final List<Territory> territories;
  private final Map<Territory, Integer> territoryIndices = new IdentityHashMap<>();
  private final Map<Player, Integer> playerIndices = new IdentityHashMap<>();

  private int turns;

  public RiskEnv(RiskConfig config, int maxTurns) {
    this.config = config;
    this.maxTurns = maxTurns;
    this.risk = config.createRisk();
    this.territories = risk.allTerritories();
    for (int i = 0; i < territories.size(); i++) {
      territoryIndices.put(territories.get(i), i);
    }
    for (int i = 0; i < risk.players().size(); i++) {
      playerIndices.put(risk.players().get(i), i);
    }
    reset(new Random().nextLong());
  }

  public RiskEnv(RiskConfig config) {
    this(config, defaultMaxTurns(config.playerCount()));
  }

  /** Starts a new game. The same seed gives the same game, if the same actions are performed. */
  public void reset(long seed) {
    turns = 0;
    risk.reset(new Random(seed), config.initialArmiesPrTerritory());
  }

  // state access

  /** The underlying game, for reading the state. Don't change it directly. */
  public Risk risk() {
    return risk;
  }

  /** All territories, in a fixed order. */
  public List<Territory> territories() {
    return territories;
  }

  public int territoryIndex(Territory territory) {
    return territoryIndices.get(territory);
  }

  public List<Player> players() {
    return risk.players();
  }

  public int playerIndex(Player player) {
    return playerIndices.get(player);
  }

  public Player currentPlayer() {
    return risk.currentPlayer();
  }

  /** The number of finished player turns. */
  public int turns() {
    return turns;
  }

  public int maxTurns() {
    return maxTurns;
  }

  public Phase phase() {
    return switch (risk.currentTurnState()) {
      case TurnState.Reinforcing _ -> Phase.REINFORCING;
      case TurnState.Attacking _ -> Phase.ATTACKING;
      case TurnState.Fortifying _ -> Phase.FORTIFYING;
      case TurnState.GameOver _ -> Phase.GAME_OVER;
    };
  }

  public boolean isWon() {
    return risk.isGameOver();
  }

  public boolean isTruncated() {
    return ! isWon() && turns >= maxTurns;
  }

  public boolean isDone() {
    return isWon() || isTruncated();
  }

  /** The winner, or null if the game isn't won (yet). */
  public Player winner() {
    return risk.winner();
  }

  // legal actions

  /**
   * The legal actions of the current player, empty when the game is done.
   * Fortify only includes moving all armies that can be moved, but {@link #step(Action)} accepts any number from 1.
   */
  public List<Action> legalActions() {
    var actions = new ArrayList<Action>();
    if (isDone()) {
      return actions;
    }
    var player = currentPlayer();
    switch (phase()) {
      case REINFORCING -> {
        for (int i = 0; i < territories.size(); i++) {
          if (territories.get(i).occupant() == player) {
            actions.add(new Action.Reinforce(i));
          }
        }
      }
      case ATTACKING -> {
        for (int from = 0; from < territories.size(); from++) {
          var attacker = territories.get(from);
          if (attacker.occupant() == player && attacker.numArmies() >= 2) {
            for (var defender : risk.connectedTerritories(attacker, false)) {
              actions.add(new Action.Attack(from, territoryIndex(defender)));
            }
          }
        }
        actions.add(new Action.EndAttack());
      }
      case FORTIFYING -> {
        for (int from = 0; from < territories.size(); from++) {
          var source = territories.get(from);
          if (source.occupant() == player && source.numArmies() >= 2) {
            for (var target : risk.connectedTerritories(source, true)) {
              actions.add(new Action.Fortify(from, territoryIndex(target), risk.maxFortifyNum(source)));
            }
          }
        }
        actions.add(new Action.SkipFortify());
      }
      case GAME_OVER -> {
      }
    }
    return actions;
  }

  // performing actions

  private Territory territory(int index) {
    if (index < 0 || index >= territories.size()) {
      throw new IllegalArgumentException("No territory with index " + index);
    }
    return territories.get(index);
  }

  private void requirePhase(Action action, Phase expected) {
    if (phase() != expected) {
      throw new IllegalArgumentException("%s is not allowed in phase %s".formatted(action, phase()));
    }
  }

  /**
   * Performs an action for the current player. Illegal actions throw {@link IllegalArgumentException}
   * and leave the game unchanged.
   */
  public StepResult step(Action action) {
    if (isDone()) {
      throw new IllegalStateException("The game is over");
    }
    var actor = currentPlayer();
    switch (action) {
      case Action.Reinforce(var index) -> {
        requirePhase(action, Phase.REINFORCING);
        var reinforcing = (TurnState.Reinforcing) risk.currentTurnState();
        reinforcing.reinforce(risk, territory(index), 1);
      }
      case Action.Attack(var from, var to) -> {
        requirePhase(action, Phase.ATTACKING);
        var attacker = territory(from);
        var defender = territory(to);
        var attacking = (TurnState.Attacking) risk.currentTurnState();
        attacking.attack(risk, attacker, defender, risk.maxAttackerDies(attacker), risk.maxDefenderDies(defender));
      }
      case Action.EndAttack _ -> {
        requirePhase(action, Phase.ATTACKING);
        risk.nextTurnState();
      }
      case Action.Fortify(var from, var to, var numArmies) -> {
        requirePhase(action, Phase.FORTIFYING);
        var fortifying = (TurnState.Fortifying) risk.currentTurnState();
        fortifying.fortify(risk, territory(from), territory(to), numArmies);
        endTurn();
      }
      case Action.SkipFortify _ -> {
        requirePhase(action, Phase.FORTIFYING);
        endTurn();
      }
    }
    return new StepResult(actor, isDone(), winner(), isTruncated());
  }

  /**
   * Moves more armies into the territory just conquered, with {@link no.hal.risk.Rules#keepArmiesBack()}.
   * Not an {@link Action}, since only a human chooses; bots always move all the armies they can, see {@link #occupyAll()}.
   */
  public void occupy(int extra) {
    risk.occupy(extra);
  }

  /** Moves all the armies that can be moved into the territory just conquered, if any. */
  public void occupyAll() {
    var occupation = risk.pendingOccupation();
    if (occupation != null) {
      risk.occupy(occupation.maxExtra());
    }
  }

  private void endTurn() {
    turns++;
    risk.nextTurnState();
  }
}
