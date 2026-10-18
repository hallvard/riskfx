# Overnight training, 2026-10-16

What was run, what came out, and what went wrong on the way. All win rates are for **one network seat against greedy bots**, on
boards the network was not trained on, with the network's seat rotated. Parity is 50% with 2 players and 25% with 4.

## Method

1. **Imitation:** the network copies the greedy bot's moves (policy) and the discounted rewards greedy's games earn (value head).
   It then plays greedy at parity (2 players: 47% and 52% in 400 games; 4 players: 23% in 200 games).
2. **PPO** from that start, with self-play where 25% of the seats are greedy bots.
   Learning rate 1e-4, entropy bonus 0.003, 8 games per iteration.
3. **Two rewards:** `default` (territories, armies, continent bonus) and `advantage` (also how many armies each territory can send
   in beyond its weakest enemy neighbour). Potential-based shaping, so neither changes what play is best.

## Results (fresh boards)

### 2 players (400 games, margin about 2.3 points)

| Run | Last | Best |
|---|---|---|
| imitation only (default reward) | 47% | |
| imitation only (advantage reward) | 52% | |
| PPO from scratch, 100 iterations (earlier) | 46% | |
| default, seed 1, 400 iterations | 61% | 60% |
| default, seed 2, 400 iterations | 74% | 75% |
| advantage, seed 1, 400 iterations | 71% | 70% |
| advantage, seed 2, 400 iterations | 67% | 70% |
| default, seed 2 continued to 1000 iterations | **81%** | 82% |

### 4 players (300 games, margin about 1.5 points)

| Run | Last | Best |
|---|---|---|
| 2-player networks, no 4-player training | 59% to 83% | |
| imitation start, 4 players (greedy copy) | 23% | |
| from the 2-player default network, 300 iterations | **96%** | 96% |
| from the 2-player advantage network, 300 iterations (froze, see below) | 93% | 91% |
| from the 4-player imitation start, 300 iterations (froze, see below) | 92% | 92% |
| ... and 300 more iterations after the fix (default reward) | **98%** | 98% |
| ... and 300 more iterations after the fix (advantage reward) | 94% | 94% |

Cross-checks through a separate code path (`Arena` with `NetworkBot`, other boards) agree: 2-player 78.5% (200 games),
4-player 97% (200 games).

### Against each other (4-player games, 200 games)

* Three 4-player networks and one greedy bot: 31%, 27.5%, 25.5% and greedy 5.5% (11% of the games hit the turn limit).
* Best 4-player network, the long 2-player network, the greedy copy and greedy: 73%, 22.5%, 1.5%, 1.5%.

## What this does and does not show

* Starting from imitation and continuing with PPO works: from parity with greedy to 60-80% (2 players) and 92-98% (4 players).
* **Does the advantage term help? Not shown.** Seed-to-seed spread within one reward (61-74%) is larger than the gap between
  rewards (69% against 67.5% on average). The diagnostic (`PotentialDiagnostics`) also found it adds almost nothing to the
  winner prediction of the plain potential.
* **Greedy is a weak opponent.** All numbers are against it, and the networks partly trained against it. The results against
  each other above are a better hint of relative strength, but nothing here says how strong the networks are against a human.
* 4-player games between strong networks often stall (11% reached the turn limit).

## Problems found and fixed

1. **Overflow of the probability ratio** (a run crashed at iteration 11): the log ratio is now clamped to +/-20.
2. **Frozen training** (two 4-player runs): one non-finite gradient had poisoned the gradient buffers, because DJL's
   `zeroGradients()` multiplies by zero, which leaves NaN as NaN. With my "skip bad steps" safeguard every later step was
   skipped. The weights themselves were fine. Gradients are now overwritten with real zeros before each backward pass
   (`PolicyValueNet.clearGradients`), with regression tests that fail without the fix.
3. **Memory leak in the first version of that fix** (two resumed runs died at iteration 165 with an out-of-memory error in
   native memory): `getGradient()` returns a new array each call, which must be closed, and a gradient set from a buffer
   shares that buffer's memory. Fixed, with a test that checks native memory stays flat over 3000 calls.
   The final 4-player runs show 0-5 skipped steps in 300 iterations, each recovered from.

## Files

* `imitation-*.bin`, `ppo-*.bin` (best during training) and `ppo-*.bin.last` (end of training): the networks.
  The ones to use: `ppo-default-2-long.bin.last` (2 players), `ppo4-resumed2-imitation.bin.last` (4 players, 98%),
  `ppo4-from-2p-default.bin.last` (4 players, 96%).
* `*.log`: the training logs. `crashed/` has the logs of the runs that died, for the record. `summary.txt` has the final numbers.
* `run.sh`, `run2.sh`, `run3.sh`, `run4.sh`: what was run, in order. `classes*/` are snapshots of the compiled classes they used,
  and `cp.txt` the class path.
* Nothing here is committed to git.

## Watching a network play

The watch window supports 2 players:

    mvn -Pwatch package
    java -jar target/riskfx-watch.jar models/overnight/ppo-default-2-long.bin.last

A 4-player watch mode (the network against three greedy bots) is not built yet.
