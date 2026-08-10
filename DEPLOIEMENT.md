# Déploiement du parcours d'onboarding

Procédure suivie le 09/08/2026 pour mettre en ligne le parcours Angular sur
`onboarding-v2.afb-firstagent.com`, et pièges rencontrés en chemin.

## Où tourne quoi

Serveur `62.169.26.178`. Quatre parcours coexistent, sur des ports distincts.

| Port | Conteneur | Ce que c'est | Sous-domaine |
|---|---|---|---|
| 8481 | `first-agent-onboarding-spa-1` | Parcours Angular précédent | `onboarding.afb-firstagent.com` |
| 8482 | `first-agent-onboarding-next-1` | Maquette Next.js, 2 écrans | aucun |
| 8483 | `first-agent-backoffice-1` | Back-office Angular | aucun |
| **8484** | **`first-agent-onboarding-v2`** | **Ce dépôt, version corrigée** | **`onboarding-v2.afb-firstagent.com`** |

Le backend Spring qu'ils interrogent tous est `first-agent-onboarding-1`
(port 8082 vers l'extérieur, `onboarding:8080` sur le réseau Docker).

Rien de l'existant n'a été remplacé : le déploiement se fait à côté, de sorte
qu'un échec ne prive personne d'une démonstration.

## Construire

```bash
cd frontend
npm ci
npx ng build --configuration production
```

La sortie va dans `dist/frontend/browser`, environ 600 Ko, polices comprises.

### Piège : l'adresse de l'API

`environment.prod.ts` portait `https://api.firstagent.com/api/v1`, un nom qui
n'existe pas. Le parcours n'aurait joint aucun backend.

Elle vaut désormais `/api/v1`, en relatif. C'est nginx qui relaie vers le
backend du réseau Docker. Une adresse absolue casserait ce montage de trois
façons : la requête sortirait du tunnel, deviendrait une requête d'origine
croisée soumise au CORS, et dépendrait d'un DNS inexistant. Le chemin relatif
suit l'origine servie, donc la même image fonctionne en démonstration comme en
production.

## Déployer

Le conteneur monte le build et `nginx.conf` en lecture seule : il n'y a pas
d'image à construire, et une nouvelle livraison se résume à remplacer le
contenu de `html/` puis à redémarrer.

```bash
# Depuis le poste : envoyer dist/frontend/browser et frontend/nginx.conf
#   vers /opt/onboarding-v2/{html,nginx.conf}

docker rm -f first-agent-onboarding-v2 2>/dev/null
docker run -d --name first-agent-onboarding-v2 --restart unless-stopped \
  --network first-agent_afribank-net -p 8484:80 \
  -v /opt/onboarding-v2/html:/usr/share/nginx/html:ro \
  -v /opt/onboarding-v2/nginx.conf:/etc/nginx/conf.d/default.conf:ro \
  nginx:1.27-alpine
```

### Piège : le nom du réseau

Le réseau s'appelle `first-agent_afribank-net` et non `afribank-net` : Docker
Compose préfixe du nom de projet. Sans le bon nom, le conteneur démarre mais
nginx ne résout pas `onboarding` et tout appel d'API échoue.

## Exposer en HTTPS

Le port 8484 répond publiquement en HTTP, mais cela ne suffit pas : les
navigateurs refusent `getUserMedia` hors contexte sécurisé, et **le scan de la
pièce comme le défi de vivacité ne démarreraient pas**. Le HTTPS vient du
tunnel Cloudflare.

```bash
cloudflared tunnel route dns f740fb53-913c-42df-9783-8f4e2ff886b7 \
  onboarding-v2.afb-firstagent.com
# puis ajouter la règle dans /root/.cloudflared/config-newbot.yml
systemctl restart cloudflared-newbot
```

### Piège, et incident du 09/08/2026

Ce tunnel sert aussi le bot et Keycloak. Une insertion faite **une ligne trop
haut** a coupé en deux la règle voisine : `onboarding.afb-firstagent.com` s'est
retrouvée sans service et la suivante avec deux. Le tunnel a refusé de démarrer
et **quatre hôtes de production sont tombés une minute**.

Deux enseignements, appliqués depuis :

1. **Sauvegarder avant** de modifier. C'est ce qui a permis de restaurer
   immédiatement (`config-newbot.yml.avant-onboarding-v2`).
2. **Valider avant de redémarrer.** Modifier la structure YAML plutôt que des
   lignes, puis relire le fichier et vérifier que chaque règle nommée porte un
   et un seul service, et que le fourre-tout reste en dernier. Le service n'est
   redémarré que si ces conditions sont réunies, et restauré automatiquement si
   un hôte ne répond plus ensuite.

Une sauvegarde sans validation ne suffit pas : elle raccourcit la panne, elle
ne l'évite pas.

## Vérifier

Depuis le serveur, les quatre hôtes du tunnel doivent répondre :

```bash
for h in new-bot auth onboarding onboarding-v2; do
  curl -s -o /dev/null -w "$h : %{http_code}\n" \
    --max-time 25 "https://$h.afb-firstagent.com/"
done
```

Attendu : `302`, `302`, `200`, `200`. Un `530` signale un tunnel qui ne démarre
pas, donc une configuration invalide.

Puis, sur le parcours déployé :

| Contrôle | Attendu |
|---|---|
| `/` | 200 |
| `/onboarding/welcome` | 200, preuve que le routage d'application fonctionne |
| `/assets/fonts/dm-sans-400.woff2` | 200, polices servies localement |
| `POST /api/v1/accounts/verify` | 400 avec un message métier, preuve que le relais atteint le backend |

Un `GET` sur `/api/v1/accounts/verify` rend 500 : c'est le comportement du
backend sur un point d'entrée qui attend un POST, identique sur le parcours
précédent. Ce n'est pas un défaut du déploiement.

## Piège : les origines CORS

Le navigateur joint un en-tête `Origin` à toute requête POST, **y compris en
même origine**. Le backend le confronte à sa liste d'origines autorisées ; un
sous-domaine absent de cette liste reçoit un 403 « Invalid CORS request », que
le parcours affiche sous la forme trompeuse d'une erreur d'analyse JSON
(« Unexpected token 'I' … is not valid JSON »), parce qu'il tente de lire comme
du JSON un corps qui est du texte brut.

**Ce défaut ne se voit pas en ligne de commande.** `curl` n'envoie pas d'en-tête
`Origin`, donc aucun contrôle ne se déclenche et le test passe. Il n'apparaît
que dans un navigateur. Les vérifications de mise en service du 09/08 étaient
toutes vertes alors que le parcours était inutilisable.

À l'ouverture d'un sous-domaine, ajouter l'origine côté backend, dans
`docker-compose.deploy.liveness.yml`, service `onboarding` :

    APP_CORS_ALLOWED_ORIGINS: https://onboarding.afb-firstagent.com,...,https://onboarding-v2.afb-firstagent.com

puis recréer le seul service concerné :

    docker compose -p first-agent -f docker-compose.deploy.yml \
      -f docker-compose.deploy.extra.yml -f docker-compose.deploy.liveness.yml \
      up -d --no-deps onboarding

Vérifier AVEC l'en-tête, seul contrôle qui vaille :

    curl -s -o /dev/null -w '%{http_code}\n' -X POST \
      -H 'Content-Type: application/json' \
      -H 'Origin: https://onboarding-v2.afb-firstagent.com' \
      -d '{"accountSuffix":"000010000007582781"}' \
      http://localhost:8484/api/v1/accounts/verify

Attendu : 200, et un en-tête `Access-Control-Allow-Origin` en réponse.

⚠️ Cette variable vit dans `daniellandry/firstagent-backend-afriland`, un dépôt
distinct de celui-ci. Elle est appliquée sur le serveur mais **n'y est pas
commitée** : un `git pull` la perdrait.

Dans le présent dépôt, `CorsConfiguration` lit désormais
`app.cors.allowed-origins` au lieu d'une liste figée à la compilation.

## Ce que la CSP autorise

La politique servie est plus stricte que celle du conteneur précédent : les
polices étant embarquées, `fonts.googleapis.com` et `fonts.gstatic.com` ont été
retirées. La caméra reste autorisée, sans quoi le parcours ne fonctionnerait
pas ; `img-src data: blob:` couvre les aperçus des faces de la pièce et les
images capturées.
