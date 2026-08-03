import re
from dataclasses import dataclass, field
from datetime import date

from mrz.checker.td3 import TD3CodeChecker

from app.ocr.text_utils import clean_name, to_date

# Zone MRZ (bas du passeport, format TD3/ICAO 9303) : deux lignes de 44 caractères. Cf.
# document_kind.py::_has_mrz pour la détection de forme ; ici on va plus loin en extrayant et
# validant réellement le contenu via la librairie `mrz` (chiffres de contrôle inclus).
_MRZ_LINE_LENGTH = 44
_MRZ_LINE_PATTERN = re.compile(r"^[A-Z0-9<]+$")
_MRZ_MIN_LINE_LENGTH = 25
_MRZ_MAX_LINE_LENGTH = 48
_MRZ_MIN_FILLER_CHARS = 3


@dataclass
class PassportFields:
    lastName: str | None = None
    firstName: str | None = None
    documentNumber: str | None = None
    nationality: str | None = None
    birthDate: date | None = None
    sex: str | None = None
    expiryDate: date | None = None
    mrzFound: bool = False
    mrzValid: bool = False
    # Noms des champs dont le chiffre de contrôle MRZ a échoué (voir ICAO 9303) — vide si tout est
    # valide ou si aucune MRZ n'a pu être lue. "global" = chiffre de contrôle final (sur toute la ligne 2).
    checkDigitFailures: list[str] = field(default_factory=list)


def parse_passport_fields(raw_text: str) -> PassportFields:
    """Priorise la zone MRZ (nom, prénoms, n° passeport, nationalité, date de naissance, sexe,
    date d'expiration y sont encodés avec chiffres de contrôle) plutôt que les champs visuels,
    nettement moins fiables en OCR générique sur la page de données biographiques d'un passeport
    (mise en page dense, filigranes). Les champs visuels ne sont pas extraits séparément ici : en
    cas d'échec de lecture de la MRZ, mieux vaut renvoyer un champ absent (voir Étape 6) que de
    deviner sur une extraction non fiable — cohérent avec le principe "jamais renvoyé comme sûr en
    silence" (Étape 7) déjà appliqué au reste du pipeline.
    """
    mrz_lines = _extract_mrz_lines(raw_text)
    if mrz_lines is None:
        return PassportFields(mrzFound=False)

    checker = TD3CodeChecker("\n".join(mrz_lines))
    parsed = checker.fields()
    mrz_valid = bool(checker)

    check_digit_failures: list[str] = []
    if not checker.birth_date_hash:
        check_digit_failures.append("birthDate")
    if not checker.expiry_date_hash:
        check_digit_failures.append("expiryDate")
    if not checker.document_number_hash:
        check_digit_failures.append("documentNumber")
    if not checker.final_hash:
        check_digit_failures.append("global")
    if mrz_valid is False and not check_digit_failures:
        # `bool(checker)` peut être invalide pour d'autres raisons que les 4 chiffres de contrôle
        # nommés ci-dessus (ex : zone nom illisible au point que la librairie `mrz` ne peut plus la
        # découper en surname/given names — elle renvoie alors la chaîne littérale "None", voir
        # _mrz_value ci-dessous). Sans ce filet, un MRZ invalide pour une autre raison serait
        # silencieusement considéré comme "sans problème de chiffre de contrôle".
        check_digit_failures.append("global")

    sex = parsed.sex if parsed.sex in ("M", "F") else None

    return PassportFields(
        lastName=clean_name(_mrz_value(parsed.surname)),
        firstName=clean_name(_mrz_value(parsed.name, replace_filler_with_space=True)),
        documentNumber=_mrz_value(parsed.document_number),
        nationality=_mrz_value(parsed.nationality),
        birthDate=_mrz_date_to_date(parsed.birth_date),
        sex=sex,
        expiryDate=_mrz_date_to_date(parsed.expiry_date),
        mrzFound=True,
        mrzValid=mrz_valid,
        checkDigitFailures=check_digit_failures,
    )


def _mrz_value(raw: str | None, replace_filler_with_space: bool = False) -> str | None:
    """Nettoie une valeur de champ MRZ brute. La librairie `mrz` retourne la chaîne littérale
    "None" (pas un vrai None Python) quand un champ n'a pas pu être découpé correctement — un OCR
    trop bruité pour être fiable ne doit jamais aboutir à ce que "None" soit traité comme une
    valeur réelle (Étape 6/7 : jamais de valeur fiable en silence)."""
    if not raw or raw == "None":
        return None
    cleaned = raw.replace("<", " ") if replace_filler_with_space else raw.replace("<", "")
    cleaned = cleaned.strip()
    return cleaned or None


def _extract_mrz_lines(raw_text: str) -> tuple[str, str] | None:
    """Repère les deux premières lignes du texte OCR qui ressemblent à de la MRZ (composition
    A-Z/0-9/<, longueur plausible, plusieurs "<"), dans leur ordre d'apparition — la MRZ TD3 a
    toujours sa ligne 1 (identité/type/pays) avant sa ligne 2 (numéro/dates/nationalité), et l'OCR
    lit normalement de haut en bas. Complète/tronque à 44 caractères avec "<" (le caractère de
    remplissage standard de la MRZ) car l'OCR ne coupe presque jamais une ligne pile à 44 caractères.
    """
    candidates: list[str] = []
    for line in raw_text.upper().splitlines():
        cleaned = re.sub(r"\s+", "", line.strip())
        if not (_MRZ_MIN_LINE_LENGTH <= len(cleaned) <= _MRZ_MAX_LINE_LENGTH):
            continue
        if not _MRZ_LINE_PATTERN.match(cleaned):
            continue
        if cleaned.count("<") < _MRZ_MIN_FILLER_CHARS:
            continue
        candidates.append(cleaned)
        if len(candidates) == 2:
            break

    if len(candidates) < 2:
        return None
    return _pad_to_length(candidates[0]), _pad_to_length(candidates[1])


def _pad_to_length(line: str, length: int = _MRZ_LINE_LENGTH) -> str:
    if len(line) >= length:
        return line[:length]
    return line + "<" * (length - len(line))


def _mrz_date_to_date(yymmdd: str) -> date | None:
    """Les dates MRZ sont encodées AAMMJJ (2 chiffres d'année). Le pivot 2 chiffres → siècle
    (< 50 → 20xx, >= 50 → 19xx, voir text_utils.to_date) est une limite connue et documentée du
    format MRZ lui-même (aucune information de siècle dans la MRZ) : appliqué ici uniformément
    pour la naissance comme pour l'expiration, faute de mieux."""
    if not yymmdd or len(yymmdd) != 6 or not yymmdd.isdigit():
        return None
    year, month, day = yymmdd[0:2], yymmdd[2:4], yymmdd[4:6]
    return to_date(day, month, year)
