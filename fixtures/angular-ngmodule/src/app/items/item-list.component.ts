import { Component } from '@angular/core';
import { ItemService } from './item.service';

@Component({ selector: 'app-item-list', templateUrl: './item-list.component.html' })
export class ItemListComponent {
  items: unknown[] = [];

  constructor(private service: ItemService) {}

  reload(): void {
    this.service.list().subscribe((rows) => (this.items = rows as unknown[]));
  }

  remove(id: number): void {
    this.service.remove(id).subscribe(() => this.reload());
  }
}
