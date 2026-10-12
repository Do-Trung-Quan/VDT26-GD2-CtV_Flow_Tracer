import type { SearchDto } from '../../core/api/api-types';
import { moveIndex, toItem, toRows } from './search-model';

const dto: SearchDto = {
  query: 'thêm',
  total: 4,
  groups: [
    {
      category: 'ELEMENT',
      label: 'Phần tử giao diện',
      total: 2,
      hits: [
        { nodeKey: 'e1', kind: 'UI_ELEMENT', name: 'openCreate', displayName: 'Thêm mới', context: 'CategoryListComponent' },
        { nodeKey: 'e2', kind: 'UI_ELEMENT', name: 'shared-input' }
      ]
    },
    {
      category: 'API',
      label: 'API Backend',
      total: 1,
      hits: [{ nodeKey: 'a1', kind: 'API_ENDPOINT', name: 'POST /api/v1/categories', context: 'Danh mục' }]
    },
    { category: 'CODE', label: 'Hàm / lớp', total: 1, hits: [{ nodeKey: 'm1', kind: 'BE_METHOD', name: 'X.y' }] }
  ]
};

describe('bảng lệnh Ctrl+K', () => {
  it('xếp nhóm theo thứ tự của bản vẽ, bỏ nhóm không hiển thị, đánh số liên tục để chọn bằng phím', () => {
    const { rows, items } = toRows(dto);
    expect(rows.map((r) => ('header' in r ? `#${r.header}` : r.item.hit.nodeKey))).toEqual(['#API Backend', 'a1', '#Phần tử giao diện', 'e1', 'e2']);
    expect(items.map((i) => i.hit.nodeKey)).toEqual(['a1', 'e1', 'e2']);
    const indexes = rows.flatMap((r) => ('item' in r ? [r.index] : []));
    expect(indexes).toEqual([0, 1, 2]);
  });

  it('tên ưu tiên nhãn tiếng Việt, dòng phụ là loại · ngữ cảnh', () => {
    const e1 = toItem('ELEMENT', dto.groups![0].hits![0]);
    expect(e1.title).toBe('Thêm mới');
    expect(e1.subtitle).toBe('Phần tử · CategoryListComponent');
    expect(toItem('ELEMENT', dto.groups![0].hits![1])).toMatchObject({ title: 'shared-input', subtitle: 'Phần tử' });
  });

  it('API: tên là "METHOD đường dẫn", mô tả và module ở dòng phụ', () => {
    const api = toItem('API', { kind: 'API_ENDPOINT', name: 'POST /api/v1/categories', displayName: 'Thêm mới danh mục', context: 'Danh mục' });
    expect(api.title).toBe('POST /api/v1/categories');
    expect(api.subtitle).toBe('Thêm mới danh mục · Danh mục');
    expect(toItem('API', { kind: 'API_ENDPOINT', name: 'GET /x' }).subtitle).toBe('API');
  });

  it('không có kết quả thì không có dòng nào', () => {
    expect(toRows(null)).toEqual({ rows: [], items: [] });
  });

  it('phím mũi tên đi vòng', () => {
    expect(moveIndex(0, -1, 3)).toBe(2);
    expect(moveIndex(2, 1, 3)).toBe(0);
    expect(moveIndex(1, 1, 3)).toBe(2);
    expect(moveIndex(5, 1, 0)).toBe(0);
  });
});
