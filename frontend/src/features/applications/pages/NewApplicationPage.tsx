import { type FormEvent, type ReactNode, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router";
import {
  ArrowLeft,
  Briefcase,
  CheckCircle2,
  FileText,
  Loader2,
  SearchCheck,
  Sparkles,
  Upload,
  UserRound,
} from "lucide-react";
import { useAuth } from "@/app/auth/AuthContext";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Checkbox } from "@/app/components/ui/checkbox";
import { Input } from "@/app/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/app/components/ui/select";
import { Textarea } from "@/app/components/ui/textarea";
import { createJobAnalysis, createCompanyAnalysis } from "../api/analysisApi";
import { createApplicationCase, updateApplicationCase } from "../api/applicationCasesApi";
import { saveJobPosting, uploadJobPostingFile } from "../api/jobPostingsApi";
import { LoginRequiredState } from "../components/LoginRequiredState";
import type { ApplicationCase, ApplicationSourceType } from "../types/applicationCase";
import { APPLICATION_SOURCE_OPTIONS } from "../types/applicationCase";
import type { JobPosting } from "../types/jobPosting";

type WizardStep = 0 | 1 | 2 | 3 | 4;

interface BasicFormState {
  companyName: string;
  jobTitle: string;
  postingDate: string;
  deadlineDate: string;
  favorite: boolean;
}

interface PostingFormState {
  sourceType: ApplicationSourceType;
  text: string;
  url: string;
  file: File | null;
}

const steps: { label: string; icon: typeof Briefcase }[] = [
  { label: "기본 정보", icon: Briefcase },
  { label: "공고문 등록", icon: FileText },
  { label: "추출 확인", icon: SearchCheck },
  { label: "프로필 선택", icon: UserRound },
  { label: "분석 시작", icon: Sparkles },
];

function displayPostingText(posting: JobPosting | null): string {
  return posting?.extractedText ?? posting?.originalText ?? "";
}

export function NewApplicationPage() {
  const navigate = useNavigate();
  const { loading: authLoading, isAuthenticated } = useAuth();
  const [step, setStep] = useState<WizardStep>(0);
  const [createdCase, setCreatedCase] = useState<ApplicationCase | null>(null);
  const [jobPosting, setJobPosting] = useState<JobPosting | null>(null);
  const [confirmedText, setConfirmedText] = useState("");
  const [includeCompanyAnalysis, setIncludeCompanyAnalysis] = useState(false);
  const [jobAnalysisCompleted, setJobAnalysisCompleted] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [basicForm, setBasicForm] = useState<BasicFormState>({
    companyName: "",
    jobTitle: "",
    postingDate: "",
    deadlineDate: "",
    favorite: false,
  });
  const [postingForm, setPostingForm] = useState<PostingFormState>({
    sourceType: "TEXT",
    text: "",
    url: "",
    file: null,
  });

  const activePostingText = useMemo(() => displayPostingText(jobPosting), [jobPosting]);

  const setBasicField = <Key extends keyof BasicFormState>(key: Key, value: BasicFormState[Key]) => {
    setBasicForm((current) => ({ ...current, [key]: value }));
  };

  const setPostingField = <Key extends keyof PostingFormState>(key: Key, value: PostingFormState[Key]) => {
    setPostingForm((current) => ({ ...current, [key]: value }));
  };

  const createCaseIfNeeded = async (): Promise<ApplicationCase> => {
    if (createdCase) return createdCase;
    const companyName = basicForm.companyName.trim();
    const jobTitle = basicForm.jobTitle.trim();
    if (!companyName || !jobTitle) {
      throw new Error("기업명과 직무명을 입력하세요.");
    }

    const created = await createApplicationCase({
      companyName,
      jobTitle,
      postingDate: basicForm.postingDate || null,
      deadlineDate: basicForm.deadlineDate || null,
      status: "DRAFT",
      favorite: basicForm.favorite,
    });
    setCreatedCase(created);
    return created;
  };

  const handleBasicSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createCaseIfNeeded();
      setStep(1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건을 생성하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const syncCaseSourceType = async (
    applicationCase: ApplicationCase,
    sourceType: ApplicationSourceType,
  ): Promise<ApplicationCase> => {
    if (applicationCase.sourceType === sourceType) {
      return applicationCase;
    }
    const updated = await updateApplicationCase(applicationCase.id, { sourceType });
    setCreatedCase(updated);
    return updated;
  };

  const handleSavePosting = async () => {
    setBusy(true);
    setError(null);
    try {
      const applicationCase = await createCaseIfNeeded();
      let saved: JobPosting;

      if (postingForm.sourceType === "PDF" || postingForm.sourceType === "IMAGE") {
        if (!postingForm.file) {
          throw new Error("업로드할 파일을 선택하세요.");
        }
        saved = await uploadJobPostingFile(applicationCase.id, postingForm.sourceType, postingForm.file);
      } else if (postingForm.sourceType === "URL") {
        const url = postingForm.url.trim();
        if (!url) {
          throw new Error("공고 URL을 입력하세요.");
        }
        saved = await saveJobPosting(applicationCase.id, {
          uploadedFileUrl: url,
          sourceType: "URL",
        });
      } else {
        const text = postingForm.text.trim();
        if (!text) {
          throw new Error("공고문 내용을 입력하세요.");
        }
        saved = await saveJobPosting(applicationCase.id, {
          originalText: text,
          sourceType: postingForm.sourceType,
        });
      }

      await syncCaseSourceType(applicationCase, saved.sourceType);
      setJobPosting(saved);
      setConfirmedText(displayPostingText(saved));
      setJobAnalysisCompleted(false);
      setStep(2);
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고문을 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleConfirmPosting = async () => {
    if (!createdCase || !jobPosting) return;
    const trimmed = confirmedText.trim();
    if (!trimmed) {
      setError("확인된 공고문 내용이 필요합니다.");
      return;
    }

    setBusy(true);
    setError(null);
    try {
      if (trimmed !== activePostingText.trim()) {
        const saved = await saveJobPosting(createdCase.id, {
          originalText: jobPosting.sourceType === "URL" ? null : trimmed,
          uploadedFileUrl: jobPosting.sourceType === "URL" ? jobPosting.uploadedFileUrl : null,
          extractedText: jobPosting.sourceType === "URL" ? trimmed : null,
          sourceType: jobPosting.sourceType,
        });
        await syncCaseSourceType(createdCase, saved.sourceType);
        setJobPosting(saved);
        setConfirmedText(displayPostingText(saved));
        setJobAnalysisCompleted(false);
      }
      setStep(3);
    } catch (err) {
      setError(err instanceof Error ? err.message : "보정한 공고문을 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleStartAnalysis = async () => {
    if (!createdCase) return;
    setBusy(true);
    setError(null);
    try {
      if (!jobAnalysisCompleted) {
        await createJobAnalysis(createdCase.id);
        setJobAnalysisCompleted(true);
      }
      if (includeCompanyAnalysis) {
        await createCompanyAnalysis(createdCase.id);
        navigate(`/applications/${createdCase.id}/company-analysis`);
      } else {
        navigate(`/applications/${createdCase.id}/job-analysis`);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "분석을 시작하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  if (authLoading) {
    return (
      <div className="min-h-[calc(100vh-72px)] bg-slate-50 px-4 py-10">
        <div className="mx-auto max-w-4xl">
          <div className="h-96 animate-pulse rounded-lg bg-slate-200" />
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return (
      <LoginRequiredState
        title="새 지원 건은 로그인 후 만들 수 있습니다"
        description="지원 건은 사용자별 데이터로 저장됩니다."
      />
    );
  }

  return (
    <div className="min-h-[calc(100vh-72px)] bg-slate-50">
      <div className="mx-auto max-w-5xl space-y-6 px-4 py-8 sm:px-6">
        <Button asChild variant="ghost" className="px-0 text-slate-600 hover:bg-transparent hover:text-blue-700">
          <Link to="/applications">
            <ArrowLeft className="size-4" />
            목록으로
          </Link>
        </Button>

        <div>
          <h1 className="flex items-center gap-2 text-2xl font-bold text-slate-950">
            <Briefcase className="size-6 text-blue-600" />
            새 지원 건
          </h1>
          <p className="mt-1 text-sm text-slate-500">공고 등록부터 분석 시작까지 한 흐름으로 진행합니다.</p>
        </div>

        <div className="grid gap-2 md:grid-cols-5">
          {steps.map((item, index) => (
            <div
              key={item.label}
              className={`rounded-lg border p-3 ${
                index === step
                  ? "border-blue-300 bg-blue-50 text-blue-800"
                  : index < step
                    ? "border-emerald-200 bg-emerald-50 text-emerald-800"
                    : "border-slate-200 bg-white text-slate-500"
              }`}
            >
              <div className="flex items-center gap-2 text-xs font-semibold">
                <item.icon className="size-4" />
                {index + 1}. {item.label}
              </div>
            </div>
          ))}
        </div>

        <Card className="border-slate-200 bg-white">
          <CardHeader>
            <CardTitle className="text-lg font-bold text-slate-900">{steps[step].label}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            {step === 0 && (
              <form className="space-y-5" onSubmit={(event) => void handleBasicSubmit(event)}>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field label="기업명" htmlFor="companyName">
                    <Input
                      id="companyName"
                      value={basicForm.companyName}
                      onChange={(event) => setBasicField("companyName", event.target.value)}
                      autoComplete="organization"
                    />
                  </Field>
                  <Field label="직무명" htmlFor="jobTitle">
                    <Input
                      id="jobTitle"
                      value={basicForm.jobTitle}
                      onChange={(event) => setBasicField("jobTitle", event.target.value)}
                    />
                  </Field>
                </div>

                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                  <Field label="공고일" htmlFor="postingDate">
                    <Input
                      id="postingDate"
                      type="date"
                      value={basicForm.postingDate}
                      onChange={(event) => setBasicField("postingDate", event.target.value)}
                    />
                  </Field>
                  <Field label="마감일" htmlFor="deadlineDate">
                    <Input
                      id="deadlineDate"
                      type="date"
                      value={basicForm.deadlineDate}
                      onChange={(event) => setBasicField("deadlineDate", event.target.value)}
                    />
                  </Field>
                  <Field label="기본 등록 방식">
                    <Select
                      value={postingForm.sourceType}
                      onValueChange={(value) => setPostingField("sourceType", value as ApplicationSourceType)}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {APPLICATION_SOURCE_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </Field>
                </div>

                <label className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
                  <Checkbox
                    checked={basicForm.favorite}
                    onCheckedChange={(checked) => setBasicField("favorite", Boolean(checked))}
                  />
                  즐겨찾기
                </label>

                <StepActions busy={busy} primaryLabel="공고문 등록으로" />
              </form>
            )}

            {step === 1 && (
              <div className="space-y-5">
                <Field label="공고문 등록 방식">
                  <Select
                    value={postingForm.sourceType}
                    onValueChange={(value) => setPostingField("sourceType", value as ApplicationSourceType)}
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {APPLICATION_SOURCE_OPTIONS.map((option) => (
                        <SelectItem key={option.value} value={option.value}>
                          {option.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </Field>

                {(postingForm.sourceType === "TEXT" || postingForm.sourceType === "MANUAL") && (
                  <Field label="공고문 내용">
                    <Textarea
                      value={postingForm.text}
                      onChange={(event) => setPostingField("text", event.target.value)}
                      className="min-h-56"
                    />
                  </Field>
                )}

                {postingForm.sourceType === "URL" && (
                  <Field label="공고 URL" htmlFor="postingUrl">
                    <Input
                      id="postingUrl"
                      value={postingForm.url}
                      onChange={(event) => setPostingField("url", event.target.value)}
                      placeholder="https://..."
                    />
                  </Field>
                )}

                {(postingForm.sourceType === "PDF" || postingForm.sourceType === "IMAGE") && (
                  <Field label="공고 파일" htmlFor="postingFile">
                    <Input
                      id="postingFile"
                      type="file"
                      accept={postingForm.sourceType === "PDF" ? "application/pdf" : "image/*"}
                      onChange={(event) => setPostingField("file", event.target.files?.[0] ?? null)}
                    />
                  </Field>
                )}

                <StepActions
                  busy={busy}
                  primaryLabel="저장하고 추출 확인"
                  onPrimary={() => void handleSavePosting()}
                  onBack={() => setStep(0)}
                  primaryIcon={Upload}
                />
              </div>
            )}

            {step === 2 && (
              <div className="space-y-5">
                <Field label="추출/보정된 공고문">
                  <Textarea
                    value={confirmedText}
                    onChange={(event) => setConfirmedText(event.target.value)}
                    className="min-h-72"
                  />
                </Field>
                <StepActions
                  busy={busy}
                  primaryLabel="프로필 선택으로"
                  onPrimary={() => void handleConfirmPosting()}
                  onBack={() => setStep(1)}
                  primaryIcon={CheckCircle2}
                />
              </div>
            )}

            {step === 3 && (
              <div className="space-y-5">
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm leading-6 text-slate-600">
                  프로필 버전 선택 API는 아직 연결하지 않습니다. 현재 기본 프로필 기준으로 분석을 시작하며,
                  실제 프로필 버전 연동은 다음 작업 범위에서 처리합니다.
                </div>
                <StepActions
                  busy={busy}
                  primaryLabel="분석 설정으로"
                  onPrimary={() => setStep(4)}
                  onBack={() => setStep(2)}
                  primaryIcon={UserRound}
                />
              </div>
            )}

            {step === 4 && (
              <div className="space-y-5">
                <div className="grid gap-3 md:grid-cols-2">
                  <div className="rounded-lg border border-blue-200 bg-blue-50 p-4">
                    <div className="flex items-center gap-2 text-sm font-bold text-blue-900">
                      <Sparkles className="size-4" />
                      공고 분석
                    </div>
                    <p className="mt-2 text-sm leading-6 text-blue-800">필수 분석으로 항상 먼저 실행합니다.</p>
                  </div>
                  <label className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                    <div className="flex items-center gap-3">
                      <Checkbox
                        checked={includeCompanyAnalysis}
                        onCheckedChange={(checked) => setIncludeCompanyAnalysis(Boolean(checked))}
                      />
                      <span className="text-sm font-bold text-slate-900">기업 분석도 함께 실행</span>
                    </div>
                    <p className="mt-2 text-sm leading-6 text-slate-600">선택하면 공고 분석 완료 후 기업 분석을 이어서 실행합니다.</p>
                  </label>
                </div>
                <StepActions
                  busy={busy}
                  primaryLabel={includeCompanyAnalysis ? "공고/기업 분석 시작" : "공고 분석 시작"}
                  onPrimary={() => void handleStartAnalysis()}
                  onBack={() => setStep(3)}
                  primaryIcon={Sparkles}
                />
              </div>
            )}

            {createdCase && (
              <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600">
                생성된 지원 건: #{createdCase.id} {createdCase.companyName} / {createdCase.jobTitle}
              </div>
            )}

            {error && (
              <div className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700">
                {error}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function Field({
  label,
  htmlFor,
  children,
}: {
  label: string;
  htmlFor?: string;
  children: ReactNode;
}) {
  return (
    <label className="grid gap-2 text-sm font-semibold text-slate-700" htmlFor={htmlFor}>
      {label}
      {children}
    </label>
  );
}

function StepActions({
  busy,
  primaryLabel,
  onPrimary,
  onBack,
  primaryIcon: PrimaryIcon,
}: {
  busy: boolean;
  primaryLabel: string;
  onPrimary?: () => void;
  onBack?: () => void;
  primaryIcon?: typeof Briefcase;
}) {
  return (
    <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
      {onBack && (
        <Button type="button" variant="outline" onClick={onBack} disabled={busy}>
          이전
        </Button>
      )}
      <Button
        type={onPrimary ? "button" : "submit"}
        className="bg-blue-600 text-white hover:bg-blue-700"
        disabled={busy}
        onClick={onPrimary}
      >
        {busy ? <Loader2 className="size-4 animate-spin" /> : PrimaryIcon ? <PrimaryIcon className="size-4" /> : null}
        {primaryLabel}
      </Button>
    </div>
  );
}
