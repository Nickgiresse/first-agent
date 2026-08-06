ALTER TABLE bank_accounts ADD COLUMN first_name VARCHAR(100);
ALTER TABLE bank_accounts ADD COLUMN last_name VARCHAR(100);

UPDATE bank_accounts
SET first_name = split_part(owner_full_name, ' ', 1),
    last_name = substring(owner_full_name FROM position(' ' IN owner_full_name) + 1);

UPDATE bank_accounts
SET account_number = '10005123451234567890123'
WHERE account_number = 'CM21100010000123456789012';

UPDATE bank_accounts
SET account_number = '10005987659876543210987'
WHERE account_number = 'CM21100010000987654321098';

ALTER TABLE bank_accounts ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE bank_accounts ALTER COLUMN last_name SET NOT NULL;

ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_status_check;
UPDATE customers SET status = 'USER' WHERE status = 'ACTIVE';
ALTER TABLE customers ADD CONSTRAINT customers_status_check
    CHECK (status IN ('USER', 'BLOCKED', 'SUSPENDED'));
