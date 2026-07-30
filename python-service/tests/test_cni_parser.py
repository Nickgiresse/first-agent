from datetime import date

from app.ocr.cni_parser import parse_cni_fields

SAMPLE_CNI_TEXT = """
REPUBLIQUE DU CAMEROUN
REPUBLIC OF CAMEROON
CARTE NATIONALE D'IDENTITE

NOM/NAME
NKENG
PRENOM(S)/GIVEN NAMES
JEAN PAUL
SEXE/SEX M
DATE DE NAISSANCE
12.05.1990
LIEU DE NAISSANCE
YAOUNDE
N° CNI
123456789
DATE D'EXPIRATION
12.05.2030
"""


def test_parses_all_fields_from_well_formed_text():
    fields = parse_cni_fields(SAMPLE_CNI_TEXT)

    assert fields.lastName == "NKENG"
    assert fields.firstName == "JEAN PAUL"
    assert fields.sex == "M"
    assert fields.documentNumber == "123456789"
    assert fields.birthPlace == "YAOUNDE"
    assert fields.birthDate == date(1990, 5, 12)
    assert fields.expiryDate == date(2030, 5, 12)


def test_missing_fields_return_none():
    fields = parse_cni_fields("TEXTE ILLISIBLE SANS AUCUN LIBELLE RECONNAISSABLE")

    assert fields.lastName is None
    assert fields.firstName is None
    assert fields.documentNumber is None
    assert fields.birthDate is None


def test_document_number_found_without_explicit_label_via_digit_fallback():
    text = "DOCUMENT ABIME\n987654321\nFIN"
    fields = parse_cni_fields(text)

    assert fields.documentNumber == "987654321"


def test_dates_fallback_to_earliest_and_latest_when_labels_not_recognized():
    text = "12.05.1990 QUELQUE CHOSE 12.05.2030"
    fields = parse_cni_fields(text)

    assert fields.birthDate == date(1990, 5, 12)
    assert fields.expiryDate == date(2030, 5, 12)


def test_two_digit_year_is_expanded_using_pivot_rule():
    text = "DATE DE NAISSANCE\n05.03.85"
    fields = parse_cni_fields(text)

    assert fields.birthDate == date(1985, 3, 5)


def test_value_on_same_line_as_label_is_captured():
    text = "SEXE/SEX: F"
    fields = parse_cni_fields(text)

    assert fields.sex == "F"

    text_lastname = "NOM/NAME: MBALLA"
    assert parse_cni_fields(text_lastname).lastName == "MBALLA"
