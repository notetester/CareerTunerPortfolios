import { useCallback, useEffect, useMemo, useState } from "react";
import { Briefcase, Check, GitCompareArrows, RefreshCw, X } from "lucide-react";
import AdminShell from "@/admin/components/AdminShell";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Textarea } from "@/app/components/ui/textarea";
import {
  CAREER_LEVEL_LABELS,
  EDUCATION_LEVEL_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  JOB_POSTING_FIELD_LABELS,
  TRUST_GRADE_LABELS,
  type JobPostingFields,
} from "@/features/company/types/company";
import {
  approveJobPostingReview,
  fetchJobPostingReviewDetail,
  fetchJobPostingReviewQueue,
  rejectJobPostingReview,
  type JobPostingReviewDetail,
  type JobPostingReviewRow,
} from "../api";

function formatDateTime(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR");
}

/** diff/상세 표기용 필드 값 문자열화 — enum 은 한국어 라벨, 배열·불리언도 사람이 읽는 형태로. */
function displayValue(key: keyof JobPostingFields, value: unknown): string {
  if (value === null || value === undefined || value === "") return "-";
  if (key === "employmentType") return EMPLOYMENT_TYPE_LABELS[String(value)] ?? String(value);
  if (key === "careerLevel") return CAREER_LEVEL_LABELS[String(value)] ?? String(value);
  if (key === "educationLevel") return EDUCATION_LEVEL_LABELS[String(value)] ?? String(value);
  if (Array.isArray(value)) return value.length === 0 ? "-" : value.join(", ");
  if (typeof value === "boolean") return value ? "예" : "아니오";
  return String(value);
}

/** 두 필드 값이 같은지(태그 배열 포함) 비교한다. */
function sameValue(a: unknown, b: unknown): boolean {
  if (Array.isArray(a) || Array.isArray(b)) {
    const left = Array.isArray(a) ? a : [];
    const right = Array.isArray(b) ? b : [];
    return left.length === right.length && left.every((item, index) => item === right[index]);
  }
  return (a ?? null) === (b ?? null);
}

/** 채용공고 검토 큐 — 신규 등록 검토와 게시 중 수정(diff) 검토를 함께 처리한다. */
export function AdminJobPostingReviewPage() {
  const [queue, setQueue] = useState<JobPostingReviewRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<JobPostingReviewDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [processing, setProcessing] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setQueue(await fetchJobPostingReviewQueue());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "검토 큐를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const openDetail = async (postingId: number) => {
    setSelectedId(postingId);
    setDetail(null);
    setDetailLoading(true);
    setError(null);
    try {
      setDetail(await fetchJobPostingReviewDetail(postingId));
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "검토 상세를 불러오지 못했습니다.");
      setSelectedId(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const closeDetail = () => {
    setSelectedId(null);
    setDetail(null);
    setRejectOpen(false);
    setRejectReason("");
  };

  const approve = async () => {
    if (selectedId == null || !detail) return;
    const isRevision = detail.pendingRevision != null;
    if (!window.confirm(isRevision ? "변경 사항을 승인해 게시 내용에 반영할까요?" : "이 공고를 승인해 게시할까요?")) return;
    setProcessing(true);
    setError(null);
    try {
      await approveJobPostingReview(selectedId);
      setMessage(isRevision ? "변경 사항을 반영했습니다." : "공고를 게시했습니다.");
      closeDetail();
      await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "승인 처리에 실패했습니다.");
    } finally {
      setProcessing(false);
    }
  };

  const reject = async () => {
    if (selectedId == null) return;
    if (!rejectReason.trim()) {
      setError("반려 사유를 입력해 주세요.");
      return;
    }
    setProcessing(true);
    setError(null);
    try {
      await rejectJobPostingReview(selectedId, rejectReason.trim());
      setMessage("검토를 반려했습니다. 기업에 사유가 알림으로 전달됩니다.");
      closeDetail();
      await load();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "반려 처리에 실패했습니다.");
    } finally {
      setProcessing(false);
    }
  };

  /** 수정 검토(diff) 행 — 변경된 필드만 기존↔변경 비교로 보여준다. */
  const diffRows = useMemo(() => {
    if (!detail?.pendingRevision) return [];
    const revision = detail.pendingRevision;
    return JOB_POSTING_FIELD_LABELS.filter(({ key }) => !sameValue(detail.posting[key], revision[key]))
      .map(({ key, label }) => ({
        key,
        label,
        before: displayValue(key, detail.posting[key]),
        after: displayValue(key, revision[key]),
      }));
  }, [detail]);

  return (
    <AdminShell
      active="job-posting-review"
      breadcrumb="공고 검토"
      title="채용공고 검토 큐"
      icon={Briefcase}
      desc="신뢰등급 정책에 따라 검토가 필요한 신규 공고와 게시 중 공고의 수정 변경본을 승인/반려합니다."
      actions={
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
          새로고침
        </Button>
      }
    >
      <div className="space-y-4">
        {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

        <Card className="border-slate-200 bg-card">
          <CardContent className="pt-6">
            {loading ? (
              <p className="py-10 text-center text-sm text-slate-500">불러오는 중...</p>
            ) : queue.length === 0 ? (
              <p className="py-10 text-center text-sm text-slate-500">검토 대기 중인 공고가 없습니다.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b border-slate-200 text-left text-xs text-slate-500">
                      <th className="px-2 py-2">유형</th>
                      <th className="px-2 py-2">공고</th>
                      <th className="px-2 py-2">기업</th>
                      <th className="px-2 py-2">신뢰등급</th>
                      <th className="px-2 py-2">제출 시각</th>
                      <th className="px-2 py-2 text-right">검토</th>
                    </tr>
                  </thead>
                  <tbody>
                    {queue.map((row) => (
                      <tr key={`${row.reviewType}-${row.postingId}-${row.revisionId ?? 0}`} className="border-b border-slate-100">
                        <td className="px-2 py-3">
                          {row.reviewType === "CREATE" ? (
                            <Badge className="bg-blue-100 text-blue-700">신규 등록</Badge>
                          ) : (
                            <Badge className="bg-amber-100 text-amber-700">수정 검토</Badge>
                          )}
                        </td>
                        <td className="px-2 py-3">
                          <div className="font-medium text-slate-900">{row.title}</div>
                          <div className="text-xs text-slate-500">{row.jobRole}</div>
                        </td>
                        <td className="px-2 py-3 text-slate-700">{row.companyName ?? "-"}</td>
                        <td className="px-2 py-3 text-slate-700">
                          {row.trustGrade ? TRUST_GRADE_LABELS[row.trustGrade] : "-"}
                        </td>
                        <td className="px-2 py-3 text-slate-500">{formatDateTime(row.submittedAt)}</td>
                        <td className="px-2 py-3 text-right">
                          <Button size="sm" variant="outline" onClick={() => void openDetail(row.postingId)}>
                            상세 보기
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 검토 상세 모달 */}
      {selectedId != null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="max-h-[85vh] w-full max-w-3xl overflow-y-auto rounded-xl bg-white p-6 shadow-xl">
            {detailLoading || !detail ? (
              <p className="py-10 text-center text-sm text-slate-500">검토 상세를 불러오는 중...</p>
            ) : (
              <div className="space-y-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <h2 className="text-base font-semibold text-slate-900">{detail.posting.title}</h2>
                    <p className="mt-1 text-sm text-slate-500">
                      {detail.posting.companyName ?? "-"} · {detail.posting.jobRole}
                      {detail.posting.trustGrade && ` · ${TRUST_GRADE_LABELS[detail.posting.trustGrade]}`}
                    </p>
                  </div>
                  <Button variant="outline" size="sm" onClick={closeDetail}>닫기</Button>
                </div>

                {detail.pendingRevision ? (
                  /* 수정 검토 — 기존 게시본 ↔ 변경본 필드 diff */
                  <Card className="border-amber-200">
                    <CardHeader>
                      <CardTitle className="flex items-center gap-2 text-sm">
                        <GitCompareArrows className="size-4 text-amber-600" />
                        변경 사항 비교 (기존 → 변경)
                      </CardTitle>
                    </CardHeader>
                    <CardContent>
                      {diffRows.length === 0 ? (
                        <p className="text-sm text-slate-500">변경된 필드가 없습니다. 승인 시 내용 변화 없이 검토만 종료됩니다.</p>
                      ) : (
                        <div className="space-y-3">
                          {diffRows.map((row) => (
                            <div key={row.key} className="rounded-lg border border-slate-100 p-3">
                              <div className="text-xs font-medium text-slate-500">{row.label}</div>
                              <div className="mt-1 grid gap-2 md:grid-cols-2">
                                <div className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-800">
                                  <span className="mr-1 text-xs text-red-400">기존</span>
                                  <span className="whitespace-pre-wrap">{row.before}</span>
                                </div>
                                <div className="rounded-md bg-green-50 px-3 py-2 text-sm text-green-800">
                                  <span className="mr-1 text-xs text-green-500">변경</span>
                                  <span className="whitespace-pre-wrap">{row.after}</span>
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </CardContent>
                  </Card>
                ) : (
                  /* 신규 등록 검토 — 전체 필드 표시 */
                  <Card className="border-slate-200">
                    <CardHeader>
                      <CardTitle className="text-sm">공고 내용</CardTitle>
                    </CardHeader>
                    <CardContent>
                      <div className="space-y-2">
                        {JOB_POSTING_FIELD_LABELS.map(({ key, label }) => (
                          <div key={key} className="grid gap-1 border-b border-slate-50 py-1.5 md:grid-cols-[140px_1fr]">
                            <div className="text-xs font-medium text-slate-500">{label}</div>
                            <div className="whitespace-pre-wrap text-sm text-slate-800">
                              {displayValue(key, detail.posting[key])}
                            </div>
                          </div>
                        ))}
                      </div>
                    </CardContent>
                  </Card>
                )}

                {rejectOpen ? (
                  <div className="rounded-lg border border-red-200 bg-red-50/50 p-4">
                    <p className="text-sm font-medium text-slate-800">반려 사유 (기업에 알림으로 전달)</p>
                    <Textarea
                      className="mt-2 min-h-24 bg-white"
                      value={rejectReason}
                      onChange={(event) => setRejectReason(event.target.value)}
                      placeholder="예) 급여 정보가 실제 조건과 다르게 기재되어 있습니다. 수정 후 다시 제출해 주세요."
                    />
                    <div className="mt-3 flex justify-end gap-2">
                      <Button variant="outline" disabled={processing} onClick={() => setRejectOpen(false)}>취소</Button>
                      <Button className="bg-red-600 text-white hover:bg-red-700" disabled={processing || !rejectReason.trim()} onClick={() => void reject()}>
                        반려 확정
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
                    <Button variant="outline" disabled={processing} onClick={() => setRejectOpen(true)}>
                      <X className="size-4" />
                      반려
                    </Button>
                    <Button className="bg-green-600 text-white hover:bg-green-700" disabled={processing} onClick={() => void approve()}>
                      <Check className="size-4" />
                      {detail.pendingRevision ? "변경 승인" : "승인·게시"}
                    </Button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </AdminShell>
  );
}

export default AdminJobPostingReviewPage;
