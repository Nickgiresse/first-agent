package com.firstagent.backend.common.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Applique le masquage des données personnelles au message journalisé.
 *
 * <p>Le masquage est fait ici, au dernier moment avant l'écriture, et non au point d'appel. La
 * différence est décisive : compter sur chaque {@code log.info(...)} pour masquer ce qu'il affiche
 * suppose que personne n'oublie jamais, sur toute la durée de vie du projet et par tous ceux qui y
 * touchent. Placé dans le formateur, le masquage s'applique à tout ce qui sort, y compris aux
 * traces écrites par les bibliothèques tierces, sur lesquelles personne n'a la main.
 *
 * <p>Déclaré dans {@code logback-spring.xml} sous le mot de conversion {@code msgMasque}, à
 * utiliser partout où l'on écrirait {@code %msg}.
 */
public class ConvertisseurMasque extends ClassicConverter {

  @Override
  public String convert(ILoggingEvent evenement) {
    return MasqueDonneesPersonnelles.appliquer(evenement.getFormattedMessage());
  }
}
