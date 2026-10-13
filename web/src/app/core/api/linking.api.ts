import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom, map } from 'rxjs';
import type {
  ApiEnvelope,
  CreateLinkOverrideRequest,
  EdgeDetailDto,
  LinkOverrideDto,
  LinkRulesDto,
  UpdateLinkRulesRequest
} from './api-types';

function unwrap<T>(response: Observable<ApiEnvelope<T>>): Promise<T> {
  return firstValueFrom(response.pipe(map((r) => r.data)));
}

/** Kết quả lưu luật liên kết: dữ liệu kèm thông điệp của Engine ("Đã nối lại N liên kết"). */
export interface SavedLinkRules {
  rules: LinkRulesDto;
  message: string;
}

/** Nhóm API E — liên kết Frontend – Backend, chốt tay, luật liên kết. */
@Injectable({ providedIn: 'root' })
export class LinkingApi {
  private readonly http = inject(HttpClient);

  /** E1 — `edgeId` là `edgeRef` của bước trên diagram. */
  edge(workspaceId: string, edgeId: string): Promise<EdgeDetailDto> {
    return unwrap(this.http.get<ApiEnvelope<EdgeDetailDto>>(`/api/workspaces/${workspaceId}/edges/${edgeId}`));
  }

  /** E3 */
  overrides(workspaceId: string): Promise<LinkOverrideDto[]> {
    return unwrap(this.http.get<ApiEnvelope<LinkOverrideDto[]>>(`/api/workspaces/${workspaceId}/link-overrides`));
  }

  /** E4 */
  createOverride(workspaceId: string, body: CreateLinkOverrideRequest): Promise<LinkOverrideDto> {
    return unwrap(this.http.post<ApiEnvelope<LinkOverrideDto>>(`/api/workspaces/${workspaceId}/link-overrides`, body));
  }

  /** E5 */
  deleteOverride(workspaceId: string, overrideId: string): Promise<void> {
    return unwrap(this.http.delete<ApiEnvelope<void>>(`/api/workspaces/${workspaceId}/link-overrides/${overrideId}`));
  }

  /** E6 */
  rules(workspaceId: string): Promise<LinkRulesDto> {
    return unwrap(this.http.get<ApiEnvelope<LinkRulesDto>>(`/api/workspaces/${workspaceId}/link-rules`));
  }

  /** E7 — thay toàn bộ luật thủ công; Engine nối lại ngay. */
  saveRules(workspaceId: string, body: UpdateLinkRulesRequest): Promise<SavedLinkRules> {
    return firstValueFrom(
      this.http
        .put<ApiEnvelope<LinkRulesDto>>(`/api/workspaces/${workspaceId}/link-rules`, body)
        .pipe(map((r) => ({ rules: r.data, message: r.message })))
    );
  }
}
