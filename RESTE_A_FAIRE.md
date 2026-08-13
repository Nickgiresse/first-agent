# Ce qui reste à faire, et ce qu'il faut savoir avant d'y toucher

État au 12/08/2026. Ce document existe pour qu'une reprise n'ait pas à
redécouvrir ce qui a déjà été établi, ni à refaire les erreurs déjà faites.

---

## 1. La vivacité n'était liée à aucun visage — CORRIGÉ, RESTE À VÉRIFIER EN RÉEL

Le code est écrit et testé (13 tests dans `face-verify/tests/`, 13 dans
`firstagent-demo/app/tests/test_kyc_liveness_binding.py`). **Il n'est pas
encore en service** : les deux processus tournent toujours sur l'ancien code,
voir « Mise en service » ci-dessous.

Ce qui a changé :

- `face-verify` mémorise l'empreinte ArcFace du visage qui joue le défi.
  `/api/verify` et `/api/verify-video` acceptent `liveness_session_id` et
  répondent un bloc `liveness` : le selfie soumis est-il bien ce visage ?
  L'empreinte vit en mémoire, 30 min au plus, jamais sur disque.
- Un **changement de visage en cours de défi** (une personne joue les actions,
  une autre est présentée) renvoie `409 face_changed` et détruit la session.
- Le bot refuse désormais un selfie qui n'est pas le visage du défi
  (`face_mismatch` → `NO_MATCH`, trace d'audit d'usurpation).
- **Le défi devient obligatoire** : sans vivacité liée, `KYC_LIVENESS_MODE`
  décide — `review` (défaut, compte suspendu et conseiller), `strict` (refus),
  `off` (comportement d'avant, à proscrire). Un selfie importé depuis la
  pellicule ne peut donc plus activer un compte tout seul.
- Le déclassement anti-photo ne dépend plus de la réussite du défi : réussir la
  vivacité ne peut plus **affaiblir** la décision.
- Chaque défi comporte au moins un clignement ou un sourire. Le tirage libre
  donnait bien un défi exclusivement rotatif une fois sur cinq (4 combinaisons
  sur 20), franchissable en pivotant un tirage papier.

**Mise en service — l'ordre compte.** Redémarrer `face-verify` (8010) **avant**
le bot (8000) : tant que le microservice répond sans bloc `liveness`, le bot ne
présume rien et envoie **tous** les dossiers en revue conseiller. C'est sans
danger, mais ingérable pour les conseillers si l'ordre est inversé.

**Reste à faire sur ce chemin :**

- Vérifier le parcours complet sur caméra réelle (exige HTTPS : `getUserMedia`
  ne fonctionne qu'en contexte sécurisé), et mesurer le taux de `REVIEW`
  légitimes : si le seuil de liaison (0.60) est trop haut, les clients sont
  renvoyés en agence pour rien.
- Risque résiduel assumé : l'identité n'est contrôlée que sur 2 frames par
  rafale (coût CPU). Mélanger deux visages *au milieu* d'une rafale reste
  théoriquement possible — mais il faudrait que le tirage papier cligne des yeux.
- Le service n'est plus sans état pendant un parcours (défis et empreintes en
  mémoire du processus) : plusieurs réplicas exigeraient une affinité de session.
- Le parcours Next.js/Java a sa **propre** vivacité (`python-service`, port 8001,
  `/api/v1/liveness/...`), non touchée ici. La même liaison y manque
  vraisemblablement : à auditer.

---

## 2. L'écran de relecture bloque les récépissés

`OcrServiceImpl.confirmExtractedData` exige `paymentDate` pour un `RECEPISSE`,
mais le formulaire Next.js n'expose que six champs et ne l'envoie jamais. Un
récépissé produit donc systématiquement « La date de paiement est obligatoire »,
sans champ pour la corriger : impasse définitive.

Pour les titres provisoires et les passeports, la branche `else` écrase avec
`null` des champs que l'OCR avait extraits — `birthPlace`, `fatherName`,
`motherName`, `kitNumber`, `requestIdentifier`. Perte de données jusqu'en base.

**Correction attendue.** Afficher les champs selon le `documentKind` renvoyé, et
les retransmettre à la confirmation.

---

## 3. Le jeton du lien WhatsApp n'est vérifié qu'à la toute fin

`POST /api/v1/onboarding/link/verify` existe et fonctionne, mais **aucun écran
ne l'appelle**. Le jeton est rangé en `sessionStorage` et n'est ressorti qu'à la
finalisation. Un lien expiré ou déjà consommé fait donc échouer le parcours à
l'écran des conditions générales, après le scan et la vivacité.

Sans lien du tout, le parcours va au bout mais le versement vers la source de
vérité est **silencieusement ignoré** : l'écran de réussite s'affiche alors que
le client n'existe pas côté WhatsApp banking.

**Correction attendue.** Vérifier le lien dès l'accueil, et décider explicitement
du comportement en son absence.

---

## 4. Aucune garde de session côté frontend

La session dure 30 minutes et tout le parcours doit y tenir. Aucun écran ne
vérifie la présence du jeton au montage : un rechargement après expiration
laisse l'utilisateur sur un message d'erreur, sans redirection.

---

## 5. Un contournement de KYC visible en production

L'écran KYC expose un bouton **« Continuer sans e-mail (test) »**, câblé sur
`POST /onboarding/kyc/skip`. C'est un contournement d'étape KYC, libellé
« test », accessible à tout client. À masquer derrière un indicateur de
configuration, ou à retirer une fois le SMTP fiable.

---

## Divergence des dépôts — à trancher avant toute nouvelle correction

Deux backends coexistent et **l'écart se creuse à chaque correction** :

| | `Nickgiresse/first-agent` | `daniellandry/firstagent-backend-afriland` |
|---|---|---|
| Structure | 5 modules hexagonaux | module unique |
| Migrations | V19 | V15 |
| Journal d'audit scellé | présent | absent |
| Déployé | non | **oui** |

Le second est celui qui sert le parcours. Le premier porte tout le travail de
conformité : journal d'audit, masquage des données personnelles dans les
traces, correction du verrou anti-force-brute de l'OTP, refus d'un compte déjà
utilisé, statut de dossier en révision.

**Tant que ce point n'est pas réglé, toute correction apportée au premier ne
sert personne**, et toute correction apportée au second est perdue au prochain
rapprochement. C'est la décision la plus utile à prendre.

---

## Ce qui a déjà été corrigé, pour ne pas le refaire

| Défaut | Où |
|---|---|
| Limite de téléversement à 1 Mo, message opaque | déployé, vérifié en ligne |
| Numéro CNI lu dans le mauvais champ MRZ | déployé, vérifié en ligne |
| Toute CNI classée « passeport » | `first-agent`, non déployé |
| Prénoms collés par `clean_name` | `first-agent`, non déployé |
| Seuil facial mal calibré à 75 | `first-agent`, ramené à 55 |
| Champ « Lieu de naissance » retiré | déployé |
| CORS du sous-domaine | déployé, non commité en amont |
| Vivacité liée au visage du selfie | `face-verify` + `firstagent-demo`, **non redémarré** |

---

## Pièges rencontrés, à ne pas réapprendre

**Le seuil facial n'est pas un pourcentage.** C'est un cosinus multiplié par
100, plafonnant vers 80. Toute valeur au-dessus de 70 envoie la quasi-totalité
des dossiers légitimes en agence.

**L'ordre des marqueurs de type de document compte.** Un titre provisoire et un
récépissé portent tous deux « Carte Nationale d'Identité » dans leur champ
« type de titre ». Les tester après le marqueur CNI les fait passer pour des
cartes définitives. Trois tests documentent cette précédence.

**`curl` n'envoie pas d'en-tête `Origin`.** Un contrôle CORS ne se déclenche
donc pas en ligne de commande : une vérification de mise en service peut être
entièrement verte alors que le parcours est inutilisable dans un navigateur.

**Le Nexus interne est à `192.168.11.137:38081/repository/npm-proxy/`**,
inscrit dans le `package-lock.json` du frontend. Il n'est pas joignable depuis
le VPS, qui est hors du réseau de la banque.

**`angular.json` ne sert que `public/`.** Des fichiers placés sous `src/assets/`
sont référencés par la feuille de style mais jamais copiés : les URL répondent
404 et le navigateur retombe silencieusement sur un repli.

**Le build Next échoue dans Docker sur ce serveur**, faute de mémoire. On
construit hors conteneur puis on sert `out/` via `Dockerfile.prebuilt`.

**Modifier la configuration d'un tunnel Cloudflare sans la valider** fait
tomber tous les hôtes qu'il sert. Sauvegarder ne suffit pas : il faut relire et
vérifier la structure produite avant de redémarrer.
