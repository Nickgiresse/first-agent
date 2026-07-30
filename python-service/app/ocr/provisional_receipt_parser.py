import re
from dataclasses import dataclass
from datetime import date

from app.ocr.text_utils import all_dates, clean_name, earliest, latest, normalize_line, value_after_label

# Calibré sur un vrai "Titre d'identité provisoire" camerounais (reçu pendant la demande de CNI,
# avant délivrance de la carte définitive) — contrairement à cni_parser.py, non deviné.
# Les libellés eux-mêmes sont souvent mal reconnus par l'OCR sur ce document (fond tamponné/
# filigrané) ; les motifs ci-dessous restent volontairement courts (un seul mot-clé) pour rester
# robustes face à un OCR partiel. Pas de correspondance directe sur "NOM" pour le nom de famille :
# ça matche aussi "Nom du Père"/"Nom de la Mère" dès que l'OCR déforme "du"/"de la" (déjà observé
# en pratique, ex. "du" lu "Gu", "Mère" lu "Meee") — on préfère la position, fiable ici car
# "Prénoms" (juste après "Nom" sur ce document) se lit toujours correctement.
_LABEL_PATTERNS: dict[str, list[str]] = {
    "firstName": [r"\bPR[EÉ]NOMS?\b"],
    "fatherName": [r"P[EÈ]RE\b", r"\bFATHER\b"],
    "motherName": [r"M[EÈ]RE\b", r"\bMOTHER\b"],
    "profession": [r"\bPROFESSION\b", r"\bOCCUPATION\b"],
    "requestIdLabel": [r"IDENTIFIANT\s*DEMANDE", r"REQUEST\s*IDENTIFIANT"],
}

# "KIT328" (collé, imprimé) plutôt que "KIT 34" (avec espace, artefact OCR observé sur un vrai
# document) : on essaie d'abord la forme collée, plus fiable, avant de se rabattre sur la forme
# espacée.
_KIT_NUMBER_TIGHT_PATTERN = re.compile(r"\bKIT(\d{2,6})\b")
_KIT_NUMBER_LOOSE_PATTERN = re.compile(r"\bKIT\s+(\d{2,6})\b")

# Identifiant de demande (valeur imprimée sous le code-barres) : recherché uniquement à proximité
# du libellé "Identifiant demande" pour limiter les faux positifs sur d'autres suites de
# caractères de l'image. Best-effort : l'OCR générique lit mal les codes-barres (souvent fragmenté
# par de la ponctuation parasite) ; une lecture dédiée (ex. pyzbar sur le code-barres lui-même)
# serait plus fiable si ce champ s'avère important en pratique — actuellement, ne pas trouver de
# valeur est plus sûr que retourner un token OCR incorrect.
_REQUEST_ID_TOKEN_PATTERN = re.compile(r"(?=[A-Z0-9]{12,25}\b)(?=[A-Z0-9]*\d)[A-Z0-9]{12,25}\b")
_REQUEST_ID_SEARCH_WINDOW = 4  # lignes après le libellé où chercher le token

_BIRTHPLACE_AFTER_DATE_PATTERN = re.compile(
    r"\d{1,2}\s*[.\-/]\s*\d{1,2}\s*[.\-/]\s*\d{2,4}\D{0,15}?([A-ZÀ-Ÿ]{3,})"
)


@dataclass
class ProvisionalReceiptFields:
    lastName: str | None = None
    firstName: str | None = None
    fatherName: str | None = None
    motherName: str | None = None
    profession: str | None = None
    birthDate: date | None = None
    birthPlace: str | None = None
    expiryDate: date | None = None  # fin de validité du titre provisoire (courte, ~3 mois)
    kitNumber: str | None = None
    requestIdentifier: str | None = None


def parse_provisional_receipt_fields(raw_text: str) -> ProvisionalReceiptFields:
    lines = [normalize_line(line) for line in raw_text.splitlines() if line.strip()]
    raw_values: dict[str, str] = {}
    last_name_fallback_index: int | None = None
    request_id_label_index: int | None = None

    for index, line in enumerate(lines):
        upper_line = line.upper()
        for field_name, patterns in _LABEL_PATTERNS.items():
            if field_name in raw_values:
                continue
            for pattern in patterns:
                match = re.search(pattern, upper_line)
                if not match:
                    continue
                if field_name == "requestIdLabel":
                    request_id_label_index = index
                    raw_values["requestIdLabel"] = "1"
                    break
                value = value_after_label(lines, index, match.end())
                if value:
                    raw_values[field_name] = value
                if field_name == "firstName":
                    last_name_fallback_index = index - 1
                break

    # "Nom" précède toujours directement "Prénoms" sur ce document : on prend la ligne juste
    # au-dessus (voir note en tête de fichier sur pourquoi on ne matche pas "NOM" directement).
    if last_name_fallback_index is not None and last_name_fallback_index >= 0:
        candidate = lines[last_name_fallback_index]
        if candidate and not re.search(r"CAT[EÉ]GORIE|CARTE\s*NATIONALE|TYPE\b", candidate.upper()):
            raw_values["lastName"] = candidate

    kit_match = _KIT_NUMBER_TIGHT_PATTERN.search(raw_text.upper()) or _KIT_NUMBER_LOOSE_PATTERN.search(
        raw_text.upper()
    )
    birthplace_match = _BIRTHPLACE_AFTER_DATE_PATTERN.search(raw_text.upper())
    dates = all_dates(raw_text)

    return ProvisionalReceiptFields(
        lastName=clean_name(raw_values.get("lastName")),
        firstName=clean_name(raw_values.get("firstName")),
        fatherName=clean_name(raw_values.get("fatherName")),
        motherName=clean_name(raw_values.get("motherName")),
        profession=clean_name(raw_values.get("profession")),
        birthDate=earliest(dates),
        birthPlace=birthplace_match.group(1) if birthplace_match else None,
        expiryDate=latest(dates),
        kitNumber=f"KIT{kit_match.group(1)}" if kit_match else None,
        requestIdentifier=_find_request_identifier(lines, request_id_label_index),
    )


def _find_request_identifier(lines: list[str], label_index: int | None) -> str | None:
    if label_index is None:
        return None
    window = lines[label_index : label_index + 1 + _REQUEST_ID_SEARCH_WINDOW]
    for line in window:
        match = _REQUEST_ID_TOKEN_PATTERN.search(re.sub(r"\s+", "", line.upper()))
        if match:
            return match.group(0)
    return None
