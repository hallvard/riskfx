package no.hal.risk.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import no.hal.risk.RiskConfig;
import no.hal.risk.ai.*;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NetworkBotTest {

  private RiskEnv env;
  private JavaPolicyNetwork net;

  @BeforeEach
  public void setUp() {
    env = new RiskEnv(new RiskConfig(2), 150);
    net = new JavaPolicyNetwork(RandomWeights.create(RandomWeights.config(16, 2, 8), 1, 0.3), new ObservationEncoder(env).adjacency());
  }

  @Test
  public void testNetworkBotChoosesLegalActions() {
    for (boolean sample : new boolean[] {false, true}) {
      var bot = new NetworkBot(net, "AI", sample, 5);
      env.reset(3);
      while (! env.isDone()) {
        var legal = env.legalActions();
        var action = bot.chooseAction(env, legal);
        assertTrue(legal.contains(action), "chose " + action + " in phase " + env.phase());
        env.step(action);
      }
    }
  }

  @Test
  public void testNetworkBotWithoutSamplingIsDeterministic() {
    var bot1 = new NetworkBot(net, "AI", false, 1);
    var bot2 = new NetworkBot(net, "AI", false, 99);
    env.reset(4);
    for (int i = 0; i < 30; i++) {
      var legal = env.legalActions();
      var action = bot1.chooseAction(env, legal);
      assertEquals(action, bot2.chooseAction(env, legal));
      env.step(action);
    }
  }

  @Test
  public void testNetworkBotWithSamplingVaries() {
    var bot = new NetworkBot(net, "AI", true, 7);
    env.reset(4);
    var legal = env.legalActions();
    // an untrained network is close to uniform, so many different reinforcements are chosen
    var chosen = new java.util.HashSet<Action>();
    for (int i = 0; i < 40; i++) {
      chosen.add(bot.chooseAction(env, legal));
    }
    assertTrue(chosen.size() > 3);
  }

  @Test
  public void testPlayingAWholeGame() {
    var match = new BotMatch(env, List.of(new GreedyBot(), new NetworkBot(net)));
    match.reset(2, List.of(new GreedyBot(), new NetworkBot(net)));
    var moves = new ArrayList<BotMatch.Move>();
    while (! match.isDone()) {
      assertEquals("In progress", match.result());
      var player = env.currentPlayer();
      var move = match.step();
      assertEquals(player, move.player());
      assertNotNull(move.description());
      assertFalse(move.description().isBlank());
      assertEquals(match.bot(player), move.bot());
      moves.add(move);
    }
    assertTrue(moves.size() > 20);
    assertEquals(1, moves.getFirst().turn());
    assertTrue(moves.getLast().turn() <= 150);
    assertFalse(match.result().isBlank());
    assertThrows(IllegalStateException.class, match::step);
  }
}
