export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export interface ErrorResponse {
  success: boolean;
  errorCode: string;
  message: string;
  details: string[] | null;
  timestamp: string;
}