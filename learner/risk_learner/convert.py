"""Converts networks from before the features were made independent of the map (version 1 of the weights file, 14 territory
features with one for each continent, and 22 global features) to the present ones (version 2, 13 and 14).

The features that stay keep their weights, the new ones start at zero, and the rest of the network is as it was, so
the converted network plays almost as before and can be trained further:

    python -m risk_learner.convert old.weights new.weights
"""
from __future__ import annotations

import argparse
import struct
from pathlib import Path

import numpy as np

from .weights import Config, write_weights

KEPT_TERRITORY_FEATURES = 8
KEPT_GLOBAL_FEATURES = 10
TERRITORY_FEATURES = 13
GLOBAL_FEATURES = 14


def read_version_1(path: str | Path) -> tuple[tuple[int, ...], dict[str, np.ndarray]]:
    data = memoryview(Path(path).read_bytes())
    magic, version = struct.unpack_from(">ii", data, 0)
    if magic != 0x52465857 or version != 1:
        raise ValueError("Not a version 1 file with network weights: " + str(path))
    config = struct.unpack_from(">6i", data, 8)
    (count,) = struct.unpack_from(">i", data, 32)
    position = 36
    tensors: dict[str, np.ndarray] = {}
    for _ in range(count):
        (length,) = struct.unpack_from(">H", data, position)
        position += 2
        name = bytes(data[position:position + length]).decode("utf-8")
        position += length
        (rank,) = struct.unpack_from(">i", data, position)
        position += 4
        shape = struct.unpack_from(f">{rank}i", data, position)
        position += 4 * rank
        size = int(np.prod(shape)) if rank else 1
        tensors[name] = np.frombuffer(bytes(data[position:position + 4 * size]), dtype=">f4").astype(np.float32).reshape(shape)
        position += 4 * size
    return config, tensors


def convert(old_path: str | Path, new_path: str | Path) -> Config:
    (_, _, _, hidden, layers, attention), tensors = read_version_1(old_path)
    tensors = dict(tensors)
    wx = np.zeros((TERRITORY_FEATURES, hidden), dtype=np.float32)
    wx[:KEPT_TERRITORY_FEATURES] = tensors["Wx"][:KEPT_TERRITORY_FEATURES]
    wg = np.zeros((GLOBAL_FEATURES, hidden), dtype=np.float32)
    wg[:KEPT_GLOBAL_FEATURES] = tensors["Wg"][:KEPT_GLOBAL_FEATURES]
    tensors["Wx"], tensors["Wg"] = wx, wg
    config = Config(TERRITORY_FEATURES, GLOBAL_FEATURES, hidden, layers, attention)
    write_weights(new_path, config, tensors)
    return config


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("old")
    parser.add_argument("new")
    arguments = parser.parse_args()
    print(convert(arguments.old, arguments.new))
