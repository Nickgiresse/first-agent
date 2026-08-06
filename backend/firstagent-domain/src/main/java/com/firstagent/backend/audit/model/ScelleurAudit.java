package com.firstagent.backend.audit.model;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Scelle une entrée du journal par un HMAC-SHA256 couvrant son contenu et l'empreinte de l'entrée
 * précédente.
 *
 * <h2>Ce que cela garantit</h2>
 *
 * <p>Modifier une entrée, en supprimer une, en insérer une ou simplement les réordonner rompt la
 * chaîne à partir du point touché. La vérification désigne alors la première rupture.
 *
 * <h2>Ce que cela ne garantit pas</h2>
 *
 * <p>Un HMAC n'est pas une signature asymétrique : quiconque détient la clé peut reforger une
 * chaîne entière et cohérente. La clé doit donc vivre ailleurs que la base, idéalement dans un
 * coffre. Sceller un historique existant ne prouve par ailleurs rien sur le passé : une entrée déjà
 * falsifiée entre dans la chaîne avec sa valeur falsifiée. La garantie court à partir de la date de
 * scellement, ce qui est dit ici plutôt que laissé à supposer.
 *
 * <p>La clé est reçue à la construction et non lue depuis l'environnement : le domaine décrit
 * comment sceller, l'infrastructure décide d'où vient le secret.
 */
public final class ScelleurAudit {

  /**
   * Séparateur des champs dans la forme canonique.
   *
   * <p>Le séparateur d'unité (U+001F) ne peut apparaître dans aucune valeur. Sans séparateur, les
   * couples («&nbsp;ab&nbsp;», «&nbsp;c&nbsp;») et («&nbsp;a&nbsp;», «&nbsp;bc&nbsp;») produiraient
   * la même empreinte, et une entrée pourrait donc être remaniée sans rompre le scellé.
   */
  private static final char SEPARATEUR = '\u001F';

  private static final String ALGORITHME = "HmacSHA256";

  /** En deçà, HMAC-SHA256 perd la marge de sécurité attendue. */
  private static final int TAILLE_CLE_RECOMMANDEE = 32;

  private final byte[] cle;

  public ScelleurAudit(byte[] cle) {
    if (cle == null || cle.length == 0) {
      throw new IllegalArgumentException("La clé de scellement du journal est obligatoire.");
    }
    this.cle = cle.clone();
  }

  /** Indique si la clé atteint la taille recommandée, pour que l'appelant puisse alerter. */
  public boolean cleTropCourte() {
    return cle.length < TAILLE_CLE_RECOMMANDEE;
  }

  /** Empreinte scellée de l'entrée. */
  public String empreinte(EntreeAudit entree) {
    try {
      Mac mac = Mac.getInstance(ALGORITHME);
      mac.init(new SecretKeySpec(cle, ALGORITHME));
      byte[] brut = mac.doFinal(canonique(entree).getBytes(StandardCharsets.UTF_8));
      return hexadecimal(brut);
    } catch (java.security.GeneralSecurityException e) {
      // HmacSHA256 est exigé de toute implémentation Java : y échouer signale
      // une plateforme cassée, pas une situation à rattraper.
      throw new IllegalStateException("HMAC-SHA256 indisponible sur cette plateforme", e);
    }
  }

  /**
   * Compare deux empreintes sans fuite de temps.
   *
   * <p>Une comparaison de chaînes ordinaire s'arrête au premier octet différent : le temps de
   * réponse renseignerait alors sur le nombre d'octets déjà devinés, et permettrait de reconstituer
   * une empreinte valide octet par octet.
   */
  public boolean correspond(String attendue, String trouvee) {
    if (attendue == null || trouvee == null) {
      return false;
    }
    return MessageDigest.isEqual(
        attendue.getBytes(StandardCharsets.UTF_8), trouvee.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Sérialisation déterministe de l'entrée.
   *
   * <p>L'ordre des champs est figé et toute valeur absente devient une chaîne vide : deux
   * exécutions doivent produire exactement le même texte, faute de quoi la vérification signalerait
   * des ruptures imaginaires.
   */
  private String canonique(EntreeAudit e) {
    StringBuilder sb = new StringBuilder(256);
    ajouter(sb, texte(e.identifiant()));
    ajouter(sb, horodatage(e.horodatage()));
    ajouter(sb, texte(e.telephone()));
    ajouter(sb, texte(e.typeEvenement()));
    ajouter(sb, texte(e.statut()));
    ajouter(sb, texte(e.acteur()));
    ajouter(sb, texte(e.typeActeur()));
    ajouter(sb, texte(e.ipSource()));
    ajouter(sb, montant(e.montant()));
    ajouter(sb, texte(e.devise()));
    ajouter(sb, texte(e.reference()));
    ajouter(sb, texte(e.details()));
    sb.append(texte(e.empreintePrecedente()));
    return sb.toString();
  }

  private void ajouter(StringBuilder sb, String valeur) {
    sb.append(valeur).append(SEPARATEUR);
  }

  private String texte(Object valeur) {
    return valeur == null ? "" : valeur.toString();
  }

  /**
   * Horodatage en UTC, tronqué à la microseconde.
   *
   * <p>La troncature n'est pas cosmétique. {@link Instant} porte la nanoseconde, PostgreSQL n'en
   * conserve que la microseconde : sceller la valeur en mémoire puis vérifier la valeur relue
   * produirait deux empreintes différentes pour la même entrée, et la chaîne paraîtrait rompue
   * partout. La troncature est donc appliquée des deux côtés, à l'écriture comme à la vérification.
   */
  private String horodatage(Instant instant) {
    return instant == null ? "" : instant.truncatedTo(ChronoUnit.MICROS).toString();
  }

  /**
   * Montant sous une forme insensible à la représentation.
   *
   * <p>{@code 100}, {@code 100.0} et {@code 100.00} désignent la même somme mais ont trois
   * représentations textuelles. La base rend d'ailleurs rarement l'échelle fournie à l'écriture.
   * Sans normalisation, l'empreinte dépendrait de l'échelle et la vérification échouerait sur des
   * entrées pourtant intactes.
   */
  private String montant(BigDecimal valeur) {
    return valeur == null ? "" : valeur.stripTrailingZeros().toPlainString();
  }

  private String hexadecimal(byte[] octets) {
    StringBuilder sb = new StringBuilder(octets.length * 2);
    for (byte b : octets) {
      sb.append(Character.forDigit((b >> 4) & 0xF, 16));
      sb.append(Character.forDigit(b & 0xF, 16));
    }
    return sb.toString();
  }
}
