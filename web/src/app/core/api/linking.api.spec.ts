import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { LinkingApi } from './linking.api';

const ok = <T>(data: T, message = 'Thành công') => ({ status: 200, code: 'OK', message, data });

describe('LinkingApi', () => {
  let api: LinkingApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(LinkingApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('E1 kèm start_key (thành phần xuất phát của luồng) để có từ khớp tên của ứng viên', async () => {
    const result = api.edge('w1', 'e1', 'ng:element:a b');
    const req = http.expectOne((r) => r.url === '/api/workspaces/w1/edges/e1' && r.params.get('start_key') === 'ng:element:a b');
    req.flush(ok({ edgeId: 'e1', candidates: [{ nodeKey: 'k', hints: ['most'] }] }));
    expect((await result).candidates?.[0].hints).toEqual(['most']);
  });

  it('E1 lấy chi tiết liên kết theo edgeRef', async () => {
    const result = api.edge('w1', 'e1');
    const req = http.expectOne('/api/workspaces/w1/edges/e1');
    expect(req.request.method).toBe('GET');
    req.flush(ok({ edgeId: 'e1', confidence: 'INFERRED' }));
    expect((await result).confidence).toBe('INFERRED');
  });

  it('E4 chốt tay, E5 bỏ chốt', async () => {
    const created = api.createOverride('w1', { fromNodeKey: 'h', toNodeKey: 'api', edgeKind: 'HTTP_REQUEST', decision: 'PIN' });
    const post = http.expectOne('/api/workspaces/w1/link-overrides');
    expect(post.request.method).toBe('POST');
    expect(post.request.body).toEqual({ fromNodeKey: 'h', toNodeKey: 'api', edgeKind: 'HTTP_REQUEST', decision: 'PIN' });
    post.flush({ status: 201, code: 'CREATED', message: 'Đã chốt tay liên kết', data: { id: 'o1' } });
    expect((await created).id).toBe('o1');

    const removed = api.deleteOverride('w1', 'o1');
    const del = http.expectOne('/api/workspaces/w1/link-overrides/o1');
    expect(del.request.method).toBe('DELETE');
    del.flush(ok(null));
    await removed;
  });

  it('E7 trả cả thông điệp "Đã nối lại N liên kết"', async () => {
    const saved = api.saveRules('w1', { manual: [{ type: 'PREFIX', pattern: '{apiUrl}', replacement: '/api/v1' }] });
    const req = http.expectOne('/api/workspaces/w1/link-rules');
    expect(req.request.method).toBe('PUT');
    req.flush(ok({ detected: [], manual: [], relink: { changed: 3 } }, 'Đã nối lại 3 liên kết'));
    const result = await saved;
    expect(result.message).toBe('Đã nối lại 3 liên kết');
    expect(result.rules.relink?.changed).toBe(3);
  });
});
