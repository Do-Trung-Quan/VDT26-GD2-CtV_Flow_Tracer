#!/usr/bin/env node
// Đo hiệu năng Engine (NFR-01 → NFR-06): quét lần đầu, quét lại, tải cây, tìm kiếm, dò vết, RAM.
// CHỈ in số liệu tổng hợp — không in tên API, màn hình hay đường dẫn của dự án được đo.
//
//   node scripts/benchmark.mjs --source <thư-mục-tương-đối-SOURCE_ROOT> [--source ...]    # tạo Workspace tạm, đo, rồi xóa
//   node scripts/benchmark.mjs --workspace <id>                                           # đo trên Workspace sẵn có (không đổi gì)
//
// Quét lại với 20 file thay đổi cần SỬA file; script không bao giờ sửa mã nguồn gốc. Thay vào đó nó SAO CHÉP các Source vào
// thư mục tạm `<SOURCE_ROOT>/ft-bench-<mã>/` (bỏ node_modules, .git, target, dist…), đo trên bản sao, sửa 20 file TRONG BẢN SAO rồi xóa
// bản sao khi xong. Tắt bằng --no-copy (khi đó chỉ đo quét lại không có file nào đổi).
import { cpSync, existsSync, readdirSync, rmSync, statSync, appendFileSync } from 'node:fs';
import { basename, join, relative } from 'node:path';
import {
  engineClient, fmtMs, findListenerPid, flattenUi, parseArgs, percentile, readContainerMemMb, readRssMb, resolveSourceRoot, sleep, waitForScan
} from './lib/engine-client.mjs';

const HELP = `Dùng: node scripts/benchmark.mjs (--source <thư-mục> ... | --workspace <id>) [tùy chọn]
  --base <url>          Engine (mặc định http://127.0.0.1:8600)
  --source <thư-mục>    thư mục mã nguồn, tương đối so với SOURCE_ROOT của Engine (lặp lại được)
  --workspace <id>      đo trên Workspace đã có thay vì tạo mới (không xóa, không quét IMPORT lại)
  --source-root <đường> SOURCE_ROOT trên máy này (mặc định: biến môi trường hoặc .env) — cần để sao chép khi đo quét lại
  --rescan-edit <n>     số file bị sửa khi đo quét lại (mặc định 20)
  --no-copy             không sao chép; quét lại chỉ đo trường hợp không file nào đổi
  --traces <n>          số luồng đem dò vết (mặc định 30 API + 15 phần tử giao diện)
  --pid <pid>           PID của Engine để đo RAM (mặc định: tiến trình đang nghe cổng của --base)
  --container <tên>     đo RAM của container Docker này (Engine chạy trong Docker) thay vì theo PID
  --exclude <glob>      loại thư mục / file khỏi mọi Source (lặp lại được), vd thư mục hàng chục nghìn file không phân tích — rất đáng làm khi chạy trong Docker trên Windows
  --keep                giữ lại Workspace tạm sau khi đo
  --json                in kết quả dạng JSON`;

const args = parseArgs(process.argv.slice(2), { flags: ['keep', 'json', 'no-copy', 'help'], repeatable: ['source', 'exclude'] });
if (args.help || (!args.source && !args.workspace)) {
  console.log(HELP);
  process.exit(args.help ? 0 : 1);
}

const client = engineClient(args.base);
if (!(await client.healthy())) {
  console.error(`Engine chưa chạy tại ${client.base}. Chạy: ./mvnw -f engine/pom.xml -pl flowtracer-app spring-boot:run -Dspring-boot.run.profiles=dev`);
  process.exit(2);
}

const TARGET = { scanFirstMs: 180_000, rescanMs: 15_000, treeMs: 1000, searchMs: 1000, traceMs: 2000, ramMb: 2048 };
const traceLimit = Number(args.traces ?? 30);
const editCount = Number(args['rescan-edit'] ?? 20);
const result = { files: 0, rows: [] };
const row = (id, name, value, target, pass, note = '') => result.rows.push({ id, name, value, target, pass, note });

// ---------------- RAM ----------------
const port = Number(new URL(client.base).port || 80);
const container = args.container ?? null;
const pid = container ? null : args.pid ? Number(args.pid) : findListenerPid(port);
let peakMb = NaN;
const sampleRam = () => {
  if (!pid && !container) return;
  const mb = container ? readContainerMemMb(container) : readRssMb(pid);
  if (Number.isFinite(mb)) peakMb = Number.isFinite(peakMb) ? Math.max(peakMb, mb) : mb;
};

// ---------------- bản sao để đo quét lại ----------------
const SKIP = new Set(['node_modules', '.git', 'target', 'dist', 'build', '.angular', '.idea', '.gradle', 'out', 'coverage']);
let tempDir = null;
let sources = args.source ?? [];
let workspaceId = args.workspace ?? null;
let createdWorkspace = false;
const sourceRoot = resolveSourceRoot(args['source-root']);

if (!workspaceId && !args['no-copy']) {
  if (!sourceRoot || !existsSync(sourceRoot)) {
    console.error('Không xác định được SOURCE_ROOT trên máy này (cần để sao chép khi đo quét lại). Dùng --source-root <đường dẫn> hoặc --no-copy.');
    process.exit(2);
  }
  tempDir = join(sourceRoot, 'ft-bench-' + Date.now().toString(36));
  const copied = [];
  for (const rel of sources) {
    const from = join(sourceRoot, rel);
    if (!existsSync(from) || !statSync(from).isDirectory()) {
      console.error('Không có thư mục nguồn: --source ' + rel);
      process.exit(2);
    }
    const to = join(tempDir, basename(from) + '-' + copied.length);
    cpSync(from, to, { recursive: true, filter: (src) => !SKIP.has(basename(src)) });
    copied.push(relative(sourceRoot, to).replaceAll('\\', '/'));
  }
  sources = copied;
}

async function cleanup() {
  if (createdWorkspace && workspaceId && !args.keep) await client.call('DELETE', `/workspaces/${workspaceId}`).catch(() => {});
  if (tempDir) rmSync(tempDir, { recursive: true, force: true });
}
process.on('SIGINT', async () => {
  await cleanup();
  process.exit(130);
});

try {
  // ---------------- NFR-01: quét lần đầu + NFR-06: RAM ----------------
  if (!workspaceId) {
    const ws = await client.call('POST', '/workspaces', {
      name: 'bench-' + Date.now().toString(36),
      sources: sources.map((rootPath) => ({ rootPath, ...(args.exclude ? { excludeGlobs: args.exclude } : {}) }))
    });
    workspaceId = ws.id;
    createdWorkspace = true;
    sampleRam();
    const baseline = peakMb;
    const t0 = performance.now();
    const started = await client.call('POST', `/workspaces/${workspaceId}/scans`, { trigger: 'IMPORT' });
    const { run, wallMs } = await waitForScan(client, started.id, { onTick: sampleRam });
    sampleRam();
    result.files = run.stats?.files?.total ?? 0;
    const serverMs = run.durationMs ?? wallMs;
    result.scanStatus = run.status;
    result.stages = (run.stats?.stageDurations ?? []).map((s) => ({ stage: s.stage, ms: s.durationMs }));
    const status = run.status === 'FAILED' ? ' (quét THẤT BẠI)' : run.status === 'PARTIAL' ? ' (PARTIAL)' : '';
    row('NFR-01', `Quét lần đầu (${result.files} file)${status}`, fmtMs(serverMs), '≤ 3 phút (cho ~2.000 file)', serverMs <= TARGET.scanFirstMs,
      result.files && result.files < 1500 ? 'Số file ít hơn 2.000 nên chỉ mang tính tham khảo' : '');
    row('NFR-06', 'RAM Engine (đỉnh khi quét)', Number.isFinite(peakMb) ? Math.round(peakMb) + ' MB' : 'không đo được', '≤ 2 GB', Number.isFinite(peakMb) ? peakMb <= TARGET.ramMb : null,
      (pid || container) ? `${container ? 'container ' + container : 'PID ' + pid}${Number.isFinite(baseline) ? ', trước quét ' + Math.round(baseline) + ' MB' : ''}` : 'Thêm --pid hoặc --container để đo RAM');
    result.scanWallMs = performance.now() - t0;
  } else {
    const runs = await client.call('GET', `/workspaces/${workspaceId}/scans?limit=1`);
    const last = runs?.items?.[0];
    if (last?.stats?.files) result.files = last.stats.files.total ?? 0;
    sampleRam();
    row('NFR-01', 'Quét lần đầu', 'bỏ qua (dùng Workspace sẵn có)', '≤ 3 phút', null, 'Bỏ --workspace để đo');
    row('NFR-06', 'RAM Engine (hiện tại, sau quét)', Number.isFinite(peakMb) ? Math.round(peakMb) + ' MB' : 'không đo được', '≤ 2 GB', Number.isFinite(peakMb) ? peakMb <= TARGET.ramMb : null, 'Chỉ là RAM tại thời điểm đo');
  }

  // ---------------- NFR-02: quét lại ----------------
  const rescan = async () => {
    const started = await client.call('POST', `/workspaces/${workspaceId}/scans`, { trigger: 'RESCAN' });
    const { run, wallMs } = await waitForScan(client, started.id, { onTick: sampleRam });
    return { run, ms: run.durationMs ?? wallMs };
  };
  const unchanged = await rescan();
  const stageText = (run) => (run.stats?.stageDurations ?? []).filter((s) => s.durationMs >= 200).map((s) => `${s.stage} ${fmtMs(s.durationMs)}`).join(', ');
  row('NFR-02', 'Quét lại, không file nào đổi', fmtMs(unchanged.ms), '≤ 15 giây', unchanged.ms <= TARGET.rescanMs,
    [unchanged.run.stats?.noChanges ? 'noChanges' : 'có phân tích lại', stageText(unchanged.run)].filter(Boolean).join('; '));
  if (tempDir) {
    const candidates = [];
    const walk = (dir) => {
      for (const e of readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
        const p = join(dir, e.name);
        if (e.isDirectory()) walk(p);
        else if (/\.(java|ts)$/.test(e.name) && !/\.(spec|test)\.ts$|\.d\.ts$/.test(e.name)) candidates.push(p);
      }
    };
    walk(tempDir);
    const step = Math.max(1, Math.floor(candidates.length / editCount));
    const edited = candidates.filter((_, i) => i % step === 0).slice(0, editCount);
    for (const f of edited) appendFileSync(f, '\n// bench\n');
    const changed = await rescan();
    row('NFR-02', `Quét lại, ${edited.length} file đổi (trên bản sao)`, fmtMs(changed.ms), '≤ 15 giây', changed.ms <= TARGET.rescanMs,
      `phân tích lại ${changed.run.stats?.files?.extracted ?? '?'} file, dùng cache ${changed.run.stats?.files?.fromCache ?? '?'}; ${stageText(changed.run)}`);
  } else if (!args['no-copy']) {
    row('NFR-02', 'Quét lại, ≤ 20 file đổi', 'bỏ qua', '≤ 15 giây', null, 'Cần --source để script tạo bản sao rồi sửa file');
  }

  // ---------------- NFR-03: tải cây, tìm kiếm ----------------
  const REPEAT = 5;
  const times = async (fn) => {
    const out = [];
    for (let i = 0; i < REPEAT; i++) out.push((await fn()).ms);
    return out;
  };
  const beT = await times(() => client.timed('GET', `/workspaces/${workspaceId}/trees/backend`));
  const feT = await times(() => client.timed('GET', `/workspaces/${workspaceId}/trees/frontend`));
  const be = (await client.timed('GET', `/workspaces/${workspaceId}/trees/backend`)).data;
  const fe = (await client.timed('GET', `/workspaces/${workspaceId}/trees/frontend`)).data;
  const endpoints = (be.modules ?? []).flatMap((m) => m.endpoints ?? []);
  row('NFR-03', `Tải cây Backend (${be.endpointCount ?? endpoints.length} API)`, `p95 ${fmtMs(percentile(beT, 95))}`, '≤ 1 giây', percentile(beT, 95) <= TARGET.treeMs);
  row('NFR-03', `Tải cây Frontend (${fe.elementCount ?? 0} phần tử)`, `p95 ${fmtMs(percentile(feT, 95))}`, '≤ 1 giây', percentile(feT, 95) <= TARGET.treeMs);
  const words = [...new Set(endpoints.flatMap((e) => (e.path ?? '').split(/[/{}]+/)).filter((w) => w.length >= 3))].slice(0, 8);
  const searchT = [];
  for (const w of words.length ? words : ['a']) searchT.push((await client.timed('GET', `/workspaces/${workspaceId}/search?q=${encodeURIComponent(w)}`)).ms);
  row('NFR-03', `Tìm kiếm (${searchT.length} từ khóa)`, `p95 ${fmtMs(percentile(searchT, 95))}`, '≤ 1 giây', percentile(searchT, 95) <= TARGET.searchMs);

  // ---------------- NFR-04: dò vết ----------------
  const pick = (list, n) => {
    if (list.length <= n) return list;
    const step = list.length / n;
    return Array.from({ length: n }, (_, i) => list[Math.floor(i * step)]);
  };
  const feStarts = flattenUi(fe.apps).filter((x) => x.node.kind === 'UI_ELEMENT' && x.node.callsApi).map((x) => x.node.nodeKey);
  const starts = [...pick(endpoints.map((e) => e.nodeKey), traceLimit), ...pick(feStarts, Math.ceil(traceLimit / 2))];
  const traceMs = [];
  const bigMs = [];
  let unresolvedSteps = 0;
  let totalSteps = 0;
  let failed = 0;
  for (const key of starts) {
    try {
      const { data, ms } = await client.timed('POST', `/workspaces/${workspaceId}/traces`, { startKey: key });
      const steps = data.steps ?? [];
      totalSteps += steps.length;
      unresolvedSteps += steps.filter((s) => s.confidence === 'UNRESOLVED').length;
      (steps.length <= 50 ? traceMs : bigMs).push(ms);
    } catch {
      failed++;
    }
  }
  row('NFR-04', `Dò vết ≤ 50 bước (${traceMs.length}/${starts.length} luồng)`, traceMs.length ? `p95 ${fmtMs(percentile(traceMs, 95))}, max ${fmtMs(Math.max(...traceMs))}` : 'không có luồng',
    '≤ 2 giây', traceMs.length ? percentile(traceMs, 95) <= TARGET.traceMs : null,
    [bigMs.length ? `${bigMs.length} luồng > 50 bước không tính (nhanh nhất-chậm nhất: ${fmtMs(Math.min(...bigMs))}–${fmtMs(Math.max(...bigMs))})` : '', failed ? `${failed} luồng lỗi khi dò` : ''].filter(Boolean).join('; '));
  result.traceStats = { sampled: starts.length, steps: totalSteps, unresolvedSteps };
} catch (e) {
  console.error('LỖI: ' + e.message);
  process.exitCode = 1;
} finally {
  await cleanup();
}

// ---------------- in kết quả ----------------
if (args.json) {
  console.log(JSON.stringify(result, null, 2));
} else {
  const mark = (p) => (p === true ? '✅' : p === false ? '❌' : '➖');
  console.log(`\nKẾT QUẢ ĐO (số liệu tổng hợp; ${result.files || '?'} file nguồn)\n`);
  console.log('| Chỉ tiêu | Nội dung | Đo được | Mục tiêu | Đạt | Ghi chú |');
  console.log('| :--- | :--- | :--- | :--- | :---: | :--- |');
  for (const r of result.rows) console.log(`| ${r.id} | ${r.name} | ${r.value} | ${r.target} | ${mark(r.pass)} | ${r.note} |`);
  if (result.traceStats) {
    const t = result.traceStats;
    console.log(`\nLuồng đã dò: ${t.sampled}; tổng ${t.steps} bước, trong đó ${t.unresolvedSteps} bước chưa xác định (${t.steps ? ((t.unresolvedSteps / t.steps) * 100).toFixed(1) : 0}%).`);
  }
  if (result.stages?.length) console.log('Thời gian từng giai đoạn quét: ' + result.stages.map((s) => `${s.stage} ${fmtMs(s.ms)}`).join(', '));
}
await sleep(0);
