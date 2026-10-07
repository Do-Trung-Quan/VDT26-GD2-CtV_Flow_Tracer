import { describe, expect, it } from 'vitest';
import { parseProxyConfig } from '../src/adapters/angular/config/proxy-config.reader.js';

describe('proxy-config.reader', () => {
  it('đọc pathRewrite và sắp xếp ổn định', () => {
    const rules = parseProxyConfig(
      JSON.stringify({ '/gateway': { target: 'http://localhost:8080', pathRewrite: { '^/gateway': '' } }, '/api': {} })
    );
    expect(rules).toEqual([
      { context: '/api', pathRewrite: {} },
      { context: '/gateway', pathRewrite: { '^/gateway': '' } }
    ]);
  });
});
