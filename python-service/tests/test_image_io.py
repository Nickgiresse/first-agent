import io

import numpy as np
import pytest
from PIL import Image

from app.utils.image_io import InvalidImageError, decode_image


def test_decodes_jpeg(sharp_noise_image, encode_jpeg):
    image = decode_image(encode_jpeg(sharp_noise_image))
    assert image.shape[:2] == sharp_noise_image.shape[:2]


def test_decodes_avif_via_pillow_fallback():
    pil_image = Image.new("RGB", (120, 80), color=(200, 50, 50))
    buffer = io.BytesIO()
    pil_image.save(buffer, format="AVIF")

    image = decode_image(buffer.getvalue())

    assert image.shape[:2] == (80, 120)
    # Pillow charge en RGB, on convertit en BGR : le rouge dominant doit finir sur le canal B->R inversé.
    assert image[0, 0, 2] > image[0, 0, 0]  # canal rouge (BGR index 2) > canal bleu (index 0)


def test_rejects_empty_file():
    with pytest.raises(InvalidImageError):
        decode_image(b"")


def test_rejects_corrupted_bytes():
    with pytest.raises(InvalidImageError):
        decode_image(b"not an image at all")


def test_rejects_oversized_file():
    oversized = b"\x00" * (8 * 1024 * 1024 + 1)
    with pytest.raises(InvalidImageError):
        decode_image(oversized)
