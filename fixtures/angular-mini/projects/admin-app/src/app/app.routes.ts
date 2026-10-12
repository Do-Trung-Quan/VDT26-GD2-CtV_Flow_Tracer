import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'categories', pathMatch: 'full' },
  {
    path: 'categories',
    loadChildren: () => import('./modules/category/category.routes').then((m) => m.CATEGORY_ROUTES)
  },
  {
    path: 'products',
    loadChildren: () => import('./modules/product/product.module').then((m) => m.ProductModule)
  },
  {
    path: 'orders',
    title: 'Đơn hàng',
    loadComponent: () => import('./modules/order/order-list.component').then((m) => m.OrderListComponent)
  }
];
