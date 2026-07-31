export interface AccountVerificationRequest {
  accountSuffix: string;
}

export interface AccountVerificationResponse {
  eligible: boolean;
  firstName: string;
  lastName: string;
  sessionToken: string;
  expiresInSeconds: number;
}