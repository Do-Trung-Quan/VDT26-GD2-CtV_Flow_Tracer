import { Component } from '@angular/core';
import { Router } from '@angular/router';

@Component({
  selector: 'app-product-list',
  standalone: false,
  templateUrl: './product-list.component.html'
})
export class ProductListComponent {
  showFilter = false;
  page = 1;

  constructor(private readonly router: Router) {}

  toggleFilter(): void {
    this.showFilter = !this.showFilter;
  }

  onPage(page: number): void {
    this.page = page;
  }

  goToOrders(): void {
    this.router.navigate(['/orders']);
  }
}
