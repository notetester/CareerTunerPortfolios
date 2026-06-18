// 앵커 워크플로 산출(프레임별 callout 텍스트 앵커)을 c-flow.spec.json 에 반영.
// 기존 nx/ny/nw/nh(폴백)는 유지하고 anchors 배열만 추가/갱신.
// usage: node patch-anchors.mjs <workflow-output-file>
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");

const outFile = process.argv[2];
if (!outFile) { console.error("output file 경로 필요"); process.exit(1); }
const raw = await readFile(outFile, "utf8");
let parsed; try { parsed = JSON.parse(raw); } catch { const a = raw.indexOf("["); const b = raw.lastIndexOf("]"); parsed = JSON.parse(raw.slice(a, b + 1)); }
const results = Array.isArray(parsed) ? parsed : (parsed.result ?? parsed.frames ?? parsed.entries);
if (!Array.isArray(results)) throw new Error("앵커 결과 배열을 찾지 못함");

const byId = new Map(results.map((r) => [r.id, r]));
const spec = JSON.parse(await readFile(SPEC, "utf8"));
const report = [];
for (const f of spec.frames) {
  const r = byId.get(f.id);
  if (!r?.callouts) { report.push(`${f.num ?? f.id}: 결과없음`); continue; }
  const aByKey = new Map(r.callouts.map((c) => [`${c.n}:${c.target}`, c.anchors]));
  let n = 0;
  for (const c of f.callouts) {
    const anchors = aByKey.get(`${c.n}:${c.target}`);
    if (Array.isArray(anchors) && anchors.length) { c.anchors = anchors; n++; }
  }
  report.push(`${f.num ?? f.id}: anchors ${n}/${f.callouts.length}`);
}
await writeFile(SPEC, JSON.stringify(spec, null, 2), "utf8");
console.log("patched anchors\n" + report.join("\n"));
