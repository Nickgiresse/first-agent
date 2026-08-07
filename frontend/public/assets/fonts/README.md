# Polices de l'application

Les polices sont hébergées ici, dans le dépôt, et non chargées depuis un
service distant.

## Pourquoi

Elles venaient de `fonts.googleapis.com`. La charte frontend §20.2 impose de
les héberger localement, et la charte backend §12 exige des builds hors ligne.
Ce n'était pas une exigence théorique : cet hôte est filtré par l'inspection
TLS du réseau interne, et la requête reçoit une page de blocage au lieu de la
feuille de style. Toute chaîne d'intégration interne butait dessus.

Voir `RESEAU_RESSOURCES_BLOQUEES.md` à la racine du dépôt.

## Contenu

Huit fichiers `woff2`, extraits des paquets `@fontsource/dm-sans` et
`@fontsource/manrope`, sous-ensemble **latin** uniquement.

| Fichier | Famille | Graisse |
|---|---|---|
| `dm-sans-400.woff2` | DM Sans | 400 |
| `dm-sans-500.woff2` | DM Sans | 500 |
| `dm-sans-600.woff2` | DM Sans | 600 |
| `dm-sans-700.woff2` | DM Sans | 700 |
| `manrope-500.woff2` | Manrope | 500 |
| `manrope-600.woff2` | Manrope | 600 |
| `manrope-700.woff2` | Manrope | 700 |
| `manrope-800.woff2` | Manrope | 800 |

Environ 14 Ko par fichier, 113 Ko au total. Les autres jeux de caractères
(cyrillique, grec, vietnamien) ont été écartés : ils tripleraient le poids sans
servir à ce parcours.

`woff2` seul, sans `woff` de repli : tous les navigateurs de la matrice visée
le prennent en charge, et il pèse environ 30 % de moins.

Les deux familles sont sous licence SIL Open Font, qui autorise expressément la
redistribution : les verser au dépôt ne pose pas de difficulté juridique.

## Pourquoi dans `public/` et non `src/assets/`

`angular.json` ne sert que `public/`. Des fichiers placés sous `src/assets/`
sont bien référencés par la feuille de style mais ne sont pas copiés dans la
sortie : les URL répondent alors 404 et le navigateur retombe silencieusement
sur la police système. Le défaut ne se voit qu'à l'œil, sur une page rendue.

## Comment elles sont déclarées

`src/styles/_polices.scss` porte les `@font-face`, sous un drapeau
`$polices-locales-disponibles`. Il permet de retomber proprement sur la pile
système si ces fichiers devaient être retirés, sans laisser une requête en
échec par famille et par graisse à chaque chargement de page.

## Si les fichiers doivent être régénérés

```
npm install --no-save @fontsource/dm-sans@5 @fontsource/manrope@5
```

puis copier `node_modules/@fontsource/<famille>/files/<famille>-latin-<graisse>-normal.woff2`
ici, sous le nom `<famille>-<graisse>.woff2`.
