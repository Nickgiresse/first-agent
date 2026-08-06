package com.firstagent.backend.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountVerificationRequest {
  @NotBlank(message = "Les 18 chiffres du compte sont obligatoires")
  @Pattern(regexp = "\\d{18}", message = "Le suffixe doit contenir exactement 18 chiffres")
  private String accountSuffix;

  /**
   * Numéro WhatsApp du client, transmis par le bot qui ouvre le parcours.
   *
   * <p>Ce champ n'est jamais alimenté par le navigateur : le bot connaît son interlocuteur et le
   * dépose ici lors de l'appel qui ouvre la session. Il sert à vérifier que le compte saisi
   * appartient bien à ce numéro.
   *
   * <p>Facultatif : un parcours ouvert directement depuis le web n'en a pas, et le contrôle
   * d'appartenance ne s'applique alors pas. Ce mode doit rester réservé aux environnements de test,
   * faute de quoi il suffirait de contourner le bot pour échapper au contrôle.
   */
  @Pattern(regexp = "^$|^\\+?[0-9 ().-]{8,20}$", message = "Numéro de téléphone invalide")
  private String phoneNumber;
}
