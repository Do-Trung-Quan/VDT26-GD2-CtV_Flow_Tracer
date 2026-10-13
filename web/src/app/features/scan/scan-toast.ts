import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import type { ScanStage } from '../../core/api/api-types';
import { Icon } from '../../core/ui/icon';
import { ScanErrorsDialog } from './scan-errors-dialog';
import { SCAN_STAGES, formatDuration, isRunning, progressFraction, statusLabel } from './scan-format';
import { ScanTracker } from './scan-tracker';

type StageState = 'done' | 'cur' | 'todo' | 'skipped';

/**
 * Khung tiến độ quét ở góc phải dưới: 7 giai đoạn, thanh tiến độ theo số mục thật đã xử lý, thu nhỏ, hủy (B4); khi xong
 * hiện tóm tắt có căn cứ (số file, thời gian đo được) và mở danh sách file lỗi (B6).
 */
@Component({
  selector: 'app-scan-toast',
  imports: [Icon, ScanErrorsDialog],
  template: `
    @if (tracker.current(); as s) {
      <section class="scan" [class.min]="s.minimized" aria-live="polite">
        <header class="sh">
          @if (running()) {
            <span class="spinner"></span>
          }
          <span class="sp1 ttl">{{ title() }}</span>
          <button class="hb" type="button" [attr.aria-label]="s.minimized ? 'Mở rộng' : 'Thu nhỏ'" (click)="tracker.toggleMinimized()">
            {{ s.minimized ? '▴' : '▾' }}
          </button>
          @if (!running()) {
            <button class="hb" type="button" aria-label="Đóng" (click)="tracker.dismiss()"><app-icon name="x" [small]="true" /></button>
          }
        </header>
        @if (!s.minimized) {
          <div class="sb">
            @if (running()) {
              <div class="row line">
                <span class="sp1">{{ s.progress?.message ?? 'Đang khởi động…' }}</span>
                @if (s.progress && s.progress.total > 0) {
                  <span class="muted">{{ s.progress.processed }}/{{ s.progress.total }}</span>
                }
              </div>
              <div class="bar"><i [style.width.%]="percent()"></i></div>
            }
            <ul class="stages">
              @for (st of stages(); track st.id) {
                <li [class]="'stg ' + st.state">
                  <span class="dot"></span>
                  <span class="sp1">{{ st.label }}</span>
                  <span class="muted">{{ st.note }}</span>
                </li>
              }
            </ul>
            @if (!running()) {
              @if (s.run.stats; as st) {
                <div class="sum">
                  @if (st.noChanges) {
                    <div>Không có file nào thay đổi kể từ lần quét trước.</div>
                  } @else {
                    @if ((st.configChanges ?? 0) > 0) {
                      <div>Có {{ st.configChanges }} file cấu hình thay đổi (vd application.yml) nên kết quả được ghép lại.</div>
                    }
                    <div>
                      {{ st.files?.total }} file · +{{ st.files?.added }} / ~{{ st.files?.modified }} / −{{ st.files?.removed }}
                      · {{ st.endpoints }} endpoint
                    </div>
                  }
                  <div class="muted">Thời gian: {{ duration(s.run.durationMs) }}</div>
                </div>
              } @else if (s.run.errorSummary) {
                <div class="sum">{{ s.run.errorSummary }}</div>
              }
              @if ((s.run.stats?.files?.errors ?? 0) > 0) {
                <button class="link" type="button" (click)="showErrors.set(true)">
                  Xem {{ s.run.stats?.files?.errors }} file lỗi
                </button>
              }
            } @else {
              <div class="actions">
                <button class="btn sm" type="button" [disabled]="s.cancelling" (click)="tracker.cancel()">
                  {{ s.cancelling ? 'Đang hủy…' : 'Hủy quét' }}
                </button>
              </div>
            }
          </div>
        }
      </section>
      @if (showErrors()) {
        <app-scan-errors-dialog [scanRunId]="s.run.id!" (closed)="showErrors.set(false)" />
      }
    }
  `,
  styles: `
    .scan { position: fixed; right: 16px; bottom: 16px; width: 350px; border-radius: 12px; overflow: hidden; box-shadow: var(--sh-xl); z-index: 150; background: #fff; }
    .sh { background: #1f2937; color: #fff; display: flex; align-items: center; gap: 8px; padding: 10px 12px; font-weight: 600; font-size: 13.5px; }
    .ttl { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .hb { border: 0; background: transparent; color: #fff; cursor: pointer; width: 24px; height: 24px; display: grid; place-items: center; border-radius: 6px; }
    .hb:hover { background: rgba(255, 255, 255, 0.15); }
    .sb { padding: 12px 14px; font-size: 13px; }
    .line { margin-bottom: 6px; }
    .bar { height: 6px; border-radius: 3px; background: #e5e7eb; overflow: hidden; margin-bottom: 10px; }
    .bar i { display: block; height: 100%; background: var(--brand); transition: width 0.2s; }
    .stages { list-style: none; margin: 0 0 8px; padding: 0; }
    .stg { display: flex; align-items: center; gap: 8px; font-size: 12.5px; color: var(--t3); padding: 2px 0; }
    .stg.done { color: var(--green); }
    .stg.cur { color: var(--t1); font-weight: 600; }
    .stg.skipped { color: var(--t4); }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: #d1d5db; flex: none; }
    .stg.done .dot { background: #16a34a; }
    .stg.cur .dot { background: var(--brand); }
    .sum { margin-bottom: 6px; }
    .actions { display: flex; justify-content: flex-end; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ScanToast {
  protected readonly tracker = inject(ScanTracker);
  protected readonly showErrors = signal(false);

  protected readonly running = computed(() => isRunning(this.tracker.current()?.run.status));

  protected readonly title = computed(() => {
    const s = this.tracker.current();
    if (!s) {
      return '';
    }
    return this.running() ? `Đang quét ${s.workspaceName}` : `${s.workspaceName}: ${statusLabel(s.run.status)}`;
  });

  protected readonly percent = computed(() => {
    const p = this.tracker.current()?.progress;
    return p ? Math.round(progressFraction(p.stageIndex, p.stageCount, p.processed, p.total) * 100) : 0;
  });

  /** Trạng thái từng giai đoạn: khi đang chạy theo sự kiện SSE; khi xong theo thời gian đo được của Engine. */
  protected readonly stages = computed(() => {
    const s = this.tracker.current();
    const ran = new Map<ScanStage, number>();
    (s?.run.stats?.stageDurations ?? []).forEach((d) => ran.set(d.stage as ScanStage, d.durationMs ?? 0));
    const currentIndex = s?.progress?.stageIndex ?? 0;
    return SCAN_STAGES.map((st, i) => {
      let state: StageState;
      let note = '';
      if (!this.running() && s?.run.stats) {
        state = ran.has(st.id) ? 'done' : 'skipped';
        note = ran.has(st.id) ? formatDuration(ran.get(st.id)) : 'bỏ qua';
      } else if (i + 1 < currentIndex) {
        state = 'done';
      } else if (i + 1 === currentIndex) {
        state = 'cur';
      } else {
        state = 'todo';
      }
      return { ...st, state, note };
    });
  });

  protected duration(ms: number | undefined): string {
    return formatDuration(ms);
  }
}
