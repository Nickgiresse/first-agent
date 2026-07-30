import re
from dataclasses import dataclass
from datetime import date

from app.ocr.text_utils import all_dates, clean_name, normalize_line, value_after_label

# Calibré par analogie avec provisional_receipt_parser.py (même imprimeur, même mise en page
# bilingue français/anglais empilée "libellé puis valeur sur la ligne suivante") — non encore
# vérifié sur une vraie sortie OCR bruitée pour CE document précis (contrairement au titre
# provisoire, voir provisional_receipt_parser.py, qui dispose d'un échantillon réel en test de
# non-régression). À affiner si un vrai récépissé produit des libellés mal reconnus.
_LABEL_PATTERNS: dict[str, list[str]] = {
    "firstName": [r"\bPR[EÉ]NOMS?\b"],
    "amount": [r"MONTANT\s*PER[ÇC]U", r"AMOUNT\s*RECEIVED"],
    "paymentDateLabel": [r"DATE\s*DE\s*PAIEMENT", r"PAYMENT\s*DATE"],
    "requestIdLabel": [r"IDENTIFIANT\s*DEMANDE", r"REQUEST\s*IDENTIFIANT"],
}

# Mêmes motifs que provisional_receipt_parser.py (même document source, même style d'impression) :
# forme collée "KIT328" essayée avant la forme espacée "KIT 34" (artefact OCR).
_KIT_NUMBER_TIGHT_PATTERN = re.compile(r"\bKIT(\d{2,6})\b")
_KIT_NUMBER_LOOSE_PATTERN = re.compile(r"\bKIT\s+(\d{2,6})\b")

_REQUEST_ID_TOKEN_PATTERN = re.compile(r"(?=[A-Z0-9]{12,25}\b)(?=[A-Z0-9]*\d)[A-Z0-9]{12,25}\b")
_REQUEST_ID_SEARCH_WINDOW = 4

# Montant en FCFA : recherché aussi en secours dans tout le texte (pas seulement après le libellé),
# car "FCFA" colle presque toujours au montant sur ce document, contrairement au libellé qui peut
# être mal reconnu.
_AMOUNT_NEAR_CURRENCY_PATTERN = re.compile(r"(\d{2,7})\s*F\s*\.?\s*C\s*\.?\s*F\s*\.?\s*A", re.IGNORECASE)


@dataclass
class ReceiptFields:
    lastName: str | None = None
    firstName: str | None = None
    kitNumber: str | None = None
    requestIdentifier: str | None = None
    paymentAmount: str | None = None
    paymentDate: date | None = None


def parse_receipt_fields(raw_text: str) -> ReceiptFields:
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

    # "Nom" précède toujours directement "Prénoms" sur ce document (voir provisional_receipt_parser.py
    # pour la même hypothèse sur le document apparenté) : on prend la ligne juste au-dessus.
    if last_name_fallback_index is not None and last_name_fallback_index >= 0:
        candidate = lines[last_name_fallback_index]
        if candidate and not re.search(r"CAT[EÉ]GORIE|CARTE\s*NATIONALE|TYPE\b|PI\s*/\s*IC", candidate.upper()):
            raw_values["lastName"] = candidate

    kit_match = _KIT_NUMBER_TIGHT_PATTERN.search(raw_text.upper()) or _KIT_NUMBER_LOOSE_PATTERN.search(
        raw_text.upper()
    )
    amount = _extract_amount(raw_values.get("amount"), raw_text)
    payment_date = _extract_payment_date(raw_values.get("paymentDateLabel"), raw_text)

    return ReceiptFields(
        lastName=clean_name(raw_values.get("lastName")),
        firstName=clean_name(raw_values.get("firstName")),
        kitNumber=f"KIT{kit_match.group(1)}" if kit_match else None,
        requestIdentifier=_find_request_identifier(lines, request_id_label_index),
        paymentAmount=amount,
        paymentDate=payment_date,
    )


def _extract_amount(label_value: str | None, raw_text: str) -> str | None:
    if label_value:
        digits = re.search(r"\d{2,7}", label_value)
        if digits:
            return digits.group(0)
    fallback = _AMOUNT_NEAR_CURRENCY_PATTERN.search(raw_text)
    return fallback.group(1) if fallback else None


def _extract_payment_date(label_value: str | None, raw_text: str) -> date | None:
    if label_value:
        dates = all_dates(label_value)
        if dates:
            return dates[0]
    dates = all_dates(raw_text)
    return dates[0] if dates else None


def _find_request_identifier(lines: list[str], label_index: int | None) -> str | None:
    if label_index is None:
        return None
    window = lines[label_index : label_index + 1 + _REQUEST_ID_SEARCH_WINDOW]
    for line in window:
        match = _REQUEST_ID_TOKEN_PATTERN.search(re.sub(r"\s+", "", line.upper()))
        if match:
            return match.group(0)
    return None
