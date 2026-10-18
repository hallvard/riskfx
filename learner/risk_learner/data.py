"""Rollouts and imitation data, as the Java actor writes them (``RolloutWriter``), as tensors for the learner."""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import torch

KINDS = ["REINFORCE", "ATTACK", "END_ATTACK", "FORTIFY", "SKIP"]


def legal_mask(offsets: np.ndarray, indices: np.ndarray, num_actions: int) -> np.ndarray:
    """A matrix with a row for each step and a column for each action, True for the legal ones, from the lists of legal actions."""
    steps = len(offsets) - 1
    mask = np.zeros((steps, num_actions), dtype=bool)
    rows = np.repeat(np.arange(steps), np.diff(offsets))
    mask[rows, indices] = True
    return mask


@dataclass
class Rollout:
    """The decisions of the network in a number of games. ``legal`` is True for the legal actions of each step."""
    obs: torch.Tensor
    globals: torch.Tensor
    legal: torch.Tensor
    action: torch.Tensor
    log_prob: torch.Tensor
    value: torch.Tensor
    advantage: torch.Tensor
    value_target: torch.Tensor
    reward: torch.Tensor
    stats: dict[str, float]

    def __len__(self) -> int:
        return self.obs.shape[0]


def load_rollout(path, num_actions: int, device: torch.device | str = "cpu") -> Rollout:
    with np.load(path) as data:
        def tensor(name: str, dtype=torch.float32) -> torch.Tensor:
            return torch.from_numpy(data[name]).to(device=device, dtype=dtype)

        stats = data["stats"]
        return Rollout(
            obs=tensor("obs"), globals=tensor("globals"),
            legal=torch.from_numpy(legal_mask(data["legal_offsets"], data["legal_indices"], num_actions)).to(device),
            action=tensor("action", torch.int64), log_prob=tensor("log_prob"), value=tensor("value"),
            advantage=tensor("advantage"), value_target=tensor("value_target"), reward=tensor("reward"),
            stats={"games": stats[0], "truncated": stats[1], "average_turns": stats[2],
                   "win_rate_against_bots": stats[3], "eliminations_per_game": stats[4]})


@dataclass
class ImitationData:
    """The decisions of the greedy bot, with the actions that are as good as the one chosen (``acceptable``), and the kind of each."""
    obs: torch.Tensor
    globals: torch.Tensor
    legal: torch.Tensor
    acceptable: torch.Tensor
    action: torch.Tensor
    kind: torch.Tensor
    value_target: torch.Tensor

    def __len__(self) -> int:
        return self.obs.shape[0]

    def subset(self, rows: torch.Tensor) -> "ImitationData":
        return ImitationData(*(getattr(self, name)[rows] for name in
                               ("obs", "globals", "legal", "acceptable", "action", "kind", "value_target")))


def load_imitation(path, num_actions: int, device: torch.device | str = "cpu") -> ImitationData:
    with np.load(path) as data:
        return ImitationData(
            obs=torch.from_numpy(data["obs"]).to(device), globals=torch.from_numpy(data["globals"]).to(device),
            legal=torch.from_numpy(legal_mask(data["legal_offsets"], data["legal_indices"], num_actions)).to(device),
            acceptable=torch.from_numpy(legal_mask(data["acceptable_offsets"], data["acceptable_indices"], num_actions)).to(device),
            action=torch.from_numpy(data["action"]).to(device=device, dtype=torch.int64),
            kind=torch.from_numpy(data["kind"]).to(device=device, dtype=torch.int64),
            value_target=torch.from_numpy(data["value_target"]).to(device))
