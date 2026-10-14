import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ItemListComponent } from './item-list.component';
import { ItemsRoutingModule } from './items-routing.module';

@NgModule({
  declarations: [ItemListComponent],
  imports: [CommonModule, ItemsRoutingModule]
})
export class ItemsModule {}
