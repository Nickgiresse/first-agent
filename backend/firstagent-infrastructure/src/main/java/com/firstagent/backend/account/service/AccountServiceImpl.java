package com.firstagent.backend.account.service;

import com.firstagent.backend.account.dto.AccountVerificationRequest;
import com.firstagent.backend.account.dto.AccountVerificationResponse;
import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.account.repository.BankAccountRepository;
import com.firstagent.backend.common.enums.OnboardingStatus;
import com.firstagent.backend.common.exception.AccountNotFoundException;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.TypeErreurMetier;
import com.firstagent.backend.common.security.SecureSessionTokenGenerator;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.repository.OnboardingSessionRepository;
import com.firstagent.backend.whatsappbanking.client.WhatsAppBankingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final long SESSION_DURATION_MILLIS = 30 * 60 * 1000L; // 30 minutes

    private final BankAccountRepository bankAccountRepository;
    private final OnboardingSessionRepository onboardingSessionRepository;
    private final SecureSessionTokenGenerator sessionTokenGenerator;
    private final WhatsAppBankingClient whatsAppBankingClient;

    /**
     * Vérifie le compte saisi par le client.
     *
     * Le référentiel des comptes est celui du WhatsApp banking (source de vérité) : on l'interroge
     * via l'API machine-à-machine plutôt que de dupliquer l'information ici. La table locale
     * bank_accounts n'est qu'un miroir technique, nécessaire parce que la session d'onboarding
     * s'y rattache par clé étrangère ; elle est alimentée/rafraîchie à partir de la réponse de la
     * banque. Si la banque est injoignable, on se rabat sur ce miroir afin qu'une coupure du bot
     * n'interrompe pas un parcours déjà commencé.
     */
    @Override
    @Transactional
    public AccountVerificationResponse verifyAccount(AccountVerificationRequest request) {
        String accountNumber = "10005" + request.getAccountSuffix();
        BankAccount bankAccount = resolveFromWhatsAppBanking(accountNumber)
            .orElseGet(() -> bankAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException("Aucun compte bancaire trouvé avec ce numéro")));

        if (!bankAccount.isEligible()) {
            throw new BusinessException("Ce compte n'est pas éligible à l'onboarding digital", TypeErreurMetier.INTERDIT);
        }

        // Contrôle d'appartenance : le numéro qui réalise le parcours doit être
        // celui déclaré sur le compte au référentiel bancaire. Le bot connaît
        // son interlocuteur et dépose ce numéro dans la demande ; le client ne
        // le saisit ni ne le voit. Sans ce contrôle, quiconque connaît un RIB
        // ouvrirait l'accès au service sur le compte d'un tiers depuis son
        // propre WhatsApp.
        String telephoneDemandeur = normaliserTelephone(request.getPhoneNumber());
        if (telephoneDemandeur != null) {
            verifierAppartenanceDuCompte(bankAccount, telephoneDemandeur);
        }

        String sessionToken = sessionTokenGenerator.generate();

        OnboardingSession session = OnboardingSession.builder()
            .sessionToken(sessionToken)
            .bankAccount(bankAccount)
            .phoneNumber(telephoneDemandeur)
            .status(OnboardingStatus.ACCOUNT_VERIFIED)
            .expiresAt(LocalDateTime.now().plusMinutes(30))
            .build();

        onboardingSessionRepository.save(session);

        return AccountVerificationResponse.builder()
            .eligible(true)
            .firstName(bankAccount.getFirstName())
            .lastName(bankAccount.getLastName())
            .sessionToken(sessionToken)
            .expiresInSeconds(SESSION_DURATION_MILLIS / 1000)
            .build();
    }

    /**
     * Refuse le parcours si le compte n'appartient pas au numéro qui le mène.
     *
     * <p>Un référentiel qui ne connaît aucun téléphone pour ce compte ne permet
     * pas de conclure. Laisser passer reviendrait à désactiver le contrôle dès
     * qu'une donnée manque, ce qui en ferait une protection illusoire : le
     * client est renvoyé en agence, où un conseiller peut vérifier son
     * identité.
     */
    private void verifierAppartenanceDuCompte(BankAccount compte, String telephoneDemandeur) {
        String telephoneDuCompte = normaliserTelephone(compte.getPhoneNumber());

        if (telephoneDuCompte == null) {
            throw new BusinessException(
                "Nous n'avons pas pu confirmer que ce compte est bien le vôtre : aucun numéro "
                    + "de téléphone n'y est enregistré. Présentez-vous dans l'agence Afriland "
                    + "First Bank la plus proche avec votre pièce d'identité.",
                TypeErreurMetier.INTERDIT);
        }

        if (!telephoneDuCompte.equals(telephoneDemandeur)) {
            // Le message ne révèle pas le numéro enregistré : l'indiquer
            // renseignerait un tiers sur le titulaire du compte.
            throw new BusinessException(
                "Ce numéro de compte n'est pas associé au numéro WhatsApp depuis lequel vous "
                    + "faites la demande. Utilisez le numéro enregistré sur votre compte, ou "
                    + "présentez-vous en agence avec votre pièce d'identité.",
                TypeErreurMetier.INTERDIT);
        }
    }

    /**
     * Ramène un numéro à ses seuls chiffres, indicatif pays compris.
     *
     * <p>Les deux sources ne se ressemblent pas : le référentiel stocke souvent
     * « 237 6 85 44 55 11 » ou « +237685445511 », le bot transmet ce que lui
     * donne WhatsApp. Comparer les chaînes brutes ferait échouer le contrôle
     * sur de simples espaces.
     */
    private String normaliserTelephone(String valeur) {
        if (valeur == null) {
            return null;
        }
        String chiffres = valeur.replaceAll("\\D", "");
        return chiffres.isEmpty() ? null : chiffres;
    }

    /** Interroge la banque et met le miroir local à jour. Vide si le compte y est inconnu. */
    private java.util.Optional<BankAccount> resolveFromWhatsAppBanking(String accountNumber) {
        Map<String, Object> remote;
        try {
            remote = whatsAppBankingClient.readAccount(null, accountNumber);
        } catch (RuntimeException e) {
            log.warn("Référentiel bancaire injoignable pour {} : {}. Repli sur le miroir local.",
                accountNumber, e.getMessage());
            return java.util.Optional.empty();
        }
        if (remote == null || !Boolean.TRUE.equals(remote.get("exists"))) {
            return java.util.Optional.empty();
        }

        String fullName = String.valueOf(remote.getOrDefault("name", "")).trim();
        // Convention locale : le nom de famille précède les prénoms (« DZANGUE DANIEL LANDRY »).
        String lastName = fullName.isEmpty() ? "" : fullName.split("\\s+")[0];
        String firstName = fullName.contains(" ") ? fullName.substring(lastName.length()).trim() : fullName;

        BankAccount mirror = bankAccountRepository.findByAccountNumber(accountNumber)
            .orElseGet(() -> BankAccount.builder().accountNumber(accountNumber).build());
        mirror.setOwnerFullName(fullName.isEmpty() ? accountNumber : fullName);
        mirror.setFirstName(firstName.isEmpty() ? "?" : firstName);
        mirror.setLastName(lastName.isEmpty() ? "?" : lastName);
        // Un compte déjà suspendu côté banque (revue KYC, décès, inactivité) n'est pas éligible.
        Object active = remote.get("is_active");
        mirror.setEligible(active == null || !"0".equals(String.valueOf(active)));
        return java.util.Optional.of(bankAccountRepository.save(mirror));
    }
}
