import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { toApiError } from '../../../core/api/api-error';
import type { CodeTabDto } from '../../../core/api/api-types';
import { ExplanationApi } from '../../../core/api/explanation.api';
import { Modal } from '../../../core/ui/modal';
import { codeLines } from './explanation-model';

/** Popup "Xem code" (API C5): mã nguồn chỉ đọc, chia tab theo từng lớp trong luồng, đánh số dòng và tô sáng dòng liên quan. */
@Component({
  selector: 'app-code-dialog',
  imports: [Modal],
  template: `
    <app-modal [title]="'Xem code — ' + title()" [large]="true" (closed)="closed.emit()">
      @if (loading()) {
        @for (i of [1, 2, 3, 4]; track i) {
          <div class="skel"></div>
        }
      } @else if (error()) {
        <div class="errb">{{ error() }}</div>
      } @else if (current(); as c) {
        <div class="tabs cv-tabs" role="tablist">
          @for (t of tabs(); track $index; let i = $index) {
            <button class="tab" type="button" role="tab" [class.on]="i === active()" (click)="active.set(i)">{{ t.title }}</button>
          }
        </div>
        <div class="row file">
          <span class="mono muted">{{ c.filePath }}</span>
          <span class="sp1"></span>
          <span class="pill p-gray">Chỉ đọc</span>
        </div>
        <div class="codev" tabindex="0" aria-label="Mã nguồn chỉ đọc">
          @for (l of lines(); track l.n) {
            <div [class.hl]="l.hl"><i>{{ l.n }}</i>{{ l.text || ' ' }}</div>
          }
        </div>
      } @else {
        <div class="hint">Thành phần này không có đoạn code để xem.</div>
      }
      <ng-container footer>
        <button class="btn" type="button" (click)="closed.emit()">Đóng</button>
      </ng-container>
    </app-modal>
  `,
  styles: `
    .cv-tabs { margin: -6px 0 12px; overflow-x: auto; }
    .file { margin-bottom: 10px; }
    .codev { background: #0f172a; border-radius: 8px; overflow: auto; max-height: 56vh; font: 12.5px/1.6 var(--mono); color: #e2e8f0; }
    .codev div { display: flex; white-space: pre; padding-right: 14px; }
    .codev div i { flex: none; width: 44px; text-align: right; padding-right: 12px; color: #64748b; font-style: normal; user-select: none; }
    .codev div.hl { background: rgba(185, 0, 14, 0.35); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CodeDialog implements OnInit {
  private readonly api = inject(ExplanationApi);

  readonly workspaceId = input.required<string>();
  readonly nodeKey = input.required<string>();
  /** Tên thành phần, hiện trên tiêu đề. */
  readonly title = input.required<string>();
  readonly closed = output<void>();

  protected readonly tabs = signal<CodeTabDto[]>([]);
  protected readonly active = signal(0);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly current = computed(() => this.tabs()[this.active()] ?? null);
  protected readonly lines = computed(() => {
    const c = this.current();
    return c ? codeLines(c) : [];
  });

  async ngOnInit(): Promise<void> {
    try {
      this.tabs.set(await this.api.source(this.workspaceId(), this.nodeKey()));
    } catch (e) {
      this.error.set(toApiError(e).message);
    } finally {
      this.loading.set(false);
    }
  }
}
