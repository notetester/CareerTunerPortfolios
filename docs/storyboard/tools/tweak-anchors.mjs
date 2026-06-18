// 검증 후 미세 조정: 특정 callout 의 앵커를 더 타이트하게 교체.
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");

// frameId → { "n:target": [anchors...] }
const OVERRIDES = {
  "04-detail-overview": { "1:web": ["적합도"] }, // '기업 분석' 제거 → 적합도 탭만 타이트하게
  "05-fit": { "5:web": ["AI 제안", "확인 필요"] }, // '신뢰도 보통' 제거 — ④ '분석 신뢰도 보통'과 충돌해 박스가 ②③④ 침범
  "06-admin-home": { "1:web": ["적합도 분석 실패"], "2:web": ["재분석 요청"], "3:web": ["장기 분석 실패"] }, // 대각선 2카드 span 방지 → 단일 카드
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
