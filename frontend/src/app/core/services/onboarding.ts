import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer } from 'rxjs';
import { SessionAbsenteError } from '../errors/session-absente.error';
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

  /**
   * En-tête de session des appels du parcours.
   *
   * <p>L'en-tête partait auparavant vide quand aucune session n'était ouverte,
   * et c'est le serveur qui refusait : le client attendait un aller-retour
   * réseau pour un 401 générique, et rien ne disait que la session manquait
   * côté navigateur.
   *
   * <p>Chaque appel qui l'emploie est enveloppé dans `defer`, qui diffère
   * l'exécution jusqu'à la souscription : l'erreur levée ici est alors capturée
   * par RxJS et remise à l'abonné comme n'importe quel échec réseau. Une levée
   * synchrone traverserait l'appelant sans qu'aucun de ses gestionnaires ne
   * l'attrape.
   *
   * <p>{@code verifyLink} ne l'utilise pas, et c'est voulu : cet appel précède
   * l'ouverture de la session, c'est même lui qui la prépare.
   */
  private authHeaders(): HttpHeaders {
    const token = this.onboardingState.sessionToken();
    if (!token) {
      throw new SessionAbsenteError();
    }
    return new HttpHeaders({ 'X-Session-Token': token });
  }

  requestEmailOtp(request: KycRequest): Observable<ApiResponse<void>> {
    return defer(() => this.http.post<ApiResponse<void>>(`${this.baseUrl}/kyc/otp/request`, request, {
      headers: this.authHeaders()
    }));
  }

  verifyEmailOtp(request: OtpVerificationRequest): Observable<ApiResponse<void>> {
    return defer(() => this.http.post<ApiResponse<void>>(`${this.baseUrl}/kyc/otp/verify`, request, {
      headers: this.authHeaders()
    }));
  }

  // skipEmailVerification retiré avec l'endpoint /kyc/skip côté backend :
  // l'envoi d'e-mail fonctionne, seule MAIL_PASSWORD n'était pas renseignée.

  createProfile(request: ProfileCreationRequest): Observable<ApiResponse<void>> {
    return defer(() => this.http.post<ApiResponse<void>>(`${this.baseUrl}/profile`, request, {
      headers: this.authHeaders()
    }));
  }

  acceptTerms(request: TermsAcceptanceRequest): Observable<ApiResponse<void>> {
    return defer(() => this.http.post<ApiResponse<void>>(`${this.baseUrl}/terms`, request, {
      headers: this.authHeaders()
    }));
  }

  // Entrée par lien : valide le JWT reçu dans l'URL (?t=) auprès du backend (qui interroge le
  // WhatsApp banking) et renvoie le contexte pour démarrer/pré-remplir le parcours.
  verifyLink(token: string): Observable<ApiResponse<LinkVerificationResponse>> {
    return defer(() => this.http.post<ApiResponse<LinkVerificationResponse>>(
      `${this.baseUrl}/link/verify`,
      { token }
    ));
  }

  completeOnboarding(): Observable<ApiResponse<OnboardingCompletionResponse>> {
    // On transmet le token du lien (consommé côté source de vérité) et le PIN en clair, portés
    // depuis les étapes précédentes. Absents (parcours sans lien) => le backend reste local.
    const body = {
      linkToken: this.onboardingState.linkToken(),
      pin: this.onboardingState.pin()
    };
    return defer(() => this.http.post<ApiResponse<OnboardingCompletionResponse>>(
      `${this.baseUrl}/complete`,
      body,
      { headers: this.authHeaders() }
    ));
  }
}

export interface LinkVerificationResponse {
  valid: boolean;
  phone: string;
  name: string;
  accountNumber: string;
  lang: string;
  alreadyOnboarded: boolean;
}