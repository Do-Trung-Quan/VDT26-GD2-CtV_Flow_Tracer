import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PagerComponent } from '@shop/shared';
import { ProductListComponent } from './product-list.component';
import { ProductRoutingModule } from './product-routing.module';

@NgModule({
  declarations: [ProductListComponent],
  imports: [CommonModule, ProductRoutingModule, PagerComponent]
})
export class ProductModule {}
