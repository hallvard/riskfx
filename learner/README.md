# The learner

Trains the network of the AI, in Python with PyTorch. The games are played by the Java **actor**
(`Actor` in the `training` module), which plays with the network in Java only, the way the app does. So the rules, the
observations and the rewards are only in Java, and nothing of them is written twice. Python has the network (`model.py`), and the
updates that improve it (`ppo.py`, `imitation.py`).

    weights file ──▶ Java actor: plays games, writes the decisions (.npz) ──▶ Python: PPO update ──▶ weights file ──▶ ...

The actor is a process that Python starts, and gives commands on its standard input, one on each line (`INFO`, `ROLLOUT`, `EVAL`,
`IMITATION`, `QUIT`, see the comment of `Actor`). The network is in a file in the format of `NetworkWeights` (`*.weights`), which
Python writes, and Java reads, so the file that is trained is the file the app uses.

## Setting up

The environment is in `learner/.venv`, which is not in git. Python 3.10 or later is needed, since current PyTorch needs it
(3.12 from Homebrew was used):

    python3.12 -m venv learner/.venv
    learner/.venv/bin/pip install -r learner/requirements.txt
    learner/prepare.sh        # builds the Java side, and writes the class path of the actor to learner/work/classpath.txt

## Training

Run from the directory `learner`. On a Mac, the GPU (`--device mps`) is used by default, if there is one; `--device cpu` otherwise.
An imitation start, and then improving it:

    cd learner
    .venv/bin/python -m risk_learner.train imitation --players 2 --out ../models/start.weights
    .venv/bin/python -m risk_learner.train ppo --players 2 --iterations 400 --load ../models/start.weights --out ../models/new.weights
    .venv/bin/python -m risk_learner.train eval --players 2 --weights ../models/new.weights

The opponents are greedy bots by default. With `--opponents greedy,cautious` the seats that are not the network's get one of them at random
in each game (and when measuring, they are taken in turn). The cautious bot (`CautiousBot`) advances slowly, with strong borders,
and holds out much longer than greedy, so it is a harder opponent in games of three or four players.

`ppo` saves the best network during training in the file of `--out`, and the last in the same name with `.last`, and measures both
against greedy bots on boards that were not used for choosing the best. `--help` lists the options. The rewards are named, with
`--reward default`, `advantage` or `elimination`.

To use a network in the app, copy its weights to `ui/src/main/resources/ai/` (`ai-2p.weights` for 2 players, `ai-4p.weights`
for 3 and 4), and build the app again.

## Tests

    cd learner && .venv/bin/python -m pytest

Some need the Java side, and are skipped if `prepare.sh` hasn't been run. The file `core/src/test/resources/golden/golden.json`
and `golden.weights` have weights, states and the scores PyTorch gives them; the Java tests check that the network in Java
computes the same. If the network is changed (in `model.py` and in `JavaPolicyNetwork`), write them again with
`python -m risk_learner.make_golden`.
