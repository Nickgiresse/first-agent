-- Support d'un troisième type de document OCR : le récépissé de paiement camerounais (reçu remis
-- au dépôt de la demande de CNI, avant le titre d'identité provisoire). Il n'a ni date de
-- naissance ni numéro de document, mais porte un montant payé et une date de paiement.
ALTER TABLE document_ocr_results
    ADD COLUMN payment_amount VARCHAR(20),
    ADD COLUMN payment_date DATE;

ALTER TABLE staging_ocr_results
    ADD COLUMN payment_amount VARCHAR(20),
    ADD COLUMN payment_date DATE;
