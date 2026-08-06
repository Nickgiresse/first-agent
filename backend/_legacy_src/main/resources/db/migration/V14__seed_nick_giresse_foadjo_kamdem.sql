-- Migration V14: Seed bank account details for NICK GIRESSE FOADJO KAMDEM
-- CNI: OU36328I5J3N7CQPJJ85 | Kit: KIT328 | PI/IC: OU36 - PI BALENG
-- Né le 26.01.2005 à BAFOUSSAM | Père: KAMDEM FOADJO VINCENT DE PAUL | Mère: TOUKAM ODETTE

INSERT INTO bank_accounts (id, account_number, owner_full_name, first_name, last_name, is_eligible, created_at, updated_at)
VALUES
    (gen_random_uuid(), '10005328006201000000001', 'NICK GIRESSE FOADJO KAMDEM', 'NICK GIRESSE', 'FOADJO KAMDEM', true, now(), now()),
    (gen_random_uuid(), '10005328006201000000002', 'FOADJO KAMDEM NICK GIRESSE', 'FOADJO KAMDEM', 'NICK GIRESSE', true, now(), now())
ON CONFLICT (account_number) DO NOTHING;
