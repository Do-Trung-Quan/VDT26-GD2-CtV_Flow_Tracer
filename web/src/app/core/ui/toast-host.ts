import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

@Component({
  selector: 'app-toast-host',
  template: `
    @for (t of toasts.toasts(); track t.id) {
      <div class="toast" [class]="'toast ' + t.kind" role="status">
        <span class="sp1">{{ t.text }}</span>
        <button class="ibtn" type="button" aria-label="Đóng" (click)="toasts.dismiss(t.id)">×</button>
      </div>
    }
  `,
  styles: `
    :host { position: fixed; top: 66px; right: 16px; z-index: 300; display: flex; flex-direction: column; gap: 8px; }
    .toast {
      display: flex; align-items: center; gap: 8px; background: #fff; border-radius: 10px; box-shadow: var(--sh-lg);
      padding: 6px 6px 6px 14px; min-width: 260px; max-width: 420px; border-left: 4px solid var(--blue); font-size: 13.5px;
    }
    .ok { border-color: #16a34a; }
    .err { border-color: var(--brand); }
    .warn { border-color: #d97706; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ToastHost {
  protected readonly toasts = inject(ToastService);
}
