import { Injectable, inject, signal } from '@angular/core';
import { toApiError } from './api/api-error';
import type { ApiError, WorkspaceDto } from './api/api-types';
import { WorkspaceApi } from './api/workspace.api';

/** Trạng thái dùng chung: danh sách Workspace (cho header và trang danh sách), Workspace đang mở, wizard Import. */
@Injectable({ providedIn: 'root' })
export class WorkspaceContext {
  private readonly api = inject(WorkspaceApi);

  readonly workspaces = signal<WorkspaceDto[]>([]);
  readonly loading = signal(false);
  readonly error = signal<ApiError | null>(null);
  /** Workspace đang mở (lấy từ URL /w/:id). */
  readonly currentId = signal<string | null>(null);
  readonly importOpen = signal(false);
  /** Bảng tìm nhanh Ctrl+K đang mở. */
  readonly paletteOpen = signal(false);

  async reload(): Promise<void> {
    this.loading.set(true);
    try {
      this.workspaces.set(await this.api.list());
      this.error.set(null);
    } catch (e) {
      this.error.set(toApiError(e));
    } finally {
      this.loading.set(false);
    }
  }

  nameTaken(name: string): boolean {
    const n = name.trim().toLowerCase();
    return this.workspaces().some((w) => (w.name ?? '').trim().toLowerCase() === n);
  }
}
