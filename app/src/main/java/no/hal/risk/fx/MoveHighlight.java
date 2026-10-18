package no.hal.risk.fx;

import java.util.HashMap;
import java.util.Map;
import javafx.scene.paint.Color;
import no.hal.risk.Territory;
import no.hal.risk.ai.BotMatch;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * The outlines that show what a move did: after an attack by how many armies each territory lost, see {@link BattleColors},
 * and for other moves the territories involved.
 */
public class MoveHighlight {

  public static Map<Territory, Color> of(BotMatch.Move move, RiskEnv env) {
    var marked = new HashMap<Territory, Color>();
    var territories = env.territories();
    switch (move.action()) {
      case Action.Reinforce(var territory) -> marked.put(territories.get(territory), BattleColors.OTHER_MOVE);
      case Action.Attack(var from, var to) -> {
        marked.put(territories.get(from), BattleColors.forLosses(move.attackerLoss(), move.defenderLoss()));
        marked.put(territories.get(to), BattleColors.forLosses(move.defenderLoss(), move.attackerLoss()));
      }
      case Action.Fortify(var from, var to, var _) -> {
        marked.put(territories.get(from), BattleColors.OTHER_MOVE);
        marked.put(territories.get(to), BattleColors.OTHER_MOVE);
      }
      case Action.EndAttack _, Action.SkipFortify _ -> {
      }
    }
    return marked;
  }
}
