export interface KycRequest {
  email: string;
}

export interface OtpVerificationRequest {
  code: string;
}

export interface PinCreationRequest {
  pin: string;
  pinConfirmation: string;
}

export interface ProfileCreationRequest {
  pin: PinCreationRequest;
}

export interface TermsAcceptanceRequest {
  termsAccepted: boolean;
}

export interface OnboardingCompletionResponse {
  customerId: string;
  firstName: string;
  lastName: string;
  message: string;
}