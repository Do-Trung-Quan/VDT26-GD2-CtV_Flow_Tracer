import { Injectable, signal } from '@angular/core';

export type ToastKind = 'ok' | 'err' | 'warn' | 'info';

export interface Toast {
  id: number;
  kind: ToastKind;
  text: string;
}

/** Thông báo nổi góc phải trên, tự tắt sau vài giây. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  show(text: string, kind: ToastKind = 'info', ms = 4500): void {
    const id = ++this.seq;
    this.toasts.update((list) => [...list, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), ms);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
