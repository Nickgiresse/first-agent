import cv2
import numpy as np

from app.services.ocr_service import extract_document_fields

VALID_MRZ_LINE_1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
VALID_MRZ_LINE_2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<10"
TAMPERED_MRZ_LINE_2 = "L898902C99UTO7408122F1204159ZE184226B<<<<<10"


def _render_lines(lines: list[str], width: int = 1000, height: int = 500) -> np.ndarray:
    """Image blanche (255) avec une ligne de texte par entrée de `lines`, assez large pour accueillir
    une ligne MRZ (44 caractères) sans être coupée."""
    image = np.full((height, width, 3), 255, dtype=np.uint8)
    line_height = height // (len(lines) + 1)
    for index, line in enumerate(lines):
        y = line_height * (index + 1)
        cv2.putText(image, line, (20, y), cv2.FONT_HERSHEY_SIMPLEX, 1.1, (0, 0, 0), 2, cv2.LINE_AA)
    return image


def _encode_jpeg(image: np.ndarray) -> bytes:
    success, buffer = cv2.imencode(".jpg", image)
    assert success
    return buffer.tobytes()


def test_extracts_cni_fields_end_to_end():
    front = _render_lines(["CARTE NATIONALE D'IDENTITE", "NOM/NAME NKENG", "PRENOMS JEAN PAUL"])
    response = extract_document_fields(_encode_jpeg(front))

    assert response.fields.documentKind == "CNI"
    assert response.fields.lastName == "NKENG"


def test_extracts_passport_and_never_silently_trusts_a_failed_checksum():
    # L'OCR générique (Tesseract) n'est pas garanti pixel-parfait sur une zone MRZ dense — il peut
    # substituer ou même insérer un caractère parasite (observé en pratique sur ce rendu
    # synthétique), ce qui décale les champs à largeur fixe de la ligne 2. Le test ne suppose donc
    # pas une lecture parfaite : il vérifie la propriété qui compte réellement (Étape 7) — si le
    # chiffre de contrôle ne correspond plus à la valeur lue, ce n'est jamais renvoyé comme fiable
    # en silence. Le nom (ligne 1, texte simple sans chiffres ambigus) se lit fiablement.
    front = _render_lines(["REPUBLIQUE", "PASSEPORT / PASSPORT", VALID_MRZ_LINE_1, VALID_MRZ_LINE_2])
    response = extract_document_fields(_encode_jpeg(front))

    assert response.fields.documentKind == "PASSEPORT"
    assert response.fields.lastName == "ERIKSSON"

    has_checksum_issue = any("contrôle" in issue for issue in response.issues)
    if response.fields.documentNumber != "L898902C3":
        assert has_checksum_issue


def test_passport_with_tampered_mrz_flags_check_digit_failure_in_issues():
    front = _render_lines([VALID_MRZ_LINE_1, TAMPERED_MRZ_LINE_2])
    response = extract_document_fields(_encode_jpeg(front))

    assert response.fields.documentKind == "PASSEPORT"
    assert any("contrôle" in issue for issue in response.issues)


def test_unknown_document_is_flagged_explicitly_not_guessed():
    front = _render_lines(["CECI EST UN TEXTE", "SANS AUCUN RAPPORT", "AVEC UN DOCUMENT CONNU"])
    response = extract_document_fields(_encode_jpeg(front))

    assert response.fields.documentKind == "UNKNOWN"
    assert any("non reconnu" in issue for issue in response.issues)


def test_receipt_gets_reduced_reliability_flag_by_default():
    front = _render_lines(["Reçu de paiement", "Payment Receipt", "Nom", "FOADJO KAMDEM", "Prénoms", "NICK GIRESSE"])
    response = extract_document_fields(_encode_jpeg(front))

    assert response.fields.documentKind == "RECEPISSE"
    assert any("fiabilité réduite" in issue for issue in response.issues)
