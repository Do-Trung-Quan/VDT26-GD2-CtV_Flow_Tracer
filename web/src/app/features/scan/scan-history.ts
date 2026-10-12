import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { PageDto, ScanRunDto } from '../../core/api/api-types';
import { ScanApi } from '../../core/api/scan.api';
import { ScanErrorsDialog } from './scan-errors-dialog';
import { formatDuration, statusLabel, statusPill, triggerLabel } from './scan-format';
import { ScanTracker } from './scan-tracker';

const PAGE_SIZE = 20;

/** Cài đặt › Lịch sử quét (API B5): mỗi lần quét kèm số file mới / thay đổi / bị xóa / lỗi; xem file lỗi (B6). */
@Component({
  selector: 'app-scan-history',
  imports: [DatePipe, ScanErrorsDialog],
  template: `
    <div class="card clip">
      <table class="tbl">
        <thead>
          <tr>
            <th>Bắt đầu</th><th>Kiểu</th><th>Trạng thái</th><th class="num">Tổng file</th><th class="num">Mới</th>
            <th class="num">Thay đổi</th><th class="num">Bị xóa</th><th class="num">Lỗi</th><th class="num">Endpoint</th>
            <th class="num">Thời gian</th>
          </tr>
        </thead>
        <tbody>
          @if (loading() && !page()) {
            <tr><td colspan="10"><div class="skel"></div></td></tr>
          }
          @for (r of page()?.items ?? []; track r.id) {
            <tr>
              <td>{{ r.startedAt | date: 'HH:mm:ss dd/MM/yyyy' }}</td>
              <td>{{ trigger(r) }}</td>
              <td>
                <span [class]="'pill ' + pill(r)" [title]="r.errorSummary ?? ''">{{ label(r) }}</span>
                @if (r.stats?.noChanges) {
                  <div class="hint">không có thay đổi</div>
                } @else if (r.errorSummary && !r.stats) {
                  <div class="hint">{{ r.errorSummary }}</div>
                }
              </td>
              @if (r.stats; as st) {
                <td class="num">{{ st.files?.total }}</td>
                <td class="num">{{ st.files?.added }}</td>
                <td class="num">{{ st.files?.modified }}</td>
                <td class="num">{{ st.files?.removed }}</td>
                <td class="num">
                  @if ((st.files?.errors ?? 0) > 0) {
                    <button class="link" type="button" (click)="errorsOf.set(r.id!)">{{ st.files?.errors }}</button>
                  } @else {
                    0
                  }
                </td>
                <td class="num">{{ st.endpoints }}</td>
              } @else {
                <td class="num muted" colspan="6">—</td>
              }
              <td class="num">{{ duration(r) }}</td>
            </tr>
          } @empty {
            @if (!loading()) {
              <tr><td colspan="10" class="muted">{{ error() ?? 'Chưa có lần quét nào.' }}</td></tr>
            }
          }
        </tbody>
      </table>
    </div>
    @if (page(); as p) {
      @if (p.total > p.limit) {
        <div class="row pager">
          <button class="btn sm" type="button" [disabled]="p.page <= 1" (click)="load(p.page - 1)">Trang trước</button>
          <span class="hint">Trang {{ p.page }} / {{ pages(p) }} · {{ p.total }} lần quét</span>
          <button class="btn sm" type="button" [disabled]="p.page >= pages(p)" (click)="load(p.page + 1)">Trang sau</button>
        </div>
      }
    }
    <div class="hint note">
      "Mới / Thay đổi / Bị xóa" so với lần quét trước theo mã băm nội dung; chỉ tính file bộ phân tích xử lý (vd .java).
      Lần quét bị hủy hoặc thất bại không ghi gì, kết quả của lần quét trước được giữ nguyên.
    </div>
    @if (errorsOf(); as id) {
      <app-scan-errors-dialog [scanRunId]="id" (closed)="errorsOf.set(null)" />
    }
  `,
  styles: `
    .clip { overflow: auto; }
    .num { text-align: right; white-space: nowrap; }
    .pager { justify-content: flex-end; margin-top: 10px; }
    .note { margin-top: 10px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScanHistory {
  private readonly api = inject(ScanApi);
  private readonly tracker = inject(ScanTracker);

  readonly workspaceId = input.required<string>();

  protected readonly page = signal<PageDto<ScanRunDto> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly errorsOf = signal<string | null>(null);

  constructor() {
    effect(() => {
      const id = this.workspaceId();
      untracked(() => void this.load(1, id));
    });
    // Lần quét của Workspace này vừa xong → làm mới trang đầu
    effect(() => {
      const done = this.tracker.finished();
      if (done && done.workspaceId === untracked(() => this.workspaceId())) {
        untracked(() => void this.load(1));
      }
    });
  }

  protected label(r: ScanRunDto): string {
    return statusLabel(r.status);
  }

  protected pill(r: ScanRunDto): string {
    return statusPill(r.status);
  }

  protected trigger(r: ScanRunDto): string {
    return triggerLabel(r.trigger);
  }

  protected duration(r: ScanRunDto): string {
    return formatDuration(r.durationMs);
  }

  protected pages(p: PageDto<ScanRunDto>): number {
    return Math.max(1, Math.ceil(p.total / p.limit));
  }

  protected async load(page: number, id = this.workspaceId()): Promise<void> {
    this.loading.set(true);
    try {
      this.page.set(await this.api.history(id, page, PAGE_SIZE));
      this.error.set(null);
    } catch (e) {
      this.error.set(toApiError(e).message);
    } finally {
      this.loading.set(false);
    }
  }
}
