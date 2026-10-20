package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.hal.risk.FortifyMode;
import org.junit.jupiter.api.Test;

public class GameSetupTest {

  @Test
  public void testASetupHasTwoToFourPlayers() {
    assertThrows(IllegalArgumentException.class, () -> new GameSetup(List.of()));
    assertThrows(IllegalArgumentException.class, () -> GameSetup.of(PlayerKind.HUMAN));
    assertThrows(IllegalArgumentException.class, () -> GameSetup.of(PlayerKind.AI, PlayerKind.AI, PlayerKind.AI, PlayerKind.AI, PlayerKind.AI));
    assertEquals(2, GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI).playerCount());
    assertEquals(4, GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.AI).playerCount());
  }

  @Test
  public void testQueries() {
    var setup = GameSetup.of(PlayerKind.HUMAN, PlayerKind.GREEDY);
    assertTrue(setup.hasHuman());
    assertFalse(setup.hasAi());
    assertTrue(GameSetup.of(PlayerKind.AI, PlayerKind.GREEDY).hasAi());
    assertFalse(GameSetup.of(PlayerKind.AI, PlayerKind.GREEDY).hasHuman());
  }

  @Test
  public void testConfigurationOfTheGame() {
    var config = GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI, PlayerKind.AI).riskConfig(FortifyMode.CONNECTED);
    assertEquals(3, config.playerCount());
    assertEquals(FortifyMode.CONNECTED, config.fortifyMode());
  }

  @Test
  public void testPresetsStartWithTheNumbersOfPlayersFromTwoToFour() {
    var presets = GameSetup.presets();
    for (int count = 2; count <= 4; count++) {
      assertEquals(count, presets.get(count - 2).setup().playerCount(), presets.get(count - 2).name());
    }
    // a human against the others, to start with
    assertEquals(PlayerKind.HUMAN, presets.get(2).setup().kinds().getFirst());
    assertEquals(List.of(PlayerKind.AI, PlayerKind.AI, PlayerKind.AI), presets.get(2).setup().kinds().subList(1, 4));
  }

  @Test
  public void testPresetsIncludeReadyMadeCombinations() {
    var byName = GameSetup.presets().stream().collect(java.util.stream.Collectors.toMap(GameSetup.Preset::name, GameSetup.Preset::setup));
    assertEquals(GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI), byName.get("Human against AI"));
    assertEquals(GameSetup.of(PlayerKind.HUMAN, PlayerKind.GREEDY), byName.get("Human against greedy bot"));
    assertEquals(GameSetup.of(PlayerKind.AI, PlayerKind.GREEDY), byName.get("Watch AI against greedy bot"));
    assertEquals(GameSetup.of(PlayerKind.HUMAN, PlayerKind.CAUTIOUS), byName.get("Human against cautious bot"));
    assertEquals(GameSetup.of(PlayerKind.AI, PlayerKind.CAUTIOUS), byName.get("Watch AI against cautious bot"));
    assertEquals(GameSetup.of(PlayerKind.CAUTIOUS, PlayerKind.GREEDY), byName.get("Watch cautious bot against greedy bot"));
    assertEquals(4, byName.get("Watch 4 AI players").playerCount());
    assertFalse(byName.get("Watch 4 AI players").hasHuman());
  }

  @Test
  public void testAllPresetsAreValidAndHaveDistinctNames() {
    var presets = GameSetup.presets();
    assertEquals(presets.size(), presets.stream().map(GameSetup.Preset::name).distinct().count());
    assertTrue(presets.stream().allMatch(preset -> preset.setup().playerCount() >= 2 && preset.setup().playerCount() <= 4));
  }

  @Test
  public void testFortificationIsToAdjacentTerritoriesForEveryone() {
    assertEquals(FortifyMode.ADJACENT, GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI).fortifyMode());
    assertEquals(FortifyMode.ADJACENT, GameSetup.of(PlayerKind.HUMAN, PlayerKind.GREEDY).fortifyMode());
    assertEquals(FortifyMode.ADJACENT, GameSetup.of(PlayerKind.HUMAN, PlayerKind.HUMAN).fortifyMode());
    assertEquals(FortifyMode.ADJACENT, GameSetup.of(PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.GREEDY).fortifyMode());
  }
}
