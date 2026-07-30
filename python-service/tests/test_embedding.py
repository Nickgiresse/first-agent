import numpy as np

from app.verification.embedding import extract_embedding


def test_extract_embedding_returns_512_dimensional_vector():
    aligned_face = np.random.default_rng(0).integers(0, 255, size=(112, 112, 3), dtype=np.uint8)
    embedding = extract_embedding(aligned_face)

    assert embedding.shape == (512,)
    assert embedding.dtype in (np.float32, np.float64)


def test_extract_embedding_is_deterministic_for_the_same_input():
    aligned_face = np.random.default_rng(1).integers(0, 255, size=(112, 112, 3), dtype=np.uint8)
    first = extract_embedding(aligned_face)
    second = extract_embedding(aligned_face)

    assert np.array_equal(first, second)
