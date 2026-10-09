// Sinh kiểu TypeScript từ contracts/*.schema.json → src/protocol/generated/ (không sửa tay các file sinh ra).
import { compileFromFile } from 'json-schema-to-typescript';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const contracts = path.resolve(here, '..', '..', 'contracts');
const outDir = path.resolve(here, '..', 'src', 'protocol', 'generated');

const schemas = {
  'ir-fragment.schema.json': 'ir-fragment.ts',
  'analyze-request.schema.json': 'analyze-request.ts',
  'progress-event.schema.json': 'progress-event.ts',
  'detect-request.schema.json': 'detect-request.ts',
  'detect-result.schema.json': 'detect-result.ts'
};

await mkdir(outDir, { recursive: true });
for (const [schema, out] of Object.entries(schemas)) {
  const ts = await compileFromFile(path.join(contracts, schema), {
    cwd: contracts,
    bannerComment: '/* SINH TỰ ĐỘNG từ contracts/' + schema + ' — KHÔNG SỬA TAY. Chạy: npm run gen:contracts */',
    additionalProperties: false,
    style: { singleQuote: true }
  });
  await writeFile(path.join(outDir, out), ts, 'utf8');
}
console.log('Đã sinh kiểu từ contracts/ →', path.relative(process.cwd(), outDir));
