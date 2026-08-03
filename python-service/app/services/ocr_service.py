from app.config.settings import Settings, get_settings
from app.models.ocr_models import DocumentExtractResponse, ExtractedFields
from app.ocr.cni_parser import parse_cni_fields
from app.ocr.cni_quality_gate import verify_cni_quality
from app.ocr.document_kind import DocumentKind, detect_document_kind
from app.ocr.engine import OcrWord, extract_text, extract_words
from app.ocr.passport_parser import parse_passport_fields
from app.ocr.preprocessing import DEFAULT_PROFILE, PALE_DOCUMENT_PROFILE, PreprocessingProfile, preprocess_for_ocr
from app.ocr.provisional_receipt_parser import parse_provisional_receipt_fields
from app.ocr.receipt_parser import parse_receipt_fields
from app.ocr.validation import validate_mrz_check_digits
from app.utils.image_io import decode_image

# Reçus de paiement / titres provisoires : documents scannés/pâles (voir preprocessing.py) pour
# lesquels la binarisation adaptative aide généralement. Tous les autres types utilisent le profil
# par défaut (CNI, passeport, type encore indéterminé au moment du premier passage).
_PALE_DOCUMENT_KINDS = (DocumentKind.RECEPISSE, DocumentKind.TITRE_PROVISOIRE)


def _lire_au_mieux(image_bgr, settings: Settings):
    """Lit une face en essayant les deux préparations, et garde la meilleure.

    Le prétraitement est calibré pour Tesseract : niveaux de gris, fond aplati,
    agrandissement. Mesuré sur trois CNI réelles avec RapidOCR, il aide sur
    deux d'entre elles (636 caractères contre 601) et nuit franchement sur la
    troisième (408 contre 594, soit 3 champs extraits au lieu de 6). Aucune
    des deux préparations ne l'emporte partout, et rien dans l'image ne permet
    de deviner à l'avance laquelle conviendra : on lit donc les deux et on
    retient la plus riche.

    Le volume de texte sert d'arbitre. C'est grossier mais fiable ici : les
    échecs observés sont massifs, une préparation inadaptée fait perdre un
    tiers du texte, pas quelques caractères.
    """
    meilleur_texte, meilleurs_mots = "", []
    for image in (preprocess_for_ocr(image_bgr), image_bgr):
        texte = extract_text(image, settings)
        if len(texte) > len(meilleur_texte):
            meilleur_texte = texte
            meilleurs_mots = extract_words(image, settings)
    return meilleur_texte, meilleurs_mots


def extract_document_fields(
    front_bytes: bytes, back_bytes: bytes | None = None, settings: Settings | None = None
) -> DocumentExtractResponse:
    settings = settings or get_settings()

    # 1. Quality Gate : vérification préalable de la qualité physique de la photo (flou, luminosité,
    # reflet, résolution), avant même de tenter une classification. Le type reste "UNKNOWN" ici
    # plutôt qu'un type deviné (ex. CNI) : la qualité de l'image ne dit rien sur le type réel du
    # document, et la mission interdit explicitement de deviner en silence (Étape 6/7).
    is_quality_valid, quality_issues = verify_cni_quality(front_bytes, back_bytes, settings)
    if not is_quality_valid:
        return DocumentExtractResponse(
            fields=ExtractedFields(documentKind=DocumentKind.UNKNOWN.value),
            rawText="",
            averageConfidence=0.0,
            issues=quality_issues,
        )

    front_image_raw = decode_image(front_bytes)
    back_image_raw = decode_image(back_bytes) if back_bytes else None

    # Étape 3 (classification) : un premier passage OCR avec le profil par défaut suffit à
    # déterminer le type de document. Pour CNI/passeport, ce même passage sert directement de
    # passage d'extraction final ; seuls les documents "pâles"
    # (reçu/provisoire) déclenchent un second passage avec un profil de prétraitement dédié.
    combined_text, all_words = _run_ocr(front_image_raw, back_image_raw, DEFAULT_PROFILE, settings)
    document_kind = detect_document_kind(combined_text)

    if document_kind in _PALE_DOCUMENT_KINDS:
        combined_text, all_words = _run_ocr(front_image_raw, back_image_raw, PALE_DOCUMENT_PROFILE, settings)

    average_confidence = sum(w.confidence for w in all_words) / len(all_words) if all_words else 0.0

    fields, kind_specific_issues = _parse_by_kind(document_kind, combined_text)
    issues = quality_issues + kind_specific_issues + _collect_issues(document_kind, fields, average_confidence, settings)

    return DocumentExtractResponse(
        fields=fields,
        rawText=combined_text,
        averageConfidence=round(average_confidence, 2),
        issues=issues,
    )


def _run_ocr(
    front_image_raw, back_image_raw, profile: PreprocessingProfile, settings: Settings
) -> tuple[str, list[OcrWord]]:
    front_image = preprocess_for_ocr(front_image_raw, profile)
    front_text = extract_text(front_image, settings)
    all_words = extract_words(front_image, settings)
    combined_text = front_text

    if back_image_raw is not None:
        back_image = preprocess_for_ocr(back_image_raw, profile)
        back_text = extract_text(back_image, settings)
        all_words.extend(extract_words(back_image, settings))
        combined_text = f"{front_text}\n{back_text}"

    return combined_text, all_words


def _parse_by_kind(document_kind: DocumentKind, combined_text: str) -> tuple[ExtractedFields, list[str]]:
    """Retourne les champs extraits ainsi que les éventuels problèmes propres à ce type de document
    (ex : échec d'un chiffre de contrôle MRZ) — distincts des vérifications génériques faites
    ensuite par _collect_issues, qui elle ne voit que le résultat final (ExtractedFields)."""
    if document_kind == DocumentKind.TITRE_PROVISOIRE:
        parsed = parse_provisional_receipt_fields(combined_text)
        return (
            ExtractedFields(
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
            ),
            [],
        )

    if document_kind == DocumentKind.RECEPISSE:
        parsed = parse_receipt_fields(combined_text)
        return (
            ExtractedFields(
                documentKind=document_kind.value,
                lastName=parsed.lastName,
                firstName=parsed.firstName,
                kitNumber=parsed.kitNumber,
                requestIdentifier=parsed.requestIdentifier,
                paymentAmount=parsed.paymentAmount,
                paymentDate=parsed.paymentDate,
            ),
            [],
        )

    if document_kind == DocumentKind.PASSEPORT:
        parsed = parse_passport_fields(combined_text)
        fields = ExtractedFields(
            documentKind=document_kind.value,
            lastName=parsed.lastName,
            firstName=parsed.firstName,
            documentNumber=parsed.documentNumber,
            nationality=parsed.nationality,
            sex=parsed.sex,
            birthDate=parsed.birthDate,
            expiryDate=parsed.expiryDate,
        )
        passport_issues: list[str] = []
        if not parsed.mrzFound:
            passport_issues.append("Zone MRZ non détectée : reprenez une photo nette du bas de la page biographique")
        else:
            check_digits = validate_mrz_check_digits(parsed.checkDigitFailures)
            if not check_digits:
                passport_issues.append(check_digits.reason or "Chiffre(s) de contrôle MRZ invalide(s)")
        return fields, passport_issues

    # CNI définitive par défaut si le type n'est pas clairement identifié : c'est le document
    # cible principal, et le parseur CNI reste inoffensif (retourne des champs vides) sur un
    # texte qui ne lui correspond pas.
    parsed = parse_cni_fields(combined_text)
    return (
        ExtractedFields(
            documentKind=document_kind.value,
            lastName=parsed.lastName,
            firstName=parsed.firstName,
            documentNumber=parsed.documentNumber,
            sex=parsed.sex,
            birthDate=parsed.birthDate,
            expiryDate=parsed.expiryDate,
            birthPlace=parsed.birthPlace,
        ),
        [],
    )


def _collect_issues(
    document_kind: DocumentKind, fields: ExtractedFields, average_confidence: float, settings: Settings
) -> list[str]:
    issues: list[str] = []

    if document_kind == DocumentKind.UNKNOWN:
        issues.append("Type de document non reconnu (ni CNI, ni titre d'identité provisoire, ni récépissé, ni passeport)")

    if document_kind == DocumentKind.PASSEPORT:
        # L'essentiel du diagnostic passeport (MRZ absente ou chiffres de contrôle invalides) est
        # déjà remonté par _parse_by_kind, qui a accès au détail du parsing MRZ ; ici on ne
        # complète qu'avec les vérifications génériques de présence de champ.
        if not fields.lastName:
            issues.append("Nom non détecté dans la MRZ")
        if not fields.documentNumber:
            issues.append("Numéro de passeport non détecté dans la MRZ")
    else:
        if not fields.lastName:
            issues.append("Nom non détecté")
        if not fields.firstName:
            issues.append("Prénom non détecté")

        if document_kind == DocumentKind.RECEPISSE:
            # Un récépissé de paiement n'a ni date de naissance ni numéro de document : seuls
            # l'identifiant de demande et la date de paiement lui sont propres. Document
            # scanné/pâle par nature : fiabilité réduite signalée par défaut (voir Étape 5b).
            if not fields.requestIdentifier:
                issues.append("Identifiant de demande non détecté")
            if not fields.paymentDate:
                issues.append("Date de paiement non détectée")
            issues.append("Document scanné/pâle : extraction à fiabilité réduite par défaut, à vérifier manuellement")
        elif document_kind == DocumentKind.TITRE_PROVISOIRE:
            if not fields.birthDate:
                issues.append("Date de naissance non détectée")
            if not fields.requestIdentifier:
                issues.append("Identifiant de demande non détecté")
            issues.append("Document scanné/pâle : extraction à fiabilité réduite par défaut, à vérifier manuellement")
        else:
            if not fields.birthDate:
                issues.append("Date de naissance non détectée")
            if not fields.documentNumber:
                issues.append("Numéro de document non détecté")

    if average_confidence < settings.min_ocr_confidence:
        issues.append(f"Confiance OCR faible ({round(average_confidence)}%) : reprenez des photos plus nettes")

    return issues
