-- =========================================================
-- Table : audit_log
-- Journal forensique scellé. Deux garanties que la simple
-- journalisation applicative ne donne pas :
--
--   1. IMPUTABILITÉ. Chaque entrée porte un acteur explicite.
--      Le seul numéro du client ne dit pas QUI a agi : une
--      opération passée au back-office porte ce numéro sans
--      être le geste du client.
--
--   2. INTÉGRITÉ DÉMONTRABLE. Chaque entrée est scellée par un
--      HMAC-SHA256 couvrant son contenu ET l'empreinte de
--      l'entrée précédente. Supprimer, insérer, modifier ou
--      réordonner rompt la chaîne au point touché.
-- =========================================================
CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Horodatage en UTC. La colonne est le pivot du chaînage :
    -- l'ordre (timestamp, id) définit la suite des maillons.
    timestamp TIMESTAMP NOT NULL DEFAULT now(),

    phone VARCHAR(32) NOT NULL DEFAULT '',
    event_type VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',

    -- Qui a agi, et à quel titre.
    actor VARCHAR(128) NOT NULL,
    actor_type VARCHAR(16) NOT NULL DEFAULT 'SYSTEM'
        CHECK (actor_type IN ('CLIENT', 'ADMIN', 'SYSTEM')),
    source_ip VARCHAR(64) NOT NULL DEFAULT '',

    amount NUMERIC(19, 2),
    currency VARCHAR(8),
    reference VARCHAR(128),
    details TEXT NOT NULL DEFAULT '',

    -- Maillons de la chaîne. prev_hash vaut 'GENESE' pour la
    -- première entrée : une valeur explicite plutôt qu'un NULL,
    -- qui se confondrait avec « non renseigné ».
    prev_hash VARCHAR(64) NOT NULL DEFAULT 'GENESE',

    -- Nullable à dessein : une entrée écrite avant le scellement
    -- n'en a pas. La vérification les compte à part plutôt que
    -- de les traiter comme des ruptures.
    entry_hash VARCHAR(64)
);

-- Index composite sur l'ordre exact du chaînage.
--
-- Il n'est pas confortable mais nécessaire : la vérification
-- parcourt le journal par pagination sur clé (timestamp, id), et
-- non par OFFSET dont le coût croît avec la profondeur. Sans cet
-- index, chaque page redevient un tri complet, et le débit de
-- vérification s'effondre.
CREATE INDEX idx_audit_log_chaine ON audit_log(timestamp, id);

-- Recherche par client et par nature d'événement, usage courant
-- du back-office.
CREATE INDEX idx_audit_log_phone ON audit_log(phone, timestamp DESC);
CREATE INDEX idx_audit_log_event ON audit_log(event_type, timestamp DESC);


-- =========================================================
-- Garde-fou d'immuabilité
--
-- Dire d'une table qu'elle est « immuable » est une intention
-- tant que rien ne l'empêche. Le chaînage HMAC rend une
-- falsification DÉTECTABLE, il ne la rend pas IMPOSSIBLE, et il
-- ne dit rien tant que personne ne lance la vérification.
--
-- Ces deux déclencheurs ferment la porte en amont :
--
--   - la suppression est refusée sans condition ;
--   - la modification n'est admise que pour poser l'empreinte
--     d'une entrée qui n'en avait pas, c'est-à-dire l'écriture
--     du scellement qui suit immédiatement l'insertion. Tout
--     autre changement de colonne est refusé.
--
-- Un propriétaire de la base peut évidemment retirer ces
-- déclencheurs. Mais il devra le faire explicitement, et cette
-- action-là laisse une trace ailleurs. C'est la différence entre
-- une porte fermée et une porte absente.
-- =========================================================
CREATE OR REPLACE FUNCTION audit_log_immuable()
RETURNS TRIGGER AS $$
BEGIN
    IF (TG_OP = 'DELETE') THEN
        RAISE EXCEPTION
            'audit_log : suppression interdite. Le journal forensique ne se purge pas ligne à ligne.';
    END IF;

    -- Seule évolution admise : NULL -> empreinte, tout le reste inchangé.
    IF (OLD.entry_hash IS NOT NULL) THEN
        RAISE EXCEPTION
            'audit_log : entrée % déjà scellée, modification interdite.', OLD.id;
    END IF;

    IF (NEW.id IS DISTINCT FROM OLD.id
        OR NEW.timestamp  IS DISTINCT FROM OLD.timestamp
        OR NEW.phone      IS DISTINCT FROM OLD.phone
        OR NEW.event_type IS DISTINCT FROM OLD.event_type
        OR NEW.status     IS DISTINCT FROM OLD.status
        OR NEW.actor      IS DISTINCT FROM OLD.actor
        OR NEW.actor_type IS DISTINCT FROM OLD.actor_type
        OR NEW.source_ip  IS DISTINCT FROM OLD.source_ip
        OR NEW.amount     IS DISTINCT FROM OLD.amount
        OR NEW.currency   IS DISTINCT FROM OLD.currency
        OR NEW.reference  IS DISTINCT FROM OLD.reference
        OR NEW.details    IS DISTINCT FROM OLD.details
        OR NEW.prev_hash  IS DISTINCT FROM OLD.prev_hash) THEN
        RAISE EXCEPTION
            'audit_log : seule la pose de l''empreinte est autorisée sur l''entrée %.', OLD.id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_pas_de_suppression
    BEFORE DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immuable();

CREATE TRIGGER trg_audit_log_scellement_unique
    BEFORE UPDATE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immuable();
