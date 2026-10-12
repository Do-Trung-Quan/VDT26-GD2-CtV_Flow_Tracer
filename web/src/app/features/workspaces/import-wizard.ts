import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { Router } from '@angular/router';
import { toApiError } from '../../core/api/api-error';
import type { DetectedRootDto } from '../../core/api/api-types';
import { WorkspaceApi } from '../../core/api/workspace.api';
import { Icon } from '../../core/ui/icon';
import { Modal } from '../../core/ui/modal';
import { ToastService } from '../../core/ui/toast.service';
import { WorkspaceContext } from '../../core/workspace-context';
import { DetectionOutcome, ROLE_LABEL, ROLE_PILL, describeDetection, stackText } from './detection-message';
import { ScanTracker } from '../scan/scan-tracker';
import { FolderBrowser } from './folder-browser';

/** Import dự án — 3 bước: thông tin → chọn thư mục (tự nhận diện, A6 + A10) → xác nhận và tạo (A2). */
@Component({
  selector: 'app-import-wizard',
  imports: [Modal, Icon, FolderBrowser],
  template: `
    <app-modal title="Import dự án" [large]="true" (closed)="closed.emit()">
      <div class="steps-ind">
        @for (n of [1, 2, 3]; track n) {
          <div [class.on]="n <= step()"></div>
        }
      </div>

      @switch (step()) {
        @case (1) {
          <h3 class="ttl">Bước 1 · Thông tin Workspace</h3>
          <div class="fld">
            <label class="lb" for="impName">Tên Workspace *</label>
            <input id="impName" class="in" [class.bad]="!!nameError()" placeholder="Ví dụ: Shop" [value]="name()"
              (input)="name.set(val($event)); nameError.set(null)" />
            @if (nameError()) {
              <div class="err-text">{{ nameError() }}</div>
            }
          </div>
          <div class="fld">
            <label class="lb" for="impDesc">Mô tả</label>
            <textarea id="impDesc" class="in" rows="3" placeholder="Dự án này làm gì?" [value]="desc()"
              (input)="desc.set(val($event))"></textarea>
          </div>
        }
        @case (2) {
          <h3 class="ttl">Bước 2 · Thư mục mã nguồn</h3>
          <div class="hint gap">Chọn thư mục, tool tự nhận diện Backend / Frontend.</div>
          <div class="row gap">
            <input class="in sp1" placeholder="Nhập đường dẫn trong SOURCE_ROOT, ví dụ projects/shop" [value]="pathInput()"
              (input)="pathInput.set(val($event))" (keydown.enter)="pathInput().trim() && addFolder(pathInput())" />
            <button class="btn" type="button" [disabled]="busy() || !pathInput().trim()" (click)="addFolder(pathInput())">Thêm</button>
            <button class="btn pri" type="button" [disabled]="busy()" (click)="browsing.set(true)">
              <app-icon name="folder" [small]="true" />Duyệt thư mục…
            </button>
          </div>
          @if (busy()) {
            <div class="info gap"><span class="spinner"></span>Đang nhận diện thư mục…</div>
          }
          @if (outcome()?.note; as n) {
            <div [class]="n.kind + ' gap'">
              <app-icon name="alert" [small]="true" />
              <div>
                @for (l of n.lines; track $index) {
                  <div>{{ l }}</div>
                }
                @if (outcome()?.suggested; as root) {
                  <button class="link" type="button" (click)="addFolder(root)">Dùng {{ root }}</button>
                }
              </div>
            </div>
          }
          @for (r of roots(); track $index; let i = $index) {
            <div class="card src">
              <app-icon [name]="r.role === 'FRONTEND' ? 'monitor' : 'server'" />
              <div class="sp1">
                <div class="mono strong">{{ r.path }}</div>
                <div class="row pills">
                  <span [class]="'pill ' + pill(r)">{{ roleLabel(r) }}</span>
                  <span class="pill p-gray">{{ stack(r) }}</span>
                </div>
              </div>
              <button class="ibtn" type="button" aria-label="Bỏ thư mục" (click)="remove(i)"><app-icon name="trash" [small]="true" /></button>
            </div>
          } @empty {
            <div class="empty">
              <div class="ic"><app-icon name="folder" /></div>
              <span>Chưa có thư mục nào. Bấm <b>Duyệt thư mục…</b> để chọn.</span>
            </div>
          }
        }
        @case (3) {
          <h3 class="ttl">Bước 3 · Xác nhận</h3>
          <div class="hint gap">Kiểm tra kết quả nhận diện trước khi tạo Workspace.</div>
          <div class="card clip">
            <table class="tbl">
              <thead><tr><th>Thư mục</th><th>Vai trò</th><th>Công nghệ</th><th>Dấu hiệu nhận diện</th></tr></thead>
              <tbody>
                @for (r of roots(); track $index) {
                  <tr>
                    <td class="mono">{{ r.path }}</td>
                    <td><span [class]="'pill ' + pill(r)">{{ roleLabel(r) }}</span></td>
                    <td>{{ stack(r) }}</td>
                    <td class="mono">{{ r.evidence }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="hint top">Workspace <b>{{ name().trim() }}</b> · {{ roots().length }} nguồn mã. Mã nguồn chỉ được đọc, không build hay chạy.</div>
          @if (createError()) {
            <div class="errb top">{{ createError() }}</div>
          }
        }
      }

      <ng-container footer>
        @if (step() === 1) {
          <button class="btn" type="button" (click)="closed.emit()">Hủy</button>
        } @else {
          <button class="btn" type="button" [disabled]="busy()" (click)="step.set(step() - 1)">Quay lại</button>
        }
        @if (step() < 3) {
          <button class="btn pri" type="button" [disabled]="busy() || (step() === 2 && roots().length === 0)" (click)="next()">Tiếp tục</button>
        } @else {
          <button class="btn pri" type="button" [disabled]="busy()" (click)="create()">{{ busy() ? 'Đang tạo…' : 'Tạo & quét' }}</button>
        }
      </ng-container>
    </app-modal>

    @if (browsing()) {
      <app-folder-browser (closed)="browsing.set(false)" (picked)="browsing.set(false); addFolder($event)" />
    }
  `,
  styles: `
    .ttl { margin: 0 0 10px; }
    .gap { margin-bottom: 10px; }
    .top { margin-top: 12px; }
    .src { display: flex; align-items: center; gap: 10px; padding: 12px; margin-bottom: 8px; }
    .strong { font-weight: 600; }
    .pills { gap: 6px; margin-top: 4px; }
    .clip { overflow: hidden; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ImportWizard {
  private readonly api = inject(WorkspaceApi);
  private readonly ctx = inject(WorkspaceContext);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly scans = inject(ScanTracker);

  readonly closed = output<void>();

  protected readonly step = signal(1);
  protected readonly name = signal('');
  protected readonly desc = signal('');
  protected readonly nameError = signal<string | null>(null);
  protected readonly pathInput = signal('');
  protected readonly roots = signal<DetectedRootDto[]>([]);
  protected readonly outcome = signal<DetectionOutcome | null>(null);
  protected readonly busy = signal(false);
  protected readonly browsing = signal(false);
  protected readonly createError = signal<string | null>(null);

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  protected roleLabel(r: DetectedRootDto): string {
    return ROLE_LABEL[r.role ?? 'BACKEND'];
  }

  protected pill(r: DetectedRootDto): string {
    return ROLE_PILL[r.role ?? 'BACKEND'];
  }

  protected stack(r: DetectedRootDto): string {
    return stackText(r);
  }

  protected next(): void {
    if (this.step() === 1) {
      const n = this.name().trim();
      if (!n) {
        this.nameError.set('Vui lòng nhập tên Workspace.');
        return;
      }
      if (this.ctx.nameTaken(n)) {
        this.nameError.set('Tên Workspace đã được dùng. Hãy chọn tên khác.');
        return;
      }
    }
    this.step.update((s) => s + 1);
  }

  protected remove(i: number): void {
    this.roots.update((list) => list.filter((_, k) => k !== i));
    this.outcome.set(null);
  }

  protected async addFolder(raw: string): Promise<void> {
    const path = raw.trim().replace(/\\/g, '/').replace(/^\/+|\/+$/g, '');
    this.busy.set(true);
    this.outcome.set(null);
    try {
      const o = describeDetection(path, await this.api.detect(path), this.roots());
      this.roots.update((list) => [...list, ...o.fresh]);
      this.outcome.set(o);
      this.pathInput.set('');
    } catch (e) {
      this.outcome.set({ fresh: [], note: { kind: 'errb', lines: [toApiError(e).message] } });
    } finally {
      this.busy.set(false);
    }
  }

  protected async create(): Promise<void> {
    this.busy.set(true);
    this.createError.set(null);
    try {
      const ws = await this.api.create({
        name: this.name().trim(),
        description: this.desc().trim() || undefined,
        sources: this.roots().map((r) => ({ rootPath: r.path ?? '' }))
      });
      await this.ctx.reload();
      this.toast.show(`Đã tạo Workspace "${ws.name}" với ${ws.sources?.length ?? 0} nguồn mã. Bắt đầu quét…`, 'ok');
      this.closed.emit();
      await this.router.navigate(['/w', ws.id]);
      void this.scans.start(ws.id!, ws.name ?? '', 'IMPORT');
    } catch (e) {
      const err = toApiError(e);
      if (err.code === 'WORKSPACE_NAME_TAKEN') {
        this.step.set(1);
        this.nameError.set('Tên Workspace đã được dùng. Hãy chọn tên khác.');
      } else {
        this.createError.set(err.message);
      }
    } finally {
      this.busy.set(false);
    }
  }
}
