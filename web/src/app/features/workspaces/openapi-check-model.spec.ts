import type { OpenApiCheckDto } from '../../core/api/api-types';
import { openApiRows } from './openapi-check-model';

describe('openApiRows', () => {
  it('liệt kê API chỉ có trong mã trước, rồi API chỉ có trong Swagger, kèm gợi ý', () => {
    const result: OpenApiCheckDto = {
      source: 'shop/openapi.yaml',
      specVersion: 'OpenAPI 3.0.3',
      codeTotal: 4,
      specTotal: 3,
      matched: [{ method: 'GET', path: '/a' }],
      onlyInCode: [{ method: 'GET', path: '/api/v1/categories/export' }],
      onlyInSpec: [{ method: 'DELETE', path: '/api/v1/legacy' }]
    };

    const rows = openApiRows(result);

    expect(rows.map((r) => [r.side, r.api, r.pill])).toEqual([
      ['CODE', 'GET /api/v1/categories/export', 'Chỉ trong mã'],
      ['SPEC', 'DELETE /api/v1/legacy', 'Chỉ trong Swagger']
    ]);
    expect(rows[0].hint).toContain('lỗi thời');
    expect(rows[1].hint).toContain('bỏ sót');
  });

  it('không có dòng nào khi hai phía khớp hoàn toàn', () => {
    expect(openApiRows({ matched: [{ method: 'GET', path: '/a' }] })).toEqual([]);
  });
});
