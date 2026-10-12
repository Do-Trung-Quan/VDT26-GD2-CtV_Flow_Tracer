import { ApplicationConfig } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { routes } from './app.routes';
import { CATEGORY_PROVIDERS } from './modules/category/data/category.providers';

export const appConfig: ApplicationConfig = {
  providers: [provideRouter(routes), provideHttpClient(), ...CATEGORY_PROVIDERS]
};
