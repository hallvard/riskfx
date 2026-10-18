"""Training from the command line.

    python -m risk_learner.train imitation --players 4 --out models/start.weights
    python -m risk_learner.train ppo --players 4 --load models/start.weights --out models/new.weights
    python -m risk_learner.train eval --players 4 --weights models/new.weights

The games are played by the Java actor, which needs the class path of the training module, in a file made by ``learner/prepare.sh``.
"""
from __future__ import annotations

import argparse
import tempfile
import time
from pathlib import Path

import numpy as np
import torch

from .actor import Actor
from .data import load_imitation, load_rollout
from .imitation import evaluate as evaluate_imitation
from .imitation import train_epoch
from .model import PolicyValueNet, action_space_size
from .ppo import Ppo, PpoConfig
from .weights import Config, read_weights

FINAL_EVALUATION_BOARDS = 123_456_789


def pick_device(name: str) -> torch.device:
    if name == "auto":
        return torch.device("mps" if torch.backends.mps.is_available() else "cpu")
    return torch.device(name)


def make_network(actor: Actor, work: Path, load: str | None, seed: int, device: torch.device) -> PolicyValueNet:
    """A network of the size of the game, with the weights in the file, or new ones."""
    info_file = work / "info.npz"
    actor.info(info_file)
    with np.load(info_file) as info:
        borders = info["adjacency"]
        n, features, globals_, _ = (int(value) for value in info["dims"])
    if load:
        config, tensors = read_weights(load)
        net = PolicyValueNet(config, borders, seed)
        net.load_tensors(tensors)
    else:
        net = PolicyValueNet(Config(features, globals_), borders, seed)
    return net.to(device)


def run_imitation(args, actor: Actor, work: Path, device: torch.device) -> None:
    net = make_network(actor, work, args.load, args.seed, device)
    num_actions = net.num_actions
    started = time.time()
    train_file, test_file = work / "imitation-train.npz", work / "imitation-test.npz"
    actor.imitation_data(args.players, args.games, 10_000, train_file, args.reward)
    actor.imitation_data(args.players, args.test_games, 500_000, test_file, args.reward)
    train, test = load_imitation(train_file, num_actions, device), load_imitation(test_file, num_actions, device)
    print(f"{len(train)} training and {len(test)} test decisions from greedy games ({time.time() - started:.0f}s)", flush=True)
    optimizer = torch.optim.Adam(net.parameters(), lr=args.lr)
    generator = torch.Generator().manual_seed(args.seed)
    for epoch in range(1, args.epochs + 1):
        policy_loss, value_loss = train_epoch(net, optimizer, train, generator=generator)
        report = evaluate_imitation(net, test)
        acceptable = ", ".join(f"{kind.lower()} {100 * report[kind]['acceptable']:.0f}%" for kind in ("REINFORCE", "ATTACK", "END_ATTACK", "FORTIFY", "SKIP"))
        print(f"epoch {epoch:2d}: policy loss {policy_loss:.3f}, value loss {value_loss:.3f} | acceptable: {acceptable} | "
              f"value explains {100 * report['value_explained']:.0f}% ({time.time() - started:.0f}s)", flush=True)
    net.save(args.out)
    print("saved", args.out, flush=True)
    result = actor.evaluate(args.players, args.match_games, args.out, FINAL_EVALUATION_BOARDS, args.reward)
    print(f"playing greedy: {result['games']} games: won {result['wins']} ({100 * result['winRate']:.0f}%), "
          f"{result['averageTurns']:.1f} turns/game, {result['eliminationsPerGame']:.2f} opponents eliminated/game", flush=True)


def run_ppo(args, actor: Actor, work: Path, device: torch.device) -> None:
    net = make_network(actor, work, args.load, args.seed, device)
    num_actions = net.num_actions
    ppo = Ppo(net, PpoConfig(lr=args.lr, entropy_coef=args.entropy), seed=args.seed)
    current = work / "current.weights"
    rollout_file = work / "rollout.npz"
    eval_opponents = args.eval_opponents or args.opponents
    best_file = Path(args.out)
    last_file = Path(str(args.out) + ".last")

    def evaluate(games: int, board_seed: int | None = None) -> dict:
        net.save(current)
        if board_seed is None:
            return actor.evaluate(args.players, games, current, reward=args.reward, opponents=eval_opponents, bonus=args.bonus)
        return actor.evaluate(args.players, games, current, board_seed, args.reward, eval_opponents, args.bonus)

    def describe(result: dict) -> str:
        return (f"{result['games']} games: won {result['wins']} ({100 * result['winRate']:.0f}%), truncated {result['truncated']}, "
                f"{result['averageTurns']:.1f} turns/game, {result['eliminationsPerGame']:.2f} opponents eliminated/game")

    print(f"start        vs {eval_opponents}:", describe(evaluate(args.eval_games)), flush=True)
    best = -1.0
    started = time.time()
    for iteration in range(1, args.iterations + 1):
        iteration_started = time.time()
        net.save(current)
        info = actor.rollout(args.players, args.games, args.seed * 1_000_003 + iteration, current, rollout_file,
                             args.reward, args.bot_seats, args.lam, args.opponents, args.bonus)
        collected = time.time()
        rollout = load_rollout(rollout_file, num_actions, device)
        stats = ppo.update(rollout)
        updated = time.time()
        win_rate = info["winRateAgainstBots"]
        print(f"iter {iteration:3d}: {info['steps']:5d} steps, {info['averageTurns']:.0f} turns/game, "
              f"{100 * info['truncated'] / info['games']:.0f}% truncated, won {100 * (win_rate if win_rate is not None else float('nan')):.0f}% of games vs bots | "
              f"policy loss {stats['policy_loss']:.4f}, value loss {stats['value_loss']:.4f}, entropy {stats['entropy']:.3f}, "
              f"kl {stats['approx_kl']:.4f}, clipped {100 * stats['clip_fraction']:.1f}% | "
              f"{collected - iteration_started:.1f}s collect, {updated - collected:.1f}s update, {ppo.skipped_steps} skipped, "
              f"{info['eliminationsPerGame']:.2f} eliminations/game", flush=True)
        if iteration % args.eval_every == 0 or iteration == args.iterations:
            result = evaluate(args.eval_games)
            print(f"iter {iteration:3d}: vs {eval_opponents}: {describe(result)} ({(time.time() - started) / 60:.0f} min)", flush=True)
            if result["winRate"] > best:
                best = result["winRate"]
                net.save(best_file)
    net.save(last_file)
    print(f"best win rate vs greedy during training: {100 * best:.0f}%, saved to {best_file}", flush=True)
    # the periodic evaluations all use the same boards, which also chose the best network, so measure both on other boards
    if args.iterations > 0 and args.final_eval_games > 0:
        net.load(last_file)
        print("FINAL last iteration, on new boards:", describe(evaluate(args.final_eval_games, FINAL_EVALUATION_BOARDS)), flush=True)
        net.load(best_file)
        print("FINAL best checkpoint, on new boards:", describe(evaluate(args.final_eval_games, FINAL_EVALUATION_BOARDS)), flush=True)


def run_eval(args, actor: Actor, work: Path, device: torch.device) -> None:
    result = actor.evaluate(args.players, args.games, args.weights, args.board_seed, args.reward, args.opponents, args.bonus)
    print(f"{result['games']} games: won {result['wins']} ({100 * result['winRate']:.0f}%), truncated {result['truncated']}, "
          f"{result['averageTurns']:.1f} turns/game, {result['eliminationsPerGame']:.2f} opponents eliminated/game", flush=True)


def parser() -> argparse.ArgumentParser:
    main = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    main.add_argument("--classpath-file", default=str(Path(__file__).resolve().parent.parent / "work" / "classpath.txt"),
                      help="the file with the class path of the Java actor (made by learner/prepare.sh)")
    main.add_argument("--device", default="auto", help="auto, cpu or mps")
    sub = main.add_subparsers(dest="command", required=True)

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--players", type=int, default=2)
    common.add_argument("--reward", default="default", help="default, advantage or elimination")
    common.add_argument("--seed", type=int, default=1)
    common.add_argument("--bonus", action="store_true", help="play with the conquest and elimination bonuses")
    common.add_argument("--opponents", default="greedy",
                        help="the bots to play against, separated by commas: greedy and cautious (in training, the seats that are not the network's)")

    imitation = sub.add_parser("imitation", parents=[common], help="learn to play as the greedy bot")
    imitation.add_argument("--games", type=int, default=300)
    imitation.add_argument("--test-games", type=int, default=40)
    imitation.add_argument("--epochs", type=int, default=12)
    imitation.add_argument("--lr", type=float, default=1e-3)
    imitation.add_argument("--match-games", type=int, default=400)
    imitation.add_argument("--load", help="start from these weights")
    imitation.add_argument("--out", required=True)
    imitation.set_defaults(run=run_imitation)

    ppo = sub.add_parser("ppo", parents=[common], help="improve by playing against itself and greedy bots")
    ppo.add_argument("--iterations", type=int, default=100)
    ppo.add_argument("--games", type=int, default=8, help="games in each iteration")
    ppo.add_argument("--lr", type=float, default=1e-4)
    ppo.add_argument("--entropy", type=float, default=0.003)
    ppo.add_argument("--lam", type=float, default=0.95)
    ppo.add_argument("--bot-seats", type=float, default=0.25, help="the share of the seats that bots (the opponents) take")
    ppo.add_argument("--eval-opponents", help="the bots to measure against, if not the same as to train against")
    ppo.add_argument("--eval-every", type=int, default=25)
    ppo.add_argument("--eval-games", type=int, default=100)
    ppo.add_argument("--final-eval-games", type=int, default=300)
    ppo.add_argument("--load", help="start from these weights")
    ppo.add_argument("--out", required=True, help="where the best network is saved, and the last one in the same name with .last")
    ppo.set_defaults(run=run_ppo)

    evaluate = sub.add_parser("eval", parents=[common], help="how often the network beats greedy bots")
    evaluate.add_argument("--weights", required=True)
    evaluate.add_argument("--games", type=int, default=300)
    evaluate.add_argument("--board-seed", type=int, default=987_654_321)
    evaluate.set_defaults(run=run_eval)
    return main


def main(argv: list[str] | None = None) -> None:
    args = parser().parse_args(argv)
    torch.manual_seed(args.seed)
    device = pick_device(args.device)
    print(f"learning on {device}", flush=True)
    with tempfile.TemporaryDirectory(prefix="risk-learner-") as directory, \
            Actor(Actor.classpath_from_file(args.classpath_file)) as actor:
        args.run(args, actor, Path(directory), device)


if __name__ == "__main__":
    main()
