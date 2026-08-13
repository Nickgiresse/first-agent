from pydantic import BaseModel, Field


class LivenessBinding(BaseModel):
    """Rattachement de la comparaison au visage qui a réellement joué le défi.

    Sans ce bloc, « le défi de vivacité a réussi » et « le selfie correspond à la
    pièce » sont deux faits établis séparément, qui peuvent parfaitement concerner
    deux personnes différentes.
    """

    bound: bool = Field(
        ..., description="Une empreinte du visage du défi était disponible pour la comparaison"
    )
    samePerson: bool | None = Field(
        None, description="La cible est-elle le visage du défi ? None si bound=false"
    )
    similarity: float | None = Field(
        None, description="Similarité cosinus entre la cible et le visage du défi"
    )
    threshold: float | None = None
    reason: str | None = Field(
        None, description="Pourquoi la liaison n'a pas pu être établie, le cas échéant"
    )


class VerificationCompareResponse(BaseModel):
    similarityScore: float = Field(..., description="Similarité cosinus entre les deux empreintes (-1 à 1)")
    decision: str = Field(..., description="MATCH | NO_MATCH")
    threshold: float
    sourceFaceDetected: bool
    targetFaceDetected: bool
    liveness: LivenessBinding | None = Field(
        None, description="Présent uniquement si un identifiant de session de vivacité a été fourni"
    )
