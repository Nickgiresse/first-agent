import re
from datetime import date

DATE_PATTERN = re.compile(r"(\d{1,2})\s*[.\-/]\s*(\d{1,2})\s*[.\-/]\s*(\d{2,4})")
_NAME_NOISE_PATTERN = re.compile(r"[^A-ZÀ-Ÿ\s\-']")


def normalize_line(line: str) -> str:
    return re.sub(r"\s+", " ", line).strip()


def clean_name(value: str | None) -> str | None:
    if not value:
        return None
    cleaned = _NAME_NOISE_PATTERN.sub("", value.upper()).strip()
    return cleaned or None


def to_date(day_str: str, month_str: str, year_str: str) -> date | None:
    try:
        day, month, year = int(day_str), int(month_str), int(year_str)
        if year < 100:
            year += 2000 if year < 50 else 1900
        return date(year, month, day)
    except ValueError:
        return None


def all_dates(text: str) -> list[date]:
    dates: list[date] = []
    for match in DATE_PATTERN.finditer(text):
        parsed = to_date(*match.groups())
        if parsed:
            dates.append(parsed)
    return sorted(dates)


def earliest(dates: list[date]) -> date | None:
    return dates[0] if dates else None


def latest(dates: list[date]) -> date | None:
    return dates[-1] if dates else None


# Mots appartenant aux ÉTIQUETTES d'une CNI, jamais à une valeur.
# La carte camerounaise est bilingue : chaque intitulé français est doublé de
# sa traduction anglaise, sur la ligne suivante ou accolé sans espace quand
# l'OCR fusionne les mots. Sans garde-fou, deux cas relevés sur des pièces
# réelles produisaient des valeurs absurdes : « PRENOMS/GIVEN NAMES » océrisé
# « PRENOMSIGIVENNAMES » donnait le prénom « IGIVENNAMES », et un « LIEU DE
# NAISSANCE » seul sur sa ligne faisait retenir la ligne suivante, « PLACE OF
# BIRTH », comme lieu de naissance.
_MOTS_ETIQUETTE = (
    "NOM", "SURNAME", "PRENOM", "PRENOMS", "GIVEN", "NAMES", "NAME",
    "DATE", "BIRTH", "NAISSANCE", "LIEU", "PLACE", "SEXE", "SEX",
    "EXPIRY", "EXPIRATION", "IDENTITY", "IDENTITE", "IDENTIFICATION",
    "IDENTIFIANT", "IDENTIFIER", "UNIQUE", "CARTE", "CARD", "NATIONAL",
    "NATIONALE", "NATIONALITE", "NATIONALITY", "POSTE", "POST", "REPUBLIQUE",
    "REPUBLIC", "CAMEROUN", "CAMEROON", "SIGNATURE", "AUTORITE", "AUTHORITY",
    "DELIVRANCE", "ISSUE", "PROFESSION", "OCCUPATION", "ADRESSE", "ADDRESS",
    "PERE", "FATHER", "MERE", "MOTHER", "TAILLE", "HEIGHT", "OF", "DE", "DU",
)


def looks_like_label(value: str | None) -> bool:
    """Le texte est-il un reste d'étiquette plutôt qu'une valeur ?

    Vrai si, une fois chiffres et ponctuation écartés, tous les mots d'au
    moins deux lettres relèvent du vocabulaire des intitulés. Un nom réel
    contient forcément un mot qui n'en fait pas partie.

    La comparaison tolère la concaténation : l'OCR colle fréquemment les mots
    d'un intitulé bilingue, et « PRENOMSIGIVENNAMES » doit être reconnu comme
    une étiquette alors qu'aucun de ses mots pris isolément n'y figure.
    """
    if not value or not value.strip():
        return True
    nettoye = re.sub(r"[^A-ZÀ-Ÿ\s]", " ", value.upper())
    mots = [m for m in nettoye.split() if len(m) >= 2]
    if not mots:
        # Aucun mot d'au moins deux lettres : c'est une valeur courte et non
        # une étiquette. Le sexe (« M », « F ») et les numéros passent ici ;
        # les rejeter faisait perdre ces champs.
        return False
    for mot in mots:
        if mot in _MOTS_ETIQUETTE:
            continue
        if not _est_suite_d_etiquettes(mot):
            return False
    return True


# Budget de caractères ignorables lors de la décomposition : les séparateurs
# « / », « ' » et « : » d'un intitulé bilingue sont souvent lus comme des
# lettres parasites (le « I » de « PRENOMSIGIVENNAMES », l'apostrophe perdue
# de « DATE D'EXPIRATION »). Strictement borné : sans limite, un vrai
# patronyme finirait grignoté lettre à lettre et passerait pour une étiquette.
_REBUTS_MAX = 2

# Lettres erronées tolérées dans l'ensemble du mot, pour absorber les
# confusions de caractères de l'OCR. Une seule : au-delà, des patronymes
# courts commenceraient à ressembler à des intitulés.
_SUBSTITUTIONS_MAX = 1

# En deçà de cette longueur, aucune tolérance : le mot doit se décomposer
# exactement en termes d'étiquette. Voir la note dans _est_suite_d_etiquettes.
_LONGUEUR_MIN_TOLERANCE = 8


def _est_suite_d_etiquettes(mot: str) -> bool:
    """Le mot se décompose-t-il entièrement en termes d'étiquette ?

    Exploration avec retour arrière, et non gloutonne. La différence n'est pas
    théorique : « DATEDEXPIRATION » se découpe en DATE + D + EXPIRATION, mais
    une lecture gloutonne prend DATE puis DE et bute sur « XPIRATION »,
    laissant passer l'intitulé pour un patronyme. C'est exactement ce qui
    faisait retenir « DATE D'EXPIRATION » comme nom de famille sur deux pièces
    réelles.
    """
    termes = tuple(t for t in _MOTS_ETIQUETTE if len(t) >= 2)
    vus: set[tuple[int, int, int]] = set()

    # Les tolérances ne s'appliquent qu'aux mots longs. Sur un mot court, le
    # cumul « deux lettres sautées + une substituée » finit par rapprocher
    # n'importe quoi d'un intitulé : le patronyme MPOAME devenait NAME après
    # avoir écarté M et P. Un intitulé collé fait de toute façon dix lettres
    # ou plus, un patronyme court n'a donc rien à y gagner.
    tolerances = len(mot) >= _LONGUEUR_MIN_TOLERANCE
    rebuts_initial = _REBUTS_MAX if tolerances else 0
    subs_initial = _SUBSTITUTIONS_MAX if tolerances else 0

    def correspond(terme: str, position: int, tolerant: bool) -> bool:
        """Le terme est-il présent à cette position, à une lettre près ?

        La tolérance couvre les confusions de caractères de l'OCR, sans avoir
        à énumérer les variantes : « NAISSANCE » lu « MAISSANCE » se reconnaît
        ainsi, comme se reconnaîtraient les confusions O/0 ou I/1.
        """
        fin = position + len(terme)
        if fin > len(mot):
            return False
        ecarts = sum(1 for a, b in zip(terme, mot[position:fin]) if a != b)
        return ecarts == 0 or (tolerant and ecarts == 1)

    def explorer(position: int, rebuts: int, subs: int) -> bool:
        if position >= len(mot):
            return True
        if (position, rebuts, subs) in vus:
            return False
        vus.add((position, rebuts, subs))
        for terme in termes:
            if correspond(terme, position, False) and explorer(position + len(terme), rebuts, subs):
                return True
        # Une seule lettre erronée dans tout le mot, et seulement sur les
        # termes assez longs : l'autoriser sur « DE » ou « OF » ferait
        # correspondre n'importe quoi.
        if subs > 0:
            for terme in termes:
                if (len(terme) >= 4 and correspond(terme, position, True)
                        and not correspond(terme, position, False)
                        and explorer(position + len(terme), rebuts, subs - 1)):
                    return True
        # Caractère parasite : on l'ignore et on reprend, dans la limite du budget.
        if rebuts > 0 and explorer(position + 1, rebuts - 1, subs):
            return True
        return False

    return explorer(0, rebuts_initial, subs_initial)


def value_after_label(lines: list[str], index: int, label_end: int) -> str | None:
    remainder = lines[index][label_end:].strip(" :/-–—")
    if remainder and not looks_like_label(remainder):
        return remainder
    if index + 1 < len(lines):
        suivante = lines[index + 1].strip(" :/-–—")
        if suivante and not looks_like_label(suivante):
            return suivante
    return None
