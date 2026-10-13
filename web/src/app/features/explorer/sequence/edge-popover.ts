import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  afterRenderEffect,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { toApiError } from '../../../core/api/api-error';
import type { EdgeDetailDto, SearchHitDto } from '../../../core/api/api-types';
import { GraphApi } from '../../../core/api/graph.api';
import { LinkingApi } from '../../../core/api/linking.api';
import { Icon } from '../../../core/ui/icon';
import { ToastService } from '../../../core/ui/toast.service';
import { CandidateRow, candidateRows, confidenceBadge, initialSelection, placePopover } from './edge-popover-model';
import type { Step } from './sequence-model';

const SEARCH_DEBOUNCE_MS = 250;
const SEARCH_LIMIT = 8;

/**
 * Popover chi tiết liên kết khi bấm một mũi tên "Suy luận" / "Chưa xác định" / "Chốt tay" (theo bản vẽ giao diện): mức tin
 * cậy, lý do, ứng viên kèm mức khớp Cao / Trung bình / Thấp (API E1), chọn rồi Chốt tay (E4) hoặc Bỏ chốt tay (E5). Lời gọi HTTP
 * còn chọn được một API bất kỳ qua ô tìm (C3) — dùng khi phân tích tĩnh không tìm ra ứng viên. "Nhờ AI phân tích" để sẵn nhưng
 * mờ tới Giai đoạn 7. Popover luôn nằm trọn trong vùng canvas.
 */
@Component({
  selector: 'app-edge-popover',
  imports: [Icon],
  template: `
    <div #box class="epop" role="dialog" aria-label="Chi tiết liên kết"
      [style.left.px]="pos().left" [style.top.px]="pos().top" [style.max-height.px]="pos().maxHeight">
      @if (loading()) {
        <div class="skel" style="width: 60%"></div>
        <div class="skel"></div>
        <div class="skel" style="width: 80%"></div>
      } @else if (error()) {
        <div class="row hd">
          <b class="sp1">Bước {{ step().index }}</b>
          <button class="ibtn" type="button" aria-label="Đóng" (click)="closed.emit()"><app-icon name="x" [small]="true" /></button>
        </div>
        <div class="errb">{{ error() }}</div>
      } @else if (detail(); as d) {
        <div class="row hd">
          <span [class]="'pill ' + badge().pill">{{ badge().label }}</span>
          <b class="sp1 ttl">Bước {{ step().index }}: {{ fromLabel() }} → {{ toLabel() }}</b>
          <button class="ibtn" type="button" aria-label="Đóng" (click)="closed.emit()"><app-icon name="x" [small]="true" /></button>
        </div>
        <div class="hint why">
          @if (d.override?.decision === 'PIN') {
            Bạn đã chốt tay <b>{{ d.override?.toName || d.override?.toNodeKey }}</b>. Liên kết này luôn được ưu tiên và giữ qua mọi lần quét lại.
            @if (d.override?.note) {
              Ghi chú: {{ d.override?.note }}
            }
          } @else {
            {{ d.reason }}
          }
          @if (d.filePath) {
            <div class="mono loc">{{ d.filePath }}{{ d.line ? ':' + d.line : '' }}</div>
          }
        </div>

        <div class="cap">Ứng viên · mức khớp do phân tích tĩnh tính</div>
        @for (c of rows(); track c.nodeKey) {
          <button class="cand" type="button" [class.on]="selected() === c.nodeKey" (click)="selected.set(c.nodeKey)">
            <span class="mk"><app-icon [name]="selected() === c.nodeKey ? 'check' : 'link'" [small]="true" /></span>
            <span class="tx">
              <span class="nm">{{ c.name }}</span>
              @if (c.detail) {
                <span class="hint">{{ c.detail }}</span>
              }
            </span>
            @if (c.level; as l) {
              <span [class]="'pill ' + l.pill">{{ l.label }}</span>
            } @else if (c.nodeKey === picked()?.nodeKey) {
              <span class="pill p-blue">Bạn chọn</span>
            }
          </button>
        } @empty {
          <div class="hint none">Phân tích tĩnh chưa tìm được ứng viên nào cho liên kết này.</div>
        }

        @if (canPickAny()) {
          <div class="fld find">
            <input class="in" placeholder="Chọn API khác: gõ đường dẫn hoặc tên API…" aria-label="Tìm API để chốt tay"
              [value]="query()" (input)="onQuery($event)" />
            @if (hits().length) {
              <div class="hits">
                @for (h of hits(); track h.nodeKey) {
                  <button class="hit" type="button" (click)="pick(h)">
                    <span class="mono">{{ h.name }}</span>
                    @if (h.displayName && h.displayName !== h.name) {
                      <span class="hint">{{ h.displayName }}</span>
                    }
                  </button>
                }
              </div>
            } @else if (searched() && query().trim()) {
              <div class="hint">Không có API nào khớp "{{ query().trim() }}".</div>
            }
          </div>
        }

        @if (!d.override) {
          <div class="fld note">
            <input class="in" placeholder="Ghi chú khi chốt tay (tùy chọn)" aria-label="Ghi chú khi chốt tay" maxlength="500"
              [value]="note()" (input)="note.set(val($event))" />
          </div>
        }
        <div class="row acts">
          @if (d.override) {
            <button class="btn sm danger" type="button" [disabled]="busy()" (click)="undo()"><app-icon name="trash" [small]="true" />Bỏ chốt tay</button>
          }
          <button class="btn sm" type="button" disabled title="Có từ Giai đoạn 7"><app-icon name="sparkle" [small]="true" />Nhờ AI phân tích</button>
          <button class="btn sm pri" type="button" [disabled]="!canPin()" (click)="pin()"><app-icon name="pin" [small]="true" />Chốt tay</button>
        </div>
        <div class="hint ai">AI phân tích liên kết chưa bật — vẫn chốt tay được.</div>
      }
    </div>
  `,
  styles: `
    :host { display: contents; }
    .epop {
      position: absolute; z-index: 20; width: 390px; max-width: calc(100% - 24px); overflow: auto; background: #fff;
      border-radius: 12px; box-shadow: var(--sh-xl), var(--ring); padding: 14px; cursor: default;
    }
    .hd { margin-bottom: 8px; align-items: flex-start; }
    .hd .pill { flex: none; margin-top: 1px; }
    .ttl { font-size: 13.5px; overflow-wrap: anywhere; }
    .why { margin-bottom: 10px; overflow-wrap: anywhere; }
    .loc { margin-top: 4px; color: var(--t4); font-size: 11.5px; }
    .cap { font-size: 12px; font-weight: 700; color: var(--t3); margin-bottom: 6px; text-transform: uppercase; letter-spacing: 0.2px; }
    .cand {
      display: flex; gap: 10px; width: 100%; padding: 10px; border: 0; border-radius: 8px; background: #fff; box-shadow: var(--ring);
      margin-bottom: 8px; cursor: pointer; align-items: flex-start; text-align: left; font: inherit; color: inherit;
    }
    .cand.on { box-shadow: inset 0 0 0 2px var(--brand); background: var(--brand-l); }
    .cand .mk { margin-top: 2px; color: var(--t4); display: grid; }
    .cand.on .mk { color: var(--brand); }
    .cand .tx { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; overflow-wrap: anywhere; }
    .cand .nm { font-weight: 600; font-size: 13px; }
    .cand .pill { flex: none; }
    .none { margin-bottom: 8px; }
    .find { margin: 4px 0 8px; }
    .hits { margin-top: 6px; box-shadow: var(--ring); border-radius: 8px; max-height: 180px; overflow: auto; }
    .hit {
      display: flex; flex-direction: column; align-items: flex-start; gap: 2px; width: 100%; border: 0; background: #fff; padding: 7px 10px;
      font: inherit; text-align: left; cursor: pointer; overflow-wrap: anywhere;
    }
    .hit:hover { background: #f3f4f6; }
    .note { margin: 10px 0 8px; }
    .acts { justify-content: flex-end; flex-wrap: wrap; }
    .ai { margin-top: 8px; text-align: right; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class EdgePopover implements OnDestroy {
  private readonly linking = inject(LinkingApi);
  private readonly graph = inject(GraphApi);
  private readonly toast = inject(ToastService);
  private readonly box = viewChild<ElementRef<HTMLElement>>('box');

  readonly workspaceId = input.required<string>();
  readonly step = input.required<Step>();
  /** Nhãn cột nguồn / đích của bước (để ghi tiêu đề "Bước n: A → B"). */
  readonly fromLabel = input('');
  readonly toLabel = input('');
  /** Điểm bấm, tính trong vùng canvas. */
  readonly at = input.required<{ x: number; y: number }>();
  /** Kích thước vùng canvas. */
  readonly area = input.required<{ width: number; height: number }>();

  readonly closed = output<void>();
  /** Đã chốt / bỏ chốt: trang chứa dò vết lại. */
  readonly changed = output<void>();

  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly detail = signal<EdgeDetailDto | null>(null);
  protected readonly selected = signal<string | null>(null);
  protected readonly note = signal('');
  protected readonly busy = signal(false);
  protected readonly query = signal('');
  protected readonly hits = signal<SearchHitDto[]>([]);
  protected readonly searched = signal(false);
  protected readonly picked = signal<CandidateRow | null>(null);
  protected readonly pos = signal({ left: 0, top: 0, maxHeight: 400 });

  protected readonly badge = computed(() => confidenceBadge(this.step().confidence, this.step().pinned));
  protected readonly rows = computed(() => {
    const d = this.detail();
    if (!d) {
      return [];
    }
    const rows = candidateRows(d);
    const p = this.picked();
    if (p && !rows.some((r) => r.nodeKey === p.nodeKey)) {
      rows.push(p);
    }
    // Đích đã ghim không nằm trong ứng viên (người dùng chọn API khác): vẫn hiện để thấy đang ghim gì
    const pinnedKey = d.override?.decision === 'PIN' ? d.override.toNodeKey : undefined;
    if (pinnedKey && !rows.some((r) => r.nodeKey === pinnedKey)) {
      rows.push({ nodeKey: pinnedKey, name: d.override?.toName || pinnedKey, detail: 'Đang chốt tay', level: null, current: false });
    }
    return rows;
  });
  /** Lời gọi HTTP chỉ có một cạnh nên ghim được sang API bất kỳ; lời gọi hàm chỉ chọn trong ứng viên. */
  protected readonly canPickAny = computed(() => this.detail()?.kind === 'HTTP_REQUEST');
  protected readonly canPin = computed(() => {
    const d = this.detail();
    const target = this.selected();
    const pinnedKey = d?.override?.decision === 'PIN' ? d.override.toNodeKey : undefined;
    return !!d?.overridable && !!target && target !== pinnedKey && !this.busy();
  });

  private searchTimer: ReturnType<typeof setTimeout> | undefined;
  private searchToken = 0;

  constructor() {
    effect(() => {
      const ref = this.step().edgeRef;
      const ws = this.workspaceId();
      untracked(() => void this.load(ws, ref));
    });
    // Đặt vị trí sau khi vẽ (cần chiều cao thật của popover); chỉ ghi khi vị trí đổi để không lặp
    afterRenderEffect(() => {
      const el = this.box()?.nativeElement;
      const at = this.at();
      const area = this.area();
      this.detail();
      this.hits();
      if (!el) {
        return;
      }
      const next = placePopover(at, { width: el.offsetWidth, height: el.scrollHeight }, area);
      const cur = untracked(this.pos);
      if (next.left !== cur.left || next.top !== cur.top || next.maxHeight !== cur.maxHeight) {
        this.pos.set(next);
      }
    });
  }

  ngOnDestroy(): void {
    clearTimeout(this.searchTimer);
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  private async load(ws: string, edgeRef: string | null): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    this.picked.set(null);
    this.query.set('');
    this.hits.set([]);
    if (!edgeRef) {
      this.error.set('Bước này không gắn với liên kết nào trong kết quả quét.');
      this.loading.set(false);
      return;
    }
    try {
      const d = await this.linking.edge(ws, edgeRef);
      this.detail.set(d);
      this.selected.set(initialSelection(d, candidateRows(d)));
    } catch (e) {
      const err = toApiError(e);
      this.error.set(err.code === 'EDGE_NOT_FOUND' ? 'Liên kết đã thay đổi sau lần quét gần nhất — hãy chọn lại thành phần để dò vết lại.' : err.message);
    } finally {
      this.loading.set(false);
    }
  }

  protected onQuery(e: Event): void {
    const q = this.val(e);
    this.query.set(q);
    clearTimeout(this.searchTimer);
    if (!q.trim()) {
      this.hits.set([]);
      this.searched.set(false);
      return;
    }
    this.searchTimer = setTimeout(() => void this.search(q.trim()), SEARCH_DEBOUNCE_MS);
  }

  private async search(q: string): Promise<void> {
    const token = ++this.searchToken;
    try {
      const result = await this.graph.search(this.workspaceId(), q, ['API'], SEARCH_LIMIT);
      if (token === this.searchToken) {
        this.hits.set(result.groups?.flatMap((g) => g.hits ?? []) ?? []);
        this.searched.set(true);
      }
    } catch {
      if (token === this.searchToken) {
        this.hits.set([]);
        this.searched.set(true);
      }
    }
  }

  protected pick(hit: SearchHitDto): void {
    const row: CandidateRow = {
      nodeKey: hit.nodeKey ?? '',
      name: hit.displayName && hit.displayName !== hit.name ? hit.displayName : (hit.name ?? ''),
      detail: hit.displayName && hit.displayName !== hit.name ? (hit.name ?? '') : '',
      level: null,
      current: false
    };
    this.picked.set(row);
    this.selected.set(row.nodeKey);
    this.hits.set([]);
    this.query.set('');
    this.searched.set(false);
  }

  protected async pin(): Promise<void> {
    const d = this.detail();
    const target = this.selected();
    if (!d?.from?.nodeKey || !d.kind || !target) {
      return;
    }
    this.busy.set(true);
    try {
      await this.linking.createOverride(this.workspaceId(), {
        fromNodeKey: d.from.nodeKey,
        toNodeKey: target,
        edgeKind: d.kind,
        decision: 'PIN',
        note: this.note().trim() || undefined
      });
      this.toast.show('Đã chốt tay — luồng được dò lại và giữ qua mọi lần quét lại.', 'ok');
      this.changed.emit();
    } catch (e) {
      this.toast.show(toApiError(e).message || 'Không chốt được.', 'err');
    } finally {
      this.busy.set(false);
    }
  }

  protected async undo(): Promise<void> {
    const id = this.detail()?.override?.id;
    if (!id) {
      return;
    }
    this.busy.set(true);
    try {
      await this.linking.deleteOverride(this.workspaceId(), id);
      this.toast.show('Đã bỏ chốt tay.');
      this.changed.emit();
    } catch (e) {
      this.toast.show(toApiError(e).message || 'Không bỏ chốt được.', 'err');
    } finally {
      this.busy.set(false);
    }
  }

}
