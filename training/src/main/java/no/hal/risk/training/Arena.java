package no.hal.risk.training;

import no.hal.risk.ai.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.RiskEnv;

/**
 * Plays bots against each other and measures how often each wins.
 * Bots are rotated through the seats, since the first player may have an advantage.
 */
public class Arena {

  /**
   * @param names the names of the bots
   * @param wins the number of games won by each bot, in the order of the bots
   * @param winsBySeat the number of games won by each seat
   * @param games the number of games
   * @param truncated the number of games that hit the turn limit, without a winner
   * @param averageTurns the average number of player turns per game
   */
  public record Result(List<String> names, int[] wins, int[] winsBySeat, int games, int truncated, double averageTurns) {

    public double winRate(int bot) {
      return (double) wins[bot] / games;
    }

    @Override
    public String toString() {
      var out = new StringBuilder("%d games, %d truncated (%.0f%%), %.1f turns/game%n".formatted(
          games, truncated, 100.0 * truncated / games, averageTurns));
      for (int i = 0; i < wins.length; i++) {
        out.append("  %-12s wins %4d (%.1f%%)%n".formatted(names.get(i) + " #" + i, wins[i], 100.0 * winRate(i)));
      }
      out.append("  by seat: ").append(Arrays.toString(winsBySeat));
      return out.toString();
    }
  }

  private final RiskConfig config;
  private final int maxTurns;

  public Arena(RiskConfig config, int maxTurns) {
    this.config = config;
    this.maxTurns = maxTurns;
  }

  public Arena(RiskConfig config) {
    this(config, RiskEnv.defaultMaxTurns(config.playerCount()));
  }

  /**
   * Plays games with a bot per player, game {@code g} with seed {@code seed + g}.
   * In game {@code g}, bot {@code b} sits in seat {@code (b + g) % players}.
   */
  public Result play(List<Bot> bots, int games, long seed) {
    int players = config.playerCount();
    if (bots.size() != players) {
      throw new IllegalArgumentException("Need a bot for each of the %d players, got %d".formatted(players, bots.size()));
    }
    var env = new RiskEnv(config, maxTurns);
    int[] wins = new int[players];
    int[] winsBySeat = new int[players];
    int truncated = 0;
    long totalTurns = 0;
    for (int game = 0; game < games; game++) {
      env.reset(seed + game);
      var seats = new Bot[players];
      for (int bot = 0; bot < players; bot++) {
        seats[(bot + game) % players] = bots.get(bot);
      }
      while (! env.isDone()) {
        var bot = seats[env.playerIndex(env.currentPlayer())];
        env.step(bot.chooseAction(env, env.legalActions()));
      }
      totalTurns += env.turns();
      if (env.winner() == null) {
        truncated++;
      } else {
        int seat = env.playerIndex(env.winner());
        winsBySeat[seat]++;
        wins[(seat - game % players + players) % players]++;
      }
    }
    return new Result(bots.stream().map(Bot::name).toList(), wins, winsBySeat, games, truncated, (double) totalTurns / games);
  }

  /** Prints the results of the baseline bots, for 2 to 4 players. */
  public static void main(String[] args) {
    int games = args.length > 0 ? Integer.parseInt(args[0]) : 200;
    for (int players = 2; players <= 4; players++) {
      var arena = new Arena(new RiskConfig(players));
      int count = players;
      System.out.println("== %d players, random bots".formatted(players));
      System.out.println(arena.play(IntStream.range(0, count).mapToObj(i -> (Bot) new RandomBot(i)).toList(), games, 1000));
      System.out.println("== %d players, greedy bots".formatted(players));
      System.out.println(arena.play(IntStream.range(0, count).mapToObj(_ -> (Bot) new GreedyBot()).toList(), games, 1000));
      System.out.println("== %d players, one greedy against random bots".formatted(players));
      var bots = new ArrayList<Bot>(List.of(new GreedyBot()));
      for (int i = 1; i < players; i++) {
        bots.add(new RandomBot(i));
      }
      System.out.println(arena.play(bots, games, 1000));
    }
  }
}
