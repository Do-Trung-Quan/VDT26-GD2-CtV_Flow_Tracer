/** Đường dẫn dạng POSIX tương đối so với gốc Source (không có '/' đầu). */

export function dirname(path: string): string {
  const i = path.lastIndexOf('/');
  return i < 0 ? '' : path.slice(0, i);
}

export function basename(path: string): string {
  return path.slice(path.lastIndexOf('/') + 1);
}

/** Ghép và chuẩn hóa `.` / `..`; vượt lên trên gốc thì trả null qua {@link joinPathStrict}. */
export function joinPath(...segments: string[]): string {
  return joinPathStrict(...segments) ?? '';
}

export function joinPathStrict(...segments: string[]): string | null {
  const out: string[] = [];
  for (const segment of segments.join('/').split('/')) {
    if (segment === '' || segment === '.') {
      continue;
    }
    if (segment === '..') {
      if (out.length === 0) {
        return null;
      }
      out.pop();
    } else {
      out.push(segment);
    }
  }
  return out.join('/');
}

/** Số đoạn thư mục chung đầu tiên của hai đường dẫn (đo độ gần nhau của hai file). */
export function commonDirDepth(a: string, b: string): number {
  const da = dirname(a).split('/').filter(Boolean);
  const db = dirname(b).split('/').filter(Boolean);
  let n = 0;
  while (n < da.length && n < db.length && da[n] === db[n]) {
    n++;
  }
  return n;
}
