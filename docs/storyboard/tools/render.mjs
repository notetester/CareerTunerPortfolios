// 스토리보드 HTML 렌더러.
// 입력: ../spec/c-flow.spec.json + ../assets/*.png
// 출력: ../output/storyboard.html  (웹/앱 나란히 + 정규화 좌표 네모박스 오버레이 + 캡션 + Mermaid 여정도)
// 좌표는 0~1 정규화라 표시 크기와 무관하게 정확히 얹힌다. GUI 조작 없음, 순수 데이터→HTML.
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");
const OUTDIR = path.resolve(__dirname, "../output");

const COLORS = {
  purple: "#6C5CE0", amber: "#B5790F", teal: "#0F8E6E", coral: "#C8501F",
  blue: "#1F6FC0", pink: "#B83A66", green: "#4E8A18", gray: "#6B6A66",
};
const cycle = ["purple", "amber", "teal", "coral", "blue", "pink"];
const col = (c, i) => COLORS[c] || COLORS[cycle[i % cycle.length]];
const esc = (s = "") => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

function sanId(s) { return "n_" + String(s).replace(/[^a-zA-Z0-9]/g, "_"); }

function mermaid(journey) {
  if (!journey?.nodes?.length) return "";
  const lines = ["flowchart LR"];
  for (const n of journey.nodes) {
    const label = (n.label || n.id) + (n.sub ? `<br/><small>${n.sub}</small>` : "");
    lines.push(`  ${sanId(n.id)}["${label}"]:::${n.current ? "cur" : (n.group || "user")}`);
  }
  for (const e of journey.edges || []) {
    const arrow = e.branch ? "-.->|" : "-->|";
    lines.push(e.label ? `  ${sanId(e.from)} ${arrow}${e.label}| ${sanId(e.to)}` : `  ${sanId(e.from)} --> ${sanId(e.to)}`);
  }
  lines.push("classDef cur fill:#EEEDFE,stroke:#6C5CE0,color:#26215C,stroke-width:2px;");
  lines.push("classDef user fill:#fff,stroke:#B4B2A9,color:#2C2C2A;");
  lines.push("classDef admin fill:#F1EFE8,stroke:#888780,color:#2C2C2A;");
  lines.push("classDef entry fill:#E1F5EE,stroke:#0F8E6E,color:#04342C;");
  lines.push("classDef state fill:#FAECE7,stroke:#C8501F,color:#4A1B0C;");
  return lines.join("\n");
}

function shot(file, callouts, target) {
  const cs = (callouts || []).filter((c) => (c.target || "web") === target);
  const boxes = cs.map((c, i) => {
    const color = col(c.color, c.n ? c.n - 1 : i);
    return `<div class="callout" style="left:${(c.nx * 100).toFixed(2)}%;top:${(c.ny * 100).toFixed(2)}%;width:${(c.nw * 100).toFixed(2)}%;height:${(c.nh * 100).toFixed(2)}%;border-color:${color};box-shadow:0 0 0 2px ${color}22;">`
      + `<span class="cbadge" style="background:${color}">${c.n ?? i + 1}</span></div>`;
  }).join("");
  return `<div class="shot"><img src="../assets/${esc(file)}" alt=""/>${boxes}</div>`;
}

function frameSection(f) {
  const colorByN = {};
  (f.callouts || []).forEach((c, i) => { colorByN[c.n ?? i + 1] = col(c.color, (c.n ?? i + 1) - 1); });
  const feats = (f.cFeatures || []).map((x) => `<span class="pill">${esc(x)}</span>`).join("");
  const narration = (f.narration || []).map((nrt, i) => {
    const color = colorByN[nrt.n ?? i + 1] || col(null, i);
    return `<li><span class="nbadge" style="background:${color}">${nrt.n ?? i + 1}</span>`
      + `<div><div class="ntitle">${esc(nrt.title)}${nrt.feature ? ` <span class="feat">${esc(nrt.feature)}</span>` : ""}</div>`
      + `<div class="ntext">${esc(nrt.text)}${nrt.goesTo ? ` <span class="goto">→ ${esc(nrt.goesTo)}</span>` : ""}</div></div></li>`;
  }).join("");
  const branches = (f.branches || []).length
    ? `<div class="branches"><span class="blabel">분기·상태</span>${f.branches.map((b) => `<span class="branch">${esc(b)}</span>`).join("")}</div>` : "";
  const groupTag = { user: "사용자", admin: "관리자", state: "상태" }[f.group] || f.group || "";
  return `<section class="frame">
  <div class="fhead">
    <span class="fnum">${esc(f.num ?? f.id)}</span>
    <h2>${esc(f.title)}</h2>
    <span class="gtag gtag-${esc(f.group)}">${esc(groupTag)}</span>
    <code class="route">${esc(f.route || "")}</code>
    <div class="feats">${feats}</div>
  </div>
  ${f.summary ? `<p class="summary">${esc(f.summary)}</p>` : ""}
  <div class="shots">
    <div class="shotcol"><div class="shotlabel">◻ 웹 · ${esc(f.web)}</div>${shot(f.web, f.callouts, "web")}</div>
    <div class="shotcol app"><div class="shotlabel">▢ 앱 · ${esc(f.app)}</div>${shot(f.app, f.callouts, "app")}</div>
  </div>
  <div class="caption">
    <div class="captitle">흐름 설명 <span class="capnote">— 번호 = 화면 위 네모박스</span></div>
    <ol class="narration">${narration}</ol>
    ${branches}
  </div>
</section>`;
}

async function main() {
  const spec = JSON.parse(await readFile(SPEC, "utf8"));
  await mkdir(OUTDIR, { recursive: true });
  const m = spec.meta || {};
  const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1"/>
<title>CareerTuner · C 영역 UI/UX 스토리보드</title>
<script type="module">import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs";mermaid.initialize({startOnLoad:true,theme:"neutral",flowchart:{htmlLabels:true}});</script>
<style>
:root{--ink:#1a1a1c;--muted:#5f5e5a;--hint:#8a8980;--line:#e6e4dd;--bg:#faf9f6;--card:#fff;--accent:#6C5CE0}
*{box-sizing:border-box}
body{font-family:'Pretendard',system-ui,-apple-system,'Segoe UI',sans-serif;color:var(--ink);background:var(--bg);margin:0;line-height:1.6}
.wrap{max-width:1180px;margin:0 auto;padding:40px 28px 80px}
.cover{padding:48px 0 28px;border-bottom:1px solid var(--line);margin-bottom:36px}
.cover .kic{display:inline-block;font-size:12px;letter-spacing:.12em;color:#fff;background:var(--accent);padding:4px 11px;border-radius:999px}
.cover h1{font-size:34px;margin:16px 0 6px;letter-spacing:-.01em}
.cover .sub{font-size:16px;color:var(--muted);margin:0}
.cover .meta{margin-top:18px;display:flex;gap:22px;flex-wrap:wrap;font-size:13px;color:var(--hint)}
.cover .meta b{color:var(--muted);font-weight:600}
.legend{margin-top:18px;font-size:12.5px;color:var(--muted);display:flex;gap:14px;align-items:center;flex-wrap:wrap}
.legend .lb{display:inline-flex;align-items:center;gap:6px}
.legend .box{width:20px;height:13px;border:2px dashed var(--accent);border-radius:3px}
h2{font-size:20px;margin:0}
.flowwrap{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:22px;margin-bottom:40px}
.flowwrap h3{margin:0 0 4px;font-size:16px}
.flowwrap .fn{margin:0 0 14px;font-size:13px;color:var(--hint)}
.mermaid{overflow-x:auto}
.frame{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:24px 26px 26px;margin-bottom:30px}
.fhead{display:flex;align-items:center;gap:11px;flex-wrap:wrap;margin-bottom:8px}
.fnum{flex:0 0 auto;min-width:30px;height:30px;padding:0 8px;border-radius:8px;background:var(--accent);color:#fff;font-size:14px;font-weight:600;display:flex;align-items:center;justify-content:center}
.gtag{font-size:11px;padding:3px 8px;border-radius:999px;font-weight:600}
.gtag-user{background:#EEEDFE;color:#4A3FB0}.gtag-admin{background:#F1EFE8;color:#5f5e5a}.gtag-state{background:#FAECE7;color:#993C1D}
.route{font-family:ui-monospace,'SF Mono',monospace;font-size:12px;color:var(--muted);background:var(--bg);padding:2px 7px;border-radius:6px;border:1px solid var(--line)}
.feats{display:flex;gap:6px;flex-wrap:wrap}
.pill{font-size:11px;color:#4A3FB0;background:#EEEDFE;border-radius:999px;padding:3px 9px}
.summary{margin:2px 0 16px;color:var(--muted);font-size:14px}
.shots{display:flex;gap:20px;align-items:flex-start}
.shotcol{flex:1;min-width:0}.shotcol.app{flex:0 0 300px}
.shotlabel{font-size:11px;color:var(--hint);letter-spacing:.04em;margin-bottom:6px;font-family:ui-monospace,monospace}
.shot{position:relative;border:1px solid var(--line);border-radius:10px;overflow:hidden;background:#fff}
.shot img{display:block;width:100%;height:auto}
.callout{position:absolute;border:2px dashed;border-radius:7px;pointer-events:none}
.cbadge{position:absolute;top:-11px;left:-11px;width:21px;height:21px;border-radius:50%;color:#fff;font-size:12px;font-weight:600;display:flex;align-items:center;justify-content:center;box-shadow:0 1px 3px #0003}
.caption{margin-top:20px;padding-top:18px;border-top:1px solid var(--line)}
.captitle{font-size:15px;font-weight:600;margin-bottom:12px}.capnote{font-size:12px;color:var(--hint);font-weight:400}
.narration{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:11px}
.narration li{display:flex;gap:11px;align-items:flex-start}
.nbadge{flex:0 0 21px;width:21px;height:21px;border-radius:50%;color:#fff;font-size:12px;font-weight:600;display:flex;align-items:center;justify-content:center;margin-top:1px}
.ntitle{font-weight:600;font-size:14px}.feat{font-size:11px;color:var(--accent);background:#EEEDFE;border-radius:5px;padding:1px 6px;font-weight:500}
.ntext{font-size:13.5px;color:var(--muted)}.goto{color:var(--accent);font-weight:500}
.branches{margin-top:14px;display:flex;gap:8px;flex-wrap:wrap;align-items:center}
.blabel{font-size:11px;color:var(--hint)}
.branch{font-size:12px;background:#FAECE7;color:#993C1D;border-radius:6px;padding:3px 9px}
@media print{body{background:#fff}.frame,.flowwrap{break-inside:avoid;border-color:#ccc}.wrap{max-width:none}}
@media(max-width:760px){.shots{flex-direction:column}.shotcol.app{flex:1}}
</style></head><body><div class="wrap">
<div class="cover">
  <span class="kic">STORYBOARD · C 영역</span>
  <h1>${esc(m.title || "CareerTuner — 스펙 비교·취업 분석·대시보드 UI/UX 스토리보드")}</h1>
  <p class="sub">${esc(m.subtitle || "채용공고 적합도 분석부터 학습·전략 추천, 장기 취업 경향까지 — 사용자 여정과 관리자 운영 화면")}</p>
  <div class="meta">
    <span><b>영역</b> C · 홈/스펙비교/취업분석/대시보드</span>
    <span><b>데모 사용자</b> ${esc(m.user || "김데모")}</span>
    <span><b>출처</b> ${esc(m.source || "mock 데모 빌드(VITE_USE_MOCK)")}</span>
    <span><b>생성</b> ${esc(m.date || "")}</span>
  </div>
  <div class="legend"><span class="lb"><span class="box"></span>네모박스 = 설명 지점</span><span class="lb">번호 ↔ 아래 캡션 번호</span><span class="lb">웹·앱 동일 기능 나란히</span></div>
</div>
<div class="flowwrap">
  <h3>${esc(spec.journey?.title || "C 가치 여정 — 화면 흐름")}</h3>
  <p class="fn">로그인 후 어떤 순서로 화면을 거치며 C의 가치(적합도→보완→전략→경향)가 이어지는지</p>
  <pre class="mermaid">${esc(mermaid(spec.journey))}</pre>
</div>
${(spec.frames || []).map(frameSection).join("\n")}
</div></body></html>`;
  await writeFile(path.join(OUTDIR, "storyboard.html"), html, "utf8");
  console.log("rendered -> output/storyboard.html  (frames: " + (spec.frames?.length || 0) + ")");
}

main().catch((e) => { console.error(e); process.exit(1); });
