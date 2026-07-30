from fastapi import APIRouter, Depends, HTTPException, UploadFile, status

from app.api.deps import verify_internal_api_key
from app.models.face_models import FaceAnalyzeResponse
from app.services.face_service import analyze_face_image
from app.utils.image_io import InvalidImageError

router = APIRouter(prefix="/face", tags=["face"], dependencies=[Depends(verify_internal_api_key)])


@router.post("/analyze", response_model=FaceAnalyzeResponse)
async def analyze(file: UploadFile) -> FaceAnalyzeResponse:
    raw_bytes = await file.read()
    try:
        return analyze_face_image(raw_bytes)
    except InvalidImageError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
