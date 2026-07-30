from pydantic import BaseModel, Field

from app.models.document_models import Resolution


class FaceQualityDetails(BaseModel):
    blurScore: float
    brightness: float
    glareDetected: bool
    glareRatio: float
    resolution: Resolution
    faceAreaRatio: float = Field(..., description="Proportion du cadre occupée par le visage (0-1)")
    offsetX: float = Field(..., description="Écart horizontal au centre de l'image (-0.5 à 0.5)")
    offsetY: float = Field(..., description="Écart vertical au centre de l'image (-0.5 à 0.5)")


class FaceAnalyzeResponse(BaseModel):
    faceDetected: bool
    faceCount: int
    singleFace: bool
    centered: bool
    eyesDetected: bool
    noseDetected: bool
    mouthDetected: bool
    qualityScore: int = Field(..., ge=0, le=100)
    qualityDetails: FaceQualityDetails
    issues: list[str] = Field(default_factory=list)
