import { describe, expect, it } from 'vitest';
import { extractFile } from '../../src/adapters/angular/extract/extract-file.js';
import type { TemplateFacts, TsFacts } from '../../src/adapters/angular/facts.js';

const HASH = 'a'.repeat(64);
const ts = async (content: string, path = 'src/x.ts'): Promise<TsFacts> => (await extractFile({ path, hash: HASH, content })) as TsFacts;
const html = async (content: string): Promise<TemplateFacts> =>
  (await extractFile({ path: 'src/x.html', hash: HASH, content })) as TemplateFacts;

describe('extract — template', () => {
  it('lấy sự kiện, lời gọi theo thứ tự, phép gán và nhãn', async () => {
    const facts = await html(`<button id="b1" (click)="onDelete(item.id); show = false">{{ t('remove') }}</button>`);
    const button = facts.nodes[0]!;
    expect(button.tag).toBe('button');
    expect(button.attrs['id']).toBe('b1');
    expect(button.events[0]).toMatchObject({ name: 'click', writes: ['show'] });
    expect(button.events[0]!.calls).toEqual([{ path: [], method: 'onDelete', order: 0 }]);
    expect(button.label).toEqual({ kind: 'key', value: 'remove' });
  });

  it('chữ tĩnh và pipe translate', async () => {
    const facts = await html(`<button (click)="a()">Lưu lại</button><a (click)="b()">{{ 'x.y' | translate }}</a>`);
    expect(facts.nodes[0]!.label).toEqual({ kind: 'text', value: 'Lưu lại' });
    expect(facts.nodes[1]!.label).toEqual({ kind: 'pipe', value: 'x.y' });
  });

  it('nhãn là biểu thức điều kiện giữa hai khóa dịch: lấy nhánh không phải trạng thái đang xử lý', async () => {
    const facts = await html(
      `<button (click)="a()">{{ isSubmitting ? t('login_submitting') : t('login_button') }}</button>` +
        `<button (click)="b()">{{ !loading ? t('save') : t('loading_text') }}</button>` +
        `<button (click)="c()">{{ ok ? t('yes') : t('no') }}</button>`
    );
    expect(facts.nodes[0]!.label).toEqual({ kind: 'key', value: 'login_button' });
    expect(facts.nodes[1]!.label).toEqual({ kind: 'key', value: 'save' });
    expect(facts.nodes[2]!.label).toEqual({ kind: 'key', value: 'yes' }); // không nhánh nào là "đang xử lý": lấy nhánh đầu
  });

  it('@if / @for / *ngIf trong suốt; phần tử không đáng giữ bị gộp', async () => {
    const facts = await html(
      `<div class="wrap"><span>chữ</span>@if (ok) { <p><button (click)="go()">Đi</button></p> }<ul *ngIf="x"><li><button (click)="del(1)">Xóa</button></li></ul></div>`
    );
    expect(facts.nodes.map((n) => n.tag)).toEqual(['button', 'button']);
  });

  it('nút gửi form và nhãn từ thẻ label bao ngoài', async () => {
    const facts = await html(
      `<form [formGroup]="f" (ngSubmit)="save()"><label>Mã <input formControlName="code" /></label><button>Lưu</button></form>`
    );
    const form = facts.nodes[0]!;
    expect(form.events[0]!.name).toBe('ngSubmit');
    expect(form.children.map((c) => c.tag)).toEqual(['input', 'button']);
    expect(form.children[0]!.label).toEqual({ kind: 'text', value: 'Mã' });
  });

  it('nhãn của ô nhập: <label> đứng ngay trước, hoặc placeholder dạng t(khóa) || chữ', async () => {
    const facts = await html(
      `<label>Tài khoản</label><shared-input formControlName="user"></shared-input>` +
        `<shared-input formControlName="pw" [placeholder]="t('col_password') || 'Mật khẩu'"></shared-input>`
    );
    expect(facts.nodes[0]!.label).toEqual({ kind: 'text', value: 'Tài khoản' });
    expect(facts.nodes[1]!.label).toEqual({ kind: 'key', value: 'col_password' });
  });

  it('tiêu đề template', async () => {
    const facts = await html(`<h2>Thêm danh mục</h2><button (click)="a()">x</button>`);
    expect(facts.heading).toEqual({ kind: 'text', value: 'Thêm danh mục' });
  });

  it('template sai cú pháp không ném lỗi', async () => {
    const facts = await html(`<div (click)="a(">`);
    expect(Array.isArray(facts.nodes)).toBe(true);
  });
});

describe('extract — TypeScript', () => {
  it('class, field inject(), constructor @Inject, method và lời gọi theo thứ tự thực thi', async () => {
    const facts = await ts(`
      @Injectable()
      export class A {
        private readonly repo = inject(REPO);
        constructor(@Inject(TOKEN) private readonly t: Thing, private http: HttpClient) {}
        run(id: number) { this.repo.save(this.other(id)); }
        other(id: number) { return id; }
      }`);
    const cls = facts.classes[0]!;
    expect(cls.decorator).toBe('Injectable');
    expect(cls.fields.map((f) => [f.name, f.injectArg, f.typeName])).toEqual([
      ['repo', 'REPO', undefined],
      ['t', 'TOKEN', 'Thing'],
      ['http', undefined, 'HttpClient']
    ]);
    const run = cls.methods.find((m) => m.name === 'run')!;
    expect(run.calls.map((c) => c.method)).toEqual(['other', 'save']); // đối số chạy trước lời gọi bao ngoài
    expect(run.calls[1]).toMatchObject({ root: 'this', path: ['repo'] });
  });

  it('HttpClient: phương thức, URL ghép từ template literal và tham số', async () => {
    const facts = await ts(`
      class R {
        private http = inject(HttpClient);
        remove(id: number) { return this.http.delete(\`\${BASE}/items/\${id}\`); }
        add(body: ItemInput) { return this.http.post('/items', body); }
      }`);
    const [remove, add] = facts.classes[0]!.methods;
    expect(remove!.httpCalls[0]).toMatchObject({ httpMethod: 'DELETE' });
    expect(remove!.httpCalls[0]!.url).toEqual([
      { t: 'ref', path: ['BASE'] },
      { t: 'lit', v: '/items/' },
      { t: 'param', name: 'id' }
    ]);
    expect(add!.httpCalls[0]).toMatchObject({ httpMethod: 'POST', bodyType: 'ItemInput' });
    expect(remove!.calls).toEqual([]);
  });

  it('mở hộp thoại và chuyển trang; window.open không bị nhận là hộp thoại', async () => {
    const facts = await ts(`
      class C {
        private dialog = inject(MatDialog);
        private router = inject(Router);
        a() { this.dialog.open(FormComponent, {}); }
        b(id: number) { this.router.navigate(['/orders', id]); }
        c() { window.open(url); }
      }`);
    const [a, b, c] = facts.classes[0]!.methods;
    expect(a!.uiActions).toMatchObject([{ action: 'OPEN_DIALOG', target: 'FormComponent' }]);
    expect(b!.uiActions).toMatchObject([{ action: 'NAVIGATE', target: '/orders/{id}' }]);
    expect(c!.uiActions).toEqual([]);
  });

  it('receiver là biến gán từ lời gọi, nối chuỗi, biến có kiểu, new, await', async () => {
    const facts = await ts(`
      class C {
        private facade = inject(Facade);
        a() { const repo = this.facade.getRepo(); repo.save(); }
        b() { this.facade.getRepo().save(); }
        c(item: Item) { item.toDto(); }
        d() { const x = new Helper(); x.run(); }
        e() { const data = await this.facade.load(); data.refresh(); }
      }`);
    const [a, b, c, d, e] = facts.classes[0]!.methods;
    expect(a!.calls[1]).toMatchObject({ root: 'repo', method: 'save', via: { root: 'this', path: ['facade'], method: 'getRepo' } });
    expect(b!.calls[1]).toMatchObject({ root: null, method: 'save', via: { root: 'this', path: ['facade'], method: 'getRepo' } });
    expect(c!.calls[0]).toMatchObject({ root: 'item', rootType: 'Item', method: 'toDto' });
    expect(d!.calls[0]).toMatchObject({ root: 'x', rootType: 'Helper', method: 'run' });
    expect(e!.calls[1]).toMatchObject({ root: 'data', method: 'refresh', via: { method: 'load', awaited: true } });
  });

  it('lời gọi trong khởi tạo field gộp vào constructor (tạo constructor giả khi không khai báo)', async () => {
    const facts = await ts(`class C { private s = inject(Svc); readonly list = this.s.load(); other = 1; }`);
    const ctor = facts.classes[0]!.methods.find((m) => m.kind === 'constructor')!;
    expect(ctor.calls.map((c) => c.method)).toEqual(['load']);
  });

  it('afterClosed$ và if lồng bên trong: lời gọi vẫn mang branch afterClosed', async () => {
    const facts = await ts(`
      class C {
        private dialog = inject(DialogService);
        private facade = inject(Facade);
        open() {
          const ref = this.dialog.open(Form, {});
          ref.afterClosed$.subscribe((ok) => { if (ok) { this.facade.load(); } });
          this.facade.other();
        }
      }`);
    const calls = facts.classes[0]!.methods[0]!.calls;
    expect(calls.find((c) => c.method === 'load')!.guard).toMatchObject({ branch: 'afterClosed', text: 'sau khi hộp thoại đóng — ok' });
    expect(calls.find((c) => c.method === 'other')!.guard).toBeUndefined();
  });

  it('route: component, loadComponent, loadChildren và children', async () => {
    const facts = await ts(`
      export const routes: Routes = [
        { path: '', redirectTo: 'a', pathMatch: 'full' },
        { path: 'a', title: 'Trang A', loadComponent: () => import('./a').then((m) => m.AComponent) },
        { path: 'b', loadChildren: () => import('./b.routes').then((m) => m.B_ROUTES), children: [{ path: 'c', component: C }] }
      ];`);
    const array = facts.routeArrays[0]!;
    expect(array).toMatchObject({ name: 'routes', exported: true });
    expect(array.routes[1]).toMatchObject({ path: 'a', title: 'Trang A', loadComponent: { importPath: './a', exportName: 'AComponent' } });
    expect(array.routes[2]!.loadChildren).toEqual({ importPath: './b.routes', exportName: 'B_ROUTES' });
    expect(array.routes[2]!.children![0]).toMatchObject({ path: 'c', component: 'C' });
  });

  it('provider, token và hằng số', async () => {
    const facts = await ts(`
      export const T = new InjectionToken<Repo>('Repo');
      export const P = [{ provide: T, useExisting: HttpRepo }];
      export const env = { apiBase: '/api', nested: { v: 'x' } };`);
    expect(facts.tokens[0]).toMatchObject({ name: 'T', typeName: 'Repo' });
    expect(facts.providers[0]).toMatchObject({ provide: 'T', useExisting: 'HttpRepo' });
    expect(facts.constants.map((c) => c.name)).toEqual(expect.arrayContaining(['env.apiBase', 'env.nested.v']));
    expect(facts.dictionaries).toEqual([]);
  });

  it('từ điển tiếng Việt nhận theo tên file / tên hằng, khóa lồng được làm phẳng', async () => {
    const facts = await ts(`export const catVi = { create: 'Thêm', group: { save: 'Lưu' } } as const;`, 'src/language/cat.vi.ts');
    expect(facts.dictionaries[0]!.entries).toEqual({ create: 'Thêm', 'group.save': 'Lưu' });
  });

  it('file sai cú pháp được ghi vào parseErrors, không ném lỗi', async () => {
    const facts = await ts(`class { ??? `);
    expect(facts.parseErrors.length).toBeGreaterThan(0);
    expect(facts.parseErrors[0]!.message).toContain('Lỗi cú pháp');
  });
});
