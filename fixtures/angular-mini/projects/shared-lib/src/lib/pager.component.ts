import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-pager',
  standalone: true,
  template: `
    <nav>
      <button type="button" (click)="previous()">Trang trước</button>
      <button type="button" (click)="next()">Trang sau</button>
    </nav>
  `
})
export class PagerComponent {
  @Input() page = 1;
  @Output() pageChange = new EventEmitter<number>();

  previous(): void {
    this.pageChange.emit(this.page - 1);
  }

  next(): void {
    this.pageChange.emit(this.page + 1);
  }
}
