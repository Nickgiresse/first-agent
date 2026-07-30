from app.config.settings import Settings, get_settings
from app.liveness.landmarker import FrameAnalysis, analyze_frame
from app.models.verification_models import VerificationCompareResponse
from app.utils.image_io import decode_image
from app.verification.alignment import align_face
from app.verification.comparison import cosine_similarity
from app.verification.embedding import extract_embedding
from app.verification.landmarks_5pt import extract_five_points


def compare_faces(
    source_bytes: bytes, target_bytes: bytes, settings: Settings | None = None
) -> VerificationCompareResponse:
    """Compare le visage d'une photo de référence (ex. CNI) à celui d'une cible (ex. selfie ou
    meilleure frame de la session de vivacité)."""
    settings = settings or get_settings()

    source_frame = analyze_frame(decode_image(source_bytes))
    target_frame = analyze_frame(decode_image(target_bytes))

    if not source_frame.faceDetected or not target_frame.faceDetected:
        return VerificationCompareResponse(
            similarityScore=0.0,
            decision="NO_MATCH",
            threshold=settings.face_match_similarity_threshold,
            sourceFaceDetected=source_frame.faceDetected,
            targetFaceDetected=target_frame.faceDetected,
        )

    source_embedding = _embed(source_bytes, source_frame)
    target_embedding = _embed(target_bytes, target_frame)
    similarity = cosine_similarity(source_embedding, target_embedding)
    decision = "MATCH" if similarity >= settings.face_match_similarity_threshold else "NO_MATCH"

    return VerificationCompareResponse(
        similarityScore=round(similarity, 4),
        decision=decision,
        threshold=settings.face_match_similarity_threshold,
        sourceFaceDetected=True,
        targetFaceDetected=True,
    )


def _embed(raw_bytes: bytes, frame: FrameAnalysis):
    image = decode_image(raw_bytes)
    five_points = extract_five_points(frame)
    aligned = align_face(image, five_points)
    return extract_embedding(aligned)
