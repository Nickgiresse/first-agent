import cv2
import numpy as np

from app.config.settings import Settings
from app.vision import quality


def test_blur_score_higher_for_sharp_image(sharp_noise_image, blurry_image):
    sharp_gray = cv2.cvtColor(sharp_noise_image, cv2.COLOR_BGR2GRAY)
    blurry_gray = cv2.cvtColor(blurry_image, cv2.COLOR_BGR2GRAY)
    assert quality.compute_blur_score(sharp_gray) > quality.compute_blur_score(blurry_gray)


def test_brightness_reflects_image_tone(dark_image, bright_image):
    dark_gray = cv2.cvtColor(dark_image, cv2.COLOR_BGR2GRAY)
    bright_gray = cv2.cvtColor(bright_image, cv2.COLOR_BGR2GRAY)
    assert quality.compute_brightness(dark_gray) < 50
    assert quality.compute_brightness(bright_gray) > 200


def test_glare_detected_on_saturated_patch(glare_image):
    settings = Settings()
    gray = cv2.cvtColor(glare_image, cv2.COLOR_BGR2GRAY)
    detected, ratio = quality.detect_glare(gray, settings)
    assert detected is True
    assert ratio > settings.glare_area_ratio_threshold


def test_no_glare_on_uniform_midtone_image(blurry_image):
    settings = Settings()
    gray = cv2.cvtColor(blurry_image, cv2.COLOR_BGR2GRAY)
    detected, _ = quality.detect_glare(gray, settings)
    assert detected is False


def test_resolution_check_passes_for_sufficient_size(sharp_noise_image):
    settings = Settings()
    ok, width, height = quality.check_resolution(sharp_noise_image, settings)
    assert ok is True
    assert (width, height) == (800, 600)


def test_resolution_check_fails_on_small_image():
    settings = Settings()
    small_image = np.zeros((100, 100, 3), dtype=np.uint8)
    ok, _, _ = quality.check_resolution(small_image, settings)
    assert ok is False
