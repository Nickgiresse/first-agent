# Intégration du microservice d'onboarding avec le WhatsApp banking

Ce document décrit comment le microservice d'onboarding (ce dépôt) s'articule avec
l'application WhatsApp banking (dépôt `firstagent-demo`), comment le démarrer, et ce
qui reste à faire avant une mise en production.

## 1. Principe

Le client reçoit sur WhatsApp un lien signé. Ce lien ouvre le microservice, qui déroule
le parcours KYC puis écrit le client dans le WhatsApp banking, **seule source de vérité**.

```
WhatsApp  ──lien ?t=JWT──►  Microservice (Angular + Spring + vision)
                                   │
                                   ├─ POST /api/onboarding/verify-token   (valide sans consommer)
                                   ├─ GET  /api/onboarding/account        (éligibilité / identité)
                                   └─ POST /api/onboarding/customer       (écrit le client, consomme le lien)
                                                   │
                                            WhatsApp banking
                                        (table accounts + audit scellé)
```

Le microservice **n'accède jamais directement** à la base du WhatsApp banking : tout passe
par l'API machine-à-machine protégée par clé (`X-API-Key`). Cela préserve la logique métier,
le journal d'audit scellé et l'unicité de la source de vérité.

## 2. Décisions d'architecture

| Sujet | Décision |
|---|---|
| Communication avec la banque | API M2M à clé (jamais d'accès direct à la base) |
| Source de vérité du client | WhatsApp banking (table `accounts`) |
| Base du microservice | Ne porte que le processus (session + staging), purgé à la fin |
| Code PIN | Saisi pendant le parcours, transmis en HTTPS à la finalisation, haché dans le schéma de la banque |
| OCR | RapidOCR (PP-OCRv4, ONNX) — remplace Tesseract |
| Vivacité | Challenge-response MediaPipe (clignement / rotation / sourire) |
| Comparaison faciale | ArcFace `w600k_r50` (identique des deux côtés) |

### Décisions KYC

| Décision | Effet côté banque |
|---|---|
| `MATCH` | Compte créé et **actif** |
| `REVIEW` | Compte créé mais **suspendu** (`is_active=0`), file de revue conseiller |
| `NO_MATCH` | **Aucun compte créé**, tentative archivée (trace anti-fraude) |

La finalisation est **fail-secure** : l'écriture dans la source de vérité a lieu *avant*
toute écriture locale, dans la même transaction. Si elle échoue, tout est annulé et le
dossier reste intact pour un nouvel essai.

## 3. Configuration

### Côté WhatsApp banking (`firstagent-demo/app/.env`)

```bash
ONBOARDING_MICROSERVICE_URL=https://onboarding.afrilandfirstbank.cm   # vide = parcours interne
ONBOARDING_API_KEY=<openssl rand -hex 32>                            # obligatoire en prod
```

Tant que `ONBOARDING_MICROSERVICE_URL` est vide, le bot continue d'utiliser son parcours
interne `/s/{code}` : la bascule est donc sans risque.

### Côté microservice (`backend/src/main/resources/application-*.yml` ou variables d'env)

```bash
WHATSAPP_BANKING_URL=https://bot.afb-firstagent.com
WHATSAPP_BANKING_API_KEY=<la MÊME valeur que ONBOARDING_API_KEY>
VISION_SERVICE_URL=http://localhost:8001
VISION_SERVICE_API_KEY=<clé interne du service de vision>
```

## 4. Démarrage local

```bash
# 1. Service de vision (OCR + visage + vivacité)
cd python-service
python -m venv .venv && .venv/Scripts/activate      # Windows
pip install -r requirements.txt
#   ⚠️ installer rapidocr APRÈS mediapipe ; en cas de conflit cv2,
#      réinstaller opencv-contrib-python en dernier.
uvicorn app.main:app --port 8001

# 2. Backend (JDK 21 obligatoire)
cd backend
./mvnw spring-boot:run

# 3. Frontend
cd frontend
npm install
npm start        # http://localhost:4200
```

Entrée par lien en local : `http://localhost:4200/onboarding/welcome?t=<JWT>`.

## 5. État de vérification

| Élément | Vérification |
|---|---|
| API M2M (lien, MATCH/REVIEW/NO_MATCH, usage unique, idempotence, CGU) | 20/20 tests automatisés |
| Moteur OCR RapidOCR | 11/11 (lecture réelle, confiance 98 %, 1,3 s/page) |
| Chaîne prétraitement → OCR → parser CNI | 7/7 (tous les champs extraits) |
| Backend Spring | Compile JDK 21 + 12/12 tests unitaires |
| Frontend Angular | Build complet sans erreur |

## 6. Conformité (CGU v1.0 et avis des quatre directions)

- Lecture effective des CGU imposée (case inactive tant que le texte n'est pas déroulé).
- Consentement biométrique **distinct** du consentement CGU.
- Mentions obligatoires affichées : anti-phishing, responsable de traitement, contact DPO,
  droit applicable COBAC/CEMAC.
- Parcours **bilingue FR/EN**, langue portée par le lien puis mémorisée.
- Traitements biométriques **100 % on-premise** (aucune IA externe).
- Lien à **usage unique**, valable 7 jours.
- Journal d'audit scellé alimenté à chaque écriture.
- PIN jamais transmis par WhatsApp, haché côté banque, purgé du navigateur en fin de parcours.

## 7. Reste à faire avant la production

1. **Tester sur de vraies photos de CNI** : l'OCR n'a été validé que sur images synthétiques.
   Vérifier en particulier le champ `expiryDate`, qui reprend la date de naissance quand aucune
   date d'expiration n'est lisible.
2. **Installer les poids ONNX** : modèle latin optionnel dans `python-service/weights/ocr_models/`
   (améliore la précision sur le français), et `w600k_r50.onnx` pour ArcFace.
3. **Smoke test de bout en bout** avec les trois services démarrés simultanément.
4. **Secrets** : remplacer toutes les valeurs par défaut (`changeme-local-dev-key`, mots de passe
   PostgreSQL) et servir le microservice en **HTTPS** (obligatoire pour la caméra).
5. **Durée de rétention biométrique** : à fixer avec le DPO/DCPO, puis implémenter la purge
   automatique (paramétrable, pas de valeur codée en dur).
6. **Rotation des secrets AIF** exposés pendant la période des tunnels (voir le rapport de sécurité).
