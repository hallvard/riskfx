"""Writes the golden files, which the tests of Python and of Java use to check that the network computes the same in both:

* ``golden.weights``: random weights for a small network, in the format of ``NetworkWeights``, written here and read by Java
* ``golden.json``: the borders of the map, some states, and the scores and values that this model computes for them

Run it again if the network changes (it needs the Java actor, so run ``learner/prepare.sh`` first):

    python -m risk_learner.make_golden
"""
from __future__ import annotations

import argparse
import json
import tempfile
from pathlib import Path

import numpy as np
import torch

from .actor import Actor
from .model import PolicyValueNet, initial_std, parameter_shapes
from .weights import Config

DEFAULT_OUT = Path(__file__).resolve().parents[2] / "core" / "src" / "test" / "resources" / "golden"


def make(out: Path, classpath_file: str, seed: int = 20260901, states: int = 3) -> None:
    out.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory, Actor(Actor.classpath_from_file(classpath_file)) as actor:
        actor.info(Path(directory) / "info.npz")
        with np.load(Path(directory) / "info.npz") as info:
            borders = info["adjacency"].astype(np.int32)
            n, features, global_features, _ = (int(value) for value in info["dims"])
    config = Config(features, global_features, hidden=16, layers=2, attention=8)
    rng = np.random.default_rng(seed)
    # the weights are random and not small, so the values of the layers are of different sizes
    tensors = {name: (rng.standard_normal(shape) * 0.4).astype(np.float32) for name in initial_std(config)
               for shape in [parameter_shapes(config)[name]]}
    net = PolicyValueNet(config, borders)
    net.load_tensors(tensors)
    net.save(out / "golden.weights")

    observations = rng.random((states, n, features)).astype(np.float32)
    observations[:, :, :2] = (observations[:, :, :2] > 0.5)  # some features are 0 or 1, as "mine" and "enemy"
    global_values = rng.random((states, global_features)).astype(np.float32)
    with torch.no_grad():
        logits, values = net(torch.from_numpy(observations), torch.from_numpy(global_values))
    document = {
        "config": list(config.as_tuple()),
        "borders": borders.tolist(),
        "territories": np.round(observations, 7).tolist(),
        "globals": np.round(global_values, 7).tolist(),
        "logits": np.round(logits.numpy(), 7).tolist(),
        "values": np.round(values.numpy(), 7).tolist(),
    }
    (out / "golden.json").write_text(json.dumps(document, separators=(",", ":")))
    print("wrote", out / "golden.weights", "and", out / "golden.json")


if __name__ == "__main__":
    argument_parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    argument_parser.add_argument("--out", default=str(DEFAULT_OUT))
    argument_parser.add_argument("--classpath-file", default=str(Path(__file__).resolve().parents[1] / "work" / "classpath.txt"))
    arguments = argument_parser.parse_args()
    make(Path(arguments.out), arguments.classpath_file)
