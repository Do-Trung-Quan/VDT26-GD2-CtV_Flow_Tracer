import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { Category, CategoryInput } from '../domain/category.model';
import { CategoryRepository } from '../domain/category.repository';

const CATEGORY_BASE = `${environment.apiBase}/categories`;

@Injectable({ providedIn: 'root' })
export class HttpCategoryRepository implements CategoryRepository {
  private readonly http = inject(HttpClient);

  list(keyword: string): Promise<Category[]> {
    return firstValueFrom(this.http.get<Category[]>(CATEGORY_BASE, { params: { keyword } }));
  }

  create(input: CategoryInput): Promise<Category> {
    return firstValueFrom(this.http.post<Category>(CATEGORY_BASE, input));
  }

  remove(id: number): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`${CATEGORY_BASE}/${id}`));
  }

  exportExcel(): Promise<Blob> {
    return firstValueFrom(this.http.get(CATEGORY_BASE + '/export', { responseType: 'blob' }));
  }
}
