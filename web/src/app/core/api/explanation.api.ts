import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map } from 'rxjs';
import type {
  ApiEnvelope,
  CodeTabDto,
  ExplanationDto,
  MockDataDto,
  NodeDetailDto,
  OpenApiCheckDto,
  ScenarioDto
} from './api-types';

/**
 * Khóa node đi trong đường dẫn phải mã hóa HAI lần: khóa chứa `/`, `#`, `:` nên một lần `%2F` bị máy chủ web giải mã trước
 * khi định tuyến; Engine giải mã lần thứ hai bằng `NodeKeyCodec.decode`.
 */
export function encodeNodeKey(nodeKey: string): string {
  return encodeURIComponent(encodeURIComponent(nodeKey));
}

/** Nhóm API C (chi tiết node, đoạn code, đối chiếu OpenAPI) và F (diễn giải, dữ liệu mẫu, kịch bản). */
@Injectable({ providedIn: 'root' })
export class ExplanationApi {
  private readonly http = inject(HttpClient);

  /** F1 — tab Diễn giải của một API, phần tử giao diện hoặc thành phần chứa. */
  explanation(workspaceId: string, nodeKey: string): Promise<ExplanationDto> {
    return this.get<ExplanationDto>(`/api/workspaces/${workspaceId}/nodes/${encodeNodeKey(nodeKey)}/explanation`);
  }

  /** F2 — dữ liệu mẫu của một API; `apiKey` bỏ trống thì lấy API đầu tiên của thành phần. */
  mockData(workspaceId: string, nodeKey: string, apiKey?: string): Promise<MockDataDto> {
    return firstValueFrom(
      this.http
        .get<ApiEnvelope<MockDataDto>>(`/api/workspaces/${workspaceId}/nodes/${encodeNodeKey(nodeKey)}/mock-data`, {
          params: apiKey ? { apiKey } : {}
        })
        .pipe(map((r) => r.data))
    );
  }

  /** F3 — tab Kịch bản của một luồng đã dò (cần `traceId` còn trong bộ nhớ đệm của Engine). */
  scenario(workspaceId: string, traceId: string): Promise<ScenarioDto> {
    return this.get<ScenarioDto>(`/api/workspaces/${workspaceId}/traces/${traceId}/scenario`);
  }

  /** C4 — chi tiết một node: vị trí nguồn, chuỗi thành phần chứa, được gọi từ / gọi tới. */
  nodeDetail(workspaceId: string, nodeKey: string): Promise<NodeDetailDto> {
    return this.get<NodeDetailDto>(`/api/workspaces/${workspaceId}/nodes/${encodeNodeKey(nodeKey)}`);
  }

  /** C5 — các đoạn code liên quan (mỗi lớp trong luồng một tab), chỉ đọc. */
  source(workspaceId: string, nodeKey: string, contextLines = 0): Promise<CodeTabDto[]> {
    return firstValueFrom(
      this.http
        .get<ApiEnvelope<CodeTabDto[]>>(`/api/workspaces/${workspaceId}/nodes/${encodeNodeKey(nodeKey)}/source`, {
          params: { context_lines: contextLines }
        })
        .pipe(map((r) => r.data))
    );
  }

  /** C6 — đối chiếu API trong mã nguồn với tài liệu OpenAPI; bỏ trống `openapiUrl` thì Engine tìm file trong mã nguồn. */
  openApiCheck(workspaceId: string, openapiUrl?: string): Promise<OpenApiCheckDto> {
    return firstValueFrom(
      this.http
        .post<ApiEnvelope<OpenApiCheckDto>>(`/api/workspaces/${workspaceId}/openapi-check`, { openapiUrl: openapiUrl || undefined })
        .pipe(map((r) => r.data))
    );
  }

  private get<T>(url: string): Promise<T> {
    return firstValueFrom(this.http.get<ApiEnvelope<T>>(url).pipe(map((r) => r.data)));
  }
}
