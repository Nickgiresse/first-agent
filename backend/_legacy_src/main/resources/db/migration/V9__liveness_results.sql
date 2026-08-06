-- =========================================================
-- Table : liveness_results
-- Résultat du défi de vivacité (Module 4 : clignement, rotation de tête, sourire...).
-- =========================================================
CREATE TABLE liveness_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL UNIQUE REFERENCES customers(id),
    session_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'LIVE', 'FAILED')),
    completed_actions TEXT,
    total_actions INT,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_liveness_results_customer ON liveness_results(customer_id);
