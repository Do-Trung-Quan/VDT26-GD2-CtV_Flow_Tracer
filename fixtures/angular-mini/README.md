# angular-mini

Dự án Angular nhỏ **tự viết 100%** (miền "Shop") cho bộ phân tích Angular của sidecar (Giai đoạn 4). Không build, không chạy.

| Ca | Nằm ở | Kiểm tra |
| --- | --- | --- |
| Hai ứng dụng + thư viện dùng chung, đường dẫn tắt `@shop/shared` | `angular.json`, `tsconfig.json` | Ứng dụng, thư viện, import qua alias |
| Luật proxy | `projects/admin-app/proxy.conf.json` | `pathRewrite` thành luật `REWRITE` |
| Route kiểu standalone: `loadChildren` trỏ file routes, `loadComponent`, `redirectTo` | `app.routes.ts`, `category.routes.ts` | Màn hình, đường dẫn đầy đủ |
| Route kiểu NgModule: `RouterModule.forChild` | `product.module.ts`, `product-routing.module.ts` | Màn hình, template tách file |
| Template tách file, template viết trong `.ts`, cú pháp `@if` / `@for` và `*ngIf` | `category-list`, `order-list`, `product-list` | Phần tử có sự kiện |
| Form, ô nhập, nút `Lưu` / `Hủy` | `category-form-dialog` | `UI_FORM`, `fields[]` |
| Hộp thoại mở bằng `dialog.open(Component)` | `category-list.component.ts` | `UI_DIALOG`, cạnh `OPENS` |
| Tab (`mat-tab-group`) | `product-list.component.html` | `UI_TAB` |
| Thành phần con dùng chung có sự kiện `(pageChange)` | `shared-lib/.../pager.component.ts` | Phần tử của thành phần con nằm trong màn hình chứa nó |
| Nút chỉ đổi giao diện (`showFilter = !showFilter`), nút chuyển trang bằng `Router` | `product-list` | Luồng "không gọi máy chủ" |
| Facade → `InjectionToken` → `useExisting` → repository HTTP | `category.facade.ts`, `category.providers.ts` | Cạnh `CALLS`, `SENDS`, mức tin cậy |
| URL ghép từ `environment`, hằng số thư viện và tham số | `http-category.repository.ts`, `order.service.ts` | `urlPattern` |
| Hàm vòng đời và khởi tạo field gọi API khi mở màn hình | `category-list.component.ts` (`ngOnInit`), `order-list.component.ts` (`readonly loaded = this.service.list()`) | Cạnh `TRIGGERS` từ màn hình tới hàm vòng đời |
| Biến cục bộ và lời gọi nối chuỗi dùng kiểu trả về khai báo (`getRepository()`) | `category-list.component.ts` (`onRefresh`, `onDownload`) | Cạnh `CALLS` `INFERRED`; nút chỉ có icon → không có nhãn |
| Nhãn từ điển `t('khóa')` (`*.vi.ts`) và `\| translate` (`assets/i18n/vi.json`) | `language/`, `assets/i18n` | Nhãn tiếng Việt |
