// Phần dùng chung của scripts/benchmark.mjs và scripts/evaluate-accuracy.mjs: gọi API của Engine, đọc tham số dòng lệnh,
// thống kê và đo RAM của tiến trình Engine. Chỉ dùng thư viện chuẩn của Node 22 (fetch có sẵn).
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

export const REPO_ROOT = join(dirname(fileURLToPath(import.meta.url)), '..', '..');

/** Đọc `--khóa giá-trị` / `--cờ`; khóa lặp lại (vd `--source a --source b`) thành mảng. */
export function parseArgs(argv, { flags = [], repeatable = [] } = {}) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith('--')) continue;
    const key = a.slice(2);
    if (flags.includes(key)) {
      out[key] = true;
      continue;
    }
    const value = argv[++i];
    if (value === undefined || value.startsWith('--')) throw new Error(`Thiếu giá trị cho --${key}`);
    if (repeatable.includes(key)) (out[key] ??= []).push(value);
    else out[key] = value;
  }
  return out;
}

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export class ApiError extends Error {
  constructor(status, code, message) {
    super(`${status} ${code}: ${message}`);
    this.status = status;
    this.code = code;
  }
}

/** Client mỏng cho REST của Engine; mọi response REST bọc trong `{status, code, message, data, errors}`. */
export function engineClient(base = 'http://127.0.0.1:8600') {
  const root = base.replace(/\/$/, '');
  async function call(method, path, body) {
    const res = await fetch(root + '/api' + path, {
      method,
      headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (res.status === 204) return null;
    const json = await res.json().catch(() => null);
    if (!res.ok) throw new ApiError(res.status, json?.code ?? 'HTTP_' + res.status, json?.message ?? res.statusText);
    return json?.data ?? null;
  }
  /** Gọi và đo thời gian (ms, theo đồng hồ của script). */
  async function timed(method, path, body) {
    const t = performance.now();
    const data = await call(method, path, body);
    return { data, ms: performance.now() - t };
  }
  async function healthy() {
    try {
      const res = await fetch(root + '/api/health');
      return res.ok;
    } catch {
      return false;
    }
  }
  return { base: root, call, timed, healthy };
}

/** Chờ một lần quét kết thúc; `onTick` được gọi mỗi vòng (dùng để lấy mẫu RAM). Trả về lần quét đã kết thúc và thời gian chờ (ms). */
export async function waitForScan(client, scanRunId, { onTick, timeoutMs = 30 * 60_000, pollMs = 500 } = {}) {
  const start = performance.now();
  for (;;) {
    const run = await client.call('GET', `/scans/${scanRunId}`);
    if (run && !['PENDING', 'RUNNING'].includes(run.status)) return { run, wallMs: performance.now() - start };
    if (performance.now() - start > timeoutMs) throw new Error('Quét quá thời gian chờ');
    onTick?.();
    await sleep(pollMs);
  }
}

// ---------------- thống kê ----------------

export function percentile(values, p) {
  if (values.length === 0) return NaN;
  const sorted = [...values].sort((a, b) => a - b);
  const idx = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[idx];
}

export const fmtMs = (ms) => (Number.isFinite(ms) ? (ms >= 10_000 ? (ms / 1000).toFixed(1) + ' s' : ms >= 1000 ? (ms / 1000).toFixed(2) + ' s' : Math.round(ms) + ' ms') : '—');
export const fmtPct = (x) => (Number.isFinite(x) ? (x * 100).toFixed(1) + '%' : '—');

// ---------------- RAM của Engine ----------------

/** PID của tiến trình đang lắng nghe `port`; null nếu không tìm được. */
export function findListenerPid(port) {
  try {
    if (process.platform === 'win32') {
      const out = execFileSync(
        'powershell',
        ['-NoProfile', '-Command', `(Get-NetTCPConnection -State Listen -LocalPort ${port} -ErrorAction SilentlyContinue | Select-Object -First 1).OwningProcess`],
        { encoding: 'utf8', timeout: 15_000 }
      ).trim();
      return /^\d+$/.test(out) ? Number(out) : null;
    }
    const out = execFileSync('sh', ['-c', `ss -ltnp 2>/dev/null | grep ':${port} ' | sed -n 's/.*pid=\\([0-9]*\\).*/\\1/p' | head -1`], { encoding: 'utf8', timeout: 15_000 }).trim();
    return /^\d+$/.test(out) ? Number(out) : null;
  } catch {
    return null;
  }
}

/** Bộ nhớ vật lý (MB) đang dùng của tiến trình; NaN nếu không đọc được. */
export function readRssMb(pid) {
  try {
    if (process.platform === 'win32') {
      const out = execFileSync('powershell', ['-NoProfile', '-Command', `(Get-Process -Id ${pid}).WorkingSet64`], { encoding: 'utf8', timeout: 15_000 }).trim();
      return Number(out) / 1024 / 1024;
    }
    const status = readFileSync(`/proc/${pid}/status`, 'utf8');
    const kb = /VmRSS:\s+(\d+)/.exec(status)?.[1];
    return kb ? Number(kb) / 1024 : NaN;
  } catch {
    return NaN;
  }
}

/** Gốc mã nguồn của Engine (SOURCE_ROOT): tham số, biến môi trường, hoặc `.env` ở gốc repo. */
export function resolveSourceRoot(explicit) {
  if (explicit) return explicit;
  if (process.env.SOURCE_ROOT) return process.env.SOURCE_ROOT;
  const envFile = join(REPO_ROOT, '.env');
  if (existsSync(envFile)) {
    const m = /^SOURCE_ROOT=(.+)$/m.exec(readFileSync(envFile, 'utf8'));
    if (m) return m[1].trim();
  }
  return null;
}

// ---------------- cây giao diện ----------------

/** Duyệt cây Frontend (C2) theo thứ tự cây; trả danh sách `{ node, trail }` với `trail` là các nút tổ tiên (không gồm chính nó). */
export function flattenUi(apps) {
  const out = [];
  const walk = (node, trail) => {
    out.push({ node, trail });
    for (const child of node.children ?? []) walk(child, [...trail, node]);
  };
  for (const app of apps ?? []) walk(app, []);
  return out;
}

/** Chuẩn hóa để so khớp tên: bỏ dấu tiếng Việt, hạ chữ thường, gộp khoảng trắng. */
export function norm(text) {
  return String(text ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .replace(/[\s*:]+$/, '') // dấu "bắt buộc" (*) và dấu hai chấm cuối nhãn không phải một phần của tên
    .trim();
}

/** "POST /api/v1/x/{id}/" → "POST /api/v1/x/{}" — so khớp API không phân biệt tên biến đường dẫn và dấu "/" cuối. */
export function normApi(label) {
  const m = /^\s*([A-Za-z]+)\s+(\S+)\s*$/.exec(String(label ?? ''));
  if (!m) return String(label ?? '').trim();
  const path = m[2].replace(/\{[^}/]*\}/g, '{}').replace(/\/+$/, '') || '/';
  return `${m[1].toUpperCase()} ${path}`;
}

/** Bộ nhớ (MB) đang dùng của một container Docker (`docker stats`); NaN nếu không đọc được. */
export function readContainerMemMb(name) {
  try {
    const out = execFileSync('docker', ['stats', '--no-stream', '--format', '{{.MemUsage}}', name], { encoding: 'utf8', timeout: 30_000 }).trim();
    const m = /^([\d.]+)\s*([KMG]i?B)/i.exec(out);
    if (!m) return NaN;
    const unit = m[2].toUpperCase();
    return Number(m[1]) * (unit.startsWith('G') ? 1024 : unit.startsWith('K') ? 1 / 1024 : 1);
  } catch {
    return NaN;
  }
}
