import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';
import { ErrorResponse } from '../models/api-response.model';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      const errorResponse: ErrorResponse = error.error ?? {
        success: false,
        errorCode: 'UNKNOWN_ERROR',
        message: 'Une erreur inattendue est survenue. Veuillez réessayer.',
        details: null,
        timestamp: new Date().toISOString()
      };

      return throwError(() => errorResponse);
    })
  );
};