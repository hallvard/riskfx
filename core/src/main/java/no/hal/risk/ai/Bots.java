package no.hal.risk.ai;

import java.util.ArrayList;
import java.util.List;

/** The rule-based bots, by name. */
public class Bots {

  public static final List<String> NAMES = List.of("greedy", "cautious");

  public static Bot byName(String name) {
    return switch (name.strip().toLowerCase()) {
      case "greedy" -> new GreedyBot();
      case "cautious" -> new CautiousBot();
      default -> throw new IllegalArgumentException("Unknown bot %s, there are %s".formatted(name, NAMES));
    };
  }

  /** Bots from a list of names, separated by commas, e.g. "greedy,cautious". */
  public static List<Bot> byNames(String names) {
    var bots = new ArrayList<Bot>();
    for (var name : names.split(",")) {
      bots.add(byName(name));
    }
    return bots;
  }
}
