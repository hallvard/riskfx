import numpy as np
import torch

from risk_learner.data import KINDS, ImitationData, Rollout, legal_mask
from risk_learner.imitation import evaluate, train_epoch
from risk_learner.model import PolicyValueNet, action_space_size, masked_log_probabilities
from risk_learner.ppo import Ppo, PpoConfig
from risk_learner.weights import Config


def small_net(borders, seed=1) -> PolicyValueNet:
    return PolicyValueNet(Config(13, 14, hidden=16, layers=2, attention=8), borders, seed)


def test_the_legal_actions_are_made_a_matrix_of_the_legal():
    mask = legal_mask(np.array([0, 2, 3, 6]), np.array([1, 4, 2, 0, 3, 4]), 5)
    assert mask.tolist() == [[False, True, False, False, True], [False, False, True, False, False], [True, False, False, True, True]]


def toy_rollout(net, steps=128, generator=None) -> Rollout:
    """The same state many times, with three legal actions, where the first is good and the others bad."""
    torch.manual_seed(0)
    obs = torch.rand(1, 42, 13).repeat(steps, 1, 1)
    globals_ = torch.rand(1, 14).repeat(steps, 1)
    legal = torch.zeros(steps, action_space_size(42), dtype=torch.bool)
    legal[:, [11, 12, 13]] = True
    with torch.no_grad():
        logits, values = net(obs, globals_)
        log_probs = masked_log_probabilities(logits, legal)
    # sampled with the probabilities the policy has now, like the actor does
    chosen = torch.multinomial(log_probs.exp(), 1, generator=generator).squeeze(1)
    advantage = torch.where(chosen == 11, 1.0, -1.0)
    return Rollout(obs=obs, globals=globals_, legal=legal, action=chosen, log_prob=log_probs.gather(1, chosen[:, None]).squeeze(1),
                   value=values, advantage=advantage, value_target=torch.full((steps,), 0.7), reward=torch.zeros(steps),
                   stats={})


def probability_of_good_action(net, rollout) -> float:
    with torch.no_grad():
        logits, _ = net(rollout.obs[:1], rollout.globals[:1])
        return masked_log_probabilities(logits, rollout.legal[:1]).exp()[0, 11].item()


def test_ppo_learns_to_prefer_the_good_action_and_to_estimate_the_value(borders):
    net = small_net(borders)
    ppo = Ppo(net, PpoConfig(lr=1e-3, epochs=4, minibatch=64, entropy_coef=0.0))
    generator = torch.Generator().manual_seed(2)
    before = probability_of_good_action(net, toy_rollout(net, generator=generator))
    for _ in range(40):
        stats = ppo.update(toy_rollout(net, generator=generator))
        assert all(np.isfinite(value) for value in stats.values())
    rollout = toy_rollout(net, generator=generator)
    assert probability_of_good_action(net, rollout) > 0.9, (before, probability_of_good_action(net, rollout))
    with torch.no_grad():
        _, values = net(rollout.obs[:1], rollout.globals[:1])
    assert abs(values.item() - 0.7) < 0.1


def test_one_update_changes_the_probabilities_only_a_little(borders):
    net = small_net(borders)
    ppo = Ppo(net, PpoConfig(lr=1e-3, epochs=4, minibatch=64, entropy_coef=0.0))
    rollout = toy_rollout(net, generator=torch.Generator().manual_seed(3))
    before = probability_of_good_action(net, rollout)
    ppo.update(rollout)
    after = probability_of_good_action(net, rollout)
    assert before < after < before * 1.5


def test_an_action_that_was_almost_impossible_when_chosen_does_not_break_the_update(borders):
    net = small_net(borders)
    ppo = Ppo(net, PpoConfig(lr=1e-3, epochs=4, minibatch=64, entropy_coef=0.0))
    rollout = toy_rollout(net, steps=32, generator=torch.Generator().manual_seed(4))
    rollout.log_prob = torch.full_like(rollout.log_prob, -200.0)
    rollout.advantage = torch.where(torch.arange(32) % 2 == 0, -1.0, 1.0)
    stats = ppo.update(rollout)
    assert all(np.isfinite(value) for value in stats.values())
    assert ppo.skipped_steps == 0


def test_a_step_with_a_gradient_that_is_not_finite_is_skipped_and_the_next_is_not_poisoned(borders):
    net = small_net(borders)
    ppo = Ppo(net, PpoConfig(lr=1e-3, epochs=1, minibatch=64, entropy_coef=0.0))
    rollout = toy_rollout(net, steps=64, generator=torch.Generator().manual_seed(5))
    before = {name: p.detach().clone() for name, p in net.p.items()}
    rollout.value_target = torch.full((64,), float("nan"))
    ppo.update(rollout)
    assert ppo.skipped_steps == 1
    for name, p in net.p.items():
        torch.testing.assert_close(p.detach(), before[name])
    # with a good rollout, the network learns again (the gradients are cleared at each step)
    rollout.value_target = torch.full((64,), 0.7)
    ppo.update(rollout)
    assert any(not torch.equal(p.detach(), before[name]) for name, p in net.p.items())
    assert ppo.skipped_steps == 1


def imitation_data(samples=64) -> ImitationData:
    generator = torch.Generator().manual_seed(7)
    actions = action_space_size(42)
    legal = torch.zeros(samples, actions, dtype=torch.bool)
    chosen = torch.zeros(samples, dtype=torch.int64)
    for i in range(samples):
        options = torch.randperm(actions, generator=generator)[:5]
        legal[i, options] = True
        chosen[i] = options[i % 5]
    acceptable = torch.zeros_like(legal)
    acceptable[torch.arange(samples), chosen] = True
    return ImitationData(obs=torch.rand(samples, 42, 13, generator=generator), globals=torch.rand(samples, 14, generator=generator),
                         legal=legal, acceptable=acceptable, action=chosen, kind=torch.arange(samples) % len(KINDS),
                         value_target=torch.rand(samples, generator=generator))


def test_imitation_learns_the_choices_it_is_shown(borders):
    net = small_net(borders)
    data = imitation_data()
    optimizer = torch.optim.Adam(net.parameters(), lr=3e-3)
    generator = torch.Generator().manual_seed(1)
    first = train_epoch(net, optimizer, data, batch_size=32, generator=generator)
    for _ in range(80):
        last = train_epoch(net, optimizer, data, batch_size=32, generator=generator)
    assert last[0] < first[0] * 0.3 and last[1] < first[1] * 0.5
    report = evaluate(net, data)
    assert all(report[kind]["acceptable"] > 0.9 for kind in KINDS), report
    assert report["value_explained"] > 0
