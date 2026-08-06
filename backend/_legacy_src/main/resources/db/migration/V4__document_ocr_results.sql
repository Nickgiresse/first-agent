-- =========================================================
-- Table : document_ocr_results
-- =========================================================
CREATE TABLE document_ocr_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL UNIQUE REFERENCES customers(id),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    document_number VARCHAR(50),
    birth_date DATE,
    expiry_date DATE,
    ocr_provider VARCHAR(30) NOT NULL,
    confidence_score NUMERIC(5,2),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'EXTRACTED', 'CONFIRMED', 'FAILED')),
    extracted_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_document_ocr_results_customer ON document_ocr_results(customer_id);
