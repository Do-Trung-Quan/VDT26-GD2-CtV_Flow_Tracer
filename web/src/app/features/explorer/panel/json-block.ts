import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, input } from '@angular/core';
import { ToastService } from '../../../core/ui/toast.service';
import { highlightJson, jsonText } from './explanation-model';

/** Khối JSON nền tối, tô màu cú pháp, có nút "Sao chép" góc phải trên (như bản vẽ giao diện). */
@Component({
  selector: 'app-json-block',
  template: `<pre class="jcode"><button class="cp" type="button" (click)="copy()">Sao chép</button><code [innerHTML]="html()"></code></pre>`,
  styles: `
    app-json-block { display: block; }
    app-json-block .jcode {
      background: #0f172a; color: #e2e8f0; border-radius: 8px; padding: 10px 12px; font: 12.5px/1.55 var(--mono); overflow: auto;
      position: relative; white-space: pre; margin: 0; max-height: 360px;
    }
    app-json-block .jcode code { font: inherit; }
    app-json-block .cp {
      position: sticky; float: right; top: 0; right: 0; border: 0; background: rgba(255, 255, 255, 0.12); color: #e2e8f0;
      border-radius: 6px; font-size: 11.5px; padding: 3px 8px; cursor: pointer; margin: -4px -6px 0 8px;
    }
    app-json-block .cp:hover { background: rgba(255, 255, 255, 0.25); }
    app-json-block .jk { color: #93c5fd; }
    app-json-block .js { color: #86efac; }
    app-json-block .jn { color: #fcd34d; }
    app-json-block .jb { color: #f9a8d4; }
  `,
  encapsulation: ViewEncapsulation.None,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class JsonBlock {
  private readonly toast = inject(ToastService);

  readonly value = input.required<unknown>();

  protected readonly text = computed(() => jsonText(this.value()));
  protected readonly html = computed(() => highlightJson(this.text()));

  protected async copy(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.text());
      this.toast.show('Đã sao chép vào bộ nhớ tạm.', 'ok', 1800);
    } catch {
      this.toast.show('Trình duyệt không cho sao chép; hãy chọn và sao chép thủ công.', 'warn');
    }
  }
}
