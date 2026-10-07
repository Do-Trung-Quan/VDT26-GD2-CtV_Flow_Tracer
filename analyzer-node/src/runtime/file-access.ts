import { realpath, readFile } from 'node:fs/promises';
import path from 'node:path';

/**
 * Đọc file AN TOÀN: chỉ các file Engine đã cấp trong request, bên trong sourceRoot; chặn '..' và symlink trỏ ra ngoài.
 */
export class FileAccess {
  private readonly allowed: Set<string>;

  constructor(
    private readonly sourceRoot: string,
    allowedPaths: readonly string[]
  ) {
    this.allowed = new Set(allowedPaths.map((p) => p.replace(/\\/g, '/')));
  }

  async read(relativePath: string): Promise<string> {
    const rel = relativePath.replace(/\\/g, '/');
    if (!this.allowed.has(rel)) {
      throw new Error(`File không nằm trong danh sách được cấp: ${rel}`);
    }
    const root = await realpath(this.sourceRoot);
    const absolute = path.resolve(root, rel);
    const real = await realpath(absolute);
    if (!isInside(root, absolute) || !isInside(root, real)) {
      throw new Error(`Đường dẫn nằm ngoài sourceRoot: ${rel}`);
    }
    return readFile(real, 'utf8');
  }
}

function isInside(root: string, candidate: string): boolean {
  const relative = path.relative(root, candidate);
  return relative !== '' && !relative.startsWith('..') && !path.isAbsolute(relative);
}
