import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router } from '@angular/router';
import { toApiError } from '../../core/api/api-error';
import type { SourceDto, WorkspaceDto } from '../../core/api/api-types';
import { WorkspaceApi } from '../../core/api/workspace.api';
import { ConfirmDialog } from '../../core/ui/confirm-dialog';
import { Icon } from '../../core/ui/icon';
import { ToastService } from '../../core/ui/toast.service';
import { WorkspaceContext } from '../../core/workspace-context';
import { statusLabel, statusPill } from '../scan/scan-format';
import { ROLE_LABEL, ROLE_PILL, stackText } from './detection-message';

/** Danh sách Workspace (API A1) — màn khởi đầu; menu mỗi thẻ: Mở · Cài đặt · Xóa (A5). */
@Component({
  selector: 'app-workspace-list',
  imports: [Icon, ConfirmDialog, DatePipe],
  template: `
    <section class="page">
      <div class="row head">
        <div class="sp1">
          <h1>Workspace</h1>
          <div class="sub">Mỗi Workspace là một dự án gồm một hoặc nhiều thư mục mã nguồn (Backend / Frontend).</div>
        </div>
        <button class="btn pri" type="button" (click)="ctx.importOpen.set(true)"><app-icon name="upload" [small]="true" />Import dự án</button>
      </div>

      @if (ctx.loading() && ctx.workspaces().length === 0) {
        <div class="grid">
          @for (i of [1, 2, 3]; track i) {
            <div class="card wsc"><div class="skel w60"></div><div class="skel"></div><div class="skel w40"></div></div>
          }
        </div>
      } @else if (ctx.error(); as err) {
        <div class="errb"><app-icon name="alert" [small]="true" />{{ err.message }}</div>
      } @else if (ctx.workspaces().length === 0) {
        <div class="card empty">
          <div class="ic"><app-icon name="folder" /></div>
          <h3>Chưa có Workspace nào</h3>
          <span>Bấm <b>Import dự án</b> và chọn thư mục mã nguồn. Tool tự nhận diện Backend / Frontend.</span>
          <button class="btn pri" type="button" (click)="ctx.importOpen.set(true)">Import dự án</button>
        </div>
      } @else {
        <div class="grid">
          @for (w of ctx.workspaces(); track w.id) {
            <div class="card wsc">
              <button class="open" type="button" (click)="open(w)">
                <h3>{{ w.name }}</h3>
                <div class="muted desc">{{ w.description || 'Không có mô tả' }}</div>
                <div class="meta">
                  @for (s of w.sources ?? []; track s.id) {
                    <span [class]="'pill ' + pill(s)" [title]="s.rootPath">{{ roleLabel(s) }} · {{ stack(s) }}</span>
                  } @empty {
                    <span class="pill p-gray">Chưa có nguồn mã</span>
                  }
                </div>
                <div class="foot">
                  <span>{{ w.sourceCount ?? 0 }} nguồn mã · <span [class]="'pill ' + scanPill(w)">{{ scanLabel(w) }}</span></span>
                  <span>
                    @if (w.lastScan; as s) {
                      Quét {{ (s.finishedAt ?? s.startedAt) | date: 'HH:mm dd/MM/yyyy' }}
                    } @else {
                      Tạo {{ w.createdAt | date: 'dd/MM/yyyy' }}
                    }
                  </span>
                </div>
              </button>
              <button class="ibtn menu-btn" type="button" aria-label="Thao tác" (click)="menuFor.set(menuFor() === w.id ? null : w.id!)">
                <app-icon name="dots" />
              </button>
              @if (menuFor() === w.id) {
                <div class="menu">
                  <button class="mi" type="button" (click)="open(w)"><app-icon name="layout" [small]="true" />Mở</button>
                  <button class="mi" type="button" (click)="settings(w)"><app-icon name="gear" [small]="true" />Cài đặt</button>
                  <div class="mdiv"></div>
                  <button class="mi red" type="button" (click)="menuFor.set(null); deleting.set(w)"><app-icon name="trash" [small]="true" />Xóa Workspace</button>
                </div>
              }
            </div>
          }
          <button class="card wsc add" type="button" (click)="ctx.importOpen.set(true)">
            <span><app-icon name="plus" /><br />Import dự án</span>
          </button>
        </div>
      }
    </section>

    @if (deleting(); as w) {
      <app-confirm-dialog title="Xóa Workspace?" okText="Xóa" [danger]="true" [busy]="busy()"
        (cancelled)="deleting.set(null)" (confirmed)="remove(w)">
        <p class="m0">
          Bạn sắp xóa <b>{{ w.name }}</b>. Dữ liệu phân tích trong CSDL của tool sẽ bị xóa;
          <b>mã nguồn của bạn không bị đụng tới</b>.
        </p>
      </app-confirm-dialog>
    }
  `,
  styles: `
    .head { align-items: flex-start; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(310px, 1fr)); gap: 16px; }
    .wsc { position: relative; min-height: 150px; }
    .open { all: unset; display: block; box-sizing: border-box; width: 100%; height: 100%; padding: 18px; cursor: pointer; border-radius: 12px; }
    .open:hover { background: #fcfcfd; }
    .open h3 { margin: 0 32px 2px 0; font-size: 16px; }
    .desc { font-size: 13px; }
    .meta { display: flex; flex-wrap: wrap; gap: 6px; margin: 12px 0; }
    .foot { display: flex; justify-content: space-between; color: var(--t3); font-size: 12.5px; }
    .menu-btn { position: absolute; top: 10px; right: 10px; }
    .menu { top: 44px; right: 10px; }
    .add { border: 2px dashed #d1d5db; box-shadow: none; background: transparent; display: grid; place-items: center; color: var(--t3); cursor: pointer; font: 600 14px var(--font); }
    .add:hover { border-color: var(--brand); color: var(--brand); background: #fff; }
    .w60 { width: 60%; }
    .w40 { width: 40%; }
    .m0 { margin: 0; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class WorkspaceListPage implements OnInit {
  protected readonly ctx = inject(WorkspaceContext);
  private readonly api = inject(WorkspaceApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly menuFor = signal<string | null>(null);
  protected readonly deleting = signal<WorkspaceDto | null>(null);
  protected readonly busy = signal(false);

  ngOnInit(): void {
    void this.ctx.reload();
  }

  protected roleLabel(s: SourceDto): string {
    return ROLE_LABEL[s.role ?? 'BACKEND'];
  }

  protected pill(s: SourceDto): string {
    return ROLE_PILL[s.role ?? 'BACKEND'];
  }

  protected scanLabel(w: WorkspaceDto): string {
    return statusLabel(w.lastScan?.status);
  }

  protected scanPill(w: WorkspaceDto): string {
    return statusPill(w.lastScan?.status);
  }

  protected stack(s: SourceDto): string {
    return s.detectedStacks?.[0] ? stackText(s.detectedStacks[0]) : '?';
  }

  protected open(w: WorkspaceDto): void {
    void this.router.navigate(['/w', w.id]);
  }

  protected settings(w: WorkspaceDto): void {
    void this.router.navigate(['/w', w.id, 'settings']);
  }

  protected async remove(w: WorkspaceDto): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.delete(w.id!);
      this.toast.show(`Đã xóa Workspace "${w.name}".`, 'ok');
      await this.ctx.reload();
    } catch (e) {
      this.toast.show(`Không xóa được: ${toApiError(e).message}`, 'err');
    } finally {
      this.busy.set(false);
      this.deleting.set(null);
    }
  }
}
