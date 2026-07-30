import re
from dataclasses import dataclass
from datetime import date

from app.ocr.text_utils import all_dates, clean_name, earliest, latest, normalize_line, value_after_label

# La CNI camerounaise est bilingue (français/anglais) : on cherche les libellés dans les deux
# langues. Approche best-effort par mots-clés + position — faute d'échantillons réels de CNI
# définitive pour calibrer des coordonnées fixes, à affiner au premier test réel (contrairement
# au titre d'identité provisoire, voir provisional_receipt_parser.py, calibré sur un vrai document).
_FIELD_LABEL_PATTERNS: dict[str, list[str]] = {
    "lastName": [r"NOM\s*/\s*NAME\b", r"\bSURNAME\b", r"^\s*NOM\s*:?\s*$"],
    "firstName": [r"PR[EÉ]NOM.?S?\s*/\s*GIVEN\s*NAMES?", r"\bGIVEN\s*NAMES?\b", r"PR[EÉ]NOM.?S?"],
    "documentNumber": [r"N[°oO]\s*C\.?\s*N\.?\s*I\.?", r"IDENTIFICATION\s*NUMBER", r"N[°oO]\s*CARTE"],
    "sex": [r"SEXE\s*/\s*SEX\b", r"\bSEXE\b", r"\bSEX\b"],
    "birthDate": [r"DATE\s*DE\s*NAISSANCE", r"DATE\s*OF\s*BIRTH", r"N[EÉ]E?\(?E?\)?\s*LE\b"],
    "expiryDate": [r"DATE\s*D.?\s*EXPIRATION", r"DATE\s*OF\s*EXPIRY", r"EXPIRE\s*LE\b"],
    "birthPlace": [r"LIEU\s*DE\s*NAISSANCE", r"PLACE\s*OF\s*BIRTH"],
}

_DOCUMENT_NUMBER_PATTERN = re.compile(r"\b\d{9,12}\b")
_SEX_PATTERN = re.compile(r"\b([MF])\b")


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

    for index, line in enumerate(lines):
        upper_line = line.upper()
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

    document_number = _extract_document_number(raw_values.get("documentNumber")) or _extract_document_number(
        raw_text
    )
    dates = all_dates(raw_text)

    return CniFields(
        lastName=clean_name(raw_values.get("lastName")),
        firstName=clean_name(raw_values.get("firstName")),
        documentNumber=document_number,
        sex=_extract_sex(raw_values.get("sex")),
        birthPlace=clean_name(raw_values.get("birthPlace")),
        birthDate=_parse_date_token(raw_values.get("birthDate", "")) or earliest(dates),
        expiryDate=_parse_date_token(raw_values.get("expiryDate", "")) or latest(dates),
    )


def _extract_sex(value: str | None) -> str | None:
    if not value:
        return None
    match = _SEX_PATTERN.search(value.upper())
    return match.group(1) if match else None


def _extract_document_number(value: str | None) -> str | None:
    if not value:
        return None
    match = _DOCUMENT_NUMBER_PATTERN.search(value)
    return match.group(0) if match else None


def _parse_date_token(text: str) -> date | None:
    dates = all_dates(text)
    return dates[0] if dates else None
