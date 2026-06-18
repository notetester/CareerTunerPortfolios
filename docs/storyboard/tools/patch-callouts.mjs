// 검증 워크플로 산출(프레임별 교정 callouts)을 c-flow.spec.json 에 반영.
// usage: node patch-callouts.mjs <workflow-output-file>
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");

const outFile = process.argv[2];
if (!outFile) { console.error("output file 경로 필요"); process.exit(1); }

const raw = await readFile(outFile, "utf8");
let parsed;
try { parsed = JSON.parse(raw); } catch { const a = raw.indexOf("["); const b = raw.lastIndexOf("]"); parsed = JSON.parse(raw.slice(a, b + 1)); }
const results = Array.isArray(parsed) ? parsed : (parsed.result ?? parsed.frames ?? parsed.entries);
if (!Array.isArray(results)) throw new Error("교정 결과 배열을 찾지 못함");

const clamp = (v) => Math.max(0, Math.min(1, Number(v) || 0));
const byId = new Map(results.map((r) => [r.id, r]));

const spec = JSON.parse(await readFile(SPEC, "utf8"));
let totalChanged = 0;
const report = [];
for (const f of spec.frames) {
  const r = byId.get(f.id);
  if (!r || !Array.isArray(r.callouts)) { report.push(`${f.num ?? f.id}: (결과 없음, 유지)`); continue; }
  // verdict 필드는 떼고 좌표만 반영. 클램프.
  f.callouts = r.callouts.map((c) => ({
    n: c.n, target: c.target || "web",
    nx: clamp(c.nx), ny: clamp(c.ny),
    nw: Math.min(clamp(c.nw), 1 - clamp(c.nx)), nh: Math.min(clamp(c.nh), 1 - clamp(c.ny)),
    color: c.color, label: c.label,
  }));
  const changed = r.changedCount ?? r.callouts.filter((c) => c.verdict === "corrected").length;
  totalChanged += changed;
  report.push(`${f.num ?? f.id}: ${changed}개 교정 / ${r.callouts.length}개`);
}
await writeFile(SPEC, JSON.stringify(spec, null, 2), "utf8");
console.log(`patched spec — 총 ${totalChanged}개 교정`);
console.log(report.join("\n"));
