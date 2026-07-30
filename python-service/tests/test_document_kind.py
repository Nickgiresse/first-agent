from app.ocr.document_kind import DocumentKind, detect_document_kind


def test_detects_provisional_receipt():
    text = "Titre d'identité provisoire\nTemporary identity document"
    assert detect_document_kind(text) == DocumentKind.TITRE_PROVISOIRE


def test_detects_cni():
    text = "CARTE NATIONALE D'IDENTITE\nNOM/NAME NKENG"
    assert detect_document_kind(text) == DocumentKind.CNI


def test_unknown_when_no_marker_present():
    assert detect_document_kind("TEXTE QUELCONQUE SANS RAPPORT") == DocumentKind.UNKNOWN


def test_provisional_marker_takes_precedence_over_cni_marker():
    # Le titre provisoire mentionne aussi "Carte Nationale d'Identité" dans son champ
    # "Type de titre" : le marqueur provisoire, plus spécifique, doit l'emporter.
    text = "Titre d'identité provisoire\nType de titre CNI - Carte Nationale d'Identité"
    assert detect_document_kind(text) == DocumentKind.TITRE_PROVISOIRE


def test_detects_receipt():
    text = "Reçu de paiement\nPayment Receipt"
    assert detect_document_kind(text) == DocumentKind.RECEPISSE


def test_receipt_marker_takes_precedence_over_cni_marker():
    # Comme le titre provisoire, le récépissé mentionne aussi "Carte Nationale d'Identité" dans
    # ses champs "Type de titre"/"Catégorie" : le marqueur récépissé, plus spécifique, doit l'emporter.
    text = "Reçu de paiement\nType de titre CNI - Carte Nationale d'Identité\nCatégorie Carte Nationale d'Identité"
    assert detect_document_kind(text) == DocumentKind.RECEPISSE
