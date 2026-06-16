import { useState } from 'react';
import { Bell, Search, ChevronLeft, ChevronRight, Download } from 'lucide-react';
import AdminShell from '../../../components/AdminShell';
import './admin-notifications.css';

interface NotifRow {
  id: number;
  user: { name: string; email: string };
  type: string;
  title: string;
  message: string;
  isRead: boolean;
  readAt: string | null;
  sent: string;
  broadcast?: boolean;
}

const CATS: Record<string, { label: string; dot: string }> = {
  ai:         { label: 'AI 분석',  dot: '#2563eb' },
  interview:  { label: '면접',     dot: '#9333ea' },
  correction: { label: '첨삭',     dot: '#0d9488' },
  community:  { label: '커뮤니티', dot: '#4f46e5' },
  billing:    { label: '결제',     dot: '#16a34a' },
  notice:     { label: '공지',     dot: '#ea580c' },
  admin:      { label: '관리자',   dot: '#dc2626' },
};

const TYPE_CAT: Record<string, string> = {
  PROFILE_ANALYZED: 'ai', JOB_ANALYSIS_COMPLETE: 'ai', COMPANY_ANALYSIS_COMPLETE: 'ai',
  FIT_ANALYSIS_COMPLETE: 'ai', CAREER_TREND_COMPLETE: 'ai',
  QUESTIONS_GENERATED: 'interview', INTERVIEW_REPORT_READY: 'interview',
  CORRECTION_COMPLETE: 'correction',
  COMMENT: 'community', COMMENT_REPLY: 'community', LIKE: 'community', POST_SUMMARY_READY: 'community',
  CREDIT_LOW: 'billing',
  NOTICE: 'notice', TICKET_ANSWERED: 'notice',
  NEW_REPORT: 'admin', NEW_TICKET: 'admin', NEW_USER: 'admin', TICKET_DRAFT_READY: 'admin',
};

const MOCK_ROWS: NotifRow[] = [
  { id: 9821, user: { name: '김지훈', email: 'redacted-ada316a1bce9af50@example.com' }, type: 'JOB_ANALYSIS_COMPLETE', title: '직무 분석이 완료됐어요', message: '백엔드 개발자 직무 기준 분석 리포트가 준비되었습니다.', isRead: false, readAt: null, sent: '방금' },
  { id: 9820, user: { name: '이서연', email: 'redacted-9448f346ed488617@example.com' }, type: 'COMMENT', title: '박서준님이 댓글을 남겼어요', message: '네이버 백엔드 면접 후기 글에 새 댓글이 달렸습니다.', isRead: false, readAt: null, sent: '2분 전' },
  { id: 9819, user: { name: '김운영', email: 'redacted-93bd88cd34b3998c@example.com' }, type: 'NEW_REPORT', title: '새 신고가 접수됐어요', message: '광고성 게시글 신고 1건이 검토 대기 중입니다.', isRead: true, readAt: '5분 전', sent: '8분 전' },
  { id: 9818, user: { name: '박민준', email: 'redacted-7d135436bb37b109@example.com' }, type: 'CREDIT_LOW', title: '무료 분석 1회 남았어요', message: '이번 달 무료 이력서 분석이 곧 소진됩니다.', isRead: false, readAt: null, sent: '14분 전' },
  { id: 9817, user: { name: '최유나', email: 'redacted-a7e7648f08934115@example.com' }, type: 'INTERVIEW_REPORT_READY', title: '모의면접 리포트가 생성됐어요', message: '6월 10일 진행한 모의면접의 답변 분석 리포트를 확인해보세요.', isRead: true, readAt: '20분 전', sent: '32분 전' },
  { id: 9816, user: { name: '정도현', email: 'redacted-57450f1deff232ab@example.com' }, type: 'TICKET_ANSWERED', title: '1:1 문의에 답변이 등록됐어요', message: '환불 관련 문의에 운영팀이 답변을 남겼습니다.', isRead: true, readAt: '41분 전', sent: '1시간 전' },
  { id: 9815, user: { name: '한소희', email: 'redacted-491d439a09dd8afc@example.com' }, type: 'LIKE', title: '이지은님이 회원님 글을 좋아해요', message: '이력서 첨삭 꿀팁 공유 글에 좋아요를 눌렀습니다.', isRead: false, readAt: null, sent: '1시간 전' },
  { id: 9814, user: { name: '오재원', email: 'redacted-bf8665f7e50eff06@example.com' }, type: 'CORRECTION_COMPLETE', title: '자소서 첨삭이 완료됐어요', message: '지원동기 문항에서 보완하면 좋을 제안 3건을 찾았어요.', isRead: true, readAt: '2시간 전', sent: '3시간 전' },
  { id: 9813, user: { name: '윤채영', email: 'redacted-126f98987658625f@example.com' }, type: 'QUESTIONS_GENERATED', title: '예상 질문이 준비됐어요', message: '지원 직무 기준 예상 면접 질문 20개가 생성되었습니다.', isRead: false, readAt: null, sent: '4시간 전' },
  { id: 9811, user: { name: '전체 회원', email: 'broadcast · 1,248명' }, type: 'NOTICE', title: '[점검] 서비스 정기 점검 안내', message: '6월 12일(목) 02:00~04:00 서비스 점검이 예정되어 있습니다.', isRead: false, readAt: null, sent: '어제', broadcast: true },
];

const RATES = [
  { cat: 'ai', sent: 412, rate: 74 },
  { cat: 'community', sent: 351, rate: 59 },
  { cat: 'notice', sent: 220, rate: 41, low: true },
  { cat: 'interview', sent: 188, rate: 81 },
  { cat: 'correction', sent: 96, rate: 77 },
  { cat: 'admin', sent: 87, rate: 96 },
  { cat: 'billing', sent: 64, rate: 92 },
];

const DAYS = [
  { d: '목', v: 688 }, { d: '금', v: 742 }, { d: '토', v: 415 }, { d: '일', v: 380 },
  { d: '월', v: 801 }, { d: '화', v: 864 }, { d: '수', v: 142, today: true },
];

export default function AdminNotifications() {
  const [query, setQuery] = useState('');
  const [cat, setCat] = useState('전체');
  const [read, setRead] = useState('전체');

  const catOptions = ['전체', ...Object.values(CATS).map((c) => c.label)];
  const labelToCat = (l: string) => Object.keys(CATS).find((k) => CATS[k].label === l);

  const rows = MOCK_ROWS.filter((r) => {
    if (cat !== '전체' && TYPE_CAT[r.type] !== labelToCat(cat)) return false;
    if (read === '읽음' && !r.isRead) return false;
    if (read === '미읽음' && r.isRead) return false;
    if (query) {
      const q = query.toLowerCase();
      if (!r.user.name.includes(query) && !r.title.includes(query) && !r.user.email.toLowerCase().includes(q)) return false;
    }
    return true;
  });

  const totalSent = MOCK_ROWS.length;
  const readCount = MOCK_ROWS.filter((r) => r.isRead).length;
  const readRate = totalSent ? Math.round((readCount / totalSent) * 100) : 0;
  const maxDay = Math.max(...DAYS.map((d) => d.v));

  return (
    <AdminShell
      active='notifications'
      breadcrumb='알림 모니터링'
      title='알림 모니터링'
      icon={Bell}
      desc='시스템이 자동 발송한 알림 현황 · 읽기 전용'
      actions={<button className='av-btn'><Download /> CSV</button>}
    >
      {/* Metrics */}
      <div className='av-metrics'>
        <div className='av-met'>
          <div className='av-met__l'>오늘 발송</div>
          <div className='av-met__row'><span className='av-met__n num'>142</span></div>
          <div className='av-met__d num'>일평균 817건</div>
        </div>
        <div className='av-met'>
          <div className='av-met__l'>7일 발송</div>
          <div className='av-met__row'><span className='av-met__n num'>5,720</span></div>
        </div>
        <div className='av-met'>
          <div className='av-met__l'>읽음률 (7일)</div>
          <div className='av-met__row'><span className='av-met__n num'>{readRate}<small>%</small></span></div>
        </div>
        <div className='av-met'>
          <div className='av-met__l'>미읽음 누적</div>
          <div className='av-met__row'><span className='av-met__n num'>{totalSent - readCount}</span></div>
        </div>
      </div>

      <div className='av-grid'>
        {/* Table */}
        <section className='av-panel'>
          <div className='av-filters'>
            <div className='av-search'>
              <Search />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder='수신자·제목 검색' />
            </div>
            <select className='av-select' value={cat} onChange={(e) => setCat(e.target.value)}>
              {catOptions.map((c) => <option key={c}>{c}</option>)}
            </select>
            <div className='av-seg'>
              {['전체', '읽음', '미읽음'].map((r) => (
                <button key={r} className={read === r ? 'on' : ''} onClick={() => setRead(r)}>{r}</button>
              ))}
            </div>
          </div>

          {rows.length === 0 ? (
            <div className='av-empty'>조건에 맞는 알림이 없습니다</div>
          ) : (
            <table className='av-table'>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>수신자</th>
                  <th>알림</th>
                  <th>타입</th>
                  <th>상태</th>
                  <th className='r'>발송</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const c = CATS[TYPE_CAT[r.type]] ?? CATS.admin;
                  return (
                    <tr key={r.id} className={r.isRead ? '' : 'unread'}>
                      <td className='av-id num'>#{r.id}</td>
                      <td>
                        <div className='av-user'>
                          <span className='av-user__av'>{r.user.name[0]}</span>
                          <div>
                            <div className='av-user__n'>{r.user.name}</div>
                            <div className='av-user__e'>{r.user.email}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div className='av-msg'>
                          <div className='av-msg__t'>{r.title}</div>
                          <div className='av-msg__m'>{r.message}</div>
                        </div>
                      </td>
                      <td>
                        <span className='av-type'>
                          <span className='av-dot' style={{ background: c.dot }} />
                          {c.label}
                        </span>
                      </td>
                      <td>
                        {r.isRead
                          ? <span className='av-st av-st--read num'>읽음 · {r.readAt}</span>
                          : <span className='av-st av-st--unread'>미읽음</span>}
                      </td>
                      <td className='av-time num'>{r.sent}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}

          <div className='av-foot'>
            <span className='num'>1–{rows.length} / {totalSent}건</span>
            <div className='av-pager'>
              <button disabled aria-label='이전'><ChevronLeft /></button>
              <button aria-label='다음'><ChevronRight /></button>
            </div>
          </div>
        </section>

        {/* Rail */}
        <aside className='av-rail'>
          <section className='av-panel'>
            <div className='av-mod__h'>
              <span className='av-mod__t'>타입별 읽음률</span>
              <span className='av-mod__s'>최근 7일</span>
            </div>
            <div className='av-rates'>
              {RATES.map((r) => {
                const c = CATS[r.cat];
                return (
                  <div className={`av-rate${r.low ? ' low' : ''}`} key={r.cat}>
                    <span className='av-rate__l'>
                      <span className='av-dot' style={{ background: c.dot }} />
                      {c.label}
                    </span>
                    <span className='av-rate__bar'>
                      <span className='av-rate__fill' style={{ width: `${r.rate}%` }} />
                    </span>
                    <span className='av-rate__v num'><b>{r.rate}%</b> · {r.sent}</span>
                  </div>
                );
              })}
            </div>
            <div className='av-note'>
              <b>공지 읽음률 41%</b> — 전체 평균 대비 낮아요. 발송 시간대 점검을 권장합니다.
            </div>
          </section>

          <section className='av-panel'>
            <div className='av-mod__h'>
              <span className='av-mod__t'>일별 발송량</span>
              <span className='av-mod__s'>최근 7일</span>
            </div>
            <div className='av-days'>
              {DAYS.map((d) => (
                <div className={`av-day${d.today ? ' today' : ''}`} key={d.d}>
                  <span className='av-day__v num'>{d.v}</span>
                  <span className='av-day__bar' style={{ height: Math.max(6, (d.v / maxDay) * 56) }} />
                </div>
              ))}
            </div>
            <div className='av-days__xw'>
              {DAYS.map((d) => <span key={d.d}>{d.today ? '오늘' : d.d}</span>)}
            </div>
          </section>
        </aside>
      </div>
    </AdminShell>
  );
}
