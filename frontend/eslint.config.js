// @ts-check
const eslint = require('@eslint/js');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');
const sonarjs = require('eslint-plugin-sonarjs');

/**
 * Analyse statique du frontend (charte §7.1).
 *
 * Le jeu de règles n'est pas une reprise des recommandations par défaut : il
 * retient ce que le compilateur ne voit pas et ce que la charte nomme
 * explicitement. Une règle qui doublonne avec `strict` de TypeScript n'apporte
 * qu'un second message pour le même défaut.
 *
 * Le formatage n'est pas traité ici : Prettier s'en charge, et faire arbitrer
 * la mise en forme par deux outils produit des conflits sans fin.
 */
module.exports = tseslint.config(
  {
    // Sorties de build et dépendances : du code produit ou tiers, sur lequel
    // aucune règle de style n'a de sens et dont l'analyse coûte plus qu'elle
    // ne rapporte.
    ignores: ['dist/**', 'node_modules/**', '.angular/**', 'coverage/**'],
  },
  {
    // Fichiers de configuration à la racine du projet.
    //
    // Ils n'appartiennent à aucun tsconfig, et c'est normal : ils ne sont pas
    // compilés dans l'application. L'analyse typée ne peut donc pas les lire et
    // échoue avec « not found by the project service ». Les écarter du typage
    // plutôt que les forcer dans un tsconfig évite de faire entrer de
    // l'outillage dans le périmètre de compilation de l'application.
    files: ['*.config.ts', '*.config.js'],
    extends: [eslint.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: {
      parserOptions: { projectService: false },
    },
    rules: {
      // Ce fichier même est chargé par ESLint avant tout transpileur : il doit
      // rester en CommonJS. La règle vise les sources de l'application, où
      // `require` trahirait un module qui n'a pas suivi la migration.
      '@typescript-eslint/no-require-imports': 'off',
    },
  },
  {
    files: ['src/**/*.ts'],
    extends: [
      eslint.configs.recommended,
      ...tseslint.configs.recommended,
      ...angular.configs.tsRecommended,
      // Règles SonarSource, celles-là mêmes qu'exécute SonarQube sur du
      // TypeScript. La porte qualité de la charte §7.3 suppose un serveur, qui
      // n'existe pas encore ; le moteur d'analyse, lui, tourne hors serveur et
      // n'a aucune raison d'attendre. Restera à raccorder pour la porte
      // elle-même, le suivi dans le temps et la notion de « code nouveau ».
      sonarjs.configs.recommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      // NOMMAGE (charte §5). Le préfixe `afb-` distingue les composants
      // maison de ceux d'une bibliothèque tierce, dans le code comme dans le
      // DOM inspecté. C'est la seule règle ici dont la violation est
      // purement conventionnelle, et elle est nommée par la charte.
      '@angular-eslint/component-selector': [
        'error',
        { type: 'element', prefix: 'afb', style: 'kebab-case' },
      ],
      '@angular-eslint/directive-selector': [
        'error',
        { type: 'attribute', prefix: 'afb', style: 'camelCase' },
      ],

      // TYPAGE (charte §6.2). `any` désactive le contrôle de types là où il
      // apparaît et se propage à tout ce qu'il touche : le reste de la
      // configuration stricte ne sert plus à rien sur ce chemin.
      '@typescript-eslint/no-explicit-any': 'error',

      // Une valeur inutilisée est presque toujours le reste d'une
      // modification incomplète. Le tiret bas reste admis pour dire
      // explicitement « je sais, et c'est voulu ».
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],

      // CORRECTION. Une promesse non attendue échoue en silence : ni le
      // résultat ni l'erreur ne remontent, et le parcours continue comme si
      // l'opération avait réussi. Dans un tunnel bancaire, c'est un défaut de
      // premier ordre.
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-misused-promises': 'error',

      // `==` compare après conversion : `0 == ''` est vrai. Le cas nul est
      // laissé libre, `x != null` étant l'idiome pour « ni null ni undefined ».
      eqeqeq: ['error', 'always', { null: 'ignore' }],

      // Une trace laissée dans le code part en production, où elle publie dans
      // la console du client ce que le développeur regardait. `console.warn` et
      // `console.error` restent admis : ils signalent, ils n'inspectent pas.
      'no-console': ['error', { allow: ['warn', 'error'] }],

      // Un TODO n'est pas un défaut mais une note, et le seul qui reste
      // aujourd'hui attend une décision métier : l'adresse WhatsApp définitive
      // du service client. Bloquer dessus obligerait soit à inventer une
      // valeur, soit à effacer la note. Signalé, donc, mais pas bloquant.
      'sonarjs/todo-tag': 'warn',

      // Le garde d'onboarding rend `boolean | UrlTree`, ce qui est le contrat
      // même de `CanActivateFn` : rendre un UrlTree est la façon dont Angular
      // veut qu'un garde redirige. La règle ne connaît pas ce cadre.
      'sonarjs/function-return-type': 'off',
    },
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: __dirname,
      },
    },
  },
  {
    files: ['**/*.html'],
    extends: [
      ...angular.configs.templateRecommended,
      ...angular.configs.templateAccessibility,
    ],
    rules: {
      // ACCESSIBILITÉ (charte §16, niveau AA du WCAG 2.1). Ces règles ne
      // suffisent pas à l'atteindre, un audit outillé restant nécessaire,
      // mais elles écartent les manquements les plus courants avant qu'ils
      // n'atteignent une revue.
    },
  },
  {
    // Les tests ont d'autres besoins : un espion se déclare volontiers `any`,
    // et une assertion peut légitimement flotter.
    files: ['**/*.spec.ts'],
    rules: {
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-floating-promises': 'off',

      // Un test paramétré est plus court mais moins lisible en cas d'échec :
      // le rapport nomme le jeu de données, pas la situation métier. Sur des
      // règles réglementaires, un intitulé qui dit ce qui a cassé vaut mieux
      // que quelques lignes économisées.
      'sonarjs/parameterized-tests': 'off',

      // Deux cas de test peuvent légitimement partager la même préparation
      // sans que cela signale une duplication à corriger.
      'sonarjs/no-identical-functions': 'off',
    },
  },
);
