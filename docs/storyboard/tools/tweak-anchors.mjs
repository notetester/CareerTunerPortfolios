// 검증 후 미세 조정: 특정 callout 의 앵커를 더 타이트하게 교체.
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");

// frameId → { "n:target": [anchors...] }
const OVERRIDES = {
  "04-detail-overview": { "1:web": ["적합도"] }, // '기업 분석' 제거 → 적합도 탭만 타이트하게
};

const spec = JSON.parse(await readFile(SPEC, "utf8"));
let n = 0;
for (const f of spec.frames) {
  const ov = OVERRIDES[f.id]; if (!ov) continue;
  for (const c of f.callouts) {
    const key = `${c.n}:${c.target || "web"}`;
    if (ov[key]) { c.anchors = ov[key]; n++; }
  }
}
await writeFile(SPEC, JSON.stringify(spec, null, 2), "utf8");
console.log(`tweaked ${n} callout(s)`);
