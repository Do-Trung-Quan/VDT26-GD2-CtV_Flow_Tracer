import { ChangeDetectionStrategy, Component, computed, inject, input, signal, effect } from '@angular/core';
import { Router } from '@angular/router';
import { toApiError } from '../../core/api/api-error';
import type { SourceDto, WorkspaceDto } from '../../core/api/api-types';
import { WorkspaceApi } from '../../core/api/workspace.api';
import { ConfirmDialog } from '../../core/ui/confirm-dialog';
import { Icon } from '../../core/ui/icon';
import { Modal } from '../../core/ui/modal';
import { ToastService } from '../../core/ui/toast.service';
import { WorkspaceContext } from '../../core/workspace-context';
import { ROLE_LABEL, ROLE_PILL, stackText } from './detection-message';
import { ScanHistory } from '../scan/scan-history';
import { FolderBrowser } from './folder-browser';

/**
 * Cài đặt Workspace › tab Nguồn mã và tab Lịch sử quét (B5). Tab Nguồn mã: thông tin Workspace (A3, A4), thêm / sửa tên và thư mục loại trừ / gỡ nguồn mã
 * (A7–A9), vùng nguy hiểm xóa Workspace (A5). Các tab khác (luật liên kết, bộ lọc nhiễu…) làm ở giai đoạn sau.
 */
@Component({
  selector: 'app-workspace-settings',
  imports: [Icon, Modal, ConfirmDialog, FolderBrowser, ScanHistory],
  template: `
    <section class="page">
      @if (loading() && !ws()) {
        <div class="skel w40"></div>
        <div class="card pad"><div class="skel"></div><div class="skel"></div></div>
      } @else if (loadError()) {
        <div class="errb"><app-icon name="alert" [small]="true" />{{ loadError() }}</div>
      } @else if (ws(); as w) {
        <h1>Cài đặt · {{ w.name }}</h1>
        <div class="sub">Thư mục mã nguồn và thông tin của Workspace.</div>
        <div class="tabs" role="tablist">
          <button class="tab" type="button" role="tab" [class.on]="tab() === 'sources'" (click)="tab.set('sources')">Nguồn mã</button>
          <button class="tab" type="button" role="tab" [class.on]="tab() === 'history'" (click)="tab.set('history')">Lịch sử quét</button>
        </div>

        @if (tab() === 'history') {
          <app-scan-history [workspaceId]="w.id!" />
        } @else {

        <div class="card pad block">
          <h3 class="h">Thông tin Workspace</h3>
          <div class="grid2">
            <div class="fld">
              <label class="lb" for="wsName">Tên *</label>
              <input id="wsName" class="in" [class.bad]="!!infoError()" [value]="name()" (input)="name.set(val($event)); infoError.set(null)" />
              @if (infoError()) {
                <div class="err-text">{{ infoError() }}</div>
              }
            </div>
            <div class="fld">
              <label class="lb" for="wsDesc">Mô tả</label>
              <input id="wsDesc" class="in" [value]="desc()" (input)="desc.set(val($event))" />
            </div>
          </div>
          <button class="btn sm" type="button" [disabled]="!infoDirty() || busy()" (click)="saveInfo(w)">Lưu thông tin</button>
        </div>

        <div class="card block">
          <div class="row pad">
            <h3 class="h sp1">Nguồn mã ({{ w.sources?.length ?? 0 }})</h3>
            <button class="btn pri sm" type="button" [disabled]="busy()" (click)="browsing.set(true)"><app-icon name="plus" [small]="true" />Thêm thư mục</button>
          </div>
          @if (addNote(); as n) {
            <div class="warn note">
              <app-icon name="alert" [small]="true" />
              <div>
                {{ n.text }}
                @if (n.suggested !== undefined) {
                  <button class="link" type="button" (click)="addSource(n.suggested)">Dùng {{ n.suggested || 'SOURCE_ROOT' }}</button>
                }
              </div>
            </div>
          }
          <table class="tbl">
            <thead><tr><th>Tên</th><th>Vai trò</th><th>Thư mục</th><th>Công nghệ</th><th>Thư mục loại trừ</th><th></th></tr></thead>
            <tbody>
              @for (s of w.sources ?? []; track s.id) {
                <tr>
                  <td><b>{{ s.name }}</b></td>
                  <td><span [class]="'pill ' + pill(s)">{{ roleLabel(s) }}</span></td>
                  <td class="mono">
                    {{ s.rootPath || 'SOURCE_ROOT' }}
                    @if (parentName(s); as p) {
                      <div class="hint">nằm trong nguồn {{ p }}</div>
                    }
                  </td>
                  <td>{{ stack(s) }}</td>
                  <td class="mono">{{ (s.excludeGlobs ?? []).join(', ') || '—' }}</td>
                  <td class="actions">
                    <button class="ibtn" type="button" aria-label="Sửa" (click)="startEdit(s)"><app-icon name="edit" [small]="true" /></button>
                    <button class="ibtn" type="button" aria-label="Gỡ" (click)="removing.set(s)"><app-icon name="trash" [small]="true" /></button>
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="muted">Chưa có nguồn mã. Bấm <b>Thêm thư mục</b>.</td></tr>
              }
            </tbody>
          </table>
          <div class="hint pad">
            Mặc định luôn bỏ qua node_modules, .git, dist, target, build, vendor, thư mục docs/ ở gốc nguồn, file nhị phân và file quá lớn.
            Nguồn nằm trong nguồn khác thì nguồn cha tự loại thư mục con để mỗi file chỉ được phân tích một lần.
          </div>
        </div>

        <div class="card pad danger-zone">
          <h3 class="h">Vùng nguy hiểm</h3>
          <div class="row">
            <div class="sp1 hint">Xóa Workspace cùng toàn bộ dữ liệu phân tích trong CSDL của tool. Mã nguồn không bị đụng tới.</div>
            <button class="btn danger" type="button" (click)="deletingWs.set(true)"><app-icon name="trash" [small]="true" />Xóa Workspace</button>
          </div>
        </div>
        }
      }
    </section>

    @if (browsing()) {
      <app-folder-browser (closed)="browsing.set(false)" (picked)="browsing.set(false); addSource($event)" />
    }

    @if (editing(); as s) {
      <app-modal title="Sửa nguồn mã" (closed)="editing.set(null)">
        <div class="fld">
          <label class="lb" for="srcName">Tên</label>
          <input id="srcName" class="in" [value]="editName()" (input)="editName.set(val($event))" />
        </div>
        <div class="fld">
          <label class="lb" for="srcEx">Thư mục loại trừ (mỗi dòng một glob, tương đối so với {{ s.rootPath || 'SOURCE_ROOT' }})</label>
          <textarea id="srcEx" class="in mono" rows="5" placeholder="generated/**" [value]="editExcludes()" (input)="editExcludes.set(val($event))"></textarea>
          <div class="hint">Luật loại trừ thư mục của nguồn con (nếu có) luôn được giữ lại.</div>
        </div>
        <ng-container footer>
          <button class="btn" type="button" (click)="editing.set(null)">Hủy</button>
          <button class="btn pri" type="button" [disabled]="busy()" (click)="saveEdit(s)">Lưu</button>
        </ng-container>
      </app-modal>
    }

    @if (removing(); as s) {
      <app-confirm-dialog title="Gỡ nguồn mã?" okText="Gỡ" [danger]="true" [busy]="busy()" (cancelled)="removing.set(null)" (confirmed)="removeSource(s)">
        <p class="m0">Gỡ <b>{{ s.name }}</b> (<span class="mono">{{ s.rootPath }}</span>) khỏi Workspace. Thư mục trên máy không bị đụng tới.</p>
      </app-confirm-dialog>
    }

    @if (deletingWs() && ws(); as w) {
      <app-confirm-dialog title="Xóa Workspace?" okText="Xóa" [danger]="true" [busy]="busy()" (cancelled)="deletingWs.set(false)" (confirmed)="deleteWorkspace(w)">
        <p class="m0">Bạn sắp xóa <b>{{ w.name }}</b>. Dữ liệu phân tích trong CSDL của tool sẽ bị xóa; <b>mã nguồn của bạn không bị đụng tới</b>.</p>
      </app-confirm-dialog>
    }
  `,
  styles: `
    .pad { padding: 14px 16px; }
    .block { margin-bottom: 16px; overflow: hidden; }
    .h { margin: 0 0 12px; font-size: 15px; }
    .row .h { margin: 0; }
    .grid2 { display: grid; grid-template-columns: 1fr 2fr; gap: 12px; }
    .note { margin: 0 16px 12px; }
    .actions { white-space: nowrap; text-align: right; }
    .danger-zone { box-shadow: inset 0 0 0 1px #fecaca; }
    .w40 { width: 40%; height: 22px; }
    .m0 { margin: 0; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkspaceSettingsPage {
  private readonly api = inject(WorkspaceApi);
  private readonly ctx = inject(WorkspaceContext);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Tham số route :workspaceId. */
  readonly workspaceId = input.required<string>();

  protected readonly ws = signal<WorkspaceDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal(false);

  protected readonly name = signal('');
  protected readonly desc = signal('');
  protected readonly infoError = signal<string | null>(null);
  protected readonly infoDirty = computed(
    () => this.name().trim() !== (this.ws()?.name ?? '') || this.desc().trim() !== (this.ws()?.description ?? '')
  );

  protected readonly browsing = signal(false);
  protected readonly addNote = signal<{ text: string; suggested?: string } | null>(null);
  protected readonly editing = signal<SourceDto | null>(null);
  protected readonly editName = signal('');
  protected readonly editExcludes = signal('');
  protected readonly removing = signal<SourceDto | null>(null);
  protected readonly deletingWs = signal(false);
  protected readonly tab = signal<'sources' | 'history'>('sources');

  constructor() {
    effect(() => {
      void this.load(this.workspaceId());
    });
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  protected roleLabel(s: SourceDto): string {
    return ROLE_LABEL[s.role ?? 'BACKEND'];
  }

  protected pill(s: SourceDto): string {
    return ROLE_PILL[s.role ?? 'BACKEND'];
  }

  protected stack(s: SourceDto): string {
    return s.detectedStacks?.[0] ? stackText(s.detectedStacks[0]) : '?';
  }

  protected parentName(s: SourceDto): string | undefined {
    return s.parentSourceId ? this.ws()?.sources?.find((p) => p.id === s.parentSourceId)?.name : undefined;
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    try {
      this.setWs(await this.api.get(id));
      this.loadError.set(null);
    } catch (e) {
      this.loadError.set(toApiError(e).message);
    } finally {
      this.loading.set(false);
    }
  }

  private setWs(w: WorkspaceDto): void {
    this.ws.set(w);
    this.name.set(w.name ?? '');
    this.desc.set(w.description ?? '');
  }

  private async refresh(): Promise<void> {
    this.setWs(await this.api.get(this.workspaceId()));
    void this.ctx.reload();
  }

  protected async saveInfo(w: WorkspaceDto): Promise<void> {
    if (!this.name().trim()) {
      this.infoError.set('Tên không được để trống.');
      return;
    }
    await this.run(async () => {
      this.setWs(await this.api.update(w.id!, { name: this.name().trim(), description: this.desc().trim() }));
      void this.ctx.reload();
      this.toast.show('Đã lưu thông tin Workspace.', 'ok');
    }, (code, msg) => (code === 'WORKSPACE_NAME_TAKEN' ? this.infoError.set('Tên Workspace đã được dùng.') : this.toast.show(msg, 'err')));
  }

  protected async addSource(raw: string): Promise<void> {
    const path = raw.trim().replace(/\\/g, '/').replace(/^\/+|\/+$/g, '');
    this.addNote.set(null);
    await this.run(async () => {
      const added = await this.api.addSource(this.workspaceId(), { rootPath: path });
      await this.refresh();
      this.toast.show(`Đã thêm ${added.length} nguồn mã: ${added.map((s) => s.rootPath).join(', ')}.`, 'ok');
    }, (code, msg, details) => {
      if (code === 'SOURCE_ALREADY_EXISTS') {
        this.toast.show('Thư mục này đã có trong Workspace.', 'warn');
      } else if (code === 'NO_PROJECT_ROOT') {
        const suggested = typeof details['suggestedRoot'] === 'string' ? details['suggestedRoot'] : undefined;
        this.addNote.set({ text: msg, suggested });
      } else {
        this.toast.show(msg, 'err');
      }
    });
  }

  protected startEdit(s: SourceDto): void {
    this.editName.set(s.name ?? '');
    this.editExcludes.set((s.excludeGlobs ?? []).join('\n'));
    this.editing.set(s);
  }

  protected async saveEdit(s: SourceDto): Promise<void> {
    const excludeGlobs = this.editExcludes()
      .split(/\r?\n/)
      .map((g) => g.trim())
      .filter(Boolean);
    await this.run(async () => {
      await this.api.updateSource(this.workspaceId(), s.id!, { name: this.editName().trim() || undefined, excludeGlobs });
      this.editing.set(null);
      await this.refresh();
      this.toast.show('Đã lưu nguồn mã.', 'ok');
    });
  }

  protected async removeSource(s: SourceDto): Promise<void> {
    await this.run(async () => {
      await this.api.deleteSource(this.workspaceId(), s.id!);
      await this.refresh();
      this.toast.show(`Đã gỡ nguồn mã "${s.name}".`, 'ok');
    });
    this.removing.set(null);
  }

  protected async deleteWorkspace(w: WorkspaceDto): Promise<void> {
    await this.run(async () => {
      await this.api.delete(w.id!);
      await this.ctx.reload();
      this.toast.show(`Đã xóa Workspace "${w.name}".`, 'ok');
      await this.router.navigate(['/']);
    });
    this.deletingWs.set(false);
  }

  /** Chạy một thao tác ghi; lỗi mặc định hiện toast. */
  private async run(
    fn: () => Promise<void>,
    onError: (code: string, message: string, details: Record<string, unknown>) => void = (_c, m) => this.toast.show(m, 'err')
  ): Promise<void> {
    this.busy.set(true);
    try {
      await fn();
    } catch (e) {
      const err = toApiError(e);
      onError(err.code, err.message, err.errors);
    } finally {
      this.busy.set(false);
    }
  }
}
