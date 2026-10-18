import numpy as np
import pytest
import torch

from risk_learner.model import PolicyValueNet, action_space_size, masked_log_probabilities, normalized_adjacency, parameter_shapes
from risk_learner.weights import Config, read_weights
from tests.conftest import GOLDEN


def golden_network(golden, borders) -> PolicyValueNet:
    config, tensors = read_weights(GOLDEN / "golden.weights")
    net = PolicyValueNet(config, borders)
    net.load_tensors(tensors)
    return net


def test_the_scores_and_values_are_those_of_the_golden_file(golden, borders):
    """The same file is the check of the network in Java: what Python computes here, Java computes for the same weights."""
    net = golden_network(golden, borders)
    x = torch.tensor(golden["territories"], dtype=torch.float32)
    g = torch.tensor(golden["globals"], dtype=torch.float32)
    with torch.no_grad():
        logits, values = net(x, g)
    np.testing.assert_allclose(logits.numpy(), np.array(golden["logits"], dtype=np.float32), rtol=1e-4, atol=1e-5)
    np.testing.assert_allclose(values.numpy(), np.array(golden["values"], dtype=np.float32), rtol=1e-4, atol=1e-5)


def test_the_size_of_the_output_is_that_of_the_action_space(golden, borders):
    net = golden_network(golden, borders)
    n = net.num_territories
    assert action_space_size(n) == 3572
    logits, values = net(torch.zeros(5, n, net.config.node_features), torch.zeros(5, net.config.global_features))
    assert logits.shape == (5, 3572) and values.shape == (5,)


def test_a_state_gives_the_same_result_in_a_batch_and_alone(golden, borders):
    net = golden_network(golden, borders)
    x = torch.tensor(golden["territories"], dtype=torch.float32)
    g = torch.tensor(golden["globals"], dtype=torch.float32)
    with torch.no_grad():
        batch_logits, batch_values = net(x, g)
        for i in range(len(x)):
            logits, values = net(x[i:i + 1], g[i:i + 1])
            torch.testing.assert_close(logits[0], batch_logits[i])
            torch.testing.assert_close(values[0], batch_values[i])


def test_the_adjacency_is_normalized_and_symmetric(borders):
    adjacency = normalized_adjacency(borders)
    torch.testing.assert_close(adjacency, adjacency.T)
    # a territory with the degree d (itself included) has weight 1/d for itself, when its neighbours have the same degree
    degrees = (borders != 0).sum(axis=1) + 1
    for i in range(len(borders)):
        assert adjacency[i, i].item() == pytest.approx(1 / degrees[i])
    assert (adjacency > 0).sum().item() == (borders != 0).sum() + len(borders)


def test_a_new_network_starts_with_small_scores_and_a_value_near_zero(borders):
    config = Config(13, 14)
    net = PolicyValueNet(config, borders, seed=3)
    x = torch.rand(8, 42, 13)
    logits, values = net(x, torch.rand(8, 14))
    assert logits.abs().max().item() < 3
    assert values.abs().max().item() < 1


def test_the_same_seed_gives_the_same_network(borders):
    config = Config(13, 14, hidden=8, layers=1, attention=4)
    a, b, c = (PolicyValueNet(config, borders, seed=s) for s in (1, 1, 2))
    for name in parameter_shapes(config):
        torch.testing.assert_close(a.p[name], b.p[name])
    assert not torch.equal(a.p["Wx"], c.p["Wx"])


def test_weights_of_another_size_are_rejected(tmp_path, borders):
    small = PolicyValueNet(Config(13, 14, hidden=8, layers=1, attention=4), borders)
    small.save(tmp_path / "small.weights")
    big = PolicyValueNet(Config(13, 14, hidden=16, layers=1, attention=4), borders)
    with pytest.raises(ValueError):
        big.load(tmp_path / "small.weights")


def test_illegal_actions_have_no_probability():
    logits = torch.tensor([[0.0, 1000.0, 2.0, -3.0, 500.0]])
    legal = torch.tensor([[True, False, True, True, False]])
    probabilities = masked_log_probabilities(logits, legal).exp()
    assert probabilities.sum().item() == pytest.approx(1.0)
    assert probabilities[0, 1].item() == 0 and probabilities[0, 4].item() == 0
    expected = np.exp(2) / (1 + np.exp(2) + np.exp(-3))
    assert probabilities[0, 2].item() == pytest.approx(expected)


def test_the_weights_work_on_another_map(borders):
    """The weights don't depend on the map: a smaller graph gives scores for its territories, and the old map still works after."""
    config = Config(13, 14, hidden=8, layers=2, attention=4)
    net = PolicyValueNet(config, borders, seed=5)
    x = torch.rand(2, 42, 13)
    g = torch.rand(2, 14)
    before, _ = net(x, g)
    ring = np.zeros((6, 6), dtype=np.int32)
    for i in range(6):
        ring[i, (i + 1) % 6] = ring[(i + 1) % 6, i] = 1
    net.set_borders(ring)
    logits, values = net(torch.rand(2, 6, 13), g)
    assert net.num_territories == 6 and logits.shape == (2, action_space_size(6)) and values.shape == (2,)
    net.set_borders(borders)
    after, _ = net(x, g)
    torch.testing.assert_close(before, after)
