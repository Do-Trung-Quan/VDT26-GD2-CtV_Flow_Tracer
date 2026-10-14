import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, output, signal, untracked, viewChild } from '@angular/core';
import type { FrontendTreeDto, UiTreeNodeDto } from '../../core/api/api-types';
import { Icon } from '../../core/ui/icon';
import { countByApi, findTrail, flattenRows, fullLabel, highlightFold, nodeIcon, nodeLabel, pruneToApi, revealIds, searchUi, type UiHit, type UiRow } from './frontend-tree-model';

/** Yêu cầu mở tới một nút (từ Ctrl+K): `seq` tăng mỗi lần để cùng một nút vẫn được mở lại. */
export interface RevealRequest {
  key: string;
  seq: number;
}

/** Nút được chọn kèm các tổ tiên của dòng được bấm (từ ứng dụng xuống). */
export interface UiSelection {
  node: UiTreeNodeDto;
  trail: UiTreeNodeDto[];
}

/**
 * Cây thành phần Frontend (API C2): Ứng dụng → Màn hình → Tab / Hộp thoại / Form → Phần tử. Nhãn "Nút Thêm mới", "Ô Mã"…
 * theo bản vẽ; phần tử gọi API gắn thẻ "API", còn lại "UI". Phần tử không có nhãn trong mã nguồn hiện tên kỹ thuật in nghiêng.
 * Ô tìm kiếm cho danh sách gợi ý hai nhóm (Màn hình / Phần tử giao diện) như ô tìm nhanh trên header; bấm một kết quả thì quay về cây
 * đầy đủ với mục đó được mở và chọn. Mỗi nút có thể xuất hiện ở nhiều màn hình (component dùng chung) nên trạng thái mở theo từng dòng.
 */
@Component({
  selector: 'app-frontend-tree',
  imports: [Icon],
  template: `
    <div class="srch">
      <input class="in" type="search" placeholder="Tìm màn hình, nút, ô nhập…" aria-label="Tìm trong cây Frontend"
        [value]="query()" (input)="query.set(val($event))" (keydown.enter)="openFirst()" (keydown.escape)="query.set('')" />
    </div>
    @if (tree(); as t) {
      <div class="hint count">
        {{ t.appCount }} ứng dụng · {{ t.screenCount }} màn hình · {{ t.elementCount }} phần tử giao diện
      </div>
      @if (counts().uiOnly > 0 && !query().trim()) {
        <label class="sw" title="Phần tử chỉ thay đổi giao diện (mở hộp thoại, đổi tab, ô nhập…) không gọi máy chủ">
          <input type="checkbox" [checked]="showAll()" (change)="setShowAll(checked($event))" />
          Hiện cả phần tử chỉ thao tác giao diện ({{ counts().uiOnly }})
        </label>
      }
    }
    @if (query().trim()) {
      <div class="tree results" role="listbox" aria-label="Kết quả tìm kiếm">
        @for (g of groups(); track g.label) {
          <div class="gh">{{ g.label }} <span>{{ g.total }}</span></div>
          @for (h of g.hits; track h.node.nodeKey) {
            <button class="hit" type="button" role="option" aria-selected="false" (click)="openHit(h)">
              <span class="lead"><app-icon [name]="icon(h.node)" [small]="true" /></span>
              <span class="hb">
                <span class="ht">
                  @for (p of hitParts(h); track $index) {
                    @if (p.hit) {
                      <mark>{{ p.text }}</mark>
                    } @else {
                      {{ p.text }}
                    }
                  }
                </span>
                @if (h.context) {
                  <span class="hc">{{ h.context }}</span>
                }
              </span>
            </button>
          }
          @if (g.total > g.hits.length) {
            <div class="more">Còn {{ g.total - g.hits.length }} kết quả khác — gõ cụ thể hơn để thu hẹp</div>
          }
        }
        @if (groups().length === 0) {
          <div class="empty small"><span>Không có thành phần nào khớp.</span></div>
        }
      </div>
    } @else {
    <div #list class="tree" role="tree">
      @for (r of rows(); track r.id) {
        <button class="tn" type="button" role="treeitem" [class.sel]="r.node.nodeKey === selectedKey()"
          [attr.data-row]="r.id" [attr.aria-selected]="r.node.nodeKey === selectedKey()"
          [attr.aria-expanded]="hasChildren(r.node) ? r.open : null" [style.padding-left.px]="6 + r.depth * 16"
          [title]="tooltip(r.node)" (click)="pick(r)">
          @if (hasChildren(r.node)) {
            <span class="chev" role="button" tabindex="-1" [class.open]="r.open" [attr.aria-label]="r.open ? 'Thu gọn' : 'Mở rộng'"
              (click)="toggle($event, r)" (keydown.enter)="toggle($event, r)"><app-icon name="chev" [small]="true" /></span>
          } @else {
            <span class="chev"></span>
          }
          <span class="lead"><app-icon [name]="icon(r.node)" [small]="true" /></span>
          <span class="nm" [class.tech]="label(r.node).technical">
            @if (label(r.node).prefix) {
              <span class="pre">{{ label(r.node).prefix }}</span>{{ ' ' }}
            }
            {{ label(r.node).text }}
          </span>
          @if (label(r.node).ai) {
            <span class="aimk" title="Tên do AI đặt (chỉ đổi cách hiển thị)">✦</span>
          }
          @if (r.node.kind === 'UI_ELEMENT') {
            <span class="tag" [class.api]="r.node.callsApi" [class.ui]="!r.node.callsApi" [title]="tagTitle(r.node)">
              {{ r.node.callsApi ? 'API' : 'UI' }}
            </span>
          }
        </button>
      } @empty {
        <div class="empty small">
          <span>{{
            tree()?.elementCount && !showAll()
              ? 'Không có phần tử nào gọi API. Bật "Hiện cả phần tử chỉ thao tác giao diện" để xem tất cả.'
              : emptyText()
          }}</span>
        </div>
      }
    </div>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 0; flex: 1; }
    .srch { padding: 10px 12px 6px; }
    .srch .in { height: 32px; }
    .count { padding: 0 14px 6px; }
    .sw { display: flex; align-items: center; gap: 8px; padding: 0 14px 8px; font-size: 12.5px; color: var(--t2); cursor: pointer; }
    .sw input { margin: 0; }
    .tree { flex: 1; overflow: auto; padding: 0 6px 14px; }
    .tn { all: unset; box-sizing: border-box; display: flex; align-items: center; gap: 6px; width: 100%; height: 30px; padding-right: 8px; border-radius: 6px; cursor: pointer; font-size: 13.5px; color: var(--t2); white-space: nowrap; }
    .tn:hover { background: #f3f4f6; }
    .tn:focus-visible { box-shadow: 0 0 0 2px var(--brand); }
    .tn.sel { background: var(--brand-l); color: var(--brand); font-weight: 600; }
    .chev { display: grid; place-items: center; width: 16px; height: 16px; flex: none; color: var(--t4); transition: transform 0.12s; }
    .chev.open { transform: rotate(90deg); }
    .lead { color: var(--t3); display: grid; flex: none; }
    .nm { overflow: hidden; text-overflow: ellipsis; flex: 1; }
    .nm .pre { color: var(--t3); font-weight: 400; }
    .aimk { color: var(--purple); font-size: 11px; flex: none; }
    .tn.sel .nm .pre { color: inherit; opacity: 0.75; }
    .nm.tech { font-style: italic; }
    mark { background: #fef08a; color: inherit; border-radius: 2px; }
    .tag { font-size: 10.5px; font-weight: 700; padding: 1px 6px; border-radius: 4px; flex: none; }
    .tag.api { background: var(--blue-l); color: var(--blue); }
    .tag.ui { background: #f3f4f6; color: var(--t3); }
    .empty.small { padding: 24px 12px; font-size: 13px; }
    .results { padding-top: 2px; }
    .gh { padding: 8px 8px 4px; font-size: 11.5px; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; color: var(--t3); }
    .gh span { font-weight: 600; color: var(--t4); margin-left: 4px; }
    .hit { all: unset; box-sizing: border-box; display: flex; align-items: flex-start; gap: 8px; width: 100%; padding: 6px 8px; border-radius: 6px; cursor: pointer; font-size: 13.5px; color: var(--t2); }
    .hit:hover { background: #f3f4f6; }
    .hit:focus-visible { box-shadow: 0 0 0 2px var(--brand); }
    .hit .lead { margin-top: 2px; }
    .hb { display: flex; flex-direction: column; min-width: 0; }
    .ht { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .hc { font-size: 12px; color: var(--t4); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .more { padding: 6px 10px 10px; font-size: 12px; color: var(--t4); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FrontendTree {
  readonly tree = input<FrontendTreeDto | null>(null);
  readonly selectedKey = input<string | null>(null);
  readonly emptyText = input('Chưa có thành phần Frontend nào.');
  readonly reveal = input<RevealRequest | null>(null);
  readonly selected = output<UiSelection>();

  protected readonly query = signal('');
  /** Mã các dòng đang mở; mặc định mở các ứng dụng để thấy ngay danh sách màn hình. */
  private readonly opened = signal<Set<string>>(new Set());
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');
  private defaulted: FrontendTreeDto | null = null;

  /** Mặc định chỉ phần tử gọi API; bật công tắc thì hiện cả phần tử chỉ thao tác giao diện. */
  protected readonly showAll = signal(readShowAll());
  protected readonly counts = computed(() => countByApi(this.tree()?.apps ?? []));
  private readonly base = computed(() => {
    const apps = this.tree()?.apps ?? [];
    return this.showAll() ? apps : pruneToApi(apps);
  });
  protected readonly rows = computed<UiRow[]>(() => flattenRows(this.base(), this.opened(), false));
  /** Kết quả tìm kiếm (trên cây đầy đủ, kể cả phần tử chỉ thao tác giao diện), chia hai nhóm; nhóm rỗng bị bỏ. */
  protected readonly groups = computed(() => {
    const q = this.query().trim();
    if (!q) {
      return [];
    }
    const r = searchUi(this.tree()?.apps ?? [], q);
    return [
      { label: 'Màn hình', hits: r.screens, total: r.screenTotal },
      { label: 'Phần tử giao diện', hits: r.elements, total: r.elementTotal }
    ].filter((g) => g.total > 0);
  });
  /** Nút đầy đủ (chưa lọc) theo key, lần xuất hiện đầu tiên. */
  private readonly byKey = computed(() => {
    const map = new Map<string, UiTreeNodeDto>();
    const visit = (n: UiTreeNodeDto): void => {
      if (n.nodeKey && !map.has(n.nodeKey)) {
        map.set(n.nodeKey, n);
      }
      (n.children ?? []).forEach(visit);
    };
    (this.tree()?.apps ?? []).forEach(visit);
    return map;
  });

  constructor() {
    // Mỗi lần có cây mới: mở các ứng dụng (giữ trạng thái mở của những dòng vẫn còn)
    effect(() => {
      const t = this.tree();
      untracked(() => {
        if (t && t !== this.defaulted) {
          this.defaulted = t;
          const next = new Set(this.opened());
          (t.apps ?? []).forEach((a) => next.add(`>${a.nodeKey}`));
          this.opened.set(next);
        }
      });
    });
    // Ctrl+K: xóa bộ lọc, mở các tổ tiên rồi cuộn tới dòng cần thấy
    effect(() => {
      const request = this.reveal();
      const t = this.tree();
      if (!request || !t) {
        return;
      }
      untracked(() => this.revealKey(t, request.key));
    });
  }

  /** Xóa ô tìm, mở các tổ tiên (và bật hiện phần tử chỉ thao tác giao diện nếu mục bị ẩn) rồi cuộn tới dòng của mục. */
  private revealKey(t: FrontendTreeDto, key: string): void {
    const ids = revealIds(t.apps ?? [], key);
    if (!ids) {
      return;
    }
    if (!this.showAll() && !findTrail(pruneToApi(t.apps ?? []), key)) {
      this.setShowAll(true);
    }
    this.query.set('');
    this.opened.set(new Set([...this.opened(), ...ids.ancestors]));
    setTimeout(() => this.scrollTo(ids.rowId), 0);
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  protected checked(e: Event): boolean {
    return (e.target as HTMLInputElement).checked;
  }

  protected setShowAll(value: boolean): void {
    this.showAll.set(value);
    try {
      localStorage.setItem(SHOW_ALL_KEY, value ? '1' : '0');
    } catch {
      // trình duyệt chặn lưu trữ: chỉ mất việc ghi nhớ
    }
  }

  protected label(n: UiTreeNodeDto) {
    return nodeLabel(n);
  }

  protected icon(n: UiTreeNodeDto) {
    return nodeIcon(n);
  }

  protected tagTitle(n: UiTreeNodeDto): string {
    if (n.callsApi) {
      return 'Thao tác này dẫn tới một lời gọi API';
    }
    return n.leadsToApi
      ? 'Tự nó không gọi máy chủ; mở hộp thoại hoặc chuyển màn hình có gọi API'
      : 'Chỉ thay đổi giao diện, không gọi máy chủ';
  }

  protected hasChildren(n: UiTreeNodeDto): boolean {
    return (n.children?.length ?? 0) > 0;
  }

  protected hitParts(h: UiHit) {
    return highlightFold(fullLabel(h.node), this.query());
  }

  /** Bấm một kết quả: quay lại cây đầy đủ, mở và chọn mục đó. */
  protected openHit(h: UiHit): void {
    const t = this.tree();
    const key = h.node.nodeKey;
    const trail = t && key ? findTrail(t.apps ?? [], key) : null;
    if (!t || !key || !trail) {
      return;
    }
    this.revealKey(t, key);
    this.selected.emit({ node: trail[trail.length - 1], trail: trail.slice(0, -1) });
  }

  /** Enter trong ô tìm: mở kết quả đầu tiên. */
  protected openFirst(): void {
    const first = this.groups()[0]?.hits[0];
    if (first) {
      this.openHit(first);
    }
  }

  protected tooltip(n: UiTreeNodeDto): string {
    const l = nodeLabel(n);
    const base = n.kind === 'UI_SCREEN' && n.routePath ? `${fullLabel(n)} — ${n.routePath}` : fullLabel(n);
    return l.technical ? `${base} — không có nhãn trong mã nguồn, đang hiện tên kỹ thuật` : base;
  }

  protected toggle(e: Event, r: UiRow): void {
    e.stopPropagation();
    const next = new Set(this.opened());
    if (next.has(r.id)) {
      next.delete(r.id);
    } else {
      next.add(r.id);
    }
    this.opened.set(next);
  }

  protected pick(r: UiRow): void {
    // chọn một thành phần chứa thì mở luôn nó (như bản vẽ)
    if (this.hasChildren(r.node) && !r.open) {
      this.opened.set(new Set([...this.opened(), r.id]));
    }
    // Khi đang lọc, cây hiển thị chỉ giữ con khớp: trả về nút gốc đầy đủ để cột phải đếm đúng
    const original = (key: string | undefined, fallback: UiTreeNodeDto) => (key ? (this.byKey().get(key) ?? fallback) : fallback);
    this.selected.emit({ node: original(r.node.nodeKey, r.node), trail: r.trail.map((n) => original(n.nodeKey, n)) });
  }

  private scrollTo(rowId: string): void {
    const host = this.list()?.nativeElement;
    const el = host?.querySelector<HTMLElement>(`[data-row="${CSS.escape(rowId)}"]`);
    el?.scrollIntoView({ block: 'center' });
  }
}

const SHOW_ALL_KEY = 'ft.explorer.showUiOnly';

/** Có hiện cả phần tử chỉ thao tác giao diện không (tiện ích hiển thị lưu trong trình duyệt; mặc định ẩn). */
function readShowAll(): boolean {
  try {
    return localStorage.getItem(SHOW_ALL_KEY) === '1';
  } catch {
    return false;
  }
}
