from fastapi.testclient import TestClient

from app.config.settings import get_settings
from app.main import app

client = TestClient(app)


def test_analyze_rejects_missing_api_key(document_like_image, encode_jpeg):
    files = {"file": ("cni.jpg", encode_jpeg(document_like_image), "image/jpeg")}
    response = client.post("/api/v1/document/analyze", files=files)
    assert response.status_code == 422


def test_analyze_rejects_wrong_api_key(document_like_image, encode_jpeg):
    files = {"file": ("cni.jpg", encode_jpeg(document_like_image), "image/jpeg")}
    response = client.post(
        "/api/v1/document/analyze", files=files, headers={"X-Internal-Api-Key": "wrong-key"}
    )
    assert response.status_code == 401


def test_analyze_returns_quality_report_for_valid_request(document_like_image, encode_jpeg):
    settings = get_settings()
    files = {"file": ("cni.jpg", encode_jpeg(document_like_image), "image/jpeg")}
    response = client.post(
        "/api/v1/document/analyze",
        files=files,
        headers={"X-Internal-Api-Key": settings.internal_api_key},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["documentDetected"] is True
    assert 0 <= body["qualityScore"] <= 100
    assert "qualityDetails" in body
    assert body["side"] in {"FRONT", "BACK", "UNKNOWN"}


def test_analyze_rejects_empty_file():
    settings = get_settings()
    files = {"file": ("empty.jpg", b"", "image/jpeg")}
    response = client.post(
        "/api/v1/document/analyze",
        files=files,
        headers={"X-Internal-Api-Key": settings.internal_api_key},
    )
    assert response.status_code == 422
