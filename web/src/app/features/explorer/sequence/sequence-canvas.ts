import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostListener,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild
} from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';
import { toApiError } from '../../../core/api/api-error';
import { TraceApi } from '../../../core/api/trace.api';
import { Icon } from '../../../core/ui/icon';
import { Modal } from '../../../core/ui/modal';
import { ToastService } from '../../../core/ui/toast.service';
import { createMeasure, downloadBlob, fileBase, svgToPng } from './sequence-export';
import { layoutSequence } from './sequence-layout';
import type { SequenceTrace, Step } from './sequence-model';
import { unresolvedSteps } from './sequence-model';
import { COLORS, renderSequenceSvg } from './sequence-svg';

const MIN_ZOOM = 0.4;
const MAX_ZOOM = 2;
const MIN_AUTO_FIT = 0.85;
const ZOOM_STEP = 0.1;
const DRAG_THRESHOLD = 4;

/** Nội dung mô tả khi di chuột vào một mũi tên. */
export interface StepTip {
  title: string;
  lines: string[];
  x: number;
  y: number;
}

/**
 * Canvas sequence diagram: vẽ luồng đã dò (API D1), zoom / kéo / vừa khung, chú giải, thẻ cảnh báo bước chưa xác định,
 * tô sáng mũi tên, mô tả khi di chuột và xuất PlantUML / SVG / PNG (D2). Cụm nút trạng thái quét nằm ở trang chứa.
 */
@Component({
  selector: 'app-sequence-canvas',
  imports: [Icon, Modal],
  template: `
    <div #scroll class="scroll" tabindex="0" role="group" aria-label="Sequence diagram" [class.dragging]="dragging()"
      (pointerdown)="startDrag($event)" (click)="onClick($event)" (keydown.escape)="highlight.set(null)"
      (mouseover)="onHover($event)" (focus)="tip.set(null)" (mousemove)="onHover($event)" (mouseleave)="tip.set(null)"
      (wheel)="onWheel($event)">
      @if (loading()) {
        <div class="sk">
          <div class="skel" style="height: 40px; width: 80%"></div>
          @for (i of [0, 1, 2, 3, 4, 5, 6, 7]; track i) {
            <div class="skel" [style.width.%]="85 - i * 6" style="height: 10px; margin: 22px 0"></div>
          }
        </div>
      } @else if (error()) {
        <div class="errb msg">{{ error() }}</div>
      } @else if (layout(); as l) {
        @if (l.steps.length) {
          <div class="pad" [innerHTML]="svg()"></div>
        } @else {
          <div class="empty center">
            <div class="ic"><app-icon name="layout" /></div>
            <h3>Chưa dựng được luồng</h3>
            <span>{{ trace()?.warnings?.[0] ?? 'API này chưa có hàm xử lý trong mã nguồn.' }}</span>
          </div>
        }
      } @else {
        <div class="empty center">
          <div class="ic"><app-icon name="layout" /></div>
          <h3>{{ emptyTitle() }}</h3>
          <span>{{ emptyText() }}</span>
        </div>
      }
    </div>

    @if (layout() && !loading() && !error() && layout()!.steps.length) {
      @if (unresolved().length) {
        <div class="alertbar">
          <app-icon name="alert" [small]="true" />{{ unresolved().length }} bước chưa xác định
          <button class="link" type="button" (click)="jumpToUnresolved()">Xem</button>
        </div>
      }
      <div class="fab bl" [class.min]="legendCollapsed()">
        <div class="lgh">
          Chú giải
          <button class="ibtn" type="button" [attr.aria-label]="legendCollapsed() ? 'Mở chú giải' : 'Thu gọn chú giải'"
            (click)="legendCollapsed.set(!legendCollapsed())">
            <app-icon [name]="legendCollapsed() ? 'chev' : 'minus'" [small]="true" />
          </button>
        </div>
        @if (!legendCollapsed()) {
          <span><svg width="26" height="8"><line x1="0" y1="4" x2="26" y2="4" [attr.stroke]="colors.CERTAIN" stroke-width="2"/></svg> Chắc chắn</span>
          <span><svg width="26" height="8"><line x1="0" y1="4" x2="26" y2="4" [attr.stroke]="colors.INFERRED" stroke-width="2"/></svg> Suy luận</span>
          <span><svg width="26" height="8"><line x1="0" y1="4" x2="26" y2="4" [attr.stroke]="colors.PIN" stroke-width="2"/></svg> Chốt tay</span>
          <span><svg width="26" height="8"><line x1="0" y1="4" x2="26" y2="4" [attr.stroke]="colors.UNRESOLVED" stroke-width="2" stroke-dasharray="2 4"/></svg> Chưa xác định</span>
          <span><svg width="26" height="8"><line x1="0" y1="4" x2="26" y2="4" [attr.stroke]="colors.CERTAIN" stroke-width="1.4" stroke-dasharray="5 4"/></svg> Trả về</span>
        }
      </div>
      <div class="fab br">
        <button class="ibtn" type="button" aria-label="Thu nhỏ" title="Thu nhỏ" (click)="zoomBy(-ZOOM_STEP)"><app-icon name="minus" [small]="true" /></button>
        <span class="pct">{{ percent() }}%</span>
        <button class="ibtn" type="button" aria-label="Phóng to" title="Phóng to" (click)="zoomBy(ZOOM_STEP)"><app-icon name="plus" [small]="true" /></button>
        <button class="ibtn" type="button" aria-label="Vừa khung" title="Vừa khung" (click)="fit()"><app-icon name="fit" [small]="true" /></button>
        <span class="vsep"></span>
        <div class="xwrap">
          <button class="btn sm" type="button" aria-haspopup="menu" [attr.aria-expanded]="menuOpen()" (click)="toggleMenu($event)">
            <app-icon name="download" [small]="true" />Xuất <app-icon name="chevd" [small]="true" />
          </button>
          @if (menuOpen()) {
            <div class="menu up" role="menu">
              <button class="mi" type="button" role="menuitem" (click)="exportPlantUml()">
                <app-icon name="code" [small]="true" />Mã PlantUML<span class="k">xem &amp; sao chép</span>
              </button>
              <button class="mi" type="button" role="menuitem" (click)="exportSvg()">
                <app-icon name="image" [small]="true" />Ảnh SVG<span class="k">.svg</span>
              </button>
              <button class="mi" type="button" role="menuitem" (click)="exportPng()">
                <app-icon name="image" [small]="true" />Ảnh PNG<span class="k">.png</span>
              </button>
            </div>
          }
        </div>
      </div>
    }

    @if (tip(); as t) {
      <div class="tip" [style.left.px]="t.x + 14" [style.top.px]="t.y + 14">
        <b>{{ t.title }}</b>
        @for (line of t.lines; track $index) {
          <br />{{ line }}
        }
      </div>
    }

    @if (plantUml(); as code) {
      <app-modal title="Mã PlantUML" [large]="true" (closed)="plantUml.set(null)">
        <p class="hint first">Dán vào bất kỳ trình xem PlantUML nào để vẽ lại diagram.</p>
        <pre class="code">{{ code }}</pre>
        <ng-container footer>
          <button class="btn" type="button" (click)="plantUml.set(null)">Đóng</button>
          <button class="btn pri" type="button" (click)="copy(code)"><app-icon name="copy" [small]="true" />Sao chép</button>
        </ng-container>
      </app-modal>
    }
  `,
  styles: `
    :host { display: block; position: absolute; inset: 0; }
    .scroll { position: absolute; inset: 0; overflow: auto; cursor: grab; }
    .scroll.dragging { cursor: grabbing; user-select: none; }
    .pad { padding: 72px 24px 80px; min-width: min-content; }
    .sk { padding: 90px 40px; }
    .msg { margin: 90px 40px; }
    .center { height: 100%; cursor: default; }
    .fab { position: absolute; z-index: 8; background: #fff; border-radius: 12px; box-shadow: var(--sh-md), var(--ring); padding: 5px; display: flex; align-items: center; gap: 4px; }
    .fab.br { bottom: 14px; right: 14px; }
    .fab.bl { bottom: 14px; left: 14px; padding: 6px 10px 8px; gap: 3px; font-size: 12px; color: var(--t2); flex-direction: column; align-items: flex-start; }
    .fab.bl.min { padding: 4px 6px 4px 10px; }
    .lgh { display: flex; align-items: center; gap: 6px; width: 100%; font-weight: 700; color: var(--t3); font-size: 11.5px; text-transform: uppercase; letter-spacing: 0.3px; }
    .lgh .ibtn { width: 24px; height: 24px; margin-left: auto; }
    .vsep { width: 1px; height: 22px; background: var(--bd); margin: 0 3px; }
    .pct { min-width: 44px; text-align: center; font-size: 12.5px; font-weight: 600; }
    .xwrap { position: relative; }
    .menu.up { bottom: calc(100% + 6px); right: 0; min-width: 230px; }
    .mi .k { margin-left: auto; color: var(--t4); font-size: 12px; }
    .alertbar {
      position: absolute; top: 18px; right: 14px; z-index: 7; background: var(--amber-l); color: var(--amber); border-radius: 999px;
      padding: 6px 14px; font-size: 13px; box-shadow: var(--sh-md), inset 0 0 0 1px #fde68a; display: flex; gap: 10px; align-items: center; white-space: nowrap;
    }
    .tip {
      position: fixed; z-index: 400; background: #111827; color: #fff; font-size: 12.5px; padding: 8px 10px; border-radius: 8px;
      max-width: 320px; pointer-events: none; box-shadow: var(--sh-lg); overflow-wrap: anywhere;
    }
    .tip b { color: #fca5a5; }
    .first { margin-top: 0; }
    .code { margin: 0; max-height: 55vh; overflow: auto; background: #f9fafb; border-radius: 8px; padding: 12px; font: 12.5px/1.5 var(--mono); box-shadow: var(--ring); white-space: pre; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SequenceCanvas {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly traceApi = inject(TraceApi);
  private readonly toast = inject(ToastService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly scroll = viewChild.required<ElementRef<HTMLElement>>('scroll');
  private readonly measure = createMeasure();

  readonly workspaceId = input.required<string>();
  readonly trace = input<SequenceTrace | null>(null);
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly emptyTitle = input('Chọn một API ở cột trái');
  readonly emptyText = input('');

  protected readonly colors = COLORS;
  protected readonly ZOOM_STEP = ZOOM_STEP;
  protected readonly zoom = signal(1);
  protected readonly highlight = signal<number | null>(null);
  protected readonly legendCollapsed = signal(false);
  protected readonly menuOpen = signal(false);
  protected readonly tip = signal<StepTip | null>(null);
  protected readonly plantUml = signal<string | null>(null);
  protected readonly dragging = signal(false);

  protected readonly layout = computed(() => {
    const t = this.trace();
    return t ? layoutSequence(t, this.measure) : null;
  });
  protected readonly unresolved = computed(() => {
    const t = this.trace();
    return t ? unresolvedSteps(t) : [];
  });
  protected readonly percent = computed(() => Math.round(this.zoom() * 100));
  protected readonly svg = computed(() => {
    const l = this.layout();
    return l ? this.sanitizer.bypassSecurityTrustHtml(renderSequenceSvg(l, { zoom: this.zoom(), highlight: this.highlight(), measure: this.measure })) : '';
  });

  private suppressClick = false;

  constructor() {
    // Luồng mới: bỏ tô sáng, tự vừa khung (không nhỏ hơn 85%)
    effect(() => {
      const l = this.layout();
      untracked(() => {
        this.highlight.set(null);
        this.tip.set(null);
        this.zoom.set(l ? this.autoZoom(l.width) : 1);
      });
    });
  }

  protected zoomBy(delta: number): void {
    this.zoom.set(clampZoom(this.zoom() + delta));
  }

  protected fit(): void {
    const l = this.layout();
    if (l) {
      const room = (this.host.nativeElement.clientWidth || 800) - 48;
      this.zoom.set(Math.max(MIN_ZOOM, Math.min(1.2, round2(room / l.width))));
    }
  }

  private autoZoom(width: number): number {
    const available = this.host.nativeElement.clientWidth || 0;
    return available > 0 && width + 48 > available ? Math.max(MIN_AUTO_FIT, round2((available - 48) / width)) : 1;
  }

  protected onWheel(e: WheelEvent): void {
    if (e.ctrlKey && this.layout()) {
      e.preventDefault();
      this.zoomBy(e.deltaY < 0 ? ZOOM_STEP : -ZOOM_STEP);
    }
  }

  // ---------------- kéo để cuộn ----------------

  protected startDrag(e: PointerEvent): void {
    if (e.button !== 0 || (e.target as Element).closest('.arrow')) {
      return;
    }
    const el = this.scroll().nativeElement;
    const start = { x: e.clientX, y: e.clientY, left: el.scrollLeft, top: el.scrollTop };
    let moved = false;
    const move = (ev: PointerEvent) => {
      const dx = ev.clientX - start.x;
      const dy = ev.clientY - start.y;
      if (!moved && Math.hypot(dx, dy) < DRAG_THRESHOLD) {
        return;
      }
      moved = true;
      this.dragging.set(true);
      el.scrollLeft = start.left - dx;
      el.scrollTop = start.top - dy;
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
      this.dragging.set(false);
      this.suppressClick = moved;
      setTimeout(() => (this.suppressClick = false));
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  }

  // ---------------- tô sáng, mô tả ----------------

  protected onClick(e: MouseEvent): void {
    if (this.suppressClick) {
      return;
    }
    const n = this.stepNumberAt(e.target);
    this.highlight.set(n === null || n === this.highlight() ? null : n);
  }

  protected onHover(e: MouseEvent): void {
    const n = this.stepNumberAt(e.target);
    const step = n === null ? undefined : this.trace()?.steps.find((s) => s.index === n);
    this.tip.set(step ? describe(step, e.clientX, e.clientY) : null);
  }

  private stepNumberAt(target: EventTarget | null): number | null {
    const g = target instanceof Element ? target.closest('.arrow') : null;
    const n = g ? Number(g.getAttribute('data-n')) : NaN;
    return Number.isFinite(n) ? n : null;
  }

  protected jumpToUnresolved(): void {
    const first = this.unresolved()[0];
    if (!first) {
      return;
    }
    this.highlight.set(first.index);
    queueMicrotask(() => {
      const arrow = this.host.nativeElement.querySelector(`.arrow[data-n="${first.index}"]`);
      arrow?.scrollIntoView({ block: 'center', inline: 'center', behavior: 'smooth' });
    });
  }

  // ---------------- xuất ----------------

  protected toggleMenu(e: Event): void {
    e.stopPropagation();
    this.menuOpen.set(!this.menuOpen());
  }

  @HostListener('document:click')
  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  /** D2: Engine sinh mã PlantUML (cùng quy tắc cột / bước với canvas). */
  protected async exportPlantUml(): Promise<void> {
    const t = this.trace();
    if (!t) {
      return;
    }
    try {
      const result = await this.traceApi.export(this.workspaceId(), t.traceId, 'plantuml');
      this.plantUml.set(result.content ?? '');
    } catch (e) {
      this.toast.show(toApiError(e).message || 'Không xuất được.', 'err');
    }
  }

  /** SVG giữ nguyên như đang thấy trên canvas (không tô sáng, zoom 100%). */
  protected exportSvg(): void {
    const t = this.trace();
    const text = this.plainSvg();
    if (t && text) {
      downloadBlob(`${fileBase(t)}.svg`, new Blob([text], { type: 'image/svg+xml' }));
      this.toast.show('Đã tải file SVG.', 'ok');
    }
  }

  protected async exportPng(): Promise<void> {
    const t = this.trace();
    const text = this.plainSvg();
    if (!t || !text) {
      return;
    }
    this.toast.show('Đang tạo ảnh PNG…');
    try {
      downloadBlob(`${fileBase(t)}.png`, await svgToPng(text));
    } catch (e) {
      this.toast.show(e instanceof Error ? e.message : 'Không xuất được PNG.', 'err');
    }
  }

  protected async copy(text: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.toast.show('Đã sao chép.', 'ok');
    } catch {
      this.toast.show('Trình duyệt không cho sao chép; hãy chọn và sao chép thủ công.', 'warn');
    }
  }

  private plainSvg(): string | null {
    const l = this.layout();
    return l ? renderSequenceSvg(l, { zoom: 1, highlight: null, measure: this.measure }) : null;
  }
}

function describe(step: Step, x: number, y: number): StepTip {
  const level = step.pinned ? 'Chốt tay' : step.confidence === 'INFERRED' ? 'Suy luận' : step.confidence === 'UNRESOLVED' ? 'Chưa xác định' : 'Chắc chắn';
  const lines: string[] = [];
  if (step.reason) {
    lines.push(step.reason);
  } else if (step.error) {
    lines.push('Nhánh lỗi: ngoại lệ nghiệp vụ lan lên từ nơi phát sinh.');
  } else if (step.returning) {
    lines.push('Chiều trả về kết quả.');
  } else if (step.from === step.to) {
    lines.push('Bước tự xử lý: gom các lời gọi hàm trong cùng một lớp.');
  } else {
    lines.push('Tham chiếu rõ ràng và duy nhất trong mã nguồn.');
  }
  if (step.repeatOf !== null) {
    lines.push(`Lần gọi lặp lại: phần bên trong xem ở bước ${step.repeatOf}.`);
  }
  return { title: `Bước ${step.index} · ${level}`, lines, x, y };
}

function clampZoom(value: number): number {
  return round2(Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value)));
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}
