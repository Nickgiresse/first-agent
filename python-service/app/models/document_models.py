from enum import Enum

from pydantic import BaseModel, Field


class DocumentSide(str, Enum):
    FRONT = "FRONT"
    BACK = "BACK"
    UNKNOWN = "UNKNOWN"


class Resolution(BaseModel):
    width: int
    height: int


class QualityDetails(BaseModel):
    blurScore: float = Field(..., description="Variance du Laplacien ; plus haut = plus net")
    brightness: float = Field(..., description="Luminosité moyenne (0-255)")
    glareDetected: bool
    glareRatio: float = Field(..., description="Proportion de pixels saturés (reflet)")
    resolution: Resolution
    fullyVisible: bool
    documentAreaRatio: float = Field(..., description="Proportion du cadre occupée par le document (0-1)")


class DocumentAnalyzeResponse(BaseModel):
    documentDetected: bool
    documentType: str | None = None
    typeConfirmed: bool = Field(
        False, description="True uniquement si confirmé par OCR (Module 2, pas encore intégré)"
    )
    side: DocumentSide = DocumentSide.UNKNOWN
    qualityScore: int = Field(..., ge=0, le=100)
    qualityDetails: QualityDetails
    issues: list[str] = Field(default_factory=list)
