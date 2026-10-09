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
    expect(el.querySelector('.topbar')?.textContent).toContain('CtV Flow Tracer');
    expect([...el.querySelectorAll('#wsSelect option')].map((o) => o.textContent?.trim())).toContain('Shop');

    (el.querySelector('.hbtn.solid') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('app-import-wizard')).not.toBeNull();
  });
});
