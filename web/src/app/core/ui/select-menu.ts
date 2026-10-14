import { ChangeDetectionStrategy, Component, ElementRef, HostListener, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { Icon, type IconName } from './icon';

/** Một lựa chọn của {@link SelectMenu}. */
export interface SelectOption {
  value: string;
  label: string;
}

/** Một lệnh thêm ở cuối danh sách (vd "Danh sách Workspace"), không phải một giá trị. */
export interface MenuAction {
  id: string;
  label: string;
  icon?: IconName;
}

/**
 * Dropdown chọn một giá trị theo thiết kế của bản vẽ (không dùng `<select>` mặc định của trình duyệt): nút bấm mở danh sách thả
 * nổi — mục đang chọn có nhãn nhỏ hoặc dấu tích, rê chuột / phím ↑ ↓ Enter chọn, Esc hoặc bấm ra ngoài thì đóng. Hai kiểu hiện:
 * {@code header} (nút mờ trên thanh đỏ) và {@code field} (ô nhập trong biểu mẫu).
 */
@Component({
  selector: 'app-select-menu',
  imports: [Icon],
  template: `
    <button #trigger class="trg" [class.hdr]="variant() === 'header'" [class.fld]="variant() === 'field'" type="button"
      aria-haspopup="listbox" [attr.aria-expanded]="open()" [attr.aria-label]="ariaLabel() || null" [disabled]="disabled()"
      (click)="toggle()" (keydown)="onTriggerKey($event)">
      @if (icon(); as ic) {
        <app-icon [name]="ic" [small]="true" />
      }
      <span class="lbl" [class.ph]="!selected()">{{ selected()?.label ?? placeholder() }}</span>
      <app-icon name="chevd" [small]="true" />
    </button>
    @if (open()) {
      <div class="pop" role="listbox" [style.left.px]="pos().left" [style.top.px]="pos().top" [style.min-width.px]="pos().width">
        @for (o of options(); track o.value; let i = $index) {
          <button class="pi" type="button" role="option" [class.act]="active() === i" [class.cur]="o.value === value()" [attr.aria-selected]="o.value === value()"
            (mouseenter)="active.set(i)" (click)="pick(o.value)">
            <span class="txt">{{ o.label }}</span>
            @if (o.value === value()) {
              @if (currentHint()) {
                <span class="k">{{ currentHint() }}</span>
              } @else {
                <app-icon name="check" [small]="true" />
              }
            }
          </button>
        }
        @if (actions().length) {
          @if (options().length) {
            <div class="pdiv"></div>
          }
          @for (a of actions(); track a.id) {
            <button class="pi" type="button" role="option" [attr.aria-selected]="false" (click)="run(a.id)">
              @if (a.icon) {
                <app-icon [name]="a.icon" [small]="true" />
              }
              <span class="txt">{{ a.label }}</span>
            </button>
          }
        }
      </div>
    }
  `,
  styles: `
    :host { position: relative; display: inline-block; min-width: 0; }
    :host(.block) { display: block; }
    .trg { display: inline-flex; align-items: center; gap: 8px; cursor: pointer; font: inherit; min-width: 0; max-width: 100%; border: 0; text-align: left; }
    .trg:disabled { opacity: 0.55; cursor: not-allowed; }
    .lbl { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; min-width: 0; }
    .lbl.ph { opacity: 0.75; }
    .trg.hdr { height: 34px; padding: 0 12px; border-radius: 8px; background: rgba(255, 255, 255, 0.14); color: #fff; font: 600 13.5px var(--font); max-width: 280px; }
    .trg.hdr:hover, .trg.hdr[aria-expanded='true'] { background: rgba(255, 255, 255, 0.24); }
    .trg.fld { width: 100%; height: 36px; padding: 0 12px; border-radius: 8px; background: #fff; color: var(--t1); box-shadow: var(--ring); justify-content: space-between; }
    .trg.fld:focus-visible, .trg.fld[aria-expanded='true'] { outline: 0; box-shadow: 0 0 0 2px var(--brand); }
    .trg.fld .lbl.ph { color: var(--t4); opacity: 1; }
    .pop { position: fixed; z-index: 400; background: #fff; border-radius: 10px; box-shadow: var(--sh-lg), var(--ring); padding: 6px; max-height: min(360px, calc(100vh - 16px)); max-width: calc(100vw - 16px); overflow: auto; }
    .pi { display: flex; width: 100%; border: 0; background: none; text-align: left; font: inherit; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 6px; cursor: pointer; font-size: 13.5px; color: var(--t2); }
    .pi.act, .pi:hover { background: #f3f4f6; color: var(--t1); }
    .pi.cur { font-weight: 600; color: var(--t1); }
    .pi .txt { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .pi .k { margin-left: auto; color: var(--t4); font-size: 12px; font-weight: 400; }
    .pi app-icon { color: var(--brand); }
    .pi .txt + app-icon { margin-left: auto; }
    .pdiv { height: 1px; background: var(--bd); margin: 6px 0; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SelectMenu {
  readonly options = input<SelectOption[]>([]);
  readonly value = input<string | null>(null);
  readonly placeholder = input('Chọn…');
  readonly variant = input<'header' | 'field'>('field');
  readonly icon = input<IconName | null>(null);
  readonly actions = input<MenuAction[]>([]);
  readonly ariaLabel = input('');
  readonly disabled = input(false);
  /** Nhãn nhỏ bên phải mục đang chọn (vd "đang mở"); trống thì hiện dấu tích. */
  readonly currentHint = input('');

  readonly changed = output<string>();
  readonly action = output<string>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');

  protected readonly open = signal(false);
  protected readonly active = signal(0);
  protected readonly pos = signal({ left: 0, top: 0, width: 0 });
  protected readonly selected = computed(() => this.options().find((o) => o.value === this.value()) ?? null);

  protected toggle(): void {
    if (this.open()) {
      this.close();
      return;
    }
    const rect = this.trigger()?.nativeElement.getBoundingClientRect();
    if (!rect) {
      return;
    }
    this.pos.set({ left: Math.max(8, Math.min(rect.left, window.innerWidth - 240)), top: rect.bottom + 6, width: Math.max(rect.width, 210) });
    this.active.set(Math.max(0, this.options().findIndex((o) => o.value === this.value())));
    this.open.set(true);
  }

  protected close(): void {
    this.open.set(false);
  }

  protected pick(value: string): void {
    this.close();
    if (value !== this.value()) {
      this.changed.emit(value);
    }
  }

  protected run(id: string): void {
    this.close();
    this.action.emit(id);
  }

  protected onTriggerKey(e: KeyboardEvent): void {
    const n = this.options().length;
    if (!this.open()) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        this.toggle();
      }
      return;
    }
    if (e.key === 'ArrowDown' && n) {
      e.preventDefault();
      this.active.set((this.active() + 1) % n);
    } else if (e.key === 'ArrowUp' && n) {
      e.preventDefault();
      this.active.set((this.active() - 1 + n) % n);
    } else if (e.key === 'Enter' && n) {
      e.preventDefault();
      this.pick(this.options()[this.active()].value);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation(); // không đóng luôn hộp thoại chứa nó
      this.close();
    } else if (e.key === 'Tab') {
      this.close();
    }
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(e: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(e.target as Node)) {
      this.close();
    }
  }

  @HostListener('window:resize')
  @HostListener('window:blur')
  protected onViewportChange(): void {
    this.close();
  }
}
