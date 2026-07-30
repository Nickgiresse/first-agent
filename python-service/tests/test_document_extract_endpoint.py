from fastapi.testclient import TestClient

from app.config.settings import get_settings
from app.main import app

client = TestClient(app)


def test_extract_rejects_missing_api_key(text_image_factory, encode_jpeg):
    files = {"front": ("cni.jpg", encode_jpeg(text_image_factory("NOM/NAME NKENG")), "image/jpeg")}
    response = client.post("/api/v1/document/extract", files=files)
    assert response.status_code == 422


def test_extract_rejects_wrong_api_key(text_image_factory, encode_jpeg):
    files = {"front": ("cni.jpg", encode_jpeg(text_image_factory("NOM/NAME NKENG")), "image/jpeg")}
    response = client.post(
        "/api/v1/document/extract", files=files, headers={"X-Internal-Api-Key": "wrong-key"}
    )
    assert response.status_code == 401


def test_extract_returns_structured_fields_for_valid_request(text_image_factory, encode_jpeg):
    settings = get_settings()
    files = {"front": ("cni.jpg", encode_jpeg(text_image_factory("NOM/NAME NKENG")), "image/jpeg")}
    response = client.post(
        "/api/v1/document/extract",
        files=files,
        headers={"X-Internal-Api-Key": settings.internal_api_key},
    )

    assert response.status_code == 200
    body = response.json()
    assert "fields" in body
    assert "rawText" in body
    assert 0 <= body["averageConfidence"] <= 100
    assert isinstance(body["issues"], list)


def test_extract_accepts_optional_back_image(text_image_factory, encode_jpeg):
    settings = get_settings()
    files = {
        "front": ("front.jpg", encode_jpeg(text_image_factory("NOM/NAME NKENG")), "image/jpeg"),
        "back": ("back.jpg", encode_jpeg(text_image_factory("LIEU DE NAISSANCE YAOUNDE")), "image/jpeg"),
    }
    response = client.post(
        "/api/v1/document/extract",
        files=files,
        headers={"X-Internal-Api-Key": settings.internal_api_key},
    )

    assert response.status_code == 200
