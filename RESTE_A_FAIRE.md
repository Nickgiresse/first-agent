# Ce qui reste à faire, et ce qu'il faut savoir avant d'y toucher

État au 12/08/2026. Ce document existe pour qu'une reprise n'ait pas à
redécouvrir ce qui a déjà été établi, ni à refaire les erreurs déjà faites.

---

## 1. La vivacité n'est liée à aucun visage — À TRAITER EN PREMIER

**C'est une faille, pas une gêne, et elle est en production.**

Rien ne garantit que le visage qui réussit le défi de vivacité est celui qui
est ensuite comparé à la pièce d'identité. Un fraudeur passe le défi avec son
propre visage, puis soumet au KYC la photo d'un tiers.

Pire, dans `firstagent-demo/app/gateway/secure_routes.py:1503`, réussir le défi
**désactive** le déclassement anti-photo :

    if (selfie_is_video and decision == "MATCH" and not liveness_ok
            and result.motion_score is not None and result.motion_score < 0.005):

Réussir la vivacité affaiblit donc la décision au lieu de la renforcer.

**Correction attendue.** Faire renvoyer par `face-verify` l'empreinte du visage
validé pendant le défi, et exiger une similarité élevée avec le selfie soumis
ensuite. Ne jamais persister cette empreinte : donnée biométrique, à garder en
session mémoire avec une durée de vie courte, comme aujourd'hui.

Deux autres points du même chemin :

- Le défi n'est pas obligatoire côté serveur. Un selfie JPEG envoyé sans avoir
  joué le défi ne subit aucun contrôle de vivacité : une photo imprimée
  rephotographiée donne directement un appariement.
- Environ un défi sur cinq ne tire que des actions de rotation, vraisemblablement
  franchissables en pivotant une photo imprimée devant l'objectif. À mesurer
  avant de conclure.

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
