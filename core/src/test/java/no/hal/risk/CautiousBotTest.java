package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.hal.risk.ai.Bots;
import no.hal.risk.ai.CautiousBot;
import no.hal.risk.ai.GreedyBot;
import no.hal.risk.env.Action;
import no.hal.risk.env.Phase;
import no.hal.risk.env.RiskEnv;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** In this package, to be able to set up a board. */
public class CautiousBotTest {

  private RiskEnv env;
  private Player me, enemy;
  private final CautiousBot bot = new CautiousBot();

  /**
   * The first player, who is to move, occupies everything with 2 armies, except Madagascar, where the other has one army, which can't attack,
   * and is far from the territories of the tests, but keeps the game from being over.
   */
  @BeforeEach
  public void setUp() {
    env = new RiskEnv(new RiskConfig(2).withFortifyMode(FortifyMode.ADJACENT), 100);
    env.reset(1);
    me = env.players().get(0);
    enemy = env.players().get(1);
    for (var territory : env.territories()) {
      territory.setOccupant(me, 2);
    }
    t("Madagascar").setOccupant(enemy, 1);
  }

  private Territory t(String name) {
    return env.risk().getTerritory(name);
  }

  private void own(String name, int armies) {
    t(name).setOccupant(me, armies);
  }

  private void enemy(String name, int armies) {
    t(name).setOccupant(enemy, armies);
  }

  private Action choose() {
    var legal = env.legalActions();
    var action = bot.chooseAction(env, legal);
    assertTrue(legal.contains(action), action + " is not legal");
    return action;
  }

  private int index(String name) {
    return env.territoryIndex(t(name));
  }

  private void toPhase(Phase phase) {
    while (env.phase() != phase) {
      env.step(env.phase() == Phase.REINFORCING ? env.legalActions().getFirst() : env.phase() == Phase.ATTACKING ? new Action.EndAttack() : new Action.SkipFortify());
    }
  }

  // reinforcing

  @Test
  public void testTheWeakestBorderIsReinforcedFirst() {
    // Alberta has 10 armies, which can attack with 9: Alaska, Northwest Territory, Ontario and Western United States need 7
    enemy("Alberta", 10);
    own("Northwest Territory", 1);
    own("Ontario", 3);
    assertEquals(new Action.Reinforce(index("Northwest Territory")), choose());
  }

  @Test
  public void testTheStrongerEnemyDecidesBetweenBordersThatAreEquallyWeak() {
    // both have a deficit of 2 armies... Alaska next to 5 armies (4 can attack, needs 3), Ontario next to 10 (9 can attack, needs 7)
    enemy("Kamchatka", 5);
    enemy("Alberta", 10);
    own("Alaska", 1);
    own("Ontario", 5);
    own("Northwest Territory", 5);
    own("Western United States", 5);
    // deficits: Alaska needs 7 (Alberta and Kamchatka, 9 can attack) less 1, Ontario needs 7 less 5
    assertEquals(new Action.Reinforce(index("Alaska")), choose());
  }

  @Test
  public void testInteriorTerritoriesAreNotReinforcedWhileABorderIsWeak() {
    enemy("Alberta", 10);
    // the choice is one of the four borders of Alberta
    var chosen = (Action.Reinforce) choose();
    assertTrue(List.of("Alaska", "Northwest Territory", "Ontario", "Western United States").contains(env.territories().get(chosen.territory()).name()));
  }

  @Test
  public void testWhenAllBordersAreSafeTheArmiesGoNextToTheWeakestEnemyWhereTheyAreMost() {
    // Madagascar has one army, so it can't attack, and nothing is threatened
    own("South Africa", 4);
    assertEquals(new Action.Reinforce(index("South Africa")), choose());
  }

  // attacking

  @Test
  public void testItAttacksWithALeadWhenWhatIsLeftIsSafe() {
    toPhase(Phase.ATTACKING);
    enemy("Alberta", 2);
    own("Alaska", 8);
    // Alberta's other neighbours are its own, and Alaska has no other enemy next to it
    assertEquals(new Action.Attack(index("Alaska"), index("Alberta")), choose());
  }

  @Test
  public void testItDoesNotAttackWithoutALead() {
    toPhase(Phase.ATTACKING);
    enemy("Alberta", 4);
    own("Alaska", 5);
    assertEquals(new Action.EndAttack(), choose());
  }

  @Test
  public void testItDoesNotAttackFromATerritoryThatWouldBeLeftToAStrongEnemy() {
    toPhase(Phase.ATTACKING);
    enemy("Alberta", 2);
    enemy("Kamchatka", 30);
    own("Alaska", 8);
    // Alaska would be left with one army next to 29 that can attack
    assertEquals(new Action.EndAttack(), choose());
  }

  @Test
  public void testItDoesNotAttackWhatItCouldNotHold() {
    toPhase(Phase.ATTACKING);
    enemy("Alberta", 2);
    enemy("Ontario", 30);
    own("Alaska", 8);
    // Alberta would get at most 5 armies, next to 29 that can attack
    assertEquals(new Action.EndAttack(), choose());
  }

  @Test
  public void testItAttacksWhereTheLeadIsBiggest() {
    toPhase(Phase.ATTACKING);
    enemy("Alberta", 4);
    enemy("Kamchatka", 1);
    own("Alaska", 9);
    assertEquals(new Action.Attack(index("Alaska"), index("Kamchatka")), choose());
  }

  // fortifying

  @Test
  public void testItMovesArmiesFromTheInteriorToTheWeakestBorder() {
    toPhase(Phase.FORTIFYING);
    enemy("Alberta", 10);
    // Quebec is not next to Alberta, and Ontario is
    own("Quebec", 5);
    assertEquals(new Action.Fortify(index("Quebec"), index("Ontario"), 4), choose());
  }

  @Test
  public void testItDoesNotMoveArmiesFromABorder() {
    toPhase(Phase.FORTIFYING);
    enemy("Alberta", 10);
    // no army can be moved from the interior, which has one army everywhere, and the only strong territory is next to Alberta itself
    for (var territory : env.territories()) {
      if (territory.occupant() == me) {
        territory.setOccupant(me, 1);
      }
    }
    own("Ontario", 8);
    assertEquals(new Action.SkipFortify(), choose());
  }

  @Test
  public void testItSkipsFortifyingWhenNoBorderIsWeak() {
    toPhase(Phase.FORTIFYING);
    own("Quebec", 6);
    assertEquals(new Action.SkipFortify(), choose());
  }

  // whole games

  @Test
  public void testItOnlyMakesLegalMovesAndGamesAreDecided() {
    int decided = 0;
    for (long seed = 1; seed <= 12; seed++) {
      var game = new RiskEnv(new RiskConfig(2).withFortifyMode(FortifyMode.ADJACENT), 200);
      game.reset(seed);
      var cautious = new CautiousBot();
      var greedy = new GreedyBot();
      while (! game.isDone()) {
        var legal = game.legalActions();
        var action = (game.playerIndex(game.currentPlayer()) == 0 ? cautious : greedy).chooseAction(game, legal);
        assertTrue(legal.contains(action), "in phase " + game.phase());
        game.step(action);
      }
      decided += game.isWon() ? 1 : 0;
    }
    assertTrue(decided >= 10, "decided games: " + decided);
  }

  @Test
  public void testTheSameStateGivesTheSameMove() {
    toPhase(Phase.ATTACKING);
    enemy("Alberta", 2);
    own("Alaska", 8);
    assertEquals(choose(), choose());
    assertEquals("cautious", bot.name());
  }

  // the bots by name

  @Test
  public void testBotsByName() {
    assertInstanceOf(CautiousBot.class, Bots.byName("cautious"));
    assertInstanceOf(GreedyBot.class, Bots.byName(" Greedy "));
    assertEquals(2, Bots.byNames("greedy,cautious").size());
    assertThrows(IllegalArgumentException.class, () -> Bots.byName("reckless"));
  }
}
