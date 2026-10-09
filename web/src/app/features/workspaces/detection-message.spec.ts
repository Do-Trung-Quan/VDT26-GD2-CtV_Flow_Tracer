import { describeDetection } from './detection-message';

const be = { path: 'shop/backend', role: 'BACKEND' as const, stackName: 'Spring Boot', version: '3.3.3', evidence: 'pom.xml có spring-boot 3.3.3' };
const fe = { path: 'shop/web', role: 'FRONTEND' as const, stackName: 'Angular', version: '21.0.0', evidence: 'angular.json + @angular/core ^21.0.0' };

describe('describeDetection — 4 trường hợp nhận diện thư mục', () => {
  it('thư mục không liên quan → cảnh báo ngắn, không thêm gì', () => {
    const o = describeDetection('notes', { status: 'NOT_FOUND', roots: [], legacyHints: [], warnings: [] }, []);
    expect(o.fresh).toEqual([]);
    expect(o.note?.lines).toEqual(['Không tìm thấy dự án Spring Boot hoặc Angular trong notes.']);
  });

  it('thư mục con trong dự án → gợi ý dùng gốc', () => {
    const o = describeDetection('shop/backend/src', { status: 'INSIDE_PROJECT', suggestedRoot: be, roots: [] }, []);
    expect(o.suggested).toBe('shop/backend');
    expect(o.note?.lines[0]).toBe('shop/backend/src nằm bên trong dự án shop/backend.');
  });

  it('thư mục chứa cả BE và FE → tự tách 2 nguồn, không hiện thông báo', () => {
    const o = describeDetection('shop', { status: 'VALID', roots: [be, fe], legacyHints: [], warnings: [] }, []);
    expect(o.fresh).toEqual([be, fe]);
    expect(o.note).toBeUndefined();
  });

  it('gốc đã có trong danh sách → không thêm trùng', () => {
    expect(describeDetection('shop', { status: 'VALID', roots: [be, fe] }, [be]).fresh).toEqual([fe]);
    expect(describeDetection('shop', { status: 'VALID', roots: [be] }, [be]).note?.kind).toBe('info');
  });

  it('chỉ báo khi phát hiện mã cũ, kèm thư mục chứa nó', () => {
    const o = describeDetection('shop', {
      status: 'VALID',
      roots: [be],
      legacyHints: [{ path: 'shop/backend/docs/old-shop', language: 'PHP', fileCount: 120 }],
      warnings: []
    }, []);
    expect(o.fresh).toEqual([be]);
    expect(o.note?.lines).toEqual(['Phát hiện mã PHP (có thể là hệ thống cũ) trong shop/backend/docs/old-shop.']);
  });
});
