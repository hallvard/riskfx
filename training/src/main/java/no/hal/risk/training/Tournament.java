package no.hal.risk.training;

import java.util.List;
import no.hal.risk.GameMap;
import no.hal.risk.RiskConfig;
import no.hal.risk.Rules;
import no.hal.risk.ai.Bot;
import no.hal.risk.ai.Bots;

/**
 * Plays each of the bots against each of the others, two players, on a map, and prints how often the one in each row beat the one in
 * each column, and how many games hit the turn limit. Seats are rotated, so being first is no advantage to any.
 *
 * <p>Arguments: the number of games for each pair (default 200), the map (classic or europe), the names of the bots (default all),
 * and "bonus" for the rules with a bonus after a conquest and for eliminating a player.
 */
public class Tournament {

  public static void main(String[] args) {
    int games = args.length > 0 ? Integer.parseInt(args[0]) : 200;
    var map = args.length > 1 && args[1].equalsIgnoreCase("europe") ? GameMap.EUROPE : GameMap.CLASSIC;
    var names = args.length > 2 ? List.of(args[2].split(",")) : Bots.NAMES;
    boolean bonus = args.length > 3 && args[3].equalsIgnoreCase("bonus");
    var arena = new Arena(new RiskConfig(2).withMap(map).withRules(new Rules(false, bonus, bonus)), 200);
    int n = names.size();
    double[][] wins = new double[n][n];
    double[][] draws = new double[n][n];
    for (int row = 0; row < n; row++) {
      for (int column = row + 1; column < n; column++) {
        var result = arena.play(List.<Bot>of(Bots.byName(names.get(row)), Bots.byName(names.get(column))), games, 5000);
        wins[row][column] = result.winRate(0);
        wins[column][row] = result.winRate(1);
        draws[row][column] = draws[column][row] = (double) result.truncated() / games;
      }
    }
    System.out.println("The map " + map.name() + (bonus ? ", with the bonuses" : "") + ", " + games + " games for each pair: the share of games that the bot in the row won against the one in the column");
    System.out.printf("%-12s", "");
    names.forEach(name -> System.out.printf("%12s", name));
    System.out.printf("%12s%n", "average");
    for (int row = 0; row < n; row++) {
      System.out.printf("%-12s", names.get(row));
      double sum = 0;
      for (int column = 0; column < n; column++) {
        if (row == column) {
          System.out.printf("%12s", "-");
        } else {
          System.out.printf("%11.0f%%", 100 * wins[row][column]);
          sum += wins[row][column];
        }
      }
      System.out.printf("%11.0f%%%n", 100 * sum / (n - 1));
    }
    System.out.println("Games that hit the turn limit, with no winner:");
    for (int row = 0; row < n; row++) {
      System.out.printf("%-12s", names.get(row));
      for (int column = 0; column < n; column++) {
        System.out.printf("%12s", row == column ? "-" : "%.0f%%".formatted(100 * draws[row][column]));
      }
      System.out.println();
    }
  }
}
