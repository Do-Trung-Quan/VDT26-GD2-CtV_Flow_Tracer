import { TestBed } from '@angular/core/testing';
import type { BackendTreeDto } from '../../core/api/api-types';
import { BackendTree } from './backend-tree';

const tree: BackendTreeDto = {
  moduleCount: 1,
  endpointCount: 2,
  modules: [
    {
      name: 'Danh mục',
      nameSource: 'TAG',
      sourceId: 's1',
      endpointCount: 2,
      endpoints: [
        { nodeKey: 'k1', httpMethod: 'GET', path: '/api/v1/categories', summary: 'Lấy danh sách danh mục', warnings: [] },
        { nodeKey: 'k2', httpMethod: 'POST', path: '/api/v1/categories/import', warnings: [] }
      ]
    }
  ]
};

describe('BackendTree', () => {
  function render(): HTMLElement {
    const fixture = TestBed.createComponent(BackendTree);
    fixture.componentRef.setInput('tree', tree);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.tn')!.click();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('nhãn là đường dẫn (mô tả ngắn nằm ở tooltip); không tự đặt tên', () => {
    const rows = [...render().querySelectorAll('.tn.ep')];

    expect(rows).toHaveLength(2);
    expect(rows[0].querySelector('.nm')?.textContent?.trim()).toBe('/api/v1/categories');
    expect(rows[0].querySelector('.nm')?.classList.contains('mono')).toBe(true);
    expect(rows[0].getAttribute('title')).toBe('Lấy danh sách danh mục');
    expect(rows[1].querySelector('.nm')?.textContent?.trim()).toBe('/api/v1/categories/import');
    expect(rows[1].getAttribute('title')).toBe('/api/v1/categories/import');
  });
});
