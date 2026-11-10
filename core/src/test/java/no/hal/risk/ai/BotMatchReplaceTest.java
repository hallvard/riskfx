package no.hal.risk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.Action;
import no.hal.risk.env.Phase;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

public class BotMatchReplaceTest {

  private RiskEnv env;
  private BotMatch match;

  private void start(Bot first, Bot second) {
    env = new RiskEnv(new RiskConfig(2), 200);
    match = new BotMatch(env, List.of(first, second));
    match.reset(3, List.of(first, second));
  }

  @Test
  public void aBotCanBeReplacedAndTheNewOneIsTheNextToPlay() {
    var cautious = new CautiousBot();
    start(new GreedyBot(), new GreedyBot());
    match.step();
    match.replaceBot(env.players().getFirst(), cautious);
    assertSame(cautious, match.bot(env.players().getFirst()));
    assertEquals("greedy", match.bot(env.players().get(1)).name());
    assertEquals("cautious", match.step().bot().name());
  }

  @Test
  public void aBotCanBeReplacedInTheMiddleOfATurnAndTheNewOneContinuesFromThere() {
    start(new GreedyBot(), new GreedyBot());
    while (env.phase() == Phase.REINFORCING) {
      match.step();
    }
    assertEquals(Phase.ATTACKING, env.phase());
    var player = env.currentPlayer();
    match.replaceBot(player, new CautiousBot());
    // it is the same turn of the same player, now played by the other bot, until the game is over
    int turns = env.turns();
    var first = match.step();
    assertEquals(player, first.player());
    assertEquals(turns, env.turns() - (env.currentPlayer() == player ? 0 : 1));
    assertEquals("cautious", first.bot().name());
    for (int steps = 0; steps < 5000 && ! env.isDone(); steps++) {
      match.step();
    }
    assertTrue(env.isDone());
  }

  @Test
  public void aHumanCanTakeOverFromABotAndGiveTheSeatBack() {
    start(new GreedyBot(), new GreedyBot());
    assertFalse(match.isHumanTurn());
    match.replaceBot(env.currentPlayer(), new HumanBot());
    assertTrue(match.isHumanTurn());
    // the human chooses, so a step by the bot is not possible, but an action is
    match.perform(env.legalActions().getFirst());
    match.replaceBot(env.currentPlayer(), new GreedyBot());
    assertFalse(match.isHumanTurn());
    match.step();
  }

  @Test
  public void replacingTheBotOfOnePlayerLeavesTheOthers() {
    start(new GreedyBot(), new CautiousBot());
    match.replaceBot(env.players().get(1), new HumanBot());
    assertEquals("greedy", match.bot(env.players().get(0)).name());
    assertTrue(match.bot(env.players().get(1)) instanceof HumanBot);
    assertEquals(2, match.bots().size());
  }
}
