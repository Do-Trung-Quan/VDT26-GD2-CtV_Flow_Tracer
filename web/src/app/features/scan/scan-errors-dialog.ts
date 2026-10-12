import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, signal } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { PageDto, ScanFileErrorDto } from '../../core/api/api-types';
import { ScanApi } from '../../core/api/scan.api';
import { Modal } from '../../core/ui/modal';

const PAGE_SIZE = 50;

/** Danh sách file không phân tích được của một lần quét (API B6), có phân trang. */
@Component({
  selector: 'app-scan-errors-dialog',
  imports: [Modal],
  template: `
    <app-modal title="File không phân tích được" [large]="true" (closed)="closed.emit()">
      @if (loading()) {
        @for (i of [1, 2, 3]; track i) {
          <div class="skel"></div>
        }
      } @else if (error()) {
        <div class="errb">{{ error() }}</div>
      } @else if (page(); as p) {
        <div class="hint gap">{{ p.total }} file. Các file này không có trên cây; phần còn lại của dự án không bị ảnh hưởng.</div>
        <div class="card clip">
          <table class="tbl">
            <thead><tr><th>File</th><th>Nguồn</th><th>Lý do</th></tr></thead>
            <tbody>
              @for (e of p.items; track e.fullPath) {
                <tr>
                  <td class="mono">{{ e.fullPath }}</td>
                  <td>{{ e.sourceName }}</td>
                  <td>{{ e.reason }}</td>
                </tr>
              } @empty {
                <tr><td colspan="3" class="muted">Không có file lỗi.</td></tr>
              }
            </tbody>
          </table>
        </div>
      }
      <ng-container footer>
        @if (page(); as p) {
          @if (p.total > p.limit) {
            <button class="btn sm" type="button" [disabled]="p.page <= 1" (click)="load(p.page - 1)">Trang trước</button>
            <span class="hint">Trang {{ p.page }} / {{ pages(p) }}</span>
            <button class="btn sm" type="button" [disabled]="p.page >= pages(p)" (click)="load(p.page + 1)">Trang sau</button>
          }
        }
        <button class="btn" type="button" (click)="closed.emit()">Đóng</button>
      </ng-container>
    </app-modal>
  `,
  styles: `
    .gap { margin-bottom: 10px; }
    .clip { overflow: hidden; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScanErrorsDialog implements OnInit {
  private readonly api = inject(ScanApi);

  readonly scanRunId = input.required<string>();
  readonly closed = output<void>();

  protected readonly page = signal<PageDto<ScanFileErrorDto> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.load(1);
  }

  protected pages(p: PageDto<ScanFileErrorDto>): number {
    return Math.max(1, Math.ceil(p.total / p.limit));
  }

  protected async load(page: number): Promise<void> {
    this.loading.set(true);
    try {
      this.page.set(await this.api.errors(this.scanRunId(), page, PAGE_SIZE));
      this.error.set(null);
    } catch (e) {
      this.error.set(toApiError(e).message);
    } finally {
      this.loading.set(false);
    }
  }
}
