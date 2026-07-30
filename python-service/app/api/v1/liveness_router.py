from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status

from app.api.deps import verify_internal_api_key
from app.liveness.errors import ActionMismatchError, SessionExpiredError, SessionNotFoundError
from app.models.liveness_models import ChallengeStartResponse, ChallengeStatusResponse, ChallengeVerifyResponse
from app.services.liveness_service import get_status, start_challenge, verify_challenge
from app.utils.image_io import InvalidImageError

router = APIRouter(prefix="/liveness", tags=["liveness"], dependencies=[Depends(verify_internal_api_key)])


@router.post("/challenge/start", response_model=ChallengeStartResponse)
def start() -> ChallengeStartResponse:
    return start_challenge()


@router.post("/challenge/verify", response_model=ChallengeVerifyResponse)
async def verify(
    session_id: str = Form(...),
    action: str = Form(...),
    frames: list[UploadFile] = File(...),
) -> ChallengeVerifyResponse:
    frames_bytes = [await frame.read() for frame in frames]
    try:
        return verify_challenge(session_id, action, frames_bytes)
    except SessionNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except SessionExpiredError as exc:
        raise HTTPException(status_code=status.HTTP_410_GONE, detail=str(exc)) from exc
    except ActionMismatchError as exc:
        raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail=str(exc)) from exc
    except InvalidImageError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc)) from exc


@router.get("/challenge/{session_id}/status", response_model=ChallengeStatusResponse)
def status_endpoint(session_id: str) -> ChallengeStatusResponse:
    try:
        return get_status(session_id)
    except SessionNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=str(exc)) from exc
    except SessionExpiredError as exc:
        raise HTTPException(status_code=status.HTTP_410_GONE, detail=str(exc)) from exc
