import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { API_BASE } from '@shop/shared';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly http = inject(HttpClient);
  private readonly resource = `${API_BASE}/orders`;

  list() {
    return this.http.get<number[]>(this.resource);
  }

  saveNote(text: string) {
    return this.http.post(`${this.resource}/note`, { text });
  }

  confirm(id: number) {
    return this.http.put(`${this.resource}/${id}/confirm`, {});
  }

  cancel(id: number) {
    return this.http.delete(this.resource + '/' + id);
  }
}
