export const environment = {
  production: true,

  /**
   * Adresse relative, et non absolue.
   *
   * Le parcours est servi par un nginx qui relaie `/api/v1/` vers le backend
   * Spring du réseau Docker. Une adresse absolue vers un autre hôte casserait
   * ce montage de trois façons : la requête sortirait du tunnel, elle
   * deviendrait une requête d'origine croisée soumise au CORS, et elle
   * dépendrait d'un nom DNS (`api.firstagent.com`) qui n'existe pas.
   *
   * Le chemin relatif suit l'origine servie, quel que soit le sous-domaine :
   * la même image fonctionne en démonstration comme en production.
   */
  apiUrl: '/api/v1',

  whatsappUrl: 'https://wa.me/237681456060'
};
