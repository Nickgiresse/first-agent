export interface PinResetRequest {
  accountNumber: string;
}

export interface PinResetResponse {
  emailSent: boolean;
  requiresBranchVisit: boolean;
  message: string;
}

export interface PinResetConfirmRequest {
  resetToken: string;
  newPin: string;
  newPinConfirmation: string;
}