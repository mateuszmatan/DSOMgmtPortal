import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideHttpClient, withFetch } from '@angular/common/http';
import {
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
} from '@angular/router';
import { SvgIconRegistryService, provideAngularSvgIcon } from 'angular-svg-icon';
import { routes } from './app.routes';
import { PortalTitleStrategy } from '@common/core/title-strategy';
import { registerIcons } from '@common/ui/design-system';
import { provideDialogs } from '@common/ui/dialog';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideHttpClient(withFetch()),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'enabled' }),
    ),
    { provide: TitleStrategy, useClass: PortalTitleStrategy },
    provideAngularSvgIcon(),
    provideDialogs(),
    provideAppInitializer(() => registerIcons(inject(SvgIconRegistryService))),
  ],
};
