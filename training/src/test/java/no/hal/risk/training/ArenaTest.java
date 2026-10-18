package no.hal.risk.training;

import no.hal.risk.ai.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

public class ArenaTest {

  @Test
  public void testGreedyBeatsRandom() {
    var result = new Arena(new RiskConfig(2)).play(List.of(new GreedyBot(), new RandomBot(1)), 40, 500);
    assertTrue(result.winRate(0) >= 0.9, "Greedy should beat random, but: " + result);
  }

  @Test
  public void testResultsAddUp() {
    var result = new Arena(new RiskConfig(3)).play(List.of(new GreedyBot(), new RandomBot(1), new RandomBot(2)), 30, 7);
    assertEquals(30, result.games());
    assertEquals(30, Arrays.stream(result.wins()).sum() + result.truncated());
    assertEquals(Arrays.stream(result.wins()).sum(), Arrays.stream(result.winsBySeat()).sum());
  }

  @Test
  public void testPlayIsRepeatable() {
    var arena = new Arena(new RiskConfig(3));
    var result1 = arena.play(List.of(new GreedyBot(), new GreedyBot(), new RandomBot(5)), 20, 11);
    var result2 = arena.play(List.of(new GreedyBot(), new GreedyBot(), new RandomBot(5)), 20, 11);
    assertArrayEquals(result1.wins(), result2.wins());
    assertArrayEquals(result1.winsBySeat(), result2.winsBySeat());
    assertEquals(result1.truncated(), result2.truncated());
    assertEquals(result1.averageTurns(), result2.averageTurns());
  }

  @Test
  public void testBotsAreRotatedThroughSeats() {
    // the same bot in every seat: wins by seat must be spread over several seats
    var result = new Arena(new RiskConfig(3)).play(List.of(new GreedyBot(), new GreedyBot(), new GreedyBot()), 60, 3);
    assertTrue(Arrays.stream(result.winsBySeat()).filter(wins -> wins > 0).count() >= 2, result.toString());
  }

  @Test
  public void testNeedsABotForEachPlayer() {
    assertThrows(IllegalArgumentException.class, () -> new Arena(new RiskConfig(3)).play(List.of(new GreedyBot()), 1, 0));
  }

  @Test
  public void testBotsOnlyChooseLegalActions() {
    var env = new RiskEnv(new RiskConfig(2), 100);
    for (Bot bot : List.of(new GreedyBot(), new RandomBot(3))) {
      env.reset(21);
      while (! env.isDone()) {
        var legal = env.legalActions();
        var action = bot.chooseAction(env, legal);
        assertTrue(legal.contains(action), bot.name() + " chose " + action + " in phase " + env.phase());
        env.step(action);
      }
    }
  }
}
