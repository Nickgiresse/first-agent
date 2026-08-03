"""Règles de validation post-OCR, communes aux différents types de documents (Étape 7 de la
mission multi-documents). Principe directeur : une valeur hors format n'est jamais renvoyée comme
fiable en silence — chaque fonction retourne un verdict explicite (ValidationResult) plutôt que de
corriger ou deviner à la place de l'appelant."""

import re
from dataclasses import dataclass
from datetime import date

_DATE_PATTERN = re.compile(r"^(\d{2})\.(\d{2})\.(\d{4})$")
_CNI_UNIQUE_ID_PATTERN = re.compile(r"^\d{17}$")

MIN_PLAUSIBLE_HEIGHT_M = 0.40
MAX_PLAUSIBLE_HEIGHT_M = 2.50


@dataclass
class ValidationResult:
    valid: bool
    reason: str | None = None

    def __bool__(self) -> bool:
        return self.valid


def validate_date_format(value: str) -> ValidationResult:
    """Vérifie le format JJ.MM.AAAA et que la date est calendaire (ex : rejette 31.02.2020,
    format correct mais jour inexistant pour ce mois)."""
    match = _DATE_PATTERN.match(value.strip())
    if not match:
        return ValidationResult(False, "Format attendu JJ.MM.AAAA")
    day, month, year = (int(part) for part in match.groups())
    try:
        date(year, month, day)
    except ValueError:
        return ValidationResult(False, "Date calendaire invalide")
    return ValidationResult(True)


def validate_issue_before_expiry(issue_date: date, expiry_date: date) -> ValidationResult:
    if issue_date >= expiry_date:
        return ValidationResult(False, "La date de délivrance doit précéder la date d'expiration")
    return ValidationResult(True)


def validate_sex(value: str) -> ValidationResult:
    if value.strip().upper() not in ("M", "F"):
        return ValidationResult(False, "Le sexe doit être M ou F")
    return ValidationResult(True)


def validate_height_meters(value: float) -> ValidationResult:
    if not (MIN_PLAUSIBLE_HEIGHT_M <= value <= MAX_PLAUSIBLE_HEIGHT_M):
        return ValidationResult(False, f"Taille peu plausible ({value} m)")
    return ValidationResult(True)


def validate_cni_unique_identifier(value: str) -> ValidationResult:
    """L'identifiant unique d'une CNI camerounaise comporte exactement 17 chiffres."""
    if not _CNI_UNIQUE_ID_PATTERN.match(value.strip()):
        return ValidationResult(False, "L'identifiant unique CNI doit comporter exactement 17 chiffres")
    return ValidationResult(True)


def validate_mrz_check_digits(check_digit_failures: list[str]) -> ValidationResult:
    """À appeler avec PassportFields.checkDigitFailures (voir passport_parser.py)."""
    if check_digit_failures:
        return ValidationResult(
            False, "Chiffre(s) de contrôle MRZ invalide(s) : " + ", ".join(check_digit_failures)
        )
    return ValidationResult(True)
