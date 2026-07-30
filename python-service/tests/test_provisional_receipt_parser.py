from datetime import date

from app.ocr.provisional_receipt_parser import parse_provisional_receipt_fields

CLEAN_SAMPLE_TEXT = """
Titre d'identité provisoire Temporary identity document
Identifiant demande / Request identifiant
OU36328I5J3N7CQPJJ85
Numéro Kit / Kit id KIT328
Nom
FOADJO KAMDEM
Prénoms
NICK GIRESSE
Nom du Père
KAMDEM FOADJO VINCENT DE PAUL
Nom de la Mère
TOUKAM ODETTE
Né(e) le 26.01.2005 à BAFOUSSAM
Profession
ELEVE
Valable du 13.07.2022 au 13.10.2022
"""

# Extrait dérivé d'une vraie photo (OCR Tesseract réel, non retouché à la main) : sert de test de
# non-régression pour les correctifs trouvés en testant sur un vrai document (voir provisional_receipt_parser.py).
REAL_OCR_EXCERPT = """
Titre d'identité provisoire |. Temporary ir document

identifiant demande / Request identifiant ;

LE at ii

363281S/3N7COPJ.

Numéro Kil / Kit 34 KIT328 bs Pé vs

Type Go biro ser CNI : Carte poder archry ie

I Criegero Carte Nationate Sigensica

FOADJO. KAMDEM ri

Prénoms NICK GIRES A

Sumar.es 0

Nom Gu Père -xadoené| FOADJO MINCENT DE PAUL |

Fathers Name = ETS ng} ine? a? Oa

Dai | Nom 63 ta Meee é7 TOUKAM ODETTE su
Moine s Name é iyé j

eyo) in fe 6.01.2005. re = BAFOUSSAM
Bua > rs Ae +
"""


def test_parses_clean_text_end_to_end():
    fields = parse_provisional_receipt_fields(CLEAN_SAMPLE_TEXT)

    assert fields.lastName == "FOADJO KAMDEM"
    assert fields.firstName == "NICK GIRESSE"
    assert fields.fatherName == "KAMDEM FOADJO VINCENT DE PAUL"
    assert fields.motherName == "TOUKAM ODETTE"
    assert fields.profession == "ELEVE"
    assert fields.kitNumber == "KIT328"
    assert fields.requestIdentifier == "OU36328I5J3N7CQPJJ85"


def test_real_ocr_excerpt_extracts_correct_last_name_via_position_fallback():
    # Le libellé "Nom" lui-même n'apparaît pas ici (dropé par l'OCR) : doit retomber sur la
    # ligne juste au-dessus de "Prénoms".
    fields = parse_provisional_receipt_fields(REAL_OCR_EXCERPT)
    assert fields.lastName is not None
    assert "KAMDEM" in fields.lastName


def test_real_ocr_excerpt_does_not_confuse_mother_line_with_last_name():
    # Régression : "Nom de la Mère" lu "Dai | Nom 63 ta Meee..." ne doit pas être pris pour le nom
    # de famille (contient "NOM" mais n'est pas le bon champ).
    fields = parse_provisional_receipt_fields(REAL_OCR_EXCERPT)
    assert fields.lastName is not None
    assert "TOUKAM" not in fields.lastName
    assert "MEEE" not in fields.lastName


def test_real_ocr_excerpt_kit_number_prefers_tight_match_over_noisy_spaced_one():
    # Régression : le texte contient à la fois "Kit 34" (bruit OCR) et "KIT328" (valeur réelle,
    # collée) — la forme collée doit être préférée.
    fields = parse_provisional_receipt_fields(REAL_OCR_EXCERPT)
    assert fields.kitNumber == "KIT328"


def test_real_ocr_excerpt_birthplace_found_after_birthdate():
    fields = parse_provisional_receipt_fields(REAL_OCR_EXCERPT)
    assert fields.birthPlace == "BAFOUSSAM"


def test_missing_fields_return_none_on_unrelated_text():
    fields = parse_provisional_receipt_fields("TEXTE SANS AUCUN RAPPORT AVEC UN TITRE PROVISOIRE")

    assert fields.lastName is None
    assert fields.firstName is None
    assert fields.kitNumber is None
    assert fields.requestIdentifier is None


def test_request_identifier_search_is_scoped_near_its_label():
    text = "IDENTIFIANT DEMANDE\nABCDEFGHIJ0123456\nAUTRE CHAMP SANS RAPPORT\nZZZZZZZZZZZZZZZZ999"
    fields = parse_provisional_receipt_fields(text)
    # Le premier token valide après le libellé doit être retenu, pas un token plus loin sans rapport.
    assert fields.requestIdentifier == "ABCDEFGHIJ0123456"
