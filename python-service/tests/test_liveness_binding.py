"""Rattachement du défi de vivacité au visage réellement comparé.

Sans ce rattachement, « le défi a réussi » et « le selfie correspond à la pièce » sont deux
faits établis séparément : une personne peut jouer les actions devant la caméra et une autre
fournir le selfie. Ces tests fixent le comportement attendu de la liaison.

Aucun modèle n'est nécessaire : les empreintes sont des vecteurs fabriqués et le pipeline
d'inférence est remplacé. Ce qui est vérifié ici, c'est la DÉCISION, pas la qualité de
l'inférence biométrique.
"""

from types import SimpleNamespace

import numpy as np
import pytest

from app.config.settings import Settings
from app.liveness.challenge import ACTIONS_DEFORMANTES, generate_challenge
from app.liveness.errors import FaceChangedError
from app.liveness.session_store import create_session, drop_session, get_session
from app.services import liveness_service, verification_service


def _visage(valeur: float, dimensions: int = 512) -> np.ndarray:
    """Empreinte artificielle. Deux valeurs différentes donnent deux visages différents."""
    vecteur = np.zeros(dimensions, dtype=np.float32)
    vecteur[0] = 1.0
    vecteur[1] = valeur
    return vecteur


ALICE = _visage(0.0)
BOB = _visage(40.0)  # cosinus avec ALICE très inférieur au seuil de liaison


# --------------------------------------------------------------------------------------
# Tirage du défi
# --------------------------------------------------------------------------------------


def test_le_defi_comporte_toujours_une_action_deformante():
    """Un défi exclusivement rotatif se franchit en faisant pivoter une photo imprimée.

    Le tirage libre précédent en produisait un sur cinq. Deux cents tirages suffisent à
    faire échouer ce test s'il y en avait ne serait-ce qu'un pour cent.
    """
    for _ in range(200):
        actions = generate_challenge(3)
        assert any(action in ACTIONS_DEFORMANTES for action in actions), actions


def test_le_defi_respecte_le_nombre_demande_et_ne_repete_pas_une_action():
    for nombre in (1, 2, 3, 4):
        actions = generate_challenge(nombre)
        assert len(actions) == nombre
        assert len(set(actions)) == nombre


def test_la_position_de_l_action_deformante_varie():
    """Si elle était toujours en tête, il suffirait de présenter le vrai visage au début."""
    positions = set()
    for _ in range(200):
        actions = generate_challenge(3)
        positions.add(next(i for i, a in enumerate(actions) if a in ACTIONS_DEFORMANTES))
    assert len(positions) > 1


# --------------------------------------------------------------------------------------
# Mémorisation du visage pendant le défi
# --------------------------------------------------------------------------------------


@pytest.fixture
def session():
    s = create_session(["BLINK", "TURN_LEFT"], ttl_seconds=300)
    yield s
    drop_session(s.session_id)


def _rafale(n: int = 3):
    """Frames analysées avec un visage détecté, et leurs octets d'origine."""
    return [b"frame"] * n, [SimpleNamespace(faceDetected=True) for _ in range(n)]


def test_la_premiere_rafale_memorise_le_visage(session, monkeypatch):
    monkeypatch.setattr(verification_service, "embed_face", lambda raw, frame: ALICE)
    octets, frames = _rafale()

    liveness_service._lier_le_visage(session, octets, frames, Settings())

    assert session.embedding is not None
    assert np.allclose(session.embedding, ALICE)


def test_le_meme_visage_sur_la_rafale_suivante_ne_change_rien(session, monkeypatch):
    monkeypatch.setattr(verification_service, "embed_face", lambda raw, frame: ALICE)
    octets, frames = _rafale()
    liveness_service._lier_le_visage(session, octets, frames, Settings())

    liveness_service._lier_le_visage(session, octets, frames, Settings())

    assert np.allclose(session.embedding, ALICE)


def test_un_changement_de_visage_detruit_la_session(session, monkeypatch):
    """Deux personnes se relaient : l'une joue les actions, l'autre sera comparée."""
    monkeypatch.setattr(verification_service, "embed_face", lambda raw, frame: ALICE)
    octets, frames = _rafale()
    liveness_service._lier_le_visage(session, octets, frames, Settings())

    monkeypatch.setattr(verification_service, "embed_face", lambda raw, frame: BOB)
    with pytest.raises(FaceChangedError):
        liveness_service._lier_le_visage(session, octets, frames, Settings())

    # Détruite, et non simplement marquée en échec : la conserver laisserait au fraudeur
    # le bénéfice des actions déjà validées.
    assert get_session(session.session_id) is None


def test_une_rafale_sans_visage_ne_conclut_rien(session, monkeypatch):
    """L'absence de preuve n'est pas une preuve de fraude : on n'invente pas de liaison."""
    monkeypatch.setattr(verification_service, "embed_face", lambda raw, frame: ALICE)
    frames = [SimpleNamespace(faceDetected=False) for _ in range(3)]

    liveness_service._lier_le_visage(session, [b"f"] * 3, frames, Settings())

    assert session.embedding is None


def test_une_empreinte_incalculable_n_interrompt_pas_le_defi(session, monkeypatch):
    def echoue(raw, frame):
        raise ValueError("alignement impossible")

    monkeypatch.setattr(verification_service, "embed_face", echoue)
    octets, frames = _rafale()

    liveness_service._lier_le_visage(session, octets, frames, Settings())

    assert session.embedding is None


# --------------------------------------------------------------------------------------
# Effet de la liaison sur la comparaison faciale
# --------------------------------------------------------------------------------------


def _preparer_comparaison(monkeypatch, empreinte_cible):
    monkeypatch.setattr(verification_service, "decode_image", lambda raw: raw)
    monkeypatch.setattr(
        verification_service, "analyze_frame", lambda image: SimpleNamespace(faceDetected=True)
    )
    # La source (CNI) et la cible (selfie) sont distinguées par leur contenu.
    monkeypatch.setattr(
        verification_service,
        "embed_face",
        lambda raw, frame: ALICE if raw == b"cni" else empreinte_cible,
    )


def test_sans_identifiant_de_session_la_reponse_ne_porte_aucune_liaison(monkeypatch):
    _preparer_comparaison(monkeypatch, ALICE)

    reponse = verification_service.compare_faces(b"cni", b"selfie")

    assert reponse.decision == "MATCH"
    assert reponse.liveness is None


def test_une_session_inconnue_donne_une_liaison_non_etablie(monkeypatch):
    _preparer_comparaison(monkeypatch, ALICE)

    reponse = verification_service.compare_faces(
        b"cni", b"selfie", liveness_session_id="session-inexistante"
    )

    assert reponse.liveness.bound is False
    assert reponse.liveness.samePerson is None
    assert reponse.liveness.reason


def test_le_selfie_du_defi_est_reconnu_comme_la_meme_personne(session, monkeypatch):
    session.embedding = ALICE
    _preparer_comparaison(monkeypatch, ALICE)

    reponse = verification_service.compare_faces(
        b"cni", b"selfie", liveness_session_id=session.session_id
    )

    assert reponse.liveness.bound is True
    assert reponse.liveness.samePerson is True
    assert reponse.decision == "MATCH"


def test_un_selfie_etranger_au_defi_est_refuse(session, monkeypatch):
    """Le coeur de la correction.

    La cible ressemble à la CNI (elle vaut donc MATCH sur le seul critère facial), mais ce
    n'est pas le visage qui a joué le défi. Détecter l'usurpation puis l'accepter n'aurait
    aucun sens : la décision est ramenée à NO_MATCH.
    """
    session.embedding = BOB
    _preparer_comparaison(monkeypatch, ALICE)

    reponse = verification_service.compare_faces(
        b"cni", b"selfie", liveness_session_id=session.session_id
    )

    assert reponse.liveness.bound is True
    assert reponse.liveness.samePerson is False
    assert reponse.decision == "NO_MATCH"
