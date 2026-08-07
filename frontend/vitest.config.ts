import { defineConfig } from 'vitest/config';

/**
 * Configuration du lanceur de tests.
 *
 * Elle n'existe que pour une raison : la mesure de couverture faisait mourir
 * les processus de travail par épuisement mémoire. Chaque fork porte sa propre
 * instrumentation, et leur nombre par défaut suit celui des cœurs.
 *
 * Le symptôme était trompeur. Les tests semblaient passer, mais 5 fichiers sur
 * 25 disparaissaient silencieusement du décompte, et la couverture rapportée
 * variait d'une exécution à l'autre : 63,3 % puis 58,2 % sur le même code. Une
 * mesure qui change sans que le code change ne mesure rien, et aurait fait
 * prendre pour une régression ce qui n'était qu'un processus mort.
 *
 * LE CORRECTIF EST CONTRE-INTUITIF : il faut MOINS de mémoire, pas plus.
 * Élargir le tas à 8 Go a aggravé la situation jusqu'à l'échec d'allocation
 * système, le poste n'ayant que 3,3 Go libres sur 16. La combinaison qui tient
 * est un processus unique et un tas modeste :
 *
 *   NODE_OPTIONS=--max-old-space-size=2048 ng test --coverage \
 *       --runner-config vitest.config.ts
 *
 * C'est ce que fait le script `npm run test:coverage`.
 */
export default defineConfig({
  test: {
    pool: 'forks',
    poolOptions: {
      forks: {
        // Quelques processus, et non un seul ni autant que de cœurs.
        //
        // Un processus unique a d'abord semblé la réponse, mais il accumule
        // l'environnement de chaque fichier de test : le coût croît avec leur
        // nombre, et la mesure a recassé dès que la suite est passée de 25 à
        // 27 fichiers. À l'inverse, les 16 forks par défaut saturent les 3 Go
        // libres de la machine.
        //
        // Deux processus recyclés bornent les deux effets à la fois.
        maxForks: 2,
        minForks: 1,
        // Le plafond est ici et non dans une variable d'environnement : une
        // mesure qui ne tient que si l'on pense à exporter NODE_OPTIONS avant
        // de la lancer finit par être lancée sans, et par mentir.
        //
        // 1,5 Go par processus, soit MOINS que le défaut de Node. C'est le
        // sens du réglage : brider pour que le ramasse-miettes travaille, au
        // lieu de laisser grossir jusqu'au refus du système.
        execArgv: ['--max-old-space-size=1536'],
      },
    },
    coverage: {
      provider: 'v8',
      reporter: ['text-summary', 'lcov'],
      // Le code produit et la configuration ne disent rien sur la qualité de
      // ce qui est écrit ici.
      exclude: [
        '**/node_modules/**',
        '**/dist/**',
        '**/.angular/**',
        '**/*.spec.ts',
        'src/main.ts',
        'src/**/*.config.ts',
        'src/**/*.routes.ts',
        'src/environments/**',
      ],
    },
  },
});
