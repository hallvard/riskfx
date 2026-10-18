package no.hal.risk.training;

import no.hal.risk.ai.*;

import java.util.ArrayList;
import java.util.List;
import no.hal.risk.RiskConfig;
import no.hal.risk.Territory;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * The decisions of the greedy bot, to learn from: to get a start, or to see what the network can represent
 * (the learner in Python trains the network to choose the same actions, and to predict the value, the rewards that greedy's games got).
 *
 * <p>Greedy breaks ties by the index of the territory, which no network can know, so a choice is also
 * counted as acceptable if it has the same <em>key</em> as greedy's: the same kind of territory and number of
 * armies when reinforcing, the same army lead when attacking, the same number of armies when fortifying.
 */
public class Imitation {

  public enum Kind {
    REINFORCE, ATTACK, END_ATTACK, FORTIFY, SKIP
  }

  /**
   * One decision of the greedy bot. {@code acceptable} are the positions of all legal actions with the same key as the chosen.
   * The value target is the discounted sum of the rewards the player got from this decision to the end of the game.
   */
  public record Sample(Observation observation, int[] legal, int action, int[] acceptable, Kind kind, float valueTarget) {
  }

  // collecting

  private static boolean bordersEnemy(RiskEnv env, Territory territory) {
    return ! env.risk().connectedTerritories(territory, false).isEmpty();
  }

  private static String key(RiskEnv env, Action action) {
    var territories = env.territories();
    return switch (action) {
      case Action.Reinforce(var index) -> "R" + (bordersEnemy(env, territories.get(index)) ? 1 : 0) + ":" + territories.get(index).numArmies();
      case Action.Attack(var from, var to) -> "A" + (territories.get(from).numArmies() - territories.get(to).numArmies());
      case Action.EndAttack _ -> "E";
      case Action.Fortify(var from, var to, var numArmies) ->
          "F" + (! bordersEnemy(env, territories.get(from)) && bordersEnemy(env, territories.get(to)) ? 1 : 0) + ":" + numArmies;
      case Action.SkipFortify _ -> "S";
    };
  }

  private static Kind kind(Action action) {
    return switch (action) {
      case Action.Reinforce _ -> Kind.REINFORCE;
      case Action.Attack _ -> Kind.ATTACK;
      case Action.EndAttack _ -> Kind.END_ATTACK;
      case Action.Fortify _ -> Kind.FORTIFY;
      case Action.SkipFortify _ -> Kind.SKIP;
    };
  }

  /** A decision whose value target is not known until the game is over. */
  private static class Pending {
    final Observation observation;
    final int[] legal;
    final int action;
    final int[] acceptable;
    final Kind kind;
    double reward;

    Pending(Observation observation, int[] legal, int action, int[] acceptable, Kind kind) {
      this.observation = observation;
      this.legal = legal;
      this.action = action;
      this.acceptable = acceptable;
      this.kind = kind;
    }
  }

  /**
   * Plays games between greedy bots and records all decisions of both players, with the games seeded
   * {@code seed}, {@code seed + 1} etc. The rewards, and so the value targets, are those of the reward function.
   */
  public static List<Sample> collect(RiskConfig config, int games, long seed, Reward reward) {
    var env = new RiskEnv(config);
    var encoder = new ObservationEncoder(env);
    var space = new ActionSpace(env.territories().size());
    var bot = new GreedyBot();
    int players = config.playerCount();
    var tracker = new RewardTracker(reward, players);
    double gamma = reward.config().gamma();
    var samples = new ArrayList<Sample>();
    for (int game = 0; game < games; game++) {
      env.reset(seed + game);
      tracker.reset();
      var trajectories = new ArrayList<List<Pending>>();
      for (int player = 0; player < players; player++) {
        trajectories.add(new ArrayList<>());
      }
      while (! env.isDone()) {
        var trajectory = trajectories.get(env.playerIndex(env.currentPlayer()));
        double previousReward = tracker.onDecision(env);
        if (! trajectory.isEmpty()) {
          trajectory.getLast().reward = previousReward;
        }
        var legal = env.legalActions();
        var action = bot.chooseAction(env, legal);
        var chosenKey = key(env, action);
        var acceptable = legal.stream().filter(candidate -> key(env, candidate).equals(chosenKey)).mapToInt(space::index).toArray();
        trajectory.add(new Pending(encoder.encode(env.currentPlayer()), legal.stream().mapToInt(space::index).toArray(),
            space.index(action), acceptable, kind(action)));
        env.step(action);
      }
      var lastRewards = tracker.onGameEnd(env);
      for (int player = 0; player < players; player++) {
        var trajectory = trajectories.get(player);
        if (trajectory.isEmpty()) {
          continue;
        }
        trajectory.getLast().reward = lastRewards[player];
        var targets = new float[trajectory.size()];
        double discounted = 0;
        for (int k = trajectory.size() - 1; k >= 0; k--) {
          discounted = trajectory.get(k).reward + gamma * discounted;
          targets[k] = (float) discounted;
        }
        for (int k = 0; k < targets.length; k++) {
          var pending = trajectory.get(k);
          samples.add(new Sample(pending.observation, pending.legal, pending.action, pending.acceptable, pending.kind, targets[k]));
        }
      }
    }
    return samples;
  }
}
