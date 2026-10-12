import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { Ajv2020 } from 'ajv/dist/2020.js';
import { beforeAll, describe, expect, it } from 'vitest';
import type { IrEdge, IrFragment, IrNode } from '../../src/protocol/generated/ir-fragment.js';
import { analyzeFixture, REPO_ROOT } from '../support/fixture.js';

let ir: IrFragment;
const node = (key: string): IrNode => ir.nodes.find((n) => n.key === key)!;
const edges = (from: string, kind: string): IrEdge[] => ir.edges.filter((e) => e.fromKey === from && e.kind === kind);
const E = 'ng:element:admin-app/';

beforeAll(async () => {
  ir = await analyzeFixture('angular-mini');
});

describe('resolve — fixtures/angular-mini', () => {
  it('IrFragment khớp contracts/ir-fragment.schema.json', async () => {
    const schema = JSON.parse(await readFile(path.join(REPO_ROOT, 'contracts', 'ir-fragment.schema.json'), 'utf8'));
    const validate = new Ajv2020({ allErrors: true }).compile(schema);
    expect(validate(ir)).toBe(true);
    expect(validate.errors ?? []).toEqual([]);
  });

  it('một ứng dụng; thư viện dùng chung không thành ứng dụng', () => {
    expect(ir.nodes.filter((n) => n.kind === 'UI_APP').map((n) => n.key)).toEqual(['ng:app:admin-app']);
  });

  it('màn hình từ route: loadChildren (file routes và NgModule) và loadComponent', () => {
    const screens = ir.nodes.filter((n) => n.kind === 'UI_SCREEN');
    expect(screens.map((n) => [n.key, n.displayName])).toEqual([
      ['ng:screen:admin-app/categories', 'Danh mục'],
      ['ng:screen:admin-app/orders', 'Đơn hàng'],
      ['ng:screen:admin-app/products', 'Sản phẩm']
    ]);
  });

  it('nút Thêm mới chỉ mở hộp thoại (không gửi HTTP); nhãn lấy từ từ điển', () => {
    const create = node(`${E}CategoryListComponent#id.btn-create`);
    expect(create.displayName).toBe('Thêm mới');
    expect(create.attrs).toMatchObject({ elementType: 'button', event: 'click', i18nKey: 'create', handlers: ['openCreate'] });
    const open = edges('ng:method:admin-app/CategoryListComponent#openCreate', 'OPENS');
    expect(open.map((e) => e.toKey)).toEqual(['ng:dialog:admin-app/CategoryFormDialogComponent']);
    expect(
      ir.edges.some(
        (e) =>
          e.kind === 'CONTAINS' &&
          e.fromKey === 'ng:screen:admin-app/categories' &&
          e.toKey === 'ng:dialog:admin-app/CategoryFormDialogComponent'
      )
    ).toBe(true);
  });

  it('lời gọi trong callback afterClosed của hộp thoại mang điều kiện afterClosed', () => {
    const reload = edges('ng:method:admin-app/CategoryListComponent#openCreate', 'CALLS');
    expect(reload).toHaveLength(1);
    expect(reload[0]!.guard).toMatchObject({ type: 'IF', branch: 'afterClosed' });
  });

  it('hàm chạy khi component được tạo (ngOnInit, khởi tạo field) nối từ màn hình tới lời gọi API', () => {
    expect(edges('ng:screen:admin-app/categories', 'TRIGGERS').map((e) => e.toKey)).toEqual([
      'ng:method:admin-app/CategoryListComponent#ngOnInit'
    ]);
    const orders = edges('ng:screen:admin-app/orders', 'TRIGGERS');
    expect(orders.map((e) => e.toKey)).toEqual(['ng:method:admin-app/OrderListComponent#constructor']);
    expect(orders[0]!.reason).toContain('Chạy khi OrderListComponent được tạo');
    expect(edges('ng:method:admin-app/OrderListComponent#constructor', 'CALLS').map((e) => e.toKey)).toEqual([
      'ng:method:admin-app/OrderService#list'
    ]);
    expect(node('ng:http:admin-app/OrderService#list:0').attrs).toMatchObject({ httpMethod: 'GET', urlPattern: '/gateway/api/v1/orders' });
    // hộp thoại có constructor chỉ gọi thư viện (FormBuilder) thì không có cạnh vòng đời
    expect(edges('ng:dialog:admin-app/CategoryFormDialogComponent', 'TRIGGERS')).toEqual([]);
  });

  it('biến cục bộ và lời gọi nối chuỗi được giải bằng kiểu trả về đã khai báo (INFERRED vì interface có 1 lớp hiện thực)', () => {
    for (const method of ['onRefresh', 'onDownload']) {
      const calls = edges(`ng:method:admin-app/CategoryListComponent#${method}`, 'CALLS');
      const repo = calls.find((e) => e.toKey?.startsWith('ng:method:admin-app/HttpCategoryRepository#'));
      expect(repo, method).toBeDefined();
      expect(repo!.confidence).toBe('INFERRED');
      expect(repo!.reason).toContain('getRepository() là CategoryRepository');
    }
  });

  it('phần tử không có chữ: không có displayName, giữ tên hàm xử lý và loại phần tử để làm tên dự phòng', () => {
    const refresh = node(`${E}CategoryListComponent#button.click.onRefresh`);
    expect(refresh.displayName).toBeUndefined();
    expect(refresh.name).toBe('onRefresh');
    expect(refresh.attrs).toMatchObject({ elementType: 'button', handlers: ['onRefresh'] });
    expect(node(`${E}CategoryFormDialogComponent#input.code`).attrs).toMatchObject({ control: 'code' });
  });

  it('callback afterClosed$ có if lồng bên trong vẫn mang điều kiện afterClosed; hộp thoại mở qua hàm riêng', () => {
    const reload = edges('ng:method:admin-app/CategoryListComponent#openForm', 'CALLS').filter((e) => e.toKey?.endsWith('CategoryFacade#load'));
    expect(reload).toHaveLength(1);
    expect(reload[0]!.guard).toMatchObject({ branch: 'afterClosed', text: 'sau khi hộp thoại đóng — ok' });
    expect(edges('ng:method:admin-app/CategoryListComponent#openForm', 'OPENS').map((e) => e.toKey)).toEqual([
      'ng:dialog:admin-app/CategoryFormDialogComponent'
    ]);
    // nút Sửa → onEdit → openForm: tới được hộp thoại qua hàm trung gian
    expect(edges('ng:method:admin-app/CategoryListComponent#onEdit', 'CALLS').map((e) => e.toKey)).toEqual([
      'ng:method:admin-app/CategoryListComponent#openForm'
    ]);
  });

  it('nút gửi là component bọc có type="submit" vẫn là phần tử và kích hoạt ngSubmit', () => {
    const save = node(`${E}CategoryFormDialogComponent#app-button.submit`);
    expect(save.displayName).toBe('Lưu');
    expect(edges(save.key, 'TRIGGERS').map((e) => e.toKey)).toEqual(['ng:method:admin-app/CategoryFormDialogComponent#save']);
  });

  it('hộp thoại đặt thẳng trong template là MỘT hộp thoại chứa phần tử của nó, không gộp phẳng vào màn hình', () => {
    const dialog = node('ng:dialog:admin-app/OrderNoteDialogComponent');
    expect(dialog).toMatchObject({ kind: 'UI_DIALOG', displayName: 'Ghi chú đơn hàng', attrs: { hosted: true } });
    const parentOf = (key: string) => ir.edges.filter((e) => e.kind === 'CONTAINS' && e.toKey === key).map((e) => e.fromKey);
    expect(parentOf(dialog.key)).toEqual(['ng:screen:admin-app/orders']);
    expect(parentOf(`${E}OrderNoteDialogComponent#button.click.save`)).toEqual([dialog.key]);
    expect(ir.nodes.some((n) => n.key.includes('app-order-note-dialog'))).toBe(false); // thẻ chủ không thành phần tử
  });

  it('nút Xóa → facade → InjectionToken → repository HTTP, mức CERTAIN kèm căn cứ provider', () => {
    const calls = edges('ng:method:admin-app/CategoryFacade#remove', 'CALLS').filter((e) => e.toKey?.endsWith('HttpCategoryRepository#remove'));
    expect(calls).toHaveLength(1);
    expect(calls[0]).toMatchObject({ confidence: 'CERTAIN' });
    expect(calls[0]!.reason).toContain('Provider CATEGORY_REPOSITORY → HttpCategoryRepository');
    const http = node('ng:http:admin-app/HttpCategoryRepository#remove:0');
    expect(http.attrs).toMatchObject({ httpMethod: 'DELETE', urlPattern: '/gateway/api/v1/categories/{id}', urlResolved: true });
  });

  it('URL ghép từ hằng số thư viện (alias @shop/shared) và field của class', () => {
    expect(node('ng:http:admin-app/OrderService#confirm:0').attrs).toMatchObject({
      httpMethod: 'PUT',
      urlPattern: '/gateway/api/v1/orders/{id}/confirm'
    });
  });

  it('form: nút gửi không có (click) vẫn kích hoạt ngSubmit; ô nhập mang nhãn của <label>', () => {
    const form = node('ng:form:admin-app/CategoryFormDialogComponent#form.ngSubmit.save');
    expect(form.attrs).toMatchObject({
      fields: [
        { name: 'code', controlType: 'input' },
        { name: 'name', controlType: 'input' }
      ]
    });
    const submit = edges(`${E}CategoryFormDialogComponent#app-button.submit`, 'TRIGGERS');
    expect(submit.map((e) => e.toKey)).toEqual(['ng:method:admin-app/CategoryFormDialogComponent#save']);
    expect(node(`${E}CategoryFormDialogComponent#input.code`).displayName).toBe('Mã danh mục');
  });

  it('tab, thành phần con dùng chung, nút chỉ đổi giao diện và nút chuyển trang', () => {
    expect(ir.nodes.filter((n) => n.kind === 'UI_TAB').map((n) => n.displayName)).toEqual(['Danh sách', 'Tồn kho']);
    const tab = 'ng:tab:admin-app/ProductListComponent#Danh_sách';
    expect(ir.edges.filter((e) => e.kind === 'CONTAINS' && e.fromKey === tab).map((e) => e.toKey)).toContain(
      `${E}PagerComponent#button.click.next`
    );
    // toggleFilter chỉ gán biến: không có CALLS / SENDS
    expect(ir.edges.filter((e) => e.fromKey === 'ng:method:admin-app/ProductListComponent#toggleFilter')).toEqual([]);
    expect(edges('ng:method:admin-app/ProductListComponent#goToOrders', 'NAVIGATES').map((e) => e.toKey)).toEqual([
      'ng:screen:admin-app/orders'
    ]);
  });

  it('template inline dùng @if/@for và nhãn pipe translate (assets/i18n/vi.json)', () => {
    expect(node(`${E}OrderListComponent#button.click.onConfirm`).displayName).toBe('Xác nhận đơn');
    expect(node(`${E}OrderListComponent#button.click.onCancel`).displayName).toBe('Hủy đơn');
  });

  it('luật proxy tự phát hiện; không có lỗi phân tích (file .spec.ts không được đọc)', () => {
    expect(ir.detectedLinkRules).toMatchObject([{ type: 'REWRITE', pattern: '^/gateway', replacement: '', origin: 'AUTO' }]);
    expect(ir.diagnostics.filter((d) => d.severity === 'ERROR')).toEqual([]);
  });

  it('mọi cạnh có vị trí hoặc lý do; mọi đầu mút tồn tại', () => {
    const keys = new Set(ir.nodes.map((n) => n.key));
    for (const e of ir.edges) {
      expect(e.location !== undefined || e.reason !== undefined).toBe(true);
      expect(keys.has(e.fromKey)).toBe(true);
      if (e.toKey) {
        expect(keys.has(e.toKey)).toBe(true);
      }
    }
  });
});
