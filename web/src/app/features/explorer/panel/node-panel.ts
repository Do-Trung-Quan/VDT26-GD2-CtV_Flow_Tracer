import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toApiError } from '../../../core/api/api-error';
import { AiStatus } from '../../../core/ai/ai-status';
import { AiApi } from '../../../core/api/ai.api';
import type { ApiIoDto, ExplanationDto, GenerateAiRequest, MockDataDto, NodeRelationDto, ScenarioDto } from '../../../core/api/api-types';
import { ExplanationApi } from '../../../core/api/explanation.api';
import { Icon, type IconName } from '../../../core/ui/icon';
import { ToastService } from '../../../core/ui/toast.service';
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
}

type PanelTab = 'explain' | 'script';
type AiKind = 'purpose' | 'mock' | 'script' | 'naming';

/**
 * Cột phải của màn hình chính: tên thành phần, nút "Xem code" và hai tab — Diễn giải (F1, F2) và Kịch bản (F3). Nội dung AI chỉ
 * được ĐỌC từ bản đã lưu; muốn có thì bấm "Sinh bằng AI" / "Sinh lại" (F4). AI tắt, hết hạn mức hoặc lỗi thì các phần hiện nội dung
 * tự sinh từ mã nguồn kèm banner nêu lý do — không có gì bị chặn.
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
        @if (banner(); as b) {
          <div [class]="b.cls + ' gap'"><app-icon name="alert" [small]="true" />{{ b.text }}</div>
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
              @if (e.kind !== 'UI_APP' && e.kind !== 'API_ENDPOINT') {
                <div class="sec first">
                  <button class="btn sm" type="button" [disabled]="!canName() || anyBusy()" [title]="nameTitle()" (click)="nameElements(true)">
                    <app-icon name="sparkle" [small]="true" />{{ isBusy('naming') ? 'AI đang đặt tên… ' + busySeconds() + ' giây' : 'Sinh lại tên thành phần bằng AI' }}
                  </button>
                  @if (isBusy('naming')) {
                    <div class="top6">
                      <ng-container [ngTemplateOutlet]="skelTpl" [ngTemplateOutletContext]="{ lines: [92, 78, 64], caption: 'AI đang đặt tên các phần tử' }" />
                    </div>
                  }
                  @if (namingResult(); as r) {
                    <div [class]="r.cls + ' top6'">{{ r.text }}</div>
                  }
                </div>
              }
              <div class="pick" aria-hidden="true">
                <div class="skel" style="width: 38%"></div>
                <div class="skel" style="width: 92%"></div>
                <div class="skel" style="width: 74%"></div>
                <div class="skel" style="width: 86%"></div>
              </div>
              <div class="hint pick-hint">Bấm vào một thành phần bên trong để xem diễn giải và kịch bản luồng đầy đủ.</div>
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
          @if (e.ai?.state === 'APPLIED') {
            <span class="pill p-purple plain">✦ AI</span>
          } @else {
            <span class="pill p-blue plain">{{ e.purpose?.source === 'SKELETON' ? SYSTEM_LABEL : e.purpose?.sourceLabel }}</span>
          }
          <ng-container [ngTemplateOutlet]="regenTpl" [ngTemplateOutletContext]="{ info: e.ai, kind: 'purpose' }" />
        </h4>
        @if (isBusy('purpose')) {
          <ng-container [ngTemplateOutlet]="skelTpl" [ngTemplateOutletContext]="{ lines: [96, 92, 95, 58], caption: 'AI đang viết mục đích' }" />
        } @else {
          <p [class.tw]="typing()">{{ typing() ? typed() : e.purpose?.text }}</p>
        }
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
          @if (isBusy('purpose')) {
            <div class="skel top6" style="width: 72%"></div>
          } @else if (a.errors?.length) {
            <div class="hint top6">Lỗi có thể gặp: {{ a.errors!.join('; ') }}.</div>
          }
        } @else {
          <p>Không có.</p>
        }
      </div>

      <div class="sec">
        <h4>Ràng buộc</h4>
        @if (isBusy('purpose')) {
          <ng-container [ngTemplateOutlet]="skelTpl" [ngTemplateOutletContext]="{ lines: [90, 82, 68], caption: 'AI đang viết lại ràng buộc' }" />
        } @else if (api()?.constraints?.length) {
          @if (e.ai?.state !== 'APPLIED') {
            <div class="hint bot4">AI chưa viết lại — hiện ràng buộc rút trực tiếp từ code:</div>
          }
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
            @if (mock()?.ai?.state === 'APPLIED') {
              <span class="pill p-purple plain">✦ AI sinh một lần · đã lưu</span>
            } @else {
              <span class="pill p-gray plain">{{ SYSTEM_LABEL }}</span>
            }
            <ng-container [ngTemplateOutlet]="regenTpl" [ngTemplateOutletContext]="{ info: mock()?.ai, kind: 'mock' }" />
          }
        </h4>
        @if (!api()) {
          <p>Không có.</p>
        } @else if (isBusy('mock')) {
          <ng-container [ngTemplateOutlet]="skelTpl" [ngTemplateOutletContext]="{ lines: [40, 74, 64, 80, 58, 70, 36], caption: 'AI đang sinh dữ liệu mẫu' }" />
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

    <ng-template #skelTpl let-lines="lines" let-caption="caption">
      <div role="status" aria-live="polite">
        @for (w of lines; track $index) {
          <div class="skel" [style.width.%]="w"></div>
        }
        <div class="hint top6">{{ caption }}… {{ busySeconds() }} giây</div>
      </div>
    </ng-template>

    <ng-template #regenTpl let-info="info" let-kind="kind">
      @if (info?.state === 'APPLIED') {
        <button class="chip nav ai-act" type="button" [disabled]="anyBusy() || blocked()" [title]="blocked() ? 'AI hiện chưa dùng được' : ''" (click)="generate(kind, true)">
          <app-icon name="refresh" [small]="true" />{{ isBusy(kind) ? 'Đang sinh… ' + busySeconds() + ' giây' : 'Sinh lại' }}
        </button>
      } @else if (info?.state === 'MISSING' && !blocked()) {
        <button class="chip nav ai-act gen" type="button" [disabled]="anyBusy()" (click)="generate(kind, false)">
          <app-icon name="sparkle" [small]="true" />{{ isBusy(kind) ? 'Đang sinh… ' + busySeconds() + ' giây' : 'Sinh bằng AI' }}
        </button>
      }
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
      } @else if (isBusy('script')) {
        <ng-container [ngTemplateOutlet]="skelTpl" [ngTemplateOutletContext]="{ lines: [34, 86, 80, 88, 76, 84, 70, 82, 60], caption: 'AI đang viết lời kể cho từng bước' }" />
      } @else if (scenarioError()) {
        <div class="errb">{{ scenarioError() }}</div>
      } @else if (scenario(); as s) {
        <div class="row head">
          @if (s.ai?.state === 'APPLIED') {
            <span class="pill p-purple">✦ AI viết lời kể</span>
          } @else {
            <span class="pill p-gray">{{ SYSTEM_LABEL }}</span>
          }
          <span class="sp1"></span>
          <ng-container [ngTemplateOutlet]="regenTpl" [ngTemplateOutletContext]="{ info: s.ai, kind: 'script' }" />
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
                  (click)="clickStep(r.index)" (keydown.enter)="clickStep(r.index)">
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
    .ai-act { margin-left: auto; text-transform: none; letter-spacing: 0; font-weight: 600; }
    .ai-act.gen { background: var(--purple-l); color: var(--purple); }
    .naming { margin-top: 14px; }
    .pick { margin-top: 18px; }
    .pick-hint { margin-top: 10px; text-align: center; }
    .gen-btn { margin-top: 8px; }
    .ai-act:disabled { opacity: 0.6; cursor: progress; }
    .tw::after { content: '▍'; color: var(--purple); animation: bl 1s infinite; }
    @keyframes bl { 50% { opacity: 0; } }
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
  private readonly aiApi = inject(AiApi);
  private readonly toast = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly ai = inject(AiStatus);

  readonly workspaceId = input.required<string>();
  readonly header = input.required<PanelHeader | null>();
  readonly nodeKey = input.required<string | null>();
  readonly trace = input<SequenceTrace | null>(null);
  readonly traceLoading = input(false);
  /** Tăng lên khi dữ liệu có thể đã đổi (chốt / bỏ chốt liên kết, quét xong): tải lại diễn giải. */
  readonly refresh = input(0);
  /** Bước đang tô sáng — dùng chung với diagram (bấm bước ↔ bấm mũi tên). */
  readonly highlight = model<number | null>(null);
  /** Bấm một bước trong kịch bản: trang chứa cuộn diagram tới mũi tên đó. */
  readonly stepClicked = output<number>();
  /** Bấm một chip quan hệ để mở thành phần đó. */
  readonly openNode = output<NodeRelationDto>();
  /** AI vừa đặt tên các phần tử của thành phần chứa: trang chứa tải lại cây Frontend để hiện tên mới. */
  readonly labelsChanged = output<void>();

  /** Nhãn thống nhất cho nội dung do hệ thống tự sinh từ mã nguồn (không phải AI). */
  protected readonly SYSTEM_LABEL = 'Hệ thống tự sinh';
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

  /** Các phần đang chờ AI sinh (mỗi lần một phần, để không bắn nhiều lời gọi cùng lúc). */
  protected readonly busyKinds = signal<readonly AiKind[]>([]);
  protected isBusy(kind: AiKind): boolean {
    return this.busyKinds().includes(kind);
  }
  protected readonly anyBusy = computed(() => this.busyKinds().length > 0);
  protected readonly namingResult = signal<{ cls: string; text: string } | null>(null);
  /** Đặt tên được khi AI dùng được (kể cả khi lần gọi trước gặp quá tải / lỗi — vẫn cho thử lại) và mục đích ELEMENT_NAMING đang bật. */
  protected readonly canName = computed(() => this.ai.usable() && this.ai.purposeEnabled('ELEMENT_NAMING'));
  /** Số giây đã chờ của lần sinh AI đang chạy (hiện trên nút). */
  protected readonly busySeconds = signal(0);
  private busyTimer: ReturnType<typeof setInterval> | undefined;
  /** Không thể gọi AI lúc này: hết hạn mức (429) hoặc chưa cấu hình. Quá tải / lỗi khác thì vẫn cho bấm để thử lại. */
  protected readonly blocked = computed(() => {
    const status = this.ai.settings()?.status;
    return status === 'COOLING' || status === 'NOT_CONFIGURED';
  });
  protected readonly nameTitle = computed(() => {
    const s = this.ai.settings();
    if (this.canName()) {
      return 'AI đặt tên cho các phần tử bên trong, kể cả trong hộp thoại lồng nhau; tên được lưu lại';
    }
    return this.ai.usable() ? 'Mục đích "Đặt tên thành phần giao diện" đang tắt trong Cấu hình AI' : (s?.statusMessage ?? 'AI chưa dùng được');
  });
  /** Hiệu ứng chữ hiện dần của Mục đích vừa được AI viết. */
  protected readonly typing = signal(false);
  protected readonly typed = signal('');
  private typeTimer: ReturnType<typeof setInterval> | undefined;

  /** Banner theo loại lỗi: 429 đạt giới hạn (vàng), 503 quá tải (vàng), lỗi khác / chưa cấu hình (đỏ). Nội dung tự sinh vẫn dùng bình thường. */
  protected readonly banner = computed<{ cls: string; text: string } | null>(() => {
    const s = this.ai.settings();
    switch (s?.status) {
      case 'COOLING':
        return { cls: 'warn', text: `${s.statusMessage ?? 'AI đã đạt giới hạn.'} Đang dùng nội dung tự sinh từ mã nguồn.` };
      case 'OVERLOADED':
        return { cls: 'warn', text: s.statusMessage ?? 'Dịch vụ AI đang quá tải.' };
      case 'ERROR':
      case 'NOT_CONFIGURED':
        return { cls: 'errb', text: s.statusMessage ?? 'AI bị lỗi.' };
      default:
        return null;
    }
  });

  private expToken = 0;
  private mockToken = 0;
  private scenarioToken = 0;
  private scenarioFor: string | null = null;
  private mockFor: string | null = null;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stopTyping());
    // Chọn thành phần khác → đóng popup code, bỏ API đã chọn; tab đang xem được giữ nguyên như bản vẽ
    effect(() => {
      this.nodeKey();
      untracked(() => {
        this.codeOpen.set(false);
        this.apiKey.set(null);
        this.namingResult.set(null);
        this.stopTyping();
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

  /** F4: sinh (hoặc sinh lại) nội dung AI của một phần rồi nạp lại phần đó. AI lỗi thì giữ nội dung cũ và báo lý do. */
  protected async generate(kind: AiKind, force: boolean): Promise<void> {
    if (this.anyBusy()) {
      return;
    }
    await this.run(kind, force);
  }

  private async run(kind: AiKind, force: boolean): Promise<void> {
    const key = this.nodeKey();
    const traceId = this.trace()?.traceId ?? null;
    if (!key || this.isBusy(kind) || (kind === 'script' && !traceId)) {
      return;
    }
    const body: GenerateAiRequest =
      kind === 'purpose'
        ? { purpose: 'PURPOSE_TEXT', targetKey: key, force }
        : kind === 'mock'
          ? { purpose: 'MOCK_DATA', targetKey: key, apiKey: this.api()?.apiKey, force }
          : { purpose: 'SCENARIO_POLISH', targetKey: traceId ?? '', force };
    this.markBusy(kind, true);
    try {
      await this.aiApi.generate(this.workspaceId(), body);
      if (kind === 'purpose') {
        await this.loadExplanation(key);
        this.startTyping(this.exp()?.purpose?.text ?? '');
      } else if (kind === 'mock') {
        this.mockFor = null;
        await this.loadMock(key, this.api()?.apiKey ?? null);
      } else {
        this.scenarioFor = null;
        await this.loadScenario(traceId);
      }
      this.toast.show(force ? 'Đã sinh lại nội dung.' : 'Đã sinh nội dung bằng AI và lưu lại.', 'ok');
    } catch (e) {
      this.toast.show(toApiError(e).message || 'AI không phản hồi — giữ nguyên nội dung hiện có.', 'err');
    } finally {
      this.markBusy(kind, false);
      void this.ai.refresh();
    }
  }

  /** Đánh dấu một phần đang / hết chờ AI; đồng hồ đếm giây chạy khi có ít nhất một phần đang chờ. */
  private markBusy(kind: AiKind, on: boolean): void {
    const wasIdle = this.busyKinds().length === 0;
    this.busyKinds.update((list) => (on ? [...list.filter((k) => k !== kind), kind] : list.filter((k) => k !== kind)));
    if (on && wasIdle) {
      this.startBusyTimer();
    } else if (!on && this.busyKinds().length === 0) {
      this.stopBusyTimer();
    }
  }

  private startBusyTimer(): void {
    const t0 = Date.now();
    this.busySeconds.set(0);
    clearInterval(this.busyTimer);
    this.busyTimer = setInterval(() => this.busySeconds.set(Math.round((Date.now() - t0) / 1000)), 500);
  }

  private stopBusyTimer(): void {
    clearInterval(this.busyTimer);
    this.busyTimer = undefined;
  }

  /** F4 với ELEMENT_NAMING: đặt tên cho mọi phần tử đáng đặt tên trong thành phần chứa đang xem. */
  protected async nameElements(force: boolean): Promise<void> {
    const key = this.nodeKey();
    if (!key || this.anyBusy() || !this.canName()) {
      return;
    }
    this.markBusy('naming', true);
    this.namingResult.set(null);
    try {
      const r = await this.aiApi.generate(this.workspaceId(), { purpose: 'ELEMENT_NAMING', targetKey: key, force });
      const text =
        r.total === 0
          ? (r.message ?? 'Không có phần tử nào cần đặt tên lại.')
          : `Đã đặt tên ${r.named ?? 0}/${r.total ?? 0} phần tử.${r.message ? ' ' + r.message : ''}`;
      this.namingResult.set({ cls: r.total === 0 || (r.named ?? 0) === (r.total ?? 0) ? 'okb' : 'warn', text });
      if ((r.named ?? 0) > 0) {
        this.labelsChanged.emit();
      }
    } catch (e) {
      this.namingResult.set({ cls: 'errb', text: toApiError(e).message || 'AI không phản hồi — giữ nguyên tên hiện có.' });
    } finally {
      this.markBusy('naming', false);
      void this.ai.refresh();
    }
  }

  private startTyping(full: string): void {
    this.stopTyping();
    if (!full) {
      return;
    }
    let i = 0;
    this.typed.set('');
    this.typing.set(true);
    this.typeTimer = setInterval(() => {
      i += 3;
      this.typed.set(full.slice(0, i));
      if (i >= full.length) {
        this.stopTyping();
      }
    }, 16);
  }

  private stopTyping(): void {
    clearInterval(this.typeTimer);
    this.typeTimer = undefined;
    this.typing.set(false);
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
      void this.ai.refresh(); // hết thời gian nghỉ / đổi cấu hình thì banner và đèn AI cập nhật theo
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
