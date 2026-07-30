import numpy as np
import pytest

from app.verification.alignment import ALIGNED_SIZE, align_face


def _sample_image_and_points():
    image = np.random.default_rng(0).integers(0, 255, size=(200, 200, 3), dtype=np.uint8)
    # Points plausibles pour un visage centré dans l'image (œil-A, œil-B, nez, bouche-A, bouche-B).
    points = np.array(
        [
            [70, 90],
            [130, 90],
            [100, 120],
            [75, 150],
            [125, 150],
        ],
        dtype=np.float32,
    )
    return image, points


def test_align_face_returns_expected_size():
    image, points = _sample_image_and_points()
    aligned = align_face(image, points)
    assert aligned.shape == (ALIGNED_SIZE, ALIGNED_SIZE, 3)


def test_align_face_rejects_wrong_point_count():
    image, _ = _sample_image_and_points()
    with pytest.raises(ValueError):
        align_face(image, np.zeros((4, 2), dtype=np.float32))


def test_alignment_is_robust_to_input_point_order_within_pairs():
    image, points = _sample_image_and_points()
    swapped = points.copy()
    swapped[[0, 1]] = swapped[[1, 0]]  # yeux inversés
    swapped[[3, 4]] = swapped[[4, 3]]  # coins de bouche inversés

    aligned_original = align_face(image, points)
    aligned_swapped = align_face(image, swapped)

    # Même transformation résultante malgré l'ordre différent en entrée : la réassignation par
    # position doit neutraliser la permutation.
    assert np.array_equal(aligned_original, aligned_swapped)
