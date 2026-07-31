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


# Transcription propre (non bruitée) d'une vraie CNI définitive camerounaise (recto+verso),
# fournie par l'utilisateur le 2026-07-31 — première calibration de ce parseur sur un document
# réel (contrairement au titre provisoire/récépissé, voir provisional_receipt_parser.py et
# receipt_parser.py, qui disposaient déjà d'échantillons réels). A révélé 3 écarts corrigés dans
# cni_parser.py : libellés bilingues à matcher en entier, sexe partageant sa ligne avec la taille,
# et absence de tout libellé "N° CNI" (numéro nu, sans étiquette, au bas du verso).
REAL_CNI_TEXT = """
RÉPUBLIQUE DU CAMEROUN
REPUBLIC OF CAMEROON
CARTE NATIONALE D'IDENTITE
NATIONAL IDENTITY CARD
NOM/SURNAME
DONGMO DJOUAKA
PRÉNOMS/GIVEN NAMES
BRYAN
DATE DE NAISSANCE/DATE OF BIRTH
22.10.2005
LIEU DE NAISSANCE/PLACE OF BIRTH
DOUALA
SEXE/SEX          TAILLE/HEIGHT
M                 1,86
PROFESSION/OCCUPATION
ELEVE
PÈRE/FATHER
DJOUAKA ALAIN ROGER
MÈRE/MOTHER
KIFACK BOUKENG FLORENCE
S.P./S.M.
850000
ADRESSE/ADDRESS
DLA-BONAMOUSSAD
I 694749247
AUTORITÉ/AUTHORITY
DATE DE DÉLIVRANCE/DATE OF ISSUE
17.05.2022
DATE D'EXPIRATION/DATE OF EXPIRY
17.05.2032
POSTE D'IDENTIFICATION/IDENTIFICATION POST
CE01
IDENTIFIANT UNIQUE/UNIQUE IDENTIFIER
20220101224610554
Martin MBARGA NGUÉLÉ
CAMEROUN CAMEROON
500018807
"""


def test_real_cni_extracts_names_correctly():
    fields = parse_cni_fields(REAL_CNI_TEXT)
    assert fields.lastName == "DONGMO DJOUAKA"
    assert fields.firstName == "BRYAN"


def test_real_cni_extracts_sex_despite_sharing_line_with_height_label():
    # Régression : "SEXE/SEX" et "TAILLE/HEIGHT" sont sur la même ligne sur ce document réel ;
    # la valeur "M" est sur la ligne suivante, mélangée à la valeur de la taille.
    fields = parse_cni_fields(REAL_CNI_TEXT)
    assert fields.sex == "M"


def test_real_cni_extracts_birth_date_and_place():
    fields = parse_cni_fields(REAL_CNI_TEXT)
    assert fields.birthDate == date(2005, 10, 22)
    assert fields.birthPlace == "DOUALA"


def test_real_cni_extracts_expiry_date_from_bilingual_label():
    fields = parse_cni_fields(REAL_CNI_TEXT)
    assert fields.expiryDate == date(2032, 5, 17)


def test_real_cni_document_number_prefers_standalone_line_over_other_digit_sequences():
    # Régression : le texte contient aussi "694749247" (numéro d'adresse) et "850000" (S.P./S.M.),
    # qui apparaissent avant le vrai numéro de carte "500018807" dans le flux de texte — sans le
    # correctif, la recherche naïve du premier nombre à 9-12 chiffres retournait le mauvais numéro.
    fields = parse_cni_fields(REAL_CNI_TEXT)
    assert fields.documentNumber == "500018807"
