export type DocumentType = 'CNI_RECTO' | 'CNI_VERSO' | 'SELFIE';

export interface DocumentUploadResponse {
  documentId: string;
  documentType: DocumentType;
  fileName: string;
  uploadedAt: string;
}