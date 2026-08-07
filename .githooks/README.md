# Crochets git du dépôt

Deux contrôles, exigés par la charte frontend §7.1 et §23.2.

| Crochet | Ce qu'il vérifie |
|---|---|
| `pre-commit` | ESLint sur les fichiers frontend indexés, formatage Java sur les fichiers backend indexés |
| `commit-msg` | Convention Conventional Commits, première ligne de 72 caractères au plus |

## Activation

Une seule fois, après le clonage :

```bash
git config core.hooksPath .githooks
```

Sous Windows, si les crochets ne se déclenchent pas, vérifier que git trouve un
shell POSIX. Git for Windows en fournit un, il n'y a normalement rien à faire.

## Pourquoi pas husky

Husky s'installe par un script `prepare` de npm, donc uniquement chez ceux qui
lancent `npm install`. Ce dépôt porte trois stacks : un développeur travaillant
sur le backend Java ou le service Python n'installe jamais le frontend, et
pousserait sans aucun contrôle. Des crochets versionnés en shell couvrent tout
le monde au prix d'une commande à la prise en main.

## Ce que les crochets ne font pas

Ils ne remplacent pas l'intégration continue. Un crochet peut être contourné
par `--no-verify`, et c'est parfois légitime : un commit de sauvegarde en cours
de travail, une reprise après un conflit. Leur rôle est d'éviter l'erreur
d'inattention, pas de garantir quoi que ce soit.

Le `pre-commit` reste volontairement rapide : il ne lance ni la compilation ni
les tests. Un crochet de deux minutes est un crochet que l'on finit par
désactiver.
