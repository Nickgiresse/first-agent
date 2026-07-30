import re
from enum import Enum


class DocumentKind(str, Enum):
    CNI = "CNI"
    TITRE_PROVISOIRE = "TITRE_PROVISOIRE"
    RECEPISSE = "RECEPISSE"
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


def detect_document_kind(raw_text: str) -> DocumentKind:
    if _PROVISIONAL_MARKERS.search(raw_text):
        return DocumentKind.TITRE_PROVISOIRE
    if _RECEIPT_MARKERS.search(raw_text):
        return DocumentKind.RECEPISSE
    if _CNI_MARKERS.search(raw_text):
        return DocumentKind.CNI
    return DocumentKind.UNKNOWN
