import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map } from 'rxjs';
import type { ApiEnvelope, BackendTreeDto } from './api-types';

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
}
