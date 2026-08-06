-- Répare les identités écrasées par des UPDATE sans clause WHERE.
--
-- Trois migrations antérieures modifient bank_accounts sans filtrer :
--   V3  : recopie owner_full_name dans first_name / last_name pour TOUTES les lignes
--   V10 : idem lors de l'ajout d'un compte
--   V13 : réécrit first_name='BRYAN', last_name='DONGMO DJOUAKA',
--         owner_full_name='BRYAN DONGMO DJOUAKA' sur TOUTES les lignes
--
-- Conséquence : chaque compte de la table porte le nom d'une seule personne.
-- BankAccountIdentityTest le signalait correctement en attendant « Jean
-- Dupont » et en obtenant « BRYAN » ; le test avait raison, la migration a
-- tort.
--
-- Une migration déjà appliquée ne se réécrit pas, Flyway en vérifiant
-- l'empreinte. La réparation passe donc par cette migration additionnelle,
-- limitée aux comptes de démonstration dont les valeurs d'origine sont
-- connues et versionnées (V2 et V3).
--
-- LIMITE ASSUMÉE : sur une base contenant de vrais comptes, ce fichier ne
-- restaure rien d'autre. Les identités réelles écrasées par V3, V10 ou V13
-- ne sont pas récupérables depuis le dépôt et doivent être resynchronisées
-- depuis le référentiel bancaire.

UPDATE bank_accounts
SET first_name      = 'Jean',
    last_name       = 'Dupont',
    owner_full_name = 'Jean Dupont'
WHERE account_number = '10005123451234567890123';

UPDATE bank_accounts
SET first_name      = 'Marie',
    last_name       = 'Kamga',
    owner_full_name = 'Marie Kamga'
WHERE account_number = '10005987659876543210987';
