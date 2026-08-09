import { ApplicationConfig, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { errorInterceptor } from './core/interceptors/error-interceptor';

/**
 * Fournisseurs de l'application.
 *
 * `provideAnimationsAsync()` a été retiré. Il était déprécié, mais surtout
 * inutile : aucun fichier n'importe `@angular/animations`, et les animations du
 * parcours (défilement des confettis, pulsation du cadre de vivacité) sont
 * écrites en CSS. Il chargeait donc un paquet entier pour rien.
 */
export const appConfig: ApplicationConfig = {
  providers: [
    provideZonelessChangeDetection(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([errorInterceptor])),
  ],
};
