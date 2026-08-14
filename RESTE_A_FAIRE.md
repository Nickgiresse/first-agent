# Ce qui reste à faire, et ce qu'il faut savoir avant d'y toucher

État au 13/08/2026. Ce document existe pour qu'une reprise n'ait pas à
redécouvrir ce qui a déjà été établi, ni à refaire les erreurs déjà faites.

---

## Le dépôt qui fait foi

**`Nickgiresse/first-agent`.** Décision de l'utilisateur : fait foi celui qui est
conforme et qui porte toutes les fonctionnalités nécessaires. L'inventaire a
vérifié les deux moitiés de ce critère.

Conformité : découpage hexagonal en 5 modules, migrations jusqu'à V19, journal
d'audit scellé, masquage des données personnelles dans les traces, outillage
qualité (Spotless, Checkstyle, SpotBugs, JaCoCo). Le déployé
(`daniellandry/firstagent-backend-afriland`) est resté en module unique, V15,
sans rien de tout cela.

Complétude : le backend déployé n'expose qu'**un seul** endpoint absent d'ici,
`POST /onboarding/kyc/skip`, et il a été **délibérément écarté** (voir plus bas).
Cinq modifications vivaient uniquement sur le serveur, non commitées ; trois
étaient déjà présentes ici (archivage des pièces KYC, méthode `archiveDocuments`,
origine CORS configurable), les deux autres ont été portées (limite de
téléversement, gestionnaire 413).

**Il reste à déployer ce dépôt à la place de l'autre.** Tant que ce n'est pas
fait, les corrections d'ici ne servent personne.

---

## Ce qui a été corrigé, et où

| Défaut | État |
|---|---|
| Vivacité non liée au visage comparé (parcours bot) | corrigé, reporté et **en service depuis le 13/08/2026** |
| Verrou du PIN attaché au lien et non au compte | corrigé (redemander un lien remettait la force brute à zéro) |
| Vivacité non liée au visage comparé (parcours Java) | corrigé, 15 tests, **non déployé** |
| Défi de vivacité parfois exclusivement rotatif | corrigé (action déformante garantie) |
| Récépissé bloqué à la relecture, sans champ pour débloquer | corrigé (Next.js) |
| Champs du titre provisoire écrasés en base | corrigé (Next.js) |
| Jeton du lien vérifié seulement à la fin | corrigé (Next.js) |
| Aucune garde de session côté frontend | corrigé (Next.js) |
| Contournement KYC visible en production | **retiré** |
| Limite de téléversement à 1 Mo, message opaque | corrigé des deux côtés |
| `application-prod.yml` faisait échouer le démarrage en profil prod | corrigé |
| Numéro CNI lu dans le mauvais champ MRZ | déployé, vérifié en ligne |
| Toute CNI classée « passeport » | corrigé, non déployé |
| Prénoms collés par `clean_name` | corrigé, non déployé |
| Seuil facial mal calibré à 75 | ramené à 55 |
| CORS du sous-domaine | déployé |

---

## 1. La vivacité était détachée du visage — CORRIGÉ PARTOUT, RESTE À METTRE EN SERVICE

Le défaut était le même sur les deux parcours, et il était structurel : « le défi
a réussi » et « le selfie correspond à la pièce » étaient deux faits établis
séparément. Rien n'imposait qu'ils portent sur la même personne. Une personne
pouvait jouer les actions devant la caméra pendant qu'un selfie de quelqu'un
d'autre partait à la comparaison.

Ce qui a changé, des deux côtés : la session de vivacité mémorise l'empreinte
ArcFace du visage qui joue le défi (en mémoire du processus, jamais sur disque) ;
un changement de visage en cours de défi détruit la session ; la comparaison
faciale reçoit l'identifiant du défi et refuse un selfie qui n'est pas ce
visage ; le tirage garantit au moins une action déformante, à position variable.

**Reste à faire :**

- **Mettre en service.** Parcours bot : redémarrer `face-verify` (8010) **avant**
  le bot (8000) ; dans l'ordre inverse, tous les dossiers partent en revue
  conseiller. Parcours Java : redéployer `python-service` et le backend.
- Vérifier sur caméra réelle (exige HTTPS, `getUserMedia` refuse un contexte non
  sécurisé) et **mesurer le taux de refus légitimes**. Le seuil de liaison est à
  0,60, choisi par raisonnement et non par mesure : trop haut, il renvoie en
  agence des clients honnêtes. C'est le premier chiffre à corriger avec de vraies
  données.
- Risque résiduel assumé : l'identité n'est contrôlée que sur 2 frames par
  rafale, pour le coût de calcul. Intercaler un visage au milieu d'une rafale
  reste théoriquement possible, mais il faudrait que le tirage papier substitué
  cligne des yeux ou sourie.
- Le service n'est plus sans état pendant un parcours : plusieurs réplicas
  exigeraient une affinité de session, ou un stockage partagé.

---

## 2. Le contournement KYC a été retiré, et ne doit pas revenir

`POST /onboarding/kyc/skip` et le bouton « Continuer sans e-mail (test) » ont été
supprimés. La tentation de les réintroduire reviendra, donc voici le raisonnement
en entier.

Ils avaient été ajoutés « tant que l'envoi d'e-mail est en panne ». L'envoi
n'était pas en panne : serveur, port et compte étaient corrects, seule la
variable `MAIL_PASSWORD` n'était pas renseignée, ce qui faisait échouer le
démarrage sur un paramètre non résolu. Les traces du serveur ne comptent **aucun
appel** à cet endpoint : le contournement n'a jamais servi.

Et il était plus grave qu'une étape sautée : le courriel porte les codes à usage
unique et le lien de réinitialisation du PIN. Un compte activé sans adresse
vérifiée n'offre à son titulaire aucun moyen de récupération.

---

## 3. Sauvegarde : en service, mais deux trous à combler

Mise en service le 13/08/2026. Le module existait depuis son ajout mais le
Dockerfile ne le copiait pas : le tick échouait sur `ModuleNotFoundError` à
chaque passage, et **aucune sauvegarde n'avait jamais tourné**. Corrigé,
archive produite et restauration vérifiée (déchiffrement Fernet, 6 pièces KYC).

**La base est désormais sauvegardée.** Le module applicatif ne sait faire un
instantané que d'une base SQLite ; celle du bot est PostgreSQL, et les données
clients n'étaient donc couvertes par rien. Un `pg_dump` quotidien chiffré est en
place côté hôte : `/usr/local/bin/firstagent-pg-backup.sh`, déclenché par le
minuteur systemd `firstagent-backup.timer` à 02h17, rétention 30 jours, dépôt
dans `/opt/backups/firstagent/db/`. Restauration vérifiée : l'archive se
déchiffre et livre un dump PostgreSQL 16.14 valide.

`cron` est absent de cet hôte, d'où systemd. `Persistent=true` rattrape
l'exécution manquée si la machine était arrêtée à l'heure prévue, ce que cron ne
fait pas.

Chiffrement `openssl` et non Fernet, délibérément : le jour où l'on restaure, il
ne faut dépendre ni de Python, ni du code applicatif, ni d'un conteneur en
marche.

```
openssl enc -d -aes-256-cbc -pbkdf2 -pass file:/root/.firstagent-pgdump-pass \
  -in <archive>.sql.gz.enc | gunzip | psql -U afribank afribank
```

**La clé ne vit plus sur le volume qu'elle protège.** Elle est passée dans
`BACKUP_ENCRYPTION_KEY`, que le module lit en priorité et que la sauvegarde
exclut. Le fichier `/app/data/.backup_key` a été retiré, mais seulement après
avoir vérifié qu'une archive antérieure se déchiffrait toujours : l'inverse
aurait rendu toutes les archives illisibles sur une valeur mal recopiée.

### Les sauvegardes existent désormais sur une seconde machine

Le serveur n'a **qu'un seul disque**, aucun montage réseau et aucun outil de
réplication (`rclone`, `restic`, `borg`, `aws` : tous absents). Le MinIO qui y
tourne est sur le même disque, donc sans effet sur une perte de disque. Il n'y
avait pas de destination externe à configurer : il fallait en créer une.

Copie de secours en place sur le poste de travail,
`Documents\firstagent-sauvegardes\` :

- `archives\db\` et `archives\kyc\`, rapatriées quotidiennement à 09h00 par la
  tâche planifiée « FirstAgent - rapatriement des sauvegardes » ;
- l'accès passe par une clé SSH dédiée, restreinte par **commande forcée** :
  elle ne peut qu'émettre l'archive des sauvegardes. Vérifié en demandant
  `cat /etc/shadow` : le serveur renvoie une archive tar, pas le fichier.
  Volée, cette clé ne livre que des fichiers déjà chiffrés, et aucun shell ;
- déchiffrement prouvé **hors serveur**, avec la seule copie locale.

Deux pièges rencontrés là, et corrigés :

- le `tar` de Git Bash prend `C:\...` pour un hôte distant. Le script annonçait
  « OK » sans avoir rien extrait. Il appelle désormais `System32/tar.exe` par
  son chemin complet et contrôle le code de retour ;
- vérifier la fraîcheur par la date des fichiers ne marche pas : `tar` restitue
  les dates d'origine. Le contrôle compare le nombre d'archives avant et après.

### Ce qui reste, et que je ne peux pas faire à votre place

**Les deux secrets sont encore en clair sur le poste**, dans
`firstagent-sauvegardes\SECRETS-A-DEPLACER-DANS-UN-COFFRE\`. Ils y ont été
déposés parce qu'ils n'existaient que sur le serveur : sans eux, la copie de
secours n'aurait servi à rien. Ils sont volontairement **séparés des archives**,
pour ne pas recréer ailleurs le défaut corrigé sur le serveur.

Il reste à les ranger dans un gestionnaire de mots de passe puis à supprimer ce
dossier. Un poste de travail n'est pas un coffre : c'est une étape.

**Le `.env` du serveur n'est dans aucune sauvegarde**, volontairement (il porte
les mots de passe de base et les clés d'API). Sans lui on restaure les données
mais on ne redémarre pas le service : à prévoir dans le coffre également.

---

## 4. Points ouverts

- **Déployer `first-agent` à la place du backend actuel.** C'est le point
  bloquant : tout ce qui précède est écrit et testé, rien n'est en service.
- ~~Mettre en service la vivacité du parcours bot~~ — **fait le 13/08/2026.**
  Attention pour la prochaine fois : le code n'est **pas** monté depuis le
  disque, il est figé dans les images. Un redémarrage ne change donc rien, il
  faut reconstruire. Les images précédentes sont étiquetées
  `20260813-liaison-vivacite` pour un retour arrière, et les fichiers remplacés
  sauvegardés en `.avant-20260813-liaison-vivacite`.
  Vérifié en service : `liveness_session_id` présent dans le schéma OpenAPI
  servi par face-verify, 4 marqueurs dans le bot, conteneur sain, mode `review`.
- **Calibrer les seuils sur données réelles** : liaison de vivacité (0,60),
  comparaison faciale (0,40), revue manuelle (70). Aucun n'a été mesuré.
- **Modification d'un message WhatsApp par le client** : aucune déduplication sur
  `message_id`, aucun traitement des événements d'édition. WhatsApp permet
  l'édition, donc le cas se produira.
- **Keycloak, déconnexion : corrigé, à confirmer en navigateur.** Le diagnostic
  initial était faux. Le client `firstagent-backoffice` du royaume `afb` déclare
  bien `https://backoffice.afb-firstagent.com/*`, et son attribut
  `post.logout.redirect.uris` est **absent**, ce qui en Keycloak signifie
  « suivre les `redirectUris` » : c'est le bon état, à laisser tel quel. Ce qui
  manquait était `http://localhost:4300/*`, le port du back-office en
  développement, où `keycloak.logout({ redirectUri: origin + '/' })` renvoyait
  vers une URI non déclarée. Ajouté le 13/08/2026. Reste à confirmer dans un
  navigateur, ce qu'aucun appel en ligne de commande ne remplace.
- **Couverture frontend** vers 80 % (charte DSI), et registre Nexus interne
  injoignable depuis le VPS.

---

## Pièges rencontrés, à ne pas réapprendre

**Le seuil facial n'est pas un pourcentage.** C'est un cosinus multiplié par
100, plafonnant vers 80. Toute valeur au-dessus de 70 envoie la quasi-totalité
des dossiers légitimes en agence.

**Le seuil de liaison de vivacité n'est pas le seuil de comparaison faciale.**
Comparer une photo de CNI imprimée à un selfie tolère un écart important (0,40).
Comparer deux captures webcam prises à quelques secondes d'intervalle ne le
tolère pas (0,60). Les confondre casse l'un ou l'autre.

**`spring.profiles.active` est interdit dans un `application-<profil>.yml`.**
Spring lève `InvalidConfigDataPropertyException` au démarrage. `application-prod.yml`
était une copie de `application.yml` et contenait cette clé : démarrer en profil
prod aurait échoué. Le défaut était invisible parce que le déploiement tourne sur
le profil `dev`, ce qui est le vrai problème.

**L'ordre des marqueurs de type de document compte.** Un titre provisoire et un
récépissé portent tous deux « Carte Nationale d'Identité » dans leur champ
« type de titre ». Les tester après le marqueur CNI les fait passer pour des
cartes définitives. Trois tests documentent cette précédence.

**Le backend réaffecte l'enregistrement OCR à partir de ce qu'il reçoit.**
N'envoyer que les champs affichés à l'écran efface donc en base ceux qui ne le
sont pas. Un formulaire doit conserver et renvoyer tous les champs, affichés ou
non.

**`curl` n'envoie pas d'en-tête `Origin`.** Un contrôle CORS ne se déclenche
donc pas en ligne de commande : une vérification de mise en service peut être
entièrement verte alors que le parcours est inutilisable dans un navigateur.

**Il y a trois frontends, dans deux dépôts.** `first-agent/frontend` est Angular.
`firstagent-frontend-afriland/` contient le back-office Angular, l'ancien
onboarding Angular, et `onboarding-next/` qui est le **parcours client réellement
servi**. Corriger un défaut du parcours client dans le mauvais dossier est une
erreur facile : elle a été commise pendant cet audit.

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
