package no.hal.risk.fx;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import no.hal.risk.FortifyMode;
import no.hal.risk.Territory;
import no.hal.risk.env.Action;
import no.hal.risk.env.Phase;

/**
 * A development tool, not a test: renders the user interface in some states to image files,
 * since a window of another application can't be captured. Run with the class path of the project,
 * and the directory for the images as the argument.
 */
public class PlaySnapshot {

  private static PlayApp app;
  private static Scene scene;
  private static File directory;

  public static void main(String[] args) throws Exception {
    directory = new File(args[0]);
    directory.mkdirs();
    Platform.startup(() -> {});
    var done = new CompletableFuture<Void>();
    Platform.runLater(() -> {
      try {
        app = new PlayApp();
        Parent root = app.createContent();
        scene = new Scene(root, 1100, 860);
        scenario();
        done.complete(null);
      } catch (Throwable e) {
        done.completeExceptionally(e);
      }
    });
    try {
      done.get();
    } finally {
      Platform.exit();
    }
  }

  private static void snapshot(String name) throws Exception {
    scene.getRoot().applyCss();
    scene.getRoot().layout();
    WritableImage image = scene.snapshot(null);
    var buffered = new BufferedImage((int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
    var reader = image.getPixelReader();
    for (int y = 0; y < buffered.getHeight(); y++) {
      for (int x = 0; x < buffered.getWidth(); x++) {
        buffered.setRGB(x, y, reader.getArgb(x, y));
      }
    }
    javax.imageio.ImageIO.write(buffered, "png", new File(directory, name + ".png"));
    System.out.println("wrote " + name + ".png");
  }

  private static Territory ownTerritory(int minArmies) {
    var env = app.match().env();
    return env.risk().playerTerritories(env.currentPlayer()).stream().filter(t -> t.numArmies() >= minArmies).findFirst().orElseThrow();
  }

  private static void scenario() throws Exception {
    // 1. human against AI, at the start
    app.startGame(GameSetup.of(PlayerKind.HUMAN, PlayerKind.AI), FortifyMode.ADJACENT, 11);
    snapshot("1-start");
    // 2. the human places all armies, ends the attack and skips the fortification, and the AI plays its turn
    var env = app.match().env();
 // a quick press places all the armies
    var first = ownTerritory(1);
    app.territoryDefault(first);
    System.out.println("after the quick press: " + env.phase() + ", " + first.name() + " has " + first.numArmies() + " armies");
    snapshot("1b-after-quick-press-reinforce");
    app.humanAction(new Action.EndAttack());
    app.humanAction(new Action.SkipFortify());
    int moves = 0;
    while (! app.match().isHumanTurn() && moves++ < 400) {
      app.botMove();
      if (moves == 12) {
        snapshot("2-ai-moving");
      }
    }
    snapshot("3-human-turn-again");
    // 4. the human selects an attacker
    var again = ownTerritory(1);
    app.territoryDefault(again);
    var attacker = env.risk().playerTerritories(env.currentPlayer()).stream()
        .filter(t -> t.numArmies() >= 2 && ! env.risk().connectedTerritories(t, false).isEmpty()).findFirst().orElseThrow();
    // the territory that got the last army is already selected, and a click on it would deselect it
    if (app.humanTurn().selectedFrom() != attacker) {
      app.territoryDefault(attacker);
    }
    snapshot("4-attacker-selected");
    // 5. an attack, by the human: the outlines show the losses
    var defender = env.risk().connectedTerritories(attacker, false).getFirst();
    int attackerBefore = attacker.numArmies(), defenderBefore = defender.numArmies();
    app.territoryDefault(defender);
    System.out.printf("attack: attacker %d -> %d, defender %d -> %d, defender now held by %s%n",
        attackerBefore, attacker.numArmies(), defenderBefore, defender.numArmies(), defender.occupant().name());
    snapshot("5-after-human-attack");
    // 6. fortifying
    app.humanAction(new Action.EndAttack());
    var from = env.risk().playerTerritories(env.currentPlayer()).stream()
        .filter(t -> t.numArmies() >= 2 && ! env.risk().connectedTerritories(t, true).isEmpty()).findFirst().orElse(null);
    if (from != null) {
      app.territoryDefault(from);
      app.territoryDefault(env.risk().connectedTerritories(from, true).getFirst());
    }
    snapshot("6-fortify");
    // 7. watching the AI against three greedy bots, after some moves with an attack
    app.startGame(GameSetup.of(PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.GREEDY, PlayerKind.GREEDY), FortifyMode.ADJACENT, 5);
    for (int i = 0; i < 400 && ! app.match().isDone(); i++) {
      app.botMove();
      if (i > 60 && app.lastMoveWasAttack()) {
        break;
      }
    }
    snapshot("7-watch-four-players");
    // 8. the Europe map
    app.startGame(GameSetup.of(PlayerKind.AI, PlayerKind.GREEDY, PlayerKind.CAUTIOUS), FortifyMode.ADJACENT, no.hal.risk.Rules.DEFAULT, no.hal.risk.GameMap.EUROPE, 3);
    snapshot("8-europe-start");
    for (int i = 0; i < 300 && ! app.match().isDone(); i++) {
      app.botMove();
    }
    snapshot("8-europe-later");
    app.worldView().setShowRegions(true);
    snapshot("8-europe-regions");
  }
}
