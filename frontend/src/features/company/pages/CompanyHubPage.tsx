import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { ArrowRight, BriefcaseBusiness, Building2, Clock3, PencilLine, Plus, RefreshCw } from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Textarea } from "@/app/components/ui/textarea";
import {
  applyCompany,
  closeJobPosting,
  createJobPosting,
  getMyCompanyApplication,
  getMyCompanyProfile,
  listMyJobPostings,
  updateJobPosting,
} from "../api/companyApi";
import { JobPostingForm } from "../components/JobPostingForm";
import {
  APPLICATION_STATUS_LABELS,
  JOB_POSTING_STATUS_LABELS,
  TRUST_GRADE_LABELS,
  type CompanyApplication,
  type CompanyJobPosting,
  type CompanyProfile,
  type JobPostingUpsertPayload,
} from "../types/company";

function formatDate(value: string | null | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString("ko-KR");
}

const STATUS_BADGE_CLASS: Record<string, string> = {
  DRAFT: "bg-slate-200 text-slate-700",
  PENDING_REVIEW: "bg-amber-100 text-amber-700",
  PUBLISHED: "bg-green-100 text-green-700",
  REJECTED: "bg-red-100 text-red-700",
  CLOSED: "bg-slate-300 text-slate-700",
};

/** 기업 서비스 대분류 허브. 신청·공고 관리와 공개 채용공고 탐색을 서로 다른 화면으로 안내한다. */
export function CompanyServiceOverviewPage() {
  const destinations = [
    {
      href: "/company/manage",
      title: "기업 계정 신청·공고 관리",
      description: "기업 계정 전환을 신청하고 승인 상태, 내 채용공고 작성·수정·마감을 관리합니다.",
      icon: Building2,
    },
    {
      href: "/jobs",
      title: "채용공고 게시판",
      description: "승인된 기업이 공개한 채용공고를 검색하고 상세 모집 조건을 확인합니다.",
      icon: BriefcaseBusiness,
    },
  ] as const;

  return (
    <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <header>
        <h1 className="flex items-center gap-2 text-2xl font-black text-slate-900">
          <Building2 className="size-6 text-blue-600" />
          기업 서비스
        </h1>
        <p className="mt-1 text-sm text-slate-500">기업 계정 신청과 공고 운영, 구직자용 채용공고 탐색을 목적에 맞는 독립 화면에서 이용합니다.</p>
      </header>
      <section className="grid gap-4 md:grid-cols-2" aria-label="기업 서비스 기능">
        {destinations.map((item) => (
          <Link key={item.href} to={item.href} className="group rounded-2xl border border-slate-200 bg-card p-6 shadow-sm transition hover:-translate-y-0.5 hover:border-blue-300 hover:shadow-md">
            <span className="flex size-12 items-center justify-center rounded-xl bg-blue-50 text-blue-700"><item.icon className="size-6" /></span>
            <h2 className="mt-5 text-lg font-black text-slate-900">{item.title}</h2>
            <p className="mt-2 min-h-12 text-sm leading-6 text-slate-500">{item.description}</p>
            <span className="mt-5 inline-flex items-center gap-1 text-sm font-bold text-blue-700">화면 열기 <ArrowRight className="size-4" /></span>
          </Link>
        ))}
      </section>
    </div>
  );
}

/** 기업 허브 — 일반 회원에게는 기업 신청 폼/상태, 승인된 기업에게는 내 공고 관리를 보여준다. */
export function CompanyHubPage() {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [profile, setProfile] = useState<CompanyProfile | null>(null);
  const [application, setApplication] = useState<CompanyApplication | null>(null);
  const [postings, setPostings] = useState<CompanyJobPosting[]>([]);
  const [editorTarget, setEditorTarget] = useState<CompanyJobPosting | "new" | null>(null);
  const [saving, setSaving] = useState(false);

  // 신청 폼 상태
  const [companyName, setCompanyName] = useState("");
  const [businessNumber, setBusinessNumber] = useState("");
  const [contact, setContact] = useState("");
  const [description, setDescription] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const nextProfile = await getMyCompanyProfile();
      setProfile(nextProfile);
      if (nextProfile) {
        setPostings(await listMyJobPostings());
      } else {
        setApplication(await getMyCompanyApplication());
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "기업 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const submitApplication = async () => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const created = await applyCompany({
        companyName: companyName.trim(),
        businessNumber: businessNumber.trim() || undefined,
        contact: contact.trim(),
        description: description.trim() || undefined,
      });
      setApplication(created);
      setMessage("기업 계정 신청이 접수되었습니다. 관리자 검토 후 알림으로 안내됩니다.");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "신청에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const savePosting = async (payload: JobPostingUpsertPayload) => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      if (editorTarget === "new" || editorTarget === null) {
        await createJobPosting(payload);
        setMessage(payload.submit ? "공고를 제출했습니다." : "공고를 임시저장했습니다.");
      } else {
        const updated = await updateJobPosting(editorTarget.id, payload);
        setMessage(
          updated.hasPendingRevision
            ? "수정 변경본이 검토 대기열에 등록되었습니다. 승인되면 게시 내용에 반영됩니다."
            : "공고를 저장했습니다.",
        );
      }
      setEditorTarget(null);
      setPostings(await listMyJobPostings());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "공고 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const close = async (posting: CompanyJobPosting) => {
    if (!window.confirm(`'${posting.title}' 공고를 마감할까요?`)) return;
    try {
      await closeJobPosting(posting.id);
      setPostings(await listMyJobPostings());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "마감 처리에 실패했습니다.");
    }
  };

  if (loading) {
    return <div className="mx-auto w-full max-w-[1400px] px-4 py-16 text-center text-slate-500 sm:px-6 lg:px-8">불러오는 중...</div>;
  }

  return (
    <div className="mx-auto w-full max-w-[1400px] space-y-4 px-4 py-8 sm:px-6 lg:px-8">
      <div className="flex items-center justify-between gap-3">
        <h1 className="flex items-center gap-2 text-xl font-bold text-slate-900">
          <Building2 className="size-5" />
          기업 서비스
        </h1>
        <Button variant="outline" size="sm" onClick={() => void load()}>
          <RefreshCw className="size-4" />
          새로고침
        </Button>
      </div>

      {error && <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
      {message && <div className="rounded-lg border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-700">{message}</div>}

      {profile ? (
        <>
          <Card className="border-slate-200">
            <CardHeader>
              <CardTitle className="flex items-center justify-between gap-3 text-base">
                <span>{profile.companyName}</span>
                <Badge className="bg-blue-100 text-blue-700">{TRUST_GRADE_LABELS[profile.trustGrade]}</Badge>
              </CardTitle>
              <p className="text-sm text-slate-500">
                신뢰등급에 따라 공고 등록/수정 시 관리자 검토 여부가 달라집니다.
              </p>
            </CardHeader>
          </Card>

          {editorTarget !== null ? (
            <Card className="border-slate-200">
              <CardHeader>
                <CardTitle className="text-base">
                  {editorTarget === "new" ? "새 공고 작성" : `공고 수정 — ${editorTarget.title}`}
                </CardTitle>
                {editorTarget !== "new" && editorTarget.status === "PUBLISHED" && (
                  <p className="text-sm text-amber-600">
                    게시 중인 공고입니다. 신뢰등급 정책에 따라 수정 내용이 검토 후 반영될 수 있습니다.
                  </p>
                )}
              </CardHeader>
              <CardContent>
                <JobPostingForm
                  initial={editorTarget === "new" ? undefined : editorTarget}
                  saving={saving}
                  onSave={(payload) => void savePosting(payload)}
                  onCancel={() => setEditorTarget(null)}
                />
              </CardContent>
            </Card>
          ) : (
            <Card className="border-slate-200">
              <CardHeader>
                <CardTitle className="flex items-center justify-between gap-3 text-base">
                  <span>내 공고 관리</span>
                  <Button size="sm" className="bg-blue-600 text-white hover:bg-blue-700" onClick={() => setEditorTarget("new")}>
                    <Plus className="size-4" />
                    새 공고 작성
                  </Button>
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {postings.length === 0 && (
                  <p className="py-8 text-center text-sm text-slate-500">등록된 공고가 없습니다. 첫 공고를 작성해 보세요.</p>
                )}
                {postings.map((posting) => (
                  <div key={posting.id} className="rounded-lg border border-slate-200 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-medium text-slate-900">{posting.title}</span>
                        <Badge className={STATUS_BADGE_CLASS[posting.status]}>
                          {JOB_POSTING_STATUS_LABELS[posting.status]}
                        </Badge>
                        {posting.hasPendingRevision && (
                          <Badge className="bg-amber-100 text-amber-700">
                            <Clock3 className="mr-1 size-3" />
                            수정 검토 대기
                          </Badge>
                        )}
                      </div>
                      <div className="flex gap-2">
                        {posting.status === "PUBLISHED" && (
                          <Link to={`/jobs/${posting.id}`} className="text-sm text-blue-600 hover:underline">
                            게시글 보기
                          </Link>
                        )}
                      </div>
                    </div>
                    <p className="mt-1 text-sm text-slate-500">
                      {posting.jobRole} · 조회 {posting.viewCount} · 등록 {formatDate(posting.createdAt)}
                      {posting.alwaysOpen ? " · 상시" : posting.deadlineDate ? ` · 마감 ${formatDate(posting.deadlineDate)}` : ""}
                    </p>
                    {posting.status === "REJECTED" && posting.rejectReason && (
                      <p className="mt-2 rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">반려 사유: {posting.rejectReason}</p>
                    )}
                    <div className="mt-3 flex gap-2">
                      {posting.status !== "CLOSED" && (
                        <Button variant="outline" size="sm" onClick={() => setEditorTarget(posting)}>
                          <PencilLine className="size-4" />
                          수정
                        </Button>
                      )}
                      {posting.status === "PUBLISHED" && (
                        <Button variant="outline" size="sm" onClick={() => void close(posting)}>
                          마감
                        </Button>
                      )}
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          )}
        </>
      ) : (
        <Card className="border-slate-200">
          <CardHeader>
            <CardTitle className="text-base">기업 계정 신청</CardTitle>
            <p className="text-sm text-slate-500">
              승인되면 채용공고 게시판에 공고를 등록할 수 있는 기업 계정으로 전환됩니다.
            </p>
          </CardHeader>
          <CardContent className="space-y-4">
            {application?.status === "PENDING" ? (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-4">
                <Badge className="bg-amber-100 text-amber-700">{APPLICATION_STATUS_LABELS.PENDING}</Badge>
                <p className="mt-2 text-sm text-slate-700">
                  <span className="font-medium">{application.companyName}</span> 기업 계정 신청을 검토하고 있습니다.
                </p>
                <p className="mt-1 text-xs text-slate-500">신청일: {formatDate(application.createdAt)}</p>
              </div>
            ) : (
              <>
                {application?.status === "REJECTED" && (
                  <div className="rounded-lg border border-red-200 bg-red-50 px-4 py-3">
                    <Badge className="bg-red-100 text-red-700">{APPLICATION_STATUS_LABELS.REJECTED}</Badge>
                    <p className="mt-2 text-sm text-red-700">반려 사유: {application.rejectReason ?? "-"}</p>
                    <p className="mt-1 text-xs text-slate-500">아래에서 다시 신청할 수 있습니다.</p>
                  </div>
                )}
                <div className="grid gap-3 md:grid-cols-2">
                  <label className="space-y-1">
                    <span className="text-sm font-medium text-slate-700">기업명 *</span>
                    <Input value={companyName} onChange={(e) => setCompanyName(e.target.value)} placeholder="예) 커리어튜너 주식회사" />
                  </label>
                  <label className="space-y-1">
                    <span className="text-sm font-medium text-slate-700">사업자등록번호</span>
                    <Input value={businessNumber} onChange={(e) => setBusinessNumber(e.target.value)} placeholder="예) 123-45-67890" />
                  </label>
                  <label className="space-y-1 md:col-span-2">
                    <span className="text-sm font-medium text-slate-700">담당자 연락처 *</span>
                    <Input value={contact} onChange={(e) => setContact(e.target.value)} placeholder="예) 홍길동 / 010-1234-5678" />
                  </label>
                  <label className="space-y-1 md:col-span-2">
                    <span className="text-sm font-medium text-slate-700">신청 설명</span>
                    <Textarea className="min-h-24" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="채용 계획, 서비스 소개 등을 자유롭게 적어 주세요." />
                  </label>
                </div>
                <div className="flex justify-end">
                  <Button
                    className="bg-blue-600 text-white hover:bg-blue-700"
                    disabled={saving || !companyName.trim() || !contact.trim()}
                    onClick={() => void submitApplication()}
                  >
                    기업 계정 신청
                  </Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
