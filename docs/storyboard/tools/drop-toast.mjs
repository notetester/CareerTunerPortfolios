// 저가치·위치 불안정한 "공고문 추출 토스트" callout 을 스펙에서 제거하고 프레임별 번호를 1..k 로 정리.
// (토스트 DOM 자체는 화면에 남되, 설명 박스/캡션만 뺀다.)
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");
const TOAST = /공고문 추출|추출이 진행|추출 진행/;

const spec = JSON.parse(await readFile(SPEC, "utf8"));
const report = [];
for (const f of spec.frames) {
  const isToast = (c) => (c.anchors || []).some((a) => TOAST.test(a)) || TOAST.test(c.label || "");
  const removeNs = new Set(f.callouts.filter(isToast).map((c) => c.n));
  if (!removeNs.size) { report.push(`${f.num}: 변경없음`); continue; }
  f.callouts = f.callouts.filter((c) => !removeNs.has(c.n));
  f.narration = (f.narration || []).filter((n) => !removeNs.has(n.n));
  // 번호 재정렬(1..k)
  const remaining = [...new Set(f.callouts.map((c) => c.n))].sort((a, b) => a - b);
  const map = new Map(remaining.map((old, i) => [old, i + 1]));
  f.callouts.forEach((c) => { c.n = map.get(c.n); });
  f.narration.forEach((n) => { if (map.has(n.n)) n.n = map.get(n.n); });
  report.push(`${f.num}: 토스트 ${removeNs.size}개 제거 → ${f.callouts.length}개`);
}
await writeFile(SPEC, JSON.stringify(spec, null, 2), "utf8");
console.log(report.join("\n"));
