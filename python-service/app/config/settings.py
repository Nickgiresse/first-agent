from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="", extra="ignore")

    app_name: str = "vision-service"
    api_version: str = "v1"

    # Sécurité : seul Spring Boot doit pouvoir appeler ce service.
    internal_api_key: str = "changeme-local-dev-key"

    # --- Module 1 : qualité du document ---
    min_blur_variance: float = 100.0          # variance du Laplacien ; en dessous = flou
    min_brightness: float = 60.0               # luminosité moyenne (0-255)
    max_brightness: float = 220.0
    glare_pixel_threshold: int = 245            # intensité à partir de laquelle un pixel est "saturé"
    glare_area_ratio_threshold: float = 0.03    # au-delà de 3% de pixels saturés -> reflet détecté
    min_resolution_width: int = 640
    min_resolution_height: int = 480
    min_document_area_ratio: float = 0.25       # le document doit occuper au moins 25% du cadre
    cni_aspect_ratio: float = 1.586             # format carte CR80 (largeur/hauteur)
    cni_aspect_ratio_tolerance: float = 0.15
    border_margin_ratio: float = 0.01           # marge (en % de l'image) sous laquelle on suspecte un rognage

    # --- Module 2 : OCR ---
    # "tesseract" fonctionne tel quel si le binaire est sur le PATH (cas Docker/Linux après
    # apt-get install tesseract-ocr). Sous Windows, définir TESSERACT_CMD dans .env si non installé
    # via l'option "add to PATH" (ex: C:\Program Files\Tesseract-OCR\tesseract.exe).
    tesseract_cmd: str = "tesseract"
    ocr_languages: str = "fra+eng"
    # Moteur d'extraction : "rapidocr" (défaut) ou "tesseract". RapidOCR lit
    # environ trois fois plus de texte sur les CNI camerounaises (607 caractères
    # contre 228, mesuré sur trois pièces réelles), ce qui change tout en aval :
    # avec Tesseract l'analyseur ne trouve ni nom, ni prénom, ni lieu de
    # naissance sur ces mêmes images.
    ocr_engine: str = "rapidocr"
    min_ocr_confidence: float = 40.0            # confiance moyenne (0-100) en dessous de laquelle on avertit

    # --- Module 3 : qualité du visage (selfie) ---
    min_face_confidence: float = 0.5            # confiance minimale du détecteur pour retenir une détection
    face_center_tolerance: float = 0.18         # écart max au centre (0-1 de l'image) pour être "centré"
    min_face_area_ratio: float = 0.05           # le visage doit occuper au moins 5% du cadre (pas trop loin)
    min_eye_distance_ratio: float = 0.15        # distance minimale œil-œil, relative à la largeur du visage

    # --- Module 4 : vivacité (challenge-response) ---
    # Seuils calibrés sur les blendshapes ARKit-52 de MediaPipe FaceLandmarker (0-1), vérifiées
    # empiriquement sur une vraie photo neutre (voir mémoire projet) mais pas encore sur de vrais
    # mouvements (clignement/sourire/rotation réels) — à ajuster au premier test réel.
    blink_score_closed: float = 0.5             # eyeBlinkLeft/Right au-dessus = yeux fermés
    blink_score_open: float = 0.3               # en dessous = yeux rouverts (pour détecter la transition)
    # 0.5 s'est révélé trop strict lors du premier test réel (sourire webcam légitime jamais détecté
    # comme complété) ; abaissé à 0.3, encore nettement au-dessus d'un visage neutre (~0.0-0.1).
    smile_score_threshold: float = 0.3          # mouthSmileLeft/Right au-dessus = sourire détecté
    head_turn_threshold_degrees: float = 15.0   # écart de lacet (yaw) vs position neutre pour valider un tour de tête
    head_pitch_threshold_degrees: float = 12.0  # écart de tangage (pitch) vs position neutre pour lever/baisser la tête
    min_face_detection_confidence_liveness: float = 0.5
    liveness_session_ttl_seconds: int = 300     # durée de vie d'une session avant expiration
    liveness_challenge_action_count: int = 3    # nombre d'actions demandées par session

    # --- Module 5 : comparaison biométrique (ArcFace) ---
    # 0.4 = seuil de départ recommandé par la doc officielle InsightFace pour ce modèle (w600k_r50) ;
    # à ajuster sur un vrai jeu de paires (même personne / personnes différentes) avant production —
    # aucune paire réelle disponible pour calibrer pendant le développement.
    face_match_similarity_threshold: float = 0.4

    # Seuil de RATTACHEMENT au visage du défi de vivacité, distinct du seuil ci-dessus et
    # volontairement plus haut.
    #
    # Les deux questions ne sont pas la même. Comparer une photo de CNI (imprimée, ancienne,
    # rephotographiée) à un selfie tolère un écart important : 0,4. Comparer deux captures
    # webcam prises à quelques secondes d'intervalle, même caméra, même éclairage, ne le
    # tolère pas : en dessous de 0,6, ce n'est plus la même personne.
    #
    # Trop haut, ce seuil renverrait en agence des clients légitimes : à mesurer sur le
    # premier lot de parcours réels avant de le figer.
    liveness_binding_similarity_threshold: float = 0.6


@lru_cache
def get_settings() -> Settings:
    return Settings()
