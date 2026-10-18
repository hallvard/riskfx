"""The file with the weights of a network, in the format of ``NetworkWeights`` in the Java core.

Numbers are big-endian, as Java writes them: the number 'RFXW', the version, the size of the network (five numbers),
the number of arrays, and for each its name, the number of dimensions, the dimensions and the values.
"""
from __future__ import annotations

import struct
from dataclasses import dataclass
from pathlib import Path

import numpy as np

MAGIC = 0x52465857
VERSION = 2


@dataclass(frozen=True)
class Config:
    """The size of a network, which doesn't depend on the map."""
    node_features: int
    global_features: int
    hidden: int = 64
    layers: int = 3
    attention: int = 32

    def as_tuple(self) -> tuple[int, ...]:
        return (self.node_features, self.global_features, self.hidden, self.layers, self.attention)


def write_weights(path: str | Path, config: Config, tensors: dict[str, np.ndarray]) -> None:
    parts = [struct.pack(">ii5ii", MAGIC, VERSION, *config.as_tuple(), len(tensors))]
    for name, array in tensors.items():
        array = np.asarray(array, dtype=np.float32)
        encoded = name.encode("utf-8")
        parts.append(struct.pack(">H", len(encoded)) + encoded)
        parts.append(struct.pack(">i", array.ndim) + b"".join(struct.pack(">i", d) for d in array.shape))
        parts.append(array.astype(">f4").tobytes())
    Path(path).write_bytes(b"".join(parts))


def read_weights(path: str | Path) -> tuple[Config, dict[str, np.ndarray]]:
    data = memoryview(Path(path).read_bytes())
    magic, version = struct.unpack_from(">ii", data, 0)
    if magic != MAGIC:
        raise ValueError("Not a file with network weights: " + str(path))
    if version != VERSION:
        raise ValueError(f"Unknown version of the weights: {version}")
    config = Config(*struct.unpack_from(">5i", data, 8))
    (count,) = struct.unpack_from(">i", data, 28)
    position = 32
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
        values = np.frombuffer(bytes(data[position:position + 4 * size]), dtype=">f4").astype(np.float32)
        position += 4 * size
        tensors[name] = values.reshape(shape)
    return config, tensors
