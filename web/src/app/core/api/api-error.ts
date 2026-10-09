import { HttpErrorResponse } from '@angular/common/http';
import type { ApiEnvelope, ApiError } from './api-types';

/** Đọc lỗi từ vỏ bọc chuẩn của Engine; lỗi mạng / Engine chưa chạy thì trả thông điệp chung. */
export function toApiError(e: unknown): ApiError {
  if (e instanceof HttpErrorResponse) {
    const body = e.error as Partial<ApiEnvelope<unknown>> | null;
    if (body?.code) {
      const fieldErrors = Object.values(body.errors ?? {}).filter((v): v is string => typeof v === 'string');
      const message = body.code === 'VALIDATION_FAILED' && fieldErrors.length ? fieldErrors.join('; ') : (body.message ?? '');
      return { status: e.status, code: body.code, message, errors: body.errors ?? {} };
    }
    if (e.status === 0) {
      return { status: 0, code: 'NETWORK', message: 'Không kết nối được Engine (cổng 8600). Engine đã chạy chưa?', errors: {} };
    }
    return { status: e.status, code: 'HTTP_' + e.status, message: 'Engine trả lỗi ' + e.status, errors: {} };
  }
  return { status: 0, code: 'UNKNOWN', message: e instanceof Error ? e.message : String(e), errors: {} };
}
