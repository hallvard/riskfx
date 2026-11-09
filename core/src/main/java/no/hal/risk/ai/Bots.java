package no.hal.risk.ai;

import java.util.ArrayList;
import java.util.List;

/** The rule-based bots, by name. */
public class Bots {

  /**
   * The names of the bots. Greedy and cautious are the first two, which the others are variations of, in the settings of
   * {@link ParametrizedBot}: turtle only attacks where it can hold what it takes against all that could attack it, rusher attacks with the smallest lead, denier breaks the continents that
   * opponents hold, builder completes its own, opportunist stops after three conquests in a turn, and erratic is greedy with some noise.
   */
  public static final List<String> NAMES = List.of("greedy", "cautious", "turtle", "rusher", "denier", "builder", "opportunist", "erratic");

  public static Bot byName(String name) {
    return switch (name.strip().toLowerCase()) {
      case "greedy" -> new GreedyBot();
      case "cautious" -> new CautiousBot();
      case "turtle" -> new ParametrizedBot("turtle", new ParametrizedBot.Params(2, 1.0, 0, Integer.MAX_VALUE, true,
          ParametrizedBot.Reinforce.STRONGEST_BORDER, ParametrizedBot.Fortify.ANY_BORDER));
      case "rusher" -> new ParametrizedBot("rusher", GreedyBot.params(1));
      case "denier" -> new ParametrizedBot("denier", new ParametrizedBot.Params(2, 0.3, 0, Integer.MAX_VALUE, false,
          ParametrizedBot.Reinforce.STRONGEST_BORDER, ParametrizedBot.Fortify.ANY_BORDER).withTargets(1.0, 2.0, 3.0));
      case "builder" -> new ParametrizedBot("builder", new ParametrizedBot.Params(2, 0.5, 0, 12, true,
          ParametrizedBot.Reinforce.WEAKEST_BORDER, ParametrizedBot.Fortify.ANY_BORDER).withTargets(3.0, 0.5, 2.0));
      case "opportunist" -> new ParametrizedBot("opportunist", GreedyBot.params(2).withLimits(Integer.MAX_VALUE, 3));
      case "erratic" -> new ParametrizedBot("erratic", GreedyBot.params(2).withNoise(3.0));
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
