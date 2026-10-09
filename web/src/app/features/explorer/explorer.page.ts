import { ChangeDetectionStrategy, Component } from '@angular/core';

/** Nhóm chức năng 3, 4, 5, 6 — TODO: cột trái (cây BE/FE), canvas sequence, cột phải (Diễn giải / Kịch bản) */
@Component({
  selector: 'app-explorer',
  template: `<section class="page"><h1>Màn hình chính</h1><p class="sub">Cây API / cây thành phần và sequence diagram có từ Giai đoạn 2–3, sau khi quét mã nguồn.</p></section>`,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExplorerPage {}
