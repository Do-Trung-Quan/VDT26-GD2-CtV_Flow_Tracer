import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, signal, untracked } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { LinkOverrideDto } from '../../core/api/api-types';
import { LinkingApi } from '../../core/api/linking.api';
import { Icon } from '../../core/ui/icon';
import { ToastService } from '../../core/ui/toast.service';
import { decisionLabel } from './link-settings-model';

/** Cài đặt › Liên kết chốt tay (API E3, E5): các quyết định đã chốt trên diagram; bỏ chốt để trở về kết quả tự động. */
@Component({
  selector: 'app-link-overrides-tab',
  imports: [Icon, DatePipe],
  template: `
    @if (loading()) {
      <div class="card pad"><div class="skel"></div><div class="skel" style="width: 70%"></div></div>
    } @else if (error()) {
      <div class="errb">{{ error() }}</div>
    } @else {
      <div class="card tblw">
        <table class="tbl">
          <thead><tr><th>Từ</th><th>Tới</th><th>Loại</th><th>Ghi chú</th><th>Thời điểm</th><th></th></tr></thead>
          <tbody>
            @for (o of items(); track o.id) {
              <tr>
                <td>
                  {{ o.fromName || o.fromNodeKey }}
                  @if (o.stale) {
                    <div class="hint">Không còn trong kết quả quét gần nhất — quyết định vẫn được giữ.</div>
                  }
                </td>
                <td class="mono">{{ o.toName || o.toNodeKey || '—' }}</td>
                <td><span [class]="'pill ' + decision(o).pill"><app-icon [name]="o.decision === 'REJECT' ? 'x' : 'pin'" [small]="true" />{{ decision(o).label }}</span></td>
                <td>{{ o.note || '—' }}</td>
                <td class="muted">{{ o.createdAt | date: 'dd/MM/yyyy HH:mm' }}</td>
                <td class="actions"><button class="btn sm danger" type="button" [disabled]="busy()" (click)="remove(o)">Bỏ chốt</button></td>
              </tr>
            } @empty {
              <tr>
                <td colspan="6">
                  <div class="empty">
                    <div class="ic"><app-icon name="pin" /></div>
                    <h3>Chưa có liên kết chốt tay</h3>
                    <span>Hãy bấm vào mũi tên "Suy luận" hoặc "Chưa xác định" trên diagram rồi chọn Chốt tay.</span>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: `
    .pad { padding: 16px; }
    .tblw { overflow: hidden; }
    .actions { text-align: right; white-space: nowrap; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class LinkOverridesTab {
  private readonly api = inject(LinkingApi);
  private readonly toast = inject(ToastService);

  readonly workspaceId = input.required<string>();

  protected readonly items = signal<LinkOverrideDto[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly busy = signal(false);

  constructor() {
    effect(() => {
      const id = this.workspaceId();
      untracked(() => void this.load(id));
    });
  }

  protected decision(o: LinkOverrideDto): { label: string; pill: string } {
    return decisionLabel(o);
  }

  private async load(id: string): Promise<void> {
    this.loading.set(true);
    try {
      this.items.set(await this.api.overrides(id));
      this.error.set(null);
    } catch (e) {
      this.error.set(toApiError(e).message);
    } finally {
      this.loading.set(false);
    }
  }

  protected async remove(o: LinkOverrideDto): Promise<void> {
    if (!o.id) {
      return;
    }
    this.busy.set(true);
    try {
      await this.api.deleteOverride(this.workspaceId(), o.id);
      this.items.set(this.items().filter((x) => x.id !== o.id));
      this.toast.show('Đã bỏ chốt tay — liên kết trở về kết quả tự động.', 'ok');
    } catch (e) {
      this.toast.show(toApiError(e).message, 'err');
    } finally {
      this.busy.set(false);
    }
  }
}
