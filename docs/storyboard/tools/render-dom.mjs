// 자체완결 정적 스토리보드(이미지·React·서버 없음) + DOM 실시간 측정 오버레이.
// 레이아웃: [웹=전체 높이(잘림/스크롤 없음)] | [오른쪽 열: 앱=폰 1화면 + 흐름 설명].
// 박스는 좌표가 아니라 텍스트 앵커로 iframe 안 실제 요소를 런타임 측정해 얹는다(폰트 밀려도 정확).
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
const attr = (s="")=> String(s).replace(/&/g,"&amp;").replace(/"/g,"&quot;");
const sanId = (s)=> "n_"+String(s).replace(/[^a-zA-Z0-9]/g,"_");
// web: callout 이 있는 영역까지만 크롭(실제 브라우저 첫 화면처럼, 설명 없는 하단은 자름).
// app: 폰 1화면(고정 높이).
const VP = { web: { w:1440, h:1024, dw:720, mode:"crop" }, app: { w:390, h:844, dw:244, mode:"phone" } };

function mermaid(j){
  const L=["flowchart LR"];
  for (const n of j.nodes){ const label=(n.label||n.id)+(n.sub?`<br/>${n.sub}`:""); L.push(`  ${sanId(n.id)}["${label}"]:::${n.current?"cur":(n.group||"user")}`); }
  for (const e of j.edges||[]){ const a=e.branch?"-.->":"-->"; L.push(e.label?`  ${sanId(e.from)} ${a}|${e.label}| ${sanId(e.to)}`:`  ${sanId(e.from)} ${a} ${sanId(e.to)}`); }
  L.push("classDef cur fill:#EEEDFE,stroke:#6C5CE0,color:#26215C,stroke-width:2px;","classDef user fill:#fff,stroke:#B4B2A9,color:#2C2C2A;","classDef admin fill:#F1EFE8,stroke:#888780,color:#2C2C2A;","classDef entry fill:#E1F5EE,stroke:#0F8E6E,color:#04342C;","classDef state fill:#FAECE7,stroke:#C8501F,color:#4A1B0C;");
  return L.join("\n");
}
const GTAG = { user:"사용자", admin:"관리자", state:"상태" };

function screen(srcdocHtml, callouts, target, frameId) {
  const vp = VP[target];
  const scale = vp.dw / vp.w;
  const dh = Math.round(vp.h * scale); // 초기 높이(web 은 JS 가 전체로 확장)
  const cs = (callouts||[]).filter((c)=>(c.target||"web")===target).map((c,i)=>{
    const color = col(c.color, (c.n??i+1)-1);
    const anchors = attr(JSON.stringify(c.anchors || []));
    const fb = `left:${(c.nx*100).toFixed(2)}%;top:${(c.ny*100).toFixed(2)}%;width:${(c.nw*100).toFixed(2)}%;height:${(c.nh*100).toFixed(2)}%`;
    return `<div class="co" data-anchors="${anchors}" style="${fb};border-color:${color};box-shadow:0 0 0 2px ${color}22"><span class="cb" style="background:${color}">${c.n??i+1}</span></div>`;
  }).join("");
  return `<div class="scr ${target}" data-mode="${vp.mode}" data-scale="${scale.toFixed(5)}" data-nw="${vp.w}" data-nh="${vp.h}" style="width:${vp.dw}px;height:${dh}px">`
    + `<iframe class="screen" scrolling="no" title="${frameId}-${target}" style="width:${vp.w}px;height:${vp.h}px;zoom:${scale.toFixed(5)}" srcdoc="${attr(srcdocHtml)}"></iframe>`
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
  <div class="shots">
    <div class="col"><div class="lab">◻ 웹 · 1440 (callout 영역)</div>${screen(webDom, f.callouts, "web", f.id)}</div>
    <div class="col"><div class="lab">▢ 앱 · 390 (폰 화면)</div>${screen(appDom, f.callouts, "app", f.id)}</div>
  </div>
  <div class="capcol"><div class="captitle">흐름 설명</div><ol class="caps">${narration}</ol>${branches}</div>
</section>`;
}

const spec = JSON.parse(await readFile(SPEC, "utf8"));
const appCss = await readFile(path.join(SNAP, "_app.css"), "utf8");
const m = spec.meta || {};
const frames = [];
for (const f of spec.frames || []) frames.push(await frameSection(f));

const INJECT = `
(function(){
  var css=document.getElementById('appcss').textContent;
  function norm(s){return (s||'').replace(/\\s+/g,' ').trim();}
  function findUnion(doc, anchors){
    var els=doc.body.querySelectorAll('*');
    // 텍스트를 포함하는 후보 요소들(헤더 영역 제외). len=구체성 판단용.
    function cands(txt){ var out=[]; for(var i=0;i<els.length;i++){ var t=norm(els[i].textContent);
      if(t && t.indexOf(txt)!==-1){ var r=els[i].getBoundingClientRect(); if(r.width>1&&r.height>1&&r.bottom>92) out.push({r:r,len:t.length}); } } return out; }
    // base = 첫 앵커의 가장 구체적(작은) 요소
    var base=null;
    for(var a=0;a<anchors.length && !base;a++){ var c=cands(anchors[a]); if(c.length){ c.sort(function(x,y){return x.len-y.len;}); base=c[0].r; } }
    if(!base) return null;
    var bc=(base.top+base.bottom)/2, u={l:base.left,t:base.top,r:base.right,b:base.bottom};
    // 이후 앵커는 base 중심에 "가장 가까운" 후보만, 그것도 150px 이내일 때만 union(대각선/원거리 오매칭 배제)
    for(var a2=0;a2<anchors.length;a2++){ var c2=cands(anchors[a2]); if(!c2.length) continue;
      c2.sort(function(x,y){return Math.abs((x.r.top+x.r.bottom)/2-bc)-Math.abs((y.r.top+y.r.bottom)/2-bc);});
      var p=c2[0].r; if(Math.abs((p.top+p.bottom)/2-bc)<=150){ u.l=Math.min(u.l,p.left);u.t=Math.min(u.t,p.top);u.r=Math.max(u.r,p.right);u.b=Math.max(u.b,p.bottom); }
    }
    return u;
  }
  // 번호 배지 충돌 방지: 박스 좌상단(-11,-11)에 고정된 배지들이 겹치면(중첩/인접 callout)
  // 번호가 가려진다. 배치 후 한 화면 안에서 겹치는 배지를 박스 테두리를 따라 밀어 항상 보이게 한다.
  function dedupeBadges(scr){
    var cos=scr.querySelectorAll('.co[data-anchored="1"]'), arr=[];
    for(var i=0;i<cos.length;i++){ var co=cos[i], cb=co.querySelector('.cb'); if(!cb) continue;
      arr.push({cb:cb, ox:parseFloat(co.style.left), oy:parseFloat(co.style.top), w:parseFloat(co.style.width)}); }
    var R=23, placed=[]; // 배지 지름 21 + 여유
    for(var j=0;j<arr.length;j++){ var it=arr[j], l=it.ox-11, t=it.oy-11, base=l, tr=0;
      while(tr<60){ var clash=false; for(var k=0;k<placed.length;k++){ var p=placed[k];
          if(Math.abs(l-p.l)<R && Math.abs(t-p.t)<R){ clash=true; break; } }
        if(!clash) break;
        l+=R; if(l>base+Math.max(0,it.w-R)){ l=base; t+=R; } tr++; } // 위 테두리 오른쪽으로, 폭 넘으면 왼쪽 테두리 아래로
      placed.push({l:l,t:t});
      it.cb.style.left=Math.round(l-it.ox)+'px'; it.cb.style.top=Math.round(t-it.oy)+'px';
    }
  }
  var ifr=document.querySelectorAll('iframe.screen'), total=ifr.length, done=0, items=[];
  // 모든 프레임 측정 끝나면 화면 높이를 "전역 최대"로 통일(프레임 간 여백/크기 차이 제거)
  function finalize(){
    var H=0; for(var i=0;i<items.length;i++) if(items[i].need>H) H=items[i].need;
    for(var j=0;j<items.length;j++){ var it=items[j]; it.scr.style.height=Math.round(H)+'px'; it.f.style.height=Math.round(H/it.scale)+'px'; }
  }
  function place(f){
    if(f.__done) return;
    var doc; try{doc=f.contentDocument;}catch(e){return;} if(!doc||!doc.body) return;
    f.__done=true;
    if(doc.head && !doc.getElementById('__appcss__')){var s=doc.createElement('style');s.id='__appcss__';s.textContent=css;doc.head.appendChild(s);}
    var scr=f.parentElement, mode=scr.getAttribute('data-mode'), scale=parseFloat(scr.getAttribute('data-scale')), NW=parseFloat(scr.getAttribute('data-nw')), nh=parseFloat(scr.getAttribute('data-nh'));
    // CSS 적용(flex/grid 등장) 전 측정하면 박스가 미적용 레이아웃에 얹혀 어긋난다 → 스타일될 때까지 폴링 후 측정.
    function styledNow(){ var all=doc.body.querySelectorAll('*'),fx=0; for(var i=0;i<all.length;i++){ var dsp=getComputedStyle(all[i]).display; if(dsp==='flex'||dsp==='grid'){ if(++fx>=3) return true; } } return false; }
    var poll=0;
    (function waitStyled(){
      if(!styledNow() && poll++<40){ setTimeout(waitStyled,150); return; }
      if(mode==='crop'){ var fullH=Math.max(doc.documentElement.scrollHeight, doc.body.scrollHeight, nh); f.style.height=fullH+'px'; }
      setTimeout(function(){
        var NH = mode==='crop' ? Math.max(doc.documentElement.scrollHeight, doc.body.scrollHeight, nh) : nh;
        var cos=scr.querySelectorAll('.co'), pad=8, maxB=0;
        for(var i=0;i<cos.length;i++){ var co=cos[i], anchors=[]; try{anchors=JSON.parse(co.getAttribute('data-anchors')||'[]');}catch(e){}
          if(!anchors.length) continue; var u=findUnion(doc,anchors); if(!u) continue;
          var L=Math.max(0,u.l-pad),T=Math.max(0,u.t-pad),R=Math.min(NW,u.r+pad),B=Math.min(NH,u.b+pad);
          if(B<=2||R<=2||T>=NH-2) continue;
          co.style.left=(L*scale).toFixed(1)+'px';co.style.top=(T*scale).toFixed(1)+'px';co.style.width=((R-L)*scale).toFixed(1)+'px';co.style.height=((B-T)*scale).toFixed(1)+'px';co.setAttribute('data-anchored','1');
          if(B>maxB)maxB=B;
        }
        dedupeBadges(scr); // 번호 배지 겹침 제거(번호 항상 보이게)
        var cropH = mode==='crop' ? (maxB>0?Math.min(maxB+50,NH):nh) : nh;
        items.push({scr:scr,f:f,scale:scale,need:cropH*scale});
        done++; if(done>=total) finalize();
      },120);
    })();
  }
  // 무거운 프레임은 iframe load 가 늦어 CSS 주입/측정이 캡처보다 뒤처질 수 있다.
  // load 이벤트 + 폴링으로 모든 iframe 의 doc.body 가 준비되는 즉시 place() 를 보장한다.
  for(var k=0;k<ifr.length;k++){ (function(f){ f.addEventListener('load',function(){place(f);}); })(ifr[k]); }
  function pump(){ var pending=0; for(var k=0;k<ifr.length;k++){ if(!ifr[k].__done){ pending++; place(ifr[k]); } } return pending; }
  pump();
  var tries=0, iv=setInterval(function(){ tries++; var p=pump();
    if(p===0||tries>50){ clearInterval(iv); setTimeout(function(){ if(done<total) finalize(); }, 800); } }, 200);
})();`;

const html = `<!doctype html><html lang="ko"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>CareerTuner · C 영역 UI/UX 스토리보드 (정적)</title>
<script type="text/css" id="appcss">${appCss.replace(/<\/script>/gi, "<\\/script>")}</script>
<script type="module">import mermaid from "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs";mermaid.initialize({startOnLoad:true,theme:"neutral",flowchart:{htmlLabels:true,useMaxWidth:true}});</script>
<style>
:root{--ink:#1a1a1c;--muted:#5f5e5a;--hint:#8a8980;--line:#e6e4dd;--bg:#faf9f6;--card:#fff;--accent:#6C5CE0}
*{box-sizing:border-box} body{font-family:'Pretendard',system-ui,'Segoe UI','Malgun Gothic',sans-serif;color:var(--ink);background:var(--bg);margin:0;line-height:1.6}
.wrap{max-width:1240px;margin:0 auto;padding:36px 24px 80px}
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
.shots{display:flex;gap:20px;align-items:flex-start;flex-wrap:wrap}
.col{flex:0 0 auto}
.capcol{margin-top:18px;padding-top:16px;border-top:1px solid var(--line)}
.lab{font-size:11px;color:var(--hint);font-family:ui-monospace,monospace;margin-bottom:6px}
.scr{position:relative;border:1px solid var(--line);border-radius:10px;overflow:hidden;background:#fff}
.scr iframe{border:0;background:#fff;display:block}
.co{position:absolute;border:2px dashed;border-radius:7px;pointer-events:none}
.cb{position:absolute;top:-11px;left:-11px;width:21px;height:21px;border-radius:50%;color:#fff;font-size:12px;font-weight:600;display:flex;align-items:center;justify-content:center;box-shadow:0 1px 3px #0003}
.captitle{font-size:15px;font-weight:600;margin-bottom:10px}
.caps{list-style:none;margin:0;padding:0;display:grid;grid-template-columns:1fr 1fr;gap:10px 28px}
.caps li{display:flex;gap:10px;align-items:flex-start}
.nb{flex:0 0 21px;width:21px;height:21px;border-radius:50%;color:#fff;font-size:12px;font-weight:600;display:flex;align-items:center;justify-content:center;margin-top:1px}
.nt{font-weight:600;font-size:13.5px}.ft{font-size:11px;color:var(--accent);background:#EEEDFE;border-radius:5px;padding:1px 6px}.nd{font-size:12.5px;color:var(--muted)}.go{color:var(--accent);font-weight:500}
.brs{margin-top:12px;display:flex;gap:7px;flex-wrap:wrap;align-items:center}.brl{font-size:11px;color:var(--hint)}.br{font-size:11px;background:#FAECE7;color:#993C1D;border-radius:6px;padding:3px 8px}
@media(max-width:980px){.caps{grid-template-columns:1fr}}
</style></head><body><div class="wrap">
<div class="cover"><span class="kic">STORYBOARD · C 영역 (정적 HTML)</span><h1>${esc(m.title||"CareerTuner — C 영역 UI/UX 스토리보드")}</h1><p class="sub">${esc(m.subtitle||"")}</p>
<div class="meta"><b>영역</b> C · <b>데모</b> ${esc(m.user||"김데모")} · <b>출처</b> ${esc(m.source||"")} · <b>생성</b> ${esc(m.date||"")}</div>
<div class="note">📄 <b>래스터 이미지·React·서버 없이</b> 동작 — 각 화면은 실제 렌더된 DOM 스냅샷(<code>srcdoc</code> iframe), CSS 1벌을 열릴 때 주입. 웹은 전체 높이, 앱은 폰 1화면. 점선 박스는 <b>요소 위치를 실시간 측정</b>해 얹습니다(번호 ↔ 캡션).</div></div>
<div class="flowwrap"><h3>${esc(spec.journey?.title||"C 가치 여정")}</h3><pre class="mermaid">${esc(mermaid(spec.journey))}</pre></div>
${frames.join("\n")}
</div>
<script>${INJECT}</script>
</body></html>`;
await writeFile(path.join(OUT, "storyboard-static.html"), html, "utf8");
console.log(`rendered -> output/storyboard-static.html (${(html.length/1024/1024).toFixed(2)}MB, frames ${spec.frames?.length||0})`);
