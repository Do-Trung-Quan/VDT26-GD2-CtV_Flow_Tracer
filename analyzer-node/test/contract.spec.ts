import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { Ajv2020 } from 'ajv/dist/2020.js';
import { describe, expect, it } from 'vitest';

const contracts = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'contracts');
const load = async (name: string) => JSON.parse(await readFile(path.join(contracts, name), 'utf8'));

describe('hợp đồng contracts/', () => {
  it('ví dụ IrFragment khớp schema', async () => {
    const validate = new Ajv2020({ allErrors: true }).compile(await load('ir-fragment.schema.json'));
    const ok = validate(await load('examples/ir-fragment.example.json'));
    expect(validate.errors ?? []).toEqual([]);
    expect(ok).toBe(true);
  });

  it('edge thiếu cả location lẫn reason bị từ chối', async () => {
    const validate = new Ajv2020().compile(await load('ir-fragment.schema.json'));
    const bad = {
      adapterId: 'angular',
      nodes: [],
      edges: [{ fromKey: 'a', toKey: 'b', kind: 'CALLS', confidence: 'CERTAIN' }],
      detectedLinkRules: [],
      diagnostics: []
    };
    expect(validate(bad)).toBe(false);
  });
});
