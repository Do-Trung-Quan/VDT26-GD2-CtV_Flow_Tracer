import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import { analyzeFixture, FIXTURES } from '../support/fixture.js';

const GOLDEN = path.join(FIXTURES, 'golden', 'angular-mini.ir.json');

describe('golden — fixtures/angular-mini', () => {
  it('IR khớp bản chuẩn (sinh lại bằng UPDATE_GOLDEN=1 chỉ khi thay đổi IR có chủ đích)', async () => {
    const fragment = await analyzeFixture('angular-mini');
    const actual = JSON.stringify(fragment, null, 2) + '\n';
    if (process.env['UPDATE_GOLDEN'] === '1') {
      await mkdir(path.dirname(GOLDEN), { recursive: true });
      await writeFile(GOLDEN, actual, 'utf8');
    }
    expect(actual).toBe(await readFile(GOLDEN, 'utf8'));
  });

  it('chạy hai lần cho cùng kết quả (tất định)', async () => {
    const a = await analyzeFixture('angular-mini');
    const b = await analyzeFixture('angular-mini');
    expect(JSON.stringify(a)).toBe(JSON.stringify(b));
  });
});
