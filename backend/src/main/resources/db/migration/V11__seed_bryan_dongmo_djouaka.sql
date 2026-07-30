-- Migration V11: Seed bank account details for BRYAN DONGMO DJOUAKA
UPDATE bank_accounts
SET first_name = 'BRYAN',
    last_name = 'DONGMO DJOUAKA',
    owner_full_name = 'BRYAN DONGMO DJOUAKA';

INSERT INTO bank_accounts (id, account_number, owner_full_name, first_name, last_name, is_eligible, created_at, updated_at)
VALUES
    (gen_random_uuid(), '10005500018807000000001', 'BRYAN DONGMO DJOUAKA', 'BRYAN', 'DONGMO DJOUAKA', true, now(), now()),
    (gen_random_uuid(), '10005500018807000000002', 'DONGMO DJOUAKA BRYAN', 'DONGMO DJOUAKA', 'BRYAN', true, now(), now())
ON CONFLICT (account_number) DO NOTHING;
