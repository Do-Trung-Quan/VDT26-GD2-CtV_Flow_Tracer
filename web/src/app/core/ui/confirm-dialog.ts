import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Modal } from './modal';

/** Hộp xác nhận (vd xóa Workspace / gỡ nguồn mã). */
@Component({
  selector: 'app-confirm-dialog',
  imports: [Modal],
  template: `
    <app-modal [title]="title()" (closed)="cancelled.emit()">
      <ng-content />
      <ng-container footer>
        <button class="btn" type="button" (click)="cancelled.emit()">Hủy</button>
        <button
          class="btn"
          type="button"
          [class.pri]="!danger()"
          [class.danger]="danger()"
          [disabled]="busy()"
          (click)="confirmed.emit()"
        >
          {{ busy() ? 'Đang xử lý…' : okText() }}
        </button>
      </ng-container>
    </app-modal>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ConfirmDialog {
  readonly title = input.required<string>();
  readonly okText = input('Đồng ý');
  readonly danger = input(false);
  readonly busy = input(false);
  readonly confirmed = output<void>();
  readonly cancelled = output<void>();
}
