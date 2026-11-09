package no.hal.risk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import no.hal.risk.GameMap;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

public class ParametrizedBotTest {

  /** Pairs of a bot and the copy of how it was before it became a setting of ParametrizedBot. */
  private record Pair(String description, Bot bot, Bot reference) {
  }

  private static List<Pair> pairs() {
    var pairs = new ArrayList<Pair>();
    for (int minAdvantage : new int[] {1, 2, 4}) {
      pairs.add(new Pair("greedy " + minAdvantage, new GreedyBot(minAdvantage), new ReferenceGreedyBot(minAdvantage)));
    }
    pairs.add(new Pair("cautious", new CautiousBot(), new ReferenceCautiousBot()));
    for (var settings : new double[][] {{2, 8, 0, 0.7}, {3, 5, 1, 0.5}, {1, 20, 0, 1.0}, {4, 3, 2, 0.9}, {2, 1000, 0, 0.3}}) {
      pairs.add(new Pair("cautious " + java.util.Arrays.toString(settings),
          new CautiousBot((int) settings[0], (int) settings[1], (int) settings[2], settings[3]),
          new ReferenceCautiousBot((int) settings[0], (int) settings[1], (int) settings[2], settings[3])));
    }
    return pairs;
  }

  /**
   * Plays games where each player is the old greedy bot, the old cautious bot or a random mover, so there are all kinds of states,
   * including those that the bots themselves never get to, and at each decision, whoever makes it, checks that the new bots
   * choose what the old ones did.
   */
  @Test
  public void greedyAndCautiousChooseExactlyWhatTheyDidBeforeTheyWereSettings() {
    var pairs = pairs();
    int decisions = 0;
    for (var map : GameMap.all()) {
      for (int players = 2; players <= 4; players++) {
        for (int game = 0; game < 6; game++) {
          long seed = 1000L * players + game;
          var env = new RiskEnv(new RiskConfig(players).withMap(map), 40 * players);
          env.reset(seed);
          var random = new Random(seed);
          // a different mix of movers in each game, and a random one that doesn't choose well to get to other states
          var movers = new ArrayList<Bot>();
          for (int seat = 0; seat < players; seat++) {
            movers.add(switch ((seat + game) % 3) {
              case 0 -> new ReferenceGreedyBot();
              case 1 -> new ReferenceCautiousBot();
              default -> null;
            });
          }
          while (! env.isDone()) {
            var legal = env.legalActions();
            for (var pair : pairs) {
              assertEquals(pair.reference().chooseAction(env, legal), pair.bot().chooseAction(env, legal),
                  "%s, seed %d, turn %d, %s".formatted(pair.description(), seed, env.turns(), env.phase()));
              decisions++;
            }
            var mover = movers.get(env.playerIndex(env.currentPlayer()));
            // a mover that is a bot mostly, and a random action now and then, whoever it is
            Action action = mover == null || random.nextInt(6) == 0
                ? legal.get(random.nextInt(legal.size()))
                : mover.chooseAction(env, legal);
            env.step(action);
          }
        }
      }
    }
    assertTrue(decisions > 20_000, "only " + decisions + " decisions were compared");
  }

  @Test
  public void theBotsAreSettingsOfTheSameBot() {
    assertEquals(new ParametrizedBot.Params(2, 0.0, 0, Integer.MAX_VALUE, false,
        ParametrizedBot.Reinforce.STRONGEST_BORDER, ParametrizedBot.Fortify.ANY_BORDER), GreedyBot.params(2));
    assertEquals(new ParametrizedBot.Params(2, 0.7, 0, 8, true,
        ParametrizedBot.Reinforce.WEAKEST_BORDER, ParametrizedBot.Fortify.WEAK_BORDER), CautiousBot.params(2, 8, 0, 0.7));
    assertEquals("greedy", new GreedyBot().name());
    assertEquals("cautious", new CautiousBot().name());
  }
}
