import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toApiError } from '../../../core/api/api-error';
import type { ApiIoDto, ExplanationDto, MockDataDto, NodeRelationDto, ScenarioDto } from '../../../core/api/api-types';
import { ExplanationApi } from '../../../core/api/explanation.api';
import { Icon, type IconName } from '../../../core/ui/icon';
import type { SequenceTrace } from '../sequence/sequence-model';
import { CodeDialog } from './code-dialog';
import { apiLabel, inputValue, isOpenable, outputValue, relationName, scenarioRows } from './explanation-model';
import { JsonBlock } from './json-block';

/** Nhãn nhỏ ở đầu cột phải (loại thành phần, "Gọi máy chủ"…). */
export interface PanelPill {
  text: string;
  cls: string;
}

/** Phần đầu cột phải do trang chứa dựng: tên, loại và (với API) phương thức + đường dẫn. */
export interface PanelHeader {
  title: string;
  /** Tên kỹ thuật (phần tử không có nhãn, hoặc API không có mô tả) hiện bằng chữ đơn cách. */
  mono: boolean;
  method: string | null;
  path: string | null;
  pills: PanelPill[];
  /** Thành phần chứa: không có tab và không có nút Xem code. */
  container: boolean;
  /** Ghi chú dưới tiêu đề (vd phần tử không có nhãn trong mã nguồn). */
  note: string | null;
}

type PanelTab = 'explain' | 'script';

/**
 * Cột phải của màn hình chính: tên thành phần, nút "Xem code" và hai tab — Diễn giải (F1, F2) và Kịch bản (F3). Chưa có AI
 * nên mục đích dùng nguồn tất định và nút "Sinh lại" ở trạng thái tắt (Giai đoạn 7 mới bật).
 */
@Component({
  selector: 'app-node-panel',
  imports: [Icon, JsonBlock, CodeDialog, NgTemplateOutlet],
  template: `
    @if (header(); as h) {
      <div class="rh">
        <div class="row top">
          <div class="sp1">
            <h2 [class.mono]="h.mono">{{ h.title }}</h2>
            <div class="row meta">
              @if (h.method) {
                <span [class]="'mt ' + h.method">{{ h.method }}</span>
              }
              @if (h.path) {
                <span class="mono muted path">{{ h.path }}</span>
              }
              @for (p of h.pills; track p.text) {
                <span [class]="'pill ' + p.cls">{{ p.text }}</span>
              }
            </div>
          </div>
          @if (!h.container) {
            <button class="btn sm" type="button" (click)="codeOpen.set(true)"><app-icon name="code" [small]="true" />Xem code</button>
          }
        </div>
        @if (!h.container) {
          <div class="tabs" role="tablist">
            <button class="tab" type="button" role="tab" [class.on]="tab() === 'explain'" (click)="tab.set('explain')">Diễn giải</button>
            <button class="tab" type="button" role="tab" [class.on]="tab() === 'script'" (click)="tab.set('script')">Kịch bản</button>
          </div>
        }
      </div>

      <div class="rb">
        @if (h.note) {
          <div class="hint gap">{{ h.note }}</div>
        }
        @for (n of notes(); track n) {
          <div class="warn gap">{{ n }}</div>
        }

        @if (h.container || tab() === 'explain') {
          @if (expLoading() && !exp()) {
            @for (w of skeleton; track $index) {
              <div class="skel" [style.width.%]="w"></div>
            }
          } @else if (expError()) {
            <div class="errb">{{ expError() }}</div>
          } @else if (exp(); as e) {
            @if (e.container) {
              <div class="sec first">
                <h4>Mô tả</h4>
                <p>{{ e.purpose?.text }}</p>
              </div>
              <div class="hint">Đây là thành phần chứa. Chọn một nút hoặc ô nhập bên trong để xem diễn giải đầy đủ và kịch bản.</div>
            } @else {
              <ng-container [ngTemplateOutlet]="explainTpl" [ngTemplateOutletContext]="{ $implicit: e }" />
            }
          }
        } @else {
          <ng-container [ngTemplateOutlet]="scriptTpl" />
        }
      </div>

      @if (codeOpen() && nodeKey()) {
        <app-code-dialog [workspaceId]="workspaceId()" [nodeKey]="nodeKey()!" [title]="h.title" (closed)="codeOpen.set(false)" />
      }
    }

    <ng-template #explainTpl let-e>
      <div class="sec first">
        <h4>
          Mục đích
          <span class="pill p-blue plain">{{ e.purpose?.sourceLabel }}</span>
        </h4>
        <p>{{ e.purpose?.text }}</p>
        <div class="hint top6">AI chưa được bật và chưa có bản lưu — đang hiện mô tả ngắn từ {{ e.purpose?.sourceLabel }}.</div>
      </div>

      @if ((e.apis?.length ?? 0) > 1) {
        <div class="sec">
          <h4>API được gọi ({{ e.apis.length }})</h4>
          <div class="chips">
            @for (a of e.apis; track a.apiKey) {
              <button class="chip nav" type="button" [class.on]="a.apiKey === api()?.apiKey" (click)="apiKey.set(a.apiKey ?? null)">
                <app-icon name="bolt" [small]="true" />{{ label(a) }}
              </button>
            }
          </div>
        </div>
      }

      <div class="sec">
        <h4>Đầu vào</h4>
        @if (api(); as a) {
          @if (e.kind !== 'API_ENDPOINT') {
            <div class="hint bot6">Theo API <span class="mono">{{ label(a) }}</span> mà thành phần này gọi.</div>
          }
          @if (inValue(); as v) {
            <app-json-block [value]="v" />
          } @else {
            <p>Không có — API này không nhận tham số hay dữ liệu gửi lên.</p>
          }
        } @else if (e.unresolvedCalls?.length) {
          <p>Chưa xác định được API đích nên chưa có đầu vào. Hãy chốt tay liên kết trên diagram.</p>
          <div class="chips top6">
            @for (u of e.unresolvedCalls; track u) {
              <span class="chip dis full"><app-icon name="alert" [small]="true" /><span class="mono">{{ u }}</span></span>
            }
          </div>
        } @else {
          <p>Không có — thao tác chỉ trên giao diện, không gửi gì lên máy chủ.</p>
        }
      </div>

      <div class="sec">
        <h4>Đầu ra</h4>
        @if (api(); as a) {
          @if (outValue(); as v) {
            <app-json-block [value]="v" />
          } @else {
            <p>Không có — API không trả nội dung.</p>
          }
          @if (a.errors?.length) {
            <div class="hint top6">Lỗi có thể gặp: {{ a.errors!.join('; ') }}.</div>
          }
        } @else {
          <p>Không có.</p>
        }
      </div>

      <div class="sec">
        <h4>Ràng buộc</h4>
        @if (api()?.constraints?.length) {
          <div class="hint bot4">AI chưa bật — hiện ràng buộc rút trực tiếp từ code:</div>
          <ul>
            @for (c of api()!.constraints!; track $index) {
              <li>{{ c }}</li>
            }
          </ul>
        } @else {
          <p>Không có.</p>
        }
      </div>

      <div class="sec">
        <h4>
          Dữ liệu mẫu
          @if (api()) {
            <span class="pill p-gray plain">Bộ sinh dự phòng</span>
          }
        </h4>
        @if (!api()) {
          <p>Không có.</p>
        } @else if (mockLoading()) {
          <div class="skel"></div>
          <div class="skel" style="width: 70%"></div>
        } @else if (mockError()) {
          <div class="errb">{{ mockError() }}</div>
        } @else if (mock(); as m) {
          <app-json-block [value]="{ request: m.request, response: m.response }" />
          @for (p of m.problems ?? []; track p) {
            <div class="warn top6">{{ p }}</div>
          }
        }
      </div>

      <div class="sec">
        <h4>Được gọi từ</h4>
        <div class="chips">
          @if (e.kind === 'API_ENDPOINT') {
            @for (r of e.calledFrom ?? []; track r.nodeKey) {
              <button class="chip nav" type="button" (click)="openNode.emit(r)">
                <app-icon name="cursor" [small]="true" />{{ name(r) }}@if (r.context) {<span class="muted ctx">· {{ r.context }}</span>}
              </button>
            } @empty {
              <span class="hint">Chưa thấy thành phần nào gọi tới API này.</span>
            }
            @if ((e.calledFromTotal ?? 0) > (e.calledFrom?.length ?? 0)) {
              <span class="hint">và {{ e.calledFromTotal - e.calledFrom.length }} thành phần khác.</span>
            }
          } @else {
            @for (r of e.trail ?? []; track r.nodeKey) {
              <button class="chip nav" type="button" (click)="openNode.emit(r)">{{ name(r) }}</button>
              <span class="muted sepc"><app-icon name="chev" [small]="true" /></span>
            }
            <span class="chip dis cur">{{ e.displayName || e.name }}</span>
          }
        </div>
      </div>

      <div class="sec">
        <h4>Gọi tới</h4>
        <div class="chips">
          @for (r of e.callsTo ?? []; track r.nodeKey) {
            @if (openable(r)) {
              <button class="chip nav" type="button" (click)="openNode.emit(r)"><app-icon [name]="icon(r)" [small]="true" />{{ text(r) }}</button>
            } @else {
              <span class="chip dis full"><app-icon [name]="icon(r)" [small]="true" />{{ text(r) }}</span>
            }
          } @empty {
            <span class="hint">{{
              e.kind === 'API_ENDPOINT'
                ? 'Không gọi tới bảng dữ liệu hay hệ thống ngoài nào.'
                : 'Không gọi tới thành phần nào khác (chỉ thay đổi giao diện).'
            }}</span>
          }
        </div>
      </div>
    </ng-template>

    <ng-template #scriptTpl>
      @if (traceLoading()) {
        @for (w of skeleton; track $index) {
          <div class="skel" [style.width.%]="w"></div>
        }
      } @else if (!trace()) {
        <div class="empty slim">
          <h3>Chưa có kịch bản</h3>
          <span>Kịch bản được sinh từ luồng trên diagram; thành phần này chưa dò được luồng.</span>
        </div>
      } @else if (scenarioLoading()) {
        @for (w of skeleton; track $index) {
          <div class="skel" [style.width.%]="w"></div>
        }
      } @else if (scenarioError()) {
        <div class="errb">{{ scenarioError() }}</div>
      } @else if (scenario(); as s) {
        <div class="row head">
          <span class="pill p-gray">Khung câu tự sinh (AI chưa bật)</span>
          <span class="sp1"></span>
          <button class="chip dis" type="button" disabled title="AI chưa được bật — không thể sinh lại"><app-icon name="refresh" [small]="true" />Sinh lại</button>
        </div>
        <div class="sec">
          <h4>Các actor tham gia</h4>
          <ul class="actors">
            @for (a of s.actors ?? []; track a.id) {
              <li><b>{{ a.name }}:</b> {{ a.description }}</li>
            }
          </ul>
        </div>
        <div class="sec">
          <h4>Scenario:</h4>
          <ul class="scr">
            @for (r of rows(); track $index) {
              @if (r.index === null) {
                <li class="cond" [style.padding-left.px]="r.depth * 14">{{ r.text }}</li>
              } @else {
                <li class="st" tabindex="0" role="button" [attr.data-n]="r.index" [class.sub]="r.depth > 0" [class.hl]="highlight() === r.index"
                  [class.unres]="r.unresolved" [style.margin-left.px]="r.depth > 0 ? 6 + (r.depth - 1) * 14 : null"
                  (click)="clickStep(r.index)" (keydown.enter)="clickStep(r.index)" (mouseenter)="preview.emit(r.index)" (mouseleave)="preview.emit(null)">
                  <b>Bước {{ r.index }}:</b> {{ r.text }}
                </li>
              }
            }
          </ul>
        </div>
      }
    </ng-template>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 0; flex: 1; }
    .rh { padding: 14px 16px 0; }
    .rh h2 { margin: 0 0 6px; font-size: 16px; line-height: 1.3; overflow-wrap: anywhere; }
    .top { align-items: flex-start; }
    .meta { gap: 6px; flex-wrap: wrap; }
    .path { overflow-wrap: anywhere; }
    .rh .tabs { margin: 12px 0 0; }
    .rb { flex: 1; overflow: auto; padding: 14px 16px 28px; overflow-x: hidden; }
    .sec { margin-bottom: 18px; }
    .sec h4 {
      white-space: nowrap; flex-wrap: wrap; row-gap: 4px; margin: 0 0 6px; font-size: 12px; letter-spacing: 0.4px; text-transform: uppercase;
      color: var(--t3); display: flex; align-items: center; gap: 8px;
    }
    .sec p { margin: 0; overflow-wrap: anywhere; }
    .sec ul { margin: 0; padding-left: 18px; }
    .sec li { margin: 3px 0; }
    .pill.plain { text-transform: none; letter-spacing: 0; }
    .gap { margin-bottom: 12px; }
    .top6 { margin-top: 6px; }
    .bot6 { margin-bottom: 6px; }
    .bot4 { margin-bottom: 4px; }
    .chips { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
    .chip { border: 0; font: inherit; font-size: 12.5px; height: auto; min-height: 26px; padding: 3px 10px; text-align: left; max-width: 100%; overflow-wrap: anywhere; }
    .chip.nav { cursor: pointer; }
    .chip.nav:hover { background: #e5e7eb; }
    .chip.nav.on { background: var(--brand-l); color: var(--brand); box-shadow: inset 0 0 0 1px #fecaca; }
    .chip.dis { cursor: default; }
    button.chip.dis:disabled { opacity: 0.5; }
    .chip.full, .chip.cur { opacity: 1; }
    .chip.cur { font-weight: 600; }
    .ctx { font-size: 11px; }
    .sepc { display: grid; }
    .empty.slim { padding: 36px 10px; }
    .head { margin-bottom: 12px; flex-wrap: wrap; }
    .actors { margin: 0; padding: 0; list-style: none; }
    .actors li { margin: 2px 0; padding: 2px 0; overflow-wrap: anywhere; }
    .sec ul.scr, .sec ul.actors { padding-left: 0; list-style: none; }
    .scr { list-style: none; margin: 0; padding: 0; }
    .scr li.st { display: block; padding: 6px 8px; border-radius: 8px; cursor: pointer; margin: 0 -8px 2px; line-height: 1.5; overflow-wrap: anywhere; outline: 0; }
    .scr li.st:hover, .scr li.st:focus-visible { background: #f9fafb; }
    .scr li.st.hl { background: var(--brand-l); box-shadow: inset 3px 0 0 var(--brand); }
    .scr li.cond { font-style: italic; font-weight: 600; color: var(--t2); padding: 8px 0 2px; }
    .scr li.unres b { color: #dc2626; }
    .mt { font: 700 11px var(--mono); padding: 2px 6px; border-radius: 4px; background: #f3f4f6; }
    .mt.GET { background: var(--green-l); color: var(--green); }
    .mt.POST { background: var(--blue-l); color: var(--blue); }
    .mt.PUT, .mt.PATCH { background: var(--amber-l); color: var(--amber); }
    .mt.DELETE { background: var(--brand-l); color: var(--brand); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NodePanel {
  private readonly explanationApi = inject(ExplanationApi);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly workspaceId = input.required<string>();
  readonly header = input.required<PanelHeader | null>();
  readonly nodeKey = input.required<string | null>();
  /** Cảnh báo của luồng / API (hiện trên đầu cả hai tab). */
  readonly notes = input<string[]>([]);
  readonly trace = input<SequenceTrace | null>(null);
  readonly traceLoading = input(false);
  /** Tăng lên khi dữ liệu có thể đã đổi (chốt / bỏ chốt liên kết, quét xong): tải lại diễn giải. */
  readonly refresh = input(0);
  /** Bước đang tô sáng — dùng chung với diagram (bấm bước ↔ bấm mũi tên). */
  readonly highlight = model<number | null>(null);
  /** Rê chuột qua một bước (null khi rời): diagram tô sáng tạm mũi tên. */
  readonly preview = output<number | null>();
  /** Bấm một bước trong kịch bản: trang chứa cuộn diagram tới mũi tên đó. */
  readonly stepClicked = output<number>();
  /** Bấm một chip quan hệ để mở thành phần đó. */
  readonly openNode = output<NodeRelationDto>();

  protected readonly skeleton = [95, 81, 67, 53, 95, 81, 67, 53];
  protected readonly tab = signal<PanelTab>('explain');
  protected readonly codeOpen = signal(false);

  protected readonly exp = signal<ExplanationDto | null>(null);
  protected readonly expLoading = signal(false);
  protected readonly expError = signal<string | null>(null);
  protected readonly apiKey = signal<string | null>(null);

  protected readonly mock = signal<MockDataDto | null>(null);
  protected readonly mockLoading = signal(false);
  protected readonly mockError = signal<string | null>(null);

  protected readonly scenario = signal<ScenarioDto | null>(null);
  protected readonly scenarioLoading = signal(false);
  protected readonly scenarioError = signal<string | null>(null);

  protected readonly api = computed<ApiIoDto | undefined>(() => {
    const apis = this.exp()?.apis ?? [];
    return apis.find((a) => a.apiKey === this.apiKey()) ?? apis[0];
  });
  protected readonly inValue = computed(() => inputValue(this.api()));
  protected readonly outValue = computed(() => outputValue(this.api()));
  protected readonly rows = computed(() => scenarioRows(this.scenario()?.items));

  private expToken = 0;
  private mockToken = 0;
  private scenarioToken = 0;
  private scenarioFor: string | null = null;
  private mockFor: string | null = null;

  constructor() {
    // Chọn thành phần khác → đóng popup code, bỏ API đã chọn; tab đang xem được giữ nguyên như bản vẽ
    effect(() => {
      this.nodeKey();
      untracked(() => {
        this.codeOpen.set(false);
        this.apiKey.set(null);
      });
    });
    // Thành phần khác (hoặc dữ liệu đổi) → nạp lại diễn giải
    effect(() => {
      const key = this.nodeKey();
      this.refresh();
      untracked(() => void this.loadExplanation(key));
    });
    // Dữ liệu mẫu của API đang chọn (F2): chỉ nạp khi tab Diễn giải đang hiện
    effect(() => {
      const key = this.nodeKey();
      const a = this.api();
      const visible = this.tab() === 'explain' && !this.exp()?.container;
      untracked(() => void this.loadMock(visible ? key : null, a?.apiKey ?? null));
    });
    // Kịch bản của luồng đang xem (F3): chỉ nạp khi mở tab Kịch bản
    effect(() => {
      const traceId = this.trace()?.traceId ?? null;
      const visible = this.tab() === 'script';
      untracked(() => void this.loadScenario(visible ? traceId : null));
    });
    // Bấm mũi tên trên diagram → cuộn danh sách kịch bản tới bước đó
    effect(() => {
      const n = this.highlight();
      if (n !== null && this.tab() === 'script') {
        queueMicrotask(() =>
          this.host.nativeElement.querySelector(`.scr li.st[data-n="${n}"]`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
        );
      }
    });
  }

  protected readonly label = apiLabel;
  protected readonly name = relationName;

  protected openable(r: NodeRelationDto): boolean {
    return isOpenable(r.kind);
  }

  protected icon(r: NodeRelationDto): IconName {
    switch (r.kind) {
      case 'DB_TABLE':
        return 'db';
      case 'API_ENDPOINT':
        return 'bolt';
      case 'UI_DIALOG':
      case 'UI_SCREEN':
        return 'window';
      default:
        return 'server';
    }
  }

  protected text(r: NodeRelationDto): string {
    if (r.kind === 'API_ENDPOINT') {
      return r.name ?? ''; // "POST /api/v1/categories" như bản vẽ, không dùng tên thân thiện
    }
    return r.kind === 'DB_TABLE' && !r.displayName ? `Bảng ${r.name}` : relationName(r);
  }

  protected clickStep(n: number): void {
    this.highlight.set(this.highlight() === n ? null : n);
    this.stepClicked.emit(n);
  }

  private async loadExplanation(key: string | null): Promise<void> {
    const token = ++this.expToken;
    this.expError.set(null);
    if (!key) {
      this.exp.set(null);
      this.expLoading.set(false);
      return;
    }
    if (this.exp()?.nodeKey !== key) {
      this.exp.set(null); // thành phần khác: không để nội dung của thành phần trước hiện trong lúc chờ
    }
    this.expLoading.set(true);
    try {
      const dto = await this.explanationApi.explanation(this.workspaceId(), key);
      if (token === this.expToken) {
        this.exp.set(dto);
        // tải lại cùng thành phần (vd sau khi chốt tay) thì giữ API đang chọn nếu còn
        if (!dto.apis?.some((a) => a.apiKey === this.apiKey())) {
          this.apiKey.set(null);
        }
      }
    } catch (e) {
      if (token === this.expToken) {
        this.exp.set(null);
        this.expError.set(toApiError(e).message);
      }
    } finally {
      if (token === this.expToken) {
        this.expLoading.set(false);
      }
    }
  }

  private async loadMock(key: string | null, apiKey: string | null): Promise<void> {
    const token = ++this.mockToken;
    if (!key || !apiKey) {
      this.mockFor = null;
      this.mock.set(null);
      this.mockError.set(null);
      this.mockLoading.set(false);
      return;
    }
    const id = `${key}|${apiKey}`;
    if (id === this.mockFor && this.mock()) {
      return;
    }
    this.mockLoading.set(true);
    this.mockError.set(null);
    try {
      const dto = await this.explanationApi.mockData(this.workspaceId(), key, apiKey);
      if (token === this.mockToken) {
        this.mock.set(dto);
        this.mockFor = id;
      }
    } catch (e) {
      if (token === this.mockToken) {
        this.mock.set(null);
        this.mockFor = null;
        this.mockError.set(toApiError(e).message);
      }
    } finally {
      if (token === this.mockToken) {
        this.mockLoading.set(false);
      }
    }
  }

  private async loadScenario(traceId: string | null): Promise<void> {
    const token = ++this.scenarioToken;
    if (!traceId) {
      this.scenarioLoading.set(false);
      return;
    }
    if (traceId === this.scenarioFor && this.scenario()) {
      return;
    }
    this.scenarioLoading.set(true);
    this.scenarioError.set(null);
    try {
      const dto = await this.explanationApi.scenario(this.workspaceId(), traceId);
      if (token === this.scenarioToken) {
        this.scenario.set(dto);
        this.scenarioFor = traceId;
      }
    } catch (e) {
      if (token === this.scenarioToken) {
        this.scenario.set(null);
        this.scenarioFor = null;
        this.scenarioError.set(toApiError(e).message);
      }
    } finally {
      if (token === this.scenarioToken) {
        this.scenarioLoading.set(false);
      }
    }
  }
}
