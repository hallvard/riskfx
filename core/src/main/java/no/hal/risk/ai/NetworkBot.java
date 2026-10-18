package no.hal.risk.ai;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import no.hal.risk.env.Action;
import no.hal.risk.env.RiskEnv;

/**
 * Plays by what the network prefers among the legal actions: either the most probable one,
 * or one sampled by probability, which makes it vary from game to game.
 */
public class NetworkBot implements Bot {

  private final PolicyNetwork net;
  private final ActionSpace actionSpace;
  private final boolean sample;
  private final Random random;
  private final String name;
  private final Map<RiskEnv, ObservationEncoder> encoders = new IdentityHashMap<>();

  public NetworkBot(PolicyNetwork net, String name, boolean sample, long seed) {
    this.net = net;
    this.name = name;
    this.sample = sample;
    this.random = new Random(seed);
    this.actionSpace = new ActionSpace(net.numTerritories());
  }

  public NetworkBot(PolicyNetwork net) {
    this(net, "AI", false, 1);
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Action chooseAction(RiskEnv env, List<Action> legalActions) {
    var encoder = encoders.computeIfAbsent(env, ObservationEncoder::new);
    var evaluation = net.evaluate(List.of(encoder.encode(env.currentPlayer())));
    var legal = new int[legalActions.size()];
    for (int i = 0; i < legal.length; i++) {
      legal[i] = actionSpace.index(legalActions.get(i));
    }
    var probabilities = Policies.probabilities(evaluation.logits()[0], legal);
    int chosen = 0;
    if (sample) {
      double target = random.nextDouble(), cumulative = 0;
      chosen = probabilities.length - 1;
      for (int i = 0; i < probabilities.length; i++) {
        cumulative += probabilities[i];
        if (target < cumulative) {
          chosen = i;
          break;
        }
      }
    } else {
      for (int i = 1; i < probabilities.length; i++) {
        if (probabilities[i] > probabilities[chosen]) {
          chosen = i;
        }
      }
    }
    return legalActions.get(chosen);
  }
}
