import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom, map } from 'rxjs';
import type { ApiEnvelope, PageDto, ScanFileErrorDto, ScanProgressDto, ScanRunDto, ScanTrigger } from './api-types';

function unwrap<T>(response: Observable<ApiEnvelope<T>>): Promise<T> {
  return firstValueFrom(response.pipe(map((r) => r.data)));
}

/** Sự kiện của luồng tiến độ quét (API B2). */
export type ScanEvent = { type: 'progress'; data: ScanProgressDto } | { type: 'done'; data: ScanRunDto };

/** Nhóm API B — quét mã nguồn. */
@Injectable({ providedIn: 'root' })
export class ScanApi {
  private readonly http = inject(HttpClient);

  /** B1 */
  start(workspaceId: string, trigger: ScanTrigger): Promise<ScanRunDto> {
    return unwrap(this.http.post<ApiEnvelope<ScanRunDto>>(`/api/workspaces/${workspaceId}/scans`, { trigger }));
  }

  /** B3 */
  get(scanRunId: string): Promise<ScanRunDto> {
    return unwrap(this.http.get<ApiEnvelope<ScanRunDto>>(`/api/scans/${scanRunId}`));
  }

  /** B4 */
  cancel(scanRunId: string): Promise<ScanRunDto> {
    return unwrap(this.http.post<ApiEnvelope<ScanRunDto>>(`/api/scans/${scanRunId}/cancel`, null));
  }

  /** B5 */
  history(workspaceId: string, page: number, limit: number): Promise<PageDto<ScanRunDto>> {
    return unwrap(
      this.http.get<ApiEnvelope<PageDto<ScanRunDto>>>(`/api/workspaces/${workspaceId}/scans`, {
        params: { page, limit }
      })
    );
  }

  /** B6 */
  errors(scanRunId: string, page: number, limit: number): Promise<PageDto<ScanFileErrorDto>> {
    return unwrap(
      this.http.get<ApiEnvelope<PageDto<ScanFileErrorDto>>>(`/api/scans/${scanRunId}/errors`, { params: { page, limit } })
    );
  }

  /** B2 — luồng SSE; đóng khi nhận `done` hoặc khi hủy đăng ký. */
  events(scanRunId: string): Observable<ScanEvent> {
    return new Observable<ScanEvent>((subscriber) => {
      const source = new EventSource(`/api/scans/${scanRunId}/events`);
      source.addEventListener('progress', (e) =>
        subscriber.next({ type: 'progress', data: JSON.parse((e as MessageEvent<string>).data) as ScanProgressDto })
      );
      source.addEventListener('done', (e) => {
        subscriber.next({ type: 'done', data: JSON.parse((e as MessageEvent<string>).data) as ScanRunDto });
        subscriber.complete();
        source.close();
      });
      source.onerror = () => subscriber.error(new Error('Mất kết nối luồng tiến độ'));
      return () => source.close();
    });
  }
}
