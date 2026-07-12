import { type ChangeEvent, type FormEvent, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Briefcase,
  Building2,
  CalendarDays,
  FileText,
  Loader2,
  RefreshCw,
  Save,
  SearchCheck,
  Sparkles,
  Star,
  Upload,
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
import {
  createApplicationCaseFromJobPosting,
  getApplicationCase,
  confirmApplicationCaseExtraction,
  getLatestApplicationCaseExtraction,
  retryApplicationCaseExtraction,
  reviewApplicationCaseExtraction,
  updateApplicationCase,
  uploadApplicationCaseFromJobPosting,
  type CreateApplicationCaseFromJobPostingResponse,
} from "../api/applicationCasesApi";
import { getJobPosting } from "../api/jobPostingsApi";
import { getModelOptions, type ModelOptions } from "../api/modelOptionsApi";
import { ApplicationExtractionBadge, getApplicationExtractionStatusLabel } from "../components/ApplicationExtractionBadge";
import { LoginRequiredState } from "../components/LoginRequiredState";
import { OcrRetryButton } from "../components/OcrRetryButton";
import { AUTO_PROVIDER, RegistrationModelSelect } from "../components/RegistrationModelSelect";
import type { ApplicationCase, ApplicationCaseExtraction, ApplicationSourceType } from "../types/applicationCase";
import {
  APPLICATION_SOURCE_OPTIONS,
  isApplicationCaseExtractionActive,
  isApplicationCaseExtractionReviewRequired,
} from "../types/applicationCase";
import type { JobPosting } from "../types/jobPosting";
import { registerApplicationCaseExtraction } from "../utils/applicationExtractionTracker";
import { JOB_POSTING_IMAGE_ACCEPT, validateJobPostingFile } from "../utils/jobPostingUpload";
import { useJobPostingUploadLimit } from "../hooks/useJobPostingUploadLimit";

type WizardStep = 0 | 1 | 2;
type FileSourceType = Extract<ApplicationSourceType, "PDF" | "IMAGE">;

interface BasicFormState {
  companyName: string;
  jobTitle: string;
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
  { label: "공고문 등록", icon: FileText },
  { label: "추출 확인 + 지원 건 정보 확인", icon: SearchCheck },
  { label: "분석 결과 확인", icon: Sparkles },
];

const EXTRACTION_POLL_INTERVAL_MS = 3000;

function displayPostingText(posting: JobPosting | null): string {
  return posting?.extractedText ?? posting?.originalText ?? "";
}

function isFileSource(sourceType: ApplicationSourceType): sourceType is FileSourceType {
  return sourceType === "PDF" || sourceType === "IMAGE";
}

function isTextSource(sourceType: ApplicationSourceType): boolean {
  return sourceType === "TEXT" || sourceType === "MANUAL";
}

function isHttpPostingUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

export function NewApplicationPage() {
  const navigate = useNavigate();
  const { loading: authLoading, isAuthenticated } = useAuth();
  const { maxBytes: uploadMaxBytes, label: uploadLimitLabel, loading: uploadLimitLoading } = useJobPostingUploadLimit();
  const [step, setStep] = useState<WizardStep>(0);
  const [createdCase, setCreatedCase] = useState<ApplicationCase | null>(null);
  const [jobPosting, setJobPosting] = useState<JobPosting | null>(null);
  const [extractionJob, setExtractionJob] = useState<ApplicationCaseExtraction | null>(null);
  const [confirmedText, setConfirmedText] = useState("");
  // 추출/검수 통과 시 백엔드 자동 파이프라인이 공고·기업 분석을 생성한다(단일 진실원).
  // 이 값은 결과 화면 이동 시 어느 탭으로 보낼지만 결정한다(분석 생성과 무관).
  const [landOnCompanyAnalysis, setLandOnCompanyAnalysis] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const appliedExtractionIdsRef = useRef<Set<number>>(new Set());
  const extractionPollInFlightRef = useRef(false);
  const [basicForm, setBasicForm] = useState<BasicFormState>({
    companyName: "",
    jobTitle: "",
    deadlineDate: "",
    favorite: false,
  });
  const [postingForm, setPostingForm] = useState<PostingFormState>({
    sourceType: "TEXT",
    text: "",
    url: "",
    file: null,
  });
  // 등록 시 초기 실행 모델 선택. AUTO = 미선택(백엔드 기본 체인). OCR 은 파일 공고(PDF/IMAGE)에서만 쓴다.
  const [jobAnalysisProvider, setJobAnalysisProvider] = useState(AUTO_PROVIDER);
  const [companyAnalysisProvider, setCompanyAnalysisProvider] = useState(AUTO_PROVIDER);
  const [ocrProvider, setOcrProvider] = useState(AUTO_PROVIDER);
  const [modelOptions, setModelOptions] = useState<ModelOptions | null>(null);
  const [modelOptionsLoading, setModelOptionsLoading] = useState(false);
  // 조회 실패(네트워크·서버 오류)와 "선택지가 없음"을 구분한다 — 실패면 안내 + 재시도 버튼을 보여준다.
  const [modelOptionsError, setModelOptionsError] = useState(false);
  const [modelOptionsReloadKey, setModelOptionsReloadKey] = useState(0);

  const activePostingText = useMemo(() => displayPostingText(jobPosting), [jobPosting]);
  const extractionActive = extractionJob ? isApplicationCaseExtractionActive(extractionJob.status) : false;
  const extractionReviewRequired = isApplicationCaseExtractionReviewRequired(extractionJob);
  const activeExtractionCaseId = extractionActive ? createdCase?.id ?? null : null;
  const activeExtractionId = extractionActive ? extractionJob?.id ?? null : null;

  const setBasicField = <Key extends keyof BasicFormState>(key: Key, value: BasicFormState[Key]) => {
    setBasicForm((current) => ({ ...current, [key]: value }));
  };

  const setPostingField = <Key extends keyof PostingFormState>(key: Key, value: PostingFormState[Key]) => {
    setPostingForm((current) => ({ ...current, [key]: value }));
  };

  const handlePostingSourceTypeChange = (nextSourceType: ApplicationSourceType) => {
    setPostingForm((current) => {
      if (current.sourceType === nextSourceType) return current;

      return {
        sourceType: nextSourceType,
        text: isTextSource(nextSourceType) && isTextSource(current.sourceType) ? current.text : "",
        url: "",
        file: null,
      };
    });
    // OCR provider 는 파일 공고 전용이라 등록 방식이 바뀌면 초기화한다(공고·기업 분석 선택은 유지).
    setOcrProvider(AUTO_PROVIDER);
    setError(null);
  };

  const handlePostingFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const nextFile = event.target.files?.[0] ?? null;
    if (!nextFile || !isFileSource(postingForm.sourceType)) {
      setPostingField("file", null);
      return;
    }

    const validationError = validateJobPostingFile(postingForm.sourceType, nextFile, uploadMaxBytes);
    if (validationError) {
      event.currentTarget.value = "";
      setPostingField("file", null);
      setError(validationError);
      return;
    }

    setError(null);
    setPostingField("file", nextFile);
  };

  const applyCaseAndPosting = useCallback((
    applicationCase: ApplicationCase,
    extractedPosting: JobPosting | null,
  ) => {
    setCreatedCase(applicationCase);
    setJobPosting(extractedPosting);
    setConfirmedText(displayPostingText(extractedPosting));
    setBasicForm({
      companyName: applicationCase.companyName || "",
      jobTitle: applicationCase.jobTitle || "",
      deadlineDate: applicationCase.deadlineDate ?? "",
      favorite: applicationCase.favorite,
    });
  }, []);

  const applyCompletedExtraction = useCallback(async (extraction: ApplicationCaseExtraction) => {
    if (!createdCase || appliedExtractionIdsRef.current.has(extraction.id)) return;

    const [latestCase, latestPosting] = await Promise.all([
      getApplicationCase(createdCase.id),
      getJobPosting(createdCase.id),
    ]);
    appliedExtractionIdsRef.current.add(extraction.id);
    applyCaseAndPosting(latestCase, latestPosting);
  }, [applyCaseAndPosting, createdCase]);

  const applyExtractionResponse = (response: CreateApplicationCaseFromJobPostingResponse) => {
    const { applicationCase, jobPosting: extractedPosting, metadata, extractionJob: nextExtractionJob } = response;
    setCreatedCase(applicationCase);
    setJobPosting(extractedPosting);
    setExtractionJob(nextExtractionJob);
    registerApplicationCaseExtraction(nextExtractionJob);
    setConfirmedText(nextExtractionJob.status === "SUCCEEDED" ? displayPostingText(extractedPosting) : "");
    setBasicForm({
      companyName: nextExtractionJob.status === "SUCCEEDED"
        ? metadata.companyName || applicationCase.companyName || ""
        : applicationCase.companyName || "",
      jobTitle: nextExtractionJob.status === "SUCCEEDED"
        ? metadata.jobTitle || applicationCase.jobTitle || ""
        : applicationCase.jobTitle || "",
      deadlineDate: nextExtractionJob.status === "SUCCEEDED"
        ? metadata.deadlineDate ?? applicationCase.deadlineDate ?? ""
        : applicationCase.deadlineDate ?? "",
      favorite: applicationCase.favorite,
    });
    setStep(1);
  };

  const handleExtractPosting = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      let response: CreateApplicationCaseFromJobPostingResponse;
      // AUTO(미선택)는 요청에서 빼서 백엔드 기본 체인을 쓰게 한다.
      const resolveProvider = (value: string) => (value === AUTO_PROVIDER ? undefined : value);
      const selectedJobProvider = resolveProvider(jobAnalysisProvider);
      const selectedCompanyProvider = resolveProvider(companyAnalysisProvider);

      if (isFileSource(postingForm.sourceType)) {
        if (!postingForm.file) {
          throw new Error("업로드할 파일을 선택하세요.");
        }
        const validationError = validateJobPostingFile(postingForm.sourceType, postingForm.file, uploadMaxBytes);
        if (validationError) {
          throw new Error(validationError);
        }
        response = await uploadApplicationCaseFromJobPosting(postingForm.file, postingForm.sourceType, {
          favorite: false,
          ocrProvider: resolveProvider(ocrProvider),
          jobAnalysisProvider: selectedJobProvider,
          companyAnalysisProvider: selectedCompanyProvider,
        });
      } else if (postingForm.sourceType === "URL") {
        const url = postingForm.url.trim();
        if (!url) {
          throw new Error("공고 URL을 입력하세요.");
        }
        if (!isHttpPostingUrl(url)) {
          throw new Error("공고 URL은 http:// 또는 https://로 시작해야 합니다.");
        }
        response = await createApplicationCaseFromJobPosting({
          uploadedFileUrl: url,
          sourceType: "URL",
          favorite: false,
          jobAnalysisProvider: selectedJobProvider,
          companyAnalysisProvider: selectedCompanyProvider,
        });
      } else {
        const text = postingForm.text.trim();
        if (!text) {
          throw new Error("공고문 내용을 입력하세요.");
        }
        response = await createApplicationCaseFromJobPosting({
          originalText: text,
          sourceType: postingForm.sourceType,
          favorite: false,
          jobAnalysisProvider: selectedJobProvider,
          companyAnalysisProvider: selectedCompanyProvider,
        });
      }

      applyExtractionResponse(response);
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고문을 추출하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  useEffect(() => {
    if (!activeExtractionCaseId || !activeExtractionId) return;

    let cancelled = false;
    const poll = async () => {
      if (extractionPollInFlightRef.current) return;
      extractionPollInFlightRef.current = true;
      try {
        const latest = await getLatestApplicationCaseExtraction(activeExtractionCaseId);
        if (cancelled || !latest) return;

        if (latest.status === "SUCCEEDED") {
          await applyCompletedExtraction(latest);
          if (!cancelled) {
            setExtractionJob(latest);
            setError(null);
          }
          return;
        }

        setExtractionJob(latest);
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : "공고문 추출 상태를 확인하지 못했습니다.");
        }
      } finally {
        extractionPollInFlightRef.current = false;
      }
    };

    void poll();
    const intervalId = window.setInterval(() => {
      void poll();
    }, EXTRACTION_POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [activeExtractionCaseId, activeExtractionId, applyCompletedExtraction]);

  // 등록 화면(step 0)에서 현재 등록 방식 기준으로 단계별 모델 선택지를 조회한다. sourceType 이 바뀌면
  // OCR 선택지가 달라지므로 다시 조회한다(공고·기업 분석 선택지는 sourceType 과 무관하지만 같은 응답에 온다).
  // 조회 실패해도 등록은 막지 않는다 — "자동"으로 진행할 수 있고, 실패는 안내 + 재시도 버튼으로 노출한다
  // (modelOptionsReloadKey 를 올리면 재조회). "선택지가 없음"(성공했으나 전부 불가)과 조회 실패를 구분한다.
  useEffect(() => {
    if (step !== 0 || !isAuthenticated) return;

    let cancelled = false;
    setModelOptionsLoading(true);
    setModelOptionsError(false);
    void getModelOptions(postingForm.sourceType)
      .then((options) => {
        if (!cancelled) setModelOptions(options);
      })
      .catch(() => {
        if (!cancelled) {
          setModelOptions(null);
          setModelOptionsError(true);
        }
      })
      .finally(() => {
        if (!cancelled) setModelOptionsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [step, isAuthenticated, postingForm.sourceType, modelOptionsReloadKey]);

  const saveConfirmation = async (): Promise<ApplicationCase> => {
    if (!createdCase || !jobPosting) {
      throw new Error("추출된 지원 건 정보가 없습니다.");
    }

    if (extractionJob && extractionJob.status !== "SUCCEEDED") {
      throw new Error("공고문 추출이 완료된 뒤 확인할 수 있습니다.");
    }

    const companyName = basicForm.companyName.trim();
    const jobTitle = basicForm.jobTitle.trim();
    const trimmedPostingText = confirmedText.trim();

    if (!companyName || !jobTitle) {
      throw new Error("기업명과 직무명을 입력하세요.");
    }
    if (!trimmedPostingText) {
      throw new Error("확인된 공고문 내용이 필요합니다.");
    }

    // 분석 실행의 단일 진실원은 백엔드 자동 파이프라인이다. 검수/수정 확정 API가 텍스트를 확정하고
    // OCR 재실행 없이 분석만 1회 갱신하므로, 프런트는 createJobAnalysis 등을 직접 호출하지 않는다.
    let nextPosting = jobPosting;
    if (isApplicationCaseExtractionReviewRequired(extractionJob)) {
      // 품질 게이트 REVIEW_REQUIRED → 검수 확정(review).
      const reviewed = await reviewApplicationCaseExtraction(createdCase.id, trimmedPostingText);
      setExtractionJob(reviewed);
      registerApplicationCaseExtraction(reviewed);
      const reviewedPosting = await getJobPosting(createdCase.id);
      if (reviewedPosting) {
        nextPosting = reviewedPosting;
        setJobPosting(reviewedPosting);
        setConfirmedText(displayPostingText(reviewedPosting));
      }
    } else if (trimmedPostingText !== activePostingText.trim()) {
      // PASS 상태에서 공고문 수정 → 수정 확정(confirm). TEXT/URL/PDF/IMAGE 모두 동일 경로.
      const confirmed = await confirmApplicationCaseExtraction(createdCase.id, trimmedPostingText);
      setExtractionJob(confirmed);
      registerApplicationCaseExtraction(confirmed);
      const confirmedPosting = await getJobPosting(createdCase.id);
      if (confirmedPosting) {
        nextPosting = confirmedPosting;
        setJobPosting(confirmedPosting);
        setConfirmedText(displayPostingText(confirmedPosting));
      }
    }

    const updated = await updateApplicationCase(createdCase.id, {
      companyName,
      jobTitle,
      deadlineDate: basicForm.deadlineDate || null,
      clearDeadlineDate: !basicForm.deadlineDate,
      sourceType: nextPosting.sourceType,
      favorite: basicForm.favorite,
    });
    setCreatedCase(updated);
    return updated;
  };

  const handleSaveAndExit = async () => {
    if (createdCase && extractionJob && extractionJob.status !== "SUCCEEDED") {
      navigate(`/applications/${createdCase.id}/overview`);
      return;
    }

    setBusy(true);
    setError(null);
    try {
      const updated = await saveConfirmation();
      navigate(`/applications/${updated.id}/overview`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건을 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleExitCreatedCase = () => {
    if (createdCase) {
      navigate(`/applications/${createdCase.id}/overview`);
    }
  };

  const handleRetryExtraction = async (ocrProvider: string) => {
    if (!createdCase) return;

    setBusy(true);
    setError(null);
    try {
      const nextExtraction = await retryApplicationCaseExtraction(createdCase.id, ocrProvider);
      setExtractionJob(nextExtraction);
      registerApplicationCaseExtraction(nextExtraction);
      setConfirmedText("");
      setStep(1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "공고문 추출을 다시 시작하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleMoveToAnalysisSettings = async () => {
    setBusy(true);
    setError(null);
    try {
      await saveConfirmation();
      setStep(2);
    } catch (err) {
      setError(err instanceof Error ? err.message : "지원 건을 저장하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  };

  const handleStartAnalysis = () => {
    if (!createdCase) return;
    // 분석은 추출 통과 시 백엔드 자동 파이프라인이 이미 생성한다. 프런트는 결과 화면으로 이동만 한다.
    navigate(`/applications/${createdCase.id}/${landOnCompanyAnalysis ? "company-analysis" : "job-analysis"}`);
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
          <p className="mt-1 text-sm text-slate-500">공고문을 먼저 추출한 뒤 지원 건 정보를 확인합니다.</p>
        </div>

        <div className="grid gap-2 sm:grid-cols-3">
          {steps.map((item, index) => (
            <div
              key={item.label}
              className={`rounded-lg border p-3 ${
                index === step
                  ? "border-blue-300 bg-blue-50 text-blue-800"
                  : index < step
                    ? "border-emerald-200 bg-emerald-50 text-emerald-800"
                    : "border-slate-200 bg-card text-slate-500"
              }`}
            >
              <div className="flex items-center gap-2 text-xs font-semibold">
                <item.icon className="size-4 shrink-0" />
                <span className="min-w-0 truncate">{index + 1}. {item.label}</span>
              </div>
            </div>
          ))}
        </div>

        <Card className="border-slate-200 bg-card">
          <CardHeader>
            <CardTitle className="text-lg font-bold text-slate-900">{steps[step].label}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            {step === 0 && (
              <form className="space-y-5" onSubmit={(event) => void handleExtractPosting(event)}>
                <div className="rounded-lg border border-blue-100 bg-blue-50 px-4 py-3 text-sm font-medium text-blue-800">
                  공고문 추출을 시작하면 새 지원 건이 생성됩니다. 파일·URL 추출은 공고 품질과 응답 상태에 따라 시간이 걸릴 수 있습니다.
                </div>
                {busy && (
                  <div className="flex items-start gap-2 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                    <Loader2 className="mt-0.5 size-4 shrink-0 animate-spin" />
                    <span>지원 건을 임시 분석중 상태로 만들고 공고문 추출을 준비하고 있습니다. 파일·이미지 추출은 몇 분 정도 걸릴 수 있으며, 완료되면 확인 단계로 이동합니다.</span>
                  </div>
                )}

                <Field label="공고문 등록 방식">
                  <Select
                    value={postingForm.sourceType}
                    onValueChange={(value) => handlePostingSourceTypeChange(value as ApplicationSourceType)}
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

                {isTextSource(postingForm.sourceType) && (
                  <Field label="공고문 내용">
                    <Textarea
                      value={postingForm.text}
                      onChange={(event) => setPostingField("text", event.target.value)}
                      className="min-h-72"
                      placeholder="채용 공고문 전체 내용을 붙여넣으세요."
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

                {isFileSource(postingForm.sourceType) && (
                  <Field label="공고 파일" htmlFor="postingFile">
                    <Input
                      key={postingForm.sourceType}
                      id="postingFile"
                      type="file"
                      accept={postingForm.sourceType === "PDF" ? "application/pdf" : JOB_POSTING_IMAGE_ACCEPT}
                      onChange={handlePostingFileChange}
                      disabled={uploadLimitLoading}
                    />
                    <span className="text-xs font-normal leading-5 text-slate-500">
                      {uploadLimitLoading
                        ? "업로드 한도를 확인하고 있습니다."
                        : uploadLimitLabel
                          ? `PDF와 이미지는 ${uploadLimitLabel} 이하만 업로드할 수 있습니다.`
                          : "파일 크기는 업로드 시 서버에서 확인합니다."}
                      {" "}이미지/스캔 PDF OCR은 완료까지 시간이 걸릴 수 있습니다.
                    </span>
                  </Field>
                )}

                <div className="grid gap-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
                  <div className="flex items-center gap-2 text-sm font-bold text-slate-800">
                    <Sparkles className="size-4 text-blue-600" />
                    분석 모델 선택 <span className="text-xs font-normal text-slate-500">(선택 사항 · 기본은 자동)</span>
                  </div>
                  <p className="text-xs leading-5 text-slate-500">
                    초기 공고·기업 분석에 쓸 모델을 고를 수 있습니다. 고른 모델을 먼저 시도하고, 실패하면 자동으로 다른 모델로 이어서 처리합니다. 그대로 두면 기본 추천 순서로 실행합니다.
                  </p>
                  {modelOptionsError && !modelOptionsLoading && (
                    <div className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                      <span>
                        모델 선택지를 불러오지 못했습니다. 그대로 &quot;자동&quot;으로 진행할 수 있고, 다시 시도할 수도 있습니다.
                      </span>
                      <button
                        type="button"
                        onClick={() => setModelOptionsReloadKey((key) => key + 1)}
                        disabled={busy}
                        className="rounded border border-amber-300 bg-card px-2 py-1 font-semibold text-amber-800 hover:bg-amber-100 disabled:opacity-50"
                      >
                        다시 시도
                      </button>
                    </div>
                  )}
                  <div className="grid gap-4 md:grid-cols-2">
                    {isFileSource(postingForm.sourceType) && (
                      <RegistrationModelSelect
                        label="공고문 추출(OCR) 모델"
                        hint="이미지·스캔 PDF에서 텍스트를 뽑는 모델입니다. 텍스트 PDF는 OCR 없이 처리됩니다."
                        stage={modelOptions?.ocr ?? null}
                        loading={modelOptionsLoading}
                        value={ocrProvider}
                        onChange={setOcrProvider}
                        disabled={busy}
                      />
                    )}
                    <RegistrationModelSelect
                      label="공고 분석 모델"
                      stage={modelOptions?.jobAnalysis ?? null}
                      loading={modelOptionsLoading}
                      value={jobAnalysisProvider}
                      onChange={setJobAnalysisProvider}
                      disabled={busy}
                    />
                    <RegistrationModelSelect
                      label="기업 분석 모델"
                      stage={modelOptions?.companyAnalysis ?? null}
                      loading={modelOptionsLoading}
                      value={companyAnalysisProvider}
                      onChange={setCompanyAnalysisProvider}
                      disabled={busy}
                    />
                  </div>
                </div>

                <StepActions
                  busy={busy}
                  primaryLabel={busy ? "지원 건 생성 및 추출 준비 중" : "공고문 추출 시작"}
                  onCancel={() => navigate("/applications")}
                  primaryIcon={Upload}
                />
              </form>
            )}

            {step === 1 && (
              <>
                {extractionJob && extractionActive ? (
                  <ExtractionProgressState
                    extraction={extractionJob}
                    busy={busy}
                    onExit={handleExitCreatedCase}
                  />
                ) : extractionJob?.status === "FAILED" ? (
                  <ExtractionFailureState
                    extraction={extractionJob}
                    busy={busy}
                    onRetry={(provider) => void handleRetryExtraction(provider)}
                    onExit={handleExitCreatedCase}
                  />
                ) : (
                  <div className="space-y-5">
                    {extractionJob && (
                      <div className="flex flex-wrap items-center gap-2">
                        <ApplicationExtractionBadge extraction={extractionJob} />
                        <span className="text-xs text-slate-500">최신 공고문과 지원 건 정보를 불러왔습니다.</span>
                      </div>
                    )}

                    {extractionReviewRequired && (
                      <div className="break-words rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-800">
                        추출 품질이 자동 분석 기준에 부족합니다. 아래 공고문을 확인·보정한 뒤 진행하면 검수 확정 후 분석을 이어갑니다.
                      </div>
                    )}

                    <Field label="공고문">
                      <Textarea
                        value={confirmedText}
                        onChange={(event) => setConfirmedText(event.target.value)}
                        className="min-h-72"
                      />
                    </Field>

                    <div className="grid gap-4 md:grid-cols-2">
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

                    <div className="max-w-md">
                      <Field label="마감일" htmlFor="deadlineDate">
                        <Input
                          id="deadlineDate"
                          type="date"
                          value={basicForm.deadlineDate}
                          onChange={(event) => setBasicField("deadlineDate", event.target.value)}
                        />
                        <span className="text-xs font-normal leading-5 text-slate-500">
                          마감일이 없거나 상시채용이면 비워두세요.
                        </span>
                      </Field>
                    </div>

                    <label className="flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
                      <Checkbox
                        checked={basicForm.favorite}
                        onCheckedChange={(checked) => setBasicField("favorite", Boolean(checked))}
                      />
                      즐겨찾기
                    </label>

                    <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
                      <Button
                        type="button"
                        variant="outline"
                        className="w-full sm:w-auto"
                        disabled={busy}
                        onClick={() => void handleSaveAndExit()}
                      >
                        {busy ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}
                        저장하고 나가기
                      </Button>
                      <Button
                        type="button"
                        className="w-full bg-blue-600 text-white hover:bg-blue-700 sm:w-auto"
                        disabled={busy}
                        onClick={() => void handleMoveToAnalysisSettings()}
                      >
                        {busy ? <Loader2 className="size-4 animate-spin" /> : <ArrowRight className="size-4" />}
                        {extractionReviewRequired ? "검수 확정 후 결과 확인" : "결과 확인으로"}
                      </Button>
                    </div>
                  </div>
                )}
              </>
            )}

            {step === 2 && (
              <div className="space-y-5">
                <div className="grid gap-3 md:grid-cols-2">
                  <div className="rounded-lg border border-blue-200 bg-blue-50 p-4">
                    <div className="flex items-center gap-2 text-sm font-bold text-blue-900">
                      <Sparkles className="size-4" />
                      공고 분석
                    </div>
                    <p className="mt-2 text-sm leading-6 text-blue-800">추출/검수가 끝나면 공고 분석이 자동으로 생성됩니다. 공고문을 수정하면 확정된 텍스트 기준으로 분석을 다시 갱신합니다.</p>
                  </div>
                  <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                    <div className="flex items-center gap-2 text-sm font-bold text-slate-900">
                      <Building2 className="size-4" />
                      기업 분석
                    </div>
                    <p className="mt-2 text-sm leading-6 text-slate-600">기업 분석도 공고 내 정보 기준으로 함께 생성됩니다. 결과 화면에서 탭으로 확인할 수 있습니다.</p>
                    <label className="mt-3 flex items-center gap-3 text-sm text-slate-700">
                      <Checkbox
                        checked={landOnCompanyAnalysis}
                        onCheckedChange={(checked) => setLandOnCompanyAnalysis(Boolean(checked))}
                      />
                      완료 후 기업 분석 화면으로 이동
                    </label>
                  </div>
                </div>
                <StepActions
                  busy={busy}
                  primaryLabel="분석 결과 보기"
                  onPrimary={() => void handleStartAnalysis()}
                  onBack={() => setStep(1)}
                  primaryIcon={Sparkles}
                />
              </div>
            )}

            {createdCase && (
              <div className="grid gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-600 sm:grid-cols-2 lg:grid-cols-4">
                <SummaryItem icon={Building2} label="기업" value={basicForm.companyName || createdCase.companyName || "미확인"} />
                <SummaryItem icon={Briefcase} label="직무" value={basicForm.jobTitle || createdCase.jobTitle || "미확인"} />
                <SummaryItem icon={CalendarDays} label="마감일" value={basicForm.deadlineDate || createdCase.deadlineDate || "마감일 없음/상시채용"} />
                <SummaryItem
                  icon={extractionJob ? SearchCheck : Star}
                  label={extractionJob ? "추출 상태" : "즐겨찾기"}
                  value={extractionJob ? getApplicationExtractionStatusLabel(extractionJob.status) : basicForm.favorite ? "설정" : "미설정"}
                />
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

function ExtractionProgressState({
  extraction,
  busy,
  onExit,
}: {
  extraction: ApplicationCaseExtraction;
  busy: boolean;
  onExit(): void;
}) {
  return (
    <div className="space-y-4 rounded-lg border border-blue-100 bg-blue-50 p-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <ApplicationExtractionBadge extraction={extraction} />
            <span className="text-sm font-bold text-blue-900">공고문을 분석 가능한 텍스트로 추출하고 있습니다.</span>
          </div>
          <p className="text-sm leading-6 text-blue-800">
            지원 건은 이미 생성됐고 완료 전까지 임시 분석중 상태로 보일 수 있습니다. 파일 크기와 이미지 품질에 따라 몇 분 정도 걸릴 수 있으며, 이 화면을 나가도 추출은 계속 진행됩니다.
          </p>
        </div>
        <Loader2 className="size-5 shrink-0 animate-spin text-blue-700" />
      </div>

      <div className="flex justify-end">
        <Button type="button" variant="outline" disabled={busy} onClick={onExit}>
          <Save className="size-4" />
          저장하고 상세로 이동
        </Button>
      </div>
    </div>
  );
}

function ExtractionFailureState({
  extraction,
  busy,
  onRetry,
  onExit,
}: {
  extraction: ApplicationCaseExtraction;
  busy: boolean;
  onRetry(ocrProvider: string): void;
  onExit(): void;
}) {
  return (
    <div className="space-y-4 rounded-lg border border-red-200 bg-red-50 p-5">
      <div className="flex items-start gap-3">
        <AlertCircle className="mt-0.5 size-5 shrink-0 text-red-600" />
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <ApplicationExtractionBadge extraction={extraction} />
            <span className="text-sm font-bold text-red-900">공고문 추출에 실패했습니다.</span>
          </div>
          <p className="mt-2 text-sm leading-6 text-red-700">
            {extraction.errorMessage || "일시적인 오류일 수 있습니다. 다시 시도하거나 생성된 지원 건 상세에서 이어서 처리할 수 있습니다."}
          </p>
        </div>
      </div>

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button type="button" variant="outline" disabled={busy} onClick={onExit}>
          상세로 이동
        </Button>
        <OcrRetryButton
          sourceType={extraction.sourceType}
          retrying={busy}
          onRetry={onRetry}
          size="default"
          variant="default"
          className="bg-red-600 text-white hover:bg-red-700"
        />
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
  onCancel,
  primaryIcon: PrimaryIcon,
}: {
  busy: boolean;
  primaryLabel: string;
  onPrimary?: () => void;
  onBack?: () => void;
  onCancel?: () => void;
  primaryIcon?: typeof Briefcase;
}) {
  return (
    <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
      {onCancel && (
        <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={onCancel} disabled={busy}>
          취소
        </Button>
      )}
      {onBack && (
        <Button type="button" variant="outline" className="w-full sm:w-auto" onClick={onBack} disabled={busy}>
          이전
        </Button>
      )}
      <Button
        type={onPrimary ? "button" : "submit"}
        className="w-full bg-blue-600 text-white hover:bg-blue-700 sm:w-auto"
        disabled={busy}
        onClick={onPrimary}
      >
        {busy ? <Loader2 className="size-4 animate-spin" /> : PrimaryIcon ? <PrimaryIcon className="size-4" /> : null}
        {primaryLabel}
      </Button>
    </div>
  );
}

function SummaryItem({
  icon: Icon,
  label,
  value,
}: {
  icon: typeof Briefcase;
  label: string;
  value: string;
}) {
  return (
    <div className="flex min-w-0 items-center gap-2">
      <Icon className="size-4 shrink-0 text-slate-500" />
      <span className="shrink-0 font-semibold text-slate-500">{label}</span>
      <span className="min-w-0 truncate text-slate-700">{value}</span>
    </div>
  );
}
