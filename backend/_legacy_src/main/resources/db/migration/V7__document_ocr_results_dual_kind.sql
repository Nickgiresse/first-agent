-- Le microservice Python (Module 2) sait extraire deux types de document : la CNI définitive
-- et le titre d'identité provisoire (récépissé délivré pendant la demande de CNI, très répandu
-- en pratique). Ces colonnes accueillent les champs propres à chaque type, plus le score de
-- qualité de l'image (Module 1), désormais calculé à l'extraction.
ALTER TABLE document_ocr_results
    ADD COLUMN document_kind VARCHAR(20),
    ADD COLUMN sex VARCHAR(5),
    ADD COLUMN birth_place VARCHAR(150),
    ADD COLUMN father_name VARCHAR(150),
    ADD COLUMN mother_name VARCHAR(150),
    ADD COLUMN kit_number VARCHAR(30),
    ADD COLUMN request_identifier VARCHAR(50),
    ADD COLUMN document_quality_score DOUBLE PRECISION;
