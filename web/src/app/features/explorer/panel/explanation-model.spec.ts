import { codeLines, highlightJson, inputValue, isOpenable, jsonText, outputValue, scenarioRows } from './explanation-model';

describe('explanation-model', () => {
  it('đầu vào giữ nguyên các phần path / query / body; rỗng nghĩa là API không nhận gì', () => {
    const input = { query: { keyword: 'string' }, body: { code: 'string — bắt buộc' } };
    expect(inputValue({ input })).toEqual(input);
    expect(inputValue({ input: {} })).toBeNull();
    expect(inputValue(undefined)).toBeNull();
  });

  it('đầu ra rỗng nghĩa là API không trả nội dung', () => {
    expect(outputValue({ output: {} })).toBeNull();
    expect(outputValue({ output: { message: 'string' } })).toEqual({ message: 'string' });
  });

  it('tô màu JSON: khóa, chuỗi, số, true/false/null; thoát HTML trong giá trị', () => {
    const html = highlightJson(jsonText({ 'a<b': '<script>', n: 12, ok: true, none: null }));

    expect(html).toContain('<span class="jk">&quot;a&lt;b&quot;</span>:');
    expect(html).toContain('<span class="js">&quot;&lt;script&gt;&quot;</span>');
    expect(html).toContain('<span class="jn">12</span>');
    expect(html).toContain('<span class="jb">true</span>');
    expect(html).toContain('<span class="jb">null</span>');
    expect(html).not.toContain('<script>');
  });

  it('chuỗi chứa dấu nháy kép hoặc số không bị tách thành nhiều đoạn', () => {
    const html = highlightJson(jsonText({ k: 'nói "xin chào" 123' }));

    expect(html).toContain('<span class="js">&quot;nói \\&quot;xin chào\\&quot; 123&quot;</span>');
    expect(html).not.toContain('<span class="jn">');
  });

  it('khung xem code đánh số từ startLine và tô sáng đúng các dòng được chỉ định', () => {
    const lines = codeLines({ startLine: 40, highlightLines: [41], code: 'a\nb\r\nc' });

    expect(lines).toEqual([
      { n: 40, text: 'a', hl: false },
      { n: 41, text: 'b', hl: true },
      { n: 42, text: 'c', hl: false }
    ]);
  });

  it('dòng điều kiện của kịch bản không có số bước', () => {
    const rows = scenarioRows([{ text: 'Nếu x:' }, { index: 9, text: 'Bước chín', depth: 1, unresolved: true }]);

    expect(rows).toEqual([
      { index: null, text: 'Nếu x:', depth: 0, unresolved: false },
      { index: 9, text: 'Bước chín', depth: 1, unresolved: true }
    ]);
  });

  it('chỉ API và thành phần giao diện mới mở được trên màn hình chính', () => {
    expect(isOpenable('API_ENDPOINT')).toBe(true);
    expect(isOpenable('UI_DIALOG')).toBe(true);
    expect(isOpenable('DB_TABLE')).toBe(false);
    expect(isOpenable(undefined)).toBe(false);
  });
});
