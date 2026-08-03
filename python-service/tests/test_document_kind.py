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


def test_detects_passport_via_keyword():
    text = "REPUBLIQUE DU CAMEROUN\nPASSEPORT / PASSPORT\nNOM/SURNAME MBALLA"
    assert detect_document_kind(text) == DocumentKind.PASSEPORT


def test_detects_passport_via_mrz_lines_without_keyword():
    # Bas de page bio du passeport, sans le mot "PASSEPORT" lui-même (mal reconnu par l'OCR par
    # exemple) — seule la zone MRZ (2 lignes A-Z/0-9/<) doit suffire à classer en passeport.
    text = (
        "QUELQUES LIGNES DE TEXTE SANS RAPPORT\n"
        "P<CMRMBALLA<<JEAN<PAUL<<<<<<<<<<<<<<<<<<<<<<\n"
        "L898902C36CMR7408122M1204159<<<<<<<<<<<<<<08\n"
    )
    assert detect_document_kind(text) == DocumentKind.PASSEPORT


def test_single_mrz_like_line_is_not_enough():
    # Une seule ligne qui ressemble à de la MRZ ne suffit pas (la MRZ TD3 en a toujours deux) :
    # évite de classer par erreur un simple code-barres/référence en passeport.
    text = "IDENTIFIANT DEMANDE\nOU36328I5J3N7CQPJJ85<<<<<<<<<<<<<<<<<<<<<<<<\n"
    assert detect_document_kind(text) != DocumentKind.PASSEPORT


def test_passport_marker_checked_before_cni_marker():
    # Un passeport ne devrait jamais contenir "Carte Nationale d'Identité", mais on vérifie que
    # l'ordre de priorité ne fait pas basculer par erreur sur CNI si les deux marqueurs coexistaient.
    text = "PASSEPORT / PASSPORT\nCARTE NATIONALE D'IDENTITE (mention erronée)"
    assert detect_document_kind(text) == DocumentKind.PASSEPORT
