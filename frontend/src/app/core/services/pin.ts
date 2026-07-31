import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { PinResetRequest, PinResetResponse, PinResetConfirmRequest } from '../models/pin.model';

@Injectable({ providedIn: 'root' })
export class PinService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/pin`;

  requestReset(accountNumber: string): Observable<ApiResponse<PinResetResponse>> {
    const request: PinResetRequest = { accountNumber };
    return this.http.post<ApiResponse<PinResetResponse>>(`${this.baseUrl}/reset/request`, request);
  }

  confirmReset(request: PinResetConfirmRequest): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/reset/confirm`, request);
  }
}