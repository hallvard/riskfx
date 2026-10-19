# RiskFx

Risk for humans and bots, with an AI that was trained by reinforcement learning (self-play, PPO).

## Modules

| Module | What it is | Depends on |
|---|---|---|
| `core` | The rules, the environment (`RiskEnv`), the bots (greedy, cautious, human, network), and what it takes to **run** a trained network: the observation encoder, the action space, and a forward pass in plain Java (`JavaPolicyNetwork`). No JavaFX, no PyTorch. | Jackson |
| `training` | The Java side of training: plays the games for the learner (`Actor`), with rewards and advantages, and measures the network (arena, evaluation). No PyTorch. | `core` |
| `app` | The window for playing and watching (Quarkus and JavaFX). It only runs the network, so it needs no PyTorch. | `core` |
| `learner` | The learner, in Python with PyTorch: the network, PPO and imitation. See `learner/README.md`. | the Java actor |

A trained network is a file of weights in a plain format (`*.weights`, see `NetworkWeights`). The learner writes it, and
`JavaPolicyNetwork` in the core reads it, in the app and in the actor that plays the games for training.

## Building and testing

    mvn test                 # all modules, a minute or two (training is the slowest)
    mvn compile              # needed once, and after mvn clean, before running the app with Quarkus (see below)

## Running the app

With Quarkus in dev mode (press `s` to restart with changed code):

    mvn compile
    mvn -pl app quarkus:dev          # or: cd app && mvn quarkus:dev

As a jar with its dependencies in `app/target/libs`, which can be started from any directory:

    mvn -Pplay -DskipTests package
    java -jar app/target/riskfx-play.jar

The networks of the AI are in the application (`app/src/main/resources/ai/`), one for games of 2 players and one for 3 or 4.
Other ones can be used with the options `--ai2=file` and `--ai4=file` for the jar, or the properties `riskfx.ai2` and `riskfx.ai4`
for Quarkus.

## Training

The network is trained by the learner in Python, which has the Java actor play the games. See `learner/README.md`.

To use a new network in the app, copy its weights to `app/src/main/resources/ai/ai-2p.weights` (2 players)
or `ai-4p.weights` (3 and 4 players) and build again.

## Files

* `models/`: trained networks (`ai-2p.weights` and `ai-4p.weights` are those of the AI), and the logs and scripts of the training runs.
  The scripts in `models/overnight` and `models/elimination` were run before the code was split into modules, and before training
  moved to Python, and use the old names of the classes. The networks of those runs were converted to `.weights` files next to
  the originals (the files of the older format, `.bin`, can no longer be read), and are not in git.

## In a browser (JPro)

The user interface can be run on a server by [JPro](https://www.jpro.one) and used in a browser, for instance on a phone.
It is the module `web`, in a profile, since JPro comes from a repository of its own:

    mvn -Pweb -pl web -am install
    mvn -Pweb -pl web jpro:run

This starts a server on http://localhost:8080 and opens it in the browser. Each browser tab is a game of its own.
The module only needs the app (without Quarkus), and JPro's own version of JavaFX is used (25.0.2 and a version of JPro that adds to it).
