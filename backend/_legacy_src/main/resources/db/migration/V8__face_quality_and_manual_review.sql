-- Score de qualité du selfie (Module 3, calculé au moment de la vérification faciale).
ALTER TABLE face_verification_results
    ADD COLUMN target_quality_score DOUBLE PRECISION;

-- Un dossier dont la qualité (document ou selfie) est en dessous du seuil configuré
-- (app.quality.manual-review-threshold) n'est plus bloqué : il est enregistré jusqu'au bout,
-- marqué pour révision manuelle par l'agence plutôt que rejeté ou approuvé automatiquement.
ALTER TABLE customers
    ADD COLUMN requires_manual_review BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN manual_review_reason TEXT;
