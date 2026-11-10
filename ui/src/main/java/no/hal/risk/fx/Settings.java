package no.hal.risk.fx;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * The settings that are kept between runs: the defaults are a resource of the application, {@code /defaults.properties}, and what the
 * user has changed is saved in a file, which has only the settings that differ from the defaults. A change is saved when it is made, since
 * a phone may end the app without telling it. Without a file, as for the users of a web app that share a server, the settings are
 * only kept as long as the program runs. A problem with the file is reported, but never stops the program: it is the defaults then.
 */
public class Settings {

  private final Properties defaults = new Properties();
  /** What differs from the defaults. */
  private final Properties changed = new Properties();
  private final Path file;

  /** The file where changes are saved, which is read now if it is there, or null for none. */
  public Settings(Path file) {
    this.file = file;
    try (InputStream stream = Settings.class.getResourceAsStream("/defaults.properties")) {
      if (stream != null) {
        defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
      } else {
        System.err.println("The defaults of the settings are missing");
      }
    } catch (IOException e) {
      System.err.println("The defaults of the settings could not be read: " + e);
    }
    if (file != null && Files.exists(file)) {
      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
        changed.load(reader);
      } catch (IOException | IllegalArgumentException e) {
        System.err.println("The settings could not be read from " + file + ": " + e);
        changed.clear();
      }
    }
  }

  /** Where the settings are saved by default: a file in the home directory, which on Android is the private directory of the app. */
  public static Path defaultFile() {
    return Path.of(System.getProperty("user.home"), ".riskfx", "settings.properties");
  }

  /** The value of a setting, which is the default if it hasn't been changed. */
  public String get(String key) {
    return changed.getProperty(key, defaults.getProperty(key));
  }

  /** The default value of a setting. */
  public String getDefault(String key) {
    return defaults.getProperty(key);
  }

  public boolean getBoolean(String key) {
    return Boolean.parseBoolean(get(key));
  }

  /** The value as a number, or the default if it isn't one. */
  public double getDouble(String key) {
    try {
      return Double.parseDouble(get(key));
    } catch (NumberFormatException | NullPointerException e) {
      return Double.parseDouble(getDefault(key));
    }
  }

  public void set(String key, boolean value) {
    set(key, String.valueOf(value));
  }

  /** Changes a setting and saves it, if it is a change. */
  public void set(String key, String value) {
    if (value.equals(get(key))) {
      return;
    }
    if (value.equals(defaults.getProperty(key))) {
      changed.remove(key);
    } else {
      changed.setProperty(key, value);
    }
    save();
  }

  private void save() {
    if (file == null) {
      return;
    }
    try {
      Files.createDirectories(file.getParent());
      try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
        changed.store(writer, "The settings that differ from the defaults of RiskFx");
      }
    } catch (IOException e) {
      System.err.println("The settings could not be saved to " + file + ": " + e);
    }
  }
}
