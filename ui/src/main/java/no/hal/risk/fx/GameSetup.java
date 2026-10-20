package no.hal.risk.fx;

import java.util.ArrayList;
import java.util.List;
import no.hal.risk.FortifyMode;
import no.hal.risk.GameMap;
import no.hal.risk.RiskConfig;
import no.hal.risk.Rules;

/**
 * Which players take part in a game, and what controls each of them.
 */
public record GameSetup(List<PlayerKind> kinds) {

  public static final int MIN_PLAYERS = 2;
  public static final int MAX_PLAYERS = 4;

  public GameSetup {
    if (kinds.size() < MIN_PLAYERS || kinds.size() > MAX_PLAYERS) {
      throw new IllegalArgumentException("A game has %d to %d players, not %d".formatted(MIN_PLAYERS, MAX_PLAYERS, kinds.size()));
    }
    kinds = List.copyOf(kinds);
  }

  public static GameSetup of(PlayerKind... kinds) {
    return new GameSetup(List.of(kinds));
  }

  public int playerCount() {
    return kinds.size();
  }

  public boolean hasHuman() {
    return kinds.contains(PlayerKind.HUMAN);
  }

  public boolean hasAi() {
    return kinds.contains(PlayerKind.AI);
  }

  /**
   * The rule for fortification: armies are moved to adjacent territories only, for all players.
   * The AI was trained for that, so it is also what makes it fair for a human playing against it.
   */
  public FortifyMode fortifyMode() {
    return FortifyMode.ADJACENT;
  }

  /** The configuration of the game itself. */
  public RiskConfig riskConfig(FortifyMode fortifyMode) {
    return riskConfig(fortifyMode, Rules.DEFAULT);
  }

  public RiskConfig riskConfig(FortifyMode fortifyMode, Rules rules) {
    return riskConfig(fortifyMode, rules, GameMap.CLASSIC);
  }

  public RiskConfig riskConfig(FortifyMode fortifyMode, Rules rules, GameMap map) {
    return new RiskConfig(kinds.size()).withMap(map).withFortifyMode(fortifyMode).withRules(rules);
  }

  /**
   * A choice of setup, either a number of players or a ready-made combination, which fills in the kind of each player.
   * The kinds can then be changed one by one.
   */
  public record Preset(String name, GameSetup setup) {

    @Override
    public String toString() {
      return name;
    }
  }

  /** The number of players first, with the first player human and the others AI, then combinations. */
  public static List<Preset> presets() {
    var presets = new ArrayList<Preset>();
    presets.add(new Preset("2 players", of(PlayerKind.HUMAN, PlayerKind.AI)));
    presets.add(new Preset("3 players", of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.AI)));
    presets.add(new Preset("4 players", of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.AI, PlayerKind.AI)));
    presets.add(new Preset("Human against AI", of(PlayerKind.HUMAN, PlayerKind.AI)));
    presets.add(new Preset("Human against greedy bot", of(PlayerKind.HUMAN, PlayerKind.GREEDY)));
    presets.add(new Preset("Human against cautious bot", of(PlayerKind.HUMAN, PlayerKind.CAUTIOUS)));
    presets.add(new Preset("Human against AI and greedy bot", of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.GREEDY)));
    presets.add(new Preset("Human against AI and two greedy bots", of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.GREEDY)));
    presets.add(new Preset("Watch AI against greedy bot", of(PlayerKind.AI, PlayerKind.GREEDY)));
    presets.add(new Preset("Watch AI against cautious bot", of(PlayerKind.AI, PlayerKind.CAUTIOUS)));
    presets.add(new Preset("Watch cautious bot against greedy bot", of(PlayerKind.CAUTIOUS, PlayerKind.GREEDY)));
    presets.add(new Preset("Watch AI against 3 greedy bots", of(PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.GREEDY, PlayerKind.GREEDY)));
    presets.add(new Preset("Watch 4 AI players", of(PlayerKind.AI, PlayerKind.AI, PlayerKind.AI, PlayerKind.AI)));
    return presets;
  }
}
