import { Inject, Injectable } from '@angular/core';
import { Category, CategoryInput } from '../domain/category.model';
import { CATEGORY_REPOSITORY, CategoryRepository } from '../domain/category.repository';

@Injectable({ providedIn: 'root' })
export class CategoryFacade {
  items: Category[] = [];

  constructor(@Inject(CATEGORY_REPOSITORY) private readonly repository: CategoryRepository) {}

  /** Trả về repository để nơi gọi dùng trực tiếp (ca "biến cục bộ / nối chuỗi" của bộ phân tích). */
  getRepository(): CategoryRepository {
    return this.repository;
  }

  async load(keyword: string): Promise<void> {
    this.items = await this.repository.list(keyword);
  }

  async save(input: CategoryInput): Promise<void> {
    await this.repository.create(input);
    await this.load('');
  }

  async remove(id: number): Promise<void> {
    await this.repository.remove(id);
    await this.load('');
  }

  exportExcel(): Promise<Blob> {
    return this.repository.exportExcel();
  }
}
