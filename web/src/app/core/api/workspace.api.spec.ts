import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { toApiError } from './api-error';
import { WorkspaceApi } from './workspace.api';

const ok = <T>(data: T) => ({ status: 200, code: 'OK', message: 'Thành công', data });

describe('WorkspaceApi', () => {
  let api: WorkspaceApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(WorkspaceApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('A10 gửi rootPath, A6 gửi path dạng query; kết quả lấy từ data của vỏ bọc', async () => {
    const detect = api.detect('shop');
    const req = http.expectOne('/api/fs/detect');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ rootPath: 'shop' });
    req.flush(ok({ status: 'NOT_FOUND', roots: [] }));
    expect((await detect).status).toBe('NOT_FOUND');

    const browse = api.browse('shop/web');
    http.expectOne((r) => r.url === '/api/fs/browse' && r.params.get('path') === 'shop/web').flush(ok({ path: 'shop/web', folders: [] }));
    expect((await browse).path).toBe('shop/web');
  });

  it('lỗi 409 đọc code, message, errors từ vỏ bọc', async () => {
    const create = api.create({ name: 'Shop' });
    http.expectOne('/api/workspaces').flush(
      { status: 409, code: 'WORKSPACE_NAME_TAKEN', message: 'Tên Workspace đã được dùng', data: null, errors: { name: 'Shop' } },
      { status: 409, statusText: 'Conflict' }
    );
    const err = await create.catch(toApiError);
    expect(err).toEqual({ status: 409, code: 'WORKSPACE_NAME_TAKEN', message: 'Tên Workspace đã được dùng', errors: { name: 'Shop' } });
  });

  it('lỗi validate hiện lý do của từng trường', async () => {
    const create = api.create({ name: ' ' });
    http.expectOne('/api/workspaces').flush(
      { status: 400, code: 'VALIDATION_FAILED', message: 'Dữ liệu gửi lên không hợp lệ', data: null, errors: { name: 'Tên Workspace không được để trống' } },
      { status: 400, statusText: 'Bad Request' }
    );
    const err = await create.then(() => null, toApiError);
    expect(err?.message).toBe('Tên Workspace không được để trống');
  });

  it('Engine không chạy → thông điệp dễ hiểu', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK');
  });
});
