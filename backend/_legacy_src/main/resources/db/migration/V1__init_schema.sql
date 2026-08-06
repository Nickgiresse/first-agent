-- Extension nécessaire pour la génération d'UUID côté PostgreSQL
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =========================================================
-- Table : bank_accounts
-- =========================================================
CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number VARCHAR(34) NOT NULL UNIQUE,
    owner_full_name VARCHAR(150) NOT NULL,
    is_eligible BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_bank_accounts_account_number ON bank_accounts(account_number);

-- =========================================================
-- Table : onboarding_sessions
-- =========================================================
CREATE TABLE onboarding_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_token VARCHAR(255) NOT NULL UNIQUE,
    bank_account_id UUID NOT NULL REFERENCES bank_accounts(id),
    status VARCHAR(30) NOT NULL DEFAULT 'ACCOUNT_VERIFIED'
        CHECK (status IN (
            'ACCOUNT_VERIFIED',
            'KYC_COMPLETED',
            'PIN_CREATED',
            'DOCUMENTS_UPLOADED',
            'TERMS_ACCEPTED',
            'COMPLETED',
            'EXPIRED'
        )),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_onboarding_sessions_token ON onboarding_sessions(session_token);
CREATE INDEX idx_onboarding_sessions_bank_account ON onboarding_sessions(bank_account_id);

-- =========================================================
-- Table : customers
-- =========================================================
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_account_id UUID NOT NULL UNIQUE REFERENCES bank_accounts(id),
    onboarding_session_id UUID NOT NULL UNIQUE REFERENCES onboarding_sessions(id),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) UNIQUE,
    phone_number VARCHAR(20) UNIQUE,
    pin_hash VARCHAR(255) NOT NULL,
    terms_accepted BOOLEAN NOT NULL DEFAULT false,
    terms_accepted_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'BLOCKED', 'SUSPENDED')),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_phone_number ON customers(phone_number);
CREATE INDEX idx_customers_bank_account ON customers(bank_account_id);

-- =========================================================
-- Table : customer_documents
-- =========================================================
CREATE TABLE customer_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    document_type VARCHAR(20) NOT NULL
        CHECK (document_type IN ('CNI_RECTO', 'CNI_VERSO', 'SELFIE')),
    file_path VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_customer_document_type UNIQUE (customer_id, document_type)
);

CREATE INDEX idx_customer_documents_customer ON customer_documents(customer_id);

-- =========================================================
-- Table : pin_reset_tokens
-- =========================================================
CREATE TABLE pin_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL REFERENCES customers(id),
    reset_token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_pin_reset_tokens_token ON pin_reset_tokens(reset_token);
CREATE INDEX idx_pin_reset_tokens_customer ON pin_reset_tokens(customer_id);