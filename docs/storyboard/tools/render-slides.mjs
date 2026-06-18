// 16:9 슬라이드 렌더러 — 각 프레임을 1280x720 .slide 로 CSS 레이아웃(웹·앱·캡션 박스 배치).
// export.mjs slides 로 각 .slide 를 캡처 → build.py 가 full-bleed PPTX 로 만든다.
// (텍스트박스 욱여넣기 폐기. 레이아웃은 전부 CSS flex.)
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");
const OUT = path.resolve(__dirname, "../output");

const COLORS = { purple:"#6C5CE0", amber:"#B5790F", teal:"#0F8E6E", coral:"#C8501F", blue:"#1F6FC0", pink:"#B83A66", green:"#4E8A18", gray:"#6B6A66" };
const CYCLE = ["purple","amber","teal","coral","blue","pink"];
const col = (c,i)=> COLORS[c] || COLORS[CYCLE[i%CYCLE.length]];
const esc = (s="")=> String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;");
const sanId = (s)=> "n_"+String(s).replace(/[^a-zA-Z0-9]/g,"_");

function mermaid(j){
  if(!j?.nodes?.length) return "";
  const L=["flowchart LR"];
  for(const n of j.nodes){ const label=(n.label||n.id)+(n.sub?`<br/>${n.sub}`:""); L.push(`  ${sanId(n.id)}["${label}"]:::${n.current?"cur":(n.group||"user")}`); }
  for(const e of j.edges||[]){ const a=e.branch?"-.->":"-->"; L.push(e.label?`  ${sanId(e.from)} ${a}|${e.label}| ${sanId(e.to)}`:`  ${sanId(e.from)} ${a} ${sanId(e.to)}`); }
  L.push("classDef cur fill:#EEEDFE,stroke:#6C5CE0,color:#26215C,stroke-width:2px;");
  L.push("classDef user fill:#ffffff,stroke:#B4B2A9,color:#2C2C2A;");
  L.push("classDef admin fill:#F1EFE8,stroke:#888780,color:#2C2C2A;");
  L.push("classDef entry fill:#E1F5EE,stroke:#0F8E6E,color:#04342C;");
  L.push("classDef state fill:#FAECE7,stroke:#C8501F,color:#4A1B0C;");
  return L.join("\n");
}

const GTAG = { user:"사용자", admin:"관리자", state:"상태" };

function frameSlide(f, idx){
  const colorByN={}; (f.callouts||[]).forEach((c,i)=>{ colorByN[c.n??i+1]=col(c.color,(c.n??i+1)-1); });
  const feats=(f.cFeatures||[]).map(x=>`<span class="pill">${esc(x)}</span>`).join("");
  const caps=(f.narration||[]).map((n,i)=>{
    const c=colorByN[n.n??i+1]||col(null,i);
    return `<li><span class="nb" style="background:${c}">${n.n??i+1}</span><div class="ntx"><span class="nt">${esc(n.title)}</span>${n.feature?`<span class="ft">${esc(n.feature)}</span>`:""}<div class="nd">${esc(n.text)}${n.goesTo?`<span class="go"> → ${esc(n.goesTo)}</span>`:""}</div></div></li>`;
  }).join("");
  const branches=(f.branches||[]).length?`<div class="brs"><span class="brl">분기·상태</span>${f.branches.slice(0,4).map(b=>`<span class="br">${esc(b)}</span>`).join("")}</div>`:"";
  return `<div class="slide frame">
  <div class="thead">
    <span class="fnum">${esc(f.num??f.id)}</span>
    <span class="ttl">${esc(f.title)}</span>
    <span class="gtag gtag-${esc(f.group)}">${esc(GTAG[f.group]||f.group)}</span>
    <code class="route">${esc(f.route||"")}</code>
    <span class="feats">${feats}</span>
  </div>
  <div class="body">
    <div class="webcol">
      <div class="lab">웹 · 1440</div>
      <div class="imgbox"><img src="annotated/${esc(f.id)}-web.png" alt=""/></div>
    </div>
    <div class="appcol">
      <div class="lab">앱 · 390</div>
      <div class="imgbox"><img src="annotated/${esc(f.id)}-app.png" alt=""/></div>
    </div>
    <div class="capcol">
      <div class="captitle">흐름 설명</div>
      <ol class="caps">${caps}</ol>
      ${branches}
    </div>
  </div>
</div>`;
}

const spec = JSON.parse(await readFile(SPEC,"utf8"));
const m = spec.meta||{};
const cover = `<div class="slide cover">
  <div class="cwrap">
    <span class="kic">STORYBOARD · C 영역</span>
    <h1>${esc(m.title||"CareerTuner — C 영역 UI/UX 스토리보드")}</h1>
    <p class="csub">${esc(m.subtitle||"")}</p>
    <p class="cmeta">데모 사용자 ${esc(m.user||"김데모")}  ·  출처 ${esc(m.source||"")}  ·  생성 ${esc(m.date||"")}</p>
  </div>
</div>`;
const flow = `<div class="slide flow">
  <h2>${esc(spec.journey?.title||"C 가치 여정 — 화면 흐름")}</h2>
  <p class="fsub">로그인 후 어떤 순서로 화면을 거치며 C의 가치(적합도 → 보완 → 전략 → 경향)가 이어지는지</p>
  <div class="mwrap"><pre class="mermaid">${esc(mermaid(spec.journey))}</pre></div>
</div>`;

const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8"/>
<title>slides</title>
<script type="module">import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs";mermaid.initialize({startOnLoad:true,theme:"neutral",flowchart:{htmlLabels:true,useMaxWidth:true}});</script>
<style>
:root{--ink:#1a1a1c;--muted:#5f5e5a;--hint:#8a8980;--line:#e7e5de;--accent:#6C5CE0}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:'Pretendard',system-ui,'Segoe UI','Malgun Gothic',sans-serif;color:var(--ink);background:#cfcdc6}
.slide{width:1280px;height:720px;background:#fff;overflow:hidden;position:relative;display:flex;flex-direction:column}
/* 표지 */
.cover{background:#100E27;justify-content:center}
.cover .cwrap{padding:0 90px}
.cover .kic{display:inline-block;font-size:18px;letter-spacing:.14em;color:#AEA8F6;border:1px solid #3a356e;padding:6px 16px;border-radius:999px}
.cover h1{color:#fff;font-size:52px;font-weight:700;line-height:1.18;margin:26px 0 14px;letter-spacing:-.01em}
.cover .csub{color:#C8C6DC;font-size:22px;line-height:1.5;max-width:980px}
.cover .cmeta{color:#8e8cae;font-size:16px;margin-top:30px}
/* 여정 */
.flow{padding:40px 56px}
.flow h2{font-size:34px;font-weight:700}
.flow .fsub{color:var(--hint);font-size:17px;margin-top:8px}
.flow .mwrap{flex:1;display:flex;align-items:center;justify-content:center;margin-top:8px}
.flow .mermaid{width:100%;font-size:17px}
/* 프레임 */
.thead{height:74px;flex:0 0 74px;display:flex;align-items:center;gap:12px;padding:0 40px;border-bottom:1px solid var(--line)}
.fnum{min-width:38px;height:38px;padding:0 10px;border-radius:9px;background:var(--accent);color:#fff;font-size:20px;font-weight:700;display:flex;align-items:center;justify-content:center}
.ttl{font-size:24px;font-weight:700}
.gtag{font-size:13px;padding:4px 11px;border-radius:999px;font-weight:600}
.gtag-user{background:#EEEDFE;color:#4A3FB0}.gtag-admin{background:#F1EFE8;color:#5f5e5a}.gtag-state{background:#FAECE7;color:#993C1D}
.route{font-family:ui-monospace,'Consolas',monospace;font-size:14px;color:var(--muted);background:#f6f5f1;padding:3px 9px;border-radius:6px;border:1px solid var(--line)}
.feats{display:flex;gap:7px;flex-wrap:wrap;margin-left:4px;overflow:hidden}
.pill{font-size:13px;color:#4A3FB0;background:#EEEDFE;border-radius:999px;padding:4px 11px;white-space:nowrap}
.body{flex:1;display:flex;gap:24px;padding:22px 40px;min-height:0}
.webcol{flex:1 1 auto;min-width:0;display:flex;flex-direction:column}
.appcol{flex:0 0 208px;display:flex;flex-direction:column}
.capcol{flex:0 0 372px;display:flex;flex-direction:column;min-height:0}
.lab{font-size:13px;color:var(--hint);font-family:ui-monospace,monospace;margin-bottom:7px;letter-spacing:.04em}
.imgbox{flex:1;min-height:0;display:flex;align-items:flex-start;justify-content:center;border:1px solid var(--line);border-radius:12px;overflow:hidden;background:#fff}
.imgbox img{max-width:100%;max-height:100%;object-fit:contain;object-position:top}
.captitle{font-size:18px;font-weight:700;margin-bottom:12px}
.caps{list-style:none;display:flex;flex-direction:column;gap:11px}
.caps li{display:flex;gap:10px;align-items:flex-start}
.nb{flex:0 0 23px;width:23px;height:23px;border-radius:50%;color:#fff;font-size:13px;font-weight:700;display:flex;align-items:center;justify-content:center;margin-top:1px}
.ntx{min-width:0}
.nt{font-size:14.5px;font-weight:700}
.ft{font-size:12px;color:var(--accent);background:#EEEDFE;border-radius:5px;padding:1px 7px;margin-left:5px;white-space:nowrap}
.nd{font-size:13px;color:var(--muted);line-height:1.45;margin-top:2px}
.go{color:var(--accent);font-weight:600}
.brs{margin-top:13px;display:flex;gap:7px;flex-wrap:wrap;align-items:center}
.brl{font-size:12px;color:var(--hint)}
.br{font-size:12px;background:#FAECE7;color:#993C1D;border-radius:6px;padding:3px 9px}
</style></head><body>
${cover}
${flow}
${(spec.frames||[]).map(frameSlide).join("\n")}
</body></html>`;
await writeFile(path.join(OUT,"slides.html"), html, "utf8");
console.log("slides -> output/slides.html (slides: "+((spec.frames?.length||0)+2)+")");
