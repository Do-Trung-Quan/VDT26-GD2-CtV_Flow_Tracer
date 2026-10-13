import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { NoiseFiltersDto } from '../../core/api/api-types';
import { WorkspaceApi } from '../../core/api/workspace.api';
import { Icon } from '../../core/ui/icon';
import { ToastService } from '../../core/ui/toast.service';

/** Cài đặt › Bộ lọc nhiễu (API I4–I5): bộ lọc mặc định của bộ phân tích (khóa) và bộ lọc do người dùng thêm. */
@Component({
  selector: 'app-noise-filters-tab',
  imports: [Icon],
  template: `
    @if (loading()) {
      <div class="card pad"><div class="skel"></div><div class="skel" style="width: 70%"></div></div>
    } @else if (error()) {
      <div class="errb">{{ error() }}</div>
    } @else if (filters(); as f) {
      <div class="card pad gap">
        <h4>Mặc định (khóa)</h4>
        <div class="chips">
          @for (n of f.defaults ?? []; track n.pattern) {
            <span class="chip" [title]="n.description ?? ''"><app-icon name="shield" [small]="true" /><span class="mono">{{ n.pattern }}</span></span>
          } @empty {
            <span class="hint">Không có bộ lọc mặc định.</span>
          }
        </div>
      </div>
      <div class="card pad">
        <h4>Do bạn thêm</h4>
        <div class="chips gap-s">
          @for (n of f.manual ?? []; track n.pattern; let i = $index) {
            <span class="chip" [title]="n.description ?? ''">
              <span class="mono">{{ n.pattern }}</span>
              <button type="button" [attr.aria-label]="'Bỏ bộ lọc ' + n.pattern" [disabled]="busy()" (click)="remove(i)"><app-icon name="x" [small]="true" /></button>
            </span>
          } @empty {
            <span class="hint">Chưa có bộ lọc riêng.</span>
          }
        </div>
        <div class="row">
          <input class="in mono addin" placeholder="Ví dụ: AuditHelper.* hoặc *Util.format" aria-label="Mẫu bộ lọc mới" maxlength="300"
            [value]="draft()" (input)="draft.set(val($event))" (keydown.enter)="add()" />
          <button class="btn pri" type="button" [disabled]="busy()" (click)="add()"><app-icon name="plus" [small]="true" />Thêm bộ lọc</button>
        </div>
        <div class="hint tip">Dấu * khớp mọi ký tự; mẫu so với tên đầy đủ <span class="mono">Lớp#hàm(kiểu)</span> của hàm Backend, hoặc tên thành phần.</div>
      </div>
    }
  `,
  styles: `
    .pad { padding: 16px; }
    .gap { margin-bottom: 14px; }
    h4 { margin: 0 0 10px; }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .gap-s { margin-bottom: 12px; }
    .addin { max-width: 360px; }
    .tip { margin-top: 8px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NoiseFiltersTab {
  private readonly api = inject(WorkspaceApi);
  private readonly toast = inject(ToastService);

  readonly workspaceId = input.required<string>();

  protected readonly filters = signal<NoiseFiltersDto | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly draft = signal('');

  constructor() {
    effect(() => {
      const id = this.workspaceId();
      untracked(() => void this.load(id));
    });
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    try {
      this.filters.set(await this.api.noiseFilters(id));
      this.error.set(null);
    } catch (e) {
      this.error.set(toApiError(e).message);
    } finally {
      this.loading.set(false);
    }
  }

  protected async add(): Promise<void> {
    const pattern = this.draft().trim();
    if (!pattern) {
      this.toast.show('Hãy nhập mẫu bộ lọc.', 'warn');
      return;
    }
    const manual = this.filters()?.manual ?? [];
    if ((this.filters()?.defaults ?? []).some((m) => m.pattern === pattern)) {
      this.toast.show('Bộ lọc này đã có trong danh sách mặc định.', 'warn');
      return;
    }
    if (manual.some((m) => m.pattern === pattern)) {
      this.toast.show('Bộ lọc này đã có.', 'warn');
      return;
    }
    if (await this.save([...manual.map((m) => ({ pattern: m.pattern ?? '', description: m.description })), { pattern, description: undefined }])) {
      this.draft.set('');
      this.toast.show('Đã thêm bộ lọc. Diagram sẽ ẩn các lời gọi khớp mẫu này.', 'ok');
    }
  }

  protected async remove(index: number): Promise<void> {
    const manual = (this.filters()?.manual ?? []).filter((_, i) => i !== index);
    if (await this.save(manual.map((m) => ({ pattern: m.pattern ?? '', description: m.description })))) {
      this.toast.show('Đã bỏ bộ lọc.', 'ok');
    }
  }

  private async save(manual: { pattern: string; description?: string }[]): Promise<boolean> {
    this.busy.set(true);
    try {
      this.filters.set(await this.api.updateNoiseFilters(this.workspaceId(), { manual }));
      return true;
    } catch (e) {
      this.toast.show(toApiError(e).message, 'err');
      return false;
    } finally {
      this.busy.set(false);
    }
  }
}
