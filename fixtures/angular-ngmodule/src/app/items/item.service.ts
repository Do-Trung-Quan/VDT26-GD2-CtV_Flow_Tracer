import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ItemService {
  constructor(private http: HttpClient) {}

  list() {
    return this.http.get('/api/v1/items');
  }

  remove(id: number) {
    return this.http.delete(`/api/v1/items/${id}`);
  }
}
