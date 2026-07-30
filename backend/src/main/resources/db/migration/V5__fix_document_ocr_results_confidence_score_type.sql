-- L'entité JPA DocumentOcrResult expose confidenceScore en Double (mappé par Hibernate
-- en DOUBLE PRECISION), alors que V4 avait créé la colonne en NUMERIC(5,2). Corrige le
-- type pour que la validation de schéma (ddl-auto=validate) passe.
ALTER TABLE document_ocr_results ALTER COLUMN confidence_score TYPE DOUBLE PRECISION;
