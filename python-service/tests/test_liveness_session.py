import time

from app.liveness.challenge import ALL_ACTIONS, generate_challenge
from app.liveness.session_store import create_session, get_session


def test_generate_challenge_returns_requested_count_of_unique_actions():
    actions = generate_challenge(3)
    assert len(actions) == 3
    assert len(set(actions)) == 3
    assert all(a in ALL_ACTIONS for a in actions)


def test_generate_challenge_caps_at_total_available_actions():
    actions = generate_challenge(100)
    assert len(actions) == len(ALL_ACTIONS)


def test_create_session_is_retrievable():
    session = create_session(["BLINK", "SMILE"], ttl_seconds=60)
    fetched = get_session(session.session_id)
    assert fetched is not None
    assert fetched.actions == ["BLINK", "SMILE"]
    assert fetched.current_action == "BLINK"
    assert fetched.all_completed is False


def test_session_progresses_through_actions_as_they_complete():
    session = create_session(["BLINK", "SMILE"], ttl_seconds=60)
    session.completed_actions.append("BLINK")
    assert session.current_action == "SMILE"
    session.completed_actions.append("SMILE")
    assert session.current_action is None
    assert session.all_completed is True


def test_session_expires_after_ttl():
    session = create_session(["BLINK"], ttl_seconds=0)
    time.sleep(0.01)
    assert session.expired is True


def test_get_session_returns_none_for_unknown_id():
    assert get_session("does-not-exist") is None
