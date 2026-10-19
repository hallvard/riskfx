package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import no.hal.risk.GameMap;
import org.junit.jupiter.api.Test;

public class LabelOffsetsTest {

  @Test
  public void theFileForSavingIsFoundFromTheModuleOfTheApplicationAndFromTheRoot() {
    var file = PlayApp.findLabelOffsetsFile(GameMap.EUROPE.labelOffsetsFile());
    assertNotNull(file);
    assertTrue(file.endsWith("tools/europe/label-offsets.json"), file.toString());
    assertTrue(java.nio.file.Files.isDirectory(file.getParent()));
  }

  @Test
  public void aFileInADirectoryThatDoesNotExistIsNotFound() {
    assertNull(PlayApp.findLabelOffsetsFile("no/such/directory/label-offsets.json"));
  }

  @Test
  public void everyMapHasAFileForTheLabelsMovedByHand() {
    assertEquals("app/src/main/resources/label-offsets.json", GameMap.CLASSIC.labelOffsetsFile());
    assertEquals("tools/europe/label-offsets.json", GameMap.EUROPE.labelOffsetsFile());
    // the displacements are applied by the application for the classic map, which has no generator, and by the generator for Europe
    assertEquals("/label-offsets.json", GameMap.CLASSIC.labelOffsetsResource());
    assertNull(GameMap.EUROPE.labelOffsetsResource());
    var file = PlayApp.findLabelOffsetsFile(GameMap.CLASSIC.labelOffsetsFile());
    assertNotNull(file);
    assertTrue(file.endsWith("app/src/main/resources/label-offsets.json"), file.toString());
  }

  @Test
  public void aMapWithoutAGeneratorHasTheDisplacementsOfLabelsFromItsFile() {
    var provider = new TerritoryPathProvider(GameMap.CLASSIC.shapesResource(), "/test-label-offsets.json");
    assertEquals(5.0, provider.getLabelOffset("Alaska").getX(), 1e-9);
    assertEquals(-3.0, provider.getLabelOffset("Alaska").getY(), 1e-9);
    assertEquals(2.5, provider.getLabelOffset("Eastern Australia").getY(), 1e-9);
    assertEquals(0.0, provider.getLabelOffset("Brazil").getX(), 1e-9);
  }

  @Test
  public void noFileForTheDisplacementsMeansNone() {
    var provider = new TerritoryPathProvider(GameMap.CLASSIC.shapesResource(), "/no-such-label-offsets.json");
    assertEquals(0.0, provider.getLabelOffset("Alaska").magnitude(), 1e-9);
    assertEquals(0.0, new TerritoryPathProvider(GameMap.EUROPE.shapesResource()).getLabelOffset("Italy").magnitude(), 1e-9);
  }

  @Test
  public void theEuropeMapHasWhereLabelsAreByDefaultAndWhereTheyAre() {
    var provider = new TerritoryPathProvider(GameMap.EUROPE.shapesResource());
    for (var name : new String[] {"Italy", "England", "Norway"}) {
      assertNotNull(provider.getLabelBase(name), name);
      assertNotNull(provider.getLabelPoint(name), name);
    }
    // without any displacement by hand, they are the same
    assertEquals(provider.getLabelBase("Italy").getX(), provider.getLabelPoint("Italy").getX(), 1e-9);
  }
}
