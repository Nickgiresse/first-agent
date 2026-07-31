from app.ocr.engine import extract_text, extract_words
from app.ocr.preprocessing import preprocess_for_ocr


def test_extract_text_reads_simple_printed_text(text_image_factory):
    image = text_image_factory("NKENG")
    preprocessed = preprocess_for_ocr(image)

    text = extract_text(preprocessed)

    assert "NKENG" in text.upper()


def test_extract_words_returns_confidence_scores(text_image_factory):
    image = text_image_factory("JEAN")
    preprocessed = preprocess_for_ocr(image)

    words = extract_words(preprocessed)

    assert len(words) >= 1
    assert all(0 <= w.confidence <= 100 for w in words)
    assert any("JEAN" in w.text.upper() for w in words)


def test_extract_text_reads_text_over_gradient_and_guilloche_pattern(patterned_text_image_factory):
    # Reproduit (en synthétique) le problème signalé sur les vraies CNI camerounaises : bandes de
    # couleur en dégradé + fond guilloché derrière le texte (voir _flatten_background dans
    # preprocessing.py). Ne remplace pas un vrai test sur une photo réelle dégradée.
    image = patterned_text_image_factory("NKENG")
    preprocessed = preprocess_for_ocr(image)

    text = extract_text(preprocessed)

    assert "NKENG" in text.upper()
