import { ChangeDetectionStrategy, Component, OnInit, computed, effect, inject, signal, untracked } from '@angular/core';
import { AiStatus, clockOf } from '../../core/ai/ai-status';
import { AiApi } from '../../core/api/ai.api';
import { toApiError } from '../../core/api/api-error';
import type { AiSettingsDto, AiUsageDto } from '../../core/api/api-types';
import { Icon } from '../../core/ui/icon';
import { ToastService } from '../../core/ui/toast.service';
import { UsageRange, barHeight, rangeDates, vnNumber } from './ai-usage-model';

/**
 * Cấu hình AI (nhóm API I1, I2, I3, I6): nhà cung cấp (chỉ xem — provider, model và khóa API đặt bằng biến môi trường), kiểm tra
 * kết nối, bật / tắt từng mục đích, hạn mức token trong ngày và thống kê sử dụng. Khi provider đang nghỉ sau lỗi 429 / 503 thì
 * hiện banner kèm giờ thử lại; mọi tính năng vẫn chạy bằng nội dung tự sinh.
 */
@Component({
  selector: 'app-ai-settings',
  imports: [Icon],
  template: `
    <div class="page">
      <h1>Cấu hình AI</h1>
      <div class="sub">
        AI chỉ <b>viết văn bản, giải thích và đặt tên hiển thị</b> — không bao giờ vẽ diagram, tạo thành phần hay tự đổi liên kết. Mọi tính năng
        vẫn chạy khi tắt AI.
      </div>

      @if (loading() && !s()) {
        <div class="card pad">
          @for (w of [90, 70, 80, 60, 75]; track $index) {
            <div class="skel" [style.width.%]="w"></div>
          }
        </div>
      } @else if (error()) {
        <div class="errb">{{ error() }}</div>
      } @else if (s(); as st) {
        <div class="card pad mb">
          <div class="row wrap">
            <div class="sp1">
              <div class="row">
                <b class="big">Nhà cung cấp</b>
                <span [class]="'pill ' + status().pill">{{ status().label }}</span>
              </div>
              <div class="row facts">
                <div>
                  <div class="hint">Provider</div>
                  <b>{{ st.provider === 'none' ? 'none' : (st.provider ?? '—') }}</b>
                </div>
                <div>
                  <div class="hint">Model</div>
                  <b class="mono">{{ st.model || '—' }}</b>
                </div>
                @if (st.keyVariable) {
                  <div>
                    <div class="hint">Khóa API</div>
                    <b>Đọc từ biến môi trường <span class="mono">{{ st.keyVariable }}</span></b>
                  </div>
                }
              </div>
            </div>
            <div class="testcol">
              <button class="btn pri" type="button" [disabled]="status$.testing() || st.status === 'OFF'" (click)="status$.runTest()">
                <app-icon name="bolt" [small]="true" />{{ status$.testing() ? 'Đang kiểm tra…' : 'Kiểm tra kết nối' }}
              </button>
            </div>
          </div>
          @if (st.status === 'COOLING' || st.status === 'OVERLOADED') {
            <div class="warn top14"><app-icon name="alert" [small]="true" />{{ st.statusMessage }}</div>
          } @else if (st.status === 'NOT_CONFIGURED' || st.status === 'ERROR') {
            <div class="errb top14"><app-icon name="alert" [small]="true" />{{ st.statusMessage }}</div>
          } @else if (st.status === 'OFF') {
            <div class="info top14"><app-icon name="info" [small]="true" />{{ st.statusMessage }}</div>
          }
          @if (status$.testing()) {
            <div class="info top12" role="status" aria-live="polite">
              <span class="spin"></span>
              <div>Đang kiểm tra kết nối… <b>{{ status$.testElapsed() }} giây</b></div>
            </div>
          } @else if (status$.lastTest(); as t) {
            @if (t.result.ok) {
              <div class="okb top12"><app-icon name="check" [small]="true" />Kết nối thành công · độ trễ khoảng {{ seconds(t.result.latencyMs) }} giây.</div>
            } @else if (st.status === 'READY' || st.status === 'OFF') {
              <div class="errb top12"><app-icon name="alert" [small]="true" />{{ t.result.message }}</div>
            }
          }
        </div>

        <div class="card pad mb">
          <b class="big">Mục đích sử dụng AI</b>
          <div class="hint bot8">Bật/tắt từng mục đích. Tắt mục đích nào thì phần đó dùng nội dung tự sinh bằng luật. Nội dung AI đã lưu vẫn dùng khi AI tắt hoặc hết hạn mức.</div>
          @for (p of st.purposes ?? []; track p.purpose) {
            <div class="row prow">
              <div class="sp1">
                <div class="pt">{{ p.label }}</div>
                <div class="hint">{{ p.description }}</div>
              </div>
              <button class="sw" type="button" role="switch" [class.on]="p.enabled" [attr.aria-checked]="p.enabled" [attr.aria-label]="p.label"
                [disabled]="st.status === 'OFF' || saving() === p.purpose" (click)="toggle(p.purpose ?? '', !p.enabled)"></button>
            </div>
          }
        </div>

        <div class="card pad mb">
          <b class="big">Mức dùng hôm nay</b>
          <div class="today">
            <div>
              <div class="hint">Lượt gọi AI đã xử lý</div>
              <div><b class="mono big">{{ num(st.requestsToday) }}{{ st.dailyRequestLimit ? ' / ' + num(st.dailyRequestLimit) : '' }}</b> lượt</div>
              @if (st.dailyRequestLimit) {
                <div class="bar top10"><i [style.width.%]="requestPercent()"></i></div>
                <div class="hint top6">Hạn mức theo ngày do nhà cung cấp báo.</div>
              } @else {
                <div class="hint top6">Chưa biết hạn mức theo ngày: nhà cung cấp chỉ báo khi chạm giới hạn. Xem hạn mức của khóa trong Google AI Studio.</div>
              }
            </div>
            <div>
              <div class="hint">Token đã dùng</div>
              <div><b class="mono big">{{ num(st.tokensToday) }}{{ st.dailyTokenLimit ? ' / ' + num(st.dailyTokenLimit) : '' }}</b> token</div>
              @if (st.dailyTokenLimit) {
                <div class="bar top10"><i [style.width.%]="tokenPercent()"></i></div>
                <div class="hint top6">Giới hạn do bạn đặt bằng <span class="mono">LLM_DAILY_TOKEN_LIMIT</span>.</div>
              }
            </div>
          </div>
        </div>

        <div class="card pad">
          <div class="row bot14">
            <b class="big sp1">Thống kê sử dụng</b>
            <div class="seg">
              <button type="button" [class.on]="range() === 1" (click)="setRange(1)">1 ngày</button>
              <button type="button" [class.on]="range() === 7" (click)="setRange(7)">7 ngày</button>
              <button type="button" [class.on]="range() === 'all'" (click)="setRange('all')">Tất cả</button>
            </div>
          </div>
          @if (usageError()) {
            <div class="errb">{{ usageError() }}</div>
          } @else if (usage(); as u) {
            <div class="stat4">
              <div class="card"><b>{{ num(u.calls) }}</b><span>Lượt gọi AI (lượt, gồm cả kiểm tra kết nối)</span></div>
              <div class="card"><b>{{ num((u.inputTokens ?? 0) + (u.outputTokens ?? 0)) }}</b><span>Token đã dùng (vào + ra)</span></div>
              <div class="card">
                <b [class.err]="(u.errors ?? 0) > 0">{{ num(u.errors) }}</b>
                <span>Lượt lỗi · TB {{ seconds(u.avgLatencyMs) }} giây/lượt thành công</span>
              </div>
            </div>
            @if (u.byPurpose?.length) {
              <div class="legend">
                <span><i class="lg tok"></i>Token (token)</span>
                <span><i class="lg cal"></i>Lượt gọi AI (lượt)</span>
                <span class="hint">Mỗi loại tính theo thang riêng</span>
              </div>
              <div class="bars">
                @for (x of u.byPurpose; track x.purpose) {
                  <div class="bc">
                    <div class="col"><span>{{ num(x.tokens) }}</span><i class="tok" [style.height.px]="barTokens(x.tokens ?? 0)"></i></div>
                    <div class="col"><span>{{ num(x.calls) }}</span><i class="cal" [style.height.px]="barCalls(x.calls ?? 0)"></i></div>
                  </div>
                }
              </div>
              <div class="row labels">
                @for (x of u.byPurpose; track x.purpose) {
                  <div class="sp1 lbl">{{ x.label }}</div>
                }
              </div>
              <div class="card tblwrap">
                <table class="tbl">
                  <thead>
                    <tr>
                      <th>Mục đích</th>
                      <th title="Số lần gọi nhà cung cấp AI">Lượt gọi AI (lượt)</th>
                      <th title="Token vào + ra do nhà cung cấp trả về">Token (token)</th>
                      <th title="Lượt gọi lỗi, bị loại do đầu ra sai hoặc hết hạn mức">Lỗi (lượt)</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (x of u.byPurpose; track x.purpose) {
                      <tr>
                        <td>{{ x.label }}</td>
                        <td>{{ num(x.calls) }}</td>
                        <td>{{ num(x.tokens) }}</td>
                        <td>{{ num(x.errors) }}</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else {
              <div class="hint">Chưa có lượt gọi AI nào trong khoảng này.</div>
            }
          } @else {
            <div class="skel"></div>
            <div class="skel" style="width: 60%"></div>
          }
        </div>
      }
    </div>
  `,
  styles: `
    .pad { padding: 18px; }
    .mb { margin-bottom: 16px; }
    .big { font-size: 15px; }
    .wrap { flex-wrap: wrap; }
    .facts { margin-top: 10px; gap: 28px; flex-wrap: wrap; align-items: flex-start; }
    .testcol { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; max-width: 260px; text-align: right; }
    .spin { width: 14px; height: 14px; border-radius: 50%; flex: none; border: 2px solid #bae6fd; border-top-color: var(--blue); animation: rot 0.8s linear infinite; }
    @keyframes rot { to { transform: rotate(360deg); } }
    .top14 { margin-top: 14px; }
    .top12 { margin-top: 12px; }
    .top10 { margin-top: 10px; }
    .top6 { margin-top: 6px; }
    .bot8 { margin-bottom: 8px; }
    .bot14 { margin-bottom: 14px; }
    .prow { padding: 12px 0; box-shadow: inset 0 -1px 0 #f3f4f6; }
    .prow:last-child { box-shadow: none; }
    .pt { font-weight: 600; }
    .pt .mono { font-weight: 400; }
    .sw { width: 38px; height: 22px; border-radius: 12px; background: #d1d5db; position: relative; cursor: pointer; flex: none; transition: 0.15s; border: 0; padding: 0; }
    .sw::after { content: ''; position: absolute; top: 2px; left: 2px; width: 18px; height: 18px; border-radius: 50%; background: #fff; box-shadow: var(--sh-sm); transition: 0.15s; }
    .sw.on { background: var(--brand); }
    .sw.on::after { left: 18px; }
    .sw:disabled { opacity: 0.45; cursor: not-allowed; }
    .bar { height: 8px; border-radius: 4px; background: #e5e7eb; overflow: hidden; }
    .bar i { display: block; height: 100%; background: var(--brand); border-radius: 4px; transition: width 0.2s; }
    .seg { display: inline-flex; background: #f3f4f6; border-radius: 8px; padding: 2px; }
    .seg button { border: 0; background: transparent; height: 28px; padding: 0 10px; border-radius: 6px; font: 600 12.5px var(--font); color: var(--t3); cursor: pointer; }
    .seg button.on { background: #fff; color: var(--brand); box-shadow: var(--sh-xs); }
    .stat4 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px; }
    .stat4 .card { padding: 14px; }
    .stat4 b { display: block; font-size: 22px; }
    .stat4 b.err { color: var(--brand); }
    .stat4 span { color: var(--t3); font-size: 12.5px; }
    .today { display: grid; grid-template-columns: repeat(2, 1fr); gap: 24px; margin-top: 12px; }
    .legend { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; font-size: 12.5px; color: var(--t2); margin-bottom: 4px; }
    .legend .lg { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 6px; }
    .tok { background: var(--brand); }
    .cal { background: var(--blue); }
    .bars { display: flex; align-items: flex-end; gap: 16px; height: 150px; padding: 10px 6px 0; box-shadow: inset 0 -1px 0 var(--bd); }
    .bc { flex: 1; display: flex; align-items: flex-end; justify-content: center; gap: 8px; height: 100%; }
    .col { display: flex; flex-direction: column; align-items: center; justify-content: flex-end; gap: 4px; font-size: 12px; color: var(--t3); }
    .col i { width: 28px; border-radius: 6px 6px 0 0; display: block; }
    .labels { gap: 16px; margin-top: 6px; align-items: flex-start; }
    .lbl { text-align: center; font-size: 12px; color: var(--t2); }
    .tblwrap { overflow: hidden; margin-top: 16px; }
    @media (max-width: 900px) { .stat4 { grid-template-columns: repeat(2, 1fr); } .today { grid-template-columns: 1fr; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AiSettingsPage implements OnInit {
  private readonly api = inject(AiApi);
  private readonly toast = inject(ToastService);
  protected readonly status$ = inject(AiStatus);

  protected readonly s = signal<AiSettingsDto | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal<string | null>(null);
  protected readonly range = signal<UsageRange>(7);
  protected readonly usage = signal<AiUsageDto | null>(null);
  protected readonly usageError = signal<string | null>(null);

  protected readonly status = computed(() => {
    const st = this.s();
    switch (st?.status) {
      case 'CHECKING':
        return { label: 'Đang kiểm tra', pill: 'p-blue' };
      case 'READY':
        return { label: 'Sẵn sàng', pill: 'p-green' };
      case 'COOLING':
        return { label: `Đạt giới hạn${st.cooldownUntil ? ' tới ' + clockOf(st.cooldownUntil) : ''}`, pill: 'p-amber' };
      case 'OVERLOADED':
        return { label: 'Đang quá tải', pill: 'p-amber' };
      case 'ERROR':
        return { label: 'Bị lỗi', pill: 'p-red' };
      case 'NOT_CONFIGURED':
        return { label: 'Chưa cấu hình đủ', pill: 'p-red' };
      default:
        return { label: 'Đã tắt (provider = none)', pill: 'p-gray' };
    }
  });
  protected readonly tokenPercent = computed(() => {
    const st = this.s();
    return st?.dailyTokenLimit ? Math.min(100, ((st.tokensToday ?? 0) / st.dailyTokenLimit) * 100) : 0;
  });

  protected readonly requestPercent = computed(() => {
    const st = this.s();
    return st?.dailyRequestLimit ? Math.min(100, ((st.requestsToday ?? 0) / st.dailyRequestLimit) * 100) : 0;
  });

  protected readonly num = vnNumber;

  constructor() {
    // Có kết quả kiểm tra mới (kể cả lần chạy bắt đầu ở lần mở trang trước) → nạp lại trạng thái nhà cung cấp để hiện đúng "Sẵn sàng / Đang nghỉ"
    effect(() => {
      this.status$.lastTest();
      untracked(() => void this.load());
    });
  }

  ngOnInit(): void {
    void this.loadUsage();
  }

  protected seconds(ms: number | undefined): string {
    return ((ms ?? 0) / 1000).toFixed(1).replace('.', ',');
  }

  protected barTokens(tokens: number): number {
    return barHeight(tokens, this.usage()?.byPurpose?.map((x) => x.tokens ?? 0) ?? []);
  }

  protected barCalls(calls: number): number {
    return barHeight(calls, this.usage()?.byPurpose?.map((x) => x.calls ?? 0) ?? []);
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    try {
      this.s.set(await this.api.settings());
      this.error.set(null);
    } catch (e) {
      this.error.set(toApiError(e).message);
    } finally {
      this.loading.set(false);
    }
  }

  private async loadUsage(): Promise<void> {
    const { from, to } = rangeDates(this.range());
    try {
      this.usage.set(await this.api.usage(from, to));
      this.usageError.set(null);
    } catch (e) {
      this.usageError.set(toApiError(e).message);
    }
  }

  protected setRange(r: UsageRange): void {
    this.range.set(r);
    this.usage.set(null);
    void this.loadUsage();
  }

  /** I2 — bật / tắt một mục đích. */
  protected async toggle(purpose: string, enabled: boolean): Promise<void> {
    this.saving.set(purpose);
    try {
      const next = await this.api.updatePurposes({ [purpose]: enabled });
      this.s.set(next);
      this.status$.settings.set(next);
      const label = next.purposes?.find((p) => p.purpose === purpose)?.label ?? purpose;
      this.toast.show(`${enabled ? 'Đã bật' : 'Đã tắt'} mục đích: ${label}.`, 'ok');
    } catch (e) {
      this.toast.show(toApiError(e).message || 'Không lưu được cấu hình.', 'err');
    } finally {
      this.saving.set(null);
    }
  }
}
