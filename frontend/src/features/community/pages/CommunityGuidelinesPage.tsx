import { useState, useEffect } from "react";
import {
  ArrowLeft, Send, MessageCircle, RotateCcw, Check, X,
  UserX, Flame, FileX2, MegaphoneOff, ShieldAlert,
  ChevronRight, Mail, MessagesSquare,
} from "lucide-react";
import {
  getPublishedGuideline,
  type GuidelineData, type GuidelineRule, type GuidelineParams,
} from "../api/guidelineApi";
import "../styles/community-guidelines.css";

const RULE_ICONS = [UserX, Flame, FileX2, MegaphoneOff, ShieldAlert];
const SANCTION_LABELS = ["삭제 + 단계 제재", "즉시 영구 제한 가능", "삭제만 (제재 없음)"];

/* ── 정적 fallback (API 호출 전/실패 시 사용) ── */
const FALLBACK_OKS = [
  { text: "회사·전형에 대한 부정적 평가", ex: '"면접 일정 안내가 늦고 과제 분량이 과했어요"' },
  { text: "면접 질문·과정 복기", ex: "기출 질문, 전형 단계, 분위기, 체감 난이도 공유" },
  { text: "연봉·처우 등 민감한 주제의 토론", ex: "경험에 기반한 수치와 조건 이야기" },
  { text: "날 선 의견과 반박", ex: "상대의 글을 비판하는 건 괜찮아요. 사람이 아니라 내용을 향한다면" },
];
const FALLBACK_NOS = [
  { text: "특정인을 알아볼 수 있게 쓰는 것", ex: '"○○팀 김 부장" — 실명이 아니어도 부서+직급 조합으로 특정되면 안 돼요' },
  { text: "인신공격·혐오 표현", ex: "의견 비판이 아닌 사람을 향한 모욕, 출신·성별 등에 대한 비하" },
  { text: "지어낸 후기", ex: "경험하지 않은 전형을 사실처럼 쓰는 것" },
  { text: "회사의 미공개 기밀", ex: "출시 전 제품 정보, 내부 문서 유출" },
];
const FALLBACK_RULES: GuidelineRule[] = [
  { t: "개인 특정·신상 노출", s: 0, b: "실명, 연락처, 또는 부서·직급·시기 조합으로 누구인지 알 수 있는 서술. 익명 커뮤니티에서 가장 큰 피해를 만드는 행위라 가장 엄격하게 봅니다." },
  { t: "인신공격·혐오 표현", s: 0, b: "특정 이용자나 집단을 향한 모욕·위협, 출신·성별·연령 등에 대한 비하. 거친 말투나 욕설 섞인 푸념 자체는 제재 대상이 아니에요 — 사람을 겨냥할 때만 적용됩니다." },
  { t: "허위 사실·조작된 후기", s: 0, b: "경험하지 않은 전형의 후기, 의도적인 평판 조작. 단순히 기억이 달라서 생긴 오류는 수정 요청으로 처리하고 제재하지 않습니다." },
  { t: "광고·스팸·도배", s: 0, b: "영리 목적의 홍보, 동일 내용 반복 게시, 외부 유도 링크. 스터디 모집이나 무료 자료 공유는 광고로 보지 않아요." },
  { t: "불법 정보·기밀 유출", s: 1, b: "법령 위반 콘텐츠, 기업의 미공개 기밀·내부 문서 유출. 피해가 크고 회복이 어려운 영역이라 단계 없이 즉시 영구 제한될 수 있습니다." },
];
const DEFAULT_PARAMS: GuidelineParams = { blind: 3, sla: 24, expire: 90, s1: 7, s2: 30, appeal: 30 };

interface Props {
  onBack: () => void;
}

export function CommunityGuidelinesPage({ onBack }: Props) {
  const [data, setData] = useState<GuidelineData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getPublishedGuideline()
      .then(setData)
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  const lede = data?.lede || "CareerTuner 커뮤니티는 면접·취업 경험을 솔직하게 나누는 곳입니다. 솔직함이 핵심 가치이기 때문에, 저희는 글을 미리 검열하지 않습니다. 대신 다른 사람에게 실제 피해를 주는 행동만 좁고 명확하게 금지하고, 위반에는 단계적으로 대응합니다.";
  const rules: GuidelineRule[] = data?.rulesJson ? JSON.parse(data.rulesJson) : FALLBACK_RULES;
  const params: GuidelineParams = data?.paramsJson ? JSON.parse(data.paramsJson) : DEFAULT_PARAMS;
  const version = data?.versionLabel || "v1.0";
  const publishedDate = data?.publishedAt
    ? new Date(data.publishedAt).toLocaleDateString("ko-KR", { year: "numeric", month: "numeric", day: "numeric" }).replace(/\./g, ". ").trim()
    : "2026. 6. 12";

  // oks/nos: API 응답이 있으면 사용, 없으면 fallback
  const oksRaw: string[] = data?.oksJson ? JSON.parse(data.oksJson) : [];
  const nosRaw: string[] = data?.nosJson ? JSON.parse(data.nosJson) : [];
  const oks = oksRaw.length > 0 ? oksRaw.map((t) => ({ text: t, ex: "" })) : FALLBACK_OKS;
  const nos = nosRaw.length > 0 ? nosRaw.map((t) => ({ text: t, ex: "" })) : FALLBACK_NOS;

  if (loading) return <div className="gl-loading">가이드라인을 불러오는 중...</div>;

  return (
    <div className="gl-wrap">
      <button className="gl-back" onClick={onBack}><ArrowLeft />커뮤니티로 돌아가기</button>

      <header>
        <div className="gl-eyebrow">커뮤니티</div>
        <h1 className="gl-title">커뮤니티 가이드라인</h1>
        <p className="gl-lede" dangerouslySetInnerHTML={{
          __html: lede.replace(/\*\*(.*?)\*\*/g, "<b>$1</b>")
        }} />
        <div className="gl-daterow">
          <span>시행일 {publishedDate}</span>
          <span>버전 {version}</span>
        </div>
      </header>

      {/* 운영 원칙 */}
      <section className="gl-principles" aria-label="운영 원칙">
        <div className="gl-pr">
          <span className="gl-pr__ic"><Send /></span>
          <div className="gl-pr__t">선 게시, 후 검토</div>
          <div className="gl-pr__s">작성한 글은 즉시 게시돼요. 사전 심사는 없으며, 신고가 접수된 글만 운영팀이 검토합니다.</div>
        </div>
        <div className="gl-pr">
          <span className="gl-pr__ic"><MessageCircle /></span>
          <div className="gl-pr__t">표현은 넓게, 금지는 좁게</div>
          <div className="gl-pr__s">회사·면접에 대한 부정적 평가도 경험에 기반했다면 괜찮아요. 금지 항목은 아래 {rules.length}가지뿐입니다.</div>
        </div>
        <div className="gl-pr">
          <span className="gl-pr__ic"><RotateCcw /></span>
          <div className="gl-pr__t">실수는 복원으로</div>
          <div className="gl-pr__s">신고로 가려진 글도 검토 후 문제가 없으면 그대로 복원돼요. 제재 기록은 {params.expire}일 뒤 소멸합니다.</div>
        </div>
      </section>

      {/* 01 — 괜찮아요 / 안 돼요 */}
      <section aria-label="허용과 금지 예시">
        <div className="gl-h2"><span className="num">01</span><h2>이런 글, 써도 괜찮아요</h2></div>
        <p className="gl-p">헷갈리기 쉬운 경계를 예시로 정리했어요. 기준은 하나입니다 — <b>경험과 의견은 자유, 특정인을 겨냥한 공격과 신상 노출은 금지.</b></p>
        <div className="gl-okno">
          <div className="gl-col gl-col--ok">
            <div className="gl-col__h"><Check />괜찮아요</div>
            <ul>
              {oks.map((item, i) => (
                <li key={i}><b>{item.text}</b>{item.ex && <span className="ex">{item.ex}</span>}</li>
              ))}
            </ul>
          </div>
          <div className="gl-col gl-col--no">
            <div className="gl-col__h"><X />안 돼요</div>
            <ul>
              {nos.map((item, i) => (
                <li key={i}><b>{item.text}</b>{item.ex && <span className="ex">{item.ex}</span>}</li>
              ))}
            </ul>
          </div>
        </div>
      </section>

      {/* 02 — 금지하는 5가지 */}
      <section aria-label="금지 사항">
        <div className="gl-h2"><span className="num">02</span><h2>금지하는 {rules.length}가지</h2><span className="s">이 목록이 전부예요</span></div>
        <div className="gl-rules">
          {rules.map((rule, i) => {
            const Icon = RULE_ICONS[i] || ShieldAlert;
            return (
              <div className="gl-rule" key={i}>
                <span className="gl-rule__ic"><Icon /></span>
                <div>
                  <div className="gl-rule__t">
                    {rule.t}
                    <span className={`lv gl-lv ${rule.s === 1 ? "gl-lv--ban" : "gl-lv--del"}`}>
                      {SANCTION_LABELS[rule.s]}
                    </span>
                  </div>
                  <div className="gl-rule__s" dangerouslySetInnerHTML={{
                    __html: rule.b.replace(/\*\*(.*?)\*\*/g, "<b>$1</b>")
                  }} />
                </div>
              </div>
            );
          })}
        </div>
      </section>

      {/* 03 — 신고 처리 흐름 */}
      <section aria-label="신고 처리 절차">
        <div className="gl-h2"><span className="num">03</span><h2>신고는 이렇게 처리돼요</h2></div>
        <p className="gl-p">운영팀이 모든 글을 들여다보지 않아요. <b>신고가 들어온 글만</b> 봅니다. 신고가 일정 수 모이면 검토 전까지 잠시 가려질 수 있지만, 문제가 없으면 그대로 돌아옵니다.</p>
        <div className="gl-flow">
          <div className="gl-step">
            <div className="gl-step__k">STEP 1</div>
            <div className="gl-step__t">신고 접수</div>
            <div className="gl-step__s">글·댓글의 신고 버튼으로 사유를 선택해요. 신고자는 익명이 보장돼요.</div>
          </div>
          <span className="gl-flow__arr"><ChevronRight /></span>
          <div className="gl-step">
            <div className="gl-step__k">STEP 2</div>
            <div className="gl-step__t">임시 블라인드</div>
            <div className="gl-step__s">신고 {params.blind}건 누적 시 검토 전까지 일시적으로 가려져요. 삭제가 아니에요.</div>
          </div>
          <span className="gl-flow__arr"><ChevronRight /></span>
          <div className="gl-step">
            <div className="gl-step__k">STEP 3</div>
            <div className="gl-step__t">운영팀 검토</div>
            <div className="gl-step__s">영업일 기준 {params.sla}시간 안에 가이드라인 위반 여부만 판단해요.</div>
          </div>
          <span className="gl-flow__arr"><ChevronRight /></span>
          <div className="gl-step">
            <div className="gl-step__k">STEP 4</div>
            <div className="gl-step__t">복원 또는 조치</div>
            <div className="gl-step__s">문제없으면 즉시 복원. 위반이면 삭제하고 사유를 작성자에게 알려요.</div>
          </div>
        </div>
        <div className="gl-note"><b>허위 신고도 위반이에요.</b> 마음에 들지 않는 글을 지우려는 반복적인 허위 신고는 신고자 쪽에 단계 제재가 적용됩니다.</div>
      </section>

      {/* 04 — 제재 단계 */}
      <section aria-label="제재 단계">
        <div className="gl-h2"><span className="num">04</span><h2>제재는 단계적으로</h2><span className="s">기록은 {params.expire}일 후 소멸</span></div>
        <p className="gl-p">한 번의 실수로 계정이 사라지지 않아요. 위반이 확인되면 아래 단계를 차례로 거치고, <b>{params.expire}일 동안 추가 위반이 없으면 이전 기록은 소멸</b>합니다.</p>
        <div className="gl-tiers">
          <div className="gl-tier">
            <span className="gl-tier__l"><span className="gl-st gl-st--off">주의</span></span>
            <span className="gl-tier__d">첫 위반 시 해당 글만 삭제하고 어떤 항목을 위반했는지 안내해요. 활동 제한은 없어요.</span>
            <span className="gl-tier__r">활동 제한 없음</span>
          </div>
          <div className="gl-tier gl-tier--warn">
            <span className="gl-tier__l"><span className="gl-st gl-st--warn">1차 경고</span></span>
            <span className="gl-tier__d">주의 이후 {params.expire}일 안에 같은 항목을 다시 위반한 경우예요.</span>
            <span className="gl-tier__r">글쓰기 {params.s1}일 정지</span>
          </div>
          <div className="gl-tier gl-tier--warn">
            <span className="gl-tier__l"><span className="gl-st gl-st--warn">2차 경고</span></span>
            <span className="gl-tier__d">1차 경고 기간 소멸 전 재위반. 댓글을 포함한 모든 작성이 제한돼요.</span>
            <span className="gl-tier__r">글쓰기 {params.s2}일 정지</span>
          </div>
          <div className="gl-tier gl-tier--bad">
            <span className="gl-tier__l"><span className="gl-st gl-st--bad">영구 제한</span></span>
            <span className="gl-tier__d">2차 경고 이후의 재위반, 또는 불법 정보·기밀 유출 등 중대한 위반.</span>
            <span className="gl-tier__r">계정 이용 제한</span>
          </div>
        </div>
      </section>

      {/* 05 — 이의신청 */}
      <section aria-label="이의신청">
        <div className="gl-h2"><span className="num">05</span><h2>판단이 잘못됐다고 생각한다면</h2></div>
        <p className="gl-p">
          조치 안내를 받은 날로부터 <b>{params.appeal}일 안에 이의신청</b>할 수 있어요. 처음 검토한 사람이 아닌 다른 운영자가
          다시 살펴보고, 잘못된 조치였다면 글과 제재 기록을 모두 되돌립니다. 이의신청은 고객센터 1:1 문의에서 접수돼요.
        </p>
      </section>

      {/* 푸터 */}
      <footer className="gl-foot">
        <p>이 가이드라인은 커뮤니티의 의견을 반영해 다듬어져요. 개정 시 시행 7일 전에 공지사항으로 안내합니다.</p>
        <div className="right">
          <button className="gl-btn" onClick={() => window.location.href = "/support/contact"}>
            <Mail />문의하기
          </button>
          <button className="gl-btn gl-btn--ink" onClick={onBack}>
            <MessagesSquare />커뮤니티 가기
          </button>
        </div>
      </footer>
    </div>
  );
}
