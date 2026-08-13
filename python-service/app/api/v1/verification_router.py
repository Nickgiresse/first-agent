from fastapi import APIRouter, Depends, Form, HTTPException, UploadFile, status

from app.api.deps import verify_internal_api_key
from app.models.verification_models import VerificationCompareResponse
from app.services.verification_service import compare_faces
from app.utils.image_io import InvalidImageError

router = APIRouter(prefix="/verification", tags=["verification"], dependencies=[Depends(verify_internal_api_key)])


@router.post("/compare", response_model=VerificationCompareResponse)
async def compare(
    source: UploadFile,
    target: UploadFile,
    liveness_session_id: str | None = Form(None),
) -> VerificationCompareResponse:
    """Compare deux visages, et rattache éventuellement la cible au défi de vivacité.

    `liveness_session_id` est optionnel pour ne pas casser les appelants existants, mais son
    absence prive la réponse du bloc `liveness` : l'appelant ne peut alors PAS conclure que le
    selfie est bien celui de la personne qui a joué le défi.
    """
    source_bytes = await source.read()
    target_bytes = await target.read()
    try:
        return compare_faces(source_bytes, target_bytes, liveness_session_id=liveness_session_id)
    except InvalidImageError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
