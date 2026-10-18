"""Proximal policy optimization, as ``Ppo`` in Java did it.

For each step, the ratio between the probability the new policy gives the chosen action and the one it had when it was chosen
says how much the policy has changed. Actions with positive advantage should become more probable, those with negative less,
but the ratio is clipped to stay within 1 +/- ``clip``, so one batch can't change the policy too much. The value is trained
towards the value targets, and an entropy bonus keeps the policy from becoming deterministic too early.
"""
from __future__ import annotations

from dataclasses import dataclass

import torch

from .data import Rollout
from .model import PolicyValueNet, masked_log_probabilities


@dataclass
class PpoConfig:
    lr: float = 3e-4
    clip: float = 0.2
    epochs: int = 4
    minibatch: int = 256
    value_coef: float = 0.5
    entropy_coef: float = 0.01
    max_grad_norm: float = 0.5


class Ppo:

    def __init__(self, net: PolicyValueNet, config: PpoConfig | None = None, seed: int = 1):
        self.net = net
        self.config = config or PpoConfig()
        self.optimizer = torch.optim.Adam(net.parameters(), lr=self.config.lr)
        self.generator = torch.Generator().manual_seed(seed)
        self.skipped_steps = 0

    def update(self, rollout: Rollout) -> dict[str, float]:
        """Epochs over the rollout in random minibatches. Returns the average of the statistics."""
        config = self.config
        count = len(rollout)
        if count == 0:
            raise ValueError("Nothing to learn from")
        # the size of the rewards doesn't matter
        advantage = (rollout.advantage - rollout.advantage.mean()) / (rollout.advantage.std(unbiased=False) + 1e-8)
        sums = {"policy_loss": 0.0, "value_loss": 0.0, "entropy": 0.0, "approx_kl": 0.0, "clip_fraction": 0.0}
        minibatches = 0
        for _ in range(config.epochs):
            order = torch.randperm(count, generator=self.generator).to(rollout.obs.device)
            for start in range(0, count, config.minibatch):
                rows = order[start:start + config.minibatch]
                stats = self._minibatch(rollout, advantage, rows)
                for key in sums:
                    sums[key] += stats[key]
                minibatches += 1
        return {key: value / minibatches for key, value in sums.items()}

    def _minibatch(self, rollout: Rollout, advantage: torch.Tensor, rows: torch.Tensor) -> dict[str, float]:
        config = self.config
        logits, values = self.net(rollout.obs[rows], rollout.globals[rows])
        log_probs = masked_log_probabilities(logits, rollout.legal[rows])
        new_log_prob = log_probs.gather(1, rollout.action[rows].unsqueeze(1)).squeeze(1)
        old_log_prob = rollout.log_prob[rows]
        # limited, so the ratio can't overflow when the old probability was tiny
        ratio = torch.exp(torch.clamp(new_log_prob - old_log_prob, -20, 20))
        batch_advantage = advantage[rows]
        policy_loss = -torch.minimum(ratio * batch_advantage, torch.clamp(ratio, 1 - config.clip, 1 + config.clip) * batch_advantage).mean()
        value_loss = (values - rollout.value_target[rows]).pow(2).mean()
        entropy = -(log_probs.exp() * log_probs).sum(dim=1).mean()
        loss = policy_loss + config.value_coef * value_loss - config.entropy_coef * entropy
        self.optimizer.zero_grad(set_to_none=True)
        loss.backward()
        norm = torch.nn.utils.clip_grad_norm_(self.net.parameters(), config.max_grad_norm)
        if torch.isfinite(norm):
            self.optimizer.step()
        else:
            # the gradients are cleared at the next step, so they can't stay as they are
            self.skipped_steps += 1
        return {"policy_loss": policy_loss.item(), "value_loss": value_loss.item(), "entropy": entropy.item(),
                "approx_kl": (old_log_prob - new_log_prob).mean().item(),
                "clip_fraction": ((ratio - 1).abs() > config.clip).float().mean().item()}
