package com.firstagent.backend.audit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Comportement d'une règle de détection. */
class RegleAlerteTest {

  private RegleAlerte regle(int seuil) {
    return new RegleAlerte(
        "kyc_echecs",
        List.of(EvenementAudit.CONTROLE_KYC),
        Duration.ofHours(24),
        seuil,
        "%d échecs pour %s");
  }

  @Test
  @DisplayName("la règle se déclenche à partir du seuil, pas seulement au-delà")
  void declenche_auSeuilExact() {
    RegleAlerte regle = regle(3);

    assertThat(regle.declenche(2)).isFalse();
    // Le seuil est inclusif : « 3 échecs » doit alerter, sinon le réglage
    // affiché ne correspond pas au comportement observé.
    assertThat(regle.declenche(3)).isTrue();
    assertThat(regle.declenche(4)).isTrue();
  }

  @Test
  @DisplayName("un seuil nul ou négatif désactive la règle")
  void active_seuilNulOuNegatif() {
    assertThat(regle(0).active()).isFalse();
    assertThat(regle(-1).active()).isFalse();
    assertThat(regle(1).active()).isTrue();

    // Une règle inactive ne se déclenche jamais, quel que soit le compte :
    // « seuil zéro » ne doit surtout pas vouloir dire « alerter à chaque fois ».
    assertThat(regle(0).declenche(1_000)).isFalse();
  }

  @Test
  @DisplayName("la référence identifie la règle et sa cible")
  void reference_combineCodeEtCible() {
    // C'est cette référence qui permet de ne pas réécrire la même alerte à
    // chaque balayage : deux cibles distinctes doivent en avoir deux.
    assertThat(regle(2).reference("+237699000001")).isEqualTo("kyc_echecs:+237699000001");
    assertThat(regle(2).reference("+237699000002"))
        .isNotEqualTo(regle(2).reference("+237699000001"));
  }

  @Test
  @DisplayName("le message reprend le compte et la cible")
  void message_remplitLeModele() {
    assertThat(regle(2).message(5, "+237699000001")).isEqualTo("5 échecs pour +237699000001");
  }

  @Test
  @DisplayName("une règle sans code ou sans événement est refusée")
  void construction_refuseUneRegleIncomplete() {
    assertThatThrownBy(() -> new RegleAlerte("", List.of("X"), Duration.ofHours(1), 1, "%d %s"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RegleAlerte("code", List.of(), Duration.ofHours(1), 1, "%d %s"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RegleAlerte("code", null, Duration.ofHours(1), 1, "%d %s"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("la liste d'événements est figée à la construction")
  void construction_figeLaListeDEvenements() {
    List<String> modifiable = new ArrayList<>(List.of(EvenementAudit.CONTROLE_KYC));
    RegleAlerte regle = new RegleAlerte("code", modifiable, Duration.ofHours(1), 1, "%d %s");

    modifiable.add(EvenementAudit.OTP_ERRONE);

    // Sans copie, modifier la liste d'origine changerait silencieusement ce que
    // la règle observe, longtemps après sa définition.
    assertThat(regle.typesEvenements()).containsExactly(EvenementAudit.CONTROLE_KYC);
  }
}
