import numpy as np

from app.liveness.landmarker import _rotation_matrix_to_euler_degrees

# Matrice réelle capturée sur l'image de test officielle MediaPipe (portrait.jpg, pose neutre
# face à la caméra) — sert de régression pour la décomposition en angles.
REAL_NEUTRAL_POSE_MATRIX = np.array(
    [
        [9.9939078e-01, -1.3315415e-02, 3.2268267e-02, -4.1232702e-01],
        [1.5467605e-02, 9.9760669e-01, -6.7391329e-02, 2.2463902e01],
        [-3.1293705e-02, 6.7849420e-02, 9.9720496e-01, -6.5447052e01],
        [0.0000000e00, 0.0000000e00, 0.0000000e00, 1.0000000e00],
    ]
)


def test_identity_matrix_gives_exactly_zero_angles():
    yaw, pitch, roll = _rotation_matrix_to_euler_degrees(np.eye(4))

    assert yaw == 0.0
    assert pitch == 0.0
    assert roll == 0.0


def test_neutral_pose_gives_near_zero_angles_on_all_axes():
    # Ne vérifie PAS quel axe correspond à quel mouvement physique (non confirmé, voir
    # landmarker.py) : seulement que la décomposition reste stable et proche de zéro sur une
    # pose réelle quasi frontale, sur les 3 angles.
    yaw, pitch, roll = _rotation_matrix_to_euler_degrees(REAL_NEUTRAL_POSE_MATRIX)

    assert abs(yaw) < 5
    assert abs(pitch) < 5
    assert abs(roll) < 5


def test_decomposition_is_deterministic():
    result_a = _rotation_matrix_to_euler_degrees(REAL_NEUTRAL_POSE_MATRIX)
    result_b = _rotation_matrix_to_euler_degrees(REAL_NEUTRAL_POSE_MATRIX)
    assert result_a == result_b
