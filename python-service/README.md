# vision-service

Microservice interne de vision par ordinateur (FastAPI). Appelé exclusivement par le backend
Spring Boot — jamais directement par le frontend Angular.

Modules implémentés : qualité/type/côté d'un document (Module 1), OCR structuré CNI + titre
provisoire (Module 2), qualité du visage/selfie (Module 3), vivacité par défi (Module 4),
comparaison biométrique (Module 5). Les 5 modules de l'architecture initiale sont couverts —
reste le branchement complet côté Spring Boot (actuellement toujours sur Mindee/AWS/CompreFace).

## Démarrage local (sans Docker)

```powershell
cd python-service
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env

# Modèles MediaPipe (Tasks API, non bundlés dans le pip package)
New-Item -ItemType Directory -Force -Path weights | Out-Null
Invoke-WebRequest -Uri "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/blaze_face_short_range.tflite" -OutFile "weights\blaze_face_short_range.tflite"
Invoke-WebRequest -Uri "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task" -OutFile "weights\face_landmarker.task"

# Modèle ArcFace (Module 5, comparaison biométrique — ~174 Mo)
Invoke-WebRequest -Uri "https://huggingface.co/public-data/insightface/resolve/main/models/buffalo_l/w600k_r50.onnx" -OutFile "weights\w600k_r50.onnx"

# Packs de langue Tesseract FR/EN (non bundlés non plus)
New-Item -ItemType Directory -Force -Path weights\tessdata | Out-Null
Invoke-WebRequest -Uri "https://github.com/tesseract-ocr/tessdata_fast/raw/main/fra.traineddata" -OutFile "weights\tessdata\fra.traineddata"
Invoke-WebRequest -Uri "https://github.com/tesseract-ocr/tessdata_fast/raw/main/eng.traineddata" -OutFile "weights\tessdata\eng.traineddata"

uvicorn app.main:app --reload --port 8001
```

Le service écoute sur `http://localhost:8001`. Documentation interactive : `http://localhost:8001/docs`.

> Compatibilité : testé avec Python 3.14 (versions épinglées dans `requirements.txt` choisies pour
> avoir des wheels précompilées cp314). Si vous utilisez une autre version de Python et qu'un
> paquet se met à compiler depuis les sources (lent, voire échoue faute de toolchain C/Rust),
> c'est probablement qu'aucune wheel précompilée n'existe pour votre version — remontez la version
> du paquet concerné dans `requirements.txt`.
>
> Ne pas ajouter `opencv-python` ou `opencv-python-headless` : `mediapipe` impose
> `opencv-contrib-python`, et les trois variantes s'installent sous le même module `cv2` (conflit
> garanti si plusieurs sont présentes).

### Tesseract OCR (Module 2)

Binaire système, pas un paquet Python — à installer séparément :

```powershell
winget install UB-Mannheim.TesseractOCR
```

Puis, dans `.env`, décommentez/ajustez (chemin par défaut de l'installeur) :
```
TESSERACT_CMD=C:\Program Files\Tesseract-OCR\tesseract.exe
```

Les packs de langue utilisés viennent de `weights/tessdata/` (téléchargés ci-dessus), pas de
l'installation système — ça évite de dépendre de ce que l'installeur a inclus par défaut
(souvent l'anglais seul).

> PaddleOCR (choix initial le plus précis) est actuellement impossible sur Python 3.14 :
> `paddlepaddle` n'a encore aucune wheel pour cette version, sur aucune plateforme. Tesseract a
> été retenu comme solution la plus simple à faire tourner sans conflit ni téléchargement lourd.
> À reconsidérer PaddleOCR quand une wheel 3.14 sera disponible, ou en installant Python 3.12 en
> parallèle spécifiquement pour ce service.

## Tests

```powershell
pytest
```

## Docker

```powershell
docker build -t vision-service .
docker run -p 8001:8001 --env-file .env vision-service
```

## Authentification

Toutes les routes exigent le header `X-Internal-Api-Key`, dont la valeur doit correspondre à
`INTERNAL_API_KEY` (voir `.env`). Spring Boot doit envoyer ce header sur chaque appel.

## Endpoints

- `GET /health` — sonde de vie, sans authentification.
- `POST /api/v1/document/analyze` — multipart `file` (image recto ou verso). Retourne la présence,
  le type provisoire, le côté (recto/verso) et un rapport qualité détaillé (netteté, luminosité,
  reflets, résolution, cadrage). **Module 1**.
- `POST /api/v1/document/extract` — multipart `front` (obligatoire) + `back` (optionnel). Retourne
  les champs structurés extraits par OCR (CNI définitive ou titre d'identité provisoire, détecté
  automatiquement), le texte brut concaténé, la confiance moyenne et les problèmes détectés.
  **Module 2**. Extraction CNI par mots-clés bilingues FR/EN — non calibrée sur de vraies photos de
  CNI définitive (aucun échantillon disponible) ; le titre provisoire est calibré sur un vrai
  échantillon.
- `POST /api/v1/face/analyze` — multipart `file` (selfie). Retourne présence/nombre de visages,
  centrage, détection plausible des yeux/nez/bouche, et le même rapport qualité que le Module 1.
  **Module 3**. Jamais testé sur un vrai selfie (seulement sur des images synthétiques et une
  photo de CNI trop dégradée pour contenir un visage détectable).
- `POST /api/v1/liveness/challenge/start` — démarre une session de vivacité, retourne `sessionId`
  et une séquence aléatoire d'actions parmi `BLINK`, `TURN_LEFT`, `TURN_RIGHT`, `SMILE`, `LOOK_UP`,
  `LOOK_DOWN`.
- `POST /api/v1/liveness/challenge/verify` — multipart `session_id`, `action`, `frames` (plusieurs
  images formant une courte rafale, ex. 5-10 frames sur ~1s, la première servant de référence
  neutre). Vérifie que l'action annoncée a bien été effectuée (transition réelle, pas une simple
  photo statique) et fait avancer la session.
- `GET /api/v1/liveness/challenge/{session_id}/status` — état courant de la session (actions
  restantes, `decision`: `IN_PROGRESS` | `LIVE`).
  **Module 4**. Sessions en mémoire (perdues au redémarrage du service ; à passer sur Redis si
  déploiement multi-instances). **Point non vérifié empiriquement, le plus fragile du module** :
  la correspondance entre les axes de rotation de tête et TURN_LEFT/TURN_RIGHT/LOOK_UP/LOOK_DOWN
  repose sur une hypothèse de convention de coordonnées MediaPipe non confirmée par un vrai test
  (aucune photo de tête tournée disponible) — voir le commentaire détaillé dans
  `app/liveness/landmarker.py::_rotation_matrix_to_euler_degrees`. À tester en priorité avec de
  vrais mouvements de tête avant mise en production ; si l'axe ou le sens est inversé, la
  correction se fait uniquement dans cette fonction.
- `POST /api/v1/verification/compare` — multipart `source` (ex. photo CNI) + `target` (ex. selfie
  ou meilleure frame de la session de vivacité). Aligne chaque visage sur le gabarit standard
  ArcFace (112x112), calcule une empreinte 512-d via `w600k_r50.onnx` (ONNX Runtime, sans le
  paquet pip `insightface` — celui-ci imposerait `opencv-python` standard, en conflit avec
  `opencv-contrib-python` de mediapipe), puis une similarité cosinus et une décision
  `MATCH`/`NO_MATCH`. **Module 5**. Seuil (0.4) = recommandation de départ de la doc officielle
  InsightFace, non calibré sur un vrai jeu de paires (même personne / personnes différentes) —
  à ajuster en production. Validé sur une vraie photo comparée à elle-même (similarité >0.9,
  décision `MATCH`) ; jamais testé sur deux vraies photos de personnes différentes (un seul
  échantillon de visage réel disponible pendant le développement).
