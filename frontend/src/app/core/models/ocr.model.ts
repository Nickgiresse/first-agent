export type DocumentKind = 'CNI' | 'TITRE_PROVISOIRE' | 'RECEPISSE' | 'UNKNOWN';

export interface OcrExtractionResponse {
  documentOcrResultId: string;
  documentKind: DocumentKind;
  firstName: string;
  lastName: string;
  documentNumber: string | null;
  sex: string | null;
  birthDate: string | null;
  expiryDate: string | null;
  birthPlace: string | null;
  fatherName: string | null;
  motherName: string | null;
  kitNumber: string | null;
  requestIdentifier: string | null;
  paymentAmount: string | null;
  paymentDate: string | null;
  confidenceScore: number;
  documentQualityScore: number | null;
  status: 'PENDING' | 'EXTRACTED' | 'CONFIRMED' | 'FAILED';
  provider: string;
}

export interface OcrConfirmationRequest {
  firstName: string;
  lastName: string;
  documentNumber?: string;
  sex?: string;
  birthDate?: string;
  expiryDate?: string;
  birthPlace?: string;
  fatherName?: string;
  motherName?: string;
  kitNumber?: string;
  requestIdentifier?: string;
  paymentAmount?: string;
  paymentDate?: string;
}
