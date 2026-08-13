from app.config.settings import Settings, get_settings
from app.liveness.landmarker import FrameAnalysis, analyze_frame
from app.liveness.session_store import get_session
from app.models.verification_models import LivenessBinding, VerificationCompareResponse
from app.utils.image_io import decode_image
from app.verification.alignment import align_face
from app.verification.comparison import cosine_similarity
from app.verification.embedding import extract_embedding
from app.verification.landmarks_5pt import extract_five_points


def compare_faces(
    source_bytes: bytes,
    target_bytes: bytes,
    settings: Settings | None = None,
    liveness_session_id: str | None = None,
) -> VerificationCompareResponse:
    """Compare le visage d'une photo de référence (ex. CNI) à celui d'une cible (ex. selfie).

    Quand `liveness_session_id` est fourni, la cible est en outre confrontée à l'empreinte du
    visage qui a joué le défi de vivacité. C'est ce rattachement qui empêche de jouer le défi
    avec son propre visage puis de soumettre le selfie de quelqu'un d'autre : les deux preuves
    portaient jusqu'ici sur des personnes qui n'avaient aucune obligation d'être la même.
    """
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
            liveness=_binding_absente("Aucun visage exploitable sur la cible")
            if liveness_session_id
            else None,
        )

    source_embedding = embed_face(source_bytes, source_frame)
    target_embedding = embed_face(target_bytes, target_frame)
    similarity = cosine_similarity(source_embedding, target_embedding)
    decision = "MATCH" if similarity >= settings.face_match_similarity_threshold else "NO_MATCH"

    liaison = (
        _lier_au_defi(liveness_session_id, target_embedding, settings) if liveness_session_id else None
    )

    # Fail-secure : une cible qui n'est pas le visage du défi ne peut pas valoir MATCH, quelle
    # que soit sa ressemblance avec la pièce d'identité. C'est précisément le scénario d'usurpation
    # que la liaison sert à détecter, il serait absurde de le détecter puis de l'accepter.
    if liaison is not None and liaison.samePerson is False:
        decision = "NO_MATCH"

    return VerificationCompareResponse(
        similarityScore=round(similarity, 4),
        decision=decision,
        threshold=settings.face_match_similarity_threshold,
        sourceFaceDetected=True,
        targetFaceDetected=True,
        liveness=liaison,
    )


def _lier_au_defi(session_id: str, target_embedding, settings: Settings) -> LivenessBinding:
    session = get_session(session_id)
    if session is None:
        return _binding_absente("Session de vivacité introuvable ou expirée")
    if session.embedding is None:
        return _binding_absente("Aucune empreinte mémorisée pour cette session de vivacité")

    similarity = cosine_similarity(session.embedding, target_embedding)
    seuil = settings.liveness_binding_similarity_threshold
    return LivenessBinding(
        bound=True,
        samePerson=similarity >= seuil,
        similarity=round(similarity, 4),
        threshold=seuil,
    )


def _binding_absente(raison: str) -> LivenessBinding:
    """Liaison impossible.

    On ne tranche pas ici : `bound=false` dit seulement que la preuve n'a pas pu être faite.
    C'est à l'appelant de décider ce qu'il fait d'un dossier non lié, selon sa politique
    (refus, revue conseiller). Répondre `samePerson=true` par défaut reviendrait à transformer
    une absence de preuve en preuve.
    """
    return LivenessBinding(bound=False, samePerson=None, similarity=None, reason=raison)


def embed_face(raw_bytes: bytes, frame: FrameAnalysis):
    """Empreinte ArcFace d'un visage déjà localisé dans `frame`.

    Publique parce que le service de vivacité s'en sert pour mémoriser le visage du défi :
    les deux chemins doivent produire des empreintes strictement comparables, ce qui ne serait
    pas garanti avec deux pipelines d'alignement distincts.
    """
    image = decode_image(raw_bytes)
    five_points = extract_five_points(frame)
    aligned = align_face(image, five_points)
    return extract_embedding(aligned)
