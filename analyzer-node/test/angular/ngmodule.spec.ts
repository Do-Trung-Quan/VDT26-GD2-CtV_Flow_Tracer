import { beforeAll, describe, expect, it } from 'vitest';
import type { IrFragment } from '../../src/protocol/generated/ir-fragment.js';
import { analyzeFixture } from '../support/fixture.js';

let ir: IrFragment;

beforeAll(async () => {
  ir = await analyzeFixture('angular-ngmodule');
});

describe('resolve — fixtures/angular-ngmodule (NgModule nạp sớm và nạp lười)', () => {
  it('một ứng dụng', () => {
    expect(ir.nodes.filter((n) => n.kind === 'UI_APP').map((n) => n.key)).toEqual(['ng:app:shop-app']);
  });

  it('route forChild của module import trực tiếp nằm ở gốc; module nạp lười dưới tiền tố; không có màn hình trùng', () => {
    const screens = ir.nodes.filter((n) => n.kind === 'UI_SCREEN').map((n) => (n.attrs as { routePath?: string } | undefined)?.routePath);
    expect([...screens].sort()).toEqual(['/', '/**', '/items', '/reports']);
  });

  it('màn hình của module nạp sớm có phần tử gọi API', () => {
    const calls = ir.nodes.filter((n) => n.kind === 'HTTP_CALL').map((n) => `${n.name} ${JSON.stringify(n.attrs ?? {})}`);
    expect(calls.join('|')).toContain('/api/v1/items');
    expect(ir.nodes.some((n) => n.kind === 'UI_ELEMENT' && n.displayName === 'Tải lại')).toBe(true);
    expect(ir.nodes.some((n) => n.kind === 'UI_ELEMENT' && n.displayName === 'In')).toBe(true);
  });
});
