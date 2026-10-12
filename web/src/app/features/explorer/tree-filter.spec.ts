import type { ApiModuleDto } from '../../core/api/api-types';
import { filterModules, highlight } from './tree-filter';

const modules: ApiModuleDto[] = [
  {
    name: 'Danh mục',
    nameSource: 'TAG',
    endpointCount: 2,
    endpoints: [
      { nodeKey: 'k1', httpMethod: 'GET', path: '/api/v1/categories', summary: 'Lấy danh sách danh mục' },
      { nodeKey: 'k2', httpMethod: 'POST', path: '/api/v1/categories', summary: 'Tạo danh mục' }
    ]
  },
  {
    name: 'Order',
    nameSource: 'CONTROLLER',
    endpointCount: 1,
    endpoints: [{ nodeKey: 'k3', httpMethod: 'GET', path: '/api/v1/orders' }]
  }
];

describe('filterModules', () => {
  it('từ khóa rỗng → giữ nguyên', () => {
    expect(filterModules(modules, '  ')).toBe(modules);
  });

  it('khớp tên module → giữ mọi endpoint của module', () => {
    const r = filterModules(modules, 'danh MỤC');
    expect(r.map((m) => m.name)).toEqual(['Danh mục']);
    expect(r[0].endpoints).toHaveLength(2);
  });

  it('khớp phương thức + đường dẫn → chỉ giữ endpoint khớp, đếm lại', () => {
    const r = filterModules(modules, 'post /api');
    expect(r).toHaveLength(1);
    expect(r[0].endpoints?.map((e) => e.nodeKey)).toEqual(['k2']);
    expect(r[0].endpointCount).toBe(1);
  });

  it('khớp mô tả', () => {
    expect(filterModules(modules, 'tạo').flatMap((m) => m.endpoints ?? []).map((e) => e.nodeKey)).toEqual(['k2']);
  });

  it('không khớp gì → rỗng', () => {
    expect(filterModules(modules, 'không-có')).toEqual([]);
  });
});

describe('highlight', () => {
  it('tách đúng các đoạn khớp, không phân biệt hoa thường', () => {
    expect(highlight('/api/v1/API', 'api')).toEqual([
      { text: '/', hit: false },
      { text: 'api', hit: true },
      { text: '/v1/', hit: false },
      { text: 'API', hit: true }
    ]);
  });

  it('không có từ khóa → một đoạn', () => {
    expect(highlight('/orders', '')).toEqual([{ text: '/orders', hit: false }]);
  });
});
