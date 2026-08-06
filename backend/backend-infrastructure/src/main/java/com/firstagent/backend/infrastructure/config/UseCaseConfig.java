package com.firstagent.backend.infrastructure.config;

import com.firstagent.backend.application.port.in.*;
import com.firstagent.backend.application.port.out.*;
import com.firstagent.backend.application.usecase.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public VerifyAccountUseCase verifyAccountUseCase(
            BankAccountRepositoryPort bankAccountRepositoryPort,
            OnboardingSessionRepositoryPort onboardingSessionRepositoryPort,
            SessionTokenGeneratorPort sessionTokenGeneratorPort,
            WhatsAppBankingPort whatsAppBankingPort) {
        return new VerifyAccountUseCaseImpl(
                bankAccountRepositoryPort,
                onboardingSessionRepositoryPort,
                sessionTokenGeneratorPort,
                whatsAppBankingPort
        );
    }

    @Bean
    public OnboardingUseCase onboardingUseCase(
            OnboardingSessionRepositoryPort onboardingSessionRepositoryPort) {
        return new OnboardingUseCaseImpl(onboardingSessionRepositoryPort);
    }

    @Bean
    public DocumentUseCase documentUseCase(DocumentStoragePort documentStoragePort) {
        return new DocumentUseCaseImpl(documentStoragePort);
    }

    @Bean
    public OcrUseCase ocrUseCase(
            OnboardingSessionRepositoryPort onboardingSessionRepositoryPort,
            BankAccountRepositoryPort bankAccountRepositoryPort,
            DocumentStoragePort documentStoragePort,
            PythonVisionPort pythonVisionPort) {
        return new OcrUseCaseImpl(
                onboardingSessionRepositoryPort,
                bankAccountRepositoryPort,
                documentStoragePort,
                pythonVisionPort
        );
    }

    @Bean
    public FaceVerificationUseCase faceVerificationUseCase(
            DocumentStoragePort documentStoragePort,
            FaceVerificationPort faceVerificationPort) {
        return new FaceVerificationUseCaseImpl(documentStoragePort, faceVerificationPort);
    }

    @Bean
    public LivenessUseCase livenessUseCase(OcrUseCase ocrUseCase) {
        return new LivenessUseCaseImpl(ocrUseCase);
    }

    @Bean
    public PinUseCase pinUseCase() {
        return new PinUseCaseImpl();
    }
}
