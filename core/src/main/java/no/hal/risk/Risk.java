package no.hal.risk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

public class Risk {
  
  private final World world;
  private final List<Player> players;

  private final FortifyMode fortifyMode;
  private final Rules rules;

  public Risk(List<Continent> continents, Map<Territory, Set<Territory>> borders, List<Player> players, FortifyMode fortifyMode) {
    this(continents, borders, players, fortifyMode, Rules.DEFAULT);
  }

  public Risk(List<Continent> continents, Map<Territory, Set<Territory>> borders, List<Player> players, FortifyMode fortifyMode, Rules rules) {
    this.fortifyMode = fortifyMode;
    this.rules = rules;
    this.world = new World(continents, continents.stream()
        .flatMap(continent -> continent.territories().stream())
        .toList(), borders);
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

  public boolean areAdjacent(Territory from, Territory to) {
    return world.areAdjacent(from, to);
  }

  /**
   * The territories that can be reached from a territory by moving armies through territories with the same occupant.
   */
  private Set<Territory> reachableTerritories(Territory territory) {
    var reachable = new HashSet<Territory>();
    var queue = new ArrayDeque<Territory>();
    queue.add(territory);
    while (! queue.isEmpty()) {
      var current = queue.remove();
      for (var neighbour : world.neighbours(current)) {
        if (neighbour.occupant() == territory.occupant() && reachable.add(neighbour)) {
          queue.add(neighbour);
        }
      }
    }
    reachable.remove(territory);
    return reachable;
  }

  public FortifyMode fortifyMode() {
    return fortifyMode;
  }

  public List<Territory> territories(Predicate<Territory> test) {
    return allTerritories().stream()
        .filter(test)
        .toList();
  }

  public List<Territory> playerTerritories(Player player, Territory except) {
    return territories(territory -> territory.occupant() == player && territory != except);
  }
  public List<Territory> playerTerritories(Player player) {
    return playerTerritories(player, null);
  }

  public List<Territory> othersTerritories(Player player) {
    return territories(territory -> territory.occupant() != player);
  }

  /**
   * Territories connected to a territory, in the order of allTerritories().
   * @param sameOccupant true: territories armies can be moved to when fortifying, according to the {@link FortifyMode},
   *   false: adjacent territories with another occupant (for attack), null: all adjacent territories
   */
  public List<Territory> connectedTerritories(Territory territory, Boolean sameOccupant) {
    if (sameOccupant != null && sameOccupant && fortifyMode == FortifyMode.CONNECTED) {
      var reachable = reachableTerritories(territory);
      return territories(reachable::contains);
    }
    return territories(otherTerritory -> areAdjacent(otherTerritory, territory)
        && (sameOccupant == null || sameOccupant == (otherTerritory.occupant() == territory.occupant())));
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

  private Random random = new Random();

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
      Player occupant = occupants.get(random.nextInt(occupants.size()));
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
        var territory = territories.get(random.nextInt(territories.size()));
        territory.transferTo(1);
        fireTerritoryUpdated(territory);
        remaingingArmies--;
      }
    }
  }

  /** The default for {@link Rules#baseArmies()}: the armies every turn gives, when the conquest bonus rule is off. */
  public static final int BASE_REINFORCEMENT = 3;

  /** The default for {@link Rules#alwaysArmies()}: the armies every turn gives, with {@link Rules#conquestBonus()}. */
  public static final int ALWAYS_REINFORCEMENT = 1;

  /** The default for {@link Rules#conquestArmies()}: the armies a turn gives only if a territory was conquered the turn before. */
  public static final int CONQUEST_BONUS = 2;

  /** The default for {@link Rules#eliminationArmies()}: the armies a turn gives for each player eliminated the turn before. */
  public static final int ELIMINATION_BONUS = 5;

  public Rules rules() {
    return rules;
  }

  /** The players who conquered a territory in their last turn, and haven't had the bonus for it yet. */
  private final Set<Player> earnedConquestBonus = new HashSet<>();

  /** The number of players each player eliminated in their last turn. */
  private final Map<Player, Integer> earnedEliminations = new HashMap<>();

  /** Whether the player gets the conquest bonus at the start of the next turn. */
  public boolean hasConquestBonus(Player player) {
    return earnedConquestBonus.contains(player);
  }

  /**
   * The occupant of a continent, i.e. the player occupying all its territories, or null if there is none.
   */
  public Player continentOccupant(Continent continent) {
    var occupant = continent.territories().getFirst().occupant();
    return continent.territories().stream().allMatch(territory -> territory.occupant() == occupant) ? occupant : null;
  }

  public List<Continent> occupiedContinents(Player player) {
    return continents().stream()
        .filter(continent -> continentOccupant(continent) == player)
        .toList();
  }

  /**
   * The number of armies a player gets at the start of a turn, apart from the conquest bonus: the base number plus the bonus of each occupied continent.
   */
  public int reinforcementArmies(Player player) {
    return (rules.conquestBonus() ? rules.alwaysArmies() : rules.baseArmies()) + occupiedContinents(player).stream().mapToInt(Continent::bonusPoints).sum();
  }

  /**
   * A player is dead when it occupies no territories.
   */
  public boolean isAlive(Player player) {
    return allTerritories().stream().anyMatch(territory -> territory.occupant() == player);
  }

  public List<Player> alivePlayers() {
    return players.stream().filter(this::isAlive).toList();
  }

  public boolean isGameOver() {
    return alivePlayers().size() <= 1;
  }

  public Player winner() {
    var alivePlayers = alivePlayers();
    return alivePlayers.size() == 1 ? alivePlayers.getFirst() : null;
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
    // skip dead players
    do {
      currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
    } while (! isAlive(currentPlayer()));
    currentTurnState = null;
  }

  void updateTurnState(TurnState turnState) {
    currentTurnState = turnState;
    firePlayerTurnStateUpdated();
  }

  /** The armies a player places at the start of a turn, including the bonuses earned the turn before, which are then used up. */
  private int takeTurnArmies(Player player) {
    int eliminations = earnedEliminations.getOrDefault(player, 0);
    earnedEliminations.remove(player);
    return reinforcementArmies(player)
        + (earnedConquestBonus.remove(player) ? rules.conquestArmies() : 0)
        + eliminations * rules.eliminationArmies();
  }

  public void nextTurnState() {
    pendingOccupation = null;
    if (isGameOver()) {
      if (! (currentTurnState instanceof TurnState.GameOver)) {
        updateTurnState(new TurnState.GameOver(winner()));
      }
      return;
    }
    var nextTurnState = switch (currentTurnState) {
      case null -> new TurnState.Reinforcing(takeTurnArmies(currentPlayer()));
      case TurnState.Reinforcing _ -> new TurnState.Attacking();
      case TurnState.Attacking _ -> new TurnState.Fortifying(null, null);
      case TurnState.Fortifying _ -> null;
      case TurnState.GameOver _ -> currentTurnState;
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

  /**
   * Starts a new game, using the random source for the initial occupation and for the dice.
   */
  public void reset(Random random, float initialArmiesPrTerritory) {
    this.random = random;
    this.dice = new UniformDice(random);
    distributeInitialArmies(initialArmiesPrTerritory);
    start();
  }

  public void start() {
    earnedConquestBonus.clear();
    earnedEliminations.clear();
    if (rules.conquestBonus()) {
      // in the first round, everyone counts as having conquered a territory in the round before
      earnedConquestBonus.addAll(players);
    }
    pendingOccupation = null;
    currentPlayerIndex = 0;
    currentTurnState = null;
    nextTurnState();
  }

  private void checkOccupiedByCurrentPlayer(Territory territory, String role) {
    if (territory.occupant() != currentPlayer()) {
      throw new IllegalArgumentException("The %s territory %s is not occupied by the current player %s".formatted(
          role, territory.name(), currentPlayer().name()));
    }
  }

  void reinforce(Territory territory, int numArmies) {
    checkOccupiedByCurrentPlayer(territory, "reinforced");
    if (numArmies < 1) {
      throw new IllegalArgumentException("Must reinforce with at least one army: " + numArmies);
    }
    territory.transferTo(numArmies);
    fireTerritoryUpdated(territory);
  }

  private Dice dice = new UniformDice();

  void setDice(Dice dice) {
    this.dice = dice;
  }

  public int maxAttackerDies(Territory attacker) {
    return Math.min(3, attacker.numArmies() - 1);
  }

  public int maxDefenderDies(Territory defender) {
    return Math.min(2, defender.numArmies());
  }

  public int maxFortifyNum(Territory from) {
    return from.numArmies() - 1;
  }

  boolean attack(Territory attacker, Territory defender, int numAttackerDies, int numDefenderDies) {
    checkOccupiedByCurrentPlayer(attacker, "attacking");
    if (numAttackerDies < 1 || numDefenderDies < 1) {
      throw new IllegalArgumentException("Must attack and defend with at least one die: " + numAttackerDies + ", " + numDefenderDies);
    }
    if (! areAdjacent(attacker, defender)) {
      throw new IllegalArgumentException("Territories in an attack must be adjacent: " + attacker.name() + " !~ " + defender.name());
    }
    if (attacker.occupant() == defender.occupant()) {
      throw new IllegalArgumentException("Cannot attack own territory: " + attacker.name() + " -> " + defender.name());
    }
    if (numAttackerDies > maxAttackerDies(attacker)) {
      throw new IllegalArgumentException("Cannot attack with more armies than " + maxAttackerDies(attacker) + ": " + numAttackerDies + " >= " + attacker.numArmies());
    }
    if (numDefenderDies > maxDefenderDies(defender)) {
      throw new IllegalArgumentException("Cannot defend with more armies than " + maxDefenderDies(defender) + ": " + numDefenderDies + " > " + defender.numArmies());
    }
    pendingOccupation = null;
    Battle battle = new Battle(attacker, defender, numAttackerDies, numDefenderDies);
    var result = battle.getResult(dice);
    if (result.attackerLoss() > 0) {
      attacker.acceptLoss(result.attackerLoss());
    }
    boolean defenceSucceded = result.defenderLoss() < defender.numArmies();
    if (defenceSucceded) {
      if (result.defenderLoss() > 0) {
        defender.acceptLoss(result.defenderLoss());
      }
    } else {
      var allNum = attacker.numArmies() - 1;
      // the armies that have to move in are at least the dice rolled, unless the rule lets all move in
      var transferNum = rules.keepArmiesBack() ? Math.min(numAttackerDies, allNum) : allNum;
      attacker.transferFrom(transferNum);
      var defeatedPlayer = defender.occupant();
      defender.setOccupant(attacker.occupant(), transferNum);
      if (transferNum < allNum) {
        pendingOccupation = new Occupation(attacker, defender, allNum - transferNum);
      }
      if (rules.conquestBonus()) {
        earnedConquestBonus.add(attacker.occupant());
      }
      if (rules.eliminationBonus() && ! isAlive(defeatedPlayer)) {
        earnedEliminations.merge(attacker.occupant(), 1, Integer::sum);
      }
    }
    fireTerritoryUpdated(attacker);
    fireTerritoryUpdated(defender);
    return ! defenceSucceded;
  }

  /** A conquered territory that more armies can be moved into, from the territory it was conquered from. */
  public record Occupation(Territory from, Territory to, int maxExtra) {
  }

  private Occupation pendingOccupation;

  /** The last conquest, if more armies can be moved in. It is gone after the next attack or when the attack ends. */
  public Occupation pendingOccupation() {
    return pendingOccupation;
  }

  /** Moves more armies into the territory conquered last, from where it was conquered from. */
  public void occupy(int extra) {
    if (pendingOccupation == null) {
      throw new IllegalArgumentException("No territory was just conquered");
    }
    if (extra < 1 || extra > pendingOccupation.maxExtra()) {
      throw new IllegalArgumentException("Can move 1 to %d more armies, not %d".formatted(pendingOccupation.maxExtra(), extra));
    }
    var occupation = pendingOccupation;
    pendingOccupation = null;
    occupation.from().transferFrom(extra);
    occupation.to().transferTo(extra);
    fireTerritoryUpdated(occupation.from());
    fireTerritoryUpdated(occupation.to());
  }

  void fortify(Territory from, Territory to, int numArmies) {
    checkOccupiedByCurrentPlayer(from, "source");
    checkOccupiedByCurrentPlayer(to, "target");
    if (numArmies < 1) {
      throw new IllegalArgumentException("Must transfer at least one army: " + numArmies);
    }
    if (from == to) {
      throw new IllegalArgumentException("Territories in a transfer must be different: " + from.name() + " == " + to.name());
    }
    if (from.occupant() != to.occupant()) {
      throw new IllegalArgumentException("Territories in a transfer must have same occupant: " + from.occupant().name() + " != " + to.occupant().name());
    }
    if (! connectedTerritories(from, true).contains(to)) {
      throw new IllegalArgumentException("Territories in a transfer must be %s: %s !~ %s".formatted(
          fortifyMode == FortifyMode.ADJACENT ? "adjacent" : "connected through own territories", from.name(), to.name()));
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
