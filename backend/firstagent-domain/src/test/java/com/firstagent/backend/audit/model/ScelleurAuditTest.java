package com.firstagent.backend.audit.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le scellement doit détecter toute retouche.
 *
 * <p>Un journal réputé infalsifiable qui ne démontre pas qu'il détecte la falsification ne vaut que
 * par la confiance qu'on lui accorde. Ces tests exercent donc chaque champ couvert : si l'un d'eux
 * sortait un jour de la forme canonique, le test correspondant tomberait.
 */
class ScelleurAuditTest {

  private static final byte[] CLE =
      "cle-de-test-de-trente-deux-octets-minimum".getBytes(StandardCharsets.UTF_8);

  private final ScelleurAudit scelleur = new ScelleurAudit(CLE);

  private EntreeAudit entreeType() {
    return new EntreeAudit(
        UUID.fromString("11111111-1111-1111-1111-111111111111"),
        Instant.parse("2026-08-06T10:00:00.123456Z"),
        "+237699000001",
        "KYC_VERIFIED",
        "SUCCESS",
        "+237699000001",
        TypeActeur.CLIENT,
        "10.0.0.1",
        new BigDecimal("1500.00"),
        "XAF",
        "REF-1",
        "vérification aboutie",
        EntreeAudit.GENESE);
  }

  @Test
  @DisplayName("la même entrée produit toujours la même empreinte")
  void empreinte_estDeterministe() {
    assertThat(scelleur.empreinte(entreeType())).isEqualTo(scelleur.empreinte(entreeType()));
  }

  @Test
  @DisplayName("deux clés différentes ne produisent pas la même empreinte")
  void empreinte_dependDeLaCle() {
    ScelleurAudit autre = new ScelleurAudit("une-tout-autre-cle".getBytes(StandardCharsets.UTF_8));

    assertThat(autre.empreinte(entreeType())).isNotEqualTo(scelleur.empreinte(entreeType()));
  }

  @Test
  @DisplayName("modifier un champ quelconque change l'empreinte")
  void empreinte_couvreTousLesChamps() {
    EntreeAudit origine = entreeType();
    String reference = scelleur.empreinte(origine);

    // Chaque variante ne touche qu'un champ. Si l'un d'eux disparaissait de la
    // forme canonique, son empreinte redeviendrait égale à la référence.
    assertThat(scelleur.empreinte(avecTelephone(origine, "+237699000002"))).isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecEvenement(origine, "KYC_REFUSED"))).isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecStatut(origine, "FAILURE"))).isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecActeur(origine, "conseiller-42"))).isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecTypeActeur(origine, TypeActeur.ADMIN)))
        .isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecIp(origine, "10.0.0.2"))).isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecMontant(origine, new BigDecimal("1500.01"))))
        .isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecDevise(origine, "EUR"))).isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecReference(origine, "REF-2"))).isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecDetails(origine, "autre chose"))).isNotEqualTo(reference);
    assertThat(scelleur.empreinte(avecMaillon(origine, "autre-maillon"))).isNotEqualTo(reference);
  }

  @Test
  @DisplayName("le maillon précédent fait partie du scellé, ce qui interdit le réordonnancement")
  void empreinte_couvreLeMaillonPrecedent() {
    EntreeAudit premiere = entreeType();
    EntreeAudit memeContenuAutreMaillon = avecMaillon(premiere, "abcdef");

    // Sans cette propriété, deux entrées de même contenu seraient
    // interchangeables et l'ordre du journal ne prouverait rien.
    assertThat(scelleur.empreinte(memeContenuAutreMaillon))
        .isNotEqualTo(scelleur.empreinte(premiere));
  }

  @Test
  @DisplayName("l'échelle du montant n'influe pas sur l'empreinte")
  void empreinte_estInsensibleALEchelleDuMontant() {
    // La base ne restitue pas nécessairement l'échelle fournie à l'écriture.
    // Sans normalisation, une entrée intacte paraîtrait falsifiée après relecture.
    EntreeAudit deuxDecimales = avecMontant(entreeType(), new BigDecimal("1500.00"));
    EntreeAudit sansDecimale = avecMontant(entreeType(), new BigDecimal("1500"));

    assertThat(scelleur.empreinte(deuxDecimales)).isEqualTo(scelleur.empreinte(sansDecimale));
  }

  @Test
  @DisplayName("la précision sous la microseconde n'influe pas sur l'empreinte")
  void empreinte_estInsensibleAuxNanosecondes() {
    // PostgreSQL ne conserve que la microseconde. Sceller la nanoseconde ferait
    // paraître toute la chaîne rompue dès la première relecture.
    Instant precis = Instant.parse("2026-08-06T10:00:00.123456Z").plusNanos(789);
    EntreeAudit avecNanos = avecHorodatage(entreeType(), precis);
    EntreeAudit tronque = avecHorodatage(entreeType(), precis.truncatedTo(ChronoUnit.MICROS));

    assertThat(scelleur.empreinte(avecNanos)).isEqualTo(scelleur.empreinte(tronque));
  }

  @Test
  @DisplayName("déplacer du texte d'un champ au suivant change l'empreinte")
  void empreinte_resisteAuGlissementEntreChamps() {
    // Sans séparateur, ("ab", "c") et ("a", "bc") donneraient le même texte
    // canonique : une entrée pourrait être remaniée sans rompre le scellé.
    EntreeAudit gauche = avecDetails(avecReference(entreeType(), "AB"), "C");
    EntreeAudit droite = avecDetails(avecReference(entreeType(), "A"), "BC");

    assertThat(scelleur.empreinte(gauche)).isNotEqualTo(scelleur.empreinte(droite));
  }

  @Test
  @DisplayName("une empreinte se compare correctement, y compris à une valeur absente")
  void correspond_traiteLesCasLimites() {
    String empreinte = scelleur.empreinte(entreeType());

    assertThat(scelleur.correspond(empreinte, empreinte)).isTrue();
    assertThat(scelleur.correspond(empreinte, "autre")).isFalse();
    assertThat(scelleur.correspond(empreinte, null)).isFalse();
    assertThat(scelleur.correspond(null, empreinte)).isFalse();
  }

  @Test
  @DisplayName("une clé vide est refusée à la construction")
  void construction_refuseUneCleVide() {
    assertThatThrownBy(() -> new ScelleurAudit(new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ScelleurAudit(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("une clé trop courte est signalée sans être refusée")
  void cleTropCourte_estSignalee() {
    assertThat(new ScelleurAudit("court".getBytes(StandardCharsets.UTF_8)).cleTropCourte())
        .isTrue();
    assertThat(scelleur.cleTropCourte()).isFalse();
  }

  // Fabriques de variantes : un seul champ change à chaque fois.

  private EntreeAudit avecHorodatage(EntreeAudit e, Instant v) {
    return new EntreeAudit(
        e.identifiant(),
        v,
        e.telephone(),
        e.typeEvenement(),
        e.statut(),
        e.acteur(),
        e.typeActeur(),
        e.ipSource(),
        e.montant(),
        e.devise(),
        e.reference(),
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecTelephone(EntreeAudit e, String v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        v,
        e.typeEvenement(),
        e.statut(),
        e.acteur(),
        e.typeActeur(),
        e.ipSource(),
        e.montant(),
        e.devise(),
        e.reference(),
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecEvenement(EntreeAudit e, String v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        v,
        e.statut(),
        e.acteur(),
        e.typeActeur(),
        e.ipSource(),
        e.montant(),
        e.devise(),
        e.reference(),
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecStatut(EntreeAudit e, String v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        e.typeEvenement(),
        v,
        e.acteur(),
        e.typeActeur(),
        e.ipSource(),
        e.montant(),
        e.devise(),
        e.reference(),
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecActeur(EntreeAudit e, String v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        e.typeEvenement(),
        e.statut(),
        v,
        e.typeActeur(),
        e.ipSource(),
        e.montant(),
        e.devise(),
        e.reference(),
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecTypeActeur(EntreeAudit e, TypeActeur v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        e.typeEvenement(),
        e.statut(),
        e.acteur(),
        v,
        e.ipSource(),
        e.montant(),
        e.devise(),
        e.reference(),
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecIp(EntreeAudit e, String v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        e.typeEvenement(),
        e.statut(),
        e.acteur(),
        e.typeActeur(),
        v,
        e.montant(),
        e.devise(),
        e.reference(),
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecMontant(EntreeAudit e, BigDecimal v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        e.typeEvenement(),
        e.statut(),
        e.acteur(),
        e.typeActeur(),
        e.ipSource(),
        v,
        e.devise(),
        e.reference(),
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecDevise(EntreeAudit e, String v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        e.typeEvenement(),
        e.statut(),
        e.acteur(),
        e.typeActeur(),
        e.ipSource(),
        e.montant(),
        v,
        e.reference(),
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecReference(EntreeAudit e, String v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        e.typeEvenement(),
        e.statut(),
        e.acteur(),
        e.typeActeur(),
        e.ipSource(),
        e.montant(),
        e.devise(),
        v,
        e.details(),
        e.empreintePrecedente());
  }

  private EntreeAudit avecDetails(EntreeAudit e, String v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        e.typeEvenement(),
        e.statut(),
        e.acteur(),
        e.typeActeur(),
        e.ipSource(),
        e.montant(),
        e.devise(),
        e.reference(),
        v,
        e.empreintePrecedente());
  }

  private EntreeAudit avecMaillon(EntreeAudit e, String v) {
    return new EntreeAudit(
        e.identifiant(),
        e.horodatage(),
        e.telephone(),
        e.typeEvenement(),
        e.statut(),
        e.acteur(),
        e.typeActeur(),
        e.ipSource(),
        e.montant(),
        e.devise(),
        e.reference(),
        e.details(),
        v);
  }
}
