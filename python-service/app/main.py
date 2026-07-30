from fastapi import FastAPI

from app.api.v1.document_router import router as document_router
from app.api.v1.face_router import router as face_router
from app.api.v1.liveness_router import router as liveness_router
from app.api.v1.verification_router import router as verification_router
from app.config.settings import get_settings

settings = get_settings()

app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    description="Microservice interne de vision par ordinateur (analyse documentaire, OCR, "
    "reconnaissance faciale, vivacité, comparaison biométrique). Appelé exclusivement par Spring Boot.",
)

app.include_router(document_router, prefix="/api/v1")
app.include_router(face_router, prefix="/api/v1")
app.include_router(liveness_router, prefix="/api/v1")
app.include_router(verification_router, prefix="/api/v1")


@app.get("/health", tags=["health"])
def health() -> dict[str, str]:
    return {"status": "ok"}
