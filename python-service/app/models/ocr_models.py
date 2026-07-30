from datetime import date

from pydantic import BaseModel, Field


class ExtractedFields(BaseModel):
    documentKind: str = Field("UNKNOWN", description="CNI | TITRE_PROVISOIRE | RECEPISSE | UNKNOWN")
    lastName: str | None = None
    firstName: str | None = None
    documentNumber: str | None = None  # numéro CNI (carte définitive)
    sex: str | None = None
    birthDate: date | None = None
    expiryDate: date | None = None  # date d'expiration CNI, ou fin de validité du titre provisoire
    birthPlace: str | None = None

    # Titre d'identité provisoire uniquement :
    fatherName: str | None = None
    motherName: str | None = None
    profession: str | None = None
    kitNumber: str | None = None
    requestIdentifier: str | None = None

    # Récépissé de paiement uniquement :
    paymentAmount: str | None = None
    paymentDate: date | None = None


class DocumentExtractResponse(BaseModel):
    fields: ExtractedFields
    rawText: str = Field(..., description="Texte brut concaténé recto+verso, pour audit/debug")
    averageConfidence: float = Field(..., ge=0, le=100)
    issues: list[str] = Field(default_factory=list)
