import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { CategoryFacade } from '../application/category.facade';
import { CATEGORY_I18N, CategoryI18nKey } from '../language';

@Component({
  selector: 'app-category-form-dialog',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './category-form-dialog.component.html'
})
export class CategoryFormDialogComponent {
  private readonly facade = inject(CategoryFacade);
  private readonly dialogRef = inject(MatDialogRef<CategoryFormDialogComponent>);
  private readonly fb = inject(FormBuilder);

  readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required]
  });

  t(key: CategoryI18nKey): string {
    return CATEGORY_I18N.vi[key];
  }

  async save(): Promise<void> {
    await this.facade.save(this.form.getRawValue());
    this.dialogRef.close(true);
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
