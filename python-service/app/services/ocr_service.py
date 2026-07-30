from app.config.settings import Settings, get_settings
from app.models.ocr_models import DocumentExtractResponse, ExtractedFields
from app.ocr.cni_parser import parse_cni_fields
from app.ocr.document_kind import DocumentKind, detect_document_kind
from app.ocr.engine import extract_text, extract_words
from app.ocr.preprocessing import preprocess_for_ocr
from app.ocr.provisional_receipt_parser import parse_provisional_receipt_fields
from app.ocr.receipt_parser import parse_receipt_fields
from app.utils.image_io import decode_image


def extract_document_fields(
    front_bytes: bytes, back_bytes: bytes | None = None, settings: Settings | None = None
) -> DocumentExtractResponse:
    settings = settings or get_settings()

    front_image = preprocess_for_ocr(decode_image(front_bytes))
    front_text = extract_text(front_image, settings)
    all_words = extract_words(front_image, settings)
    combined_text = front_text

    if back_bytes:
        back_image = preprocess_for_ocr(decode_image(back_bytes))
        back_text = extract_text(back_image, settings)
        all_words.extend(extract_words(back_image, settings))
        combined_text = f"{front_text}\n{back_text}"

    average_confidence = sum(w.confidence for w in all_words) / len(all_words) if all_words else 0.0
    document_kind = detect_document_kind(combined_text)

    fields = _parse_by_kind(document_kind, combined_text)
    issues = _collect_issues(document_kind, fields, average_confidence, settings)

    return DocumentExtractResponse(
        fields=fields,
        rawText=combined_text,
        averageConfidence=round(average_confidence, 2),
        issues=issues,
    )


def _parse_by_kind(document_kind: DocumentKind, combined_text: str) -> ExtractedFields:
    if document_kind == DocumentKind.TITRE_PROVISOIRE:
        parsed = parse_provisional_receipt_fields(combined_text)
        return ExtractedFields(
            documentKind=document_kind.value,
            lastName=parsed.lastName,
            firstName=parsed.firstName,
            birthDate=parsed.birthDate,
            expiryDate=parsed.expiryDate,
            birthPlace=parsed.birthPlace,
            fatherName=parsed.fatherName,
            motherName=parsed.motherName,
            profession=parsed.profession,
            kitNumber=parsed.kitNumber,
            requestIdentifier=parsed.requestIdentifier,
        )

    if document_kind == DocumentKind.RECEPISSE:
        parsed = parse_receipt_fields(combined_text)
        return ExtractedFields(
            documentKind=document_kind.value,
            lastName=parsed.lastName,
            firstName=parsed.firstName,
            kitNumber=parsed.kitNumber,
            requestIdentifier=parsed.requestIdentifier,
            paymentAmount=parsed.paymentAmount,
            paymentDate=parsed.paymentDate,
        )

    # CNI définitive par défaut si le type n'est pas clairement identifié : c'est le document
    # cible principal, et le parseur CNI reste inoffensif (retourne des champs vides) sur un
    # texte qui ne lui correspond pas.
    parsed = parse_cni_fields(combined_text)
    return ExtractedFields(
        documentKind=document_kind.value,
        lastName=parsed.lastName,
        firstName=parsed.firstName,
        documentNumber=parsed.documentNumber,
        sex=parsed.sex,
        birthDate=parsed.birthDate,
        expiryDate=parsed.expiryDate,
        birthPlace=parsed.birthPlace,
    )


def _collect_issues(
    document_kind: DocumentKind, fields: ExtractedFields, average_confidence: float, settings: Settings
) -> list[str]:
    issues: list[str] = []

    if document_kind == DocumentKind.UNKNOWN:
        issues.append("Type de document non reconnu (ni CNI, ni titre d'identité provisoire, ni récépissé)")

    if not fields.lastName:
        issues.append("Nom non détecté")
    if not fields.firstName:
        issues.append("Prénom non détecté")

    if document_kind == DocumentKind.RECEPISSE:
        # Un récépissé de paiement n'a ni date de naissance ni numéro de document : seuls
        # l'identifiant de demande et la date de paiement lui sont propres.
        if not fields.requestIdentifier:
            issues.append("Identifiant de demande non détecté")
        if not fields.paymentDate:
            issues.append("Date de paiement non détectée")
    elif document_kind == DocumentKind.TITRE_PROVISOIRE:
        if not fields.birthDate:
            issues.append("Date de naissance non détectée")
        if not fields.requestIdentifier:
            issues.append("Identifiant de demande non détecté")
    else:
        if not fields.birthDate:
            issues.append("Date de naissance non détectée")
        if not fields.documentNumber:
            issues.append("Numéro de document non détecté")

    if average_confidence < settings.min_ocr_confidence:
        issues.append(f"Confiance OCR faible ({round(average_confidence)}%) : reprenez des photos plus nettes")

    return issues
