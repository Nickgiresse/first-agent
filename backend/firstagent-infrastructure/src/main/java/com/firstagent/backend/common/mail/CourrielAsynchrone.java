package com.firstagent.backend.common.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Envoi de courriel hors du fil de la requête.
 *
 * <p>Mesuré le 16/08/2026 sur le parcours réel : la demande de code à usage unique répondait en
 * <b>4,03 secondes</b>, passées presque entièrement dans le dialogue SMTP avec Office 365 (poignée
 * de main TLS, connexion rouverte à chaque envoi). Le client regardait un bouton inerte pendant ce
 * temps, sur une étape qui ne lui demande qu'une adresse. Après passage en asynchrone : 77 ms,
 * mesuré dans le navigateur.
 *
 * <p>Deux appelants partagent ce composant, le code à usage unique et le lien de réinitialisation
 * du PIN. Tous deux enregistrent leur jeton <b>avant</b> l'envoi : le jeton reste donc valide quel
 * que soit le sort du courriel, et l'écran propose un renvoi. C'est ce qui rend l'asynchrone
 * défendable ici plutôt que hasardeux.
 *
 * <p>Ce qu'on accepte en échange : si l'envoi échoue, l'utilisateur lit « envoyé » alors que rien
 * n'arrive. Il attend, puis renvoie. Moins bon qu'une erreur immédiate, mais quatre secondes à
 * chaque demande coûtent à tout le monde, alors que l'échec d'envoi est l'exception. L'échec est
 * journalisé en erreur avec l'adresse visée, faute de quoi une panne de messagerie se devinerait au
 * lieu de se voir.
 *
 * <p>Composant distinct et non méthode privée annotée : Spring n'intercepte {@code @Async} que sur
 * un appel passant par le proxy. Une méthode annotée appelée depuis la même classe s'exécuterait de
 * façon synchrone, sans que rien ne le signale.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourrielAsynchrone {

  private final JavaMailSender mailSender;

  @Value("${app.mail.from-address}")
  private String adresseExpediteur;

  /**
   * @param motif libellé court journalisé en cas d'échec, pour distinguer une panne du code à usage
   *     unique d'une panne de la réinitialisation du PIN.
   */
  @Async
  public void envoyer(String destinataire, String objet, String corps, String motif) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(adresseExpediteur);
    message.setTo(destinataire);
    message.setSubject(objet);
    message.setText(corps);
    try {
      mailSender.send(message);
      log.info("Courriel « {} » envoyé.", motif);
    } catch (RuntimeException e) {
      log.error(
          "Envoi du courriel « {} » vers {} échoué : {}", motif, destinataire, e.getMessage());
    }
  }
}
