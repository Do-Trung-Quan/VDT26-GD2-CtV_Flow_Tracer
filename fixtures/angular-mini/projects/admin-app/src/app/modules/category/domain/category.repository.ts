import { InjectionToken } from '@angular/core';
import { Category, CategoryInput } from './category.model';

export interface CategoryRepository {
  list(keyword: string): Promise<Category[]>;
  create(input: CategoryInput): Promise<Category>;
  remove(id: number): Promise<void>;
  exportExcel(): Promise<Blob>;
}

export const CATEGORY_REPOSITORY = new InjectionToken<CategoryRepository>('CategoryRepository');
