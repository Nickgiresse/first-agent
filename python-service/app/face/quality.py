from dataclasses import dataclass

import numpy as np

from app.config.settings import Settings
from app.face.detector import LEFT_EYE, MOUTH, NOSE_TIP, RIGHT_EYE, FaceKeypoint, detect_faces


@dataclass
class FaceQualityResult:
    faceDetected: bool
    faceCount: int
    singleFace: bool
    centered: bool
    eyesDetected: bool
    noseDetected: bool
    mouthDetected: bool
    offsetX: float
    offsetY: float
    faceAreaRatio: float


def analyze_face(image_bgr: np.ndarray, settings: Settings) -> FaceQualityResult:
    faces = [f for f in detect_faces(image_bgr) if f.confidence >= settings.min_face_confidence]
    face_count = len(faces)

    if face_count == 0:
        return FaceQualityResult(
            faceDetected=False,
            faceCount=0,
            singleFace=False,
            centered=False,
            eyesDetected=False,
            noseDetected=False,
            mouthDetected=False,
            offsetX=0.0,
            offsetY=0.0,
            faceAreaRatio=0.0,
        )

    # S'il y a plusieurs visages, on analyse le plus grand (le plus proche de l'appareil) pour
    # donner un retour utile, mais singleFace reste False pour bloquer la suite du pipeline.
    face = max(faces, key=lambda f: f.width * f.height)
    height, width = image_bgr.shape[:2]
    image_area = width * height

    face_area_ratio = (face.width * face.height) / image_area if image_area else 0.0
    face_width_normalized = face.width / width if width else 0.0
    face_center_x = (face.x + face.width / 2) / width
    face_center_y = (face.y + face.height / 2) / height
    offset_x = face_center_x - 0.5
    offset_y = face_center_y - 0.5
    centered = (
        abs(offset_x) <= settings.face_center_tolerance
        and abs(offset_y) <= settings.face_center_tolerance
        and face_area_ratio >= settings.min_face_area_ratio
    )

    eyes_detected = _eyes_plausible(face.keypoints, face_width_normalized, settings)
    nose_detected = _nose_plausible(face.keypoints)
    mouth_detected = _mouth_plausible(face.keypoints)

    return FaceQualityResult(
        faceDetected=True,
        faceCount=face_count,
        singleFace=face_count == 1,
        centered=centered,
        eyesDetected=eyes_detected,
        noseDetected=nose_detected,
        mouthDetected=mouth_detected,
        offsetX=round(offset_x, 4),
        offsetY=round(offset_y, 4),
        faceAreaRatio=round(face_area_ratio, 4),
    )


def _eyes_plausible(keypoints: list[FaceKeypoint], face_width_normalized: float, settings: Settings) -> bool:
    if len(keypoints) <= max(LEFT_EYE, RIGHT_EYE):
        return False
    left_eye, right_eye = keypoints[LEFT_EYE], keypoints[RIGHT_EYE]
    eye_distance = abs(left_eye.x - right_eye.x)
    vertical_diff = abs(left_eye.y - right_eye.y)
    # Yeux suffisamment écartés l'un de l'autre relativement à la taille du visage (visage pas
    # trop de profil) et à peu près à la même hauteur (tête pas trop inclinée) : deux garde-fous
    # géométriques simples plutôt qu'une simple vérification de présence des coordonnées.
    min_distance = settings.min_eye_distance_ratio * face_width_normalized
    return eye_distance >= min_distance and vertical_diff < eye_distance


def _nose_plausible(keypoints: list[FaceKeypoint]) -> bool:
    if len(keypoints) <= max(LEFT_EYE, RIGHT_EYE, NOSE_TIP):
        return False
    nose = keypoints[NOSE_TIP]
    left_eye, right_eye = keypoints[LEFT_EYE], keypoints[RIGHT_EYE]
    eyes_y = (left_eye.y + right_eye.y) / 2
    # Le nez doit être sous la ligne des yeux (coordonnées image standard, y croissant vers le bas).
    return nose.y > eyes_y


def _mouth_plausible(keypoints: list[FaceKeypoint]) -> bool:
    if len(keypoints) <= max(MOUTH, NOSE_TIP):
        return False
    mouth = keypoints[MOUTH]
    nose = keypoints[NOSE_TIP]
    return mouth.y > nose.y
