from fastapi import APIRouter, Depends, HTTPException, UploadFile, status

from app.api.deps import verify_internal_api_key
from app.models.document_models import DocumentAnalyzeResponse
from app.models.ocr_models import DocumentExtractResponse
from app.services.document_service import analyze_document
from app.services.ocr_service import extract_document_fields
from app.utils.image_io import InvalidImageError

router = APIRouter(prefix="/document", tags=["document"], dependencies=[Depends(verify_internal_api_key)])


@router.post("/analyze", response_model=DocumentAnalyzeResponse)
async def analyze(file: UploadFile) -> DocumentAnalyzeResponse:
    raw_bytes = await file.read()
    try:
        return analyze_document(raw_bytes)
    except InvalidImageError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc


@router.post("/extract", response_model=DocumentExtractResponse)
async def extract(front: UploadFile, back: UploadFile | None = None) -> DocumentExtractResponse:
    front_bytes = await front.read()
    back_bytes = await back.read() if back else None
    try:
        return extract_document_fields(front_bytes, back_bytes)
    except InvalidImageError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc
