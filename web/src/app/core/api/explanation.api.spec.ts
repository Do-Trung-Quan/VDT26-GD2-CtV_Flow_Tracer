import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ExplanationApi, encodeNodeKey } from './explanation.api';

const ok = <T>(data: T) => ({ status: 200, code: 'OK', message: 'Thành công', data });
const KEY = 'ng:element:admin-app/CategoryFormComponent#button.click.onSave';

describe('ExplanationApi', () => {
  let api: ExplanationApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ExplanationApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('khóa node trên đường dẫn được mã hóa hai lần', () => {
    expect(encodeNodeKey('a/b#c')).toBe('a%252Fb%2523c');
  });

  it('F1, F2, C4, C5 gọi đúng đường dẫn và lấy dữ liệu trong vỏ bọc', async () => {
    const enc = encodeNodeKey(KEY);

    const explanation = api.explanation('w1', KEY);
    http.expectOne(`/api/workspaces/w1/nodes/${enc}/explanation`).flush(ok({ nodeKey: KEY, container: false }));
    expect((await explanation).nodeKey).toBe(KEY);

    const mock = api.mockData('w1', KEY, 'api:key');
    const mockReq = http.expectOne((r) => r.url === `/api/workspaces/w1/nodes/${enc}/mock-data`);
    expect(mockReq.request.params.get('apiKey')).toBe('api:key');
    mockReq.flush(ok({ apiKey: 'api:key', source: 'RULE' }));
    expect((await mock).source).toBe('RULE');

    const detail = api.nodeDetail('w1', KEY);
    http.expectOne(`/api/workspaces/w1/nodes/${enc}`).flush(ok({ nodeKey: KEY }));
    expect((await detail).nodeKey).toBe(KEY);

    const source = api.source('w1', KEY, 3);
    const sourceReq = http.expectOne((r) => r.url === `/api/workspaces/w1/nodes/${enc}/source`);
    expect(sourceReq.request.params.get('context_lines')).toBe('3');
    sourceReq.flush(ok([{ title: 'Template' }]));
    expect(await source).toHaveLength(1);
  });

  it('F3 lấy kịch bản theo traceId; C6 gửi openapiUrl hoặc để trống', async () => {
    const scenario = api.scenario('w1', 't1');
    http.expectOne('/api/workspaces/w1/traces/t1/scenario').flush(ok({ traceId: 't1', items: [] }));
    expect((await scenario).traceId).toBe('t1');

    const byUrl = api.openApiCheck('w1', 'http://localhost:8080/v3/api-docs');
    const urlReq = http.expectOne('/api/workspaces/w1/openapi-check');
    expect(urlReq.request.method).toBe('POST');
    expect(urlReq.request.body).toEqual({ openapiUrl: 'http://localhost:8080/v3/api-docs' });
    urlReq.flush(ok({ source: 'x' }));
    await byUrl;

    const inRepo = api.openApiCheck('w1');
    const repoReq = http.expectOne('/api/workspaces/w1/openapi-check');
    expect(repoReq.request.body).toEqual({});
    repoReq.flush(ok({ source: 'openapi.yaml' }));
    await inRepo;
  });
});
