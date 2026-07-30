from fastapi import APIRouter, Depends, HTTPException, UploadFile, status

from app.api.deps import verify_internal_api_key
from app.models.verification_models import VerificationCompareResponse
from app.services.verification_service import compare_faces
from app.utils.image_io import InvalidImageError

router = APIRouter(prefix="/verification", tags=["verification"], dependencies=[Depends(verify_internal_api_key)])


@router.post("/compare", response_model=VerificationCompareResponse)
async def compare(source: UploadFile, target: UploadFile) -> VerificationCompareResponse:
    source_bytes = await source.read()
    target_bytes = await target.read()
    try:
        return compare_faces(source_bytes, target_bytes)
    except InvalidImageError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
