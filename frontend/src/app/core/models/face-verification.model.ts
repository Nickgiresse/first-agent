export interface FaceVerificationResponse {
  faceVerificationResultId: string;
  matched: boolean;
  similarityScore: number;
  targetQualityScore: number | null;
  status: 'PENDING' | 'VERIFIED' | 'FAILED';
  provider: string;
  verifiedAt: string | null;
}
