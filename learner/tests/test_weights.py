import numpy as np
import pytest

from risk_learner.weights import Config, read_weights, write_weights
from tests.conftest import GOLDEN


def test_weights_survive_being_written_and_read(tmp_path):
    config = Config(13, 14, hidden=4, layers=1, attention=2)
    tensors = {"Wx": np.arange(8, dtype=np.float32).reshape(2, 4), "b0": np.array([1.5, -2, 0, 3], dtype=np.float32),
               "Wro": np.ones((4, 1), dtype=np.float32)}
    write_weights(tmp_path / "w.weights", config, tensors)
    read_config, read_tensors = read_weights(tmp_path / "w.weights")
    assert read_config == config
    assert list(read_tensors) == ["Wx", "b0", "Wro"]
    for name, array in tensors.items():
        assert read_tensors[name].shape == array.shape
        np.testing.assert_array_equal(read_tensors[name], array)


def test_something_else_than_weights_is_rejected(tmp_path):
    (tmp_path / "x.weights").write_bytes(b"NDLI\x00\x00\x00\x01rest")
    with pytest.raises(ValueError):
        read_weights(tmp_path / "x.weights")


def test_the_file_is_in_the_big_endian_format_of_java(tmp_path):
    write_weights(tmp_path / "w.weights", Config(13, 14, 4, 1, 2), {"a": np.array([1.0], dtype=np.float32)})
    data = (tmp_path / "w.weights").read_bytes()
    assert data[:4] == bytes([0x52, 0x46, 0x58, 0x57])  # 'RFXW'
    assert data[4:8] == bytes([0, 0, 0, 2])  # version 2
    assert data[-4:] == bytes([0x3F, 0x80, 0, 0])  # 1.0f, big-endian


def test_the_weights_that_java_wrote_can_be_read():
    # networks of the AI, written by Java (ConvertWeights), if they are there
    path = GOLDEN.parents[4] / "models" / "ai-2p.weights"
    if not path.exists():
        pytest.skip("no " + str(path))
    config, tensors = read_weights(path)
    assert config.hidden == 64 and config.layers == 3
    assert tensors["Wx"].shape == (config.node_features, 64)
    assert all(np.isfinite(array).all() for array in tensors.values())
