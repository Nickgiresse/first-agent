import re
from dataclasses import dataclass
from datetime import date

from app.ocr.text_utils import all_dates, clean_name, earliest, latest, normalize_line, value_after_label

# La CNI camerounaise est bilingue (français/anglais) : on cherche les libellés dans les deux
# langues. Calibré le 2026-07-31 sur une vraie CNI définitive (recto+verso), qui a révélé 3 écarts
# par rapport aux hypothèses initiales (best-effort, jamais testées sur un vrai document jusque-là) :
#
# 1. Les libellés bilingues complets ("DATE DE NAISSANCE/DATE OF BIRTH") doivent être matchés en
#    entier, sinon la partie anglaise restante sur la même ligne est prise à tort pour la valeur
#    (value_after_label ne retombe sur la ligne suivante que si le reste de la ligne est vide).
# 2. "SEXE/SEX" partage sa ligne avec "TAILLE/HEIGHT" sur ce document — la valeur du sexe (un
#    simple M/F) doit donc être cherchée à la fois dans le reste de la ligne ET le début de la
#    ligne suivante, pas seulement via value_after_label générique.
# 3. Il n'existe aucun libellé "N° CNI" visible sur ce document réel : le numéro de carte apparaît
#    seul, sans étiquette, généralement en bas de page — mais d'autres suites de 9-12 chiffres
#    existent ailleurs (numéro d'adresse, S.P./S.M.) qui peuvent produire un faux positif si on
#    prend juste la première suite de chiffres du texte. On préfère donc une ligne qui ne contient
#    QUE des chiffres (9 à 12) avant de se rabattre sur la recherche dans tout le texte.
_FIELD_LABEL_PATTERNS: dict[str, list[str]] = {
    "lastName": [
        r"NOM\s*[\/\:\-]?\s*SURNAME\b",
        r"NOM\s*[\/\:\-]?\s*NAME\b",
        r"\bSURNAME\b",
        r"^\s*NOM\b",
        r"FAMILY\s*NAME\b",
    ],
    "firstName": [
        r"PR[EÉ]NOMS?\s*[\/\:\-]?\s*GIVEN\s*NAMES?",
        r"PR[EÉ]NOMS?\s*[\/\:\-]?\s*FIRST\s*NAMES?",
        r"\bGIVEN\s*NAMES?\b",
        r"\bFIRST\s*NAMES?\b",
        r"PR[EÉ]NOMS?\b",
    ],
    "documentNumber": [
        r"N[°oO0]\s*C\.?\s*N\.?\s*I\.?",
        r"IDENTIFICATION\s*NUMBER",
        r"N[°oO0]\s*CARTE",
        r"DOCUMENT\s*N[°oO0]?",
        r"CARD\s*N[°oO0]?",
    ],
    "birthDate": [
        r"DATE\s*DE\s*NAISSANCE\s*[\/\:]?\s*DATE\s*OF\s*BIRTH",
        r"DATE\s*DE\s*NAISSANCE",
        r"DATE\s*OF\s*BIRTH",
        r"N[EÉ]E?\(?E?\)?\s*LE\b",
        r"BORN\s*ON\b",
    ],
    "expiryDate": [
        r"DATE\s*D.?\s*EXPIRATION\s*[\/\:]?\s*DATE\s*OF\s*EXPIRY",
        r"DATE\s*D.?\s*EXPIRATION",
        r"DATE\s*OF\s*EXPIRY",
        r"EXPIRE\s*LE\b",
        r"EXPIRATION\b",
        r"VALID\s*TO\b",
    ],
    "birthPlace": [
        r"LIEU\s*DE\s*NAISSANCE\s*[\/\:]?\s*PLACE\s*OF\s*BIRTH",
        r"LIEU\s*DE\s*NAISSANCE",
        r"PLACE\s*OF\s*BIRTH",
        r"\b[AÀ]\s*:\s*[A-Z]+",
    ],
}

_SEX_LABEL_PATTERN = re.compile(r"SEXE\s*[\/\:\-]?\s*SEX\b|\bSEXE\b|\bSEX\b", re.IGNORECASE)
_DOCUMENT_NUMBER_PATTERN = re.compile(r"\b\d{9,12}\b")
_STANDALONE_DOCUMENT_NUMBER_PATTERN = re.compile(r"^\d{9,12}$")
_SEX_PATTERN = re.compile(r"\b([MF])\b", re.IGNORECASE)


@dataclass
class CniFields:
    lastName: str | None = None
    firstName: str | None = None
    documentNumber: str | None = None
    sex: str | None = None
    birthDate: date | None = None
    expiryDate: date | None = None
    birthPlace: str | None = None


def parse_cni_fields(raw_text: str) -> CniFields:
    lines = [normalize_line(line) for line in raw_text.splitlines() if line.strip()]
    raw_values: dict[str, str] = {}
    sex: str | None = None

    for index, line in enumerate(lines):
        upper_line = line.upper()

        if sex is None:
            sex_match = _SEX_LABEL_PATTERN.search(upper_line)
            if sex_match:
                sex = _find_sex_value(lines, index, sex_match.end())

        for field_name, patterns in _FIELD_LABEL_PATTERNS.items():
            if field_name in raw_values:
                continue
            for pattern in patterns:
                match = re.search(pattern, upper_line)
                if match:
                    value = value_after_label(lines, index, match.end())
                    if value:
                        raw_values[field_name] = value
                    break

    document_number = (
        _extract_document_number(raw_values.get("documentNumber"))
        or _extract_standalone_document_number(lines)
        or _extract_document_number(raw_text)
    )
    dates = all_dates(raw_text)

    return CniFields(
        lastName=clean_name(raw_values.get("lastName")),
        firstName=clean_name(raw_values.get("firstName")),
        documentNumber=document_number,
        sex=sex,
        birthPlace=clean_name(raw_values.get("birthPlace")),
        birthDate=_parse_date_token(raw_values.get("birthDate", "")) or earliest(dates),
        expiryDate=_parse_date_token(raw_values.get("expiryDate", "")) or latest(dates),
    )


def _find_sex_value(lines: list[str], index: int, label_end: int) -> str | None:
    same_line_remainder = lines[index][label_end:]
    match = _SEX_PATTERN.search(same_line_remainder.upper())
    if match:
        return match.group(1)
    if index + 1 < len(lines):
        match = _SEX_PATTERN.search(lines[index + 1].upper())
        if match:
            return match.group(1)
    return None


def _extract_document_number(value: str | None) -> str | None:
    if not value:
        return None
    match = _DOCUMENT_NUMBER_PATTERN.search(value)
    return match.group(0) if match else None


def _extract_standalone_document_number(lines: list[str]) -> str | None:
    for line in lines:
        match = _STANDALONE_DOCUMENT_NUMBER_PATTERN.match(line.strip())
        if match:
            return match.group(0)
    return None


def _parse_date_token(text: str) -> date | None:
    dates = all_dates(text)
    return dates[0] if dates else None
