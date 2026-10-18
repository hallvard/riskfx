package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.hal.risk.ai.BotMatch;
import no.hal.risk.ai.GreedyBot;
import no.hal.risk.ai.HumanBot;
import no.hal.risk.env.Action;
import no.hal.risk.env.Phase;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** In this package, to be able to set up a board. */
public class BotMatchSkipTest {

  private RiskEnv env;
  private BotMatch match;
  private Territory alaska, madagascar, eastAfrica, southAfrica;

  /**
   * The first player, a human, occupies everything with 2 armies, except Madagascar, which the second occupies.
   * Madagascar only borders East Africa and South Africa, which have 1 army, so nothing can attack.
   */
  @BeforeEach
  public void setUp() {
    env = new RiskEnv(new RiskConfig(2), 100);
    match = new BotMatch(env, List.of(new HumanBot(), new GreedyBot()));
    match.reset(1, List.of(new HumanBot(), new GreedyBot()));
    var risk = env.risk();
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(risk.players().get(0), 2);
    }
    alaska = risk.getTerritory("Alaska");
    madagascar = risk.getTerritory("Madagascar");
    eastAfrica = risk.getTerritory("East Africa");
    southAfrica = risk.getTerritory("South Africa");
    madagascar.setOccupant(risk.players().get(1), 2);
    eastAfrica.setOccupant(risk.players().get(0), 1);
    southAfrica.setOccupant(risk.players().get(0), 1);
  }

  private void reinforceAll() {
    while (env.phase() == Phase.REINFORCING) {
      match.perform(new Action.Reinforce(env.territoryIndex(alaska)));
    }
  }

  @Test
  public void testNothingIsSkippedWhileThereIsSomethingToDo() {
    assertEquals(Phase.REINFORCING, env.phase());
    assertTrue(match.skipForcedPhases().isEmpty());
    assertEquals(Phase.REINFORCING, env.phase());
  }

  @Test
  public void testTheAttackIsSkippedWhenNoTerritoryCanAttack() {
    reinforceAll();
    assertEquals(Phase.ATTACKING, env.phase());
    assertEquals(List.of(new Action.EndAttack()), env.legalActions());
    var moves = match.skipForcedPhases();
    assertEquals(1, moves.size());
    assertEquals(new Action.EndAttack(), moves.getFirst().action());
    // armies can be moved, so the fortification is up to the player
    assertEquals(Phase.FORTIFYING, env.phase());
    assertTrue(match.isHumanTurn());
  }

  @Test
  public void testTheAttackIsNotSkippedWhenATerritoryCanAttack() {
    reinforceAll();
    // East Africa borders Madagascar, and now has armies to attack with
    eastAfrica.setOccupant(env.risk().players().get(0), 5);
    assertTrue(match.skipForcedPhases().isEmpty());
    assertEquals(Phase.ATTACKING, env.phase());
  }

  @Test
  public void testTheFortificationIsSkippedWhenNoArmiesCanBeMoved() {
    reinforceAll();
    match.skipForcedPhases();
    assertEquals(Phase.FORTIFYING, env.phase());
    // one army everywhere: none can move
    for (var territory : env.risk().playerTerritories(env.players().get(0))) {
      territory.setOccupant(env.players().get(0), 1);
    }
    var moves = match.skipForcedPhases();
    assertEquals(1, moves.size());
    assertEquals(new Action.SkipFortify(), moves.getFirst().action());
    // it is the next player's turn, which starts with reinforcing
    assertEquals(env.players().get(1), env.currentPlayer());
    assertEquals(Phase.REINFORCING, env.phase());
    assertEquals(1, env.turns());
  }

  @Test
  public void testBothPhasesAreSkippedInARow() {
    // nobody can attack, and after reinforcing nothing can be moved: the first player has one territory with enough armies,
    // which is next to enemy territories only
    var risk = env.risk();
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(risk.players().get(1), 1);
    }
    var iceland = risk.getTerritory("Iceland");
    iceland.setOccupant(risk.players().get(0), 1);
    // Iceland borders Greenland, Great Britain and Scandinavia, all of the second player
    while (env.phase() == Phase.REINFORCING) {
      match.perform(new Action.Reinforce(env.territoryIndex(iceland)));
    }
    // Iceland has armies now and borders enemies, so it can attack
    assertEquals(Phase.ATTACKING, env.phase());
    assertTrue(match.skipForcedPhases().isEmpty());
    // but when it can't (all armies gone, say), both phases pass and the other player is up
    iceland.setOccupant(risk.players().get(0), 1);
    var moves = match.skipForcedPhases();
    assertEquals(2, moves.size());
    assertEquals(new Action.EndAttack(), moves.get(0).action());
    assertEquals(new Action.SkipFortify(), moves.get(1).action());
    assertEquals(risk.players().get(1), env.currentPlayer());
  }

  @Test
  public void testNothingIsSkippedWhenTheGameIsOver() {
    for (var territory : env.risk().allTerritories()) {
      territory.setOccupant(env.risk().players().get(0), 2);
    }
    assertTrue(env.isDone());
    assertTrue(match.skipForcedPhases().isEmpty());
  }
}
