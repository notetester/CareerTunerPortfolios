import { useState, useEffect } from "react";
import { Mail, Search, ChevronLeft, ChevronRight, CheckCircle2, MessageSquareWarning } from "lucide-react";
import AdminShell from "../../../components/AdminShell";
import { type Inquiry } from "../data/inquiriesData";
import * as adminTicketApi from "../api/adminTicketApi";
import { ConfirmDialog } from "@/app/components/ui/confirm-dialog";
import { useAdminDomainAuthorization } from "../../../auth/useAdminAuthorization";
import "./admin-inquiries.css";

type FilterKey = "전체" | "대기" | "답변 완료";

const STATUS_LABEL: Record<string, string> = {
  pending: "대기", progress: "진행중", hold: "보류", answered: "답변 완료",
};

export default function AdminInquiries() {
  const { canUpdate } = useAdminDomainAuthorization("CONTENT");
  const [items, setItems] = useState<Inquiry[]>([]);
  const [filter, setFilter] = useState<FilterKey>("전체");
  const [query, setQuery] = useState("");
  const [toast, setToast] = useState<{ msg: string; tone: string } | null>(null);
  const [dialog, setDialog] = useState<{ inquiry: Inquiry; target: string } | null>(null);

  useEffect(() => {
    adminTicketApi.getTickets().then(setItems)
      .catch(() => flash("문의 목록을 불러오지 못했습니다.", "red"));
  }, []);

  const flash = (msg: string, tone: string) => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2200);
  };

  const handleConfirm = async () => {
    if (!canUpdate || !dialog) return;
    try {
      const updated = await adminTicketApi.updateTicket(dialog.inquiry.id, { status: dialog.target });
      setItems((prev) => prev.map((i) => (i.id === updated.id ? updated : i)));
      flash(`문의가 ${STATUS_LABEL[dialog.target] ?? dialog.target}(으)로 변경되었습니다.`, "green");
    } catch {
      flash("처리에 실패했습니다.", "red");
    }
    setDialog(null);
  };

  const pendingCount = items.filter((i) => i.status === "pending" || i.status === "progress" || i.status === "hold").length;
  const answeredCount = items.filter((i) => i.status === "answered").length;

  const filtered = items.filter((i) => {
    if (filter === "대기" && i.status === "answered") return false;
    if (filter === "답변 완료" && i.status !== "answered") return false;
    if (query) {
      const q = query.toLowerCase();
      if (!i.title.toLowerCase().includes(q) && !i.member.toLowerCase().includes(q)) return false;
    }
    return true;
  });

  return (
    <AdminShell
      active="inquiries"
      breadcrumb="문의 관리"
      title="문의 관리"
      icon={Mail}
      desc="1:1 문의 응대 — AI 답변 초안이 준비된 문의는 표시됩니다"
      actions={<button className="av-btn">CSV</button>}
    >
      {/* Metrics */}
      <div className="av-metrics">
        <div className="av-met">
          <div className="av-met__l">답변 대기</div>
          <div className="av-met__row"><span className="av-met__n num">{pendingCount}</span></div>
        </div>
        <div className="av-met">
          <div className="av-met__l">오늘 답변</div>
          <div className="av-met__row"><span className="av-met__n num">{answeredCount}</span></div>
        </div>
        <div className="av-met">
          <div className="av-met__l">평균 첫 응답</div>
          <div className="av-met__row"><span className="av-met__n num">–</span></div>
        </div>
        <div className="av-met">
          <div className="av-met__l">응대 만족도</div>
          <div className="av-met__row"><span className="av-met__n num">–</span></div>
        </div>
      </div>

      {/* Table */}
      <section className="av-panel">
        <div className="av-filters">
          <div className="av-search">
            <Search />
            <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="회원·제목 검색" />
          </div>
          <div className="right">
            <div className="av-seg">
              {(["전체", "대기", "답변 완료"] as FilterKey[]).map((f) => (
                <button key={f} className={filter === f ? "on" : ""} onClick={() => setFilter(f)}>{f}</button>
              ))}
            </div>
          </div>
        </div>

        <table className="av-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>회원</th>
              <th>문의</th>
              <th>분류</th>
              <th>상태</th>
              <th className="r">접수</th>
              {canUpdate && <th className="r">조치</th>}
            </tr>
          </thead>
          <tbody>
            {filtered.map((i) => (
              <tr key={i.id}>
                <td className="av-id num">T-{i.id}</td>
                <td>
                  <div className="av-user">
                    <span className="av-user__av">{i.member[0]}</span>
                    <div><div className="av-user__n">{i.member}</div></div>
                  </div>
                </td>
                <td>
                  <div className="av-cell__t">{i.title}</div>
                  <div className="av-cell__m">{i.msgs[0]?.text ?? ""}</div>
                </td>
                <td className="av-muted" style={{ whiteSpace: "nowrap" }}>{i.cat}</td>
                <td>
                  {i.status === "answered"
                    ? <span className="av-st av-st--off">답변 완료</span>
                    : <span className="av-st av-st--warn">대기</span>}
                </td>
                <td className="r av-muted num">{i.date}</td>
                {canUpdate && <td className="r">
                  <div className="inq-actions">
                    {i.status !== "answered" && (
                      <button
                        className="av-btn"
                        title="답변 완료"
                        onClick={() => setDialog({ inquiry: i, target: "answered" })}
                      >
                        <CheckCircle2 />
                      </button>
                    )}
                    {i.status === "answered" && (
                      <button
                        className="av-btn"
                        title="재오픈"
                        onClick={() => setDialog({ inquiry: i, target: "pending" })}
                      >
                        <MessageSquareWarning />
                      </button>
                    )}
                  </div>
                </td>}
              </tr>
            ))}
          </tbody>
        </table>

        <div className="av-foot">
          <span className="num">{filtered.length}건 표시 · 전체 {items.length}건</span>
          <div className="av-pager">
            <button disabled aria-label="이전"><ChevronLeft /></button>
            <button aria-label="다음"><ChevronRight /></button>
          </div>
        </div>
      </section>

      {canUpdate && dialog && (() => {
        const isClose = dialog.target === "answered";
        return (
          <ConfirmDialog
            variant={isClose ? "success" : "warning"}
            icon={isClose ? <CheckCircle2 /> : <MessageSquareWarning />}
            title={isClose ? "이 문의를 답변 완료 처리할까요?" : "이 문의를 다시 열까요?"}
            description={isClose
              ? "답변 완료로 전환하면 대기 큐에서 제거됩니다."
              : "재오픈하면 대기 큐에 다시 추가됩니다."}
            meta={[
              { label: "회원", value: dialog.inquiry.member },
              { label: "문의", value: dialog.inquiry.title },
              { label: "분류", value: dialog.inquiry.cat },
            ]}
            confirmLabel={isClose ? "답변 완료" : "재오픈"}
            onConfirm={handleConfirm}
            onCancel={() => setDialog(null)}
          />
        );
      })()}

      {toast && <div className={`inq-toast inq-toast--${toast.tone}`}>{toast.msg}</div>}
    </AdminShell>
  );
}
