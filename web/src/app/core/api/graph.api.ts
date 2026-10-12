import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map } from 'rxjs';
import type { ApiEnvelope, BackendTreeDto, FrontendTreeDto, SearchCategory, SearchDto } from './api-types';

/** Nhóm API C — khám phá cấu trúc dự án. */
@Injectable({ providedIn: 'root' })
export class GraphApi {
  private readonly http = inject(HttpClient);

  /** C1 */
  backendTree(workspaceId: string): Promise<BackendTreeDto> {
    return firstValueFrom(
      this.http.get<ApiEnvelope<BackendTreeDto>>(`/api/workspaces/${workspaceId}/trees/backend`).pipe(map((r) => r.data))
    );
  }

  /** C2 */
  frontendTree(workspaceId: string): Promise<FrontendTreeDto> {
    return firstValueFrom(
      this.http.get<ApiEnvelope<FrontendTreeDto>>(`/api/workspaces/${workspaceId}/trees/frontend`).pipe(map((r) => r.data))
    );
  }

  /** C3 — tìm nhanh; `kinds` bỏ trống là mọi nhóm. */
  search(workspaceId: string, q: string, kinds?: SearchCategory[], limit?: number): Promise<SearchDto> {
    const params: Record<string, string | string[] | number> = { q };
    if (kinds?.length) {
      params['kinds'] = kinds;
    }
    if (limit) {
      params['limit'] = limit;
    }
    return firstValueFrom(
      this.http.get<ApiEnvelope<SearchDto>>(`/api/workspaces/${workspaceId}/search`, { params }).pipe(map((r) => r.data))
    );
  }
}
