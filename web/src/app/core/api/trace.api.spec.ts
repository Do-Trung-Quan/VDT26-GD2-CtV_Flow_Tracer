import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { TraceApi } from './trace.api';

const ok = <T>(data: T) => ({ status: 200, code: 'OK', message: 'Thành công', data });

describe('TraceApi', () => {
  let api: TraceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TraceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('D1 gửi startKey trong body, kết quả lấy từ data của vỏ bọc', async () => {
    const result = api.trace('w1', 'java:endpoint:GET /a', 8);
    const req = http.expectOne('/api/workspaces/w1/traces');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ startKey: 'java:endpoint:GET /a', maxDepth: 8 });
    req.flush(ok({ traceId: 't1', steps: [] }));
    expect((await result).traceId).toBe('t1');
  });

  it('D2 gửi định dạng dạng query', async () => {
    const result = api.export('w1', 't1', 'plantuml');
    const req = http.expectOne((r) => r.url === '/api/workspaces/w1/traces/t1/export' && r.params.get('format') === 'plantuml');
    expect(req.request.method).toBe('GET');
    req.flush(ok({ format: 'PLANTUML', fileName: 'a.puml', content: '@startuml' }));
    expect((await result).content).toBe('@startuml');
  });
});
