#!/usr/bin/env node
// Chặn lỡ commit từ khóa nội bộ (tên dự án demo, package, domain nội bộ…) — CLAUDE.md §3.5.
//
//   node scripts/check-forbidden-terms.mjs          # quét nội dung các file đã `git add` (bản trong index)
//   node scripts/check-forbidden-terms.mjs --all    # quét mọi file của repo (đã theo dõi + mới, trừ file bị .gitignore)
//
// Danh sách từ khóa nằm trong file `.forbidden-terms` ở gốc repo (đã gitignore, KHÔNG commit):
// mỗi dòng một từ khóa, không phân biệt hoa thường; dòng trống và dòng bắt đầu bằng `#` bị bỏ qua.
// Mã thoát: 0 = sạch (hoặc chưa có `.forbidden-terms`), 1 = có vi phạm, 2 = lỗi chạy.
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const TERMS_FILE = path.join(ROOT, '.forbidden-terms');
const MAX_BYTES = 2 * 1024 * 1024;

function git(args, encoding = 'utf8') {
  return execFileSync('git', args, { cwd: ROOT, encoding, maxBuffer: 256 * 1024 * 1024 });
}

function loadTerms() {
  if (!existsSync(TERMS_FILE)) {
    return null;
  }
  return readFileSync(TERMS_FILE, 'utf8')
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#'))
    .map((t) => t.toLowerCase());
}

function listFiles(all) {
  const lines = all
    ? git(['ls-files', '-z', '--cached', '--others', '--exclude-standard'])
    : git(['diff', '--cached', '--name-only', '-z', '--diff-filter=ACMR']);
  return [...new Set(lines.split('\0').filter(Boolean))].sort();
}

/** Nội dung cần quét: bản trong index khi quét file sắp commit, bản trên ổ đĩa khi --all. */
function readContent(file, all) {
  if (all) {
    const abs = path.join(ROOT, file);
    if (!existsSync(abs) || !statSync(abs).isFile() || statSync(abs).size > MAX_BYTES) {
      return null;
    }
    return readFileSync(abs);
  }
  const buf = git(['show', `:${file}`], 'buffer');
  return buf.length > MAX_BYTES ? null : buf;
}

function main(argv) {
  const all = argv.includes('--all');
  const terms = loadTerms();
  if (terms === null) {
    console.warn('⚠ Chưa có file .forbidden-terms ở gốc repo — bỏ qua kiểm tra. Tạo file này (mỗi dòng một từ khóa) để bật.');
    return 0;
  }
  if (terms.length === 0) {
    console.warn('⚠ .forbidden-terms không có từ khóa nào — bỏ qua kiểm tra.');
    return 0;
  }

  const files = listFiles(all);
  const hits = [];
  for (const file of files) {
    const lowerPath = file.toLowerCase();
    for (const term of terms) {
      if (lowerPath.includes(term)) {
        hits.push({ file, line: 0, term });
      }
    }
    const buf = readContent(file, all);
    if (buf === null || buf.subarray(0, 8000).includes(0)) {
      continue; // file lớn hoặc nhị phân
    }
    const lines = buf.toString('utf8').toLowerCase().split(/\r?\n/);
    lines.forEach((text, i) => {
      for (const term of terms) {
        if (text.includes(term)) {
          hits.push({ file, line: i + 1, term });
        }
      }
    });
  }

  if (hits.length === 0) {
    console.log(`✓ Sạch: đã quét ${files.length} file với ${terms.length} từ khóa${all ? ' (--all)' : ' (file đã git add)'}.`);
    return 0;
  }
  console.error(`✗ Phát hiện ${hits.length} chỗ chứa từ khóa nội bộ:`);
  for (const h of hits) {
    console.error(`  ${h.file}${h.line ? `:${h.line}` : ' (tên file)'}  → "${h.term}"`);
  }
  console.error('Hãy xóa / đổi sang miền nghiệp vụ giả định trước khi commit (CLAUDE.md §3.5).');
  return 1;
}

try {
  process.exitCode = main(process.argv.slice(2));
} catch (e) {
  console.error(`Lỗi khi kiểm tra: ${e.message}`);
  process.exitCode = 2;
}
