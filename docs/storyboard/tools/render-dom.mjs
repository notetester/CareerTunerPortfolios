// 자체완결 정적 스토리보드: 각 화면을 srcdoc iframe(DOM만)으로 렌더하고,
// 앱 CSS 1벌을 열릴 때 각 iframe 에 주입한다(React/서버/이미지 불필요, 더블클릭으로 열림).
// 그 위에 스펙의 callout 박스·캡션을 오버레이. 출력: ../output/storyboard-static.html
import { readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");
const SNAP = path.resolve(__dirname, "../snapshots");
const OUT = path.resolve(__dirname, "../output");

const COLORS = { purple:"#6C5CE0", amber:"#B5790F", teal:"#0F8E6E", coral:"#C8501F", blue:"#1F6FC0", pink:"#B83A66", green:"#4E8A18", gray:"#6B6A66" };
const CYCLE = ["purple","amber","teal","coral","blue","pink"];
const col = (c,i)=> COLORS[c] || COLORS[CYCLE[i%CYCLE.length]];
const esc = (s="")=> String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;");
const attr = (s="")=> String(s).replace(/&/g,"&amp;").replace(/"/g,"&quot;"); // srcdoc 속성용
const sanId = (s)=> "n_"+String(s).replace(/[^a-zA-Z0-9]/g,"_");

// 화면 표시 크기(자연 크기를 scale 로 축소, 오버레이는 wrap 기준 %)
const VP = { web: { w:1440, h:1024, dw:680 }, app: { w:390, h:844, dw:232 } };

function mermaid(j){
  const L=["flowchart LR"];
  for (const n of j.nodes){ const label=(n.label||n.id)+(n.sub?`<br/>${n.sub}`:""); L.push(`  ${sanId(n.id)}["${label}"]:::${n.current?"cur":(n.group||"user")}`); }
  for (const e of j.edges||[]){ const a=e.branch?"-.->":"-->"; L.push(e.label?`  ${sanId(e.from)} ${a}|${e.label}| ${sanId(e.to)}`:`  ${sanId(e.from)} ${a} ${sanId(e.to)}`); }
  L.push("classDef cur fill:#EEEDFE,stroke:#6C5CE0,color:#26215C,stroke-width:2px;","classDef user fill:#fff,stroke:#B4B2A9,color:#2C2C2A;","classDef admin fill:#F1EFE8,stroke:#888780,color:#2C2C2A;","classDef entry fill:#E1F5EE,stroke:#0F8E6E,color:#04342C;","classDef state fill:#FAECE7,stroke:#C8501F,color:#4A1B0C;");
  return L.join("\n");
}
const GTAG = { user:"사용자", admin:"관리자", state:"상태" };

async function screen(srcdocHtml, callouts, target, frameId) {
  const vp = VP[target];
  const cs = (callouts||[]).filter((c)=>(c.target||"web")===target).map((c,i)=>{
    const color = col(c.color, (c.n??i+1)-1);
    return `<div class="co" style="left:${(c.nx*100).toFixed(2)}%;top:${(c.ny*100).toFixed(2)}%;width:${(c.nw*100).toFixed(2)}%;height:${(c.nh*100).toFixed(2)}%;border-color:${color};box-shadow:0 0 0 2px ${color}22"><span class="cb" style="background:${color}">${c.n??i+1}</span></div>`;
  }).join("");
  const scale = vp.dw / vp.w;
  return `<div class="scr ${target}" style="width:${vp.dw}px;height:${Math.round(vp.h*scale)}px">`
    + `<iframe class="screen" loading="lazy" title="${frameId}-${target}" style="width:${vp.w}px;height:${vp.h}px;transform:scale(${scale.toFixed(4)})" srcdoc="${attr(srcdocHtml)}"></iframe>`
    + cs + `</div>`;
}

async function frameSection(f){
  const colorByN = {}; (f.callouts||[]).forEach((c,i)=>{ colorByN[c.n??i+1]=col(c.color,(c.n??i+1)-1); });
  const feats = (f.cFeatures||[]).map((x)=>`<span class="pill">${esc(x)}</span>`).join("");
  const webDom = await readFile(path.join(SNAP, "dom", `${f.id}-web.html`), "utf8").catch(()=> "");
  const appDom = await readFile(path.join(SNAP, "dom", `${f.id}-app.html`), "utf8").catch(()=> "");
  const narration = (f.narration||[]).map((n,i)=>{
    const c = colorByN[n.n??i+1] || col(null,i);
    return `<li><span class="nb" style="background:${c}">${n.n??i+1}</span><div><div class="nt">${esc(n.title)}${n.feature?` <span class="ft">${esc(n.feature)}</span>`:""}</div><div class="nd">${esc(n.text)}${n.goesTo?` <span class="go">→ ${esc(n.goesTo)}</span>`:""}</div></div></li>`;
  }).join("");
  const branches = (f.branches||[]).length ? `<div class="brs"><span class="brl">분기·상태</span>${f.branches.map((b)=>`<span class="br">${esc(b)}</span>`).join("")}</div>` : "";
  return `<section class="frame">
  <div class="fhead"><span class="fnum">${esc(f.num??f.id)}</span><h2>${esc(f.title)}</h2><span class="gtag gtag-${esc(f.group)}">${esc(GTAG[f.group]||f.group)}</span><code class="route">${esc(f.route||"")}</code><span class="feats">${feats}</span></div>
  ${f.summary?`<p class="summary">${esc(f.summary)}</p>`:""}
  <div class="shots"><div class="col"><div class="lab">◻ 웹 · 1440</div>${await screen(webDom, f.callouts, "web", f.id)}</div><div class="col"><div class="lab">▢ 앱 · 390</div>${await screen(appDom, f.callouts, "app", f.id)}</div>
    <div class="capcol"><div class="captitle">흐름 설명</div><ol class="caps">${narration}</ol>${branches}</div></div>
</section>`;
}

const spec = JSON.parse(await readFile(SPEC, "utf8"));
const appCss = await readFile(path.join(SNAP, "_app.css"), "utf8");
const m = spec.meta || {};
const frames = [];
for (const f of spec.frames || []) frames.push(await frameSection(f));

const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>CareerTuner · C 영역 UI/UX 스토리보드 (정적)</title>
<script type="text/css" id="appcss">${appCss.replace(/<\/script>/gi, "<\\/script>")}</script>
<script type="module">import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs";mermaid.initialize({startOnLoad:true,theme:"neutral",flowchart:{htmlLabels:true,useMaxWidth:true}});</script>
<style>
:root{--ink:#1a1a1c;--muted:#5f5e5a;--hint:#8a8980;--line:#e6e4dd;--bg:#faf9f6;--card:#fff;--accent:#6C5CE0}
*{box-sizing:border-box} body{font-family:'Pretendard',system-ui,'Segoe UI','Malgun Gothic',sans-serif;color:var(--ink);background:var(--bg);margin:0;line-height:1.6}
.wrap{max-width:1200px;margin:0 auto;padding:36px 24px 80px}
.cover{padding:40px 0 24px;border-bottom:1px solid var(--line);margin-bottom:30px}
.cover .kic{display:inline-block;font-size:12px;letter-spacing:.12em;color:#fff;background:var(--accent);padding:4px 11px;border-radius:999px}
.cover h1{font-size:30px;margin:14px 0 6px} .cover .sub{color:var(--muted);margin:0}
.cover .meta{margin-top:14px;font-size:12.5px;color:var(--hint)} .cover .note{margin-top:10px;font-size:12px;color:var(--hint);background:#fff;border:1px solid var(--line);border-radius:8px;padding:8px 12px}
.flowwrap{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:20px;margin-bottom:34px}
.flowwrap h3{margin:0 0 10px;font-size:16px} .mermaid{overflow-x:auto}
.frame{background:var(--card);border:1px solid var(--line);border-radius:16px;padding:22px 24px;margin-bottom:26px}
.fhead{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:6px}
.fnum{min-width:30px;height:30px;padding:0 8px;border-radius:8px;background:var(--accent);color:#fff;font-weight:600;display:flex;align-items:center;justify-content:center} h2{font-size:19px;margin:0}
.gtag{font-size:11px;padding:3px 8px;border-radius:999px;font-weight:600}.gtag-user{background:#EEEDFE;color:#4A3FB0}.gtag-admin{background:#F1EFE8;color:#5f5e5a}.gtag-state{background:#FAECE7;color:#993C1D}
.route{font-family:ui-monospace,Consolas,monospace;font-size:12px;color:var(--muted);background:var(--bg);padding:2px 7px;border-radius:6px;border:1px solid var(--line)}
.feats{display:flex;gap:6px;flex-wrap:wrap}.pill{font-size:11px;color:#4A3FB0;background:#EEEDFE;border-radius:999px;padding:3px 9px}
.summary{margin:2px 0 14px;color:var(--muted);font-size:14px}
.shots{display:flex;gap:18px;align-items:flex-start;flex-wrap:wrap}
.col{flex:0 0 auto}.lab{font-size:11px;color:var(--hint);font-family:ui-monospace,monospace;margin-bottom:6px}
.scr{position:relative;border:1px solid var(--line);border-radius:10px;overflow:hidden;background:#fff}
.scr iframe{border:0;transform-origin:top left;background:#fff}
.co{position:absolute;border:2px dashed;border-radius:7px;pointer-events:none}
.cb{position:absolute;top:-11px;left:-11px;width:21px;height:21px;border-radius:50%;color:#fff;font-size:12px;font-weight:600;display:flex;align-items:center;justify-content:center;box-shadow:0 1px 3px #0003}
.capcol{flex:1;min-width:240px;padding-top:18px}
.captitle{font-size:15px;font-weight:600;margin-bottom:10px}
.caps{list-style:none;margin:0;padding:0;display:flex;flex-direction:column;gap:10px}
.caps li{display:flex;gap:10px;align-items:flex-start}
.nb{flex:0 0 21px;width:21px;height:21px;border-radius:50%;color:#fff;font-size:12px;font-weight:600;display:flex;align-items:center;justify-content:center;margin-top:1px}
.nt{font-weight:600;font-size:13.5px}.ft{font-size:11px;color:var(--accent);background:#EEEDFE;border-radius:5px;padding:1px 6px}.nd{font-size:12.5px;color:var(--muted)}.go{color:var(--accent);font-weight:500}
.brs{margin-top:12px;display:flex;gap:7px;flex-wrap:wrap;align-items:center}.brl{font-size:11px;color:var(--hint)}.br{font-size:11px;background:#FAECE7;color:#993C1D;border-radius:6px;padding:3px 8px}
@media(max-width:900px){.shots{flex-direction:column}}
</style></head><body><div class="wrap">
<div class="cover"><span class="kic">STORYBOARD · C 영역 (정적 HTML)</span><h1>${esc(m.title||"CareerTuner — C 영역 UI/UX 스토리보드")}</h1><p class="sub">${esc(m.subtitle||"")}</p>
<div class="meta"><b>영역</b> C · <b>데모</b> ${esc(m.user||"김데모")} · <b>출처</b> ${esc(m.source||"")} · <b>생성</b> ${esc(m.date||"")}</div>
<div class="note">📄 이 파일은 <b>래스터 이미지·React·서버 없이</b> 동작합니다 — 각 화면은 실제 렌더된 DOM 스냅샷(<code>srcdoc</code> iframe)이고, 앱 CSS 1벌을 열릴 때 주입합니다. 점선 박스 = 설명 지점(번호 ↔ 캡션).</div></div>
<div class="flowwrap"><h3>${esc(spec.journey?.title||"C 가치 여정")}</h3><pre class="mermaid">${esc(mermaid(spec.journey))}</pre></div>
${frames.join("\n")}
</div>
<script>
(function(){
  var css=document.getElementById('appcss').textContent;
  function inject(f){try{var d=f.contentDocument;if(d&&d.head&&!d.getElementById('__appcss__')){var s=d.createElement('style');s.id='__appcss__';s.textContent=css;d.head.appendChild(s);}}catch(e){}}
  document.querySelectorAll('iframe.screen').forEach(function(f){f.addEventListener('load',function(){inject(f);});inject(f);});
})();
</script>
</body></html>`;
await writeFile(path.join(OUT, "storyboard-static.html"), html, "utf8");
console.log(`rendered -> output/storyboard-static.html (${(html.length/1024/1024).toFixed(2)}MB, frames ${spec.frames?.length||0})`);
