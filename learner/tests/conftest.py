import json
from pathlib import Path

import numpy as np
import pytest

LEARNER = Path(__file__).resolve().parents[1]
GOLDEN = LEARNER.parent / "core" / "src" / "test" / "resources" / "golden"
CLASSPATH_FILE = LEARNER / "work" / "classpath.txt"


@pytest.fixture(scope="session")
def golden():
    return json.loads((GOLDEN / "golden.json").read_text())


@pytest.fixture(scope="session")
def borders(golden):
    return np.array(golden["borders"], dtype=np.int32)


@pytest.fixture(scope="session")
def java_actor():
    """The Java actor, if the Java side has been built with learner/prepare.sh."""
    if not CLASSPATH_FILE.exists():
        pytest.skip("run learner/prepare.sh first, to build the Java actor")
    from risk_learner.actor import Actor
    with Actor(Actor.classpath_from_file(CLASSPATH_FILE)) as actor:
        yield actor
