from pydantic import BaseModel, Field


class VerificationCompareResponse(BaseModel):
    similarityScore: float = Field(..., description="Similarité cosinus entre les deux empreintes (-1 à 1)")
    decision: str = Field(..., description="MATCH | NO_MATCH")
    threshold: float
    sourceFaceDetected: bool
    targetFaceDetected: bool
