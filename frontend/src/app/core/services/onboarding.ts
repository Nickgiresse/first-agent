import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import {
  KycRequest,
  OtpVerificationRequest,
  ProfileCreationRequest,
  TermsAcceptanceRequest,
  OnboardingCompletionResponse
} from '../models/onboarding.model';
import { OnboardingState } from './onboarding-state';

@Injectable({ providedIn: 'root' })
export class OnboardingService {
  private readonly http = inject(HttpClient);
  private readonly onboardingState = inject(OnboardingState);
  private readonly baseUrl = `${environment.apiUrl}/onboarding`;

  private authHeaders(): HttpHeaders {
    const token = this.onboardingState.sessionToken();
    return new HttpHeaders({ 'X-Session-Token': token ?? '' });
  }

  requestEmailOtp(request: KycRequest): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/kyc/otp/request`, request, {
      headers: this.authHeaders()
    });
  }

  verifyEmailOtp(request: OtpVerificationRequest): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/kyc/otp/verify`, request, {
      headers: this.authHeaders()
    });
  }

  // Contournement temporaire tant que l'envoi d'e-mail (SMTP) est en panne.
  skipEmailVerification(): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/kyc/skip`, {}, {
      headers: this.authHeaders()
    });
  }

  createProfile(request: ProfileCreationRequest): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/profile`, request, {
      headers: this.authHeaders()
    });
  }

  acceptTerms(request: TermsAcceptanceRequest): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/terms`, request, {
      headers: this.authHeaders()
    });
  }

  completeOnboarding(): Observable<ApiResponse<OnboardingCompletionResponse>> {
    return this.http.post<ApiResponse<OnboardingCompletionResponse>>(
      `${this.baseUrl}/complete`,
      {},
      { headers: this.authHeaders() }
    );
  }
}