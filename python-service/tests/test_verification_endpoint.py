from fastapi.testclient import TestClient

from app.config.settings import get_settings
from app.main import app

client = TestClient(app)
HEADERS = {"X-Internal-Api-Key": get_settings().internal_api_key}


def test_compare_rejects_missing_api_key(sample_portrait_bytes):
    files = {
        "source": ("a.jpg", sample_portrait_bytes, "image/jpeg"),
        "target": ("b.jpg", sample_portrait_bytes, "image/jpeg"),
    }
    response = client.post("/api/v1/verification/compare", files=files)
    assert response.status_code == 422


def test_compare_rejects_wrong_api_key(sample_portrait_bytes):
    files = {
        "source": ("a.jpg", sample_portrait_bytes, "image/jpeg"),
        "target": ("b.jpg", sample_portrait_bytes, "image/jpeg"),
    }
    response = client.post(
        "/api/v1/verification/compare", files=files, headers={"X-Internal-Api-Key": "wrong-key"}
    )
    assert response.status_code == 401


def test_compare_same_photo_returns_match(sample_portrait_bytes):
    files = {
        "source": ("a.jpg", sample_portrait_bytes, "image/jpeg"),
        "target": ("b.jpg", sample_portrait_bytes, "image/jpeg"),
    }
    response = client.post("/api/v1/verification/compare", files=files, headers=HEADERS)

    assert response.status_code == 200
    body = response.json()
    assert body["decision"] == "MATCH"
    assert body["similarityScore"] > 0.9


def test_compare_rejects_empty_file(sample_portrait_bytes):
    files = {
        "source": ("a.jpg", b"", "image/jpeg"),
        "target": ("b.jpg", sample_portrait_bytes, "image/jpeg"),
    }
    response = client.post("/api/v1/verification/compare", files=files, headers=HEADERS)
    assert response.status_code == 422
