from fastapi.testclient import TestClient

from app.config.settings import get_settings
from app.main import app

client = TestClient(app)
HEADERS = {"X-Internal-Api-Key": get_settings().internal_api_key}


def test_start_rejects_missing_api_key():
    response = client.post("/api/v1/liveness/challenge/start")
    assert response.status_code == 422


def test_start_returns_session_and_actions():
    response = client.post("/api/v1/liveness/challenge/start", headers=HEADERS)
    assert response.status_code == 200
    body = response.json()
    assert body["sessionId"]
    assert len(body["actions"]) == get_settings().liveness_challenge_action_count
    assert body["expiresInSeconds"] > 0


def test_verify_rejects_unknown_session(document_like_image, encode_jpeg):
    files = [("frames", ("f1.jpg", encode_jpeg(document_like_image), "image/jpeg"))]
    response = client.post(
        "/api/v1/liveness/challenge/verify",
        data={"session_id": "does-not-exist", "action": "BLINK"},
        files=files,
        headers=HEADERS,
    )
    assert response.status_code == 404


def test_verify_rejects_action_mismatch(document_like_image, encode_jpeg):
    start_response = client.post("/api/v1/liveness/challenge/start", headers=HEADERS)
    session_id = start_response.json()["sessionId"]
    first_action = start_response.json()["actions"][0]
    wrong_action = next(a for a in ["BLINK", "SMILE", "TURN_LEFT"] if a != first_action)

    files = [("frames", ("f1.jpg", encode_jpeg(document_like_image), "image/jpeg"))]
    response = client.post(
        "/api/v1/liveness/challenge/verify",
        data={"session_id": session_id, "action": wrong_action},
        files=files,
        headers=HEADERS,
    )
    assert response.status_code == 409


def test_verify_reports_not_completed_when_no_face_in_frames(document_like_image, encode_jpeg):
    start_response = client.post("/api/v1/liveness/challenge/start", headers=HEADERS)
    session_id = start_response.json()["sessionId"]
    first_action = start_response.json()["actions"][0]

    files = [
        ("frames", ("f1.jpg", encode_jpeg(document_like_image), "image/jpeg")),
        ("frames", ("f2.jpg", encode_jpeg(document_like_image), "image/jpeg")),
    ]
    response = client.post(
        "/api/v1/liveness/challenge/verify",
        data={"session_id": session_id, "action": first_action},
        files=files,
        headers=HEADERS,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["actionCompleted"] is False
    assert body["allActionsCompleted"] is False


def test_status_rejects_unknown_session():
    response = client.get("/api/v1/liveness/challenge/does-not-exist/status", headers=HEADERS)
    assert response.status_code == 404


def test_status_returns_in_progress_for_fresh_session():
    start_response = client.post("/api/v1/liveness/challenge/start", headers=HEADERS)
    session_id = start_response.json()["sessionId"]

    response = client.get(f"/api/v1/liveness/challenge/{session_id}/status", headers=HEADERS)
    assert response.status_code == 200
    body = response.json()
    assert body["decision"] == "IN_PROGRESS"
    assert body["allActionsCompleted"] is False
