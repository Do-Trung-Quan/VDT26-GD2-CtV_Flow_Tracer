import { ChangeDetectionStrategy, Component, ElementRef, HostListener, OnDestroy, afterNextRender, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { toApiError } from '../../core/api/api-error';
import type { SearchDto, SearchHitDto } from '../../core/api/api-types';
import { GraphApi } from '../../core/api/graph.api';
import { Icon } from '../../core/ui/icon';
import { moveIndex, PALETTE_CATEGORIES, PALETTE_LIMIT, toRows } from './search-model';

const DEBOUNCE_MS = 160;

/**
 * Bảng tìm nhanh Ctrl+K (API C3): gõ để tìm API, màn hình, nút, bảng dữ liệu của Workspace đang mở; ↑ ↓ chọn, Enter mở, Esc
 * đóng. Kết quả nhóm theo loại. Chọn một kết quả thì phát `picked`; việc mở đúng thành phần trên cây do màn hình chính làm.
 */
@Component({
  selector: 'app-search-palette',
  imports: [Icon],
  template: `
    <div class="ovl" (mousedown)="backdrop($event)">
      <div class="cmd" role="dialog" aria-modal="true" aria-label="Tìm kiếm nhanh">
        <div class="ci">
          <app-icon name="search" />
          <input #box type="text" placeholder="Tìm API, màn hình, nút, bảng dữ liệu…" autocomplete="off" spellcheck="false"
            aria-label="Từ khóa tìm kiếm" [value]="query()" (input)="onInput($event)" (keydown)="onKey($event)" />
          <kbd>Esc</kbd>
        </div>
        <div class="cr" role="listbox">
          @if (loading()) {
            <div class="skel" style="margin: 12px"></div>
            <div class="skel" style="margin: 12px; width: 70%"></div>
          } @else if (error()) {
            <div class="errb" style="margin: 12px">{{ error() }}</div>
          } @else if (!query().trim()) {
            <div class="hint pad">Gõ để tìm. Dùng ↑ ↓ để chọn, Enter để mở.</div>
          } @else if (!view().items.length) {
            <div class="hint pad">Không có kết quả cho "{{ query().trim() }}".</div>
          } @else {
            @for (row of view().rows; track $index) {
              @if ('header' in row) {
                <div class="cg">{{ row.header }}</div>
              } @else {
                <div class="ck" role="option" [class.on]="row.index === index()" [attr.aria-selected]="row.index === index()"
                  tabindex="-1" (mouseenter)="index.set(row.index)" (click)="pick(row.item.hit)" (keydown.enter)="pick(row.item.hit)">
                  <span class="ico"><app-icon [name]="row.item.icon" [small]="true" /></span>
                  <div class="txt">
                    <div class="tt">{{ row.item.title }}</div>
                    @if (row.item.subtitle) {
                      <div class="hint">{{ row.item.subtitle }}</div>
                    }
                  </div>
                </div>
              }
            }
            @if (view().more) {
              <div class="hint pad">Còn {{ view().more }} kết quả khác — gõ cụ thể hơn để thu hẹp.</div>
            }
          }
        </div>
      </div>
    </div>
  `,
  styles: `
    .ovl { align-items: flex-start; }
    .cmd { width: min(620px, 94vw); background: #fff; border-radius: 14px; box-shadow: var(--sh-xl); overflow: hidden; margin-top: 12vh; }
    .ci { display: flex; align-items: center; gap: 10px; padding: 0 16px; box-shadow: inset 0 -1px 0 var(--bd); }
    .ci input { flex: 1; height: 52px; border: 0; outline: 0; font: 16px var(--font); min-width: 0; }
    .ci kbd { font: 11px var(--mono); background: #f3f4f6; border-radius: 4px; padding: 1px 5px; box-shadow: inset 0 -1px 0 #d1d5db; color: var(--t2); }
    .cr { max-height: 50vh; overflow: auto; padding: 6px; }
    .cg { font-size: 11.5px; font-weight: 700; color: var(--t4); padding: 8px 10px 4px; text-transform: uppercase; }
    .ck { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; cursor: pointer; }
    .ck.on { background: var(--brand-l); }
    .ico { color: var(--t3); display: grid; flex: none; }
    .txt { min-width: 0; }
    .tt { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .txt .hint { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .pad { padding: 16px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SearchPalette implements OnDestroy {
  private readonly api = inject(GraphApi);
  private readonly box = viewChild<ElementRef<HTMLInputElement>>('box');

  readonly workspaceId = input.required<string>();
  readonly picked = output<SearchHitDto>();
  readonly closed = output<void>();

  protected readonly query = signal('');
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<SearchDto | null>(null);
  protected readonly index = signal(0);
  protected readonly view = computed(() => {
    const dto = this.result();
    const shown = toRows(dto);
    const more = PALETTE_CATEGORIES.reduce((n, c) => {
      const g = (dto?.groups ?? []).find((x) => x.category === c);
      return n + Math.max(0, (g?.total ?? 0) - (g?.hits?.length ?? 0));
    }, 0);
    return { ...shown, more };
  });

  private timer: ReturnType<typeof setTimeout> | undefined;
  private token = 0;

  constructor() {
    afterNextRender(() => this.box()?.nativeElement.focus());
  }

  ngOnDestroy(): void {
    clearTimeout(this.timer);
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    this.closed.emit();
  }

  protected backdrop(e: MouseEvent): void {
    if ((e.target as HTMLElement).classList.contains('ovl')) {
      this.closed.emit();
    }
  }

  protected onInput(e: Event): void {
    const q = (e.target as HTMLInputElement).value;
    this.query.set(q);
    clearTimeout(this.timer);
    const mine = ++this.token;
    this.error.set(null);
    if (!q.trim()) {
      this.result.set(null);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.timer = setTimeout(() => void this.search(q, mine), DEBOUNCE_MS);
  }

  protected onKey(e: KeyboardEvent): void {
    const count = this.view().items.length;
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault();
      this.index.set(moveIndex(this.index(), e.key === 'ArrowDown' ? 1 : -1, count));
    } else if (e.key === 'Enter') {
      const item = this.view().items[this.index()];
      if (item) {
        e.preventDefault();
        this.pick(item.hit);
      }
    }
  }

  protected pick(hit: SearchHitDto): void {
    this.picked.emit(hit);
  }

  private async search(q: string, mine: number): Promise<void> {
    try {
      const dto = await this.api.search(this.workspaceId(), q.trim(), PALETTE_CATEGORIES, PALETTE_LIMIT);
      if (mine === this.token) {
        this.result.set(dto);
        this.index.set(0);
      }
    } catch (e) {
      if (mine === this.token) {
        this.result.set(null);
        this.error.set(toApiError(e).message);
      }
    } finally {
      if (mine === this.token) {
        this.loading.set(false);
      }
    }
  }
}
