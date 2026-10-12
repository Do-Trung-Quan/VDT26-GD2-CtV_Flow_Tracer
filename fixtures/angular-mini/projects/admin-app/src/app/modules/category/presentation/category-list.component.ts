import { Component, OnInit, inject } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { CategoryFacade } from '../application/category.facade';
import { CATEGORY_I18N, CategoryI18nKey } from '../language';
import { CategoryFormDialogComponent } from './category-form-dialog.component';

@Component({
  selector: 'app-category-list',
  standalone: true,
  imports: [],
  templateUrl: './category-list.component.html'
})
export class CategoryListComponent implements OnInit {
  readonly facade = inject(CategoryFacade);
  private readonly dialog = inject(MatDialog);

  t(key: CategoryI18nKey): string {
    return CATEGORY_I18N.vi[key];
  }

  ngOnInit(): void {
    void this.facade.load('');
  }

  onSearch(keyword: string): void {
    void this.facade.load(keyword);
  }

  openCreate(): void {
    const ref = this.dialog.open(CategoryFormDialogComponent, { width: '480px' });
    ref.afterClosed().subscribe(() => this.facade.load(''));
  }

  /** Mở hộp thoại bằng hàm riêng; việc tải lại danh sách nằm trong callback `afterClosed$` có `if` lồng bên trong. */
  onEdit(id: number): void {
    this.openForm(id);
  }

  private openForm(id: number): void {
    const ref = this.dialog.open(CategoryFormDialogComponent, { width: '480px', data: id });
    ref.afterClosed$.subscribe((ok: boolean) => {
      if (ok) {
        void this.facade.load('');
      }
    });
  }

  onDelete(id: number): void {
    void this.facade.remove(id);
  }

  onExport(): void {
    void this.facade.exportExcel();
  }

  /** Dùng kết quả của lời gọi qua biến cục bộ: kiểu suy ra từ kiểu trả về đã khai báo của getRepository(). */
  onRefresh(): void {
    const repository = this.facade.getRepository();
    void repository.list('');
  }

  /** Nối chuỗi: this.facade.getRepository().exportExcel(). */
  onDownload(): void {
    void this.facade.getRepository().exportExcel();
  }
}
