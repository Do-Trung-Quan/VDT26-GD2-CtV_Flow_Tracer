import { ChangeDetectionStrategy, Component, OnInit, computed, inject, output, signal } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { FolderBrowseDto } from '../../core/api/api-types';
import { WorkspaceApi } from '../../core/api/workspace.api';
import { Icon } from '../../core/ui/icon';
import { Modal } from '../../core/ui/modal';

/** Trình duyệt thư mục trong SOURCE_ROOT (API A6), dùng chung cho Import và Cài đặt › Nguồn mã. */
@Component({
  selector: 'app-folder-browser',
  imports: [Modal, Icon],
  template: `
    <app-modal title="Chọn thư mục mã nguồn" (closed)="closed.emit()">
      <div class="hint crumbs">
        Chỉ duyệt được bên trong thư mục gốc đã gắn vào tool (chỉ đọc). Đường dẫn:
        <button class="link" type="button" (click)="go('')">SOURCE_ROOT</button>
        @for (c of crumbs(); track c.path) {
          / <button class="link" type="button" (click)="go(c.path)">{{ c.name }}</button>
        }
      </div>
      <div class="card list">
        @if (loading()) {
          @for (i of [1, 2, 3, 4, 5]; track i) {
            <div class="skel"></div>
          }
        } @else if (error()) {
          <div class="errb">{{ error() }}</div>
        } @else {
          @for (f of data()?.folders ?? []; track f.path) {
            <button class="mi" type="button" (click)="go(f.path ?? '')">
              <app-icon name="folder" [small]="true" />
              <span class="sp1">{{ f.name }}</span>
              @if (f.gitRepo) {
                <span class="pill p-gray">Git</span>
              }
              <app-icon name="chev" [small]="true" />
            </button>
          } @empty {
            <div class="empty"><span>Thư mục không chứa thư mục con.</span></div>
          }
        }
      </div>
      <ng-container footer>
        <button class="btn" type="button" (click)="closed.emit()">Hủy</button>
        <button class="btn pri" type="button" [disabled]="loading() || !!error()" (click)="picked.emit(path())">
          Chọn thư mục này: <span class="mono">{{ path() || 'SOURCE_ROOT' }}</span>
        </button>
      </ng-container>
    </app-modal>
  `,
  styles: `
    .crumbs { margin-bottom: 8px; }
    .list { min-height: 220px; max-height: 320px; overflow: auto; padding: 6px; }
    .skel { margin: 12px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FolderBrowser implements OnInit {
  private readonly api = inject(WorkspaceApi);

  readonly picked = output<string>();
  readonly closed = output<void>();

  protected readonly path = signal('');
  protected readonly data = signal<FolderBrowseDto | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly crumbs = computed(() => {
    const parts = this.path() ? this.path().split('/') : [];
    return parts.map((name, i) => ({ name, path: parts.slice(0, i + 1).join('/') }));
  });

  ngOnInit(): void {
    void this.go('');
  }

  protected async go(path: string): Promise<void> {
    this.path.set(path);
    this.loading.set(true);
    this.error.set(null);
    try {
      this.data.set(await this.api.browse(path));
    } catch (e) {
      this.error.set(toApiError(e).message);
    } finally {
      this.loading.set(false);
    }
  }
}
