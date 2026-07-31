-- Numéro de téléphone porté par la session d'onboarding.
--
-- Le parcours est ouvert par le bot WhatsApp, qui connaît déjà le numéro de
-- son interlocuteur. Ce numéro accompagne la session de bout en bout, sans
-- jamais être saisi ni modifiable depuis le navigateur : un client ne peut
-- donc pas s'inscrire au nom d'un autre numéro.
--
-- Nullable à dessein : les sessions ouvertes directement depuis le web, sans
-- passer par le bot, restent possibles et laissent la colonne vide. Le client
-- créé au bout d'une telle session n'a pas de téléphone, exactement comme
-- avant cette migration.
ALTER TABLE onboarding_sessions
    ADD COLUMN phone_number VARCHAR(20);

-- Une invitation en cours par numéro : deux parcours simultanés pour le même
-- client relèvent d'une erreur, ou d'une tentative de contournement.
CREATE INDEX IF NOT EXISTS idx_onboarding_sessions_phone_number
    ON onboarding_sessions (phone_number);

-- Numéro déclaré par le référentiel bancaire pour ce compte.
--
-- Sert au contrôle d'appartenance : le numéro qui réalise l'onboarding doit
-- être celui enregistré sur le compte. Sans ce contrôle, quiconque connaît un
-- RIB peut ouvrir l'accès au service sur le compte d'un tiers depuis son
-- propre WhatsApp.
--
-- ATTENTION, source de vérité à trancher avec la DSI : le core banking AIF
-- tel qu'intégré aujourd'hui n'expose PAS le téléphone (getAccountDetail
-- renvoie un champ vide, et le contrat SOAP utilisé n'en comporte aucun).
-- Cette colonne est donc alimentée par la synchronisation du référentiel, à
-- la manière de la table miroir du bot. Si une opération AIF permettant de
-- récupérer le téléphone d'un client existe, c'est elle qui devra faire foi.
ALTER TABLE bank_accounts
    ADD COLUMN phone_number VARCHAR(20);
