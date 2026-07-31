import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { DocumentType, DocumentUploadResponse } from '../models/document.model';
import { OcrConfirmationRequest, OcrExtractionResponse } from '../models/ocr.model';
import { FaceVerificationResponse } from '../models/face-verification.model';
import { ChallengeStartResponse, ChallengeStatusResponse, ChallengeVerifyResponse, LivenessAction } from '../models/liveness.model';
import { OnboardingState } from './onboarding-state';

@Injectable({ providedIn: 'root' })
export class DocumentService {
  private readonly http = inject(HttpClient);
  private readonly onboardingState = inject(OnboardingState);
  private readonly baseUrl = `${environment.apiUrl}/documents`;

  private authHeaders(): HttpHeaders {
    const token = this.onboardingState.sessionToken();
    return new HttpHeaders({ 'X-Session-Token': token ?? '' });
  }

  uploadDocument(documentType: DocumentType, file: File): Observable<ApiResponse<DocumentUploadResponse>> {
    const formData = new FormData();
    formData.append('file', file);

    return this.http.post<ApiResponse<DocumentUploadResponse>>(
      `${this.baseUrl}/upload?documentType=${documentType}`,
      formData,
      { headers: this.authHeaders() }
    );
  }

  extractOcrData(): Observable<ApiResponse<OcrExtractionResponse>> {
    return this.http.post<ApiResponse<OcrExtractionResponse>>(
      `${this.baseUrl}/ocr/extract`,
      {},
      { headers: this.authHeaders() }
    );
  }

  getOcrData(): Observable<ApiResponse<OcrExtractionResponse>> {
    return this.http.get<ApiResponse<OcrExtractionResponse>>(`${this.baseUrl}/ocr`, {
      headers: this.authHeaders()
    });
  }

  confirmOcrData(payload: OcrConfirmationRequest): Observable<ApiResponse<OcrExtractionResponse>> {
    return this.http.put<ApiResponse<OcrExtractionResponse>>(`${this.baseUrl}/ocr`, payload, {
      headers: this.authHeaders()
    });
  }

  verifyFace(): Observable<ApiResponse<FaceVerificationResponse>> {
    return this.http.post<ApiResponse<FaceVerificationResponse>>(
      `${this.baseUrl}/face-verification/verify`,
      {},
      { headers: this.authHeaders() }
    );
  }

  getFaceVerification(): Observable<ApiResponse<FaceVerificationResponse>> {
    return this.http.get<ApiResponse<FaceVerificationResponse>>(`${this.baseUrl}/face-verification`, {
      headers: this.authHeaders()
    });
  }

  startLivenessChallenge(): Observable<ApiResponse<ChallengeStartResponse>> {
    return this.http.post<ApiResponse<ChallengeStartResponse>>(
      `${this.baseUrl}/liveness/challenge/start`,
      {},
      { headers: this.authHeaders() }
    );
  }

  verifyLivenessAction(action: LivenessAction, frames: Blob[]): Observable<ApiResponse<ChallengeVerifyResponse>> {
    const formData = new FormData();
    formData.append('action', action);
    frames.forEach((frame, index) => formData.append('frames', frame, `frame${index}.jpg`));

    return this.http.post<ApiResponse<ChallengeVerifyResponse>>(
      `${this.baseUrl}/liveness/challenge/verify`,
      formData,
      { headers: this.authHeaders() }
    );
  }

  getLivenessStatus(): Observable<ApiResponse<ChallengeStatusResponse>> {
    return this.http.get<ApiResponse<ChallengeStatusResponse>>(`${this.baseUrl}/liveness/challenge/status`, {
      headers: this.authHeaders()
    });
  }
}
