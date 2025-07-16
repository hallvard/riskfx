package no.hal.risk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Risk {
  
  private final World world;
  private final List<Player> players;

  public Risk(List<Continent> continents, List<Player> players) {
    this.world = new World(continents, continents.stream()
        .flatMap(continent -> continent.territories().stream())
        .toList());
    this.players = players;
  }

  public World world() {
    return this.world;
  }

  public List<Continent> continents() {
    return this.world.continents();
  }

  public List<Territory> allTerritories() {
    return this.world.allTerritories();
  }

  public List<Territory> playerTerritories(Player player, Territory except) {
    return allTerritories().stream()
        .filter(territory -> territory.occupant() == player)
        .filter(territory -> territory != except)
        .toList();
  }

  public Territory getTerritory(String name) {
    return allTerritories().stream()
        .filter(territory -> territory.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No territory with name: " + name));
  }

  public List<Player> players() {
    return players;
  }

  void distributeInitialArmies(float initialArmiesPrTerritory) {
    Map<Player, List<Territory>> playerTerritories = new HashMap<>();
    List<Player> occupants = null;
    // make sure that the number of armies is divisible by the number of players
    var remaingingArmies = initialArmiesPrTerritory * allTerritories().size() / players.size() * players.size();
    // decide initial occupants of territories
    for (Territory territory : allTerritories()) {
      if (occupants == null || occupants.isEmpty()) {
        occupants = new ArrayList<>(players);
      }
      Player occupant = occupants.get((int) (Math.random() * occupants.size()));
      territory.setOccupant(occupant, 1);
      fireTerritoryUpdated(territory);
      occupants.remove(occupant);
      remaingingArmies--;
      playerTerritories.computeIfAbsent(occupant, key -> new ArrayList<>()).add(territory);
    }
    // distribute remaining armies
    while (remaingingArmies > 0) {
      for (Player player : players) {
        List<Territory> territories = playerTerritories.get(player);
        // select territory receiving the next army for a player
        var territory = territories.get((int) (Math.random() * territories.size()));
        territory.transferTo(1);
        fireTerritoryUpdated(territory);
        remaingingArmies--;
      }
    }
  }

  private int currentPlayerIndex = 0;
  private TurnState currentTurnState = null;

  public Player currentPlayer() {
    return players.get(currentPlayerIndex);
  }

  public TurnState currentTurnState() {
    return currentTurnState;
  }

  private void nextPlayer() {
    currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
  }

  void updateTurnState(TurnState turnState) {
    currentTurnState = turnState;
    firePlayerTurnStateUpdated();
  }

  public void nextTurnState() {
    var nextTurnState = switch (currentTurnState) {
      case null -> new TurnState.Reinforcing(3);
      case TurnState.Reinforcing reinforcing -> new TurnState.Attacking();
      case TurnState.Attacking attacking -> new TurnState.Fortifying(null, null);
      case TurnState.Fortifying fortifying -> null;
    };
    if (nextTurnState == null) {
      nextPlayer();
      nextTurnState();
    } else {
      updateTurnState(nextTurnState);
    }
  }

  private Collection<RiskListener> riskListeners = new ArrayList<>();

  public void addRiskListener(RiskListener riskListener) {
    riskListeners.add(riskListener);
  }

  void firePlayerTurnStateUpdated() {
    for (RiskListener riskListener : riskListeners) {
      riskListener.playerTurnStateUpdated(this, currentPlayer(), currentTurnState());
    }
  }

  void fireTerritoryUpdated(Territory territory) {
    for (RiskListener riskListener : riskListeners) {
      riskListener.territoryUpdated(this, territory);
    }
  }

  public void start() {
    currentPlayerIndex = 0;
    currentTurnState = null;
    nextTurnState();
  }

  void reinforce(Territory territory, int numArmies) {
    territory.transferTo(numArmies);
    fireTerritoryUpdated(territory);
  }

  private Dice dice = new UniformDice();

  void attack(Territory attacker, Territory defender, int numAttackerDies, int numDefenderDies) {
    Battle battle = new Battle(attacker, defender, numAttackerDies, numDefenderDies);
    var result = battle.getResult(dice);
    attacker.acceptLoss(result.attackerLoss());
    defender.acceptLoss(result.defenderLoss());
    fireTerritoryUpdated(attacker);
    fireTerritoryUpdated(defender);
  }

  void fortify(Territory from, Territory to, int numArmies) {
    if (from == to) {
      throw new IllegalArgumentException("Territories in a transfer must be different: " + from.name() + " == " + to.name());
    }
    if (from.occupant() != to.occupant()) {
      throw new IllegalArgumentException("Territories in a transfer must have same occupant: " + from.occupant().name() + " != " + to.occupant().name());
    }
    if (numArmies >= from.numArmies()) {
      throw new IllegalArgumentException("Cannot transfer all armies: " + numArmies + " >= " + from.numArmies());
    }
    from.transferFrom(numArmies);
    to.transferTo(numArmies);
    fireTerritoryUpdated(from);
    fireTerritoryUpdated(to);
  }
}
