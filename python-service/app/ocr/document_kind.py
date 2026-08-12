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
# Deux formats normalisés, deux gabarits distincts.
#
# TD1 (carte d'identité) : 3 lignes de 30 caractères.
# TD3 (passeport)        : 2 lignes de 44 caractères.
#
# La tolérance absorbe les caractères que l'OCR ajoute ou perd en bordure de
# bande ; elle reste assez étroite pour que les deux gabarits ne se recouvrent
# pas, sans quoi la distinction ne servirait à rien.
_LIGNES_TD1, _LONGUEUR_TD1 = 3, (26, 34)
_LIGNES_TD3, _LONGUEUR_TD3 = 2, (40, 48)


def detect_document_kind(raw_text: str) -> DocumentKind:
    # Une MRZ ne dit PAS « passeport » : elle dit « document de voyage
    # normalisé ». La CNI camerounaise en porte une, au format TD1, sur trois
    # lignes d'environ 30 caractères ; le passeport porte une TD3, sur deux
    # lignes de 44.
    #
    # Le test précédent se contentait de « au moins deux lignes de 25 à 48
    # caractères » : les trois lignes d'une CNI le satisfaisaient toutes, et
    # TOUTE carte d'identité lisible était donc classée passeport. Elle partait
    # alors dans l'analyseur TD3, qui ne retient que les deux premières lignes
    # — jamais celle des noms — et découpe le numéro de document à la mauvaise
    # position. Le parseur CNI n'était jamais atteint sur le cas nominal.
    #
    # Les marqueurs textuels priment désormais sur la seule forme : un document
    # qui se déclare carte d'identité l'est, quelle que soit sa MRZ.
    # L'ordre des marqueurs textuels est celui d'origine, et il compte : un
    # titre provisoire comme un récépissé portent « Carte Nationale d'Identité »
    # dans leur champ « type de titre ». Les tester après le marqueur CNI les
    # ferait passer pour des cartes définitives.
    if _PROVISIONAL_MARKERS.search(raw_text):
        return DocumentKind.TITRE_PROVISOIRE
    if _RECEIPT_MARKERS.search(raw_text):
        return DocumentKind.RECEPISSE
    if _PASSPORT_MARKERS.search(raw_text):
        return DocumentKind.PASSEPORT
    if _CNI_MARKERS.search(raw_text):
        return DocumentKind.CNI
    # Aucun libellé lisible : on tranche alors sur le gabarit de la MRZ.
    if _has_mrz(raw_text, _LIGNES_TD3, _LONGUEUR_TD3):
        return DocumentKind.PASSEPORT
    # Une TD1 dont aucun libellé n'a été lu reste une carte : c'est le format
    # des cartes d'identité, jamais celui d'un passeport.
    if _has_mrz(raw_text, _LIGNES_TD1, _LONGUEUR_TD1):
        return DocumentKind.CNI
    return DocumentKind.UNKNOWN


def _has_mrz(raw_text: str, lignes_attendues: int, longueur: tuple[int, int]) -> bool:
    """Détecte la présence probable d'une zone MRZ : au moins deux lignes composées presque
    uniquement de A-Z/0-9/< (caractère de remplissage MRZ), suffisamment longues et contenant
    plusieurs "<". Ne valide pas les chiffres de contrôle ici — seulement une détection de forme,
    la validation complète se fait dans l'extracteur passeport (voir passport_parser.py)."""
    matching_lines = 0
    for line in raw_text.upper().splitlines():
        cleaned = line.strip().replace(" ", "")
        if not (longueur[0] <= len(cleaned) <= longueur[1]):
            continue
        if not _MRZ_LINE_PATTERN.match(cleaned):
            continue
        if cleaned.count("<") < _MRZ_MIN_FILLER_CHARS:
            continue
        matching_lines += 1
    return matching_lines >= lignes_attendues
