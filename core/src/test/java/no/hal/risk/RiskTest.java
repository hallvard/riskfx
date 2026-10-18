package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

public class RiskTest {

  private Risk createRisk(int playerCount) {
    return new RiskConfig(playerCount).createRisk();
  }

  /** Gives all territories to the first player, except the given ones, which go to the second player. */
  private void occupy(Risk risk, Territory... others) {
    var first = risk.players().get(0);
    var second = risk.players().get(1);
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(first, 2);
    }
    for (var territory : others) {
      territory.setOccupant(second, 2);
    }
  }

  // borders

  @Test
  public void testBordersAreSymmetricAndComplete() {
    var risk = createRisk(2);
    int borderCount = 0;
    for (var territory : risk.allTerritories()) {
      var neighbours = risk.world().neighbours(territory);
      assertFalse(neighbours.isEmpty(), territory.name() + " has no neighbours");
      assertFalse(neighbours.contains(territory), territory.name() + " borders itself");
      for (var neighbour : neighbours) {
        assertTrue(risk.areAdjacent(neighbour, territory), territory.name() + " -> " + neighbour.name() + " is not symmetric");
      }
      borderCount += neighbours.size();
    }
    // the classic map has 42 territories and 83 borders
    assertEquals(83, borderCount / 2);
  }

  @Test
  public void testWorldIsConnected() {
    var risk = createRisk(2);
    var first = risk.allTerritories().getFirst();
    var visited = new HashSet<Territory>(List.of(first));
    var queue = new java.util.ArrayDeque<>(List.of(first));
    while (! queue.isEmpty()) {
      for (var neighbour : risk.world().neighbours(queue.remove())) {
        if (visited.add(neighbour)) {
          queue.add(neighbour);
        }
      }
    }
    assertEquals(risk.allTerritories().size(), visited.size());
  }

  @Test
  public void testBordersAcrossSea() {
    var risk = createRisk(2);
    assertTrue(risk.areAdjacent(risk.getTerritory("Alaska"), risk.getTerritory("Kamchatka")));
    assertTrue(risk.areAdjacent(risk.getTerritory("Brazil"), risk.getTerritory("North Africa")));
    assertTrue(risk.areAdjacent(risk.getTerritory("Siam"), risk.getTerritory("Indonesia")));
    assertFalse(risk.areAdjacent(risk.getTerritory("Alaska"), risk.getTerritory("Brazil")));
  }

  @Test
  public void testAttackMustBeBetweenAdjacentTerritoriesOfDifferentOccupants() {
    var risk = createRisk(2);
    var alaska = risk.getTerritory("Alaska");
    var brazil = risk.getTerritory("Brazil");
    var alberta = risk.getTerritory("Alberta");
    occupy(risk, brazil, alberta);
    assertThrows(IllegalArgumentException.class, () -> risk.attack(alaska, brazil, 1, 1));
    // Alaska and Alberta are adjacent, and now have different occupants
    risk.setDice(new DeterministicDice(List.of(1, 6)));
    risk.attack(alaska, alberta, 1, 1);
    // both are now occupied by the first player
    alberta.setOccupant(alaska.occupant(), 2);
    assertThrows(IllegalArgumentException.class, () -> risk.attack(alaska, alberta, 1, 1));
  }

  @Test
  public void testConnectedTerritories() {
    var risk = createRisk(2);
    var alaska = risk.getTerritory("Alaska");
    var alberta = risk.getTerritory("Alberta");
    var northwest = risk.getTerritory("Northwest Territory");
    var kamchatka = risk.getTerritory("Kamchatka");
    occupy(risk, alberta);
    // attackable: adjacent with other occupant
    assertEquals(List.of(alberta), risk.connectedTerritories(alaska, false));
    assertEquals(List.of(alberta, northwest, kamchatka), risk.connectedTerritories(alaska, null));
    // fortification: adjacent with the same occupant, never the territory itself
    assertEquals(List.of(northwest, kamchatka), risk.connectedTerritories(alaska, true));
  }

  @Test
  public void testFortifyRequiresAdjacentTerritories() {
    var risk = createRisk(2);
    var alaska = risk.getTerritory("Alaska");
    var kamchatka = risk.getTerritory("Kamchatka");
    var alberta = risk.getTerritory("Alberta");
    var siberia = risk.getTerritory("Siberia");
    occupy(risk, alberta);
    // adjacent, same occupant
    risk.fortify(alaska, kamchatka, 1);
    assertEquals(1, alaska.numArmies());
    assertEquals(3, kamchatka.numArmies());
    // same occupant and connected through own territories, but not adjacent
    assertThrows(IllegalArgumentException.class, () -> risk.fortify(kamchatka, siberia, 1));
    var brazil = risk.getTerritory("Brazil");
    assertThrows(IllegalArgumentException.class, () -> risk.fortify(kamchatka, brazil, 1));
    // adjacent, but another occupant
    assertThrows(IllegalArgumentException.class, () -> risk.fortify(alaska, alberta, 1));
  }

  @Test
  public void testConnectedFortificationReachesThroughOwnTerritories() {
    var risk = new RiskConfig(2).withFortifyMode(FortifyMode.CONNECTED).createRisk();
    var alaska = risk.getTerritory("Alaska");
    var alberta = risk.getTerritory("Alberta");
    var northwest = risk.getTerritory("Northwest Territory");
    var kamchatka = risk.getTerritory("Kamchatka");
    var siberia = risk.getTerritory("Siberia");
    var brazil = risk.getTerritory("Brazil");
    occupy(risk, alberta);
    // everything but Alberta, and never the territory itself
    var targets = risk.connectedTerritories(alaska, true);
    assertEquals(risk.allTerritories().size() - 2, targets.size());
    assertFalse(targets.contains(alaska));
    assertFalse(targets.contains(alberta));
    // attacks are still between neighbours
    assertEquals(List.of(alberta), risk.connectedTerritories(alaska, false));
    // far away, but connected through own territories
    risk.fortify(alaska, siberia, 1);
    assertEquals(1, alaska.numArmies());
    assertEquals(3, siberia.numArmies());
    assertEquals(FortifyMode.CONNECTED, risk.fortifyMode());
    // other occupant
    assertThrows(IllegalArgumentException.class, () -> risk.fortify(siberia, alberta, 1));
    // cut off Alaska from the rest, by giving all its neighbours to the other player
    occupy(risk, alberta, northwest, kamchatka);
    assertTrue(risk.connectedTerritories(alaska, true).isEmpty());
    assertThrows(IllegalArgumentException.class, () -> risk.fortify(alaska, brazil, 1));
  }

  @Test
  public void testAdjacentIsTheDefaultFortifyMode() {
    assertEquals(FortifyMode.ADJACENT, createRisk(2).fortifyMode());
  }

  // reinforcement bonus

  @Test
  public void testReinforcementBonusForOccupiedContinents() {
    var risk = createRisk(2);
    var australia = risk.continents().stream().filter(continent -> continent.name().equals("Australia")).findFirst().orElseThrow();
    occupy(risk, australia.territories().toArray(Territory[]::new));
    var first = risk.players().get(0);
    var second = risk.players().get(1);
    // second occupies Australia (bonus 2), first all the others (5 + 2 + 5 + 3 + 7)
    assertEquals(List.of(australia), risk.occupiedContinents(second));
    assertEquals(Risk.BASE_REINFORCEMENT + 2, risk.reinforcementArmies(second));
    assertEquals(Risk.BASE_REINFORCEMENT + 5 + 2 + 5 + 3 + 7, risk.reinforcementArmies(first));
  }

  @Test
  public void testNoBonusForPartiallyOccupiedContinent() {
    var risk = createRisk(2);
    var australia = risk.continents().stream().filter(continent -> continent.name().equals("Australia")).findFirst().orElseThrow();
    occupy(risk, australia.territories().getFirst());
    var second = risk.players().get(1);
    assertNull(risk.continentOccupant(australia));
    assertEquals(Risk.BASE_REINFORCEMENT, risk.reinforcementArmies(second));
  }

  @Test
  public void testReinforcingStartsWithBonus() {
    var risk = createRisk(2);
    var australia = risk.continents().stream().filter(continent -> continent.name().equals("Australia")).findFirst().orElseThrow();
    occupy(risk, australia.territories().toArray(Territory[]::new));
    risk.start();
    // first player starts
    assertEquals(new TurnState.Reinforcing(Risk.BASE_REINFORCEMENT + 5 + 2 + 5 + 3 + 7), risk.currentTurnState());
  }

  @Test
  public void testReinforcingCannotPlaceMoreThanTheTotal() {
    var risk = createRisk(2);
    risk.start();
    var territory = risk.playerTerritories(risk.currentPlayer()).getFirst();
    var before = territory.numArmies();
    var reinforcing = (TurnState.Reinforcing) risk.currentTurnState();
    reinforcing.reinforce(risk, territory, reinforcing.totalArmies() + 10);
    assertEquals(before + reinforcing.totalArmies(), territory.numArmies());
    assertTrue(risk.currentTurnState() instanceof TurnState.Attacking);
  }

  // dead players and game over

  @Test
  public void testDeadPlayersAreSkipped() {
    var risk = createRisk(3);
    var players = risk.players();
    // the second player occupies nothing
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(players.get(territory.name().length() % 2 == 0 ? 0 : 2), 2);
    }
    assertTrue(risk.isAlive(players.get(0)));
    assertFalse(risk.isAlive(players.get(1)));
    assertTrue(risk.isAlive(players.get(2)));
    assertEquals(List.of(players.get(0), players.get(2)), risk.alivePlayers());
    assertFalse(risk.isGameOver());
    risk.start();
    assertSame(players.get(0), risk.currentPlayer());
    risk.nextTurnState(); // attacking
    risk.nextTurnState(); // fortifying
    risk.nextTurnState(); // next player, skipping the dead one
    assertSame(players.get(2), risk.currentPlayer());
    assertTrue(risk.currentTurnState() instanceof TurnState.Reinforcing);
    risk.nextTurnState();
    risk.nextTurnState();
    risk.nextTurnState();
    assertSame(players.get(0), risk.currentPlayer());
  }

  @Test
  public void testGameOverWhenLastTerritoryIsConquered() {
    var risk = createRisk(2);
    var alaska = risk.getTerritory("Alaska");
    var alberta = risk.getTerritory("Alberta");
    occupy(risk, alberta);
    var first = risk.players().get(0);
    var second = risk.players().get(1);
    alaska.setOccupant(first, 4);
    alberta.setOccupant(second, 1);
    assertNull(risk.winner());
    risk.setDice(new DeterministicDice(List.of(6, 6, 6, 1)));
    risk.start();
    risk.nextTurnState(); // attacking
    var attacking = (TurnState.Attacking) risk.currentTurnState();

    assertTrue(attacking.attack(risk, alaska, alberta, 3, 1));

    assertFalse(risk.isAlive(second));
    assertTrue(risk.isGameOver());
    assertSame(first, risk.winner());
    assertEquals(new TurnState.GameOver(first), risk.currentTurnState());
    // the game stays over
    risk.nextTurnState();
    assertEquals(new TurnState.GameOver(first), risk.currentTurnState());
  }

  @Test
  public void testConqueringATerritoryDoesNotEndTheGameWhenOthersRemain() {
    var risk = createRisk(2);
    var alaska = risk.getTerritory("Alaska");
    var alberta = risk.getTerritory("Alberta");
    var japan = risk.getTerritory("Japan");
    occupy(risk, alberta, japan);
    var first = risk.players().get(0);
    alaska.setOccupant(first, 4);
    alberta.setOccupant(risk.players().get(1), 1);
    risk.setDice(new DeterministicDice(List.of(6, 6, 6, 1)));
    risk.start();
    risk.nextTurnState();
    var attacking = (TurnState.Attacking) risk.currentTurnState();

    assertTrue(attacking.attack(risk, alaska, alberta, 3, 1));

    assertSame(first, alberta.occupant());
    assertFalse(risk.isGameOver());
    assertEquals(new TurnState.Attacking(), risk.currentTurnState());
  }

  // only the current player can act, on own territories

  @Test
  public void testOnlyCurrentPlayerCanAct() {
    var risk = createRisk(2);
    var alaska = risk.getTerritory("Alaska");
    var alberta = risk.getTerritory("Alberta");
    var northwest = risk.getTerritory("Northwest Territory");
    var kamchatka = risk.getTerritory("Kamchatka");
    // the first player (current) occupies Alaska and Northwest Territory, the second Alberta and Kamchatka
    occupy(risk, alberta, kamchatka);
    assertSame(risk.players().get(0), risk.currentPlayer());
    // reinforce another player's territory
    assertThrows(IllegalArgumentException.class, () -> risk.reinforce(alberta, 1));
    // attack with another player's territory (alberta -> alaska is adjacent and between different occupants)
    assertThrows(IllegalArgumentException.class, () -> risk.attack(alberta, alaska, 1, 1));
    // fortify between another player's territories
    assertThrows(IllegalArgumentException.class, () -> risk.fortify(alberta, kamchatka, 1));
    // fortify between the current player's own territory and another player's
    assertThrows(IllegalArgumentException.class, () -> risk.fortify(alaska, kamchatka, 1));
    // nothing changed
    assertEquals(2, alberta.numArmies());
    assertEquals(2, alaska.numArmies());
    // the same actions are fine for the current player
    risk.reinforce(northwest, 1);
    assertEquals(3, northwest.numArmies());
    risk.fortify(northwest, alaska, 1);
    assertEquals(2, northwest.numArmies());
    assertEquals(3, alaska.numArmies());
  }

  @Test
  public void testActionsRequirePositiveNumbers() {
    var risk = createRisk(2);
    var alaska = risk.getTerritory("Alaska");
    var alberta = risk.getTerritory("Alberta");
    var northwest = risk.getTerritory("Northwest Territory");
    occupy(risk, alberta);
    assertThrows(IllegalArgumentException.class, () -> risk.reinforce(alaska, 0));
    assertThrows(IllegalArgumentException.class, () -> risk.reinforce(alaska, -1));
    assertThrows(IllegalArgumentException.class, () -> risk.attack(alaska, alberta, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> risk.attack(alaska, alberta, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> risk.fortify(alaska, northwest, 0));
  }
}
