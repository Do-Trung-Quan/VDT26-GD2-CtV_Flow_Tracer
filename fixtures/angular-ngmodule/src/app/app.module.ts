import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule } from '@angular/common/http';
import { AppComponent } from './app.component';
import { AppRoutingModule } from './app-routing.module';
import { HomeComponent } from './home.component';
import { ItemsModule } from './items/items.module';

// NgModule nạp sớm: ItemsModule được import trực tiếp nên route `RouterModule.forChild` của nó nằm ở GỐC cấu hình route
@NgModule({
  declarations: [AppComponent, HomeComponent],
  imports: [BrowserModule, HttpClientModule, ItemsModule, AppRoutingModule],
  bootstrap: [AppComponent]
})
export class AppModule {}
