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
# l'OCR fusionne les mots. Sans garde-fou, deux cas réels relevés sur des
# pièces archivées produisaient des valeurs absurdes : « PRENOMS/GIVEN NAMES »
# océrisé « PRENOMSIGIVENNAMES » donnait le prénom « IGIVENNAMES », et un
# « LIEU DE NAISSANCE » seul sur sa ligne faisait retenir la ligne suivante,
# « PLACE OF BIRTH », comme lieu de naissance.
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
        # Mot collé : « PRENOMSIGIVENNAMES » se décompose entièrement en
        # termes d'étiquette, un patronyme non.
        reste = mot
        # Budget de caractères ignorables : les séparateurs « / » et « : » de
        # l'intitulé bilingue sont souvent lus comme des lettres parasites
        # (le « I » de « ...PRENOMSI GIVENNAMES »). Strictement borné à deux,
        # sinon un vrai patronyme finirait grignoté lettre à lettre et serait
        # pris à tort pour une étiquette.
        rebuts = 2
        consomme = True
        while reste and consomme:
            consomme = False
            for terme in sorted(_MOTS_ETIQUETTE, key=len, reverse=True):
                if len(terme) >= 3 and reste.startswith(terme):
                    reste = reste[len(terme):]
                    consomme = True
                    break
            if not consomme and rebuts > 0 and len(reste) > 1:
                reste = reste[1:]
                rebuts -= 1
                consomme = True
            elif not consomme and len(reste) == 1:
                reste = ""
        if reste:
            return False
    return True


def value_after_label(lines: list[str], index: int, label_end: int) -> str | None:
    remainder = lines[index][label_end:].strip(" :/-–—")
    if remainder and not looks_like_label(remainder):
        return remainder
    if index + 1 < len(lines):
        suivante = lines[index + 1].strip(" :/-–—")
        if suivante and not looks_like_label(suivante):
            return suivante
    return None
