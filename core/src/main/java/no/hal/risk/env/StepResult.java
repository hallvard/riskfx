package no.hal.risk.env;

import no.hal.risk.Player;

/**
 * The outcome of an action.
 *
 * @param actor the player that performed the action
 * @param done whether the game is over, either won or truncated
 * @param winner the winner, or null if the game isn't won (yet)
 * @param truncated whether the game ended since the turn limit was reached
 */
public record StepResult(Player actor, boolean done, Player winner, boolean truncated) {
}
