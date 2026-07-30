-- =========================================================
-- Table : face_verification_results
-- Résultat de la comparaison biométrique entre le selfie et la photo de la CNI (recto).
-- =========================================================
CREATE TABLE face_verification_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL UNIQUE REFERENCES customers(id),
    similarity_score DOUBLE PRECISION,
    provider VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED')),
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_face_verification_results_customer ON face_verification_results(customer_id);
