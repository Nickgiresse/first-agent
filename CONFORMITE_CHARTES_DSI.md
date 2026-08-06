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

## 2. Frontend , charte v1.1

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
| **Jetons hors stockage navigateur** (§12.4) | jeton de session **et PIN** en `sessionStorage` , voir §0 |
| **Intégration Keycloak** (§12) | absente , voir §1.2 |

### 2.3 Second passage , sections 5 à 23

La charte a été dépouillée intégralement le 06/08. Constats complémentaires.

#### Conforme

| Exigence | Constat |
|---|---|
| Répertoires et fichiers en kebab-case (§5) | respecté |
| `noImplicitReturns`, `noFallthroughCasesInSwitch` (§6.1) | activés |
| Parcours long découpé en étapes avec progression et retour (§13.4) | respecté |
| Budgets de paquet déclarés dans `angular.json` (§20.1) | présents |
| Paquet initial ≤ 300 Ko pour une application publique (§20.1) | 9 Ko |
| Suite de tests présente (§22) | 24 fichiers de test |

#### Non conforme

| Exigence | Constat |
|---|---|
| **Options TypeScript du socle minimal** (§6.1) | 5 des 8 manquent : `noImplicitAny`, `strictNullChecks`, `noUnusedLocals`, `noUnusedParameters`, `forceConsistentCasingInFileNames` |
| **Interdiction du type `any`** (§6.2) | 3 occurrences dans le code applicatif |
| **Sélecteurs Angular préfixés `afb-`** (§5) | tous en `app-` |
| **Aucune donnée personnelle dans le navigateur** (§13.4) | PIN et jeton en `sessionStorage` , voir §0 |
| **Aucune conservation locale des données sensibles** (§13.2) | même constat |
| **Jetons de couleur, aucun hexadécimal en composant** (§15.1) | 24 valeurs hexadécimales dans les feuilles de composants |
| **Polices hébergées localement** (§20.2) | importées depuis `fonts.googleapis.com` |
| **Détection de changement `OnPush`** (§20.3) | aucun composant |
| **Matrice de navigateurs consignée** (§21) | ni `.browserslistrc`, ni champ `browserslist`, ni mention au README |
| **Couverture ≥ 80 %, et 100 % sur les règles réglementaires** (§22.1) | non mesurée |
| **Branches `develop`, `feature/<ticket>-<libellé>`** (§23.1) | ni `develop`, ni convention de ticket ; `main` non protégée, écritures directes |
| **Conventional Commits contrôlés automatiquement** (§23.2) | 10 sur les 20 derniers, aucun contrôle |

#### À vérifier autrement

L'accessibilité (§16, niveau AA du WCAG 2.1), les seuils de performance mesurés (LCP, CLS, INP, Lighthouse) et le comportement en réseau dégradé (§19) ne se constatent pas par lecture du code : ils demandent un audit outillé sur l'application en fonctionnement. Le paquet initial de 9 Ko est le seul indicateur de performance mesurable en l'état, et il est très en deçà du budget.

Deux exigences méritent une attention particulière au vu du métier : §13.2 impose pour les données sensibles que l'autocomplétion soit désactivée et qu'aucune valeur ne soit préremplie, et §19.1 interdit de mettre une opération financière en file d'attente locale pour la rejouer au retour du réseau. Ni l'une ni l'autre n'a été vérifiée.

---

## 3. Backend , charte v1.0

| Exigence | Attendu | Constaté |
|---|---|---|
| Java | 21 | **21** |
| Versionnement des routes | `/api/v1/...` | **conforme** |
| Maven Wrapper | obligatoire | **présent** |
| Spring Boot | 3.5.x | **4.0.7** |
| Modèle web | WebFlux, `starter-web` proscrit | **`starter-web` servlet** |
| Persistance | R2DBC | **JPA** |
| Migrations | Liquibase | **Flyway** |
| Modules Maven | 5 (`domain`, `commons`, `application`, `infrastructure`, `boot`) | **1 seul** , *depuis : les 5 modules existent, voir §7* |
| Architecture | hexagonale, domaine pur vérifié au build | **en couches** : `controller`/`dto`/`entity`/`repository`/`service` , *depuis : pureté du domaine vérifiée au build, contenu à extraire* |
| Outils qualité (§10) | Spotless, Checkstyle, SpotBugs, SonarQube, enforcer | **aucun** , *depuis : 4 sur 5 en place, voir §7* |
| Authentification | Resource Server Keycloak | JWT applicatif |

Le versionnement des routes et le Maven Wrapper sont deux points où ce backend fait mieux que `firstagent-backend-afriland`. Tout le reste du socle technique s'en écarte, et davantage : Spring Boot 4 n'est pas seulement en avance sur la version demandée, il change les règles du jeu pour la migration vers WebFlux et R2DBC.

---

## 4. Défauts relevés hors charte

**Migrations destructrices.** `V3`, `V10` et `V13` contiennent des `UPDATE bank_accounts SET ...` **sans clause `WHERE`** : elles réécrivent l'identité de *tous* les titulaires. `V13` renomme chaque compte en « BRYAN DONGMO DJOUAKA », ce qui fait échouer `BankAccountIdentityTest` , le test a raison, la migration a tort. Bénin sur des données de test, destructeur sur une vraie base.

**Build de production impossible sans Internet.** `frontend/src/styles.scss` importe les polices depuis `fonts.googleapis.com`. La charte backend §12 impose des builds *air-gapped* ; une chaîne d'intégration interne butera sur la même erreur. Voir `RESEAU_RESSOURCES_BLOQUEES.md`.

**Regroupement en lignes de l'OCR.** Sur une carte dense, plusieurs champs partagent une ligne visuelle et l'OCR les rend accolés ; la valeur retenue après un intitulé mêle alors données et intitulés suivants. Le défaut est dans `python-service/app/ocr/engine.py`, fonction `extract_text`.

**Vivacité et comparaison biométrique jamais exécutées.** Sept tests du service de vision échouent faute des modèles MediaPipe, dont l'hébergeur est bloqué par l'inspection TLS du réseau. Ces deux fonctions n'ont donc jamais tourné, ni ici ni ailleurs à ma connaissance.

---

## 5. Ce que ce document ne couvre pas

La charte frontend a été dépouillée intégralement. Trois exigences échappent toutefois à une vérification par lecture du code et demandent un audit outillé sur l'application en fonctionnement : l'accessibilité au niveau AA du WCAG 2.1 (§16), les seuils de performance mesurés en conditions représentatives (§20.1) et le comportement en réseau dégradé (§19).

La charte backend a été confrontée au socle technique, à l'architecture, à la qualité et aux tests. Les sections sur l'observabilité, la configuration distribuée et le déploiement n'ont pas été détaillées faute d'objet : rien n'est en place sur ces sujets.

Aucun chiffrage n'est proposé : il dépend entièrement des arbitrages de la section 1. Si le framework et l'absence de Keycloak sont actés en dérogation, l'essentiel du reste relève d'une mise à niveau d'outillage et de configuration, de l'ordre de quelques jours pour le frontend. Le backend est d'un autre ordre : passer de Spring Boot 4 servlet et JPA à 3.5.x réactif et R2DBC, en cinq modules hexagonaux, n'est pas une mise à niveau mais une refonte du socle.

## 6. Synthèse

| Domaine | Conformité |
|---|---|
| Frontend , structure, TypeScript de base, découpage du parcours | acquise |
| Frontend , outillage qualité, nommage, design system, performance | à construire |
| Frontend , stockage du PIN et du jeton | **défaut de sécurité, section 0** |
| Frontend , framework et Keycloak | **arbitrage comité, section 1** |
| Backend , versionnement des routes, Maven Wrapper | acquise |
| Backend , socle technique et architecture | refonte |
| Gestion des sources | à construire (branches, protection, contrôle des commits) |

---

## 7. Relevé des corrections apportées

Les sections 0 à 6 sont l'audit, daté et laissé tel quel : elles disent ce qui
a été trouvé. Cette section dit ce qui a été traité depuis, et ce qui reste.

### Traité

**Stockage du PIN dans le navigateur** (section 0). Le PIN ne transite plus par
`sessionStorage`. Le jeton de session y demeure, écart assumé et documenté dans
le code : la correction conforme n'est pas de le supprimer mais de porter la
reprise de parcours par un cookie inaccessible au script, ce que demande la
charte frontend §13.4.

**Traversée de chemin dans le stockage des pièces.** Signalée par
FindSecBugs, corrigée et couverte par sept tests. Le nom fourni par le client
ne compose plus le chemin ; tout accès est confiné au répertoire de dépôt.

**Découpage en cinq modules Maven.** `domain`, `commons`, `application`,
`infrastructure`, `boot`, avec un sens de dépendance unique. La pureté du
module `domain` n'est plus affaire de discipline : l'enforcer bannit Spring,
JPA, Hibernate, Lombok, Jackson et Reactor à chaque compilation.

**Découplage des exceptions métier du transport.** Elles portaient toutes un
`HttpStatus`, donc une dépendance à Spring jusqu'au cœur du métier. Elles
portent désormais un `TypeErreurMetier`, et la correspondance vers un code HTTP
est faite une seule fois, dans le gestionnaire d'exceptions.

**Outillage qualité (§10), quatre outils sur cinq.** Spotless avec
google-java-format et JaCoCo avec un plancher de non-régression, tous deux
bloquants ; SpotBugs avec FindSecBugs, non bloquant tant que le parc existant
n'est pas résorbé ; Checkstyle, bloquant sur les règles de correction et de
nommage. Manque SonarQube, qui suppose un serveur.

**Migrations destructrices.** `V17` répare les identités écrasées par les
`UPDATE` sans clause `WHERE` de `V3`, `V10` et `V13`.

### Reste à faire, par ordre de dépendance

1. **Extraction du contenu métier** vers `domain`, `application` et `commons`.
   Les cinq modules existent et la règle de pureté est tenue, mais le code
   attend encore dans `infrastructure`. C'est la partie longue.
2. **Relèvement du plancher de couverture** de 30 % vers les 80 % de la
   charte §11, par paliers. Le laisser à 30 % reviendrait à s'en accommoder.
3. **SonarQube**, une fois un serveur disponible.
4. **Outillage frontend** : dépôt Nexus, ESLint, crochet de pré-commit,
   préfixe `afb-`, jetons de couleur, polices locales, `OnPush`.
5. **Gestion des sources** : branche `develop`, protection de `main`,
   convention `feature/<ticket>-<libellé>`, contrôle automatisé des commits.

### Suspendu sur décision

La rétrogradation de Spring Boot 4 vers 3.5.x, et avec elle le passage à
WebFlux et R2DBC, est écartée du périmètre actuel. Ce n'est pas une mise à
niveau mais une refonte du socle, et elle relève d'un arbitrage de comité au
même titre que le choix du framework frontend et l'absence de Keycloak.
