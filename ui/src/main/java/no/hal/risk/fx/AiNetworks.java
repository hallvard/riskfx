package no.hal.risk.fx;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import no.hal.risk.FortifyMode;
import no.hal.risk.GameMap;
import no.hal.risk.RiskConfig;
import no.hal.risk.ai.Bot;
import no.hal.risk.ai.JavaPolicyNetwork;
import no.hal.risk.ai.NetworkBot;
import no.hal.risk.ai.ObservationEncoder;
import no.hal.risk.env.RiskEnv;

/**
 * The trained networks, read when first needed, and run in Java only. There is one for games of two players and one for games
 * of three or four, which is how they were trained. By default, they are those in the application, in the directory {@code /ai}
 * of its resources, but files with other ones can be used.
 */
public class AiNetworks {

  private static final String TWO_PLAYERS_RESOURCE = "/ai/ai-2p.weights";
  private static final String MORE_PLAYERS_RESOURCE = "/ai/ai-4p.weights";

  private final Path twoPlayers;
  private final Path moreThanTwoPlayers;
  private final Map<String, NetworkBot> bots = new HashMap<>();

  /** @param twoPlayers a file with the network for two players, or null for the one in the application
   * @param moreThanTwoPlayers a file with the network for three or four players, or null for the one in the application */
  public AiNetworks(Path twoPlayers, Path moreThanTwoPlayers) {
    this.twoPlayers = twoPlayers;
    this.moreThanTwoPlayers = moreThanTwoPlayers;
  }

  /** The networks in the application. */
  public AiNetworks() {
    this(null, null);
  }

  private Path fileFor(int playerCount) {
    return playerCount == 2 ? twoPlayers : moreThanTwoPlayers;
  }

  private String resourceFor(int playerCount) {
    return playerCount == 2 ? TWO_PLAYERS_RESOURCE : MORE_PLAYERS_RESOURCE;
  }

  /** Where the network for this number of players is read from, for messages. */
  public String describe(int playerCount) {
    var file = fileFor(playerCount);
    return file != null ? "the file " + file : "the network in the application, " + resourceFor(playerCount);
  }

  private InputStream open(int playerCount) throws IOException {
    var file = fileFor(playerCount);
    if (file != null) {
      return Files.newInputStream(file);
    }
    var stream = AiNetworks.class.getResourceAsStream(resourceFor(playerCount));
    if (stream == null) {
      throw new IOException("There is no " + resourceFor(playerCount) + " in the application");
    }
    return stream;
  }

  /** A bot playing with the network for this number of players, on the classic map. */
  public Bot botFor(int playerCount) throws IOException {
    return botFor(playerCount, GameMap.CLASSIC);
  }

  /** A bot playing with the network for this number of players on the map. The weights are the same for all maps. */
  public Bot botFor(int playerCount, GameMap map) throws IOException {
    var key = describe(playerCount) + " on " + map.name();
    var bot = bots.get(key);
    if (bot == null) {
      // the network runs on the borders of the map, and doesn't depend on the number of players or the rules
      var borders = new ObservationEncoder(new RiskEnv(new RiskConfig(2).withMap(map).withFortifyMode(FortifyMode.ADJACENT), 10)).adjacency();
      try (var in = open(playerCount)) {
        bot = new NetworkBot(JavaPolicyNetwork.load(in, borders), "AI", false, 1);
      }
      bots.put(key, bot);
    }
    return bot;
  }
}
