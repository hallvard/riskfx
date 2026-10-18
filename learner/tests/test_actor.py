import numpy as np
import torch

from risk_learner.data import load_imitation, load_rollout
from risk_learner.imitation import evaluate
from risk_learner.model import PolicyValueNet, action_space_size
from risk_learner.ppo import Ppo, PpoConfig
from risk_learner.weights import Config


def network(actor, tmp_path) -> PolicyValueNet:
    actor.info(tmp_path / "info.npz")
    with np.load(tmp_path / "info.npz") as info:
        borders = info["adjacency"]
        n, features, global_features, _ = (int(v) for v in info["dims"])
    return PolicyValueNet(Config(features, global_features, hidden=16, layers=2, attention=8), borders, seed=1)


def test_the_java_actor_plays_with_weights_from_python_and_the_learner_can_use_the_games(java_actor, tmp_path):
    net = network(java_actor, tmp_path)
    net.save(tmp_path / "w.weights")
    info = java_actor.rollout(2, 3, 5, tmp_path / "w.weights", tmp_path / "rollout.npz")
    assert info["games"] == 3 and info["steps"] > 50
    rollout = load_rollout(tmp_path / "rollout.npz", action_space_size(42))
    assert len(rollout) == info["steps"]
    assert rollout.obs.shape == (info["steps"], 42, 13)
    # the action of each step is one of the legal
    assert rollout.legal.gather(1, rollout.action[:, None]).all()
    # what Python computes for the states is what Java computed when it chose: the probabilities, and the values
    with torch.no_grad():
        logits, values = net(rollout.obs, rollout.globals)
    from risk_learner.model import masked_log_probabilities
    log_probs = masked_log_probabilities(logits, rollout.legal).gather(1, rollout.action[:, None]).squeeze(1)
    torch.testing.assert_close(log_probs, rollout.log_prob, rtol=1e-3, atol=1e-3)
    torch.testing.assert_close(values, rollout.value, rtol=1e-3, atol=1e-3)
    # and it can learn from them
    before = {name: p.detach().clone() for name, p in net.p.items()}
    stats = Ppo(net, PpoConfig(lr=1e-3)).update(rollout)
    assert all(np.isfinite(v) for v in stats.values())
    assert any(not torch.equal(p.detach(), before[name]) for name, p in net.p.items())


def test_the_network_is_evaluated_against_greedy_bots(java_actor, tmp_path):
    network(java_actor, tmp_path).save(tmp_path / "w.weights")
    result = java_actor.evaluate(2, 4, tmp_path / "w.weights")
    assert result["games"] == 4 and 0 <= result["wins"] <= 4


def test_the_decisions_of_greedy_bots_can_be_learned_from(java_actor, tmp_path):
    net = network(java_actor, tmp_path)
    java_actor.imitation_data(2, 2, 1, tmp_path / "imitation.npz")
    data = load_imitation(tmp_path / "imitation.npz", action_space_size(42))
    assert len(data) > 100
    assert data.legal.gather(1, data.action[:, None]).all() and data.acceptable.gather(1, data.action[:, None]).all()
    assert evaluate(net, data)["REINFORCE"]["samples"] > 0


def test_the_opponents_can_be_chosen(java_actor, tmp_path):
    network(java_actor, tmp_path).save(tmp_path / "w.weights")
    for opponents in ("greedy", "cautious", "greedy,cautious"):
        result = java_actor.evaluate(2, 4, tmp_path / "w.weights", opponents=opponents)
        assert result["games"] == 4
    info = java_actor.rollout(2, 2, 3, tmp_path / "w.weights", tmp_path / "rollout.npz", bot_seats=1.0, opponents="cautious")
    assert info["games"] == 2 and info["steps"] > 20
