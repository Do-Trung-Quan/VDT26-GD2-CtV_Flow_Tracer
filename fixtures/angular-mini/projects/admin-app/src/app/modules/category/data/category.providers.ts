import { Provider } from '@angular/core';
import { CATEGORY_REPOSITORY } from '../domain/category.repository';
import { HttpCategoryRepository } from './http-category.repository';

export const CATEGORY_PROVIDERS: Provider[] = [{ provide: CATEGORY_REPOSITORY, useExisting: HttpCategoryRepository }];
