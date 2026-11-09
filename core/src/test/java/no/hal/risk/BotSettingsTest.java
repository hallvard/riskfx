package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import no.hal.risk.ai.Bots;
import no.hal.risk.ai.GreedyBot;
import no.hal.risk.ai.ParametrizedBot;
import no.hal.risk.env.Action;
import no.hal.risk.env.Phase;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.Test;

public class BotSettingsTest {

  /**
   * The first player holds everything, with 2 armies, except South America and Madagascar, which the second holds with 2 armies,
   * and 1 army on Madagascar. East Africa, next to Madagascar, has 8 armies, and Central America, next to South America, 6, so there are
   * two attacks that are fine: from East Africa to Madagascar, with a lead of 7, and from Central America to Venezuela, with a lead of 4.
   * It is the first player's turn to attack.
   */
  private RiskEnv attackingEnv() {
    var env = new RiskEnv(new RiskConfig(2), 100);
    env.reset(1);
    var risk = env.risk();
    var first = risk.players().get(0);
    var second = risk.players().get(1);
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(first, 2);
    }
    for (var name : List.of("Venezuela", "Brazil", "Peru", "Argentina")) {
      risk.getTerritory(name).setOccupant(second, 2);
    }
    risk.getTerritory("Madagascar").setOccupant(second, 1);
    risk.getTerritory("East Africa").setOccupant(first, 8);
    risk.getTerritory("Central America").setOccupant(first, 6);
    while (env.phase() == Phase.REINFORCING) {
      env.step(new Action.Reinforce(env.territoryIndex(risk.getTerritory("Alaska"))));
    }
    assertEquals(Phase.ATTACKING, env.phase());
    return env;
  }

  private Action.Attack attack(RiskEnv env, String from, String to) {
    var risk = env.risk();
    return new Action.Attack(env.territoryIndex(risk.getTerritory(from)), env.territoryIndex(risk.getTerritory(to)));
  }

  private Action choose(ParametrizedBot.Params params, RiskEnv env) {
    return new ParametrizedBot("test", params).chooseAction(env, env.legalActions());
  }

  @Test
  public void withoutPreferencesTheBiggestLeadIsAttacked() {
    var env = attackingEnv();
    assertEquals(attack(env, "East Africa", "Madagascar"), choose(GreedyBot.params(2), env));
  }

  @Test
  public void aDenierBreaksAContinentThatAnOpponentHolds() {
    var env = attackingEnv();
    // the second player holds all of South America, which gives 2, and an army of lead is worth 2 for each: 4 + 4 is more than 7
    var denier = GreedyBot.params(2).withTargets(0, 2.0, 0);
    assertEquals(attack(env, "Central America", "Venezuela"), choose(denier, env));
    // and for less, the lead decides
    assertEquals(attack(env, "East Africa", "Madagascar"), choose(GreedyBot.params(2).withTargets(0, 0.5, 0), env));
  }

  @Test
  public void aBuilderCompletesAContinentOfItsOwn() {
    var env = attackingEnv();
    var risk = env.risk();
    var first = risk.players().get(0);
    var second = risk.players().get(1);
    // the second player has Indonesia, which is all that the first lacks of Australia, and Siam next to it has 5 armies, a lead of 3
    risk.getTerritory("Indonesia").setOccupant(second, 2);
    risk.getTerritory("Siam").setOccupant(first, 5);
    // and Egypt, so that taking Madagascar doesn't complete Africa, which would be worth more to a builder
    risk.getTerritory("Egypt").setOccupant(second, 2);
    // Australia gives 2, so 3 for each point is 3 + 6 more than the lead of 7 at Madagascar
    assertEquals(attack(env, "Siam", "Indonesia"), choose(GreedyBot.params(2).withTargets(3.0, 0, 0), env));
    assertEquals(attack(env, "East Africa", "Madagascar"), choose(GreedyBot.params(2).withTargets(1.0, 0, 0), env));
  }

  @Test
  public void anOpponentOnItsLastTerritoryIsAWorthwhileTarget() {
    // three players: the first holds everything but Madagascar and Iceland, which the second holds, and Venezuela, which is all that the
    // third has left, with 1 army. East Africa has 8 armies against 1 on Madagascar, a lead of 7, Central America 6 against 1, a lead of 5.
    var env = new RiskEnv(new RiskConfig(3), 100);
    env.reset(1);
    var risk = env.risk();
    var first = risk.players().get(0);
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(first, 2);
    }
    risk.getTerritory("Madagascar").setOccupant(risk.players().get(1), 1);
    risk.getTerritory("Iceland").setOccupant(risk.players().get(1), 3);
    risk.getTerritory("Venezuela").setOccupant(risk.players().get(2), 1);
    risk.getTerritory("East Africa").setOccupant(first, 8);
    risk.getTerritory("Central America").setOccupant(first, 6);
    while (env.phase() == Phase.REINFORCING) {
      env.step(new Action.Reinforce(env.territoryIndex(risk.getTerritory("Alaska"))));
    }
    assertEquals(attack(env, "East Africa", "Madagascar"), choose(GreedyBot.params(2), env));
    // taking the last territory of the third player is worth 3 more than the lead of 5
    assertEquals(attack(env, "Central America", "Venezuela"), choose(GreedyBot.params(2).withTargets(0, 0, 3.0), env));
  }

  @Test
  public void theAttacksOfATurnCanBeLimited() {
    // after the limit, it ends the attack, whatever there is to attack
    for (var limits : new int[][] {{1, Integer.MAX_VALUE}, {Integer.MAX_VALUE, 1}, {3, 2}}) {
      var bot = new ParametrizedBot("limited", GreedyBot.params(1).withLimits(limits[0], limits[1]));
      var env = new RiskEnv(new RiskConfig(3), 150);
      var opponent = new GreedyBot();
      for (int game = 0; game < 6; game++) {
        env.reset(game);
        while (! env.isDone()) {
          var mover = env.playerIndex(env.currentPlayer()) == 0 ? bot : opponent;
          env.step(mover.chooseAction(env, env.legalActions()));
          if (env.playerIndex(env.currentPlayer()) == 0) {
            assertTrue(env.attacksThisTurn() <= limits[0], "attacks " + env.attacksThisTurn());
            assertTrue(env.conquestsThisTurn() <= limits[1], "conquests " + env.conquestsThisTurn());
          }
        }
      }
    }
  }

  @Test
  public void theEnvironmentCountsTheAttacksAndConquestsOfATurn() {
    var env = attackingEnv();
    assertEquals(0, env.attacksThisTurn());
    env.step(attack(env, "East Africa", "Madagascar"));
    // 8 armies against 1: the attacker wins every round of three dice against one with 1 in 3 odds of losing none, so it may take several
    assertEquals(1, env.attacksThisTurn());
    env.step(new Action.EndAttack());
    env.step(new Action.SkipFortify());
    assertEquals(0, env.attacksThisTurn());
    assertEquals(0, env.conquestsThisTurn());
  }

  @Test
  public void noiseMakesTheChoiceVaryAndNoNoiseDoesNot() {
    var env = attackingEnv();
    var steady = new ParametrizedBot("steady", GreedyBot.params(2));
    var noisy = new ParametrizedBot("noisy", GreedyBot.params(2).withNoise(6.0));
    var steadyChoices = new HashSet<Action>();
    var noisyChoices = new HashSet<Action>();
    for (int i = 0; i < 300; i++) {
      steadyChoices.add(steady.chooseAction(env, env.legalActions()));
      noisyChoices.add(noisy.chooseAction(env, env.legalActions()));
    }
    assertEquals(1, steadyChoices.size());
    assertTrue(noisyChoices.size() >= 2, "the noisy bot always chose " + noisyChoices);
  }

  @Test
  public void everyNamedBotPlaysACompleteGame() {
    assertEquals(Bots.NAMES.size(), new HashSet<>(Bots.NAMES).size());
    for (var name : Bots.NAMES) {
      var bot = Bots.byName(name);
      assertEquals(name, bot.name());
      var env = new RiskEnv(new RiskConfig(2), 120);
      env.reset(7);
      var other = new GreedyBot();
      var random = new Random(7);
      while (! env.isDone()) {
        var mover = env.playerIndex(env.currentPlayer()) == 0 ? bot : other;
        var legal = env.legalActions();
        var action = mover.chooseAction(env, legal);
        assertTrue(legal.contains(action), name + " chose an illegal action " + action);
        env.step(action);
      }
    }
  }

  @Test
  public void theNamedBotsAreDifferent() {
    // different settings play different games: from the same board, they are not all the same
    var results = new HashSet<String>();
    for (var name : Bots.NAMES) {
      if (name.equals("erratic")) {
        continue;
      }
      var env = new RiskEnv(new RiskConfig(2), 120);
      env.reset(3);
      var bot = Bots.byName(name);
      var actions = new StringBuilder();
      for (int i = 0; i < 40 && ! env.isDone(); i++) {
        var action = bot.chooseAction(env, env.legalActions());
        actions.append(action).append(';');
        env.step(action);
      }
      results.add(actions.toString());
    }
    assertNotEquals(1, results.size());
    assertTrue(results.size() >= 4, "only " + results.size() + " different games");
  }
}
