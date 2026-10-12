import { Component, EventEmitter, Output, inject } from '@angular/core';
import { OrderService } from './order.service';

/** Hộp thoại đặt thẳng trong template của màn hình đơn hàng (không mở bằng dialog.open). */
@Component({
  selector: 'app-order-note-dialog',
  standalone: true,
  template: `
    <h3>Ghi chú đơn hàng</h3>
    <textarea name="note"></textarea>
    <button type="button" (click)="save()">Lưu ghi chú</button>
    <button type="button" (click)="closed.emit()">Đóng</button>
  `
})
export class OrderNoteDialogComponent {
  private readonly service = inject(OrderService);
  @Output() closed = new EventEmitter<void>();

  save(): void {
    this.service.saveNote('ghi chú').subscribe(() => this.closed.emit());
  }
}
