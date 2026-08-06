-- Staging server-side de tout le parcours d'onboarding : plus rien n'est écrit dans customers /
-- customer_documents / document_ocr_results / face_verification_results / liveness_results avant
-- la toute fin (completeOnboarding), qui matérialise tout en une seule transaction.

ALTER TABLE onboarding_sessions
    ADD COLUMN email VARCHAR(150),
    ADD COLUMN pin_hash VARCHAR(255),
    ADD COLUMN terms_accepted BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN terms_accepted_at TIMESTAMP;

CREATE TABLE staging_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    onboarding_session_id UUID NOT NULL REFERENCES onboarding_sessions(id),
    document_type VARCHAR(20) NOT NULL
        CHECK (document_type IN ('CNI_RECTO', 'CNI_VERSO', 'SELFIE')),
    file_path VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_staging_document_type UNIQUE (onboarding_session_id, document_type)
);

CREATE INDEX idx_staging_documents_session ON staging_documents(onboarding_session_id);

CREATE TABLE staging_ocr_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    onboarding_session_id UUID NOT NULL UNIQUE REFERENCES onboarding_sessions(id),
    document_kind VARCHAR(20),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    document_number VARCHAR(50),
    sex VARCHAR(5),
    birth_date DATE,
    expiry_date DATE,
    birth_place VARCHAR(150),
    father_name VARCHAR(150),
    mother_name VARCHAR(150),
    kit_number VARCHAR(30),
    request_identifier VARCHAR(50),
    ocr_provider VARCHAR(30) NOT NULL,
    confidence_score DOUBLE PRECISION,
    document_quality_score DOUBLE PRECISION,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'EXTRACTED', 'CONFIRMED', 'FAILED')),
    extracted_at TIMESTAMP,
    confirmed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE staging_face_verification_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    onboarding_session_id UUID NOT NULL UNIQUE REFERENCES onboarding_sessions(id),
    similarity_score DOUBLE PRECISION,
    target_quality_score DOUBLE PRECISION,
    provider VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED')),
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE staging_liveness_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    onboarding_session_id UUID NOT NULL UNIQUE REFERENCES onboarding_sessions(id),
    session_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING', 'LIVE', 'FAILED')),
    completed_actions TEXT,
    total_actions INT,
    verified_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);
