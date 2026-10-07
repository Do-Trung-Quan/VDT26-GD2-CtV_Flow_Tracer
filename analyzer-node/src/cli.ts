#!/usr/bin/env node
/**
 * CLI sidecar — xem contracts/sidecar-protocol.md.
 *   node dist/cli.js analyze < request.json > fragment.json 2> progress.ndjson
 * Mã thoát: 0 ok · 2 lỗi đầu vào · 3 lỗi phân tích · 4 timeout.
 */
import { readFile } from 'node:fs/promises';
import { findAdapter } from './adapters/registry.js';
import type { AnalyzeRequest } from './protocol/generated/analyze-request.js';
import { reportProgress } from './protocol/progress.js';
import { runPipeline } from './runtime/pipeline.js';

export const EXIT = { OK: 0, BAD_INPUT: 2, ANALYSIS_FAILED: 3, TIMEOUT: 4 } as const;

async function readStdin(): Promise<string> {
  const chunks: Buffer[] = [];
  for await (const chunk of process.stdin) {
    chunks.push(chunk as Buffer);
  }
  return Buffer.concat(chunks).toString('utf8');
}

async function main(argv: string[]): Promise<number> {
  const [command, requestFile] = argv;
  if (command !== 'analyze') {
    process.stderr.write('Cách dùng: cli.js analyze [request.json]  (mặc định đọc request từ stdin)\n');
    return EXIT.BAD_INPUT;
  }

  let request: AnalyzeRequest;
  try {
    const raw = requestFile ? await readFile(requestFile, 'utf8') : await readStdin();
    request = JSON.parse(raw) as AnalyzeRequest;
  } catch (e) {
    process.stderr.write(`Request không phải JSON hợp lệ: ${(e as Error).message}\n`);
    return EXIT.BAD_INPUT;
  }

  const adapter = findAdapter(request.adapter);
  if (!adapter) {
    process.stderr.write(`Adapter không được hỗ trợ: ${request.adapter}\n`);
    return EXIT.BAD_INPUT;
  }

  try {
    const fragment = await runPipeline(adapter, request);
    reportProgress({ type: 'progress', stage: 'DONE' });
    process.stdout.write(JSON.stringify(fragment));
    return EXIT.OK;
  } catch (e) {
    process.stderr.write(`Phân tích thất bại: ${(e as Error).message}\n`);
    return EXIT.ANALYSIS_FAILED;
  }
}

process.exitCode = await main(process.argv.slice(2));
