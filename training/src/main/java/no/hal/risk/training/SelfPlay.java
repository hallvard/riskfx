package no.hal.risk.training;

import no.hal.risk.ai.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.RiskEnv;

/**
 * Plays games with the network, to collect experience or to measure how good it is.
 * Several games are played in lockstep, so the network can decide for all of them in one batch.
 * Seats not played by the network are played by a greedy bot.
 */
public class SelfPlay {

  public record Config(RiskConfig risk, int maxTurns, int parallelGames, double botSeatProbability, double lambda, long seed) {

    public static Config of(RiskConfig risk) {
      return new Config(risk, RiskEnv.defaultMaxTurns(risk.playerCount()), 8, 0.25, 0.95, 1);
    }
  }

  /**
   * @param wins games won by the network
   * @param truncated games without a winner
   * @param eliminationsPerGame the average number of opponents that the network eliminated, by its own attacks
   */
  public record Evaluation(int games, int wins, int truncated, double averageTurns, double eliminationsPerGame) {

    public double winRate() {
      return (double) wins / games;
    }

    @Override
    public String toString() {
      return "%d games: won %d (%.0f%%), truncated %d, %.1f turns/game, %.2f opponents eliminated/game".formatted(
          games, wins, 100 * winRate(), truncated, averageTurns, eliminationsPerGame);
    }
  }

  private final PolicyNetwork net;
  private final Reward reward;
  private final Config config;
  private final ActionSpace actionSpace;
  private final Random random;
  /** The bots that take the seats that the network doesn't. */
  private List<Bot> opponents = List.of(new GreedyBot());
  /** Training games continue the series of seeds, so no board is played twice. */
  private long nextTrainingSeed;
  /** Evaluations always play the same boards, to be comparable. */
  private static final long EVALUATION_SEED = 987_654_321L;

  public SelfPlay(PolicyNetwork net, Reward reward, Config config) {
    this.net = net;
    this.reward = reward;
    this.config = config;
    this.actionSpace = new ActionSpace(net.numTerritories());
    this.random = new Random(config.seed());
    this.nextTrainingSeed = config.seed() * 1_000_003;
  }

  /**
   * The bots to play against, instead of greedy bots. In training games, each such seat gets one of them at random,
   * and when measuring, they are taken in turn, so each is met as often as the others.
   */
  public SelfPlay opponents(List<Bot> opponents) {
    if (opponents.isEmpty()) {
      throw new IllegalArgumentException("There must be at least one opponent");
    }
    this.opponents = List.copyOf(opponents);
    return this;
  }

  private final class Game {
    final RiskEnv env = new RiskEnv(config.risk(), config.maxTurns());
    final ObservationEncoder encoder = new ObservationEncoder(env);
    final RewardTracker tracker = new RewardTracker(reward, config.risk().playerCount());
    boolean active;
    int networkEliminations;
    boolean[] networkSeats;
    Bot[] seatBots;
    List<List<Rollout.Step>> trajectories;

    void start(long seed, boolean[] networkSeats, int gameIndex, boolean randomOpponents) {
      env.reset(seed);
      tracker.reset();
      this.networkSeats = networkSeats;
      seatBots = new Bot[networkSeats.length];
      // when measuring, the opponents are taken in turn, from game to game and from seat to seat
      int opponentSeat = 0;
      for (int seat = 0; seat < seatBots.length; seat++) {
        if (! networkSeats[seat]) {
          seatBots[seat] = opponents.get(randomOpponents ? random.nextInt(opponents.size()) : (gameIndex + opponentSeat++) % opponents.size());
        }
      }
      networkEliminations = 0;
      trajectories = new ArrayList<>();
      for (int seat = 0; seat < networkSeats.length; seat++) {
        trajectories.add(new ArrayList<>());
      }
      active = true;
    }

    boolean networkToMove() {
      return networkSeats[env.playerIndex(env.currentPlayer())];
    }
  }

  /**
   * Plays games where each seat is the network, or a greedy bot with the configured probability,
   * and records the decisions of the network, with the sampled actions of the current policy.
   */
  public Rollout collect(int games) {
    var rollout = new Rollout();
    int players = config.risk().playerCount();
    play(games, rollout, game -> {
      var seats = new boolean[players];
      boolean any = false;
      for (int seat = 0; seat < players; seat++) {
        seats[seat] = random.nextDouble() >= config.botSeatProbability();
        any |= seats[seat];
      }
      if (! any) {
        seats[random.nextInt(players)] = true;
      }
      return seats;
    }, true, null, nextTrainingSeed);
    nextTrainingSeed += games;
    return rollout;
  }

  /**
   * Plays the network against greedy bots, with the network in a different seat in each game,
   * choosing the most probable action, and returns how often the network wins.
   */
  public Evaluation evaluate(int games) {
    return evaluate(games, EVALUATION_SEED);
  }

  /** As {@link #evaluate(int)}, on the boards seeded {@code boardSeed}, {@code boardSeed + 1} etc. */
  public Evaluation evaluate(int games, long boardSeed) {
    int players = config.risk().playerCount();
    int[] counts = new int[4];
    var turns = new long[1];
    play(games, null, game -> {
      var seats = new boolean[players];
      seats[game % players] = true;
      return seats;
    }, false, (won, truncated, gameTurns, eliminations) -> {
      counts[0] += won ? 1 : 0;
      counts[1] += truncated ? 1 : 0;
      counts[3] += eliminations;
      turns[0] += gameTurns;
    }, boardSeed);
    return new Evaluation(games, counts[0], counts[1], (double) turns[0] / games, (double) counts[3] / games);
  }

  private interface SeatChooser {
    boolean[] seats(int gameIndex);
  }

  private interface ResultListener {
    void gameFinished(boolean networkWon, boolean truncated, int turns, int networkEliminations);
  }

  private void play(int totalGames, Rollout rollout, SeatChooser seatChooser, boolean sample, ResultListener listener, long seedBase) {
    var slots = new ArrayList<Game>();
    for (int i = 0; i < Math.min(config.parallelGames(), totalGames); i++) {
      slots.add(new Game());
    }
    int started = 0, finished = 0;
    while (finished < totalGames) {
      for (var game : slots) {
        if (! game.active && started < totalGames) {
          game.start(seedBase + started, seatChooser.seats(started), started, sample);
          started++;
        }
      }
      var waiting = new ArrayList<Game>();
      for (var game : slots) {
        if (! game.active) {
          continue;
        }
        var env = game.env;
        while (! env.isDone() && ! game.networkToMove()) {
          env.step(game.seatBots[env.playerIndex(env.currentPlayer())].chooseAction(env, env.legalActions()));
        }
        if (env.isDone()) {
          finish(game, rollout, listener);
          finished++;
        } else {
          waiting.add(game);
        }
      }
      if (! waiting.isEmpty()) {
        decide(waiting, rollout != null, sample);
      }
    }
  }

  /** The network chooses an action in each of the games, which all have a network seat to move. */
  private void decide(List<Game> games, boolean record, boolean sample) {
    var observations = new ArrayList<Observation>();
    for (var game : games) {
      if (record) {
        int seat = game.env.playerIndex(game.env.currentPlayer());
        var trajectory = game.trajectories.get(seat);
        double previousReward = game.tracker.onDecision(game.env);
        if (! trajectory.isEmpty()) {
          trajectory.getLast().reward = (float) previousReward;
        }
      }
      observations.add(game.encoder.encode(game.env.currentPlayer()));
    }
    var evaluation = net.evaluate(observations);
    for (int i = 0; i < games.size(); i++) {
      var game = games.get(i);
      var legalActions = game.env.legalActions();
      var legal = new int[legalActions.size()];
      for (int a = 0; a < legal.length; a++) {
        legal[a] = actionSpace.index(legalActions.get(a));
      }
      float[] logits = evaluation.logits()[i];
      var probabilities = Policies.probabilities(logits, legal);
      int chosen = sample ? sampleIndex(probabilities) : argMax(probabilities);
      if (record) {
        int seat = game.env.playerIndex(game.env.currentPlayer());
        game.trajectories.get(seat).add(new Rollout.Step(observations.get(i), legal, legal[chosen],
            (float) Math.log(probabilities[chosen]), evaluation.values()[i]));
      }
      var action = legalActions.get(chosen);
      int aliveBefore = action instanceof no.hal.risk.env.Action.Attack ? game.env.risk().alivePlayers().size() : 0;
      game.env.step(action);
      if (action instanceof no.hal.risk.env.Action.Attack) {
        game.networkEliminations += aliveBefore - game.env.risk().alivePlayers().size();
      }
    }
  }

  private void finish(Game game, Rollout rollout, ResultListener listener) {
    var env = game.env;
    game.active = false;
    int winnerSeat = env.winner() != null ? env.playerIndex(env.winner()) : -1;
    boolean hadBots = false;
    for (boolean networkSeat : game.networkSeats) {
      hadBots |= ! networkSeat;
    }
    boolean networkWon = winnerSeat >= 0 && game.networkSeats[winnerSeat];
    if (rollout != null) {
      var lastRewards = game.tracker.onGameEnd(env);
      for (int seat = 0; seat < game.trajectories.size(); seat++) {
        var trajectory = game.trajectories.get(seat);
        if (! trajectory.isEmpty()) {
          trajectory.getLast().reward = (float) lastRewards[seat];
          Rollout.estimateAdvantages(trajectory, reward.config().gamma(), config.lambda());
          rollout.addTrajectory(trajectory);
        }
      }
      rollout.addGame(env.turns(), env.isTruncated(), hadBots, networkWon, game.networkEliminations);
    }
    if (listener != null) {
      listener.gameFinished(networkWon, env.isTruncated(), env.turns(), game.networkEliminations);
    }
  }

  // choosing among the legal actions

  private int sampleIndex(double[] probabilities) {
    double target = random.nextDouble();
    double cumulative = 0;
    for (int i = 0; i < probabilities.length; i++) {
      cumulative += probabilities[i];
      if (target < cumulative) {
        return i;
      }
    }
    return probabilities.length - 1;
  }

  private static int argMax(double[] probabilities) {
    int best = 0;
    for (int i = 1; i < probabilities.length; i++) {
      if (probabilities[i] > probabilities[best]) {
        best = i;
      }
    }
    return best;
  }
}
