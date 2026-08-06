package com.firstagent.backend.common.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le masquage doit cacher assez pour protéger, et laisser assez pour exploiter.
 *
 * <p>Les deux exigences se contredisent, et c'est tout l'intérêt de les vérifier ensemble : un
 * masquage total rendrait les journaux inutilisables, un masquage insuffisant ne protégerait rien.
 */
class MasqueDonneesPersonnellesTest {

  @Test
  @DisplayName("un numéro international garde son indicatif et ses deux derniers chiffres")
  void telephone_international() {
    String masque = MasqueDonneesPersonnelles.appliquer("Client +237699000001 bloqué");

    assertThat(masque).isEqualTo("Client +23769…01 bloqué");
    // Le numéro complet ne doit plus s'y trouver, sous aucune forme.
    assertThat(masque).doesNotContain("+237699000001");
  }

  @Test
  @DisplayName("un numéro national est masqué aussi")
  void telephone_national() {
    assertThat(MasqueDonneesPersonnelles.appliquer("appel de 699000001"))
        .isEqualTo("appel de 699…01");
  }

  @Test
  @DisplayName("un RIB ne laisse que le code banque et la clé")
  void rib_neLaisseQueLeCodeBanque() {
    String masque =
        MasqueDonneesPersonnelles.appliquer("Virement vers 10005 00001 00000075827 81 refusé");

    // Le code banque désigne l'établissement et n'identifie personne ; le
    // conserver permet de reconnaître un RIB Afriland sans rien révéler.
    assertThat(masque).isEqualTo("Virement vers 10005…81 refusé");
    assertThat(masque).doesNotContain("00000075827");
  }

  @Test
  @DisplayName("un RIB collé ou tireté est reconnu de la même façon")
  void rib_toutesFormesDeSeparateur() {
    assertThat(MasqueDonneesPersonnelles.appliquer("10005-00001-00000075827-81"))
        .isEqualTo("10005…81");
    assertThat(MasqueDonneesPersonnelles.appliquer("10005000010000007582781"))
        .isEqualTo("10005…81");
  }

  @Test
  @DisplayName("une adresse garde son domaine, pas son titulaire")
  void courriel_gardeLeDomaine() {
    String masque =
        MasqueDonneesPersonnelles.appliquer("envoi à jean.dupont@afrilandfirstbank.com échoué");

    // Le domaine sert au diagnostic (serveur, orthographe), la partie locale non.
    assertThat(masque).isEqualTo("envoi à j…@afrilandfirstbank.com échoué");
  }

  @Test
  @DisplayName("plusieurs valeurs dans une même ligne sont toutes masquées")
  void plusieursValeurs_toutesMasquees() {
    String masque =
        MasqueDonneesPersonnelles.appliquer(
            "kyc_echecs:+237699000001 compte 10005 00001 00000075827 81 "
                + "notifié à securite@afrilandfirstbank.com");

    assertThat(masque)
        .doesNotContain("+237699000001")
        .doesNotContain("00000075827")
        .contains("+23769…01")
        .contains("10005…81")
        .contains("s…@afrilandfirstbank.com");
  }

  @Test
  @DisplayName("le masquage est stable : la même valeur donne toujours la même trace")
  void masquage_estStable() {
    // C'est ce qui permet de suivre un même client d'une ligne à l'autre.
    String premier = MasqueDonneesPersonnelles.appliquer("+237699000001");
    String second = MasqueDonneesPersonnelles.appliquer("+237699000001");

    assertThat(premier).isEqualTo(second);
  }

  @Test
  @DisplayName("deux clients différents restent distinguables")
  void masquage_conserveLaDistinction() {
    // Un masquage qui rendrait tous les clients identiques empêcherait de
    // comprendre un incident impliquant plusieurs personnes.
    assertThat(MasqueDonneesPersonnelles.appliquer("+237699000001"))
        .isNotEqualTo(MasqueDonneesPersonnelles.appliquer("+237699000042"));
  }

  @Test
  @DisplayName("un texte sans donnée personnelle est rendu tel quel")
  void texteNeutre_resteIntact() {
    String neutre = "Règle kyc_echecs en échec : base indisponible";

    assertThat(MasqueDonneesPersonnelles.appliquer(neutre)).isEqualTo(neutre);
  }

  @Test
  @DisplayName("les valeurs absentes ne font pas échouer le masquage")
  void valeursAbsentes_sontTolerees() {
    assertThat(MasqueDonneesPersonnelles.appliquer(null)).isNull();
    assertThat(MasqueDonneesPersonnelles.appliquer("")).isEmpty();
  }

  @Test
  @DisplayName("un identifiant technique n'est pas confondu avec un numéro")
  void identifiantTechnique_nEstPasMasque() {
    // Les identifiants de session sont des UUID : les masquer priverait le
    // diagnostic de son principal fil conducteur.
    String trace = "session:3f2504e0-4f89-11d3-9a0c-0305e82c3301 refusée";

    assertThat(MasqueDonneesPersonnelles.appliquer(trace)).isEqualTo(trace);
  }
}
