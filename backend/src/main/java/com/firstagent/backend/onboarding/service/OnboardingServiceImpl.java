package com.firstagent.backend.onboarding.service;

import com.firstagent.backend.common.enums.CustomerStatus;
import com.firstagent.backend.common.enums.OnboardingStatus;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.InvalidPinException;
import com.firstagent.backend.common.exception.ResourceNotFoundException;
import com.firstagent.backend.document.service.DocumentService;
import com.firstagent.backend.onboarding.dto.*;
import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import com.firstagent.backend.pin.service.PinService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private final OnboardingSessionService onboardingSessionService;
    private final CustomerRepository customerRepository;
    private final PinService pinService;
    private final DocumentService documentService;

    @Override
    public void validateKyc(String sessionToken, KycRequest request) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        if (session.getStatus() != OnboardingStatus.ACCOUNT_VERIFIED) {
            throw new BusinessException("Étape KYC déjà complétée ou non accessible à ce stade");
        }

        onboardingSessionService.updateStatus(session, OnboardingStatus.KYC_COMPLETED);
    }

    @Override
    @Transactional
    public Customer createProfile(String sessionToken, KycRequest kycRequest, PinCreationRequest pinRequest) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        if (session.getStatus() != OnboardingStatus.KYC_COMPLETED) {
            throw new BusinessException("Le KYC doit être complété avant la création du PIN");
        }

        if (!pinRequest.getPin().equals(pinRequest.getPinConfirmation())) {
            throw new InvalidPinException("Le PIN et sa confirmation ne correspondent pas");
        }

        if (customerRepository.findByBankAccount_AccountNumber(session.getBankAccount().getAccountNumber()).isPresent()) {
            throw new BusinessException("Un utilisateur existe déjà pour ce numéro de compte. Utilisez la réinitialisation de PIN si nécessaire.", HttpStatus.CONFLICT);
        }

        if (kycRequest.getEmail() != null && customerRepository.existsByEmail(kycRequest.getEmail())) {
            throw new BusinessException("Cette adresse e-mail est déjà utilisée par un autre utilisateur.", HttpStatus.CONFLICT);
        }

        Customer customer = Customer.builder()
            .bankAccount(session.getBankAccount())
            .onboardingSession(session)
            .firstName(session.getBankAccount().getFirstName())
            .lastName(session.getBankAccount().getLastName())
            .email(kycRequest.getEmail())
            .phoneNumber(null)
            .pinHash(pinService.hashPin(pinRequest.getPin()))
            .status(CustomerStatus.USER)
            .termsAccepted(false)
            .build();

        customerRepository.save(customer);
        onboardingSessionService.updateStatus(session, OnboardingStatus.PIN_CREATED);

        return customer;
    }

    @Override
    @Transactional
    public void acceptTerms(String sessionToken, TermsAcceptanceRequest request) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        Customer customer = customerRepository.findByOnboardingSession_Id(session.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Profil client introuvable pour cette session"));

        if (!documentService.hasAllRequiredDocuments(customer.getId())) {
            throw new BusinessException("Tous les documents requis (CNI recto, verso, selfie) doivent être téléversés avant d'accepter les conditions");
        }

        customer.setTermsAccepted(true);
        customer.setTermsAcceptedAt(LocalDateTime.now());
        customerRepository.save(customer);

        onboardingSessionService.updateStatus(session, OnboardingStatus.TERMS_ACCEPTED);
    }

    @Override
    @Transactional
    public OnboardingCompletionResponse completeOnboarding(String sessionToken) {
        OnboardingSession session = onboardingSessionService.getValidSession(sessionToken);

        if (session.getStatus() != OnboardingStatus.TERMS_ACCEPTED) {
            throw new BusinessException("Les conditions d'utilisation doivent être acceptées avant la finalisation");
        }

        Customer customer = customerRepository.findByOnboardingSession_Id(session.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Profil client introuvable pour cette session"));

        onboardingSessionService.updateStatus(session, OnboardingStatus.COMPLETED);

        return OnboardingCompletionResponse.builder()
            .customerId(customer.getId())
            .firstName(customer.getFirstName())
            .lastName(customer.getLastName())
            .message("Votre compte digital a été créé avec succès. Vous pouvez maintenant vous connecter.")
            .build();
    }
}
