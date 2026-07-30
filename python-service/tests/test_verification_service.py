from app.services.verification_service import compare_faces


def test_same_photo_compared_to_itself_is_a_match(sample_portrait_bytes):
    response = compare_faces(sample_portrait_bytes, sample_portrait_bytes)

    assert response.sourceFaceDetected is True
    assert response.targetFaceDetected is True
    assert response.decision == "MATCH"
    assert response.similarityScore > 0.9  # même image des deux côtés : quasi-identique


def test_missing_face_on_one_side_reports_no_match(sample_portrait_bytes, document_like_image, encode_jpeg):
    response = compare_faces(sample_portrait_bytes, encode_jpeg(document_like_image))

    assert response.sourceFaceDetected is True
    assert response.targetFaceDetected is False
    assert response.decision == "NO_MATCH"
    assert response.similarityScore == 0.0
