import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { BackendTreeDto, EndpointNodeDto, WorkspaceDto } from '../../core/api/api-types';
import { TraceApi } from '../../core/api/trace.api';
import { GraphApi } from '../../core/api/graph.api';
import { WorkspaceApi } from '../../core/api/workspace.api';
import { Icon } from '../../core/ui/icon';
import { isRunning, statusLabel } from '../scan/scan-format';
import { ScanTracker } from '../scan/scan-tracker';
import { BackendTree } from './backend-tree';
import { SequenceCanvas } from './sequence/sequence-canvas';
import type { SequenceTrace } from './sequence/sequence-model';
import { normalizeTrace } from './sequence/sequence-model';

const MIN_LEFT = 220;
const MAX_LEFT = 560;
const DEFAULT_LEFT = 300;
const LEFT_WIDTH_KEY = 'ft.explorer.leftWidth';

/**
 * Màn hình chính 3 cột: cột trái cây API Backend (C1), canvas giữa (sequence diagram của API đang chọn, D1) với cụm nút nổi
 * trạng thái quét / Quét lại, cột phải thông tin endpoint đang chọn (tab Diễn giải ở Giai đoạn 6).
 */
@Component({
  selector: 'app-explorer',
  imports: [BackendTree, Icon, SequenceCanvas],
  template: `
    <div class="explorer" [style.--lw.px]="collapsed() ? 0 : leftWidth()">
      <aside class="left" [class.hidden]="collapsed()">
        <div class="tabs">
          <button class="tab on" type="button">Backend</button>
          <button class="tab" type="button" disabled title="Cây Frontend có từ Giai đoạn 4">Frontend</button>
          <span class="sp1"></span>
          <button class="ibtn" type="button" aria-label="Thu gọn cột trái" (click)="collapsed.set(true)">‹</button>
        </div>
        @if (treeLoading() && !tree()) {
          @for (i of [1, 2, 3, 4, 5, 6]; track i) {
            <div class="skel pad"></div>
          }
        } @else if (treeError()) {
          <div class="errb pad">{{ treeError() }}</div>
        } @else {
          <app-backend-tree [tree]="tree()" [selectedKey]="selected()?.nodeKey ?? null" [emptyText]="emptyText()"
            (selected)="selected.set($event)" />
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
          [emptyTitle]="emptyTitle()" [emptyText]="emptyHint()" />
      </section>

      <aside class="right">
        @if (selected(); as e) {
          <div class="rh">
            <div class="row"><span [class]="'mt ' + e.httpMethod">{{ e.httpMethod }}</span></div>
            <h2 class="mono">{{ e.path }}</h2>
          </div>
          <div class="rb">
            <div class="sec">
              <h4>Mô tả</h4>
              @if (e.summary) {
                <p>{{ e.summary }}</p>
                <div class="hint">Nguồn: {{ e.summarySource === 'OPERATION' ? '@Operation (Swagger)' : 'Javadoc' }}</div>
              } @else {
                <p class="muted">Mã nguồn không có @Operation hay Javadoc cho API này.</p>
              }
            </div>
            <div class="sec">
              <h4>Vị trí trong mã nguồn</h4>
              <p class="mono">{{ e.filePath }}@if (e.line) {:{{ e.line }}}</p>
            </div>
            @if (e.deprecated) {
              <div class="warn">API được đánh dấu ngừng dùng (@Deprecated).</div>
            }
            @for (w of e.warnings ?? []; track w) {
              <div class="warn gap">{{ w }}</div>
            }
            @if (trace(); as t) {
              <div class="sec">
                <h4>Luồng xử lý</h4>
                <p>{{ t.steps.length }} bước · {{ t.lanes.length }} cột</p>
                <div class="chips">
                  @for (l of t.lanes; track l.id) {
                    <span class="pill p-gray">{{ l.label }}</span>
                  }
                </div>
              </div>
              @for (w of t.warnings; track w) {
                <div class="warn gap">{{ w }}</div>
              }
            }
            <div class="hint">Tab Diễn giải và Kịch bản có từ Giai đoạn 6.</div>
          </div>
        } @else {
          <div class="empty">
            <span>Chọn một API để xem thông tin.</span>
          </div>
        }
      </aside>
    </div>
  `,
  styles: `
    :host { display: block; height: 100%; }
    .explorer { display: grid; grid-template-columns: var(--lw) minmax(0, 1fr) 360px; height: 100%; }
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
    .rh { padding: 14px 16px 0; }
    .rh h2 { margin: 8px 0 6px; font-size: 15px; overflow-wrap: anywhere; }
    .rb { flex: 1; overflow: auto; padding: 14px 16px 28px; }
    .sec { margin-bottom: 18px; }
    .sec h4 { margin: 0 0 6px; font-size: 12px; letter-spacing: 0.4px; text-transform: uppercase; color: var(--t3); }
    .sec p { margin: 0; overflow-wrap: anywhere; }
    .gap { margin-bottom: 8px; }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 8px; }
    .mt { font: 700 11px var(--mono); padding: 2px 6px; border-radius: 4px; background: #f3f4f6; }
    .mt.GET { background: var(--green-l); color: var(--green); }
    .mt.POST { background: var(--blue-l); color: var(--blue); }
    .mt.PUT, .mt.PATCH { background: var(--amber-l); color: var(--amber); }
    .mt.DELETE { background: var(--brand-l); color: var(--brand); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExplorerPage {
  private readonly workspaceApi = inject(WorkspaceApi);
  private readonly graphApi = inject(GraphApi);
  private readonly traceApi = inject(TraceApi);
  private readonly tracker = inject(ScanTracker);

  /** Tham số route :workspaceId. */
  readonly workspaceId = input.required<string>();

  protected readonly ws = signal<WorkspaceDto | null>(null);
  protected readonly tree = signal<BackendTreeDto | null>(null);
  protected readonly treeLoading = signal(false);
  protected readonly treeError = signal<string | null>(null);
  protected readonly selected = signal<EndpointNodeDto | null>(null);
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

  protected readonly emptyTitle = computed(() => (this.tree()?.endpointCount ? 'Chọn một API ở cột trái' : 'Chưa có dữ liệu'));
  protected readonly emptyHint = computed(() =>
    this.tree()?.endpointCount ? 'Sequence diagram của luồng xử lý sẽ hiện ở đây.' : 'Bấm Quét để phân tích mã nguồn của Workspace.'
  );

  protected readonly emptyText = computed(() =>
    this.ws()?.lastScan ? 'Lần quét không tìm thấy endpoint nào trong các nguồn Backend.' : 'Workspace chưa được quét.'
  );

  private readonly datePipe = new DatePipe('en-US');

  constructor() {
    effect(() => {
      const id = this.workspaceId();
      untracked(() => {
        this.selected.set(null);
        void this.load(id);
      });
    });
    // Chọn API khác → dò vết luồng của nó (D1)
    effect(() => {
      const endpoint = this.selected();
      untracked(() => void this.loadTrace(endpoint));
    });
    // Lần quét của Workspace này vừa xong → tải lại cây, thông tin Workspace và luồng đang xem
    effect(() => {
      const done = this.tracker.finished();
      if (done && done.workspaceId === untracked(() => this.workspaceId())) {
        untracked(() => void this.load(done.workspaceId).then(() => this.loadTrace(this.selected())));
      }
    });
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

  private async loadTrace(endpoint: EndpointNodeDto | null): Promise<void> {
    const token = ++this.traceToken;
    this.traceError.set(null);
    if (!endpoint?.nodeKey) {
      this.trace.set(null);
      this.traceLoading.set(false);
      return;
    }
    this.traceLoading.set(true);
    try {
      const dto = await this.traceApi.trace(this.workspaceId(), endpoint.nodeKey);
      if (token === this.traceToken) {
        this.trace.set(normalizeTrace(dto));
      }
    } catch (e) {
      if (token === this.traceToken) {
        this.trace.set(null);
        this.traceError.set(toApiError(e).message);
      }
    } finally {
      if (token === this.traceToken) {
        this.traceLoading.set(false);
      }
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
