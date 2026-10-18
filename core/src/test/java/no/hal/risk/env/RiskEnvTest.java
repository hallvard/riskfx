package no.hal.risk.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import no.hal.risk.FortifyMode;
import no.hal.risk.RiskConfig;
import org.junit.jupiter.api.Test;

public class RiskEnvTest {

  private RiskEnv createEnv(int playerCount, int maxTurns, long seed) {
    var env = new RiskEnv(new RiskConfig(playerCount), maxTurns);
    env.reset(seed);
    return env;
  }

  /** The occupant and number of armies of each territory, and whose turn it is. */
  private String signature(RiskEnv env) {
    var signature = new StringBuilder();
    for (var territory : env.territories()) {
      signature.append(env.playerIndex(territory.occupant())).append(':').append(territory.numArmies()).append(' ');
    }
    return signature.append(env.phase()).append(' ').append(env.playerIndex(env.currentPlayer())).toString();
  }

  /** Plays a game with random legal actions, preferring attacks, and returns the signature of the final state. */
  private String play(RiskEnv env, long botSeed, double endAttackWeight) {
    var random = new Random(botSeed);
    int steps = 0;
    while (! env.isDone()) {
      var actions = env.legalActions();
      assertFalse(actions.isEmpty(), "No legal actions in phase " + env.phase());
      var attacks = actions.stream().filter(action -> action instanceof Action.Attack).toList();
      var action = (! attacks.isEmpty() && random.nextDouble() > endAttackWeight)
          ? attacks.get(random.nextInt(attacks.size()))
          : actions.get(random.nextInt(actions.size()));
      env.step(action);
      assertTrue(++steps < 1_000_000, "The game doesn't end");
    }
    return signature(env) + " winner=" + (env.winner() != null ? env.playerIndex(env.winner()) : null);
  }

  @Test
  public void testResetStartsAtReinforcement() {
    var env = createEnv(3, 100, 1);
    assertEquals(Phase.REINFORCING, env.phase());
    assertEquals(0, env.playerIndex(env.currentPlayer()));
    assertFalse(env.isDone());
    assertEquals(0, env.turns());
    assertEquals(42, env.territories().size());
    var actions = env.legalActions();
    assertFalse(actions.isEmpty());
    assertTrue(actions.stream().allMatch(action -> action instanceof Action.Reinforce));
  }

  @Test
  public void testSameSeedGivesSameGame() {
    var signature1 = play(createEnv(3, 200, 42), 7, 0.5);
    var signature2 = play(createEnv(3, 200, 42), 7, 0.5);
    assertEquals(signature1, signature2);
    assertNotEquals(signature1, play(createEnv(3, 200, 43), 7, 0.5));
  }

  @Test
  public void testResetGivesSameStartAndIsRepeatable() {
    var env = createEnv(4, 100, 5);
    var start = signature(env);
    var game = play(env, 3, 0.5);
    env.reset(5);
    assertEquals(start, signature(env));
    assertEquals(game, play(env, 3, 0.5));
  }

  @Test
  public void testGamesFinishWithoutErrors() {
    // all actions come from legalActions(), so none may be rejected
    for (int seed = 0; seed < 20; seed++) {
      var env = createEnv(2 + seed % 3, 150, seed);
      play(env, seed, 0.2);
      assertTrue(env.isDone());
      assertTrue(env.legalActions().isEmpty());
      assertEquals(Phase.GAME_OVER == env.phase(), env.isWon());
    }
  }

  @Test
  public void testGamesCanBeWon() {
    int won = 0;
    for (int seed = 0; seed < 10; seed++) {
      var env = createEnv(2, 400, seed);
      play(env, seed, 0.05);
      if (env.isWon()) {
        won++;
        assertNotNull(env.winner());
        assertEquals(1, env.risk().alivePlayers().size());
        assertTrue(env.risk().allTerritories().stream().allMatch(territory -> territory.occupant() == env.winner()));
        assertFalse(env.isTruncated());
      }
    }
    assertTrue(won > 0, "No game was won");
  }

  @Test
  public void testGameIsTruncatedAtMaxTurns() {
    var env = createEnv(2, 3, 11);
    play(env, 1, 1.0);
    assertTrue(env.isTruncated());
    assertFalse(env.isWon());
    assertNull(env.winner());
    assertEquals(3, env.turns());
    assertTrue(env.legalActions().isEmpty());
    assertThrows(IllegalStateException.class, () -> env.step(new Action.SkipFortify()));
  }

  @Test
  public void testStepResult() {
    var env = createEnv(2, 100, 2);
    var player = env.currentPlayer();
    var result = env.step(env.legalActions().getFirst());
    assertEquals(player, result.actor());
    assertFalse(result.done());
    assertNull(result.winner());
    assertFalse(result.truncated());
  }

  // illegal actions

  @Test
  public void testActionsInWrongPhaseAreRejected() {
    var env = createEnv(2, 100, 3);
    var before = signature(env);
    var own = env.legalActions().getFirst();
    assertTrue(own instanceof Action.Reinforce);
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.EndAttack()));
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Attack(0, 1)));
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.SkipFortify()));
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Fortify(0, 1, 1)));
    assertEquals(before, signature(env));
  }

  @Test
  public void testReinforcingOthersTerritoryIsRejected() {
    var env = createEnv(2, 100, 3);
    var before = signature(env);
    int others = -1;
    for (int i = 0; i < env.territories().size(); i++) {
      if (env.territories().get(i).occupant() != env.currentPlayer()) {
        others = i;
        break;
      }
    }
    int other = others;
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Reinforce(other)));
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Reinforce(-1)));
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Reinforce(env.territories().size())));
    assertEquals(before, signature(env));
  }

  private void toPhase(RiskEnv env, Phase phase) {
    while (env.phase() != phase) {
      env.step(switch (env.phase()) {
        case REINFORCING -> env.legalActions().getFirst();
        case ATTACKING -> new Action.EndAttack();
        default -> throw new IllegalStateException("Cannot reach " + phase + " from " + env.phase());
      });
    }
  }

  @Test
  public void testIllegalAttacksAreRejected() {
    var env = createEnv(2, 100, 4);
    toPhase(env, Phase.ATTACKING);
    var before = signature(env);
    var player = env.currentPlayer();
    var territories = env.territories();
    int own = -1, other = -1, notAdjacent = -1;
    for (int i = 0; i < territories.size(); i++) {
      var territory = territories.get(i);
      if (territory.occupant() == player && own < 0) {
        own = i;
      } else if (territory.occupant() != player && other < 0) {
        other = i;
      }
    }
    // an enemy territory that doesn't border the own territory
    for (int i = 0; i < territories.size(); i++) {
      if (territories.get(i).occupant() != player && ! env.risk().areAdjacent(territories.get(own), territories.get(i))) {
        notAdjacent = i;
        break;
      }
    }
    int a = own, o = other, n = notAdjacent;
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Attack(a, n)));
    // from the enemy
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Attack(o, a)));
    // own territory
    assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Attack(a, a)));
    assertEquals(before, signature(env));
    // a territory with a single army can never attack
    for (var action : env.legalActions()) {
      if (action instanceof Action.Attack(var from, var _)) {
        assertTrue(territories.get(from).numArmies() >= 2);
      }
    }
  }

  @Test
  public void testIllegalFortificationsAreRejected() {
    var env = createEnv(2, 100, 6);
    toPhase(env, Phase.ATTACKING);
    env.step(new Action.EndAttack());
    assertEquals(Phase.FORTIFYING, env.phase());
    var before = signature(env);
    var player = env.currentPlayer();
    var territories = env.territories();
    var legal = env.legalActions().stream()
        .filter(action -> action instanceof Action.Fortify)
        .map(action -> (Action.Fortify) action)
        .toList();
    assertTrue(legal.stream().allMatch(fortify -> env.risk().areAdjacent(territories.get(fortify.from()), territories.get(fortify.to()))));
    // more armies than can be moved
    if (! legal.isEmpty()) {
      var fortify = legal.getFirst();
      assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Fortify(fortify.from(), fortify.to(), fortify.numArmies() + 1)));
      assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Fortify(fortify.from(), fortify.to(), 0)));
      assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Fortify(fortify.from(), fortify.from(), 1)));
    }
    // to another player's territory
    for (int from = 0; from < territories.size(); from++) {
      for (int to = 0; to < territories.size(); to++) {
        if (territories.get(from).occupant() == player && territories.get(to).occupant() != player
            && env.risk().areAdjacent(territories.get(from), territories.get(to))) {
          int f = from, t = to;
          assertThrows(IllegalArgumentException.class, () -> env.step(new Action.Fortify(f, t, 1)));
        }
      }
    }
    assertEquals(before, signature(env));
  }

  @Test
  public void testFortifyEndsTurnAndPassesToNextPlayer() {
    var env = createEnv(2, 100, 6);
    var first = env.currentPlayer();
    toPhase(env, Phase.ATTACKING);
    env.step(new Action.EndAttack());
    var fortifications = env.legalActions().stream().filter(action -> action instanceof Action.Fortify).toList();
    assertEquals(0, env.turns());
    if (fortifications.isEmpty()) {
      env.step(new Action.SkipFortify());
    } else {
      // any number from 1 up to the maximum is accepted
      var fortify = (Action.Fortify) fortifications.getFirst();
      env.step(new Action.Fortify(fortify.from(), fortify.to(), 1));
    }
    assertEquals(1, env.turns());
    assertNotEquals(first, env.currentPlayer());
    assertEquals(Phase.REINFORCING, env.phase());
  }

  @Test
  public void testReinforcementArmiesAreUsedUpOneByOne() {
    var env = createEnv(2, 100, 8);
    var player = env.currentPlayer();
    int total = env.risk().reinforcementArmies(player);
    int before = env.risk().playerTerritories(player).stream().mapToInt(territory -> territory.numArmies()).sum();
    for (int i = 0; i < total; i++) {
      assertEquals(Phase.REINFORCING, env.phase());
      env.step(env.legalActions().getFirst());
    }
    assertEquals(Phase.ATTACKING, env.phase());
    int after = env.risk().playerTerritories(player).stream().mapToInt(territory -> territory.numArmies()).sum();
    assertEquals(before + total, after);
  }

  @Test
  public void testLegalAttackActionsAreSortedAndDeterministic() {
    var env1 = createEnv(2, 100, 9);
    var env2 = createEnv(2, 100, 9);
    toPhase(env1, Phase.ATTACKING);
    toPhase(env2, Phase.ATTACKING);
    assertEquals(env1.legalActions(), env2.legalActions());
    List<Action> actions = env1.legalActions();
    assertEquals(new Action.EndAttack(), actions.getLast());
  }

  @Test
  public void testConnectedFortificationActionsAreAccepted() {
    var env = new RiskEnv(new RiskConfig(2).withFortifyMode(FortifyMode.CONNECTED), 100);
    env.reset(12);
    toPhase(env, Phase.ATTACKING);
    env.step(new Action.EndAttack());
    var territories = env.territories();
    var fortifications = env.legalActions().stream()
        .filter(action -> action instanceof Action.Fortify)
        .map(action -> (Action.Fortify) action)
        .toList();
    // more than just the neighbours
    assertTrue(fortifications.stream().anyMatch(fortify ->
        ! env.risk().areAdjacent(territories.get(fortify.from()), territories.get(fortify.to()))));
    var fortify = fortifications.getFirst();
    env.step(fortify);
    assertEquals(Phase.REINFORCING, env.phase());
  }

  @Test
  public void testConnectedGamesFinishWithoutErrors() {
    for (int seed = 0; seed < 10; seed++) {
      var env = new RiskEnv(new RiskConfig(3).withFortifyMode(FortifyMode.CONNECTED), 150);
      env.reset(seed);
      play(env, seed, 0.2);
      assertTrue(env.isDone());
    }
  }
}
