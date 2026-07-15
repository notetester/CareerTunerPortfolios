import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link, useBlocker } from "react-router";
import {
  Award,
  BriefcaseBusiness,
  CheckCircle2,
  FileText,
  GraduationCap,
  Layers3,
  Loader2,
  Plus,
  RefreshCw,
  Save,
  Sparkles,
  Trash2,
  Upload,
  UserRound,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { Input } from "@/app/components/ui/input";
import { Label } from "@/app/components/ui/label";
import { Textarea } from "@/app/components/ui/textarea";
import { useConsent } from "@/app/auth/ConsentContext";
import { ApiError } from "@/app/lib/api";
import { draftPickFromCounts } from "@/app/profile/profileDraftMerge";
import { registerNativeExitGuard } from "@/platform/nativeExitGuard";
import {
  mergeProfileSectionPatch,
  ProfileSectionConflictError,
  profileValueEquals,
  rebaseDraftAfterCommit,
} from "../lib/profileSectionMerge";
import {
  PROFILE_DOC_ACCEPT,
  deleteUnlinkedProfileFile,
  deleteProfilePortfolioFile,
  downloadProfilePortfolioFile,
  draftHasStructuredFields,
  getProfile,
  importProfileDocument,
  listProfilePortfolioFiles,
  pollProfileAnalyze,
  saveProfile,
  startProfileAnalyze,
  uploadProfileFile,
  uploadProfilePortfolioFile,
  type ProfileAnalyzeDraft,
  type ProfilePortfolioFile,
  type UserProfile,
} from "@/app/profile/profileApi";

type ProfilePatch = Partial<UserProfile>;
type ProfilePatchFactory = ProfilePatch | ((baseline: UserProfile) => ProfilePatch);

const UNSAVED_SECTION_MESSAGE = "저장하지 않은 프로필 변경사항이 있습니다. 이 페이지를 나가면 입력 내용이 사라집니다. 계속할까요?";
const ACTIVE_SECTION_OPERATION_MESSAGE = "프로필 저장 또는 문서 처리가 진행 중입니다. 지금 나가면 완료 여부를 확인하기 어렵습니다. 계속할까요?";

interface SectionOperationContext<T extends object> {
  baselineProfile: UserProfile;
  baselineDraft: T;
  requestStartDraft: T;
  commitProfile: (profile: UserProfile) => void;
  isCurrent: () => boolean;
}

interface SectionController<T extends object> {
  draft: T;
  setDraft: React.Dispatch<React.SetStateAction<T>>;
  profile: UserProfile | null;
  loading: boolean;
  saving: boolean;
  isDirty: boolean;
  writeConflict: boolean;
  error: string | null;
  message: string | null;
  reload: () => Promise<void>;
  save: () => Promise<boolean>;
  persistPatch: (
    patch: ProfilePatchFactory,
    successMessage: string,
    conflictBaseline?: UserProfile,
  ) => Promise<boolean>;
  runExclusive: <R>(
    task: (context: SectionOperationContext<T>) => Promise<R>,
    fallbackMessage: string,
  ) => Promise<R | undefined>;
  setError: React.Dispatch<React.SetStateAction<string | null>>;
  setMessage: React.Dispatch<React.SetStateAction<string | null>>;
}

function useProfileSection<T extends object>(
  initial: T,
  read: (profile: UserProfile) => T,
  write: (draft: T) => ProfilePatch,
  validate?: (draft: T) => string | null,
): SectionController<T> {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [draft, setDraft] = useState<T>(initial);
  const [baselineDraft, setBaselineDraft] = useState<T>(initial);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [writeConflict, setWriteConflict] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const profileRef = useRef<UserProfile | null>(null);
  const baselineDraftRef = useRef<T>(initial);
  const draftRef = useRef<T>(initial);
  const dirtyRef = useRef(false);
  const mountedRef = useRef(true);
  const loadEpochRef = useRef(0);
  const operationEpochRef = useRef(0);
  const operationInFlightRef = useRef(false);

  const updateDraft = useCallback<React.Dispatch<React.SetStateAction<T>>>((action) => {
    setDraft((current) => {
      const next = typeof action === "function"
        ? (action as (value: T) => T)(current)
        : action;
      draftRef.current = next;
      dirtyRef.current = !profileValueEquals(next, baselineDraftRef.current);
      return next;
    });
  }, []);

  const isDirty = useMemo(
    () => !profileValueEquals(draft, baselineDraft),
    [baselineDraft, draft],
  );
  dirtyRef.current = isDirty;
  const guardActive = isDirty || saving;
  const leaveMessage = saving ? ACTIVE_SECTION_OPERATION_MESSAGE : UNSAVED_SECTION_MESSAGE;

  const applyLoadedProfile = useCallback((next: UserProfile) => {
    const nextDraft = read(next);
    profileRef.current = next;
    baselineDraftRef.current = nextDraft;
    draftRef.current = nextDraft;
    dirtyRef.current = false;
    setProfile(next);
    setBaselineDraft(nextDraft);
    setDraft(nextDraft);
    setWriteConflict(false);
  }, [read]);

  const applyCommittedProfile = useCallback((next: UserProfile, requestStartDraft: T) => {
    const committedDraft = read(next);
    const rebaseResult = rebaseDraftAfterCommit(requestStartDraft, draftRef.current, committedDraft);
    const rebasedDraft = rebaseResult.draft;
    profileRef.current = next;
    baselineDraftRef.current = committedDraft;
    draftRef.current = rebasedDraft;
    dirtyRef.current = !profileValueEquals(rebasedDraft, committedDraft);
    setProfile(next);
    setBaselineDraft(committedDraft);
    setDraft(rebasedDraft);
    setWriteConflict(rebaseResult.conflicts.length > 0);
    if (rebaseResult.conflicts.length) {
      setError(`요청이 처리되는 동안 같은 입력 항목이 다른 곳에서도 변경됐습니다 (${rebaseResult.conflicts.join(", ")}). 현재 입력은 보존했으며, 다시 불러오기 전까지 후속 저장을 중단합니다.`);
    }
  }, [read]);

  const performReload = useCallback(async (force: boolean) => {
    if (operationInFlightRef.current) {
      setError("저장이나 문서 처리가 끝난 뒤 다시 불러와 주세요.");
      return;
    }
    if (!force && dirtyRef.current && !window.confirm(UNSAVED_SECTION_MESSAGE)) return;
    const epoch = ++loadEpochRef.current;
    setLoading(true);
    setError(null);
    setMessage(null);
    try {
      const next = await getProfile();
      if (!mountedRef.current || epoch !== loadEpochRef.current) return;
      applyLoadedProfile(next);
    } catch (requestError) {
      if (!mountedRef.current || epoch !== loadEpochRef.current) return;
      setError(requestError instanceof Error ? requestError.message : "프로필을 불러오지 못했습니다.");
    } finally {
      if (mountedRef.current && epoch === loadEpochRef.current) setLoading(false);
    }
  }, [applyLoadedProfile]);

  const reload = useCallback(async () => {
    await performReload(false);
  }, [performReload]);

  useEffect(() => {
    mountedRef.current = true;
    void performReload(true);
    return () => {
      mountedRef.current = false;
      loadEpochRef.current += 1;
      operationEpochRef.current += 1;
    };
  }, [performReload]);

  useEffect(() => {
    if (!guardActive) return;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [guardActive]);

  useEffect(() => {
    if (!guardActive) return;
    return registerNativeExitGuard(() => window.confirm(leaveMessage));
  }, [guardActive, leaveMessage]);

  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    guardActive && currentLocation.pathname !== nextLocation.pathname,
  );

  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm(leaveMessage)) blocker.proceed();
    else blocker.reset();
  }, [blocker, leaveMessage]);

  const reportWriteError = useCallback((requestError: unknown, fallback: string) => {
    const conflict = requestError instanceof ProfileSectionConflictError
      || (requestError instanceof ApiError && requestError.status === 409);
    if (conflict) {
      setWriteConflict(true);
      const fields = requestError instanceof ProfileSectionConflictError && requestError.fields.length
        ? ` (${requestError.fields.join(", ")})`
        : "";
      setError(`다른 화면에서 같은 프로필 항목을 먼저 변경했습니다${fields}. 현재 입력은 보존했습니다. 다시 불러온 뒤 최신 내용과 합쳐 저장해 주세요.`);
      return;
    }
    setError(requestError instanceof Error ? requestError.message : fallback);
  }, []);

  const beginOperation = useCallback(() => {
    const baselineProfile = profileRef.current;
    if (!baselineProfile || loading || operationInFlightRef.current || writeConflict) return null;
    operationInFlightRef.current = true;
    const epoch = ++operationEpochRef.current;
    setSaving(true);
    setError(null);
    setMessage(null);
    return {
      epoch,
      baselineProfile,
      baselineDraft: baselineDraftRef.current,
      requestStartDraft: draftRef.current,
    };
  }, [loading, writeConflict]);

  const finishOperation = useCallback((epoch: number) => {
    operationInFlightRef.current = false;
    if (mountedRef.current && epoch === operationEpochRef.current) setSaving(false);
  }, []);

  const save = useCallback(async () => {
    if (!profileRef.current || loading) {
      setError("프로필을 정상적으로 불러온 뒤 저장해 주세요.");
      return false;
    }
    if (operationInFlightRef.current) {
      setError("진행 중인 저장이나 문서 처리가 끝난 뒤 다시 시도해 주세요.");
      return false;
    }
    if (!dirtyRef.current) {
      setError(null);
      setMessage("저장할 변경사항이 없습니다.");
      return true;
    }
    const operation = beginOperation();
    if (!operation) {
      setError(writeConflict
        ? "충돌한 프로필을 다시 불러온 뒤 저장해 주세요."
        : "프로필을 불러오거나 진행 중인 작업을 마친 뒤 저장해 주세요.");
      return false;
    }
    const validationError = validate?.(operation.requestStartDraft) ?? null;
    if (validationError) {
      setError(validationError);
      setMessage(null);
      finishOperation(operation.epoch);
      return false;
    }
    try {
      const latest = await getProfile();
      // API가 JSON column을 문자열/객체 중 어느 형태로 돌려주더라도 section read/write 계약으로
      // 양쪽 기준값을 같은 표현으로 정규화한 뒤 실제 사용자 변경만 비교한다.
      const normalizedBaseline = {
        ...operation.baselineProfile,
        ...write(operation.baselineDraft),
      };
      const normalizedLatest = {
        ...latest,
        ...write(read(latest)),
      };
      const request = mergeProfileSectionPatch(
        normalizedBaseline,
        normalizedLatest,
        write(operation.requestStartDraft),
      );
      const saved = await saveProfile(request);
      if (!mountedRef.current || operation.epoch !== operationEpochRef.current) return false;
      applyCommittedProfile(saved, operation.requestStartDraft);
      setMessage("변경사항을 저장했습니다.");
      return true;
    } catch (requestError) {
      if (mountedRef.current && operation.epoch === operationEpochRef.current) {
        reportWriteError(requestError, "프로필 저장에 실패했습니다.");
      }
      return false;
    } finally {
      finishOperation(operation.epoch);
    }
  }, [applyCommittedProfile, beginOperation, finishOperation, loading, read, reportWriteError, validate, write, writeConflict]);

  const persistPatch = useCallback(async (
    patch: ProfilePatchFactory,
    successMessage: string,
    conflictBaseline?: UserProfile,
  ) => {
    const operation = beginOperation();
    if (!operation) {
      setError("프로필을 다시 불러오거나 진행 중인 작업을 마친 뒤 적용해 주세요.");
      return false;
    }
    try {
      const latest = await getProfile();
      // 구조화 결과의 의도값도 분석 시작 시 section 기준본에서 만들고, 선택 field가
      // 다른 화면에서 바뀌었다면 일반 저장과 똑같이 충돌로 닫는다.
      const patchBaseline = conflictBaseline ?? operation.baselineProfile;
      const ownedPatch = typeof patch === "function" ? patch(patchBaseline) : patch;
      const saved = await saveProfile(mergeProfileSectionPatch(patchBaseline, latest, ownedPatch));
      if (!mountedRef.current || operation.epoch !== operationEpochRef.current) return false;
      // 이 저장은 현재 section draft가 아니라 구조화된 다른 profile field를 반영한다.
      // 따라서 요청 전부터 존재한 현재 section의 미저장 입력도 그대로 보존한다.
      applyCommittedProfile(saved, operation.baselineDraft);
      setMessage(successMessage);
      return true;
    } catch (requestError) {
      if (mountedRef.current && operation.epoch === operationEpochRef.current) {
        reportWriteError(requestError, "프로필 저장에 실패했습니다.");
      }
      return false;
    } finally {
      finishOperation(operation.epoch);
    }
  }, [applyCommittedProfile, beginOperation, finishOperation, reportWriteError]);

  const runExclusive = useCallback(async <R,>(
    task: (context: SectionOperationContext<T>) => Promise<R>,
    fallbackMessage: string,
  ): Promise<R | undefined> => {
    const operation = beginOperation();
    if (!operation) {
      setError("프로필을 다시 불러오거나 진행 중인 작업을 마친 뒤 시도해 주세요.");
      return undefined;
    }
    const context: SectionOperationContext<T> = {
      baselineProfile: operation.baselineProfile,
      baselineDraft: operation.baselineDraft,
      requestStartDraft: operation.requestStartDraft,
      commitProfile: (next) => {
        if (mountedRef.current && operation.epoch === operationEpochRef.current) {
          applyCommittedProfile(next, operation.requestStartDraft);
        }
      },
      isCurrent: () => mountedRef.current && operation.epoch === operationEpochRef.current,
    };
    try {
      return await task(context);
    } catch (requestError) {
      if (context.isCurrent()) reportWriteError(requestError, fallbackMessage);
      return undefined;
    } finally {
      finishOperation(operation.epoch);
    }
  }, [applyCommittedProfile, beginOperation, finishOperation, reportWriteError]);

  return {
    draft,
    setDraft: updateDraft,
    profile,
    loading,
    saving,
    isDirty,
    writeConflict,
    error,
    message,
    reload,
    save,
    persistPatch,
    runExclusive,
    setError,
    setMessage,
  };
}

const profileDestinations = [
  { title: "기본 정보", description: "희망 직무·산업과 근무 조건", href: "/profile/basic", icon: UserRound, read: basicRead },
  { title: "이력서 관리", description: "이력서 원문 작성과 문서 가져오기", href: "/profile/resume", icon: FileText, read: resumeRead },
  { title: "자기소개서 관리", description: "자기소개서 원문 작성과 분량 점검", href: "/profile/self-introduction", icon: Sparkles, read: selfIntroductionRead },
  { title: "경력·프로젝트 관리", description: "경력, 프로젝트와 포트폴리오 링크", href: "/profile/experience", icon: BriefcaseBusiness, read: experienceRead },
  { title: "기술스택 관리", description: "분석에 사용할 기술·역량 키워드", href: "/profile/skills", icon: Layers3, read: skillsRead },
  { title: "자격증·학력 관리", description: "학력, 자격증과 어학 정보", href: "/profile/credentials", icon: GraduationCap, read: credentialsRead },
] as const;

/** 내 프로필 대분류 전용 허브. 편집 폼과 분리해 현재 입력 상태와 각 기능 진입점을 제공한다. */
export function ProfileHubPage() {
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setProfile(await getProfile());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "프로필을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const completion = useMemo(() => {
    if (!profile) return 0;
    const completed = profileDestinations.filter((item) => sectionHasValue(item.read(profile))).length;
    return Math.round((completed / profileDestinations.length) * 100);
  }, [profile]);

  return (
    <PageCanvas>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <PageHeading icon={<UserRound className="size-6" />} title="내 프로필" description="지원 분석과 면접 질문에 사용되는 프로필을 영역별로 점검하고 관리합니다." />
        <div className="flex flex-wrap gap-2">
          <Button asChild><Link to="/profile/ai-analysis"><Sparkles className="size-4" />AI 프로필 분석</Link></Button>
          <Button asChild variant="outline"><Link to="/profile/detail">계정·닉네임 정보</Link></Button>
          <Button variant="outline" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} /> 새로고침
          </Button>
        </div>
      </div>

      {error && <Notice tone="error">{error}</Notice>}

      <Card className="border-border bg-card">
        <CardContent className="grid gap-5 pt-6 lg:grid-cols-[1fr_auto] lg:items-center">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-sm font-semibold text-foreground">프로필 입력 현황</span>
              {profile?.versionNo != null && <Badge variant="secondary">버전 {profile.versionNo}</Badge>}
            </div>
            <div className="mt-3 h-2 overflow-hidden rounded-full bg-muted">
              <div className="h-full rounded-full bg-primary transition-all" style={{ width: `${completion}%` }} />
            </div>
            <p className="mt-2 text-sm text-muted-foreground">
              {loading ? "프로필을 확인하는 중입니다." : `${completion}% 입력됨 · 필요한 영역부터 바로 이어서 작성할 수 있습니다.`}
            </p>
          </div>
          <div className="text-left lg:text-right">
            <div className="text-3xl font-black text-foreground">{loading ? "—" : `${completion}%`}</div>
            {profile?.updatedAt && <div className="mt-1 text-xs text-muted-foreground">최근 저장 {formatDateTime(profile.updatedAt)}</div>}
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {profileDestinations.map((item) => {
          const complete = profile ? sectionHasValue(item.read(profile)) : false;
          return (
            <Link key={item.href} to={item.href} className="group rounded-xl border border-border bg-card p-5 shadow-[var(--shadow-card)] transition hover:-translate-y-0.5 hover:border-primary/50">
              <div className="flex items-start justify-between gap-3">
                <span className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary"><item.icon className="size-5" /></span>
                <Badge variant={complete ? "default" : "secondary"}>{complete ? "입력됨" : "보완 필요"}</Badge>
              </div>
              <h2 className="mt-5 font-bold text-foreground group-hover:text-primary">{item.title}</h2>
              <p className="mt-1 text-sm text-muted-foreground">{item.description}</p>
            </Link>
          );
        })}
      </div>
    </PageCanvas>
  );
}

interface BasicDraft {
  desiredJob: string;
  desiredIndustry: string;
  preferences: { region: string; workType: string; salary: string; employmentType: string };
}

const emptyBasic: BasicDraft = { desiredJob: "", desiredIndustry: "", preferences: { region: "", workType: "", salary: "", employmentType: "" } };
function basicRead(profile: UserProfile): BasicDraft {
  const preferences = asRecord(profile.preferences);
  return {
    desiredJob: profile.desiredJob ?? "",
    desiredIndustry: profile.desiredIndustry ?? "",
    preferences: {
      region: asText(preferences.region), workType: asText(preferences.workType), salary: asText(preferences.salary), employmentType: asText(preferences.employmentType),
    },
  };
}
const basicWrite = (draft: BasicDraft): ProfilePatch => ({
  desiredJob: blankToNull(draft.desiredJob), desiredIndustry: blankToNull(draft.desiredIndustry),
  preferences: Object.fromEntries(Object.entries(draft.preferences).filter(([, value]) => value.trim())),
});
const validateBasic = (draft: BasicDraft) => !draft.desiredJob.trim() ? "희망 직무를 입력해 주세요." : draft.desiredJob.length > 80 || draft.desiredIndustry.length > 80 ? "희망 직무와 산업은 80자 이하로 입력해 주세요." : null;

export function ProfileBasicPage() {
  const state = useProfileSection(emptyBasic, basicRead, basicWrite, validateBasic);
  const setPreference = (key: keyof BasicDraft["preferences"], value: string) => state.setDraft((current) => ({ ...current, preferences: { ...current.preferences, [key]: value } }));
  return (
    <SectionPage state={state} icon={<UserRound className="size-6" />} title="기본 정보" description="희망 직무와 근무 조건을 관리합니다. 이 정보는 적합도 분석의 기준값으로 사용됩니다.">
      <CardGrid>
        <Field label="희망 직무" required><Input value={state.draft.desiredJob} onChange={(event) => state.setDraft((current) => ({ ...current, desiredJob: event.target.value }))} placeholder="예: 프론트엔드 개발자" maxLength={80} /></Field>
        <Field label="희망 산업"><Input value={state.draft.desiredIndustry} onChange={(event) => state.setDraft((current) => ({ ...current, desiredIndustry: event.target.value }))} placeholder="예: IT 서비스" maxLength={80} /></Field>
        <Field label="희망 지역"><Input value={state.draft.preferences.region} onChange={(event) => setPreference("region", event.target.value)} placeholder="예: 서울·경기" /></Field>
        <Field label="근무 방식"><Input value={state.draft.preferences.workType} onChange={(event) => setPreference("workType", event.target.value)} placeholder="예: 출근, 하이브리드, 원격" /></Field>
        <Field label="고용 형태"><Input value={state.draft.preferences.employmentType} onChange={(event) => setPreference("employmentType", event.target.value)} placeholder="예: 정규직" /></Field>
        <Field label="희망 연봉"><Input value={state.draft.preferences.salary} onChange={(event) => setPreference("salary", event.target.value)} placeholder="예: 4,000만원 이상" /></Field>
      </CardGrid>
    </SectionPage>
  );
}

interface TextDraft { text: string }
const emptyText: TextDraft = { text: "" };
function resumeRead(profile: UserProfile): TextDraft { return { text: profile.resumeText ?? "" }; }
const resumeWrite = (draft: TextDraft): ProfilePatch => ({ resumeText: blankToNull(draft.text) });
const validateResume = (draft: TextDraft) => draft.text.length > 20_000 ? "이력서 원문은 20,000자 이하로 입력해 주세요." : null;

export function ProfileResumePage() {
  const state = useProfileSection(emptyText, resumeRead, resumeWrite, validateResume);
  const { status: consent } = useConsent();
  const resumeAnalysisAgreed = consent?.resumeAnalysisAgreed === true;
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [analysisStatus, setAnalysisStatus] = useState<"idle" | "running" | "done" | "failed">("idle");
  const [analysisDraft, setAnalysisDraft] = useState<ProfileAnalyzeDraft | null>(null);
  const [analysisError, setAnalysisError] = useState<string | null>(null);
  const [applyingDraft, setApplyingDraft] = useState(false);
  const analysisEpochRef = useRef(0);
  const analysisBaselineRef = useRef<UserProfile | null>(null);

  const importFile = async (file: File) => {
    if (!resumeAnalysisAgreed) {
      state.setError("이력서 분석 개인정보 수집·이용 동의가 필요합니다. 설정에서 동의한 뒤 파일을 가져와 주세요.");
      return;
    }
    if (state.draft.text.trim() && !window.confirm("기존 이력서 원문을 가져온 문서로 대체할까요?")) return;
    const analysisEpoch = ++analysisEpochRef.current;
    setUploading(true);
    setAnalysisDraft(null);
    analysisBaselineRef.current = null;
    setAnalysisError(null);
    setAnalysisStatus("idle");
    try {
      await state.runExclusive(async (context) => {
        let uploadedFileId: number | null = null;
        try {
          const uploaded = await uploadProfileFile(file, "RESUME");
          uploadedFileId = uploaded.id;
          const imported = await importProfileDocument(
            uploaded.id,
            "RESUME_TEXT",
            context.baselineProfile.versionNo ?? null,
          );
          if (!context.isCurrent() || analysisEpoch !== analysisEpochRef.current) return;
          context.commitProfile(imported.profile);
          // 구조화 초안이 어떤 프로필 snapshot을 기준으로 생성됐는지 적용 시점까지 고정한다.
          // 이후 다른 저장으로 profileRef가 전진해도 오래된 AI 결과가 최신 field를 덮지 않는다.
          analysisBaselineRef.current = imported.profile;
          const importMessage = imported.truncated
            ? "이력서 원문의 앞부분만 저장했습니다. 길이 제한으로 잘린 내용을 확인해 주세요."
            : "이력서 원문을 저장했습니다.";
          state.setMessage(`${importMessage} 학력·경력·기술 구조를 분석하는 중입니다.`);

          setAnalysisStatus("running");
          try {
            const started = await startProfileAnalyze(uploaded.id);
            if (!started.jobId) throw new Error("구조화 분석 작업을 시작하지 못했습니다.");
            const finished = await pollProfileAnalyze(started.jobId);
            if (!context.isCurrent() || analysisEpoch !== analysisEpochRef.current) return;
            if (finished.status === "DONE" && finished.draft) {
              setAnalysisDraft(finished.draft);
              setAnalysisStatus("done");
              state.setMessage(draftHasStructuredFields(finished.draft)
                ? `${importMessage} 추출 결과에서 저장할 항목을 선택해 주세요.`
                : `${importMessage} 추가로 추출된 구조 항목은 없습니다.`);
            } else {
              setAnalysisStatus("failed");
              setAnalysisError(finished.errorMessage || "구조화 분석에 실패했습니다. 원문은 정상적으로 저장됐습니다.");
              state.setMessage(importMessage);
            }
          } catch (analysisRequestError) {
            if (!context.isCurrent() || analysisEpoch !== analysisEpochRef.current) return;
            setAnalysisStatus("failed");
            setAnalysisError(analysisRequestError instanceof Error
              ? analysisRequestError.message
              : "구조화 분석에 실패했습니다. 원문은 정상적으로 저장됐습니다.");
            state.setMessage(importMessage);
          }
        } finally {
          if (uploadedFileId !== null) {
            try {
              await deleteUnlinkedProfileFile(uploadedFileId);
            } catch {
              if (context.isCurrent()) {
                state.setMessage((current) => `${current ?? "문서 처리는 완료했습니다."} 처리 원본은 즉시 지우지 못해 서버가 24시간 TTL로 정리합니다.`);
              }
            }
          }
        }
      }, "이력서 문서를 가져오지 못했습니다.");
    } finally {
      setUploading(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  const applyAnalysisDraft = async (selection: AnalyzeDraftSelection) => {
    if (!analysisDraft) return;
    const analysisBaseline = analysisBaselineRef.current;
    if (!analysisBaseline) {
      state.setError("구조화 분석의 기준 프로필을 확인할 수 없습니다. 문서를 다시 분석해 주세요.");
      return;
    }
    setApplyingDraft(true);
    const saved = await state.persistPatch((baseline) => {
      const patch: ProfilePatch = {};
      if (selection.education && Array.isArray(analysisDraft.education)) patch.education = analysisDraft.education;
      if (selection.career && Array.isArray(analysisDraft.career)) patch.career = analysisDraft.career;
      if (selection.projects && Array.isArray(analysisDraft.projects)) patch.projects = analysisDraft.projects;
      if (selection.skills && Array.isArray(analysisDraft.skills)) {
        patch.skills = unique([...asStringList(baseline.skills), ...analysisDraft.skills]);
      }
      if (selection.portfolioLinks && Array.isArray(analysisDraft.portfolioLinks)) {
        patch.portfolioLinks = unique([...asStringList(baseline.portfolioLinks), ...analysisDraft.portfolioLinks]);
      }
      return patch;
    }, "선택한 이력서 추출 결과를 프로필에 저장했습니다.", analysisBaseline);
    if (saved) {
      setAnalysisDraft(null);
      analysisBaselineRef.current = null;
      setAnalysisStatus("idle");
      setAnalysisError(null);
    }
    setApplyingDraft(false);
  };

  return (
    <SectionPage state={state} icon={<FileText className="size-6" />} title="이력서 관리" description="이력서 원문을 직접 작성하거나 문서에서 가져와 분석 기준으로 저장합니다."
      secondaryAction={<><input ref={inputRef} type="file" accept={PROFILE_DOC_ACCEPT} className="hidden" onChange={(event) => { const file = event.target.files?.[0]; event.target.value = ""; if (file) void importFile(file); }} /><Button variant="outline" onClick={() => inputRef.current?.click()} disabled={uploading || state.saving || state.writeConflict || !resumeAnalysisAgreed}>{uploading ? <Loader2 className="size-4 animate-spin" /> : <Upload className="size-4" />}{uploading ? "원문 저장·분석 중..." : "문서 가져오기·분석"}</Button></>}>
      {!resumeAnalysisAgreed && (
        <div className="flex flex-col gap-3 rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-800 dark:text-amber-200 sm:flex-row sm:items-center sm:justify-between">
          <span>문서 업로드와 구조화 분석에는 이력서 분석 개인정보 수집·이용 동의가 필요합니다. 직접 입력과 저장은 계속 사용할 수 있습니다.</span>
          <Button asChild variant="outline" size="sm"><Link to="/settings/privacy">동의 설정</Link></Button>
        </div>
      )}
      {(analysisStatus !== "idle" || analysisDraft || analysisError) && (
        <StructuredAnalysisCard
          status={analysisStatus}
          draft={analysisDraft}
          error={analysisError}
          applying={applyingDraft}
          onApply={(selection) => void applyAnalysisDraft(selection)}
          onDismiss={() => { setAnalysisDraft(null); analysisBaselineRef.current = null; setAnalysisStatus("idle"); setAnalysisError(null); }}
        />
      )}
      <Field label="이력서 원문" hint={`${state.draft.text.length.toLocaleString("ko-KR")} / 20,000자`}>
        <Textarea value={state.draft.text} onChange={(event) => state.setDraft({ text: event.target.value })} className="min-h-[520px] resize-y leading-7" placeholder="요약, 핵심 역량, 경력과 성과를 입력해 주세요." />
      </Field>
      <p className="text-xs text-muted-foreground">.pdf, .docx, .txt, .md 파일을 지원합니다(.doc 제외). 원문은 먼저 저장되며 구조화 결과는 선택한 항목만 별도로 저장합니다.</p>
    </SectionPage>
  );
}

function selfIntroductionRead(profile: UserProfile): TextDraft { return { text: profile.selfIntro ?? "" }; }
const selfIntroductionWrite = (draft: TextDraft): ProfilePatch => ({ selfIntro: blankToNull(draft.text) });
const validateSelfIntroduction = (draft: TextDraft) => draft.text.length > 12_000 ? "자기소개서는 12,000자 이하로 입력해 주세요." : null;

export function ProfileSelfIntroductionPage() {
  const state = useProfileSection(emptyText, selfIntroductionRead, selfIntroductionWrite, validateSelfIntroduction);
  const { status: consent } = useConsent();
  const resumeAnalysisAgreed = consent?.resumeAnalysisAgreed === true;
  const inputRef = useRef<HTMLInputElement>(null);
  const importEpochRef = useRef(0);
  const [importing, setImporting] = useState(false);

  const importFile = async (file: File) => {
    if (!resumeAnalysisAgreed) {
      state.setError("문서 원문 추출에는 이력서 분석 개인정보 수집·이용 동의가 필요합니다. 설정에서 동의한 뒤 가져와 주세요.");
      return;
    }
    if (state.draft.text.trim() && !window.confirm("기존 자기소개서 원문을 가져온 문서로 대체할까요?")) return;
    const importEpoch = ++importEpochRef.current;
    setImporting(true);
    try {
      await state.runExclusive(async (context) => {
        let uploadedFileId: number | null = null;
        try {
          const uploaded = await uploadProfileFile(file, "RESUME");
          uploadedFileId = uploaded.id;
          const imported = await importProfileDocument(
            uploaded.id,
            "SELF_INTRO",
            context.baselineProfile.versionNo ?? null,
          );
          if (!context.isCurrent() || importEpoch !== importEpochRef.current) return;
          context.commitProfile(imported.profile);
          state.setMessage(imported.truncated
            ? "자기소개서 앞부분만 저장했습니다. 길이 제한으로 잘린 내용을 확인해 주세요."
            : "자기소개서 원문을 문서에서 가져와 저장했습니다.");
        } finally {
          if (uploadedFileId !== null) {
            try {
              await deleteUnlinkedProfileFile(uploadedFileId);
            } catch {
              if (context.isCurrent()) {
                state.setMessage((current) => `${current ?? "자기소개서 처리는 완료했습니다."} 처리 원본은 즉시 지우지 못해 서버가 24시간 TTL로 정리합니다.`);
              }
            }
          }
        }
      }, "자기소개서 문서를 가져오지 못했습니다.");
    } finally {
      setImporting(false);
      if (inputRef.current) inputRef.current.value = "";
    }
  };

  return (
    <SectionPage
      state={state}
      icon={<Sparkles className="size-6" />}
      title="자기소개서 관리"
      description="지원동기와 경험, 강점이 담긴 자기소개서 원문을 독립적으로 관리합니다."
      secondaryAction={
        <>
          <input ref={inputRef} type="file" accept={PROFILE_DOC_ACCEPT} className="hidden" onChange={(event) => { const file = event.target.files?.[0]; event.target.value = ""; if (file) void importFile(file); }} />
          <Button variant="outline" onClick={() => inputRef.current?.click()} disabled={importing || state.saving || state.writeConflict || !resumeAnalysisAgreed}>
            {importing ? <Loader2 className="size-4 animate-spin" /> : <Upload className="size-4" />}{importing ? "문서 가져오는 중..." : "문서에서 가져오기"}
          </Button>
        </>
      }
    >
      {!resumeAnalysisAgreed && (
        <div className="flex flex-col gap-3 rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-800 dark:text-amber-200 sm:flex-row sm:items-center sm:justify-between">
          <span>문서 원문 추출에는 이력서 분석 개인정보 수집·이용 동의가 필요합니다. 직접 입력과 저장은 계속 사용할 수 있습니다.</span>
          <Button asChild variant="outline" size="sm"><Link to="/settings/privacy">동의 설정</Link></Button>
        </div>
      )}
      <Field label="자기소개서 원문" hint={`${state.draft.text.length.toLocaleString("ko-KR")} / 12,000자`}>
        <Textarea value={state.draft.text} onChange={(event) => state.setDraft({ text: event.target.value })} className="min-h-[520px] resize-y leading-7" placeholder="지원동기, 직무 관련 경험, 강점, 입사 후 포부를 입력해 주세요." />
      </Field>
    </SectionPage>
  );
}

interface CareerEntry { company: string; role: string; startDate: string; endDate: string; tasks: string; achievements: string }
interface ProjectEntry { title: string; type: string; role: string; startDate: string; endDate: string; description: string; result: string }
interface ExperienceDraft { career: CareerEntry[]; projects: ProjectEntry[]; portfolioLinks: string }
const newCareer = (): CareerEntry => ({ company: "", role: "", startDate: "", endDate: "", tasks: "", achievements: "" });
const newProject = (): ProjectEntry => ({ title: "", type: "", role: "", startDate: "", endDate: "", description: "", result: "" });
const emptyExperience: ExperienceDraft = { career: [newCareer()], projects: [newProject()], portfolioLinks: "" };
function experienceRead(profile: UserProfile): ExperienceDraft {
  return { career: asEntries(profile.career, newCareer), projects: asEntries(profile.projects, newProject), portfolioLinks: asStringList(profile.portfolioLinks).join("\n") };
}
const experienceWrite = (draft: ExperienceDraft): ProfilePatch => ({ career: cleanEntries(draft.career), projects: cleanEntries(draft.projects), portfolioLinks: lines(draft.portfolioLinks) });
const validateExperience = (draft: ExperienceDraft) => [...draft.career, ...draft.projects].some((entry) => entry.startDate && entry.endDate && entry.startDate > entry.endDate) ? "종료월은 시작월보다 빠를 수 없습니다." : null;

export function ProfileExperiencePage() {
  const state = useProfileSection(emptyExperience, experienceRead, experienceWrite, validateExperience);
  const portfolioInputRef = useRef<HTMLInputElement>(null);
  const [portfolioFiles, setPortfolioFiles] = useState<ProfilePortfolioFile[]>([]);
  const [portfolioLoading, setPortfolioLoading] = useState(true);
  const [portfolioUploading, setPortfolioUploading] = useState(false);
  const [portfolioDeletingId, setPortfolioDeletingId] = useState<number | null>(null);

  const loadPortfolioFiles = useCallback(async () => {
    setPortfolioLoading(true);
    try {
      setPortfolioFiles(await listProfilePortfolioFiles());
    } catch (requestError) {
      state.setError(requestError instanceof Error ? requestError.message : "포트폴리오 파일을 불러오지 못했습니다.");
    } finally {
      setPortfolioLoading(false);
    }
  }, [state.setError]);

  useEffect(() => {
    void loadPortfolioFiles();
  }, [loadPortfolioFiles]);

  const uploadPortfolio = async (file: File) => {
    setPortfolioUploading(true);
    state.setError(null);
    state.setMessage(null);
    try {
      const uploaded = await uploadProfilePortfolioFile(file);
      setPortfolioFiles((current) => [uploaded, ...current.filter((item) => item.id !== uploaded.id)]);
      state.setMessage("포트폴리오 파일을 프로필에 연결했습니다. 다음 프로필 분석부터 이 파일을 근거로 사용할 수 있습니다.");
    } catch (requestError) {
      state.setError(requestError instanceof Error ? requestError.message : "포트폴리오 업로드에 실패했습니다.");
    } finally {
      setPortfolioUploading(false);
      if (portfolioInputRef.current) portfolioInputRef.current.value = "";
    }
  };

  const deletePortfolio = async (file: ProfilePortfolioFile) => {
    if (!window.confirm(`'${file.originalName}' 파일을 프로필과 저장소에서 영구 삭제할까요?`)) return;
    setPortfolioDeletingId(file.id);
    state.setError(null);
    state.setMessage(null);
    try {
      await deleteProfilePortfolioFile(file.id);
      setPortfolioFiles((current) => current.filter((item) => item.id !== file.id));
      state.setMessage("포트폴리오 파일을 영구 삭제했습니다. 다음 분석부터 근거에서 제외됩니다.");
    } catch (requestError) {
      state.setError(requestError instanceof Error ? requestError.message : "포트폴리오 파일 삭제에 실패했습니다.");
    } finally {
      setPortfolioDeletingId(null);
    }
  };

  const downloadPortfolio = async (file: ProfilePortfolioFile) => {
    state.setError(null);
    try {
      await downloadProfilePortfolioFile(file);
    } catch (requestError) {
      state.setError(requestError instanceof Error ? requestError.message : "포트폴리오 파일을 내려받지 못했습니다.");
    }
  };

  return (
    <SectionPage state={state} icon={<BriefcaseBusiness className="size-6" />} title="경력·프로젝트 관리" description="회사 경력과 프로젝트의 역할·성과를 구분해 기록하고 포트폴리오 링크를 연결합니다.">
      <Card className="border-border">
        <CardHeader className="flex-row items-center justify-between gap-3">
          <div><CardTitle className="text-base">포트폴리오 파일</CardTitle><p className="mt-1 text-xs text-muted-foreground">문서와 이미지를 프로필에 연결해 AI 분석 근거로 관리합니다.</p></div>
          <div>
            <input ref={portfolioInputRef} type="file" className="hidden" accept=".txt,.md,.pdf,.docx,image/*" onChange={(event) => { const file = event.target.files?.[0]; event.target.value = ""; if (file) void uploadPortfolio(file); }} />
            <Button type="button" variant="outline" size="sm" onClick={() => portfolioInputRef.current?.click()} disabled={portfolioUploading}>
              {portfolioUploading ? <Loader2 className="size-4 animate-spin" /> : <Upload className="size-4" />}{portfolioUploading ? "업로드 중..." : "파일 추가"}
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {portfolioLoading ? (
            <div className="flex min-h-24 items-center justify-center gap-2 text-sm text-muted-foreground"><Loader2 className="size-4 animate-spin" />연결된 파일을 불러오는 중입니다.</div>
          ) : portfolioFiles.length ? (
            <div className="space-y-2">
              {portfolioFiles.map((file) => (
                <div key={file.id} className="flex items-center gap-2 rounded-lg border border-border bg-muted/20 p-2">
                  <button type="button" onClick={() => void downloadPortfolio(file)} className="flex min-w-0 flex-1 items-center gap-3 rounded-md px-2 py-1.5 text-left hover:bg-accent" aria-label={`${file.originalName} 다운로드`}>
                    <FileText className="size-4 shrink-0 text-primary" />
                    <span className="min-w-0 flex-1 truncate text-sm font-semibold text-foreground">{file.originalName}</span>
                    <span className="shrink-0 text-xs text-muted-foreground">{formatFileSize(file.sizeBytes)}</span>
                  </button>
                  <Button type="button" variant="ghost" size="sm" className="shrink-0 text-destructive" disabled={portfolioDeletingId != null} onClick={() => void deletePortfolio(file)} aria-label={`${file.originalName} 영구 삭제`}>
                    {portfolioDeletingId === file.id ? <Loader2 className="size-4 animate-spin" /> : <Trash2 className="size-4" />}삭제
                  </Button>
                </div>
              ))}
            </div>
          ) : (
            <div className="rounded-lg border border-dashed border-border py-8 text-center text-sm text-muted-foreground">연결된 포트폴리오 파일이 없습니다.</div>
          )}
        </CardContent>
      </Card>
      <EntrySection title="경력" onAdd={() => state.setDraft((current) => ({ ...current, career: [...current.career, newCareer()] }))}>
        {state.draft.career.map((entry, index) => <EntryCard key={index} title={`경력 ${index + 1}`} onRemove={() => state.setDraft((current) => ({ ...current, career: removeEntry(current.career, index, newCareer) }))}>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="회사"><Input value={entry.company} onChange={(e) => state.setDraft((c) => ({ ...c, career: patchEntry(c.career, index, "company", e.target.value) }))} /></Field>
            <Field label="직무·역할"><Input value={entry.role} onChange={(e) => state.setDraft((c) => ({ ...c, career: patchEntry(c.career, index, "role", e.target.value) }))} /></Field>
            <MonthRange start={entry.startDate} end={entry.endDate} onStart={(v) => state.setDraft((c) => ({ ...c, career: patchEntry(c.career, index, "startDate", v) }))} onEnd={(v) => state.setDraft((c) => ({ ...c, career: patchEntry(c.career, index, "endDate", v) }))} />
            <Field label="담당 업무" className="md:col-span-2"><Textarea value={entry.tasks} onChange={(e) => state.setDraft((c) => ({ ...c, career: patchEntry(c.career, index, "tasks", e.target.value) }))} className="min-h-24 resize-y" /></Field>
            <Field label="성과" className="md:col-span-2"><Textarea value={entry.achievements} onChange={(e) => state.setDraft((c) => ({ ...c, career: patchEntry(c.career, index, "achievements", e.target.value) }))} className="min-h-24 resize-y" /></Field>
          </div>
        </EntryCard>)}
      </EntrySection>
      <EntrySection title="프로젝트·경험" onAdd={() => state.setDraft((current) => ({ ...current, projects: [...current.projects, newProject()] }))}>
        {state.draft.projects.map((entry, index) => <EntryCard key={index} title={`프로젝트 ${index + 1}`} onRemove={() => state.setDraft((current) => ({ ...current, projects: removeEntry(current.projects, index, newProject) }))}>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="프로젝트명"><Input value={entry.title} onChange={(e) => state.setDraft((c) => ({ ...c, projects: patchEntry(c.projects, index, "title", e.target.value) }))} /></Field>
            <Field label="유형"><Input value={entry.type} onChange={(e) => state.setDraft((c) => ({ ...c, projects: patchEntry(c.projects, index, "type", e.target.value) }))} placeholder="예: 팀 프로젝트, 대외활동" /></Field>
            <Field label="담당 역할"><Input value={entry.role} onChange={(e) => state.setDraft((c) => ({ ...c, projects: patchEntry(c.projects, index, "role", e.target.value) }))} /></Field>
            <MonthRange start={entry.startDate} end={entry.endDate} onStart={(v) => state.setDraft((c) => ({ ...c, projects: patchEntry(c.projects, index, "startDate", v) }))} onEnd={(v) => state.setDraft((c) => ({ ...c, projects: patchEntry(c.projects, index, "endDate", v) }))} />
            <Field label="수행 내용" className="md:col-span-2"><Textarea value={entry.description} onChange={(e) => state.setDraft((c) => ({ ...c, projects: patchEntry(c.projects, index, "description", e.target.value) }))} className="min-h-24 resize-y" /></Field>
            <Field label="결과·성과" className="md:col-span-2"><Textarea value={entry.result} onChange={(e) => state.setDraft((c) => ({ ...c, projects: patchEntry(c.projects, index, "result", e.target.value) }))} className="min-h-24 resize-y" /></Field>
          </div>
        </EntryCard>)}
      </EntrySection>
      <Field label="포트폴리오 링크" hint="한 줄에 하나씩 입력"><Textarea value={state.draft.portfolioLinks} onChange={(e) => state.setDraft((c) => ({ ...c, portfolioLinks: e.target.value }))} className="min-h-32 resize-y" placeholder="https://..." /></Field>
    </SectionPage>
  );
}

interface SkillsDraft { values: string[] }
const emptySkills: SkillsDraft = { values: [] };
function skillsRead(profile: UserProfile): SkillsDraft { return { values: asStringList(profile.skills) }; }
const skillsWrite = (draft: SkillsDraft): ProfilePatch => ({ skills: unique(draft.values) });

export function ProfileSkillsPage() {
  const state = useProfileSection(emptySkills, skillsRead, skillsWrite);
  const [input, setInput] = useState("");
  const add = () => {
    const next = input.split(/[,\n]/).map((value) => value.trim()).filter(Boolean);
    if (!next.length) return;
    state.setDraft((current) => ({ values: unique([...current.values, ...next]) }));
    setInput("");
  };
  return (
    <SectionPage state={state} icon={<Layers3 className="size-6" />} title="기술스택 관리" description="보유 기술과 업무 역량을 키워드 단위로 관리합니다. 공고 비교와 역량 추출에 직접 사용됩니다.">
      <Card className="border-border">
        <CardHeader><CardTitle className="text-base">기술·역량 추가</CardTitle></CardHeader>
        <CardContent>
          <div className="flex flex-col gap-2 sm:flex-row"><Input value={input} onChange={(e) => setInput(e.target.value)} onKeyDown={(e) => { if (e.key === "Enter") { e.preventDefault(); add(); } }} placeholder="예: React, Java, 데이터 분석" /><Button type="button" onClick={add}><Plus className="size-4" />추가</Button></div>
          <div className="mt-5 flex min-h-32 flex-wrap content-start gap-2 rounded-lg border border-dashed border-border p-4">
            {state.draft.values.length ? state.draft.values.map((value) => <button key={value} type="button" onClick={() => state.setDraft((current) => ({ values: current.values.filter((item) => item !== value) }))} className="inline-flex items-center gap-1 rounded-full bg-primary/10 px-3 py-1.5 text-sm font-medium text-primary hover:bg-destructive/10 hover:text-destructive">{value}<Trash2 className="size-3.5" /></button>) : <p className="text-sm text-muted-foreground">등록된 기술이 없습니다. 위 입력창에서 기술을 추가해 주세요.</p>}
          </div>
        </CardContent>
      </Card>
    </SectionPage>
  );
}

interface EducationEntry { school: string; major: string; startDate: string; endDate: string; status: string }
interface CredentialsDraft { certificates: string; languages: string; education: EducationEntry[] }
const newEducation = (): EducationEntry => ({ school: "", major: "", startDate: "", endDate: "", status: "" });
const emptyCredentials: CredentialsDraft = { certificates: "", languages: "", education: [newEducation()] };
function credentialsRead(profile: UserProfile): CredentialsDraft { return { certificates: asStringList(profile.certificates).join("\n"), languages: asStringList(profile.languages).join("\n"), education: asEntries(profile.education, newEducation) }; }
const credentialsWrite = (draft: CredentialsDraft): ProfilePatch => ({ certificates: lines(draft.certificates), languages: lines(draft.languages), education: cleanEntries(draft.education) });
const validateCredentials = (draft: CredentialsDraft) => draft.education.some((entry) => entry.startDate && entry.endDate && entry.startDate > entry.endDate) ? "졸업월은 입학월보다 빠를 수 없습니다." : null;

export function ProfileCredentialsPage() {
  const state = useProfileSection(emptyCredentials, credentialsRead, credentialsWrite, validateCredentials);
  return (
    <SectionPage state={state} icon={<GraduationCap className="size-6" />} title="자격증·학력 관리" description="학력, 자격증과 어학 정보를 각각 관리해 프로필의 증빙 정보를 보강합니다.">
      <EntrySection title="학력" onAdd={() => state.setDraft((current) => ({ ...current, education: [...current.education, newEducation()] }))}>
        {state.draft.education.map((entry, index) => <EntryCard key={index} title={`학력 ${index + 1}`} onRemove={() => state.setDraft((current) => ({ ...current, education: removeEntry(current.education, index, newEducation) }))}>
          <div className="grid gap-4 md:grid-cols-2">
            <Field label="학교"><Input value={entry.school} onChange={(e) => state.setDraft((c) => ({ ...c, education: patchEntry(c.education, index, "school", e.target.value) }))} /></Field>
            <Field label="전공"><Input value={entry.major} onChange={(e) => state.setDraft((c) => ({ ...c, education: patchEntry(c.education, index, "major", e.target.value) }))} /></Field>
            <Field label="상태"><Input value={entry.status} onChange={(e) => state.setDraft((c) => ({ ...c, education: patchEntry(c.education, index, "status", e.target.value) }))} placeholder="예: 졸업, 재학" /></Field>
            <MonthRange start={entry.startDate} end={entry.endDate} startLabel="입학" endLabel="졸업" onStart={(v) => state.setDraft((c) => ({ ...c, education: patchEntry(c.education, index, "startDate", v) }))} onEnd={(v) => state.setDraft((c) => ({ ...c, education: patchEntry(c.education, index, "endDate", v) }))} />
          </div>
        </EntryCard>)}
      </EntrySection>
      <div className="grid gap-5 lg:grid-cols-2">
        <Field label="자격증" hint="한 줄에 하나씩 입력"><Textarea value={state.draft.certificates} onChange={(e) => state.setDraft((c) => ({ ...c, certificates: e.target.value }))} className="min-h-48 resize-y" placeholder="정보처리기사\nSQLD" /></Field>
        <Field label="어학" hint="한 줄에 하나씩 입력"><Textarea value={state.draft.languages} onChange={(e) => state.setDraft((c) => ({ ...c, languages: e.target.value }))} className="min-h-48 resize-y" placeholder="TOEIC 900\nOPIc IH" /></Field>
      </div>
    </SectionPage>
  );
}

interface AnalyzeDraftSelection {
  education: boolean;
  career: boolean;
  projects: boolean;
  skills: boolean;
  portfolioLinks: boolean;
}

function StructuredAnalysisCard({
  status,
  draft,
  error,
  applying,
  onApply,
  onDismiss,
}: {
  status: "idle" | "running" | "done" | "failed";
  draft: ProfileAnalyzeDraft | null;
  error: string | null;
  applying: boolean;
  onApply: (selection: AnalyzeDraftSelection) => void;
  onDismiss: () => void;
}) {
  const counts = {
    education: Array.isArray(draft?.education) ? draft.education.length : 0,
    career: Array.isArray(draft?.career) ? draft.career.length : 0,
    projects: Array.isArray(draft?.projects) ? draft.projects.length : 0,
    skills: Array.isArray(draft?.skills) ? draft.skills.length : 0,
    portfolioLinks: Array.isArray(draft?.portfolioLinks) ? draft.portfolioLinks.length : 0,
  };
  const [selection, setSelection] = useState<AnalyzeDraftSelection>(() => draftPickFromCounts(counts));
  const hasAny = Object.values(counts).some((count) => count > 0);

  useEffect(() => {
    setSelection(draftPickFromCounts(counts));
  }, [counts.education, counts.career, counts.projects, counts.skills, counts.portfolioLinks, draft]);

  const options: Array<{ key: keyof AnalyzeDraftSelection; label: string }> = [
    { key: "education", label: "학력" },
    { key: "career", label: "경력" },
    { key: "projects", label: "프로젝트" },
    { key: "skills", label: "기술" },
    { key: "portfolioLinks", label: "포트폴리오 링크" },
  ];

  return (
    <Card className="border-primary/30 bg-primary/5">
      <CardHeader className="flex-row items-start justify-between gap-3">
        <div><CardTitle className="flex items-center gap-2 text-base"><Sparkles className="size-4 text-primary" />이력서 구조화 분석</CardTitle><p className="mt-1 text-xs text-muted-foreground">원문은 이미 저장됐으며 구조화 항목은 선택 후에만 프로필에 저장됩니다.</p></div>
        <Button type="button" variant="ghost" size="sm" onClick={onDismiss} disabled={status === "running" || applying}>닫기</Button>
      </CardHeader>
      <CardContent className="space-y-4">
        {status === "running" && <div className="flex items-center gap-2 text-sm text-muted-foreground"><Loader2 className="size-4 animate-spin" />학력·경력·프로젝트·기술을 분석 중입니다. 원격 모델 준비 상태에 따라 수 분 걸릴 수 있습니다.</div>}
        {status === "failed" && <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-sm text-amber-800 dark:text-amber-200">{error || "구조화 분석에 실패했습니다. 원문은 정상적으로 저장됐습니다."}</div>}
        {status === "done" && !hasAny && <p className="text-sm text-muted-foreground">추출된 구조 항목이 없습니다. 표나 스캔 문서는 직접 입력이 필요할 수 있습니다.</p>}
        {status === "done" && hasAny && (
          <>
            <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-5">
              {options.filter((option) => counts[option.key] > 0).map((option) => (
                <label key={option.key} className="flex cursor-pointer items-center gap-2 rounded-lg border border-border bg-card px-3 py-2 text-sm text-foreground">
                  <input type="checkbox" checked={selection[option.key]} onChange={(event) => setSelection((current) => ({ ...current, [option.key]: event.target.checked }))} />
                  <span>{option.label} {counts[option.key]}{option.key === "skills" || option.key === "portfolioLinks" ? "개" : "건"}</span>
                </label>
              ))}
            </div>
            <Button type="button" onClick={() => onApply(selection)} disabled={applying || !Object.values(selection).some(Boolean)}>
              {applying ? <Loader2 className="size-4 animate-spin" /> : <Save className="size-4" />}{applying ? "저장 중..." : "선택한 구조 항목 저장"}
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function PageCanvas({ children }: { children: ReactNode }) {
  return <main className="min-h-[calc(100vh-72px)] bg-background"><div className="mx-auto w-full max-w-[1440px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">{children}</div></main>;
}

function PageHeading({ icon, title, description }: { icon: ReactNode; title: string; description: string }) {
  return <div><h1 className="flex items-center gap-2 text-2xl font-black text-foreground"><span className="text-primary">{icon}</span>{title}</h1><p className="mt-1 text-sm text-muted-foreground">{description}</p></div>;
}

function SectionPage<T extends object>({ state, icon, title, description, secondaryAction, children }: { state: SectionController<T>; icon: ReactNode; title: string; description: string; secondaryAction?: ReactNode; children: ReactNode }) {
  if (state.loading) return <PageCanvas><div className="flex min-h-80 items-center justify-center gap-2 text-sm text-muted-foreground"><Loader2 className="size-5 animate-spin" />프로필을 불러오는 중입니다.</div></PageCanvas>;
  if (!state.profile) {
    return <PageCanvas><PageHeading icon={icon} title={title} description={description} />{state.error && <Notice tone="error">{state.error}</Notice>}<Button variant="outline" onClick={() => void state.reload()}><RefreshCw className="size-4" />다시 불러오기</Button></PageCanvas>;
  }
  return (
    <PageCanvas>
      <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <PageHeading icon={icon} title={title} description={description} />
        <div className="flex flex-wrap gap-2">{secondaryAction}<Button variant="outline" onClick={() => void state.reload()} disabled={state.saving}><RefreshCw className="size-4" />다시 불러오기</Button><Button onClick={() => void state.save()} disabled={state.loading || state.saving || state.writeConflict || !state.isDirty}><Save className="size-4" />{state.saving ? "처리 중..." : "저장"}</Button></div>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground"><Link to="/profile" className="font-medium text-primary hover:underline">내 프로필 허브</Link><span>›</span><span>{title}</span>{state.profile?.versionNo != null && <Badge variant="secondary">버전 {state.profile.versionNo}</Badge>}</div>
      {state.error && <Notice tone="error">{state.error}</Notice>}{state.message && <Notice tone="success">{state.message}</Notice>}
      {state.isDirty && <Notice tone="warning">저장하지 않은 변경사항이 있습니다. 이동하거나 다시 불러오기 전에 저장해 주세요.</Notice>}
      {children}
    </PageCanvas>
  );
}

function Notice({ tone, children }: { tone: "error" | "success" | "warning"; children: ReactNode }) {
  const className = tone === "error"
    ? "border-destructive/30 bg-destructive/10 text-destructive"
    : tone === "warning"
      ? "border-amber-500/30 bg-amber-500/10 text-amber-800 dark:text-amber-200"
      : "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300";
  return <div className={`flex items-center gap-2 rounded-lg border px-4 py-3 text-sm ${className}`}>{tone === "success" && <CheckCircle2 className="size-4" />}{children}</div>;
}

function CardGrid({ children }: { children: ReactNode }) { return <Card className="border-border"><CardContent className="grid gap-5 pt-6 md:grid-cols-2">{children}</CardContent></Card>; }
function Field({ label, required, hint, className = "", children }: { label: string; required?: boolean; hint?: string; className?: string; children: ReactNode }) { return <div className={`space-y-2 ${className}`}><div className="flex items-center justify-between gap-2"><Label>{label}{required && <span className="ml-1 text-destructive">*</span>}</Label>{hint && <span className="text-xs text-muted-foreground">{hint}</span>}</div>{children}</div>; }

function EntrySection({ title, onAdd, children }: { title: string; onAdd: () => void; children: ReactNode }) { return <section className="space-y-3"><div className="flex items-center justify-between"><h2 className="text-lg font-bold text-foreground">{title}</h2><Button type="button" variant="outline" size="sm" onClick={onAdd}><Plus className="size-4" />추가</Button></div>{children}</section>; }
function EntryCard({ title, onRemove, children }: { title: string; onRemove: () => void; children: ReactNode }) { return <Card className="border-border"><CardHeader className="flex-row items-center justify-between"><CardTitle className="text-base">{title}</CardTitle><Button type="button" variant="ghost" size="sm" onClick={onRemove} className="text-destructive"><Trash2 className="size-4" />삭제</Button></CardHeader><CardContent>{children}</CardContent></Card>; }
function MonthRange({ start, end, onStart, onEnd, startLabel = "시작", endLabel = "종료" }: { start: string; end: string; onStart: (value: string) => void; onEnd: (value: string) => void; startLabel?: string; endLabel?: string }) { return <div className="grid grid-cols-2 gap-2"><Field label={startLabel}><Input type="month" value={start} onChange={(e) => onStart(e.target.value)} /></Field><Field label={endLabel}><Input type="month" value={end} onChange={(e) => onEnd(e.target.value)} /></Field></div>; }

function parseUnknown(value: unknown): unknown { if (typeof value !== "string") return value; try { return JSON.parse(value); } catch { return value; } }
function asRecord(value: unknown): Record<string, unknown> { const parsed = parseUnknown(value); return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed as Record<string, unknown> : {}; }
function asText(value: unknown): string { return value == null ? "" : String(value); }
function asStringList(value: unknown): string[] { const parsed = parseUnknown(value); if (Array.isArray(parsed)) return parsed.map(String).map((item) => item.trim()).filter(Boolean); if (typeof parsed === "string") return lines(parsed); return []; }
function asEntries<T extends object>(value: unknown, create: () => T): T[] { const parsed = parseUnknown(value); if (!Array.isArray(parsed)) return [create()]; const rows = parsed.filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === "object" && !Array.isArray(item)).map((item) => { const row = create(); for (const key of Object.keys(row) as Array<keyof T>) row[key] = asText(item[key as string]) as T[keyof T]; return row; }); return rows.length ? rows : [create()]; }
function cleanEntries<T extends object>(entries: T[]): T[] { return entries.map((entry) => Object.fromEntries(Object.entries(entry).map(([key, value]) => [key, String(value ?? "").trim()])) as T).filter((entry) => Object.values(entry).some(Boolean)); }
function patchEntry<T, K extends keyof T>(entries: T[], index: number, key: K, value: T[K]): T[] { return entries.map((entry, row) => row === index ? { ...entry, [key]: value } : entry); }
function removeEntry<T>(entries: T[], index: number, create: () => T): T[] { const next = entries.filter((_, row) => row !== index); return next.length ? next : [create()]; }
function lines(value: string): string[] { return value.split(/\r?\n/).map((item) => item.trim()).filter(Boolean); }
function unique(values: string[]): string[] { return [...new Map(values.map((value) => [value.trim().toLowerCase(), value.trim()] as const).filter(([key]) => key)).values()]; }
function blankToNull(value: string): string | null { const next = value.trim(); return next || null; }
function formatFileSize(size: number | null | undefined): string { if (size == null || size <= 0) return "크기 정보 없음"; if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`; return `${Math.max(1, Math.round(size / 1024))} KB`; }
function sectionHasValue(value: unknown): boolean { if (typeof value === "string") return Boolean(value.trim()); if (Array.isArray(value)) return value.some(sectionHasValue); if (value && typeof value === "object") return Object.values(value).some(sectionHasValue); return Boolean(value); }
function formatDateTime(value: string): string { const date = new Date(value); return Number.isNaN(date.getTime()) ? value : date.toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }); }
