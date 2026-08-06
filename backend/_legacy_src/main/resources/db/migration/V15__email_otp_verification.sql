-- Vérification de l'adresse e-mail par code OTP à l'étape KYC : l'email devient obligatoire et
-- doit être confirmé via un code envoyé par e-mail avant de pouvoir passer à la création du PIN.
-- pending_email/email_otp_* sont temporaires (purgés dès la vérification réussie) ; email (déjà
-- présent depuis V11) ne reçoit la valeur qu'une fois le code vérifié.
ALTER TABLE onboarding_sessions
    ADD COLUMN pending_email VARCHAR(150),
    ADD COLUMN email_otp_code_hash VARCHAR(255),
    ADD COLUMN email_otp_expires_at TIMESTAMP,
    ADD COLUMN email_otp_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN email_otp_last_sent_at TIMESTAMP;
