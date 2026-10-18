"""The client of the Java actor, which plays the games. See ``Actor`` in the training module for the commands."""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path


class ActorError(RuntimeError):
    pass


class Actor:
    """Starts the Java actor as a process, and gives it commands. Use it in a ``with`` statement, so the process is ended."""

    def __init__(self, classpath: str, java: str = "java", jvm_args: tuple[str, ...] = ("--enable-native-access=ALL-UNNAMED",)):
        self.process = subprocess.Popen(
            [java, *jvm_args, "-cp", classpath, "no.hal.risk.training.Actor"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=sys.stderr, text=True, bufsize=1)

    @staticmethod
    def classpath_from_file(path: str | Path) -> str:
        return Path(path).read_text().strip()

    def command(self, verb: str, **arguments) -> dict:
        line = " ".join([verb, *(f"{key}={value}" for key, value in arguments.items())])
        if self.process.poll() is not None:
            raise ActorError("The Java actor has ended")
        self.process.stdin.write(line + "\n")
        self.process.stdin.flush()
        answer = self.process.stdout.readline()
        if not answer:
            raise ActorError(f"The Java actor ended while giving {line}")
        answer = answer.strip()
        if answer.startswith("ERROR"):
            raise ActorError(f"{line}: {answer}")
        if not answer.startswith("OK"):
            raise ActorError(f"Unexpected answer to {line}: {answer}")
        return json.loads(answer[2:] or "{}")

    def info(self, out) -> dict:
        return self.command("INFO", out=out)

    def rollout(self, players: int, games: int, seed: int, weights, out, reward: str = "default", bot_seats: float = 0.25,
                lam: float = 0.95, opponents: str = "greedy", bonus: bool = False) -> dict:
        # "lambda" is a keyword of Python, so it can't be written as an argument
        return self.command("ROLLOUT", players=players, games=games, seed=seed, weights=weights, out=out, reward=reward,
                            botSeats=bot_seats, opponents=opponents, bonus=int(bonus), **{"lambda": lam})

    def evaluate(self, players: int, games: int, weights, board_seed: int = 987_654_321, reward: str = "default",
                 opponents: str = "greedy", bonus: bool = False) -> dict:
        return self.command("EVAL", players=players, games=games, weights=weights, boardSeed=board_seed, reward=reward,
                            opponents=opponents, bonus=int(bonus))

    def imitation_data(self, players: int, games: int, seed: int, out, reward: str = "default") -> dict:
        return self.command("IMITATION", players=players, games=games, seed=seed, out=out, reward=reward)

    def close(self) -> None:
        if self.process.poll() is None:
            try:
                self.process.stdin.write("QUIT\n")
                self.process.stdin.flush()
                self.process.wait(timeout=10)
            except Exception:
                self.process.kill()

    def __enter__(self) -> "Actor":
        return self

    def __exit__(self, *exception) -> None:
        self.close()
