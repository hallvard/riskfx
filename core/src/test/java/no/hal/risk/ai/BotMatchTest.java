package no.hal.risk.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import no.hal.risk.RiskConfig;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BotMatchTest {

  private RiskEnv env;

  @BeforeEach
  public void setUp() {
    env = new RiskEnv(new RiskConfig(2), 150);
  }

  @Test
  public void testPlayingAWholeGame() {
    var match = new BotMatch(env, List.of(new GreedyBot(), new GreedyBot()));
    match.reset(2, List.of(new GreedyBot(), new GreedyBot()));
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

  @Test
  public void testDescriptions() {
    var match = new BotMatch(env, List.of(new GreedyBot(), new GreedyBot()));
    match.reset(6, List.of(new GreedyBot(), new GreedyBot()));
    boolean attack = false, reinforce = false, fortify = false, end = false;
    while (! match.isDone()) {
      var move = match.step();
      var description = move.description();
      switch (move.action()) {
        case Action.Reinforce _ -> {
          reinforce = true;
          assertTrue(description.startsWith("reinforces "), description);
        }
        case Action.Attack attackAction -> {
          attack = true;
          assertTrue(description.startsWith("attacks "), description);
          assertTrue(description.contains(env.territories().get(attackAction.from()).name()), description);
          assertTrue(description.contains("lost "), description);
        }
        case Action.Fortify _ -> {
          fortify = true;
          assertTrue(description.startsWith("moves "), description);
        }
        case Action.EndAttack _, Action.SkipFortify _ -> end = true;
      }
    }
    assertTrue(attack && reinforce && fortify && end);
  }

  @Test
  public void testAttackDescriptionReportsLossesConsistentlyWithTheBoard() {
    var match = new BotMatch(env, List.of(new GreedyBot(), new GreedyBot()));
    match.reset(8, List.of(new GreedyBot(), new GreedyBot()));
    while (! match.isDone()) {
      int before = totalArmies();
      var move = match.step();
      int after = totalArmies();
      if (move.action() instanceof Action.Attack) {
        var parts = move.description().replaceAll(".*: lost (\\d+), defender lost (\\d+).*", "$1 $2").split(" ");
        // the armies that disappeared from the board are exactly the reported losses
        assertEquals(before - after, Integer.parseInt(parts[0]) + Integer.parseInt(parts[1]), move.description());
      }
    }
  }

  @Test
  public void testMovesCarryTheLossesOfAttacks() {
    var match = new BotMatch(env, List.of(new GreedyBot(), new GreedyBot()));
    match.reset(10, List.of(new GreedyBot(), new GreedyBot()));
    int attacks = 0;
    while (! match.isDone()) {
      int before = totalArmies();
      var move = match.step();
      if (move.action() instanceof Action.Attack) {
        attacks++;
        assertTrue(move.attackerLoss() >= 0 && move.defenderLoss() >= 0);
        assertTrue(move.attackerLoss() + move.defenderLoss() > 0, "someone always loses armies in an attack");
        assertEquals(before - totalArmies(), move.attackerLoss() + move.defenderLoss());
      } else {
        assertEquals(0, move.attackerLoss());
        assertEquals(0, move.defenderLoss());
      }
    }
    assertTrue(attacks > 5);
  }

  private int totalArmies() {
    return env.territories().stream().mapToInt(territory -> territory.numArmies()).sum();
  }

  @Test
  public void testResetNeedsABotForEachPlayer() {
    assertThrows(IllegalArgumentException.class, () -> new BotMatch(env, List.of(new GreedyBot())));
  }

  @Test
  public void testAHumanChoosesActionsThatArePerformed() {
    var match = new BotMatch(env, List.of(new HumanBot(), new GreedyBot()));
    match.reset(3, List.of(new HumanBot(), new GreedyBot()));
    var helper = new GreedyBot();
    int humanMoves = 0, botMoves = 0;
    assertTrue(match.isHumanTurn());
    assertThrows(IllegalStateException.class, match::step);
    while (! match.isDone()) {
      if (match.isHumanTurn()) {
        var legal = env.legalActions();
        var move = match.perform(helper.chooseAction(env, legal));
        assertTrue(move.bot() instanceof HumanBot);
        assertFalse(move.description().isBlank());
        humanMoves++;
      } else {
        var move = match.step();
        assertFalse(move.bot() instanceof HumanBot);
        botMoves++;
      }
    }
    assertTrue(humanMoves > 10 && botMoves > 10);
    assertTrue(match.result().contains("won") || match.result().contains("Draw"));
  }

  @Test
  public void testPerformingAnIllegalActionLeavesTheGameUnchanged() {
    var match = new BotMatch(env, List.of(new HumanBot(), new GreedyBot()));
    var before = totalArmies();
    // the first player is reinforcing: attacking is not allowed
    assertThrows(IllegalArgumentException.class, () -> match.perform(new Action.Attack(0, 1)));
    assertEquals(before, totalArmies());
    assertEquals(0, env.turns());
  }

  @Test
  public void testHumanBotCannotChoose() {
    assertThrows(UnsupportedOperationException.class, () -> new HumanBot().chooseAction(env, env.legalActions()));
    assertEquals("Human", new HumanBot().name());
  }
}
