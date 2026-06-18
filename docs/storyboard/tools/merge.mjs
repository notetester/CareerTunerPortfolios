// 워크플로 산출(프레임 배열) + spec-base(meta·journey) → c-flow.spec.json 병합.
// usage: node merge.mjs <workflow-output-file>
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPECDIR = path.resolve(__dirname, "../spec");

const outFile = process.argv[2];
if (!outFile) { console.error("output file 경로 필요"); process.exit(1); }

const raw = await readFile(outFile, "utf8");
let parsed;
try { parsed = JSON.parse(raw); }
catch { const a = raw.indexOf("["); const b = raw.lastIndexOf("]"); parsed = JSON.parse(raw.slice(a, b + 1)); }
// 워크플로 출력은 { summary, result:[...] } 래퍼이거나 배열 그 자체일 수 있다.
let entries = Array.isArray(parsed) ? parsed : (parsed.result ?? parsed.frames ?? parsed.entries);
if (!Array.isArray(entries)) throw new Error("프레임 배열을 찾지 못함");

// 정규화: 좌표 0~1 클램프, num 보정, 정렬
const clamp = (v) => Math.max(0, Math.min(1, Number(v) || 0));
for (const f of entries) {
  for (const c of f.callouts || []) {
    c.nx = clamp(c.nx); c.ny = clamp(c.ny);
    c.nw = Math.min(clamp(c.nw), 1 - c.nx); c.nh = Math.min(clamp(c.nh), 1 - c.ny);
  }
}
entries.sort((x, y) => String(x.num ?? x.id).localeCompare(String(y.num ?? y.id)));

const base = JSON.parse(await readFile(path.join(SPECDIR, "spec-base.json"), "utf8"));
const spec = { meta: base.meta, journey: base.journey, frames: entries };
await writeFile(path.join(SPECDIR, "c-flow.spec.json"), JSON.stringify(spec, null, 2), "utf8");
console.log(`merged -> spec/c-flow.spec.json (frames: ${entries.length})`);
console.log("frame ids: " + entries.map((e) => e.id).join(", "));
console.log("callout counts: " + entries.map((e) => `${e.num}:${(e.callouts || []).length}`).join("  "));
