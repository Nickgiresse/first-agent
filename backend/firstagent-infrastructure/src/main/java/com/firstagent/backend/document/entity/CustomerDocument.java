package com.firstagent.backend.document.entity;

import com.firstagent.backend.common.enums.DocumentType;
import com.firstagent.backend.onboarding.entity.Customer;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
    name = "customer_documents",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_customer_document_type",
            columnNames = {"customer_id", "document_type"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CustomerDocument {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "customer_id", nullable = false)
  private Customer customer;

  @Enumerated(EnumType.STRING)
  @Column(name = "document_type", length = 20, nullable = false)
  private DocumentType documentType;

  @Column(name = "file_path", length = 500, nullable = false)
  private String filePath;

  @Column(name = "file_name", length = 255, nullable = false)
  private String fileName;

  @Column(name = "mime_type", length = 100, nullable = false)
  private String mimeType;

  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "uploaded_at", nullable = false, updatable = false)
  private LocalDateTime uploadedAt;

  @PrePersist
  protected void onCreate() {
    this.uploadedAt = LocalDateTime.now();
  }
}
