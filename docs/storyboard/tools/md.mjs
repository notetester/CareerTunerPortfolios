// 마크다운 스토리보드 생성(버전관리·GitHub·PR용).
// 주석 합성 이미지(output/annotated) + Mermaid 여정도 + 번호 캡션.
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");
const OUT = path.resolve(__dirname, "../output");

function sanId(s) { return "n_" + String(s).replace(/[^a-zA-Z0-9]/g, "_"); }
function mermaid(j) {
  if (!j?.nodes?.length) return "";
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
let md = `# ${m.title || "CareerTuner — C 영역 UI/UX 스토리보드"}\n\n`;
md += `> ${m.subtitle || ""}\n\n`;
md += `| 영역 | 데모 사용자 | 출처 | 생성 |\n|---|---|---|---|\n| C · 홈/스펙비교/취업분석/대시보드 | ${m.user || "김데모"} | ${m.source || "mock"} | ${m.date || ""} |\n\n`;
md += `네모 박스 = 설명 지점, 번호 ↔ 아래 캡션 번호. 웹·앱은 동일 기능을 다른 폼팩터로 나란히 캡처했다.\n\n`;
md += `## C 가치 여정 — 화면 흐름\n\n로그인 후 어떤 순서로 화면을 거치며 C의 가치(적합도→보완→전략→경향)가 이어지는지.\n\n`;
md += "```mermaid\n" + mermaid(spec.journey) + "\n```\n\n---\n\n";

for (const f of spec.frames || []) {
  md += `## ${f.num ?? f.id}. ${f.title}\n\n`;
  md += `\`${GTAG[f.group] || f.group}\` · 경로 \`${f.route || ""}\``;
  if (f.cFeatures?.length) md += ` · ${f.cFeatures.join(" · ")}`;
  md += `\n\n`;
  if (f.summary) md += `${f.summary}\n\n`;
  md += `<p align="left">`;
  md += `<img src="annotated/${f.id}-web.png" alt="${f.title} 웹" width="660">&nbsp;`;
  md += `<img src="annotated/${f.id}-app.png" alt="${f.title} 앱" width="200">`;
  md += `</p>\n\n`;
  md += `**흐름 설명**\n\n`;
  for (const [i, n] of (f.narration || []).entries()) {
    const feat = n.feature ? ` \`${n.feature}\`` : "";
    const goto = n.goesTo ? ` _→ ${n.goesTo}_` : "";
    md += `${n.n ?? i + 1}. **${n.title}**${feat} — ${n.text}${goto}\n`;
  }
  if (f.branches?.length) md += `\n**분기·상태:** ${f.branches.join(" · ")}\n`;
  md += `\n---\n\n`;
}
await writeFile(path.join(OUT, "storyboard.md"), md, "utf8");
console.log("md -> output/storyboard.md (frames: " + (spec.frames?.length || 0) + ")");
