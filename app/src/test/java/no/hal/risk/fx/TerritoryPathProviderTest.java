package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.hal.risk.GameMap;
import no.hal.risk.RiskConfig;
import no.hal.risk.Territory;
import org.junit.jupiter.api.Test;

public class TerritoryPathProviderTest {

  private List<String> territoryNames(GameMap map) {
    return new RiskConfig(2).withMap(map).createRisk().allTerritories().stream().map(Territory::name).toList();
  }

  @Test
  public void everyTerritoryHasAShapeAndALabelPointOnTheEuropeMap() {
    var provider = new TerritoryPathProvider(GameMap.EUROPE.shapesResource());
    for (var name : territoryNames(GameMap.EUROPE)) {
      assertNotNull(provider.getPath(name), name);
      assertNotNull(provider.getLabelPoint(name), name);
    }
  }

  @Test
  public void shapesInNoRegionAreInactive() {
    var provider = new TerritoryPathProvider(GameMap.EUROPE.shapesResource());
    var inactive = provider.inactive(territoryNames(GameMap.EUROPE)).stream().map(TerritoryPathProvider.Inactive::name).toList();
    assertTrue(inactive.containsAll(List.of("Switzerland", "Belarus", "Turkey")), inactive.toString());
    assertTrue(inactive.stream().noneMatch(territoryNames(GameMap.EUROPE)::contains));
  }

  @Test
  public void theClassicMapHasNoInactiveShapes() {
    var provider = new TerritoryPathProvider();
    assertEquals(List.of(), provider.inactive(territoryNames(GameMap.CLASSIC)));
  }

  @Test
  public void territoriesThatMeetOnTheMapNeedNoLineBetweenThem() {
    var provider = new TerritoryPathProvider(GameMap.EUROPE.shapesResource());
    assertTrue(provider.meet("Germany", "France"));
    assertTrue(! provider.meet("Scotland", "Norway"));
  }
}
