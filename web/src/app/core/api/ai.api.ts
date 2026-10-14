import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom, map } from 'rxjs';
import type {
  AiGenerationDto,
  AiSettingsDto,
  AiTestDto,
  AiUsageDto,
  ApiEnvelope,
  EdgeAdviceDto,
  GenerateAiRequest
} from './api-types';

/** Nhóm API I (cấu hình AI, thống kê), F4 (sinh / sinh lại nội dung AI) và E2 (Nhờ AI phân tích liên kết). */
@Injectable({ providedIn: 'root' })
export class AiApi {
  private readonly http = inject(HttpClient);

  /** I1 — provider, model, trạng thái (READY / COOLING / OFF / NOT_CONFIGURED), hạn mức, từng mục đích. */
  settings(): Promise<AiSettingsDto> {
    return firstValueFrom(this.http.get<ApiEnvelope<AiSettingsDto>>('/api/settings/ai').pipe(map((r) => r.data)));
  }

  /** I2 — bật / tắt mục đích (chỉ cần gửi mục đích muốn đổi). */
  updatePurposes(purposes: Record<string, boolean>): Promise<AiSettingsDto> {
    return firstValueFrom(this.http.patch<ApiEnvelope<AiSettingsDto>>('/api/settings/ai', { purposes }).pipe(map((r) => r.data)));
  }

  /** I3 — gọi thử provider một lần ngắn. */
  test(): Promise<AiTestDto> {
    return firstValueFrom(this.http.post<ApiEnvelope<AiTestDto>>('/api/settings/ai/test', {}).pipe(map((r) => r.data)));
  }

  /** I6 — thống kê theo khoảng ngày (`yyyy-MM-dd`); bỏ trống thì 7 ngày gần nhất. */
  usage(from?: string, to?: string): Promise<AiUsageDto> {
    const params: Record<string, string> = {};
    if (from) {
      params['from_date'] = from;
    }
    if (to) {
      params['to_date'] = to;
    }
    return firstValueFrom(this.http.get<ApiEnvelope<AiUsageDto>>('/api/ai/usage', { params }).pipe(map((r) => r.data)));
  }

  /** F4 — sinh (hoặc sinh lại) nội dung AI cho một mục đích và đối tượng; AI lỗi → 503 kèm lý do, nội dung cũ được giữ. */
  generate(workspaceId: string, body: GenerateAiRequest): Promise<AiGenerationDto> {
    return firstValueFrom(
      this.http.post<ApiEnvelope<AiGenerationDto>>(`/api/workspaces/${workspaceId}/ai-artifacts/regenerate`, body).pipe(map((r) => r.data))
    );
  }

  /** E2 — AI giải thích từng ứng viên của liên kết; không bao giờ đổi liên kết. */
  advise(workspaceId: string, edgeId: string, force = false, startKey?: string): Promise<EdgeAdviceDto> {
    return firstValueFrom(
      this.http
        .post<ApiEnvelope<EdgeAdviceDto>>(`/api/workspaces/${workspaceId}/edges/${edgeId}/advice`, { force, ...(startKey ? { startKey } : {}) })
        .pipe(map((r) => r.data))
    );
  }
}
