package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import no.hal.risk.training.BoardEvaluation;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

/** In this package, to be able to set up a board. */
public class BoardEvaluationTest {

  private RiskEnv env;

  /** The first player occupies all territories with 2 armies, except the given ones, which the second occupies with 2 armies. */
  private void setUp(String... secondPlayerTerritories) {
    env = new RiskEnv(new RiskConfig(2), 100);
    env.reset(1);
    for (var territory : env.territories()) {
      territory.setOccupant(env.players().get(0), 2);
    }
    for (var name : secondPlayerTerritories) {
      env.risk().getTerritory(name).setOccupant(env.players().get(1), 2);
    }
  }

  @Test
  public void testPotentialsOfTwoPlayersCancelOut() {
    setUp("Alberta", "Brazil", "Japan");
    var evaluation = new BoardEvaluation();
    assertEquals(0, evaluation.potential(env, 0) + evaluation.potential(env, 1), 1e-12);
    assertTrue(evaluation.potential(env, 0) > 0);
    assertTrue(evaluation.potential(env, 1) < 0);
  }

  @Test
  public void testMoreArmiesGivesHigherPotential() {
    setUp("Alberta", "Brazil", "Japan");
    var evaluation = new BoardEvaluation();
    double before = evaluation.potential(env, 1);
    env.risk().getTerritory("Brazil").setOccupant(env.players().get(1), 6);
    assertTrue(evaluation.potential(env, 1) > before);
  }

  @Test
  public void testMoreTerritoriesGivesHigherPotential() {
    setUp("Alberta", "Brazil", "Japan");
    var evaluation = new BoardEvaluation();
    double before = evaluation.potential(env, 1);
    env.risk().getTerritory("Peru").setOccupant(env.players().get(1), 2);
    assertTrue(evaluation.potential(env, 1) > before);
  }

  @Test
  public void testOccupyingAContinentGivesItsBonus() {
    // Australia has 4 territories and bonus 2, of a total of 24
    var continentsOnly = new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 1, 0));
    setUp("Eastern Australia", "Indonesia", "New Guinea", "Western Australia");
    assertEquals(2.0 / 24, continentsOnly.scores(env)[1], 1e-9);
    assertEquals(22.0 / 24, continentsOnly.scores(env)[0], 1e-9);
    // three of four territories is no continent
    setUp("Eastern Australia", "Indonesia", "New Guinea");
    assertEquals(0, continentsOnly.scores(env)[1], 1e-9);
  }

  @Test
  public void testPotentialIsZeroWhenGameIsOver() {
    setUp();
    // the second player has nothing left
    assertTrue(env.isDone());
    var evaluation = new BoardEvaluation();
    assertEquals(0, evaluation.potential(env, 0));
    assertEquals(0, evaluation.potential(env, 1));
  }

  @Test
  public void testExposure() {
    // exposure only: Alberta has 6 armies next to Alaska, with 2 armies
    setUp("Alberta");
    env.risk().getTerritory("Alberta").setOccupant(env.players().get(1), 6);
    var exposure = new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 0, 1));
    var scores = exposure.scores(env);
    // the first player: Alaska, Northwest Territory, Ontario and Western United States (6 - 2 each), out of 41 * 2 + 6 armies
    assertEquals(-16.0 / 88, scores[0], 1e-9);
    // the second player: Alberta has 6 armies, and four enemy neighbours with 2 armies each, so 8 - 6
    assertEquals(-2.0 / 88, scores[1], 1e-9);
  }

  @Test
  public void testAttackAdvantage() {
    // Alaska has 6 armies next to Alberta with 2, so it can send in 6 - 1 - 2 = 3 beyond the defenders.
    // The other territories next to Alberta have 2 armies, which gives no advantage.
    setUp("Alberta");
    env.risk().getTerritory("Alaska").setOccupant(env.players().get(0), 6);
    // 40 territories with 2 armies, Alaska with 6 and Alberta with 2
    double armies = 40 * 2 + 6 + 2;
    var plain = new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 0, 1, 0, 0)).scores(env);
    assertEquals(3 / armies, plain[0], 1e-9);
    assertEquals(0, plain[1], 1e-9);
    var capped = new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 0, 0, 1, 0)).scores(env);
    assertEquals(3.0 / (BoardEvaluation.ADVANTAGE_CAP * 42), capped[0], 1e-9);
    var count = new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 0, 0, 0, 1)).scores(env);
    assertEquals(1.0 / 42, count[0], 1e-9);
  }

  @Test
  public void testAttackAdvantageIsCappedPerTerritory() {
    setUp("Alberta");
    env.risk().getTerritory("Alaska").setOccupant(env.players().get(0), 30);
    var capped = new BoardEvaluation(BoardEvaluation.Weights.only(0, 0, 0, 0, 1, 0)).scores(env);
    // a lead of 27 counts as the cap
    assertEquals((double) BoardEvaluation.ADVANTAGE_CAP / (BoardEvaluation.ADVANTAGE_CAP * 42), capped[0], 1e-9);
  }

  @Test
  public void testAdvantageRaisesThePotentialRelativeToTheOpponent() {
    setUp("Alberta");
    var advantage = new BoardEvaluation(new BoardEvaluation.Weights(0, 0, 0, 0, 1, 0, 0, 1));
    double before = advantage.potential(env, 0);
    env.risk().getTerritory("Alaska").setOccupant(env.players().get(0), 8);
    assertTrue(advantage.potential(env, 0) > before);
    assertEquals(-advantage.potential(env, 0), advantage.potential(env, 1), 1e-12);
  }

  // eliminating opponents

  private RiskEnv fourPlayers() {
    var env = new RiskEnv(new RiskConfig(4), 100);
    env.reset(1);
    return env;
  }

  /** The first three players occupy one territory each with 2 armies, or the given number of players, and the first all the others. */
  private void occupy(RiskEnv env, int playersAlive) {
    var risk = env.risk();
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(risk.players().get(0), 2);
    }
    for (int player = 1; player < playersAlive; player++) {
      risk.allTerritories().get(player).setOccupant(risk.players().get(player), 2);
    }
  }

  @Test
  public void testPotentialRisesWithTheShareOfOpponentsEliminated() {
    var env = fourPlayers();
    // only the eliminations count, with the scale 1
    var evaluation = new BoardEvaluation(new BoardEvaluation.Weights(0, 0, 0, 0, 0, 0, 0, 1, 1));
    occupy(env, 4);
    assertEquals(0, evaluation.potential(env, 0), 1e-12);
    occupy(env, 3);
    assertEquals(1.0 / 3, evaluation.potential(env, 0), 1e-12);
    assertEquals(1.0 / 3, evaluation.potential(env, 1), 1e-12);
    occupy(env, 2);
    assertEquals(2.0 / 3, evaluation.potential(env, 0), 1e-12);
    // an eliminated player has no more decisions, and gets nothing
    assertEquals(0, evaluation.potential(env, 3), 1e-12);
    // and when one is left, the game is over, with no potential
    occupy(env, 1);
    assertTrue(env.isDone());
    assertEquals(0, evaluation.potential(env, 0), 1e-12);
  }

  @Test
  public void testEliminationsDoNotMatterWithTwoPlayersUntilTheGameIsOver() {
    var env = new RiskEnv(new RiskConfig(2), 100);
    env.reset(1);
    var evaluation = new BoardEvaluation(new BoardEvaluation.Weights(0, 0, 0, 0, 0, 0, 0, 1, 1));
    // both alive: the share of opponents eliminated is 0
    assertEquals(0, evaluation.potential(env, 0), 1e-12);
    assertEquals(0, evaluation.potential(env, 1), 1e-12);
  }

  @Test
  public void testTheEliminationRewardOnlyDiffersFromTheDefaultWhenAPlayerIsEliminated() {
    var env = fourPlayers();
    var plain = no.hal.risk.training.Reward.named("default").evaluation();
    var withEliminations = no.hal.risk.training.Reward.named("elimination").evaluation();
    occupy(env, 4);
    assertEquals(plain.potential(env, 0), withEliminations.potential(env, 0), 1e-12);
    occupy(env, 3);
    // 0.5 of the share 1/3, scaled by 0.3
    assertEquals(plain.potential(env, 0) + 0.3 * 0.5 / 3, withEliminations.potential(env, 0), 1e-12);
    assertThrows(IllegalArgumentException.class, () -> no.hal.risk.training.Reward.named("nonsense"));
  }
}
