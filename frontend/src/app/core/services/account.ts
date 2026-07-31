import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { AccountVerificationRequest, AccountVerificationResponse } from '../models/account.model';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/accounts`;

  verifyAccount(accountSuffix: string): Observable<ApiResponse<AccountVerificationResponse>> {
    const request: AccountVerificationRequest = { accountSuffix };
    return this.http.post<ApiResponse<AccountVerificationResponse>>(`${this.baseUrl}/verify`, request);
  }
}