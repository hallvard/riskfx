package no.hal.risk.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SettingsTest {

  @TempDir
  Path dir;

  @Test
  public void testDefaultsComeFromTheResource() {
    var settings = new Settings(null);
    assertTrue(settings.getBoolean("view.showNames"));
    assertFalse(settings.getBoolean("view.showRegions"));
    assertEquals(70, settings.getDouble("view.speed"));
    assertEquals("Classic", settings.get("game.map"));
    assertEquals("HUMAN", settings.get("game.seat1"));
    assertEquals("off", settings.get("game.seat4"));
  }

  @Test
  public void testAChangeIsSavedAndReadBack() throws IOException {
    var file = dir.resolve("a").resolve("settings.properties");
    var settings = new Settings(file);
    settings.set("view.showRegions", true);
    settings.set("view.speed", "30");
    assertTrue(Files.exists(file));
    var later = new Settings(file);
    assertTrue(later.getBoolean("view.showRegions"));
    assertEquals(30, later.getDouble("view.speed"));
    assertTrue(later.getBoolean("view.showNames"));
  }

  @Test
  public void testOnlyWhatDiffersFromTheDefaultsIsSaved() throws IOException {
    var file = dir.resolve("settings.properties");
    var settings = new Settings(file);
    settings.set("view.showRegions", true);
    settings.set("view.speed", "30");
    // back to the defaults: nothing to keep
    settings.set("view.speed", "70");
    var text = Files.readString(file);
    assertTrue(text.contains("view.showRegions=true"));
    assertFalse(text.contains("view.speed"));
    assertFalse(text.contains("view.showNames"));
  }

  @Test
  public void testNoFileKeepsTheChangesForTheRun() {
    var settings = new Settings(null);
    settings.set("game.map", "Europe");
    assertEquals("Europe", settings.get("game.map"));
  }

  @Test
  public void testAFileThatIsNotUsableGivesTheDefaults() throws IOException {
    var file = dir.resolve("settings.properties");
    Files.writeString(file, "view.speed=fast\nview.showNames=false\n\\u12");
    var settings = new Settings(file);
    assertTrue(settings.getBoolean("view.showNames"));
    // a value that isn't a number is the default
    Files.writeString(file, "view.speed=fast\n");
    assertEquals(70, new Settings(file).getDouble("view.speed"));
  }
}
