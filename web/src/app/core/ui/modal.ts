import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, OnInit, input, output } from '@angular/core';
import { Icon } from './icon';

/** Các modal đang mở, theo thứ tự; Esc chỉ đóng modal trên cùng (vd trình duyệt thư mục mở từ wizard Import). */
const openStack: Modal[] = [];

/** Khung modal dùng chung: tiêu đề, thân (nội dung chiếu vào), chân (slot [footer]); Esc để đóng. */
@Component({
  selector: 'app-modal',
  imports: [Icon],
  template: `
    <div class="ovl">
      <div class="mdl" [class.lg]="large()" role="dialog" aria-modal="true" [attr.aria-label]="title()">
        <div class="mdl-h">
          <h2>{{ title() }}</h2>
          <button class="ibtn" type="button" aria-label="Đóng" (click)="closed.emit()"><app-icon name="x" /></button>
        </div>
        <div class="mdl-b"><ng-content /></div>
        <div class="mdl-f"><ng-content select="[footer]" /></div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class Modal implements OnInit, OnDestroy {
  readonly title = input.required<string>();
  readonly large = input(false);
  readonly closed = output<void>();

  ngOnInit(): void {
    openStack.push(this);
  }

  ngOnDestroy(): void {
    openStack.splice(openStack.indexOf(this), 1);
  }

  @HostListener('document:keydown.escape')
  onEsc(): void {
    if (openStack.at(-1) === this) {
      this.closed.emit();
    }
  }
}
