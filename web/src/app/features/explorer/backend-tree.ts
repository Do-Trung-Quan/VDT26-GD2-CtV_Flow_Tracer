import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, input, output, signal, untracked, viewChild } from '@angular/core';
import type { ApiModuleDto, BackendTreeDto, EndpointNodeDto } from '../../core/api/api-types';
import { Icon } from '../../core/ui/icon';
import type { RevealRequest } from './frontend-tree';
import { filterModules, highlight } from './tree-filter';

/** Cây API Backend Module → Endpoint (API C1): lọc nhanh có tô sáng, thu/mở module, chọn endpoint. */
@Component({
  selector: 'app-backend-tree',
  imports: [Icon],
  template: `
    <div class="srch">
      <input class="in" type="search" placeholder="Lọc nhanh: đường dẫn, phương thức, module…" aria-label="Lọc cây API"
        [value]="query()" (input)="query.set(val($event))" />
    </div>
    @if (tree(); as t) {
      <div class="hint count">
        @if (query().trim()) {
          {{ shownEndpoints() }} / {{ t.endpointCount }} endpoint khớp
        } @else {
          {{ t.moduleCount }} module · {{ t.endpointCount }} endpoint
        }
      </div>
    }
    <div #list class="tree" role="tree">
      @for (m of modules(); track key(m)) {
        <button class="tn" type="button" role="treeitem" aria-selected="false" [attr.aria-expanded]="isOpen(m)" (click)="toggle(m)">
          <span class="chev" [class.open]="isOpen(m)"><app-icon name="chev" [small]="true" /></span>
          <app-icon name="folder" [small]="true" />
          <span class="nm" [title]="moduleTitle(m)">
            @for (p of parts(m.name); track $index) {
              @if (p.hit) {
                <mark>{{ p.text }}</mark>
              } @else {
                {{ p.text }}
              }
            }
          </span>
          <span class="cnt">{{ m.endpointCount }}</span>
        </button>
        @if (isOpen(m)) {
          @for (e of m.endpoints ?? []; track e.nodeKey) {
            <button class="tn ep" type="button" role="treeitem" [class.sel]="e.nodeKey === selectedKey()"
              [attr.aria-selected]="e.nodeKey === selectedKey()" [attr.data-key]="e.nodeKey" [title]="e.summary ?? e.path"
              (click)="selected.emit(e)">
              <span [class]="'mt ' + e.httpMethod">{{ e.httpMethod }}</span>
              <span class="nm mono">
                @for (p of parts(e.path); track $index) {
                  @if (p.hit) {
                    <mark>{{ p.text }}</mark>
                  } @else {
                    {{ p.text }}
                  }
                }
              </span>
              @if (e.warnings?.length) {
                <span class="warnic" [title]="e.warnings!.join('; ')"><app-icon name="alert" [small]="true" /></span>
              }
            </button>
          }
        }
      } @empty {
        <div class="empty small">
          <span>{{ query().trim() ? 'Không có endpoint nào khớp.' : emptyText() }}</span>
        </div>
      }
    </div>
  `,
  styles: `
    :host { display: flex; flex-direction: column; min-height: 0; flex: 1; }
    .srch { padding: 10px 12px 6px; }
    .srch .in { height: 32px; }
    .count { padding: 0 14px 6px; }
    .tree { flex: 1; overflow: auto; padding: 0 6px 14px; }
    .tn { all: unset; box-sizing: border-box; display: flex; align-items: center; gap: 6px; width: 100%; height: 30px; padding: 0 8px 0 4px; border-radius: 6px; cursor: pointer; font-size: 13.5px; color: var(--t2); white-space: nowrap; }
    .tn:hover { background: #f3f4f6; }
    .tn:focus-visible { box-shadow: 0 0 0 2px var(--brand); }
    .tn.sel { background: var(--brand-l); color: var(--brand); font-weight: 600; }
    .tn.ep { padding-left: 26px; }
    .nm { overflow: hidden; text-overflow: ellipsis; flex: 1; }
    .chev { display: grid; place-items: center; color: var(--t4); transition: transform 0.12s; }
    .chev.open { transform: rotate(90deg); }
    .cnt { font-size: 11.5px; color: var(--t4); }
    mark { background: #fef08a; color: inherit; border-radius: 2px; }
    .mt { font: 700 10px var(--mono); padding: 2px 5px; border-radius: 4px; flex: none; min-width: 42px; text-align: center; background: #f3f4f6; color: var(--t2); }
    .mt.GET { background: var(--green-l); color: var(--green); }
    .mt.POST { background: var(--blue-l); color: var(--blue); }
    .mt.PUT, .mt.PATCH { background: var(--amber-l); color: var(--amber); }
    .mt.DELETE { background: var(--brand-l); color: var(--brand); }
    .warnic { color: var(--amber); display: inline-flex; }
    .empty.small { padding: 24px 12px; font-size: 13px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class BackendTree {
  readonly tree = input<BackendTreeDto | null>(null);
  readonly selectedKey = input<string | null>(null);
  readonly emptyText = input('Chưa có endpoint nào.');
  /** Yêu cầu mở tới một endpoint (từ Ctrl+K): xóa bộ lọc, mở module chứa nó rồi cuộn tới. */
  readonly reveal = input<RevealRequest | null>(null);
  readonly selected = output<EndpointNodeDto>();
  private readonly list = viewChild<ElementRef<HTMLElement>>('list');

  protected readonly query = signal('');
  /** Module đang mở (key = tên + Source); khi đang lọc thì mở tất cả. */
  private readonly opened = signal<Set<string>>(new Set());

  protected readonly modules = computed(() => filterModules(this.tree()?.modules ?? [], this.query()));
  protected readonly shownEndpoints = computed(() => this.modules().reduce((n, m) => n + (m.endpoints?.length ?? 0), 0));

  constructor() {
    effect(() => {
      const request = this.reveal();
      const t = this.tree();
      if (!request || !t) {
        return;
      }
      untracked(() => {
        const module = (t.modules ?? []).find((m) => (m.endpoints ?? []).some((e) => e.nodeKey === request.key));
        if (!module) {
          return;
        }
        this.query.set('');
        this.opened.set(new Set([...this.opened(), this.key(module)]));
        setTimeout(() => this.list()?.nativeElement.querySelector<HTMLElement>(`[data-key="${CSS.escape(request.key)}"]`)?.scrollIntoView({ block: 'center' }), 0);
      });
    });
  }

  protected val(e: Event): string {
    return (e.target as HTMLInputElement).value;
  }

  protected parts(text: string | undefined) {
    return highlight(text, this.query());
  }

  protected isOpen(m: ApiModuleDto): boolean {
    return !!this.query().trim() || this.opened().has(this.key(m));
  }

  protected toggle(m: ApiModuleDto): void {
    const next = new Set(this.opened());
    const k = this.key(m);
    if (next.has(k)) {
      next.delete(k);
    } else {
      next.add(k);
    }
    this.opened.set(next);
  }

  protected moduleTitle(m: ApiModuleDto): string {
    const source = m.nameSource === 'TAG' ? 'theo @Tag' : m.nameSource === 'CONTROLLER' ? 'theo tên Controller' : 'theo package';
    return `${m.name} — tên module ${source}; nguồn mã ${m.sourceName ?? ''}`;
  }

  protected key(m: ApiModuleDto): string {
    return `${m.name}|${m.sourceId}`;
  }
}
