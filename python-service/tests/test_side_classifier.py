from app.models.document_models import DocumentSide
from app.vision.side_classifier import classify_side


def test_classifies_as_back_when_no_face_present(document_like_image):
    # Un simple rectangle ne contient aucun visage détectable par MediaPipe.
    assert classify_side(document_like_image) == DocumentSide.BACK
