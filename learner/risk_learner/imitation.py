"""Learning to play as the greedy bot does, to get a start, or to see what the network can represent.

The network is trained to choose the same action as greedy among the legal ones (cross entropy), and to predict
the value, the rewards that greedy's games got.
"""
from __future__ import annotations

import torch

from .data import KINDS, ImitationData
from .model import PolicyValueNet, masked_log_probabilities


def train_epoch(net: PolicyValueNet, optimizer: torch.optim.Optimizer, data: ImitationData, batch_size: int = 256,
                value_coef: float = 0.5, max_grad_norm: float = 1.0, generator: torch.Generator | None = None) -> tuple[float, float]:
    """One pass over the data in random minibatches. Returns the average policy loss and value loss."""
    order = torch.randperm(len(data), generator=generator).to(data.obs.device)
    policy_total = value_total = 0.0
    batches = 0
    for start in range(0, len(data), batch_size):
        rows = order[start:start + batch_size]
        logits, values = net(data.obs[rows], data.globals[rows])
        log_probs = masked_log_probabilities(logits, data.legal[rows])
        policy_loss = -log_probs.gather(1, data.action[rows].unsqueeze(1)).mean()
        value_loss = (values - data.value_target[rows]).pow(2).mean()
        optimizer.zero_grad(set_to_none=True)
        (policy_loss + value_coef * value_loss).backward()
        norm = torch.nn.utils.clip_grad_norm_(net.parameters(), max_grad_norm)
        if torch.isfinite(norm):
            optimizer.step()
        policy_total += policy_loss.item()
        value_total += value_loss.item()
        batches += 1
    return policy_total / batches, value_total / batches


@torch.no_grad()
def evaluate(net: PolicyValueNet, data: ImitationData, batch_size: int = 512) -> dict:
    """How often the most probable legal action is the one greedy chose (``exact``), or as good as it (``acceptable``), for each kind
    of decision, and how much of the variance of the value targets the value explains."""
    predicted_all = []
    value_error = 0.0
    for start in range(0, len(data), batch_size):
        rows = slice(start, start + batch_size)
        logits, values = net(data.obs[rows], data.globals[rows])
        predicted_all.append(logits.masked_fill(~data.legal[rows], -1e9).argmax(dim=1))
        value_error += (values - data.value_target[rows]).pow(2).sum().item()
    predicted = torch.cat(predicted_all)
    exact = predicted == data.action
    acceptable = data.acceptable.gather(1, predicted.unsqueeze(1)).squeeze(1)
    result: dict = {}
    for number, kind in enumerate(KINDS):
        selected = data.kind == number
        count = int(selected.sum())
        result[kind] = {"samples": count,
                        "exact": float(exact[selected].float().mean()) if count else float("nan"),
                        "acceptable": float(acceptable[selected].float().mean()) if count else float("nan")}
    variance = data.value_target.var(unbiased=False).item()
    mse = value_error / len(data)
    result["value_mse"] = mse
    result["value_explained"] = 1 - mse / variance if variance > 0 else float("nan")
    return result
