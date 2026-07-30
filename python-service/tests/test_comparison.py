import numpy as np

from app.verification.comparison import cosine_similarity


def test_identical_vectors_have_similarity_one():
    vector = np.array([1.0, 2.0, 3.0, -4.0])
    assert abs(cosine_similarity(vector, vector) - 1.0) < 1e-9


def test_orthogonal_vectors_have_similarity_zero():
    a = np.array([1.0, 0.0])
    b = np.array([0.0, 1.0])
    assert abs(cosine_similarity(a, b)) < 1e-9


def test_opposite_vectors_have_similarity_minus_one():
    a = np.array([1.0, 2.0, 3.0])
    b = -a
    assert abs(cosine_similarity(a, b) - (-1.0)) < 1e-9


def test_similarity_is_scale_invariant():
    a = np.array([1.0, 2.0, 3.0])
    b = np.array([2.0, 4.0, 6.0])  # même direction, norme différente
    assert abs(cosine_similarity(a, b) - 1.0) < 1e-9
