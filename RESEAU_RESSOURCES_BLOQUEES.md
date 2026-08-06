# Ressources bloquées par le réseau Afriland

Ce document liste ce que le réseau de la banque empêche de télécharger, les
symptômes correspondants, et **ce qu'il faut faire depuis un poste au réseau
non filtré** pour débloquer le projet.

Constaté le 31/07/2026 sur le poste de développement Afriland.

---

## 1. Ce qui se passe

L'inspection TLS du réseau interrompt les connexions vers plusieurs hôtes.
Le symptôme n'est pas une erreur de certificat mais une **coupure brutale** :

```
curl: (35) Recv failure: Connection was reset
Client network socket disconnected before secure TLS connection was established
```

État constaté, mesuré par `curl -o /dev/null -w "%{http_code}"` :

| Hôte | État | Ce qu'il empêche |
|---|---|---|
| `storage.googleapis.com` | **bloqué** | modèles MediaPipe (visage, vivacité) |
| `fonts.googleapis.com` | **bloqué** | polices du frontend — RÉSOLU, polices embarquées (§2.2) |
| `registry.npmjs.org` | **bloqué** | `npm install` en direct |
| `github.com` (HTTPS) | **bloqué** | `git clone`/`push` en HTTPS |
| `huggingface.co` | accessible | — |

Deux contournements sont déjà en place et n'ont rien à refaire :

- **npm** passe par le miroir `registry.npmmirror.com`, déjà configuré
  (`npm config get registry`). Les installations fonctionnent.
- **git** passe par SSH (`git@github.com:...`), qui n'est pas filtré.

---

## 2. À faire depuis un poste au réseau libre

### 2.1 Modèles MediaPipe — débloque 7 tests

Sans eux, le service de vision ne peut exécuter **ni la détection de visage,
ni la vivacité, ni la comparaison biométrique**. Sept tests échouent avec
`FileNotFoundError: Modèle ... introuvable`.

Télécharger ces deux fichiers dans `python-service/weights/` :

```
https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite
https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task
```

Le troisième modèle, ArcFace `w600k_r50.onnx` (174 Mo), **est déjà présent**
sur le poste Afriland dans `%USERPROFILE%\.insightface\models\buffalo_l\` : le
copier dans `python-service/weights/` plutôt que le retélécharger.

Les paquets de langue Tesseract sont également déjà sur le poste, dans
`%LOCALAPPDATA%\Programs\Tesseract-OCR\tessdata\` (`fra`, `eng`), à copier
dans `python-service/weights/tessdata/`.

Ces fichiers ne doivent **pas** être versionnés : ils sont volumineux et
`weights/` doit rester hors dépôt. Les transporter par clé USB ou partage
interne.

### 2.2 Polices du frontend — RÉSOLU le 06/08/2026

**Ce point ne bloque plus rien.** Il est conservé parce qu'il illustre bien la
nature du problème et la façon de le traiter.

`frontend/src/styles.scss` importait les polices depuis Internet :

```scss
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;...');
```

Angular tentait de les incorporer au build de production et **échouait**.
`ng build --configuration development` passait, lui, car il n'incorpore pas les
polices : le défaut ne se manifestait donc qu'au moment de livrer.

Ce n'était pas qu'un problème de poste. La charte backend §12 impose des builds
hors ligne et la charte frontend §20.2 l'hébergement local des polices : une
chaîne d'intégration dans le réseau de la banque aurait rencontré exactement la
même erreur.

**Correction apportée.** Les huit fichiers `woff2` sont désormais dans
`frontend/public/assets/fonts/`, extraits des paquets `@fontsource`,
sous-ensemble latin, 113 Ko au total. L'`@import` distant est remplacé par des
déclarations `@font-face` locales dans `src/styles/_polices.scss`, avec
`font-display: swap`. Le build de production aboutit sans aucun accès réseau,
et plus aucune URL distante ne figure dans la sortie.

Deux pièges rencontrés, notés pour qui referait le chemin :

- **Le miroir npm est intermittent, pas bloqué.** Un premier `npm view` a
  échoué en erreur réseau, un second a répondu. Il vaut donc la peine de
  réessayer avant de conclure à l'inaccessibilité.
- **`angular.json` ne sert que `public/`.** Des polices placées sous
  `src/assets/` sont correctement référencées par la feuille de style mais ne
  sont pas copiées dans la sortie : les URL répondent 404 et le navigateur
  retombe silencieusement sur la police système. Le build ne signale rien, et
  le défaut ne se voit qu'à l'œil sur une page rendue.

---

## 3. Vérifier que le déblocage a fonctionné

```bash
# Service de vision : 109 tests doivent passer (102 aujourd'hui)
cd python-service
python -m venv .venv && .venv/Scripts/activate
pip install -r requirements.txt
python -m pytest -q

# Frontend : le build de production doit aboutir
cd ../frontend
npm install
npx ng build
```

État de référence au 31/07/2026, **avant** déblocage :

| Contrôle | Résultat |
|---|---|
| Tests du service de vision | 102 passants, 7 échecs (tous par modèles absents) |
| `ng build` (production) | échec sur les polices |
| `ng build --configuration development` | passe |
| Compilation Maven du backend | passe |

---

## 4. Ce qui reste hors de ce document

Deux points sans rapport avec le réseau, signalés ici pour ne pas les perdre :

**Migrations destructrices.** `V3`, `V10` et `V13` contiennent des
`UPDATE bank_accounts SET ...` **sans clause `WHERE`** : elles réécrivent
l'identité de *tous* les titulaires. `V13` renomme ainsi chaque compte en
« BRYAN DONGMO DJOUAKA », ce qui fait échouer `BankAccountIdentityTest`. Le
test a raison, la migration a tort. Bénin sur des données de test,
destructeur sur une vraie base.

**Regroupement en lignes de l'OCR.** Sur une carte dense, plusieurs champs
partagent une même ligne visuelle et l'OCR les rend accolés : la valeur
retenue après un intitulé peut mêler données et intitulés suivants. Le défaut
est dans `python-service/app/ocr/engine.py`, fonction `extract_text`, pas dans
l'analyseur. Une tentative de filtrage mot à mot a été écartée : elle
tronquait « KAMDEM FOADJO VINCENT DE PAUL » à « VINCENT », la particule
« DE » étant aussi un mot d'intitulé.
