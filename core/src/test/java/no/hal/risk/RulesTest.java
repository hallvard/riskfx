package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

public class RulesTest {

  private Risk alaska6AgainstAlberta1(Rules rules) {
    var risk = new RiskConfig(2).withRules(rules).createRisk();
    var first = risk.players().get(0);
    var second = risk.players().get(1);
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(first, 2);
    }
    risk.getTerritory("Alberta").setOccupant(second, 1);
    risk.getTerritory("Japan").setOccupant(second, 2);
    risk.getTerritory("Alaska").setOccupant(first, 6);
    risk.setDice(new DeterministicDice(List.of(6, 6, 6, 1)));
    risk.start();
    risk.nextTurnState(); // attacking
    return risk;
  }

  private void conquerAlberta(Risk risk) {
    var attacking = (TurnState.Attacking) risk.currentTurnState();
    assertTrue(attacking.attack(risk, risk.getTerritory("Alaska"), risk.getTerritory("Alberta"), 3, 1));
  }

  @Test
  public void allArmiesMoveInByDefault() {
    var risk = alaska6AgainstAlberta1(Rules.DEFAULT);
    conquerAlberta(risk);
    assertEquals(5, risk.getTerritory("Alberta").numArmies());
    assertEquals(1, risk.getTerritory("Alaska").numArmies());
    assertNull(risk.pendingOccupation());
  }

  @Test
  public void withKeepArmiesBackOnlyTheDiceMoveInAtFirst() {
    var risk = alaska6AgainstAlberta1(new Rules(true, false));
    conquerAlberta(risk);
    assertEquals(3, risk.getTerritory("Alberta").numArmies());
    assertEquals(3, risk.getTerritory("Alaska").numArmies());
    assertEquals(2, risk.pendingOccupation().maxExtra());

    risk.occupy(2);

    assertEquals(5, risk.getTerritory("Alberta").numArmies());
    assertEquals(1, risk.getTerritory("Alaska").numArmies());
    assertNull(risk.pendingOccupation());
    assertThrows(IllegalArgumentException.class, () -> risk.occupy(1));
  }

  @Test
  public void extraArmiesCanBeMovedInPartlyAndOnlyOnce() {
    var risk = alaska6AgainstAlberta1(new Rules(true, false));
    conquerAlberta(risk);
    assertThrows(IllegalArgumentException.class, () -> risk.occupy(3));
    assertThrows(IllegalArgumentException.class, () -> risk.occupy(0));
    risk.occupy(1);
    assertEquals(4, risk.getTerritory("Alberta").numArmies());
    assertNull(risk.pendingOccupation());
  }

  @Test
  public void endingTheAttackDropsThePendingOccupation() {
    var risk = alaska6AgainstAlberta1(new Rules(true, false));
    conquerAlberta(risk);
    risk.nextTurnState();
    assertNull(risk.pendingOccupation());
  }

  @Test
  public void noPendingOccupationWhenAllArmiesAlreadyMovedIn() {
    var risk = alaska6AgainstAlberta1(new Rules(true, false));
    risk.getTerritory("Alaska").setOccupant(risk.players().get(0), 4);
    conquerAlberta(risk);
    assertEquals(3, risk.getTerritory("Alberta").numArmies());
    assertNull(risk.pendingOccupation());
  }

  private int armiesAtNextTurnOfFirstPlayer(Risk risk) {
    // ends the turn of this player and of the other player, who does nothing
    var first = risk.players().get(0);
    do {
      risk.nextTurnState();
    } while (risk.currentPlayer() == first);
    do {
      risk.nextTurnState();
    } while (risk.currentPlayer() != first);
    return ((TurnState.Reinforcing) risk.currentTurnState()).totalArmies();
  }

  @Test
  public void conquestBonusIsGivenTheNextTurnOnly() {
    var risk = alaska6AgainstAlberta1(new Rules(false, true));
    conquerAlberta(risk);
    assertTrue(risk.hasConquestBonus(risk.players().get(0)));
    int expected = risk.reinforcementArmies(risk.players().get(0)) + Risk.CONQUEST_BONUS;

    assertEquals(expected, armiesAtNextTurnOfFirstPlayer(risk));

    assertFalse(risk.hasConquestBonus(risk.players().get(0)));
    // no conquest that turn, so no bonus the turn after
    assertEquals(risk.reinforcementArmies(risk.players().get(0)), armiesAtNextTurnOfFirstPlayer(risk));
  }

  @Test
  public void turnsGiveOneArmyAlwaysAndTwoMoreAfterAConquest() {
    var risk = alaska6AgainstAlberta1(new Rules(false, true));
    var first = risk.players().get(0);
    int continents = risk.occupiedContinents(first).stream().mapToInt(Continent::bonusPoints).sum();
    assertEquals(Risk.ALWAYS_REINFORCEMENT + continents, risk.reinforcementArmies(first));
    conquerAlberta(risk);
    continents = risk.occupiedContinents(first).stream().mapToInt(Continent::bonusPoints).sum();
    assertEquals(Risk.ALWAYS_REINFORCEMENT + continents + Risk.CONQUEST_BONUS, armiesAtNextTurnOfFirstPlayer(risk));
    assertEquals(1, Risk.ALWAYS_REINFORCEMENT);
    assertEquals(2, Risk.CONQUEST_BONUS);
  }

  @Test
  public void noConquestBonusWithoutConquestOrWithoutTheRule() {
    var noConquest = alaska6AgainstAlberta1(new Rules(false, true));
    assertEquals(noConquest.reinforcementArmies(noConquest.players().get(0)), armiesAtNextTurnOfFirstPlayer(noConquest));

    var ruleOff = alaska6AgainstAlberta1(Rules.DEFAULT);
    conquerAlberta(ruleOff);
    assertEquals(ruleOff.reinforcementArmies(ruleOff.players().get(0)), armiesAtNextTurnOfFirstPlayer(ruleOff));
  }

  @Test
  public void inTheFirstRoundEveryoneGetsTheConquestBonus() {
    var risk = new RiskConfig(3).withRules(new Rules(false, true)).createRisk();
    risk.start();
    for (var player : risk.players()) {
      assertTrue(risk.hasConquestBonus(player) || player == risk.currentPlayer(), player.name());
    }
    var first = risk.players().get(0);
    assertEquals(risk.reinforcementArmies(first) + Risk.CONQUEST_BONUS, ((TurnState.Reinforcing) risk.currentTurnState()).totalArmies());
    var off = new RiskConfig(3).createRisk();
    off.start();
    assertEquals(off.reinforcementArmies(first), ((TurnState.Reinforcing) off.currentTurnState()).totalArmies());
  }

  @Test
  public void eliminatingAPlayerGivesTheEliminationBonusTheNextTurn() {
    var risk = new RiskConfig(3).withRules(new Rules(false, true, true)).createRisk();
    var first = risk.players().get(0);
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(first, 2);
    }
    risk.getTerritory("Alberta").setOccupant(risk.players().get(1), 1);
    risk.getTerritory("Japan").setOccupant(risk.players().get(2), 2);
    risk.getTerritory("Alaska").setOccupant(first, 6);
    risk.setDice(new DeterministicDice(List.of(6, 6, 6, 1)));
    risk.start();
    risk.nextTurnState();

    conquerAlberta(risk);

    assertFalse(risk.isAlive(risk.players().get(1)));
    int continents = risk.occupiedContinents(first).stream().mapToInt(Continent::bonusPoints).sum();
    int expected = Risk.ALWAYS_REINFORCEMENT + continents + Risk.CONQUEST_BONUS + Risk.ELIMINATION_BONUS;
    // the third player is next, since the second is out
    do {
      risk.nextTurnState();
    } while (risk.currentPlayer() == first);
    assertEquals(risk.players().get(2), risk.currentPlayer());
    do {
      risk.nextTurnState();
    } while (risk.currentPlayer() != first);
    assertEquals(expected, ((TurnState.Reinforcing) risk.currentTurnState()).totalArmies());
  }

  @Test
  public void theNumbersOfArmiesCanBeSet() {
    var rules = new Rules(false, true, true, 4, 0, 7, 9);
    var risk = new RiskConfig(2).withRules(rules).createRisk();
    assertEquals(rules.alwaysArmies() + risk.occupiedContinents(risk.players().get(0)).stream().mapToInt(Continent::bonusPoints).sum(),
        risk.reinforcementArmies(risk.players().get(0)));
    assertEquals(Risk.BASE_REINFORCEMENT, Rules.DEFAULT.baseArmies());
  }

  @Test
  public void eliminationBonusWorksWithoutTheConquestBonus() {
    var risk = new RiskConfig(3).withRules(new Rules(false, false, true)).createRisk();
    var first = risk.players().get(0);
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(first, 2);
    }
    risk.getTerritory("Alberta").setOccupant(risk.players().get(1), 1);
    risk.getTerritory("Japan").setOccupant(risk.players().get(2), 2);
    risk.getTerritory("Alaska").setOccupant(first, 6);
    risk.setDice(new DeterministicDice(List.of(6, 6, 6, 1)));
    risk.start();
    risk.nextTurnState();
    conquerAlberta(risk);
    assertFalse(risk.hasConquestBonus(first));
    do {
      risk.nextTurnState();
    } while (risk.currentPlayer() == first);
    do {
      risk.nextTurnState();
    } while (risk.currentPlayer() != first);
    assertEquals(risk.reinforcementArmies(first) + Risk.ELIMINATION_BONUS, ((TurnState.Reinforcing) risk.currentTurnState()).totalArmies());
  }

  @Test
  public void conquestWithoutEliminationGivesNoEliminationBonus() {
    var risk = alaska6AgainstAlberta1(new Rules(false, true, false));
    conquerAlberta(risk);
    assertEquals(risk.reinforcementArmies(risk.players().get(0)) + Risk.CONQUEST_BONUS, armiesAtNextTurnOfFirstPlayer(risk));
  }
}
