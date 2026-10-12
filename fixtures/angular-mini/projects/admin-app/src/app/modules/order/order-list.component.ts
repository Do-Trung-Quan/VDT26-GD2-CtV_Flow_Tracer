import { Component, inject } from '@angular/core';
import { OrderNoteDialogComponent } from './order-note-dialog.component';
import { OrderService } from './order.service';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [OrderNoteDialogComponent],
  template: `
    <button type="button" (click)="showNote = true">Ghi chú</button>
    @if (showNote) {
      <app-order-note-dialog (closed)="showNote = false"></app-order-note-dialog>
    }
    @if (orders.length === 0) {
      <p>{{ 'order.empty' | translate }}</p>
    }
    <ul>
      @for (order of orders; track order) {
        <li>
          <button type="button" (click)="onConfirm(order)">{{ 'order.confirm' | translate }}</button>
          <button type="button" (click)="onCancel(order)">Hủy đơn</button>
        </li>
      }
    </ul>
  `
})
export class OrderListComponent {
  private readonly service = inject(OrderService);
  orders: number[] = [];
  showNote = false;
  /** Khởi tạo field chạy khi component được tạo: là lời gọi API chạy khi mở màn hình. */
  readonly loaded = this.service.list();

  onConfirm(id: number): void {
    this.service.confirm(id).subscribe();
  }

  onCancel(id: number): void {
    this.service.cancel(id).subscribe();
  }
}
