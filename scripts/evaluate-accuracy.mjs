#!/usr/bin/env node
// Đo độ chính xác (NFR-07, NFR-08, NFR-09) của một Workspace đã quét, so với file ground truth đặt NGOÀI repo.
// CHỈ in số liệu tổng hợp (mặc định không in tên API / màn hình / phần tử). Ground truth định dạng JSON — xem scripts/README.md.
//
//   node scripts/evaluate-accuracy.mjs --workspace <id> --ground-truth <đường-dẫn-cục-bộ> [--openapi <url>] [--verbose]
import { readFileSync } from 'node:fs';
import { engineClient, flattenUi, fmtPct, norm, normApi, parseArgs } from './lib/engine-client.mjs';

const HELP = `Dùng: node scripts/evaluate-accuracy.mjs --workspace <id> --ground-truth <file.json> [tùy chọn]
  --base <url>          Engine (mặc định http://127.0.0.1:8600)
  --workspace <id>      Workspace đã quét xong
  --ground-truth <file> ground truth cục bộ (không đưa vào repo)
  --openapi <url>       URL tài liệu OpenAPI cho NFR-07 (bỏ trống: Engine tự tìm file trong mã nguồn)
  --verbose             in thêm số thứ tự mục sai (vẫn không in tên) để dò lại thủ công
  --diagnose            CHỈ để gỡ lỗi trên máy bạn: in chi tiết (CÓ tên) của các mục sai ra màn hình — không ghi file, đừng dán vào repo / tài liệu
  --json                in kết quả dạng JSON`;

const args = parseArgs(process.argv.slice(2), { flags: ['verbose', 'json', 'help', 'diagnose'] });
if (args.help || !args.workspace || !args['ground-truth']) {
  console.log(HELP);
  process.exit(args.help ? 0 : 1);
}

const client = engineClient(args.base);
if (!(await client.healthy())) {
  console.error(`Engine chưa chạy tại ${client.base}.`);
  process.exit(2);
}
const ws = args.workspace;
const truth = JSON.parse(readFileSync(args['ground-truth'], 'utf8'));
const report = { rows: [], details: [] };
const row = (id, name, value, target, pass, note = '') => report.rows.push({ id, name, value, target, pass, note });

// ---------------- dữ liệu của Workspace ----------------
const [be, fe] = await Promise.all([client.call('GET', `/workspaces/${ws}/trees/backend`), client.call('GET', `/workspaces/${ws}/trees/frontend`)]);
const endpoints = (be.modules ?? []).flatMap((m) => m.endpoints ?? []);
const endpointByApi = new Map();
for (const e of endpoints) {
  const k = normApi(`${e.httpMethod} ${e.path}`);
  endpointByApi.set(k, [...(endpointByApi.get(k) ?? []), e]);
}
const ui = flattenUi(fe.apps);
const CONTAINERS = new Set(['UI_SCREEN', 'UI_TAB', 'UI_DIALOG', 'UI_FORM']);
const labels = (n) => [n.displayName, n.aiLabel, n.fallbackName, n.name, n.routePath].filter(Boolean).map(norm);

/** Tìm phần tử giao diện theo {screen, element}: `screen` khớp (chứa) một tổ tiên là màn hình / tab / hộp thoại / form, `element` khớp đúng tên. */
async function findElement(spec) {
  const screen = norm(spec.screen ?? '');
  const element = norm(spec.element ?? '');
  let hits = ui.filter(
    ({ node, trail }) =>
      node.kind === 'UI_ELEMENT' &&
      labels(node).includes(element) &&
      (!screen || trail.some((t) => CONTAINERS.has(t.kind) && labels(t).some((l) => l.includes(screen))))
  );
  if (hits.length > 1) {
    // Bước 1: ưu tiên phần tử nằm trong màn hình / hộp thoại có tên TRÙNG ĐÚNG với `screen` (không chỉ chứa chuỗi)
    const exact = hits.filter(({ trail }) => trail.some((t) => CONTAINERS.has(t.kind) && labels(t).includes(screen)));
    if (exact.length > 0 && exact.length < hits.length) hits = exact;
  }
  if (hits.length > 1) {
    // Bước 2: nhiều phần tử cùng nhãn mà tool cho cùng một tập API → coi là tương đương (không mơ hồ)
    const sets = [];
    for (const h of hits) {
      const e = await client.call('GET', `/workspaces/${ws}/nodes/${encodeURIComponent(encodeURIComponent(h.node.nodeKey))}/explanation`).catch(() => null);
      sets.push([...new Set((e?.apis ?? []).map((a) => normApi(`${a.method} ${a.path}`)))].sort().join('|'));
    }
    if (new Set(sets).size === 1) return { key: hits[0].node.nodeKey, equivalent: hits.length };
  }
  return hits.length === 1
    ? { key: hits[0].node.nodeKey }
    : { error: hits.length === 0 ? 'không tìm thấy' : 'mơ hồ', hits };
}

async function findStart(start) {
  if (start.api) {
    const found = endpointByApi.get(normApi(start.api)) ?? [];
    return found.length === 1 ? { key: found[0].nodeKey } : { error: found.length === 0 ? 'không tìm thấy' : 'nhiều API trùng' };
  }
  return findElement(start);
}

/** Gỡ lỗi (--diagnose): vì sao một phần tử trong ground truth không khớp duy nhất — in CÓ tên, chỉ ra màn hình. */
async function diagnoseLink(n, spec, found, expected) {
  const screen = norm(spec.screen ?? '');
  const element = norm(spec.element ?? '');
  const containers = ui.filter((x) => CONTAINERS.has(x.node.kind) && labels(x.node).some((l) => l.includes(screen)));
  const anywhere = ui.filter((x) => x.node.kind === 'UI_ELEMENT' && labels(x.node).includes(element));
  const partial = ui.filter((x) => x.node.kind === 'UI_ELEMENT' && labels(x.node).some((l) => l.includes(element) || (element.includes(l) && l.length > 2)));
  console.log(`
[chẩn đoán #${n}] screen="${spec.screen}" element="${spec.element}" — ${found.error}; đáp án: ${[...expected].join(', ') || '(không gọi API)'}`);
  console.log(`  màn hình/hộp thoại khớp "screen": ${containers.length} ${containers.slice(0, 4).map((x) => `[${x.node.kind} ${x.node.displayName ?? x.node.name}]`).join(' ')}`);
  console.log(`  phần tử đúng nhãn ở MỌI nơi: ${anywhere.length}; nhãn chứa/được chứa (gần đúng): ${partial.length}`);
  for (const h of (found.hits ?? anywhere).slice(0, 6)) {
    const ex = await client.call('GET', `/workspaces/${ws}/nodes/${encodeURIComponent(encodeURIComponent(h.node?.nodeKey ?? h.nodeKey))}/explanation`).catch(() => null);
    const trail = (h.trail ?? []).filter((t) => t.kind !== 'UI_APP').map((t) => t.displayName ?? t.name).join(' > ');
    console.log(`   - ${trail} | ${(h.node ?? h).displayName ?? (h.node ?? h).fallbackName} | API: ${(ex?.apis ?? []).map((a) => a.method + ' ' + a.path).join(', ') || '(không)'}`);
  }
  if (!(found.hits ?? anywhere).length) {
    for (const h of partial.slice(0, 5)) console.log(`   ~ gần đúng: ${h.trail.filter((t) => t.kind !== 'UI_APP').map((t) => t.displayName ?? t.name).join(' > ')} | ${h.node.displayName ?? h.node.fallbackName}`);
  }
}

// ---------------- NFR-07: endpoint so với OpenAPI ----------------
try {
  const check = await client.call('POST', `/workspaces/${ws}/openapi-check`, { openapiUrl: args.openapi || undefined });
  const matched = check.matched?.length ?? 0;
  const onlyCode = check.onlyInCode?.length ?? 0;
  const onlySpec = check.onlyInSpec?.length ?? 0;
  const recall = check.specTotal ? matched / check.specTotal : NaN;
  row('NFR-07', `Endpoint khớp OpenAPI (đặc tả ${check.specTotal}, mã nguồn ${check.codeTotal})`, fmtPct(recall), '≥ 98%', recall >= 0.98,
    `khớp ${matched}; chỉ trong mã ${onlyCode}; chỉ trong đặc tả ${onlySpec}`);
  if (args.verbose && onlySpec) report.details.push(`NFR-07: ${onlySpec} API chỉ có trong đặc tả (bị sót hoặc đặc tả lỗi thời) — mở C6 trên giao diện để xem.`);
} catch (e) {
  row('NFR-07', 'Endpoint khớp OpenAPI', 'không đo được', '≥ 98%', null, e.message);
}

// ---------------- NFR-08: liên kết FE → BE ----------------
const links = truth.links ?? [];
if (links.length === 0) {
  row('NFR-08', 'Liên kết FE → BE', 'ground truth không có mục "links"', 'P ≥ 95%, R ≥ 85%', null);
} else {
  let tp = 0;
  let fp = 0;
  let fn = 0;
  let missingElement = 0;
  let ambiguous = 0;
  let unidentifiedApis = 0;
  let equivalent = 0;
  const bad = [];
  for (const [i, spec] of links.entries()) {
    const expected = new Set((spec.apis ?? []).map(normApi));
    const found = await findElement(spec);
    if (found.error) {
      if (found.error === 'mơ hồ') ambiguous++;
      else missingElement++;
      unidentifiedApis += expected.size;
      bad.push(`#${i + 1}: ${found.error}`);
      if (args.diagnose) await diagnoseLink(i + 1, spec, found, expected);
      continue;
    }
    if (found.equivalent) equivalent++;
    const exp = await client.call('GET', `/workspaces/${ws}/nodes/${encodeURIComponent(encodeURIComponent(found.key))}/explanation`);
    const predicted = new Set((exp.apis ?? []).map((a) => normApi(`${a.method} ${a.path}`)));
    let t = 0;
    for (const p of predicted) (expected.has(p) ? t++ : fp++);
    tp += t;
    const missed = expected.size - t;
    fn += missed;
    if (missed > 0 || predicted.size > t) bad.push(`#${i + 1}: thiếu ${missed}, thừa ${predicted.size - t}`);
    if (args.diagnose && (missed > 0 || predicted.size > t)) {
      console.log(`
[chẩn đoán #${i + 1}] screen="${spec.screen}" element="${spec.element}": đáp án ${[...expected].join(', ') || '(không)'} | tool thấy ${[...predicted].join(', ') || '(không)'} | chưa xác định được API đích: ${(exp.unresolvedCalls ?? []).join(' ; ') || '(không)'}`);
    }
  }
  const identified = links.length - ambiguous - missingElement;
  const precision = tp + fp ? tp / (tp + fp) : NaN;
  const recall = tp + fn ? tp / (tp + fn) : NaN;
  // Nghiêm ngặt: phần tử không xác định được (không có trong cây hoặc mơ hồ) coi như bỏ sót toàn bộ API của nó
  const strictRecall = tp + fn + unidentifiedApis ? tp / (tp + fn + unidentifiedApis) : NaN;
  row('NFR-08', `Liên kết FE → BE — precision (trên ${identified}/${links.length} phần tử xác định được)`, fmtPct(precision), '≥ 95%', precision >= 0.95,
    `đúng ${tp}, thừa ${fp}${equivalent ? `; ${equivalent} mục có nhiều phần tử trùng nhãn nhưng cùng tập API` : ''}`);
  row('NFR-08', `Liên kết FE → BE — recall (trên ${identified} phần tử xác định được)`, fmtPct(recall), '≥ 85%', recall >= 0.85, `thiếu ${fn}`);
  row('NFR-08', `Liên kết FE → BE — recall NGHIÊM NGẶT (tính cả ${links.length - identified} phần tử không xác định được là bỏ sót)`, fmtPct(strictRecall), '≥ 85%', strictRecall >= 0.85,
    `không có trong cây ${missingElement}; mơ hồ ${ambiguous} (cùng nhãn, khác tập API); thiếu ${unidentifiedApis} API`);
  if (args.verbose && bad.length) report.details.push('NFR-08 mục sai: ' + bad.join('; '));
}

// ---------------- NFR-09: trace đầy đủ, đúng tới bảng ----------------
const traces = truth.traces ?? [];
if (traces.length === 0) {
  row('NFR-09', 'Trace tới bảng dữ liệu', 'ground truth không có mục "traces"', '≥ 90%', null);
} else {
  let ok = 0;
  const reasons = { khongTimThay: 0, thieuBang: 0, buocChuaXacDinh: 0, loiDoVet: 0 };
  const bad = [];
  for (const [i, spec] of traces.entries()) {
    const start = await findStart(spec.start ?? {});
    if (start.error) {
      reasons.khongTimThay++;
      bad.push(`#${i + 1}: ${start.error}`);
      continue;
    }
    let trace;
    try {
      trace = await client.call('POST', `/workspaces/${ws}/traces`, { startKey: start.key, maxDepth: spec.maxDepth });
    } catch {
      reasons.loiDoVet++;
      bad.push(`#${i + 1}: lỗi khi dò vết`);
      continue;
    }
    const tables = (trace.participants ?? []).filter((p) => p.role === 'DATABASE').map((p) => norm(p.label));
    const expected = (spec.tables ?? []).map(norm);
    const missing = expected.filter((t) => !tables.some((x) => x.includes(t)));
    const unresolved = (trace.steps ?? []).filter((s) => s.confidence === 'UNRESOLVED').length;
    if (missing.length) reasons.thieuBang++;
    if (unresolved) reasons.buocChuaXacDinh++;
    if (args.diagnose && (missing.length || unresolved)) {
      console.log(`\n[chẩn đoán luồng #${i + 1}] điểm xuất phát: ${JSON.stringify(spec.start)}; đáp án bảng: ${expected.join(', ') || '(không)'}; tool thấy bảng: ${tables.join(', ') || '(không)'}`);
      const name = new Map((trace.participants ?? []).map((p) => [p.id, p.label]));
      for (const st of (trace.steps ?? []).filter((x) => x.confidence === 'UNRESOLVED')) {
        console.log(`   bước chưa xác định: ${name.get(st.from)} → ${name.get(st.to) ?? '?'} | ${st.label} | lý do: ${st.reason}`);
      }
      if (missing.length) console.log(`   thiếu bảng: ${missing.join(', ')}; các thành phần trong luồng: ${[...name.values()].join(' · ')}`);
    }
    if (!missing.length && !unresolved) ok++;
    else bad.push(`#${i + 1}: ${missing.length ? `thiếu ${missing.length}/${expected.length} bảng` : ''}${missing.length && unresolved ? ', ' : ''}${unresolved ? `${unresolved} bước chưa xác định` : ''}`);
  }
  const rate = ok / traces.length;
  row('NFR-09', `Trace đúng tới bảng (${ok}/${traces.length} luồng)`, fmtPct(rate), '≥ 90%', rate >= 0.9,
    `không tìm thấy điểm xuất phát ${reasons.khongTimThay}; thiếu bảng ${reasons.thieuBang}; có bước chưa xác định ${reasons.buocChuaXacDinh}; lỗi dò ${reasons.loiDoVet}`);
  if (args.verbose && bad.length) report.details.push('NFR-09 luồng sai: ' + bad.join('; '));
}

// ---------------- in kết quả ----------------
if (args.json) {
  console.log(JSON.stringify(report, null, 2));
} else {
  const mark = (p) => (p === true ? '✅' : p === false ? '❌' : '➖');
  console.log('\nĐỘ CHÍNH XÁC (số liệu tổng hợp)\n');
  console.log('| Chỉ tiêu | Nội dung | Đo được | Mục tiêu | Đạt | Ghi chú |');
  console.log('| :--- | :--- | :--- | :--- | :---: | :--- |');
  for (const r of report.rows) console.log(`| ${r.id} | ${r.name} | ${r.value} | ${r.target} | ${mark(r.pass)} | ${r.note} |`);
  for (const d of report.details) console.log('\n' + d);
}
