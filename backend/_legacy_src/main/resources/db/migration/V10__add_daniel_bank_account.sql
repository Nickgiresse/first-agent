-- Migration V10: Seed bank account details for DANIEL CHARLES AUGUSTINE TANDENT YANG AHANDA

INSERT INTO bank_accounts (id, account_number, owner_full_name, first_name, last_name, is_eligible, created_at, updated_at)
VALUES
    (gen_random_uuid(), '10005111789079000000001', 'DANIEL CHARLES AUGUSTINE TANDENT YANG AHANDA', 'DANIEL CHARLES AUGUSTINE', 'TANDENT YANG AHANDA', true, now(), now())
ON CONFLICT (account_number) DO NOTHING;
