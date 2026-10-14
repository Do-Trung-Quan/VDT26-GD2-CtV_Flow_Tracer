import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { App } from './app';

describe('App', () => {
  it('khung ứng dụng: header có chọn Workspace và nút Import', async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    TestBed.inject(HttpTestingController)
      .expectOne('/api/workspaces')
      .flush({ status: 200, code: 'OK', message: 'Thành công', data: [{ id: 'w1', name: 'Shop', sourceCount: 0, sources: [] }] });
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.logo b')?.textContent).toBe('CtV');
    expect(el.querySelector('.topbar')?.textContent).toBe('Flow Tracer');

    // dropdown chọn Workspace theo thiết kế riêng (không dùng <select> mặc định)
    expect(el.querySelector('select')).toBeNull();
    (el.querySelector('app-select-menu .trg') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect([...el.querySelectorAll('app-select-menu .pi')].map((o) => o.textContent?.trim())).toEqual(['Shop', 'Danh sách Workspace']);
    (document.body as HTMLElement).click();
    fixture.detectChanges();

    // nút 3 gạch mở rộng / thu gọn menu bên trái
    const rail = el.querySelector('nav.rail') as HTMLElement;
    expect(rail.classList.contains('wide')).toBe(false);
    (el.querySelector('.hbtn.icon') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(rail.classList.contains('wide')).toBe(true);
    expect([...rail.querySelectorAll('.ri span')].map((o) => o.textContent?.trim())).toContain('Danh sách Workspace');
    (el.querySelector('.hbtn.icon') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(rail.classList.contains('wide')).toBe(false);

    (el.querySelector('.hbtn.solid') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('app-import-wizard')).not.toBeNull();
  });
});
