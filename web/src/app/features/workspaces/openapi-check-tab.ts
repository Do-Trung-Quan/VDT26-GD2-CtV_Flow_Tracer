import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { OpenApiCheckDto } from '../../core/api/api-types';
import { ExplanationApi } from '../../core/api/explanation.api';
import { Icon } from '../../core/ui/icon';
import { openApiRows } from './openapi-check-model';

/**
 * Cài đặt › Đối chiếu OpenAPI (API C6): so sánh API tìm được từ mã nguồn với file đặc tả Swagger/OpenAPI trong repo, hoặc với
 * đặc tả của một Backend đang chạy sẵn (nhập URL). Tool không chạy dự án; URL chỉ được tải về như một tài liệu.
 */
@Component({
  selector: 'app-openapi-check-tab',
  imports: [Icon],
  template: `
    <div class="card pad">
      @if (probing()) {
        <div class="skel"></div>
      } @else if (repoFound()) {
        <div class="okb gap">
          <app-icon name="check" [small]="true" />
          <div>Đã tìm thấy file đặc tả trong repo: <b class="mono">{{ repoFound()!.source }}</b> ({{ repoFound()!.specVersion }}).</div>
        </div>
      } @else {
        <div class="warn gap">
          <app-icon name="alert" [small]="true" />
          <div>{{ repoNote() }}</div>
        </div>
      }

      <label class="lb" for="oaUrl">Hoặc đọc từ Backend đang chạy (tùy chọn)</label>
      <div class="row wrap">
        <input id="oaUrl" class="in url" placeholder="http://localhost:8080/v3/api-docs" autocomplete="off" [value]="url()"
          (input)="url.set(val($event))" (keydown.enter)="run()" />
        <button class="btn pri" type="button" [disabled]="running()" (click)="run()">Đối chiếu</button>
      </div>
      <div class="hint tip">
        Tool không chạy dự án của bạn; URL chỉ dùng khi Backend đã chạy sẵn ở đâu đó. Nội dung không phải OpenAPI 3 / Swagger 2 sẽ bị từ chối.
      </div>

      <div class="res">
        @if (running()) {
          <div class="skel"></div>
          <div class="skel" style="width: 70%"></div>
        } @else if (error()) {
          <div class="warn"><app-icon name="alert" [small]="true" /><div>{{ error() }} Không có gì được đối chiếu.</div></div>
        } @else if (result(); as r) {
          @if (r.source !== repoFound()?.source) {
            <div class="hint gap">Nguồn đặc tả: <b class="mono">{{ r.source }}</b></div>
          }
          <div class="stat3">
            <div class="card"><b class="g">{{ r.matched?.length ?? 0 }}</b><span>Khớp cả hai phía</span></div>
            <div class="card"><b class="a">{{ r.onlyInCode?.length ?? 0 }}</b><span>Chỉ có trong mã nguồn</span></div>
            <div class="card"><b class="r">{{ r.onlyInSpec?.length ?? 0 }}</b><span>Chỉ có trong Swagger</span></div>
          </div>
          @if (rows().length) {
            <div class="card clip">
              <table class="tbl">
                <thead><tr><th>Kết quả</th><th>API</th><th>Gợi ý</th></tr></thead>
                <tbody>
                  @for (row of rows(); track row.side + row.api) {
                    <tr>
                      <td><span [class]="'pill ' + row.pillClass">{{ row.pill }}</span></td>
                      <td class="mono">{{ row.api }}</td>
                      <td>{{ row.hint }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          } @else {
            <div class="okb"><app-icon name="check" [small]="true" /><div>Mọi API trong mã nguồn đều có trong đặc tả và ngược lại.</div></div>
          }
        }
      </div>
    </div>
  `,
  styles: `
    .pad { padding: 16px; }
    .gap { margin-bottom: 12px; }
    .wrap { flex-wrap: wrap; }
    .url { flex: 1; min-width: 260px; }
    .tip { margin-top: 6px; }
    .res { margin-top: 16px; }
    .stat3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px; }
    .stat3 .card { padding: 14px; }
    .stat3 b { display: block; font-size: 22px; }
    .stat3 span { color: var(--t3); font-size: 12.5px; }
    .g { color: var(--green); }
    .a { color: var(--amber); }
    .r { color: var(--brand); }
    .clip { overflow: hidden; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class OpenApiCheckTab {
  private readonly api = inject(ExplanationApi);

  readonly workspaceId = input.required<string>();

  protected readonly url = signal('');
  protected readonly probing = signal(false);
  /** Kết quả đối chiếu với file đặc tả trong repo (chạy một lần khi mở tab); null nếu repo không có file đó. */
  protected readonly repoFound = signal<OpenApiCheckDto | null>(null);
  protected readonly repoNote = signal('');
  protected readonly result = signal<OpenApiCheckDto | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly running = signal(false);
  protected readonly rows = computed(() => {
    const r = this.result();
    return r ? openApiRows(r) : [];
  });

  constructor() {
    effect(() => {
      const id = this.workspaceId();
      untracked(() => void this.probe(id));
    });
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  /** Đối chiếu với địa chỉ đã nhập; bỏ trống thì dùng lại file đặc tả trong repo. */
  protected async run(): Promise<void> {
    this.running.set(true);
    this.error.set(null);
    this.result.set(null);
    try {
      this.result.set(await this.api.openApiCheck(this.workspaceId(), this.url().trim() || undefined));
    } catch (e) {
      this.error.set(toApiError(e).message);
    } finally {
      this.running.set(false);
    }
  }

  /** Mở tab: thử đối chiếu với file đặc tả trong repo để báo ngay có tìm thấy hay không. */
  private async probe(id: string): Promise<void> {
    this.probing.set(true);
    this.result.set(null);
    this.repoFound.set(null);
    try {
      const r = await this.api.openApiCheck(id);
      if (id === this.workspaceId()) {
        this.repoFound.set(r);
        this.result.set(r);
      }
    } catch (e) {
      const err = toApiError(e);
      this.repoNote.set(
        err.code === 'OPENAPI_NOT_FOUND'
          ? 'Không tìm thấy file đặc tả Swagger/OpenAPI trong repo. Hãy nhập địa chỉ của một Backend đang chạy ở ô bên dưới.'
          : err.message
      );
    } finally {
      this.probing.set(false);
    }
  }
}
