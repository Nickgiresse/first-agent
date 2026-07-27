package com.firstagent.backend.document.service;

import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.common.exception.BusinessException;
import com.firstagent.backend.common.exception.ResourceNotFoundException;
import com.firstagent.backend.document.dto.DocumentUploadResponse;
import com.firstagent.backend.document.entity.CustomerDocument;
import com.firstagent.backend.document.repository.DocumentRepository;
import com.firstagent.backend.onboarding.entity.Customer;
import com.firstagent.backend.onboarding.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private static final Set<DocumentType> REQUIRED_TYPES = Set.of(
        DocumentType.CNI_RECTO, DocumentType.CNI_VERSO, DocumentType.SELFIE
    );

    private final DocumentRepository documentRepository;
    private final CustomerRepository customerRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public DocumentUploadResponse uploadDocument(UUID customerId, DocumentType documentType, MultipartFile file) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new ResourceNotFoundException("Client introuvable"));

        validateFile(file);

        if (documentRepository.existsByCustomer_IdAndDocumentType(customerId, documentType)) {
            throw new BusinessException("Ce type de document a déjà été téléversé");
        }

        String filePath = storageService.store(file, customerId.toString());

        CustomerDocument document = CustomerDocument.builder()
            .customer(customer)
            .documentType(documentType)
            .filePath(filePath)
            .fileName(file.getOriginalFilename())
            .mimeType(file.getContentType())
            .fileSize(file.getSize())
            .build();

        documentRepository.save(document);

        return DocumentUploadResponse.builder()
            .documentId(document.getId())
            .documentType(document.getDocumentType().name())
            .fileName(document.getFileName())
            .uploadedAt(document.getUploadedAt())
            .build();
    }

    @Override
    public boolean hasAllRequiredDocuments(UUID customerId) {
        List<CustomerDocument> documents = documentRepository.findByCustomer_Id(customerId);
        Set<DocumentType> uploadedTypes = documents.stream()
            .map(CustomerDocument::getDocumentType)
            .collect(java.util.stream.Collectors.toSet());

        return uploadedTypes.containsAll(REQUIRED_TYPES);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("Le fichier est vide");
        }
        if (!ALLOWED_MIME_TYPES.contains(file.getContentType())) {
            throw new BusinessException("Seuls les fichiers JPEG et PNG sont acceptés");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("Le fichier ne doit pas dépasser 5 Mo");
        }
    }
}