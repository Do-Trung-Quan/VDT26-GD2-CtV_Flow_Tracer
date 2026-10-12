import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map } from 'rxjs';
import type { ApiEnvelope, ExportFormat, TraceDto, TraceExportDto } from './api-types';

/** Nhóm API D — dò vết và xuất diagram. */
@Injectable({ providedIn: 'root' })
export class TraceApi {
  private readonly http = inject(HttpClient);

  /** D1 — dò vết luồng từ một API Backend; `maxDepth` bỏ trống thì Engine dùng mặc định. */
  trace(workspaceId: string, startKey: string, maxDepth?: number): Promise<TraceDto> {
    return firstValueFrom(
      this.http
        .post<ApiEnvelope<TraceDto>>(`/api/workspaces/${workspaceId}/traces`, { startKey, maxDepth })
        .pipe(map((r) => r.data))
    );
  }

  /** D2 — mã PlantUML hoặc SVG do Engine sinh của một luồng đã dò. */
  export(workspaceId: string, traceId: string, format: ExportFormat): Promise<TraceExportDto> {
    return firstValueFrom(
      this.http
        .get<ApiEnvelope<TraceExportDto>>(`/api/workspaces/${workspaceId}/traces/${traceId}/export`, { params: { format } })
        .pipe(map((r) => r.data))
    );
  }
}
