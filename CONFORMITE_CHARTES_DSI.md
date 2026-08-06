# Conformité aux chartes DSI

**Projet** : microservice d'onboarding FirstAgent (ce dépôt)
**Référentiels** : Charte de développement **frontend** v1.1 du 04/08/2026, Charte de développement **backend** v1.0 du 24/07/2026
**Date de mesure** : 06/08/2026
**Portée** : `frontend/` (Angular 21), `backend/` (Spring Boot 4), `python-service/`

État mesuré sur le code, pas déduit. Les décisions qui relèvent de la DSI ou du comité d'architecture sont isolées en section 1.

---

## 0. À traiter sans attendre

**Le code PIN du client est écrit en clair dans le stockage du navigateur.**

`frontend/src/app/core/services/onboarding-state.ts` sérialise l'état complet du parcours dans `sessionStorage`, PIN compris :

```ts
// PIN en clair, conservé pour la finalisation (transmis en HTTPS, haché côté banque).
setPin(pin: string | null): void {
  this.persist({ ...this.state(), pin });
}
```

Le commentaire décrit correctement le transport et le stockage côté banque, mais pas ce qui se passe entre-temps : le PIN reste lisible dans l'onglet, accessible à toute extension de navigateur, à tout script injecté et à quiconque ouvre les outils de développement sur un poste partagé. Il y demeure jusqu'à la fermeture de l'onglet, bien après la finalisation.

La charte frontend §12.4 proscrit déjà l'écriture du jeton d'accès dans le stockage du navigateur ; un code PIN bancaire relève a fortiori de la même interdiction. Le jeton de session du parcours y figure également.

Correction attendue : conserver le PIN en mémoire seulement, et ne persister que ce qui permet de reprendre un parcours interrompu, à l'exclusion de tout élément secret.

---

## 1. Décisions attendues du comité d'architecture

### 1.1 Le framework retenu contredit le critère de la charte

La charte frontend §3.1 pose un critère unique et explicite : **l'exposition de l'application**. Elle range les **parcours de souscription** parmi les usages de **Next.js**, et réserve Angular aux « applications internes riches et durables : back-offices, consoles d'administration, outils métier complexes ».

L'onboarding est un parcours de souscription destiné au public, écrit en Angular. La charte impose en outre que le choix soit « arbitré au démarrage, consigné dans le README et validé par l'architecte référent » : aucune trace de cet arbitrage n'existe dans le dépôt.

Deux issues : faire acter la dérogation, ou reconsidérer le socle. La seconde n'est pas anodine, le frontend étant fonctionnel et structuré correctement par ailleurs.

### 1.2 Authentification sans Keycloak

La charte frontend §12 impose Keycloak à **toute** application, interne comme cliente, et proscrit qu'une application « propose son propre écran de saisie de mot de passe » ou « implémente un mécanisme d'authentification parallèle ».

Le parcours a été conçu **sans connexion** : le client est identifié par un lien signé reçu sur WhatsApp, et crée un code PIN bancaire. Aucune intégration Keycloak n'existe dans le dépôt.

Cette conception se défend : il n'y a pas d'authentification applicative à proprement parler, et le PIN est un moyen de validation d'opérations bancaires, non un identifiant de connexion. Mais l'écart au texte est réel et doit être tranché, d'autant que §12.7 interdit explicitement tout « mode de contournement » et tout « accès invité ».

### 1.3 Socle de démarrage du DPTI

La charte frontend §3.4 impose que tout nouveau projet soit issu du socle fourni par le DPTI, préconfiguré avec analyseur statique, formateur, client HTTP, intégration au fournisseur d'identité, jetons graphiques, image et pipeline. Le projet n'en est pas issu ; l'écart doit être justifié et les éléments manquants reconstitués.

---

## 2. Frontend — charte v1.1

### 2.1 Conforme

| Exigence | Constat |
|---|---|
| TypeScript obligatoire, mode strict (§3.2, §6.1) | `strict: true`, aucun `.js` ni `.jsx` dans les sources |
| Découpe `core` / `shared` / `features` (§4.1, §4.2) | respectée |
| Profondeur ≤ 4 niveaux (§4.1) | 4 niveaux |
| Fichier de verrouillage versionné (§3.3) | `package-lock.json` présent |
| Formateur (§7.1, partiel) | `.prettierrc` présent |

### 2.2 Non conforme

| Exigence | Constat |
|---|---|
| **Registre Nexus interne** (§3.3) | `registry.npmmirror.com`, registre **public**, explicitement proscrit |
| **Version de Node figée** (§3.3) | ni `.nvmrc`, ni champ `engines` |
| **Alias de chemins** (§4.1) | aucun `paths` dans `tsconfig.json` |
| **Analyseur statique** (§7.1) | aucun ESLint |
| **Contrôle avant commit** (§7.1) | aucun hook |
| **Porte SonarQube** (§7.3) | aucune analyse ; couverture non mesurée, seuil 80 % sur le code nouveau |
| **Audit des dépendances** (§3.3) | non exécuté |
| **Jetons hors stockage navigateur** (§12.4) | jeton de session **et PIN** en `sessionStorage` — voir §0 |
| **Intégration Keycloak** (§12) | absente — voir §1.2 |

### 2.3 Non vérifié

Les sections 5, 8, 10, 11, 13 à 17 et 19 à 23 n'ont pas été dépouillées : nommage, gestion d'état, routage, formulaires, fichiers, design system, accessibilité, internationalisation, résilience réseau, performance, compatibilité, tests et livraison. Un second passage est nécessaire pour une conformité complète.

---

## 3. Backend — charte v1.0

| Exigence | Attendu | Constaté |
|---|---|---|
| Java | 21 | **21** |
| Versionnement des routes | `/api/v1/...` | **conforme** |
| Maven Wrapper | obligatoire | **présent** |
| Spring Boot | 3.5.x | **4.0.7** |
| Modèle web | WebFlux, `starter-web` proscrit | **`starter-web` servlet** |
| Persistance | R2DBC | **JPA** |
| Migrations | Liquibase | **Flyway** |
| Modules Maven | 5 (`domain`, `commons`, `application`, `infrastructure`, `boot`) | **1 seul** |
| Architecture | hexagonale, domaine pur vérifié au build | **en couches** : `controller`/`dto`/`entity`/`repository`/`service` |
| Outils qualité (§10) | Spotless, Checkstyle, SpotBugs, SonarQube, enforcer | **aucun** |
| Authentification | Resource Server Keycloak | JWT applicatif |

Le versionnement des routes et le Maven Wrapper sont deux points où ce backend fait mieux que `firstagent-backend-afriland`. Tout le reste du socle technique s'en écarte, et davantage : Spring Boot 4 n'est pas seulement en avance sur la version demandée, il change les règles du jeu pour la migration vers WebFlux et R2DBC.

---

## 4. Défauts relevés hors charte

**Migrations destructrices.** `V3`, `V10` et `V13` contiennent des `UPDATE bank_accounts SET ...` **sans clause `WHERE`** : elles réécrivent l'identité de *tous* les titulaires. `V13` renomme chaque compte en « BRYAN DONGMO DJOUAKA », ce qui fait échouer `BankAccountIdentityTest` — le test a raison, la migration a tort. Bénin sur des données de test, destructeur sur une vraie base.

**Build de production impossible sans Internet.** `frontend/src/styles.scss` importe les polices depuis `fonts.googleapis.com`. La charte backend §12 impose des builds *air-gapped* ; une chaîne d'intégration interne butera sur la même erreur. Voir `RESEAU_RESSOURCES_BLOQUEES.md`.

**Regroupement en lignes de l'OCR.** Sur une carte dense, plusieurs champs partagent une ligne visuelle et l'OCR les rend accolés ; la valeur retenue après un intitulé mêle alors données et intitulés suivants. Le défaut est dans `python-service/app/ocr/engine.py`, fonction `extract_text`.

**Vivacité et comparaison biométrique jamais exécutées.** Sept tests du service de vision échouent faute des modèles MediaPipe, dont l'hébergeur est bloqué par l'inspection TLS du réseau. Ces deux fonctions n'ont donc jamais tourné, ni ici ni ailleurs à ma connaissance.

---

## 5. Ce que ce document ne couvre pas

La charte frontend compte 23 sections ; quatre ont été dépouillées. Les exigences de test, de performance, d'accessibilité et de livraison restent à confronter au code.

Aucun chiffrage n'est proposé : il dépend entièrement des arbitrages de la section 1. Si le framework et l'absence de Keycloak sont actés en dérogation, le reste relève d'une mise à niveau d'outillage, de l'ordre de quelques jours. Si l'un des deux est refusé, le sujet change de nature.
