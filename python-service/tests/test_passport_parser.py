from datetime import date

from app.ocr.passport_parser import parse_passport_fields

# Exemple de référence officiel ICAO 9303 (partie 3), utilisé tel quel pour vérifier que la
# validation des chiffres de contrôle fonctionne correctement de bout en bout.
VALID_MRZ_LINE_1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
VALID_MRZ_LINE_2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"


def test_parses_all_fields_from_valid_reference_mrz():
    text = f"REPUBLIQUE\nPASSEPORT\n{VALID_MRZ_LINE_1}\n{VALID_MRZ_LINE_2}\n"
    fields = parse_passport_fields(text)

    assert fields.mrzFound is True
    assert fields.mrzValid is True
    assert fields.checkDigitFailures == []
    assert fields.lastName == "ERIKSSON"
    assert fields.firstName == "ANNA MARIA"
    assert fields.documentNumber == "L898902C3"
    assert fields.nationality == "UTO"
    assert fields.sex == "F"
    assert fields.birthDate == date(1974, 8, 12)
    assert fields.expiryDate == date(2012, 4, 15)


def test_flags_tampered_document_number_as_invalid():
    # Le numéro de document est modifié sans recalculer son chiffre de contrôle : doit être
    # détecté comme invalide plutôt que silencieusement accepté.
    tampered_line_2 = "L898902C99UTO7408122F1204159ZE184226B<<<<<10"
    text = f"{VALID_MRZ_LINE_1}\n{tampered_line_2}\n"
    fields = parse_passport_fields(text)

    assert fields.mrzFound is True
    assert fields.mrzValid is False
    assert "documentNumber" in fields.checkDigitFailures


def test_no_fields_returned_when_mrz_absent():
    # Pas de MRZ exploitable : on ne devine rien, tout reste absent (voir Étape 6/7 — jamais de
    # valeur renvoyée comme sûre en silence).
    fields = parse_passport_fields("UN DOCUMENT SANS RAPPORT AVEC UN PASSEPORT")

    assert fields.mrzFound is False
    assert fields.mrzValid is False
    assert fields.lastName is None
    assert fields.documentNumber is None


def test_tolerates_ocr_noise_around_mrz_lines():
    # Espaces parasites et texte avant/après les deux lignes MRZ, comme produirait un vrai OCR.
    text = f"  RAS  \n{VALID_MRZ_LINE_1}  \n   {VALID_MRZ_LINE_2}\nFIN DE PAGE\n"
    fields = parse_passport_fields(text)

    assert fields.mrzFound is True
    assert fields.mrzValid is True
    assert fields.documentNumber == "L898902C3"


def test_pads_short_line_with_filler_before_parsing():
    # Une ligne MRZ tronquée par l'OCR (caractère de fin perdu) est complétée avec "<" plutôt que
    # rejetée d'emblée — le chiffre de contrôle global échouera naturellement si le contenu manque
    # réellement, plutôt que de planter sur une chaîne de mauvaise longueur.
    short_line_2 = VALID_MRZ_LINE_2[:-2]  # 42 caractères au lieu de 44
    text = f"{VALID_MRZ_LINE_1}\n{short_line_2}\n"
    fields = parse_passport_fields(text)

    assert fields.mrzFound is True
    # Le contenu réel manque : le chiffre de contrôle global ne peut plus être valide.
    assert fields.mrzValid is False
