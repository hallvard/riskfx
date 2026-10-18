# Does a reward for eliminating opponents help? (2026-10-16)

**Short answer: no measurable gain.** The networks already eliminate nearly every opponent they can, and a control with the
old reward and the same extra training was as good or better.

## What was tried

The potential of the reward got a term for the share of the opponents that are eliminated (`Reward.named("elimination")`,
weight 0.5, scaled by 0.3), which is potential-based shaping, so it can't change what the best play is.
Both runs continued from the previous best network (`models/overnight/ppo4-resumed2-imitation.bin.last`), for 250 iterations,
with the same settings (learning rate 1e-4, entropy 0.003, 8 games per iteration, a quarter of the seats greedy bots):

* `ppo-4p`, `ppo-3p`: the elimination reward, in games of 4 and of 3 players
* `control-4p`, `control-3p`: the old reward, as a control

## Results (300 games, one network against greedy bots; wins / turns per game / opponents eliminated per game)

| | 4 players | 3 players |
|---|---|---|
| starting network | 96% / 69 / 2.70 | 94% / 46 / 1.81 |
| elimination reward, end of training | 99% / 89 / 2.80 | 98% / 43 / 1.89 |
| control, end of training | 98% / 71 / 2.78 | 98% / 36 / 1.85 |

More training helped, the elimination reward added nothing, and in one case made the games longer.
The maximum of the eliminations is 3 (4 players) and 2 (3 players), and the networks are already close to it.

## The network now used by the AI for 3 and 4 players

`control-4p.bin` (the best checkpoint of the control run in 4-player games) is now `models/ai-4p.bin`.
It was chosen among five candidates, and confirmed on other boards, with a separate evaluation (`Arena`, 300 games each):

| | 3 players | 4 players |
|---|---|---|
| previous network (`models/ai-4p-previous.bin`) | 93.0% wins, 44 turns | 98.0%, 67 turns |
| new network | 98.0%, 39 turns | 98.7%, 57 turns |

Against greedy bots only: these numbers say nothing about how strong the AI is against a human.

## Files

`*.bin` best network during training, `*.bin.last` the end of it, `*.log` training logs, `run.sh` and `run2.sh` what was run
(`classes/` is the compiled code it used).
