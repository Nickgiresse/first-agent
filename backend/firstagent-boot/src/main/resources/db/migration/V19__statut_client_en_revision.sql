-- =========================================================
-- Statut PENDING_REVIEW pour les clients
--
-- Un dossier parti en révision — pièce peu lisible, ressemblance
-- faciale insuffisamment nette — était jusqu'ici enregistré avec le
-- statut USER, donc actif. Le drapeau requires_manual_review existait,
-- mais rien n'empêchait l'accès au service pendant l'attente.
--
-- Le statut porte désormais cette distinction, et l'accès en découle.
--
-- La contrainte posée par V3 n'admettait que trois valeurs : sans cette
-- migration, toute inscription en révision échouerait à l'insertion,
-- et l'échec surviendrait au dernier écran du parcours, après que le
-- client ait tout fourni.
-- =========================================================

ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_status_check;

ALTER TABLE customers
    ADD CONSTRAINT customers_status_check
    CHECK (status IN ('USER', 'PENDING_REVIEW', 'BLOCKED', 'SUSPENDED'));

-- Les dossiers déjà marqués pour révision sont alignés sur le nouveau
-- statut. Sans cela, ils resteraient actifs alors qu'ils attendent
-- précisément une confirmation d'identité, et la règle ne vaudrait que
-- pour les inscriptions à venir.
--
-- La clause WHERE est indispensable : trois migrations de ce dépôt ont
-- déjà réécrit l'identité de TOUS les titulaires faute de l'avoir posée.
UPDATE customers
   SET status = 'PENDING_REVIEW'
 WHERE requires_manual_review = true
   AND status = 'USER';
