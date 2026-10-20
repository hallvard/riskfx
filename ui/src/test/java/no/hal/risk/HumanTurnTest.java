package no.hal.risk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import no.hal.risk.env.Action;
import no.hal.risk.env.Phase;
import no.hal.risk.env.RiskEnv;
import no.hal.risk.fx.HumanTurn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** In this package, to be able to set up a board. */
public class HumanTurnTest {

  private RiskEnv env;
  private HumanTurn turn;
  private Territory alaska, alberta, northwest, kamchatka, brazil;

  /** The first player occupies everything with 2 armies, except Alberta (2 armies) and Brazil (5), which the second occupies. */
  @BeforeEach
  public void setUp() {
    env = new RiskEnv(new RiskConfig(2).withFortifyMode(FortifyMode.CONNECTED), 100);
    env.reset(1);
    var risk = env.risk();
    for (var territory : risk.allTerritories()) {
      territory.setOccupant(risk.players().get(0), 2);
    }
    alaska = risk.getTerritory("Alaska");
    alberta = risk.getTerritory("Alberta");
    northwest = risk.getTerritory("Northwest Territory");
    kamchatka = risk.getTerritory("Kamchatka");
    brazil = risk.getTerritory("Brazil");
    alberta.setOccupant(risk.players().get(1), 2);
    brazil.setOccupant(risk.players().get(1), 5);
    turn = new HumanTurn(env);
  }

  private void perform(HumanTurn.Click click) {
    assertNotNull(click.action(), click.message());
    env.step(click.action());
    turn.afterAction(click.action());
  }

  private void toAttacking() {
    while (env.phase() == Phase.REINFORCING) {
      env.step(turn.click(alaska).action());
    }
    assertEquals(Phase.ATTACKING, env.phase());
  }

  // reinforcing

  @Test
  public void testClickingYourOwnTerritoryPlacesAnArmy() {
    var click = turn.click(alaska);
    assertEquals(new Action.Reinforce(env.territoryIndex(alaska)), click.action());
    assertNull(click.message());
    var before = alaska.numArmies();
    perform(click);
    assertEquals(before + 1, alaska.numArmies());
  }

  @Test
  public void testYouCanNotReinforceAnEnemyTerritory() {
    var click = turn.click(alberta);
    assertNull(click.action());
    assertTrue(click.message().contains("own territories"));
    assertTrue(turn.instructions().startsWith("Reinforce"));
    assertTrue(turn.instructions().contains("armies left"));
  }

  // attacking

  @Test
  public void testAnAttackNeedsAnAttackerAndAnAdjacentEnemy() {
    toAttacking();
    assertTrue(turn.instructions().startsWith("Attack"));
    // an enemy before an attacker
    var early = turn.click(alberta);
    assertNull(early.action());
    assertTrue(early.message().contains("first"));
    // select the attacker
    var select = turn.click(alaska);
    assertNull(select.action());
    assertNull(select.message());
    assertEquals(alaska, turn.selectedFrom());
    assertEquals(HumanTurn.Selection.SOURCE, turn.selection().get(alaska));
    assertTrue(turn.instructions().contains("Alaska"));
    // not adjacent
    var far = turn.click(brazil);
    assertNull(far.action());
    assertTrue(far.message().contains("does not border"));
    // adjacent
    var attack = turn.click(alberta);
    assertEquals(new Action.Attack(env.territoryIndex(alaska), env.territoryIndex(alberta)), attack.action());
  }

  @Test
  public void testATerritoryWithOneArmyCanNotAttack() {
    toAttacking();
    alaska.setOccupant(env.risk().players().get(0), 1);
    var click = turn.click(alaska);
    assertNull(click.action());
    assertTrue(click.message().contains("only one army"));
    assertNull(turn.selectedFrom());
  }

  @Test
  public void testClickingTheAttackerAgainDeselectsIt() {
    toAttacking();
    turn.click(alaska);
    assertEquals(alaska, turn.selectedFrom());
    turn.click(alaska);
    assertNull(turn.selectedFrom());
    assertTrue(turn.selection().isEmpty());
  }

  @Test
  public void testAnotherOwnTerritoryChangesTheAttacker() {
    toAttacking();
    turn.click(alaska);
    turn.click(northwest);
    assertEquals(northwest, turn.selectedFrom());
  }

  @Test
  public void testTheSelectionFollowsTheArmiesAfterAnAttack() {
    toAttacking();
    // a big army against a small one: either the defender is conquered, and the armies move in, or the attack goes on
    alaska.setOccupant(env.risk().players().get(0), 30);
    var attacker = alaska;
    turn.click(alaska);
    for (int i = 0; i < 20 && alberta.occupant() != env.currentPlayer(); i++) {
      var click = turn.click(alberta);
      assertNotNull(click.action(), click.message());
      env.step(click.action());
      turn.afterAction(click.action());
      if (alberta.occupant() != env.currentPlayer()) {
        assertEquals(attacker, turn.selectedFrom(), "still the attacker while the defender holds");
      }
    }
    assertEquals(env.currentPlayer(), alberta.occupant(), "it should have been conquered with 30 armies against 2");
    // the armies moved in, so Alberta is the attacker now
    assertTrue(alberta.numArmies() >= 2);
    assertEquals(alberta, turn.selectedFrom());
    assertEquals(1, alaska.numArmies());
  }

  @Test
  public void testASelectionThatCanNoLongerAttackIsDropped() {
    toAttacking();
    turn.click(northwest);
    assertEquals(northwest, turn.selectedFrom());
    northwest.setOccupant(env.risk().players().get(0), 1);
    turn.afterAction(new Action.EndAttack());
    assertNull(turn.selectedFrom());
    // or it was lost to somebody else
    turn.click(alaska);
    alaska.setOccupant(env.risk().players().get(1), 5);
    turn.afterAction(new Action.EndAttack());
    assertNull(turn.selectedFrom());
  }

  // fortifying

  private void toFortifying() {
    toAttacking();
    env.step(new Action.EndAttack());
    turn.afterAction(new Action.EndAttack());
    assertEquals(Phase.FORTIFYING, env.phase());
    // the reinforcements were all placed on Alaska: back to 2 armies, as the others
    alaska.setOccupant(env.risk().players().get(0), 2);
  }

  @Test
  public void testFortifyingNeedsASourceAndATargetAndANumber() {
    toFortifying();
    assertTrue(turn.instructions().startsWith("Fortify"));
    assertNull(turn.fortify(1));
    assertNull(turn.click(alaska).action());
    assertEquals(alaska, turn.selectedFrom());
    assertEquals(1, turn.maxFortifyArmies());
    assertNull(turn.fortify(1), "no target yet");
    // in the connected mode, any territory connected through your own can be the target
    assertNull(turn.click(kamchatka).action());
    assertEquals(kamchatka, turn.selectedTo());
    assertEquals(HumanTurn.Selection.TARGET, turn.selection().get(kamchatka));
    assertNull(turn.fortify(0));
    assertNull(turn.fortify(2), "only one army can be moved");
    var fortify = turn.fortify(1);
    assertEquals(new Action.Fortify(env.territoryIndex(alaska), env.territoryIndex(kamchatka), 1), fortify);
    var before = kamchatka.numArmies();
    env.step(fortify);
    turn.afterAction(fortify);
    assertEquals(before + 1, kamchatka.numArmies());
    assertNull(turn.selectedFrom());
  }

  @Test
  public void testYouCanNotFortifyFromOrToEnemyTerritoriesOrWithASingleArmy() {
    toFortifying();
    assertTrue(turn.click(alberta).message().contains("own territories"));
    alaska.setOccupant(env.risk().players().get(0), 1);
    assertTrue(turn.click(alaska).message().contains("only one army"));
    // an enemy territory is no target either
    turn.click(northwest);
    assertEquals(northwest, turn.selectedFrom());
    assertTrue(turn.click(brazil).message().contains("own territories"));
    assertNull(turn.selectedTo());
  }

  @Test
  public void testTheSelectionIsClearedWhenThePhaseChanges() {
    toAttacking();
    turn.click(alaska);
    assertEquals(alaska, turn.selectedFrom());
    env.step(new Action.EndAttack());
    assertNull(turn.selectedFrom());
    assertTrue(turn.selection().isEmpty());
  }

  @Test
  public void testNothingHappensWhenTheGameIsOver() {
    // the second player has nothing left
    for (var territory : env.risk().allTerritories()) {
      territory.setOccupant(env.risk().players().get(0), 2);
    }
    assertTrue(env.isDone());
    var click = turn.click(alaska);
    assertNull(click.action());
    assertEquals("The game is over", click.message());
    assertFalse(turn.instructions().isBlank());
  }

  @Test
  public void testTheLastReinforcedTerritoryIsSelectedToAttackFrom() {
    // reinforce two territories, with the last on northwest, until the reinforcements are used up
    perform(turn.click(alaska));
    while (env.phase() == Phase.REINFORCING) {
      perform(turn.click(northwest));
    }
    assertEquals(Phase.ATTACKING, env.phase());
    assertEquals(northwest, turn.selectedFrom());
    assertEquals(HumanTurn.Selection.SOURCE, turn.selection().get(northwest));
    assertTrue(turn.instructions().contains("Attack from Northwest Territory"));
    // an adjacent enemy can be attacked at once: Northwest Territory borders Alberta
    var attack = turn.click(alberta);
    assertEquals(new Action.Attack(env.territoryIndex(northwest), env.territoryIndex(alberta)), attack.action());
  }

  @Test
  public void testTheSelectionToAttackFromIsOnlyMadeRightAfterReinforcing() {
    while (env.phase() == Phase.REINFORCING) {
      perform(turn.click(alaska));
    }
    assertEquals(alaska, turn.selectedFrom());
    // a deselection stays, and it is not selected again when nothing changed
    turn.click(alaska);
    assertNull(turn.selectedFrom());
    assertNull(turn.selectedFrom());
    // in the next phase, the selection is cleared as always
    env.step(new Action.EndAttack());
    assertNull(turn.selectedFrom());
  }

  // double click

  @Test
  public void testDoubleClickWhenReinforcingGivesAllTheRemainingArmies() {
    // the click placed one army
    perform(turn.click(alaska));
    int left = ((TurnState.Reinforcing) env.risk().currentTurnState()).totalArmies();
    var actions = turn.reinforceAll(alaska);
    assertEquals(left, actions.size());
    assertTrue(actions.stream().allMatch(action -> action.equals(new Action.Reinforce(env.territoryIndex(alaska)))));
    var before = alaska.numArmies();
    for (var action : actions) {
      env.step(action);
      turn.afterAction(action);
    }
    assertEquals(before + left, alaska.numArmies());
    assertEquals(Phase.ATTACKING, env.phase());
    // and then the territory is the one to attack from
    assertEquals(alaska, turn.selectedFrom());
  }

  @Test
  public void testDoubleClickWhenReinforcingDoesNothingOnAnEnemyTerritoryOrInAnotherPhase() {
    assertTrue(turn.reinforceAll(alberta).isEmpty());
    toAttacking();
    assertTrue(turn.reinforceAll(alaska).isEmpty());
  }

  @Test
  public void testDoubleClickOnTheTargetWhenFortifyingMovesAsManyArmiesAsPossible() {
    toFortifying();
    alaska.setOccupant(env.risk().players().get(0), 9);
    turn.click(alaska);
    // the click selected the target
    turn.click(kamchatka);
    assertEquals(kamchatka, turn.selectedTo());
    var action = turn.fortifyMax(kamchatka);
    assertEquals(new Action.Fortify(env.territoryIndex(alaska), env.territoryIndex(kamchatka), 8), action);
    var before = kamchatka.numArmies();
    env.step(action);
    assertEquals(before + 8, kamchatka.numArmies());
    assertEquals(1, alaska.numArmies());
  }

  @Test
  public void testDoubleClickWhenFortifyingNeedsTheTarget() {
    toFortifying();
    alaska.setOccupant(env.risk().players().get(0), 9);
    // nothing selected
    assertNull(turn.fortifyMax(kamchatka));
    turn.click(alaska);
    // only a source: a double click on it is not moving armies to it
    assertNull(turn.fortifyMax(alaska));
    turn.click(kamchatka);
    // another territory than the target
    assertNull(turn.fortifyMax(northwest));
  }
}
