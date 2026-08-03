import re
from enum import Enum


class DocumentKind(str, Enum):
    CNI = "CNI"
    TITRE_PROVISOIRE = "TITRE_PROVISOIRE"
    RECEPISSE = "RECEPISSE"
    PASSEPORT = "PASSEPORT"
    UNKNOWN = "UNKNOWN"


# Un titre provisoire contient aussi "Carte Nationale d'Identité" dans son champ "Type de titre" :
# le marqueur provisoire doit donc être vérifié avant le marqueur CNI, pas après. Le récépissé de
# paiement (reçu remis au dépôt de la demande, avant le titre provisoire) a le même problème — son
# champ "Type de titre"/"Catégorie" contient aussi "Carte Nationale d'Identité" — donc son marqueur
# doit lui aussi être vérifié avant celui de la CNI.
_PROVISIONAL_MARKERS = re.compile(
    r"TITRE\s*D.?\s*IDENTIT[EÉ]\s*PROVISOIRE|TEMPORARY\s*IDENTITY\s*DOCUMENT", re.IGNORECASE
)
_RECEIPT_MARKERS = re.compile(r"RE[ÇC]U\s*DE\s*PAIEMENT|PAYMENT\s*RECEIPT", re.IGNORECASE)
_CNI_MARKERS = re.compile(r"CARTE\s*NATIONALE\s*D.?\s*IDENTIT[EÉ]", re.IGNORECASE)
_PASSPORT_MARKERS = re.compile(r"\bPASSEPORT\b|\bPASSPORT\b", re.IGNORECASE)

# Ligne de zone MRZ (bas du passeport, format TD3/ICAO 9303) : uniquement des lettres majuscules,
# chiffres et le caractère de remplissage "<". 44 caractères en théorie, mais l'OCR ne coupe
# presque jamais pile à cette longueur (bruit, fusion/perte d'un caractère) — on tolère une
# fourchette plutôt qu'une longueur exacte, et on vérifie surtout la composition du texte.
_MRZ_LINE_PATTERN = re.compile(r"^[A-Z0-9<]+$")
_MRZ_MIN_LINE_LENGTH = 25
_MRZ_MAX_LINE_LENGTH = 48
_MRZ_MIN_FILLER_CHARS = 3  # au moins quelques "<" : un vrai texte ne matcherait pas ce motif par hasard
_MRZ_MIN_LINES = 2  # la MRZ TD3 (passeport) comporte exactement 2 lignes


def detect_document_kind(raw_text: str) -> DocumentKind:
    # Vérifié avant les autres marqueurs : la zone MRZ est un signal très spécifique (jamais produit
    # par hasard sur une CNI/un reçu) donc aucun risque de faux positif à la tester en premier.
    if _PASSPORT_MARKERS.search(raw_text) or _has_mrz(raw_text):
        return DocumentKind.PASSEPORT
    if _PROVISIONAL_MARKERS.search(raw_text):
        return DocumentKind.TITRE_PROVISOIRE
    if _RECEIPT_MARKERS.search(raw_text):
        return DocumentKind.RECEPISSE
    if _CNI_MARKERS.search(raw_text):
        return DocumentKind.CNI
    return DocumentKind.UNKNOWN


def _has_mrz(raw_text: str) -> bool:
    """Détecte la présence probable d'une zone MRZ : au moins deux lignes composées presque
    uniquement de A-Z/0-9/< (caractère de remplissage MRZ), suffisamment longues et contenant
    plusieurs "<". Ne valide pas les chiffres de contrôle ici — seulement une détection de forme,
    la validation complète se fait dans l'extracteur passeport (voir passport_parser.py)."""
    matching_lines = 0
    for line in raw_text.upper().splitlines():
        cleaned = line.strip().replace(" ", "")
        if not (_MRZ_MIN_LINE_LENGTH <= len(cleaned) <= _MRZ_MAX_LINE_LENGTH):
            continue
        if not _MRZ_LINE_PATTERN.match(cleaned):
            continue
        if cleaned.count("<") < _MRZ_MIN_FILLER_CHARS:
            continue
        matching_lines += 1
    return matching_lines >= _MRZ_MIN_LINES
