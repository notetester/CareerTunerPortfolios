// Notion 페이지용 마크다운 생성(이미지 없이, Mermaid 코드블록 + 캡션).
// 출력: output/notion-page.md  → create-pages content 로 사용.
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");
const OUT = path.resolve(__dirname, "../output");

const sanId = (s) => "n_" + String(s).replace(/[^a-zA-Z0-9]/g, "_");
function mermaid(j) {
  const L = ["flowchart LR"];
  for (const n of j.nodes) {
    const label = (n.label || n.id) + (n.sub ? `<br/>${n.sub}` : "");
    L.push(`  ${sanId(n.id)}["${label}"]:::${n.current ? "cur" : (n.group || "user")}`);
  }
  for (const e of j.edges || []) {
    const a = e.branch ? "-.->" : "-->";
    L.push(e.label ? `  ${sanId(e.from)} ${a}|${e.label}| ${sanId(e.to)}` : `  ${sanId(e.from)} ${a} ${sanId(e.to)}`);
  }
  L.push("classDef cur fill:#EEEDFE,stroke:#6C5CE0,color:#26215C,stroke-width:2px;");
  L.push("classDef user fill:#ffffff,stroke:#B4B2A9,color:#2C2C2A;");
  L.push("classDef admin fill:#F1EFE8,stroke:#888780,color:#2C2C2A;");
  L.push("classDef entry fill:#E1F5EE,stroke:#0F8E6E,color:#04342C;");
  L.push("classDef state fill:#FAECE7,stroke:#C8501F,color:#4A1B0C;");
  return L.join("\n");
}
const GTAG = { user: "사용자", admin: "관리자", state: "상태" };

const spec = JSON.parse(await readFile(SPEC, "utf8"));
const m = spec.meta || {};
let md = `> ${m.subtitle || ""}\n\n`;
md += `**영역** C · 홈/스펙비교/취업분석/대시보드 · **데모 사용자** ${m.user} · **출처** ${m.source} · **생성** ${m.date}\n\n`;
md += `## C 가치 여정\n\n로그인 후 적합도 → 보완 → 전략 → 경향으로 이어지는 화면 흐름.\n\n`;
md += "```mermaid\n" + mermaid(spec.journey) + "\n```\n\n";
md += `## 화면별 흐름\n\n각 화면의 핵심 요소와 동작·이동을 정리했다. 화면 캡처(주석 포함)는 PPTX/PDF 스토리보드를 참조.\n\n`;
for (const f of spec.frames || []) {
  md += `### ${f.num}. ${f.title} \`${GTAG[f.group] || f.group}\`\n\n`;
  md += `경로 \`${f.route}\`${f.cFeatures?.length ? ` · ${f.cFeatures.join(" · ")}` : ""}\n\n`;
  if (f.summary) md += `${f.summary}\n\n`;
  for (const [i, n] of (f.narration || []).entries()) {
    md += `${n.n ?? i + 1}. **${n.title}**${n.feature ? ` (${n.feature})` : ""} — ${n.text}${n.goesTo ? ` → ${n.goesTo}` : ""}\n`;
  }
  if (f.branches?.length) md += `\n_분기·상태: ${f.branches.join(" · ")}_\n`;
  md += `\n`;
}
md += `## 산출물\n\n- PPTX/PDF 스토리보드(웹·앱 캡처 + 주석 + 여정도)\n- 통합 기획서 · C 파트 DB 설계서\n- 코드: \`docs/storyboard/\` (캡처→스펙→렌더 파이프라인, 재현 가능)\n`;
await writeFile(path.join(OUT, "notion-page.md"), md, "utf8");
console.log("notion -> output/notion-page.md (" + md.length + " chars)");
