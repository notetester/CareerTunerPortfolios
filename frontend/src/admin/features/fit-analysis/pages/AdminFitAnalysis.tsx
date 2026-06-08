import { useEffect, useMemo, useState } from "react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Progress } from "@/app/components/ui/progress";
import {
  AlertCircle,
  BarChart3,
  Bookmark,
  CheckCircle2,
  ClipboardList,
  Loader2,
  MessageSquareText,
  PenLine,
  Trash2,
} from "lucide-react";
import {
  createAdminFitAnalysisMemo,
  deleteAdminFitAnalysisMemo,
  getAdminFitAnalyses,
  getAdminFitAnalysis,
  updateAdminFitAnalysisMemo,
} from "../api/adminFitAnalysisApi";
import type {
  AdminFitAnalysisDetail,
  AdminFitAnalysisListItem,
  AdminFitAnalysisMemo,
} from "../types/adminFitAnalysis";

const memoTypeOptions = [
  { value: "GENERAL", label: "일반" },
  { value: "QUALITY", label: "품질 확인" },
  { value: "USER_INQUIRY", label: "문의 대응" },
  { value: "REANALYSIS", label: "재분석 필요" },
];

const statusLabel: Record<string, string> = {
  DRAFT: "초안",
  ANALYZING: "분석 중",
  READY: "준비 완료",
  APPLIED: "지원 완료",
  CLOSED: "종료",
};

function formatDateTime(value: string | null) {
  if (!value) return "기록 없음";
  return new Intl.DateTimeFormat("ko-KR", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function scoreTone(score: number | null) {
  if ((score ?? 0) >= 70) return "text-green-600";
  if ((score ?? 0) >= 50) return "text-amber-600";
  return "text-red-500";
}

export default function AdminFitAnalysisPage() {
  const [items, setItems] = useState<AdminFitAnalysisListItem[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<AdminFitAnalysisDetail | null>(null);
  const [loadingList, setLoadingList] = useState(true);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [memoType, setMemoType] = useState("GENERAL");
  const [memoContent, setMemoContent] = useState("");
  const [editingMemo, setEditingMemo] = useState<AdminFitAnalysisMemo | null>(null);
  const [savingMemo, setSavingMemo] = useState(false);

  useEffect(() => {
    let ignore = false;
    setLoadingList(true);
    setError(null);

    getAdminFitAnalyses()
      .then((data) => {
        if (ignore) return;
        setItems(data);
        setSelectedId(data[0]?.id ?? null);
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "적합도 분석 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoadingList(false);
      });

    return () => {
      ignore = true;
    };
  }, []);

  useEffect(() => {
    if (selectedId == null) {
      setDetail(null);
      return;
    }

    let ignore = false;
    setLoadingDetail(true);
    setError(null);

    getAdminFitAnalysis(selectedId)
      .then((data) => {
        if (!ignore) setDetail(data);
      })
      .catch((requestError) => {
        if (!ignore) setError(requestError instanceof Error ? requestError.message : "적합도 분석 상세를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!ignore) setLoadingDetail(false);
      });

    return () => {
      ignore = true;
    };
  }, [selectedId]);

  const averageScore = useMemo(() => {
    const scored = items.filter((item) => item.fitScore != null);
    if (scored.length === 0) return 0;
    return Math.round(scored.reduce((sum, item) => sum + (item.fitScore ?? 0), 0) / scored.length);
  }, [items]);

  const memoSummary = useMemo(() => {
    return items.reduce((sum, item) => sum + item.memoCount, 0);
  }, [items]);

  function resetMemoForm() {
    setMemoType("GENERAL");
    setMemoContent("");
    setEditingMemo(null);
  }

  function startEditMemo(memo: AdminFitAnalysisMemo) {
    setEditingMemo(memo);
    setMemoType(memo.memoType);
    setMemoContent(memo.content);
  }

  async function submitMemo() {
    if (!detail || !memoContent.trim()) return;

    setSavingMemo(true);
    setError(null);
    try {
      if (editingMemo) {
        await updateAdminFitAnalysisMemo(detail.id, editingMemo.id, { memoType, content: memoContent });
      } else {
        await createAdminFitAnalysisMemo(detail.id, { memoType, content: memoContent });
      }
      const refreshedDetail = await getAdminFitAnalysis(detail.id);
      const refreshedItems = await getAdminFitAnalyses();
      setDetail(refreshedDetail);
      setItems(refreshedItems);
      resetMemoForm();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "운영 메모를 저장하지 못했습니다.");
    } finally {
      setSavingMemo(false);
    }
  }

  async function removeMemo(memo: AdminFitAnalysisMemo) {
    if (!detail) return;

    setSavingMemo(true);
    setError(null);
    try {
      await deleteAdminFitAnalysisMemo(detail.id, memo.id);
      const refreshedDetail = await getAdminFitAnalysis(detail.id);
      const refreshedItems = await getAdminFitAnalyses();
      setDetail(refreshedDetail);
      setItems(refreshedItems);
      if (editingMemo?.id === memo.id) resetMemoForm();
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "운영 메모를 삭제하지 못했습니다.");
    } finally {
      setSavingMemo(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6">
        <section className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
          <div>
            <Badge className="mb-3 bg-blue-600 text-white">Fit Analysis Ops</Badge>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
              <BarChart3 className="size-6 text-blue-600" />
              적합도 분석 관리
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              지원 건별 적합도, 매칭/부족 역량, 추천 학습 결과와 운영 메모를 함께 확인합니다.
            </p>
          </div>
          <div className="grid grid-cols-3 gap-2 sm:min-w-[360px]">
            {[
              { label: "분석 결과", value: `${items.length}건` },
              { label: "평균 점수", value: `${averageScore}점` },
              { label: "운영 메모", value: `${memoSummary}건` },
            ].map((stat) => (
              <Card key={stat.label} className="border border-slate-200 bg-white">
                <CardContent className="p-3">
                  <div className="text-[11px] font-semibold text-slate-400">{stat.label}</div>
                  <div className="mt-1 text-xl font-black text-slate-900">{stat.value}</div>
                </CardContent>
              </Card>
            ))}
          </div>
        </section>

        {error && (
          <Card className="border border-red-200 bg-red-50">
            <CardContent className="flex items-center gap-2 p-4 text-sm text-red-700">
              <AlertCircle className="size-4" />
              {error}
            </CardContent>
          </Card>
        )}

        <section className="grid gap-6 xl:grid-cols-[420px_minmax(0,1fr)]">
          <Card className="border border-slate-200 bg-white">
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <ClipboardList className="size-4 text-blue-600" />
                분석 결과 목록
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {loadingList ? (
                <div className="flex items-center gap-2 rounded-lg bg-slate-50 p-4 text-sm text-slate-500">
                  <Loader2 className="size-4 animate-spin" />
                  목록을 불러오는 중입니다.
                </div>
              ) : items.length > 0 ? (
                items.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setSelectedId(item.id)}
                    className={`w-full rounded-lg border p-3 text-left transition-colors ${
                      selectedId === item.id ? "border-blue-300 bg-blue-50" : "border-slate-100 bg-slate-50 hover:border-blue-200"
                    }`}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <div className="truncate text-sm font-bold text-slate-800">{item.companyName} · {item.jobTitle}</div>
                        <div className="mt-0.5 truncate text-xs text-slate-500">{item.userName} ({item.userEmail})</div>
                      </div>
                      <span className={`text-sm font-black ${scoreTone(item.fitScore)}`}>{item.fitScore ?? 0}점</span>
                    </div>
                    <div className="mt-2 flex flex-wrap items-center gap-1.5">
                      {item.favorite && <Badge className="bg-amber-100 text-amber-700">관심</Badge>}
                      <Badge className="bg-slate-100 text-slate-600">{statusLabel[item.applicationStatus] ?? item.applicationStatus}</Badge>
                      {item.memoCount > 0 && <Badge className="bg-indigo-100 text-indigo-700">메모 {item.memoCount}</Badge>}
                    </div>
                    <Progress value={item.fitScore ?? 0} className="mt-2 h-1.5" />
                  </button>
                ))
              ) : (
                <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">적합도 분석 결과가 아직 없습니다.</div>
              )}
            </CardContent>
          </Card>

          <div className="space-y-5">
            {loadingDetail ? (
              <Card className="border border-slate-200 bg-white">
                <CardContent className="flex items-center gap-2 p-5 text-sm text-slate-500">
                  <Loader2 className="size-4 animate-spin" />
                  상세를 불러오는 중입니다.
                </CardContent>
              </Card>
            ) : detail ? (
              <>
                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center justify-between gap-3 text-base">
                      <span>{detail.companyName} · {detail.jobTitle}</span>
                      <span className={`text-xl font-black ${scoreTone(detail.fitScore)}`}>{detail.fitScore ?? 0}점</span>
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="grid gap-3 md:grid-cols-3">
                      {[
                        { label: "사용자", value: `${detail.userName} (${detail.userEmail})` },
                        { label: "지원 상태", value: statusLabel[detail.applicationStatus] ?? detail.applicationStatus },
                        { label: "분석 시각", value: formatDateTime(detail.createdAt) },
                      ].map((row) => (
                        <div key={row.label} className="rounded-lg bg-slate-50 p-3">
                          <div className="text-[11px] font-semibold text-slate-400">{row.label}</div>
                          <div className="mt-1 text-sm font-semibold text-slate-700">{row.value}</div>
                        </div>
                      ))}
                    </div>

                    <div className="grid gap-4 lg:grid-cols-2">
                      <SkillBox title="매칭 역량" icon="match" items={detail.matchedSkills} />
                      <SkillBox title="부족 역량" icon="gap" items={detail.missingSkills} />
                      <SkillBox title="추천 학습" icon="study" items={detail.recommendedStudy} />
                      <SkillBox title="추천 자격증" icon="cert" items={detail.recommendedCertificates} />
                    </div>

                    <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
                      <div className="mb-2 flex items-center gap-2 text-sm font-bold text-blue-900">
                        <CheckCircle2 className="size-4" />
                        지원 전략
                      </div>
                      <p className="text-sm leading-relaxed text-blue-800">{detail.strategy || "등록된 지원 전략이 없습니다."}</p>
                    </div>
                  </CardContent>
                </Card>

                <Card className="border border-slate-200 bg-white">
                  <CardHeader className="pb-3">
                    <CardTitle className="flex items-center gap-2 text-base">
                      <MessageSquareText className="size-4 text-indigo-600" />
                      운영 메모
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                      <div className="flex flex-col gap-2 sm:flex-row">
                        <select
                          value={memoType}
                          onChange={(event) => setMemoType(event.target.value)}
                          className="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-700"
                        >
                          {memoTypeOptions.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                          ))}
                        </select>
                        <textarea
                          value={memoContent}
                          onChange={(event) => setMemoContent(event.target.value)}
                          placeholder="운영 메모를 입력하세요"
                          className="min-h-24 flex-1 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-700 outline-none focus:border-blue-400"
                        />
                      </div>
                      <div className="mt-2 flex justify-end gap-2">
                        {editingMemo && (
                          <Button type="button" variant="outline" onClick={resetMemoForm} disabled={savingMemo}>
                            취소
                          </Button>
                        )}
                        <Button type="button" onClick={submitMemo} disabled={savingMemo || !memoContent.trim()}>
                          {savingMemo ? <Loader2 className="mr-2 size-4 animate-spin" /> : <PenLine className="mr-2 size-4" />}
                          {editingMemo ? "메모 수정" : "메모 저장"}
                        </Button>
                      </div>
                    </div>

                    <div className="space-y-2">
                      {detail.memos.length > 0 ? (
                        detail.memos.map((memo) => (
                          <div key={memo.id} className="rounded-lg border border-slate-100 p-3">
                            <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                              <div>
                                <div className="flex items-center gap-2">
                                  <Badge className="bg-indigo-100 text-indigo-700">
                                    {memoTypeOptions.find((option) => option.value === memo.memoType)?.label ?? memo.memoType}
                                  </Badge>
                                  <span className="text-xs text-slate-400">{memo.adminName} · {formatDateTime(memo.updatedAt)}</span>
                                </div>
                                <p className="mt-2 whitespace-pre-wrap text-sm leading-relaxed text-slate-700">{memo.content}</p>
                              </div>
                              <div className="flex shrink-0 gap-1">
                                <Button type="button" size="sm" variant="outline" onClick={() => startEditMemo(memo)} disabled={savingMemo}>
                                  <PenLine className="size-3.5" />
                                </Button>
                                <Button type="button" size="sm" variant="outline" onClick={() => removeMemo(memo)} disabled={savingMemo}>
                                  <Trash2 className="size-3.5 text-red-500" />
                                </Button>
                              </div>
                            </div>
                          </div>
                        ))
                      ) : (
                        <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">아직 운영 메모가 없습니다.</div>
                      )}
                    </div>
                  </CardContent>
                </Card>
              </>
            ) : (
              <Card className="border border-slate-200 bg-white">
                <CardContent className="p-5 text-sm text-slate-500">선택된 적합도 분석 결과가 없습니다.</CardContent>
              </Card>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}

function SkillBox({ title, icon, items }: { title: string; icon: "match" | "gap" | "study" | "cert"; items: string[] }) {
  const Icon = icon === "match" ? CheckCircle2 : icon === "gap" ? AlertCircle : icon === "cert" ? Bookmark : ClipboardList;
  const tone = icon === "match" ? "text-green-600" : icon === "gap" ? "text-red-500" : icon === "cert" ? "text-amber-600" : "text-blue-600";

  return (
    <div className="rounded-lg border border-slate-100 p-4">
      <div className="mb-3 flex items-center gap-2 text-sm font-bold text-slate-800">
        <Icon className={`size-4 ${tone}`} />
        {title}
      </div>
      <div className="flex flex-wrap gap-1.5">
        {items.length > 0 ? (
          items.map((item) => (
            <span key={item} className="rounded bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">{item}</span>
          ))
        ) : (
          <span className="text-sm text-slate-400">등록된 항목이 없습니다.</span>
        )}
      </div>
    </div>
  );
}
