# angular-ngmodule — mini fixture cho ứng dụng Angular dùng NgModule

Tự viết (miền "Shop"), không biên dịch. Mô phỏng ứng dụng kiểu cũ: `AppModule` import **trực tiếp** `ItemsModule` (nạp sớm) có `RouterModule.forChild`, còn `ReportsModule` nạp lười qua `loadChildren`.

| Ca | Kết quả mong đợi |
| :--- | :--- |
| `ItemsRoutingModule` (forChild) của module import trực tiếp | màn hình `/items` nằm ở gốc, không có tiền tố |
| `ReportsModule` nạp lười, route `''` | màn hình `/reports` (một lần), **không** thêm một màn hình `/` nữa |
| `forRoot` có route `''` và `**` | `/` và `/**` |
