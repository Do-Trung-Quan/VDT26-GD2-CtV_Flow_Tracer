import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { BackendTreeDto, EndpointNodeDto, FrontendTreeDto, NodeRelationDto, SearchHitDto, WorkspaceDto } from '../../core/api/api-types';
import { TraceApi } from '../../core/api/trace.api';
import { GraphApi } from '../../core/api/graph.api';
import { WorkspaceApi } from '../../core/api/workspace.api';
import { ExplorerNavigation, type NavigationRequest } from '../../core/explorer-navigation';
import { Icon } from '../../core/ui/icon';
import { ToastService } from '../../core/ui/toast.service';
import { isRunning, statusLabel } from '../scan/scan-format';
import { ScanTracker } from '../scan/scan-tracker';
import { BackendTree } from './backend-tree';
import { FrontendTree, type RevealRequest, type UiSelection } from './frontend-tree';
import { findTrail, fullLabel, isContainer, nodeLabel, typeLabel } from './frontend-tree-model';
import { NodePanel, type PanelHeader } from './panel/node-panel';
import { SequenceCanvas } from './sequence/sequence-canvas';
import type { SequenceTrace } from './sequence/sequence-model';
import { normalizeTrace } from './sequence/sequence-model';

const MIN_LEFT = 220;
const MAX_LEFT = 560;
const DEFAULT_LEFT = 300;
const LEFT_WIDTH_KEY = 'ft.explorer.leftWidth';

/**
 * Chi tiết Workspace (khung 3 cột): cột trái có hai tab — cây API Backend (C1) và cây thành phần Frontend (C2); canvas giữa (sequence
 * diagram của API hoặc phần tử đang chọn, D1) với cụm nút nổi trạng thái quét / Quét lại; cột phải thông tin thành phần đang
 * chọn (tab Diễn giải ở Giai đoạn 6). Bảng tìm nhanh Ctrl+K (ở khung ứng dụng) mở tới đúng thành phần qua {@link ExplorerNavigation}.
 */
@Component({
  selector: 'app-explorer',
  imports: [BackendTree, FrontendTree, Icon, NodePanel, SequenceCanvas],
  template: `
    <div class="explorer" [style.--lw.px]="collapsed() ? 0 : leftWidth()">
      <aside class="left" [class.hidden]="collapsed()">
        <div class="tabs">
          <button class="tab" type="button" [class.on]="sideTab() === 'be'" (click)="openTab('be')">Backend</button>
          <button class="tab" type="button" [class.on]="sideTab() === 'fe'" (click)="openTab('fe')">Frontend</button>
          <span class="sp1"></span>
          <button class="ibtn" type="button" aria-label="Thu gọn cột trái" (click)="collapsed.set(true)">‹</button>
        </div>
        @if (sideTab() === 'be') {
          @if (treeLoading() && !tree()) {
            @for (i of [1, 2, 3, 4, 5, 6]; track i) {
              <div class="skel pad"></div>
            }
          } @else if (treeError()) {
            <div class="errb pad">{{ treeError() }}</div>
          } @else {
            <app-backend-tree [tree]="tree()" [selectedKey]="selected()?.nodeKey ?? null" [emptyText]="emptyText()"
              [reveal]="beReveal()" (selected)="selectEndpoint($event)" />
          }
        } @else {
          @if (feLoading() && !feTree()) {
            @for (i of [1, 2, 3, 4, 5, 6]; track i) {
              <div class="skel pad"></div>
            }
          } @else if (feError()) {
            <div class="errb pad">{{ feError() }}</div>
          } @else {
            <app-frontend-tree [tree]="feTree()" [selectedKey]="selectedUi()?.node?.nodeKey ?? null" [emptyText]="feEmptyText()"
              [reveal]="feReveal()" (selected)="selectUi($event)" />
          }
        }
        <div class="rz" role="separator" aria-label="Kéo để đổi độ rộng cột" (pointerdown)="startResize($event)"></div>
      </aside>

      <section class="canvas">
        <div class="fab tl">
          @if (collapsed()) {
            <button class="ibtn" type="button" aria-label="Mở cột trái" (click)="collapsed.set(false)">›</button>
            <span class="vsep"></span>
          }
          <span class="stat">
            <span [class]="'sdot ' + dotClass()"></span>
            {{ scanText() }}
          </span>
          <button class="btn sm" type="button" [disabled]="scanning() || !ws()" (click)="rescan()">
            <app-icon name="upload" [small]="true" />{{ ws()?.lastScan ? 'Quét lại' : 'Quét' }}
          </button>
        </div>
        <app-sequence-canvas [workspaceId]="workspaceId()" [trace]="trace()" [loading]="traceLoading()" [error]="traceError()"
          [emptyTitle]="emptyTitle()" [emptyText]="emptyHint()" [(highlight)]="highlight" (linkChanged)="reloadTrace()" />
      </section>

      <aside class="right">
        @if (panelHeader(); as h) {
          <app-node-panel [workspaceId]="workspaceId()" [header]="h" [nodeKey]="panelKey()" [trace]="trace()"
            [traceLoading]="traceLoading()" [refresh]="refreshSeq()" [(highlight)]="highlight"
            (stepClicked)="canvas()?.revealStep($event)" (openNode)="openRelation($event)" (labelsChanged)="reloadFrontend()" />
        } @else {
          <div class="empty full">
            <div class="ic"><app-icon name="info" /></div>
            <h3>Chưa chọn thành phần</h3>
            <span>Chọn một API hoặc phần tử ở cột trái để xem diễn giải và kịch bản.</span>
          </div>
        }
      </aside>
    </div>
  `,
  styles: `
    :host { display: block; height: 100%; }
    .explorer { display: grid; grid-template-columns: var(--lw) minmax(0, 1fr) var(--rw, 390px); height: 100%; }
    @media (max-width: 1100px) { .explorer { --rw: 330px; } }
    .left { background: #fff; box-shadow: inset -1px 0 0 var(--bd); display: flex; flex-direction: column; min-height: 0; position: relative; }
    .left.hidden { visibility: hidden; }
    .left .tabs { padding: 0 8px 0 12px; margin: 0; align-items: center; }
    .pad { margin: 10px 12px; }
    .rz { position: absolute; top: 0; bottom: 0; right: -3px; width: 6px; cursor: col-resize; z-index: 6; }
    .rz:hover { background: rgba(185, 0, 14, 0.25); }
    .canvas { position: relative; background: #fcfcfd radial-gradient(#e5e7eb 1px, transparent 1px) 0 0 / 18px 18px; min-width: 0; }
    .fab { position: absolute; top: 12px; left: 12px; z-index: 8; background: #fff; border-radius: 12px; box-shadow: var(--sh-md), var(--ring); padding: 5px; display: flex; align-items: center; gap: 4px; }
    .vsep { width: 1px; height: 22px; background: var(--bd); margin: 0 3px; }
    .stat { display: flex; align-items: center; gap: 8px; padding: 0 8px; font-size: 13px; color: var(--t2); }
    .sdot { width: 9px; height: 9px; border-radius: 50%; background: #9ca3af; }
    .sdot.green { background: #16a34a; }
    .sdot.amber { background: #f59e0b; }
    .sdot.red { background: var(--brand); }
    .sdot.blue { background: var(--blue); }
    .center { height: 100%; }
    .right { background: #fff; box-shadow: inset 1px 0 0 var(--bd); display: flex; flex-direction: column; min-height: 0; min-width: 0; }
    .empty.full { height: 100%; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExplorerPage {
  private readonly workspaceApi = inject(WorkspaceApi);
  private readonly graphApi = inject(GraphApi);
  private readonly traceApi = inject(TraceApi);
  private readonly tracker = inject(ScanTracker);
  private readonly navigation = inject(ExplorerNavigation);
  private readonly toast = inject(ToastService);

  /** Tham số route :workspaceId. */
  readonly workspaceId = input.required<string>();

  protected readonly ws = signal<WorkspaceDto | null>(null);
  protected readonly tree = signal<BackendTreeDto | null>(null);
  protected readonly treeLoading = signal(false);
  protected readonly treeError = signal<string | null>(null);
  protected readonly selected = signal<EndpointNodeDto | null>(null);
  protected readonly sideTab = signal<'be' | 'fe'>('be');
  protected readonly feTree = signal<FrontendTreeDto | null>(null);
  protected readonly feLoading = signal(false);
  protected readonly feError = signal<string | null>(null);
  protected readonly selectedUi = signal<UiSelection | null>(null);
  protected readonly beReveal = signal<RevealRequest | null>(null);
  protected readonly feReveal = signal<RevealRequest | null>(null);
  /** Thông báo (không phải lỗi) thay cho diagram: phần tử gọi API chưa dò được khi chưa có phần nối FE–BE. */
  protected readonly traceInfo = signal<{ title: string; text: string } | null>(null);
  private revealSeq = 0;
  /** Bước đang tô sáng, dùng chung giữa diagram và tab Kịch bản; chỉ đổi khi bấm chọn một bước / một mũi tên (không theo rê chuột). */
  protected readonly highlight = signal<number | null>(null);
  protected readonly refreshSeq = signal(0);
  protected readonly canvas = viewChild(SequenceCanvas);
  protected readonly trace = signal<SequenceTrace | null>(null);
  protected readonly traceLoading = signal(false);
  protected readonly traceError = signal<string | null>(null);
  private traceToken = 0;
  protected readonly collapsed = signal(false);
  protected readonly leftWidth = signal(readLeftWidth());

  /** Trạng thái lần quét hiện tại: ưu tiên lần quét đang theo dõi trên giao diện, rồi tới lần quét gần nhất đã lưu. */
  private readonly status = computed(() => {
    const tracked = this.tracker.current();
    if (tracked && tracked.workspaceId === this.workspaceId()) {
      return { status: tracked.run.status, at: tracked.run.finishedAt ?? tracked.run.startedAt };
    }
    const last = this.ws()?.lastScan;
    return last ? { status: last.status, at: last.finishedAt ?? last.startedAt } : null;
  });

  protected readonly scanning = computed(() => isRunning(this.status()?.status));

  protected readonly dotClass = computed(() => {
    switch (this.status()?.status) {
      case 'SUCCEEDED':
        return 'green';
      case 'PARTIAL':
      case 'CANCELLED':
        return 'amber';
      case 'FAILED':
        return 'red';
      case 'PENDING':
      case 'RUNNING':
        return 'blue';
      default:
        return '';
    }
  });

  protected readonly scanText = computed(() => {
    const s = this.status();
    if (!s) {
      return 'Chưa quét';
    }
    const when = s.at ? ` · ${this.datePipe.transform(s.at, 'HH:mm dd/MM')}` : '';
    return statusLabel(s.status) + (isRunning(s.status) ? '…' : when);
  });

  protected readonly emptyTitle = computed(() => {
    const info = this.traceInfo();
    if (info) {
      return info.title;
    }
    const ui = this.selectedUi();
    if (ui) {
      return `${typeLabel(ui.node)} là thành phần chứa`;
    }
    if (this.sideTab() === 'fe') {
      return this.feTree()?.elementCount ? 'Chọn một nút hoặc ô nhập ở cột trái' : 'Chưa có dữ liệu';
    }
    return this.tree()?.endpointCount ? 'Chọn một API ở cột trái' : 'Chưa có dữ liệu';
  });
  protected readonly emptyHint = computed(() => {
    const info = this.traceInfo();
    if (info) {
      return info.text;
    }
    if (this.selectedUi()) {
      return 'Chọn một nút hoặc ô nhập bên trong để xem luồng thao tác.';
    }
    if (this.sideTab() === 'fe') {
      return this.feTree()?.elementCount
        ? 'Luồng thao tác của phần tử sẽ hiện ở đây.'
        : 'Bấm Quét để phân tích mã nguồn của Workspace.';
    }
    return this.tree()?.endpointCount
      ? 'Sequence diagram của luồng xử lý sẽ hiện ở đây.'
      : 'Bấm Quét để phân tích mã nguồn của Workspace.';
  });
  protected readonly feEmptyText = computed(() =>
    this.ws()?.lastScan ? 'Lần quét không tìm thấy thành phần Frontend nào.' : 'Workspace chưa được quét.'
  );

  protected readonly emptyText = computed(() =>
    this.ws()?.lastScan ? 'Lần quét không tìm thấy endpoint nào trong các nguồn Backend.' : 'Workspace chưa được quét.'
  );

  protected readonly panelKey = computed(() => this.selectedUi()?.node.nodeKey ?? this.selected()?.nodeKey ?? null);

  protected readonly panelHeader = computed<PanelHeader | null>(() => {
    const u = this.selectedUi();
    if (u) {
      const element = u.node.kind === 'UI_ELEMENT';
      const technical = this.uiTechnical(u);
      return {
        title: this.uiTitle(u),
        mono: technical,
        method: null,
        path: null,
        pills: [
          { text: this.typeOf(u), cls: 'p-gray' },
          ...(element ? [u.node.callsApi ? { text: 'Gọi máy chủ', cls: 'p-blue' } : { text: 'Chỉ giao diện', cls: 'p-gray' }] : [])
        ],
        container: this.uiIsContainer(u)
      };
    }
    const e = this.selected();
    if (e) {
      return {
        title: e.summary || e.path || '',
        mono: !e.summary,
        method: e.httpMethod ?? null,
        path: e.summary ? (e.path ?? null) : null,
        pills: [],
        container: false
      };
    }
    return null;
  });

  private readonly datePipe = new DatePipe('en-US');

  constructor() {
    effect(() => {
      const id = this.workspaceId();
      untracked(() => {
        this.selected.set(null);
        this.selectedUi.set(null);
        this.feTree.set(null);
        this.feError.set(null);
        void this.load(id);
        if (this.sideTab() === 'fe') {
          void this.loadFrontend(id);
        }
      });
    });
    // Chọn API hoặc phần tử khác → dò vết luồng của nó (D1); thành phần chứa không có luồng
    effect(() => {
      const start = this.traceStart();
      untracked(() => void this.loadTrace(start));
    });
    // Lần quét của Workspace này vừa xong → tải lại cây, thông tin Workspace và luồng đang xem
    effect(() => {
      const done = this.tracker.finished();
      if (done && done.workspaceId === untracked(() => this.workspaceId())) {
        untracked(() => {
          const reloaded = [this.load(done.workspaceId)];
          if (this.feTree() || this.sideTab() === 'fe') {
            reloaded.push(this.loadFrontend(done.workspaceId));
          }
          void Promise.all(reloaded).then(() => {
            this.refreshSeq.update((n) => n + 1);
            return this.loadTrace(this.traceStart());
          });
        });
      }
    });
    // Ctrl+K: mở tới đúng thành phần được chọn trong bảng tìm nhanh
    effect(() => {
      const request = this.navigation.request();
      if (request && request.workspaceId === this.workspaceId()) {
        untracked(() => {
          this.navigation.consume();
          void this.openHit(request);
        });
      }
    });
  }

  /** Node bắt đầu luồng: phần tử giao diện, hoặc API Backend; thành phần chứa thì không có luồng. */
  private readonly traceStart = computed<string | null>(() => {
    const ui = this.selectedUi();
    if (ui) {
      return ui.node.kind === 'UI_ELEMENT' ? (ui.node.nodeKey ?? null) : null;
    }
    return this.selected()?.nodeKey ?? null;
  });

  protected openTab(tab: 'be' | 'fe'): void {
    this.sideTab.set(tab);
    if (tab === 'fe' && !this.feTree() && !this.feLoading()) {
      void this.loadFrontend(this.workspaceId());
    }
  }

  protected selectEndpoint(endpoint: EndpointNodeDto): void {
    this.selectedUi.set(null);
    this.selected.set(endpoint);
  }

  protected selectUi(selection: UiSelection): void {
    this.selected.set(null);
    this.selectedUi.set(selection);
  }

  protected typeOf(u: UiSelection): string {
    return typeLabel(u.node);
  }

  protected uiTitle(u: UiSelection): string {
    return fullLabel(u.node);
  }

  protected uiTechnical(u: UiSelection): boolean {
    return nodeLabel(u.node).technical && u.node.kind === 'UI_ELEMENT';
  }

  protected uiIsContainer(u: UiSelection): boolean {
    return isContainer(u.node);
  }

  /** Mở thành phần mà bảng tìm nhanh vừa chọn: đổi tab, mở các tổ tiên trên cây, chọn và cuộn tới. */
  private async openHit({ hit }: NavigationRequest): Promise<void> {
    const key = hit.nodeKey ?? '';
    if (hit.kind === 'API_ENDPOINT') {
      this.sideTab.set('be');
      const endpoint = (this.tree()?.modules ?? []).flatMap((m) => m.endpoints ?? []).find((e) => e.nodeKey === key);
      if (!endpoint) {
        this.toast.show('Không tìm thấy API này trên cây — hãy quét lại Workspace.', 'warn');
        return;
      }
      this.selectEndpoint(endpoint);
      this.reveal(this.beReveal, key);
    } else if (hit.kind?.startsWith('UI_')) {
      this.sideTab.set('fe');
      if (!this.feTree()) {
        await this.loadFrontend(this.workspaceId());
      }
      const trail = findTrail(this.feTree()?.apps ?? [], key);
      const node = trail?.at(-1);
      if (!trail || !node) {
        this.toast.show('Không tìm thấy thành phần này trên cây — hãy quét lại Workspace.', 'warn');
        return;
      }
      this.selectUi({ node, trail: trail.slice(0, -1) });
      this.reveal(this.feReveal, key);
    } else {
      this.describeOther(hit);
    }
  }

  /** Bấm một chip quan hệ ở cột phải: mở thành phần đó như khi chọn từ bảng tìm nhanh. */
  protected openRelation(r: NodeRelationDto): void {
    void this.openHit({ seq: 0, workspaceId: this.workspaceId(), hit: { nodeKey: r.nodeKey, kind: r.kind, name: r.name } });
  }

  /** Bảng dữ liệu và loại khác chưa có chỗ mở riêng: báo thông tin thay vì im lặng. */
  private describeOther(hit: SearchHitDto): void {
    const detail = hit.context ? ` (entity ${hit.context})` : '';
    this.toast.show(`${hit.kind === 'DB_TABLE' ? 'Bảng' : 'Thành phần'} "${hit.name}"${detail} — chưa có màn hình riêng để mở.`, 'info');
  }

  private reveal(target: { set(value: RevealRequest | null): void }, key: string): void {
    target.set({ key, seq: ++this.revealSeq });
    // yêu cầu chỉ có tác dụng một lần: không mở lại khi cây được tải lại sau lần quét
    setTimeout(() => target.set(null), 400);
  }

  protected rescan(): void {
    const w = this.ws();
    if (w) {
      void this.tracker.start(w.id!, w.name ?? '', w.lastScan ? 'RESCAN' : 'IMPORT');
    }
  }

  protected startResize(e: PointerEvent): void {
    e.preventDefault();
    const startX = e.clientX;
    const startWidth = this.leftWidth();
    const move = (ev: PointerEvent) =>
      this.leftWidth.set(Math.min(MAX_LEFT, Math.max(MIN_LEFT, startWidth + ev.clientX - startX)));
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      saveLeftWidth(this.leftWidth());
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  }

  /** AI vừa đặt tên phần tử: tải lại cây Frontend để hiện tên mới (cây, liên kết, diagram không đổi). */
  protected reloadFrontend(): void {
    void this.loadFrontend(this.workspaceId());
  }

  /** Dò vết lại thành phần đang chọn (vd sau khi chốt / bỏ chốt một liên kết trên diagram). */
  protected reloadTrace(): void {
    this.refreshSeq.update((n) => n + 1);
    void this.loadTrace(this.traceStart());
  }

  private async loadTrace(startKey: string | null): Promise<void> {
    const token = ++this.traceToken;
    this.traceError.set(null);
    this.traceInfo.set(null);
    if (!startKey) {
      this.trace.set(null);
      this.traceLoading.set(false);
      return;
    }
    this.traceLoading.set(true);
    try {
      const dto = await this.traceApi.trace(this.workspaceId(), startKey);
      if (token === this.traceToken) {
        this.trace.set(normalizeTrace(dto));
      }
    } catch (e) {
      if (token === this.traceToken) {
        this.trace.set(null);
        const error = toApiError(e);
        if (error.code === 'TRACE_START_UNSUPPORTED' && this.selectedUi()) {
          // Không phải lỗi: loại thành phần này không phải điểm xuất phát của một luồng
          this.traceInfo.set({ title: 'Chưa dò được luồng từ thành phần này', text: error.message });
        } else {
          this.traceError.set(error.message);
        }
      }
    } finally {
      if (token === this.traceToken) {
        this.traceLoading.set(false);
      }
    }
  }

  private async loadFrontend(id: string): Promise<void> {
    this.feLoading.set(true);
    try {
      const tree = await this.graphApi.frontendTree(id);
      if (id === this.workspaceId()) {
        this.feTree.set(tree);
        this.feError.set(null);
      }
    } catch (e) {
      this.feError.set(toApiError(e).message);
    } finally {
      this.feLoading.set(false);
    }
  }

  private async load(id: string): Promise<void> {
    this.treeLoading.set(true);
    try {
      const [ws, tree] = await Promise.all([this.workspaceApi.get(id), this.graphApi.backendTree(id)]);
      this.ws.set(ws);
      this.tree.set(tree);
      this.treeError.set(null);
      const last = ws.lastScan;
      if (last && isRunning(last.status)) {
        this.tracker.resume(id, ws.name ?? '', { ...last, workspaceId: id });
      }
    } catch (e) {
      this.treeError.set(toApiError(e).message);
    } finally {
      this.treeLoading.set(false);
    }
  }
}

/** Độ rộng cột trái lưu trong trình duyệt (chỉ là tiện ích hiển thị). */
function readLeftWidth(): number {
  try {
    const v = Number(localStorage.getItem(LEFT_WIDTH_KEY));
    return v >= MIN_LEFT && v <= MAX_LEFT ? v : DEFAULT_LEFT;
  } catch {
    return DEFAULT_LEFT;
  }
}

function saveLeftWidth(width: number): void {
  try {
    localStorage.setItem(LEFT_WIDTH_KEY, String(width));
  } catch {
    // trình duyệt chặn lưu trữ: bỏ qua
  }
}
