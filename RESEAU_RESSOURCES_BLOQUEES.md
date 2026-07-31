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
| `fonts.googleapis.com` | **bloqué** | polices du frontend, build de production |
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

### 2.2 Polices du frontend — débloque le build de production

`frontend/src/styles.scss` importe les polices depuis Internet :

```scss
@import url('https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Manrope:wght@500;600;700;800&display=swap');
```

Angular tente de les incorporer au build et **échoue** :

```
X [ERROR] Failed to inline external stylesheet
  'https://fonts.googleapis.com/css2?family=DM+Sans...'
  Error: Inlining of fonts failed.
```

`ng build --configuration development` passe, car il n'incorpore pas les
polices. Seul le build de production est bloqué.

**Ce n'est pas qu'un problème de poste** : la charte DSI impose des builds
*air-gapped*, et une chaîne d'intégration dans le réseau de la banque
rencontrera exactement la même erreur. Il faut donc embarquer les polices
dans le dépôt, ce qui est de toute façon la bonne pratique :

1. Récupérer les fichiers `.woff2` de **DM Sans** (400, 500, 600, 700) et
   **Manrope** (500, 600, 700, 800).
2. Les placer dans `frontend/public/fonts/`.
3. Remplacer l'`@import` par des déclarations `@font-face` locales, avec
   `font-display: swap`.

Après quoi `ng build` doit passer sans accès réseau.

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
