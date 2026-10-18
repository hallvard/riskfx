"""The network, as it is in Java: ``PolicyValueNet`` in training, and ``JavaPolicyNetwork`` in the core, which runs it in the app.

The three must compute the same, so this follows ``JavaPolicyNetwork`` step by step, and the tests compare them.

1. each territory's features and the global features are projected to a vector of size ``hidden``, and normalized
2. ``layers`` rounds of message passing: a territory is updated from itself and its neighbours (the normalized adjacency
   matrix with self-loops), normalized, and added to what it was
3. heads: reinforcement scores each territory, attack and fortify score each pair of territories with a dot product
   of two projections, "end attack" and "skip fortify" are scored from the average territory vector, which also gives the value

The scores are in the layout of the action space: reinforce (N), attack (N*N), end attack, fortify (N*N), skip fortify.
"""
from __future__ import annotations

import math

import numpy as np
import torch
from torch import nn

from .weights import Config, read_weights, write_weights

NORMALIZATION_EPSILON = 1e-5


def normalized_adjacency(borders: np.ndarray) -> torch.Tensor:
    """D^-1/2 (A + I) D^-1/2, for the matrix of borders A."""
    a = (np.asarray(borders) != 0).astype(np.float64)
    a = np.maximum(a, np.eye(len(a)))
    degrees = a.sum(axis=1)
    return torch.tensor(a / np.sqrt(np.outer(degrees, degrees)), dtype=torch.float32)


def action_space_size(num_territories: int) -> int:
    return num_territories + num_territories ** 2 + 1 + num_territories ** 2 + 1


def parameter_shapes(config: Config) -> dict[str, tuple[int, ...]]:
    f, g, h, d = config.node_features, config.global_features, config.hidden, config.attention
    shapes: dict[str, tuple[int, ...]] = {"Wx": (f, h), "Wg": (g, h), "b0": (h,)}
    for layer in range(config.layers):
        shapes[f"Wn{layer}"] = (h, h)
        shapes[f"Ws{layer}"] = (h, h)
        shapes[f"b{layer + 1}"] = (h,)
    shapes.update({"Wrn": (h, h), "Wrg": (h, h), "br": (h,), "Wro": (h, 1),
                   "Wqa": (h, d), "Wka": (h, d), "Wqf": (h, d), "Wkf": (h, d),
                   "We": (h, 2), "be": (2,), "Wv1": (h, h), "bv1": (h,), "Wv2": (h, 1), "bv2": (1,)})
    return shapes


def _he(fan_in: int) -> float:
    return math.sqrt(2.0 / fan_in)


def initial_std(config: Config) -> dict[str, float]:
    """The standard deviation of the starting values of the weights, 0 for those that start at zero, as in ``PolicyValueNet``."""
    f, g, h = config.node_features, config.global_features, config.hidden
    std = {"Wx": _he(f), "Wg": _he(g), "b0": 0.0}
    for layer in range(config.layers):
        std[f"Wn{layer}"] = _he(h)
        std[f"Ws{layer}"] = _he(h)
        std[f"b{layer + 1}"] = 0.0
    std.update({"Wrn": _he(h), "Wrg": _he(h), "br": 0.0, "Wro": 0.01,
                # small, so the initial policy is close to uniform
                "Wqa": 0.3 / math.sqrt(h), "Wka": 0.3 / math.sqrt(h), "Wqf": 0.3 / math.sqrt(h), "Wkf": 0.3 / math.sqrt(h),
                "We": 0.01, "be": 0.0, "Wv1": _he(h), "bv1": 0.0, "Wv2": 0.01, "bv2": 0.0})
    return std


def _normalize(x: torch.Tensor) -> torch.Tensor:
    centered = x - x.mean(dim=-1, keepdim=True)
    variance = centered.pow(2).mean(dim=-1, keepdim=True)
    return centered / torch.sqrt(variance + NORMALIZATION_EPSILON)


class PolicyValueNet(nn.Module):

    def __init__(self, config: Config, borders: np.ndarray, seed: int | None = None):
        super().__init__()
        self.config = config
        self.register_buffer("adjacency", normalized_adjacency(borders))
        generator = torch.Generator().manual_seed(seed) if seed is not None else None
        std = initial_std(config)
        self.p = nn.ParameterDict()
        for name, shape in parameter_shapes(config).items():
            values = torch.randn(shape, generator=generator) * std[name] if std[name] > 0 else torch.zeros(shape)
            self.p[name] = nn.Parameter(values)

    @property
    def num_territories(self) -> int:
        return self.adjacency.shape[0]

    @property
    def num_actions(self) -> int:
        return action_space_size(self.num_territories)

    def set_borders(self, borders: np.ndarray) -> None:
        """Plays on another map: the weights don't depend on the map, only the graph the territories send messages along."""
        self.adjacency = normalized_adjacency(borders).to(self.adjacency.device)

    def forward(self, x: torch.Tensor, g: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        """Scores of the actions, (batch, actions), and values, (batch,), for the territory features (batch, N, F) and global features (batch, G)."""
        p = self.p
        batch = x.shape[0]
        n = self.num_territories
        d = self.config.attention
        h = torch.relu(_normalize(x @ p["Wx"] + (g @ p["Wg"]).unsqueeze(1) + p["b0"]))
        for layer in range(self.config.layers):
            neighbours = self.adjacency @ h
            h = h + torch.relu(_normalize(neighbours @ p[f"Wn{layer}"] + h @ p[f"Ws{layer}"] + p[f"b{layer + 1}"]))
        e = h.mean(dim=1)

        reinforce = (torch.relu(h @ p["Wrn"] + (e @ p["Wrg"]).unsqueeze(1) + p["br"]) @ p["Wro"]).squeeze(-1)
        attack = self._pair_scores(h, p["Wqa"], p["Wka"], d).reshape(batch, n * n)
        fortify = self._pair_scores(h, p["Wqf"], p["Wkf"], d).reshape(batch, n * n)
        ends = e @ p["We"] + p["be"]
        logits = torch.cat([reinforce, attack, ends[:, 0:1], fortify, ends[:, 1:2]], dim=1)
        values = (torch.relu(e @ p["Wv1"] + p["bv1"]) @ p["Wv2"] + p["bv2"]).squeeze(-1)
        return logits, values

    @staticmethod
    def _pair_scores(h: torch.Tensor, wq: torch.Tensor, wk: torch.Tensor, d: int) -> torch.Tensor:
        return (h @ wq) @ (h @ wk).transpose(1, 2) / math.sqrt(d)

    # weights

    def to_weights(self) -> dict[str, np.ndarray]:
        return {name: parameter.detach().cpu().numpy() for name, parameter in self.p.items()}

    def save(self, path) -> None:
        write_weights(path, self.config, self.to_weights())

    def load_tensors(self, tensors: dict[str, np.ndarray]) -> None:
        expected = parameter_shapes(self.config)
        if set(tensors) != set(expected):
            raise ValueError(f"The weights have the arrays {sorted(tensors)}, not {sorted(expected)}")
        with torch.no_grad():
            for name, shape in expected.items():
                if tuple(tensors[name].shape) != shape:
                    raise ValueError(f"The array {name} has the shape {tensors[name].shape}, not {shape}")
                self.p[name].copy_(torch.from_numpy(np.asarray(tensors[name], dtype=np.float32)))

    def load(self, path) -> None:
        config, tensors = read_weights(path)
        if config != self.config:
            raise ValueError(f"The saved network has the size {config}, not {self.config}")
        self.load_tensors(tensors)


def masked_log_probabilities(logits: torch.Tensor, legal: torch.Tensor) -> torch.Tensor:
    """Log of the probabilities of the actions, where the illegal ones (False in ``legal``) have none."""
    return torch.log_softmax(logits.masked_fill(~legal, -1e9), dim=-1)
