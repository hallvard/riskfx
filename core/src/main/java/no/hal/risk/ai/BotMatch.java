package no.hal.risk.ai;

import java.util.ArrayList;
import java.util.List;
import no.hal.risk.Player;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * A game played by bots, one action at a time, with a description of each action. For watching games.
 */
public class BotMatch {

  /**
   * @param turn the number of the turn, from 1
   * @param attackerLoss the armies the attacker lost, 0 if the move wasn't an attack
   * @param defenderLoss the armies the defender lost, 0 if the move wasn't an attack
   */
  public record Move(int turn, Player player, Bot bot, Action action, String description, int attackerLoss, int defenderLoss) {
  }

  private final RiskEnv env;
  private List<Bot> bots;

  /** @param botsBySeat the bot of each player, in the order of the players of the environment */
  public BotMatch(RiskEnv env, List<Bot> botsBySeat) {
    this.env = env;
    reset(1, botsBySeat);
  }

  public RiskEnv env() {
    return env;
  }

  public List<Bot> bots() {
    return bots;
  }

  public Bot bot(Player player) {
    return bots.get(env.playerIndex(player));
  }

  public void reset(long seed, List<Bot> botsBySeat) {
    if (botsBySeat.size() != env.players().size()) {
      throw new IllegalArgumentException("Need a bot for each of the %d players".formatted(env.players().size()));
    }
    this.bots = List.copyOf(botsBySeat);
    env.reset(seed);
  }

  public boolean isDone() {
    return env.isDone();
  }

  /** Describes how the game ended. */
  public String result() {
    if (! env.isDone()) {
      return "In progress";
    }
    if (env.winner() == null) {
      return "Draw: the limit of %d turns was reached".formatted(env.maxTurns());
    }
    return "%s (%s) won after %d turns".formatted(env.winner().name(), bot(env.winner()).name(), env.turns());
  }

  /**
   * Skips the phases where nothing can be done but ending them: the attack when no territory can attack,
   * and the fortification when no armies can be moved. Returns the actions that were performed, in order.
   */
  public List<Move> skipForcedPhases() {
    var moves = new ArrayList<Move>();
    while (! env.isDone()) {
      var legal = env.legalActions();
      boolean forced = legal.size() == 1 && (legal.getFirst() instanceof Action.EndAttack || legal.getFirst() instanceof Action.SkipFortify);
      if (! forced) {
        break;
      }
      moves.add(perform(legal.getFirst()));
    }
    return moves;
  }

  /** Whether the current player is a human, who chooses actions in the user interface. */
  public boolean isHumanTurn() {
    return ! env.isDone() && bot(env.currentPlayer()) instanceof HumanBot;
  }

  /** Lets the bot of the current player choose and perform an action. */
  public Move step() {
    if (env.isDone()) {
      throw new IllegalStateException("The game is over");
    }
    if (isHumanTurn()) {
      throw new IllegalStateException("It is the turn of a human, who must choose an action");
    }
    return perform(bot(env.currentPlayer()).chooseAction(env, env.legalActions()));
  }

  /**
   * Performs an action for the current player, whether chosen by a bot or by a human,
   * and describes it. An illegal action throws and leaves the game unchanged.
   */
  public Move perform(Action action) {
    if (env.isDone()) {
      throw new IllegalStateException("The game is over");
    }
    var player = env.currentPlayer();
    var bot = bot(player);
    int turn = env.turns() + 1;
    var territories = env.territories();
    String description;
    int attackerLoss = 0, defenderLoss = 0;
    switch (action) {
      case Action.Reinforce(var index) -> {
        var territory = territories.get(index);
        env.step(action);
        description = "reinforces %s, now %d armies".formatted(territory.name(), territory.numArmies());
      }
      case Action.Attack(var from, var to) -> {
        var attacker = territories.get(from);
        var defender = territories.get(to);
        int attackers = attacker.numArmies(), defenders = defender.numArmies();
        var defendingPlayer = defender.occupant();
        env.step(action);
        if (! (bot instanceof HumanBot)) {
          env.occupyAll();
        }
        boolean conquered = defender.occupant() == player;
        int moved = conquered ? defender.numArmies() : 0;
        attackerLoss = attackers - attacker.numArmies() - moved;
        defenderLoss = conquered ? defenders : defenders - defender.numArmies();
        description = "attacks %s (%d armies) from %s (%d armies): lost %d, defender lost %d%s".formatted(
            defender.name(), defenders, attacker.name(), attackers, attackerLoss, defenderLoss,
            conquered ? ", conquered it from %s with %d armies".formatted(defendingPlayer.name(), moved) : "");
      }
      case Action.EndAttack _ -> {
        env.step(action);
        description = "ends the attack";
      }
      case Action.Fortify(var from, var to, var numArmies) -> {
        env.step(action);
        description = "moves %d armies from %s to %s".formatted(numArmies, territories.get(from).name(), territories.get(to).name());
      }
      case Action.SkipFortify _ -> {
        env.step(action);
        description = "ends the turn without moving armies";
      }
    }
    return new Move(turn, player, bot, action, description, attackerLoss, defenderLoss);
  }
}
