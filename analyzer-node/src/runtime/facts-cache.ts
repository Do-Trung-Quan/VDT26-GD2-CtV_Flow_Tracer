import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

/** Cache dữ kiện pha 1 theo mã băm file: <cacheDir>/<hash>.json — nơi DUY NHẤT sidecar được ghi. */
export class FactsCache {
  constructor(
    private readonly cacheDir: string,
    private readonly version: string
  ) {}

  async get<T>(hash: string): Promise<T | undefined> {
    try {
      const entry = JSON.parse(await readFile(this.file(hash), 'utf8')) as { v?: string; facts?: T };
      return entry.v === this.version ? entry.facts : undefined;
    } catch {
      return undefined;
    }
  }

  async put(hash: string, facts: unknown): Promise<void> {
    await mkdir(this.cacheDir, { recursive: true });
    await writeFile(this.file(hash), JSON.stringify({ v: this.version, facts }), 'utf8');
  }

  private file(hash: string): string {
    if (!/^[0-9a-f]{64}$/.test(hash)) {
      throw new Error(`Mã băm không hợp lệ: ${hash}`);
    }
    return path.join(this.cacheDir, `${hash}.json`);
  }
}
