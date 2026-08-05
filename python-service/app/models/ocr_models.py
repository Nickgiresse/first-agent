from datetime import date

from pydantic import BaseModel, Field


class ExtractedFields(BaseModel):
    documentKind: str = Field("UNKNOWN", description="CNI | TITRE_PROVISOIRE | RECEPISSE | PASSEPORT | UNKNOWN")
    lastName: str | None = None
    firstName: str | None = None
    documentNumber: str | None = None  # numéro CNI (carte définitive) ou numéro de passeport
    sex: str | None = None
    birthDate: date | None = None
    expiryDate: date | None = None  # date d'expiration CNI/passeport, ou fin de validité du titre provisoire
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

    # Passeport uniquement (extrait de la MRZ, voir passport_parser.py) :
    nationality: str | None = None


class DocumentExtractResponse(BaseModel):
    fields: ExtractedFields
    rawText: str = Field(..., description="Texte brut concaténé recto+verso, pour audit/debug")
    averageConfidence: float = Field(..., ge=0, le=100)
    issues: list[str] = Field(default_factory=list)
