package com.firstagent.backend.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.firstagent.backend.account.dto.AccountVerificationRequest;
import com.firstagent.backend.account.dto.AccountVerificationResponse;
import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.account.repository.BankAccountRepository;
import com.firstagent.backend.common.exception.AccountNotFoundException;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.TypeErreurMetier;
import com.firstagent.backend.common.security.SecureSessionTokenGenerator;
import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import com.firstagent.backend.onboarding.repository.OnboardingSessionRepository;
import com.firstagent.backend.whatsappbanking.client.WhatsAppBankingClient;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Vérification du compte : la porte d'entrée du parcours.
 *
 * <p>Deux règles de sécurité s'y jouent, et aucune ne se voit à l'exécution quand elle est
 * enfreinte. Le contrôle d'appartenance interdit d'ouvrir le service sur le compte d'un tiers dont
 * on connaîtrait le RIB, et le repli sur le miroir local évite qu'une coupure du référentiel
 * n'interrompe un parcours en cours.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

  private static final String SUFFIXE = "0000100000075827813";
  private static final String NUMERO_COMPLET = "10005" + SUFFIXE;
  private static final String TELEPHONE = "+237685445511";

  @Mock private BankAccountRepository bankAccountRepository;
  @Mock private CustomerRepository customerRepository;
  @Mock private OnboardingSessionRepository onboardingSessionRepository;
  @Mock private SecureSessionTokenGenerator sessionTokenGenerator;
  @Mock private WhatsAppBankingClient whatsAppBankingClient;

  private AccountServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new AccountServiceImpl(
            bankAccountRepository,
            customerRepository,
            onboardingSessionRepository,
            sessionTokenGenerator,
            whatsAppBankingClient);
  }

  private BankAccount compte(boolean eligible, String telephone) {
    return BankAccount.builder()
        .accountNumber(NUMERO_COMPLET)
        .firstName("DANIEL LANDRY")
        .lastName("DZANGUE")
        .phoneNumber(telephone)
        .eligible(eligible)
        .build();
  }

  private AccountVerificationRequest demande(String telephone) {
    AccountVerificationRequest r = new AccountVerificationRequest();
    r.setAccountSuffix(SUFFIXE);
    r.setPhoneNumber(telephone);
    return r;
  }

  /** Fait répondre le référentiel bancaire comme s'il ignorait le compte. */
  private void referentielMuet() {
    when(whatsAppBankingClient.readAccount(null, NUMERO_COMPLET))
        .thenReturn(Map.of("exists", false));
  }

  @Nested
  @DisplayName("appartenance du compte")
  class Appartenance {

    @Test
    @DisplayName("un numéro qui ne correspond pas au titulaire est refusé")
    void telephoneDifferent_estRefuse() {
      referentielMuet();
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
          .thenReturn(Optional.of(compte(true, TELEPHONE)));

      // Sans ce contrôle, quiconque connaît un RIB ouvrirait l'accès au service
      // sur le compte d'un tiers depuis son propre WhatsApp.
      assertThatThrownBy(() -> service.verifyAccount(demande("+237699000042")))
          .isInstanceOf(BusinessException.class)
          .hasFieldOrPropertyWithValue("type", TypeErreurMetier.INTERDIT);

      verify(onboardingSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("le refus ne révèle pas le numéro enregistré sur le compte")
    void telephoneDifferent_neDivulguePasLeNumeroDuTitulaire() {
      referentielMuet();
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
          .thenReturn(Optional.of(compte(true, TELEPHONE)));

      // Afficher le numéro attendu renseignerait un tiers sur le titulaire du
      // compte, ce qui retournerait la protection contre elle-même.
      assertThatThrownBy(() -> service.verifyAccount(demande("+237699000042")))
          .hasMessageNotContaining("685445511")
          .hasMessageNotContaining(TELEPHONE);
    }

    @Test
    @DisplayName("un compte sans numéro enregistré renvoie en agence")
    void compteSansTelephone_renvoieEnAgence() {
      referentielMuet();
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
          .thenReturn(Optional.of(compte(true, null)));

      // Laisser passer quand la donnée manque désactiverait le contrôle dès
      // qu'un compte est mal renseigné, ce qui en ferait une protection
      // illusoire. Le client est renvoyé là où un conseiller peut vérifier.
      assertThatThrownBy(() -> service.verifyAccount(demande(TELEPHONE)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("agence");
    }

    @Test
    @DisplayName("les écarts de présentation du numéro ne font pas échouer le contrôle")
    void formatsDifferents_sontRapproches() {
      referentielMuet();
      // Le référentiel stocke volontiers « 237 6 85 44 55 11 », le bot transmet
      // « +237685445511 ». Comparer les chaînes brutes ferait échouer le
      // contrôle sur de simples espaces, et renverrait en agence un client
      // légitime.
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
          .thenReturn(Optional.of(compte(true, "237 6 85 44 55 11")));
      when(sessionTokenGenerator.generate()).thenReturn("jeton");

      AccountVerificationResponse reponse = service.verifyAccount(demande("+237685445511"));

      assertThat(reponse.isEligible()).isTrue();
    }

    @Test
    @DisplayName("une demande sans numéro passe le contrôle, faute de pouvoir le mener")
    void demandeSansTelephone_neDeclenchePasLeControle() {
      // Cas du parcours ouvert autrement que par le bot. Le contrôle ne peut
      // pas s'exercer ; c'est un écart connu, figé ici pour qu'un durcissement
      // futur soit un choix visible et non un effet de bord.
      referentielMuet();
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
          .thenReturn(Optional.of(compte(true, TELEPHONE)));
      when(sessionTokenGenerator.generate()).thenReturn("jeton");

      assertThat(service.verifyAccount(demande(null)).isEligible()).isTrue();
    }
  }

  @Nested
  @DisplayName("éligibilité et existence")
  class Eligibilite {

    @Test
    @DisplayName("un compte non éligible est refusé")
    void compteNonEligible_estRefuse() {
      referentielMuet();
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
          .thenReturn(Optional.of(compte(false, TELEPHONE)));

      assertThatThrownBy(() -> service.verifyAccount(demande(TELEPHONE)))
          .isInstanceOf(BusinessException.class)
          .hasMessageContaining("éligible");

      verify(onboardingSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("un compte inconnu des deux sources est signalé comme introuvable")
    void compteInconnu_estSignale() {
      referentielMuet();
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.verifyAccount(demande(TELEPHONE)))
          .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    @DisplayName("le code banque est ajouté au suffixe saisi")
    void numeroInterroge_porteLeCodeBanque() {
      referentielMuet();
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.verifyAccount(demande(TELEPHONE)));

      // Le client ne saisit pas « 10005 », affiché à côté du champ. L'oublier
      // ici ferait chercher un compte qui n'existe pas.
      verify(whatsAppBankingClient).readAccount(null, NUMERO_COMPLET);
    }
  }

  @Nested
  @DisplayName("repli sur le miroir local")
  class Repli {

    @Test
    @DisplayName("un référentiel injoignable n'interrompt pas le parcours")
    void referentielInjoignable_basculeSurLeMiroir() {
      when(whatsAppBankingClient.readAccount(null, NUMERO_COMPLET))
          .thenThrow(new IllegalStateException("connexion refusée"));
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
          .thenReturn(Optional.of(compte(true, TELEPHONE)));
      when(sessionTokenGenerator.generate()).thenReturn("jeton");

      // Une coupure du bot ne doit pas arrêter un parcours déjà commencé : le
      // miroir local suffit à poursuivre.
      assertThat(service.verifyAccount(demande(TELEPHONE)).isEligible()).isTrue();
    }
  }

  @Nested
  @DisplayName("session ouverte")
  class Session {

    @Test
    @DisplayName("la session porte le numéro normalisé et le compte vérifié")
    void sessionOuverte_porteLesBonnesValeurs() {
      referentielMuet();
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
          .thenReturn(Optional.of(compte(true, TELEPHONE)));
      when(sessionTokenGenerator.generate()).thenReturn("jeton-de-session");

      AccountVerificationResponse reponse = service.verifyAccount(demande("+237 685 445 511"));

      ArgumentCaptor<OnboardingSession> ouverte = ArgumentCaptor.forClass(OnboardingSession.class);
      verify(onboardingSessionRepository).save(ouverte.capture());

      // Le numéro est stocké normalisé : c'est lui qui servira d'acteur au
      // journal d'audit et de clé de rapprochement plus loin dans le parcours.
      assertThat(ouverte.getValue().getPhoneNumber()).isEqualTo("237685445511");
      assertThat(ouverte.getValue().getSessionToken()).isEqualTo("jeton-de-session");
      assertThat(ouverte.getValue().getExpiresAt()).isNotNull();
      assertThat(reponse.getSessionToken()).isEqualTo("jeton-de-session");
      assertThat(reponse.getExpiresInSeconds()).isPositive();
    }

    @Test
    @DisplayName("l'identité rendue est celle du référentiel, pas celle du client")
    void identiteRendue_vientDuReferentiel() {
      referentielMuet();
      when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
          .thenReturn(Optional.of(compte(true, TELEPHONE)));
      when(sessionTokenGenerator.generate()).thenReturn("jeton");

      AccountVerificationResponse reponse = service.verifyAccount(demande(TELEPHONE));

      assertThat(reponse.getFirstName()).isEqualTo("DANIEL LANDRY");
      assertThat(reponse.getLastName()).isEqualTo("DZANGUE");
    }
  }

  @Test
  @DisplayName("un compte déjà rattaché à un client est refusé dès le premier écran")
  void verifyAccount_compteDejaUtilise_estRefuse() {
    // Sans ce contrôle, le parcours se rejouait entièrement : le client
    // refaisait KYC, vivacité et conditions générales pour se heurter à
    // l'unicité au tout dernier écran, après avoir tout fourni. Pire, la
    // seconde inscription écrasait la première, et avec elle son code PIN.
    when(whatsAppBankingClient.readAccount(null, NUMERO_COMPLET)).thenReturn(null);
    when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
        .thenReturn(Optional.of(compte(true, TELEPHONE)));
    when(customerRepository.findByBankAccount_AccountNumber(NUMERO_COMPLET))
        .thenReturn(Optional.of(Customer.builder().build()));

    assertThatThrownBy(() -> service.verifyAccount(demande(TELEPHONE)))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("déjà")
        // Le message oriente vers la bonne action plutôt que de constater un
        // refus : un client qui a oublié son PIN doit le réinitialiser, pas
        // recommencer une inscription qui échouera.
        .hasMessageContaining("PIN oublié");
  }

  @Test
  @DisplayName("aucune session n'est ouverte pour un compte déjà utilisé")
  void verifyAccount_compteDejaUtilise_nOuvreAucuneSession() {
    // Une session ouverte donnerait un jeton valide trente minutes sur un
    // compte auquel le demandeur n'a pas à accéder.
    when(whatsAppBankingClient.readAccount(null, NUMERO_COMPLET)).thenReturn(null);
    when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
        .thenReturn(Optional.of(compte(true, TELEPHONE)));
    when(customerRepository.findByBankAccount_AccountNumber(NUMERO_COMPLET))
        .thenReturn(Optional.of(Customer.builder().build()));

    assertThatThrownBy(() -> service.verifyAccount(demande(TELEPHONE)))
        .isInstanceOf(BusinessException.class);

    verify(onboardingSessionRepository, never()).save(any());
  }

  @Test
  @DisplayName("un compte encore libre poursuit normalement")
  void verifyAccount_compteLibre_poursuit() {
    when(whatsAppBankingClient.readAccount(null, NUMERO_COMPLET)).thenReturn(null);
    when(bankAccountRepository.findByAccountNumber(NUMERO_COMPLET))
        .thenReturn(Optional.of(compte(true, TELEPHONE)));
    when(customerRepository.findByBankAccount_AccountNumber(NUMERO_COMPLET))
        .thenReturn(Optional.empty());
    when(sessionTokenGenerator.generate()).thenReturn("jeton");

    assertThat(service.verifyAccount(demande(TELEPHONE)).isEligible()).isTrue();
  }
}
