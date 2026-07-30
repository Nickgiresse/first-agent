from datetime import date

from app.ocr.receipt_parser import parse_receipt_fields

# Transcription manuelle du récépissé de paiement (reçu remis au dépôt de la demande de CNI, avant
# le titre provisoire) — pas encore d'échantillon dérivé d'une vraie sortie OCR bruitée pour ce
# document précis (voir provisional_receipt_parser.py pour l'équivalent avec échantillon réel).
CLEAN_SAMPLE_TEXT = """
Reçu de paiement Payment Receipt
Identifiant demande / Request identifiant
OU36328I5J3N7CQPJJ85
PI/IC OU36 - PI BALENG
Numéro Kit / Kit id KIT328
Type de titre CNI - Carte Nationale d'Identité
Catégorie Carte Nationale d'Identité
Nom
FOADJO KAMDEM
Prénoms
NICK GIRESSE
Montant perçu 2800 FCFA
Date de paiement 13.07.2022
"""


def test_parses_clean_text_end_to_end():
    fields = parse_receipt_fields(CLEAN_SAMPLE_TEXT)

    assert fields.lastName == "FOADJO KAMDEM"
    assert fields.firstName == "NICK GIRESSE"
    assert fields.kitNumber == "KIT328"
    assert fields.requestIdentifier == "OU36328I5J3N7CQPJJ85"
    assert fields.paymentAmount == "2800"
    assert fields.paymentDate == date(2022, 7, 13)


def test_amount_falls_back_to_currency_proximity_when_label_not_matched():
    text = "Reçu de paiement\nPrénoms\nJEAN\nblahblah 2800 FCFA blahblah"
    fields = parse_receipt_fields(text)
    assert fields.paymentAmount == "2800"


def test_missing_fields_return_none_on_unrelated_text():
    fields = parse_receipt_fields("TEXTE SANS AUCUN RAPPORT AVEC UN RECEPISSE")

    assert fields.lastName is None
    assert fields.firstName is None
    assert fields.kitNumber is None
    assert fields.requestIdentifier is None
    assert fields.paymentAmount is None
    assert fields.paymentDate is None


def test_request_identifier_search_is_scoped_near_its_label():
    text = "IDENTIFIANT DEMANDE\nABCDEFGHIJ0123456\nAUTRE CHAMP SANS RAPPORT\nZZZZZZZZZZZZZZZZ999"
    fields = parse_receipt_fields(text)
    assert fields.requestIdentifier == "ABCDEFGHIJ0123456"
