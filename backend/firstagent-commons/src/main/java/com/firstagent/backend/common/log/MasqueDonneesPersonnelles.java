package com.firstagent.backend.common.log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masque les données personnelles dans les traces applicatives.
 *
 * <h2>Pourquoi</h2>
 *
 * <p>Les journaux techniques quittent le périmètre de la base : ils partent vers un agrégateur,
 * sont lus par des exploitants, conservés longtemps et sauvegardés ailleurs. Un numéro de téléphone
 * ou un RIB qui s'y trouve échappe à toutes les protections mises autour des données elles-mêmes,
 * et il y reste. Le journal d'audit, lui, est fait pour porter ces données et les protège ; les
 * traces techniques n'ont pas cette vocation.
 *
 * <h2>Masquage partiel, et non suppression</h2>
 *
 * <p>Chaque valeur garde un début et une fin. C'est délibéré : un exploitant doit pouvoir suivre un
 * même client d'une ligne à l'autre pour comprendre un incident, ce qu'une suppression complète
 * rendrait impossible. Ce qui reste ne permet pas de reconstituer la valeur, mais permet de la
 * reconnaître.
 *
 * <h2>Ce que cela ne fait pas</h2>
 *
 * <p>Le masquage est irréversible : rien ne permet de retrouver la valeur d'origine depuis la
 * trace, et c'est le but. Il ne remplace pas la discipline consistant à ne pas journaliser ce dont
 * on n'a pas besoin ; il rattrape ce qui passe malgré tout.
 */
public final class MasqueDonneesPersonnelles {

  private MasqueDonneesPersonnelles() {}

  /** Ce qui remplace la partie cachée. Un caractère unique, pour ne pas suggérer une longueur. */
  private static final String ELISION = "…";

  /**
   * RIB Afriland : 23 chiffres, éventuellement groupés par espaces ou tirets.
   *
   * <p>Traité en premier : ses 23 chiffres pourraient autrement être happés par le motif des
   * numéros de téléphone, qui produirait un masquage moins pertinent.
   */
  private static final Pattern RIB =
      Pattern.compile("\\b(\\d{5})[-\\s]?(\\d{5})[-\\s]?(\\d{11})[-\\s]?(\\d{2})\\b");

  /** Adresse électronique. */
  private static final Pattern COURRIEL =
      Pattern.compile("\\b([\\w.+-])[\\w.+-]*(@[\\w-]+(?:\\.[\\w-]+)+)\\b");

  /**
   * Numéro de téléphone au format international, ou national camerounais à 9 chiffres.
   *
   * <p>Placé après le RIB et le courriel : appliqué en premier, il découperait leurs chiffres.
   */
  private static final Pattern TELEPHONE = Pattern.compile("(\\+\\d{6,15})|\\b([2-9]\\d{8})\\b");

  /**
   * Masque les données personnelles reconnues dans un texte.
   *
   * @param texte trace brute, éventuellement nulle
   * @return le texte où chaque valeur reconnue ne laisse qu'un début et une fin
   */
  public static String appliquer(String texte) {
    if (texte == null || texte.isEmpty()) {
      return texte;
    }

    String resultat = masquerRib(texte);
    resultat = masquerCourriel(resultat);
    return masquerTelephone(resultat);
  }

  /**
   * Le code banque reste lisible, le reste disparaît.
   *
   * <p>Le code banque n'identifie personne : il désigne l'établissement, et le conserver permet de
   * distinguer un RIB Afriland d'un autre sans rien révéler du titulaire.
   */
  private static String masquerRib(String texte) {
    Matcher m = RIB.matcher(texte);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + ELISION + m.group(4)));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /** Le domaine reste lisible, la partie locale se réduit à sa première lettre. */
  private static String masquerCourriel(String texte) {
    Matcher m = COURRIEL.matcher(texte);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + ELISION + m.group(2)));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  /** Indicatif et deux derniers chiffres conservés. */
  private static String masquerTelephone(String texte) {
    Matcher m = TELEPHONE.matcher(texte);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      String numero = m.group();
      m.appendReplacement(sb, Matcher.quoteReplacement(reduire(numero)));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static String reduire(String numero) {
    // Assez de tête pour reconnaître le pays et l'opérateur, assez de queue pour
    // suivre un même client d'une ligne à l'autre, pas assez pour l'appeler.
    int tete = numero.startsWith("+") ? 6 : 3;
    if (numero.length() <= tete + 2) {
      return ELISION;
    }
    return numero.substring(0, tete) + ELISION + numero.substring(numero.length() - 2);
  }
}
