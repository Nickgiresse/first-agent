package com.firstagent.backend.document.service;

import com.firstagent.backend.account.entity.BankAccount;
import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.common.enums.OcrStatus;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.ResourceNotFoundException;
import com.firstagent.backend.document.dto.OcrConfirmationRequest;
import com.firstagent.backend.document.dto.OcrExtractionResponse;
import com.firstagent.backend.document.entity.StagingDocument;
import com.firstagent.backend.document.entity.StagingOcrResult;
import com.firstagent.backend.document.repository.StagingDocumentRepository;
import com.firstagent.backend.document.repository.StagingOcrResultRepository;
import com.firstagent.backend.onboarding.entity.OnboardingSession;
import com.firstagent.backend.onboarding.service.OnboardingSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrServiceImplTest {

    @Mock
    private StagingDocumentRepository stagingDocumentRepository;
    @Mock
    private StagingOcrResultRepository stagingOcrResultRepository;
    @Mock
    private OnboardingSessionService onboardingSessionService;
    @Mock
    private StorageService storageService;
    @Mock
    private OcrProvider ocrProvider;

    private OcrServiceImpl ocrService;

    private final String sessionToken = "session-token";
    private final UUID sessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ocrService = new OcrServiceImpl(stagingDocumentRepository, stagingOcrResultRepository, onboardingSessionService, storageService, ocrProvider);
        ReflectionTestUtils.setField(ocrService, "minConfidenceScore", 60.0);
        ReflectionTestUtils.setField(ocrService, "ocrNameSimilarityThreshold", 0.75);
        ReflectionTestUtils.setField(ocrService, "identityNameSimilarityThreshold", 0.70);
    }

    private OnboardingSession session(String firstName, String lastName) {
        BankAccount bankAccount = BankAccount.builder().firstName(firstName).lastName(lastName).build();
        return OnboardingSession.builder().id(sessionId).bankAccount(bankAccount).build();
    }

    private static OcrExtractionResult cniExtraction(String firstName, String lastName, String documentNumber, double confidence) {
        return new OcrExtractionResult(
            "CNI", firstName, lastName, documentNumber, "M",
            LocalDate.of(1990, 1, 1), LocalDate.of(2030, 1, 1), "YAOUNDE",
            null, null, null, null,
            null, null,
            confidence, 90.0, List.of()
        );
    }

    @Test
    void extractDocumentDataThrowsWhenSessionInvalid() {
        when(onboardingSessionService.getValidSession(sessionToken))
            .thenThrow(new ResourceNotFoundException("Session d'onboarding introuvable"));

        assertThatThrownBy(() -> ocrService.extractDocumentData(sessionToken))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void extractDocumentDataThrowsWhenFrontDocumentMissing() {
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session("Jean", "Nkeng"));
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.CNI_RECTO))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> ocrService.extractDocumentData(sessionToken))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("recto");
    }

    @Test
    void extractDocumentDataCreatesResultOnFirstExtraction() {
        OnboardingSession session = session("Jean", "Nkeng");
        StagingDocument front = StagingDocument.builder().filePath("/front.jpg").build();
        StagingDocument back = StagingDocument.builder().filePath("/back.jpg").build();
        OcrExtractionResult extraction = cniExtraction("Jean", "Nkeng", "123456789", 92.0);

        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.CNI_RECTO))
            .thenReturn(Optional.of(front));
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.CNI_VERSO))
            .thenReturn(Optional.of(back));
        when(storageService.read("/front.jpg")).thenReturn(new byte[]{1});
        when(storageService.read("/back.jpg")).thenReturn(new byte[]{2});
        when(ocrProvider.extractIdentityDocument(any(), any())).thenReturn(extraction);
        when(ocrProvider.getProviderName()).thenReturn("PYTHON_VISION");
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.empty());
        when(stagingOcrResultRepository.save(any(StagingOcrResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OcrExtractionResponse response = ocrService.extractDocumentData(sessionToken);

        assertThat(response.getFirstName()).isEqualTo("Jean");
        assertThat(response.getDocumentNumber()).isEqualTo("123456789");
        assertThat(response.getDocumentKind()).isEqualTo("CNI");
        assertThat(response.getStatus()).isEqualTo(OcrStatus.EXTRACTED.name());
        assertThat(response.getProvider()).isEqualTo("PYTHON_VISION");
    }

    @Test
    void extractDocumentDataMarksFailedAndThrowsWhenConfidenceTooLow() {
        OnboardingSession session = session("Jean", "Nkeng");
        StagingDocument front = StagingDocument.builder().filePath("/front.jpg").build();
        StagingDocument back = StagingDocument.builder().filePath("/back.jpg").build();
        OcrExtractionResult extraction = cniExtraction("Jean", "Nkeng", "123456789", 40.0);

        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.CNI_RECTO))
            .thenReturn(Optional.of(front));
        when(stagingDocumentRepository.findByOnboardingSession_IdAndDocumentType(sessionId, DocumentType.CNI_VERSO))
            .thenReturn(Optional.of(back));
        when(storageService.read("/front.jpg")).thenReturn(new byte[]{1});
        when(storageService.read("/back.jpg")).thenReturn(new byte[]{2});
        when(ocrProvider.extractIdentityDocument(any(), any())).thenReturn(extraction);
        when(ocrProvider.getProviderName()).thenReturn("PYTHON_VISION");
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.empty());
        when(stagingOcrResultRepository.save(any(StagingOcrResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> ocrService.extractDocumentData(sessionToken))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("qualité");

        verify(stagingOcrResultRepository).save(argThat(saved -> saved.getStatus() == OcrStatus.FAILED));
    }

    @Test
    void confirmExtractedDataUpdatesStatusToConfirmed() {
        OnboardingSession session = session("Marie", "Fotso");
        StagingOcrResult existing = StagingOcrResult.builder()
            .id(UUID.randomUUID())
            .onboardingSession(session)
            .documentKind("CNI")
            .status(OcrStatus.EXTRACTED)
            .ocrProvider("PYTHON_VISION")
            .build();

        OcrConfirmationRequest request = new OcrConfirmationRequest();
        request.setFirstName("Marie");
        request.setLastName("Fotso");
        request.setDocumentNumber("987654321");
        request.setBirthDate(LocalDate.of(1985, 5, 5));
        request.setExpiryDate(LocalDate.now().plusYears(3));

        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(existing));
        when(stagingOcrResultRepository.save(any(StagingOcrResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OcrExtractionResponse response = ocrService.confirmExtractedData(sessionToken, request);

        assertThat(response.getFirstName()).isEqualTo("Marie");
        assertThat(response.getDocumentNumber()).isEqualTo("987654321");
        assertThat(response.getStatus()).isEqualTo(OcrStatus.CONFIRMED.name());
    }

    @Test
    void confirmExtractedDataUpdatesStatusToConfirmedForProvisionalReceipt() {
        OnboardingSession session = session("Nick", "Foadjo");
        StagingOcrResult existing = StagingOcrResult.builder()
            .id(UUID.randomUUID())
            .onboardingSession(session)
            .documentKind("TITRE_PROVISOIRE")
            .status(OcrStatus.EXTRACTED)
            .ocrProvider("PYTHON_VISION")
            .build();

        OcrConfirmationRequest request = new OcrConfirmationRequest();
        request.setFirstName("Nick");
        request.setLastName("Foadjo");
        request.setKitNumber("KIT328");
        request.setBirthDate(LocalDate.of(1985, 5, 5));
        request.setExpiryDate(LocalDate.now().plusMonths(2));

        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(existing));
        when(stagingOcrResultRepository.save(any(StagingOcrResult.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OcrExtractionResponse response = ocrService.confirmExtractedData(sessionToken, request);

        assertThat(response.getKitNumber()).isEqualTo("KIT328");
        assertThat(response.getStatus()).isEqualTo(OcrStatus.CONFIRMED.name());
    }

    @Test
    void confirmExtractedDataThrowsWhenExpiryDateIsInThePast() {
        OnboardingSession session = session("Marie", "Fotso");
        StagingOcrResult existing = StagingOcrResult.builder()
            .id(UUID.randomUUID())
            .onboardingSession(session)
            .documentKind("CNI")
            .status(OcrStatus.EXTRACTED)
            .ocrProvider("PYTHON_VISION")
            .build();

        OcrConfirmationRequest request = new OcrConfirmationRequest();
        request.setFirstName("Marie");
        request.setLastName("Fotso");
        request.setDocumentNumber("987654321");
        request.setBirthDate(LocalDate.of(1985, 5, 5));
        request.setExpiryDate(LocalDate.now().minusDays(1));

        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> ocrService.confirmExtractedData(sessionToken, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("expiré");
    }

    @Test
    void confirmExtractedDataThrowsWhenSubmittedNameDoesNotMatchExtractedName() {
        OnboardingSession session = session("Marie", "Fotso");
        StagingOcrResult existing = StagingOcrResult.builder()
            .id(UUID.randomUUID())
            .onboardingSession(session)
            .documentKind("CNI")
            .firstName("Marie")
            .lastName("Fotso")
            .status(OcrStatus.EXTRACTED)
            .ocrProvider("PYTHON_VISION")
            .build();

        OcrConfirmationRequest request = new OcrConfirmationRequest();
        request.setFirstName("Quelquun");
        request.setLastName("Dautre");
        request.setDocumentNumber("987654321");
        request.setBirthDate(LocalDate.of(1985, 5, 5));
        request.setExpiryDate(LocalDate.now().plusYears(3));

        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> ocrService.confirmExtractedData(sessionToken, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("prénom");
    }

    @Test
    void confirmExtractedDataThrowsWhenIdentityDoesNotMatchAccountHolder() {
        OnboardingSession session = session("Marie", "Fotso");
        StagingOcrResult existing = StagingOcrResult.builder()
            .id(UUID.randomUUID())
            .onboardingSession(session)
            .documentKind("CNI")
            .status(OcrStatus.EXTRACTED)
            .ocrProvider("PYTHON_VISION")
            .build();

        OcrConfirmationRequest request = new OcrConfirmationRequest();
        request.setFirstName("Quelquun");
        request.setLastName("Dautre");
        request.setDocumentNumber("987654321");
        request.setBirthDate(LocalDate.of(1985, 5, 5));
        request.setExpiryDate(LocalDate.now().plusYears(3));

        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session);
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> ocrService.confirmExtractedData(sessionToken, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("titulaire du compte");
    }

    @Test
    void getExtractedDataThrowsWhenNoResultExists() {
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session("Marie", "Fotso"));
        when(stagingOcrResultRepository.findByOnboardingSession_Id(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ocrService.getExtractedData(sessionToken))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void isConfirmedDelegatesToRepository() {
        when(onboardingSessionService.getValidSession(sessionToken)).thenReturn(session("Marie", "Fotso"));
        when(stagingOcrResultRepository.existsByOnboardingSession_IdAndStatus(sessionId, OcrStatus.CONFIRMED)).thenReturn(true);

        assertThat(ocrService.isConfirmed(sessionToken)).isTrue();
    }
}
