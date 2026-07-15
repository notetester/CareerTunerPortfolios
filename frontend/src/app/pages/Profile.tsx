import { useEffect, useMemo, useRef, useState } from "react";
import { AlertCircle, Brain, CheckCircle2, FileText, Loader2, Plus, RefreshCw, Save, Sparkles, Trash2, Upload, User, X } from "lucide-react";
import { Link, useBlocker, useSearchParams } from "react-router";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { registerNativeExitGuard } from "@/platform/nativeExitGuard";
import { ApiError } from "../lib/api";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { Textarea } from "../components/ui/textarea";
import { ModelPicker, type AiModelChoice } from "@/app/components/ai/ModelPicker";
import {
  diagnoseProfileCompleteness,
  deleteUnlinkedProfileFile,
  deleteProfilePortfolioFile,
  downloadProfilePortfolioFile,
  extractProfileSkills,
  getProfile,
  getProfileVersions,
  draftHasStructuredFields,
  importProfileDocument,
  listProfilePortfolioFiles,
  pollProfileAnalyze,
  PROFILE_DOC_ACCEPT,
  saveProfile,
  startProfileAnalyze,
  summarizeProfile,
  getProfileAiAnalysis,
  uploadProfileFile,
  uploadProfilePortfolioFile,
  type ProfileAiResponse,
  type ProfileAnalyzeDraft,
  type ProfileCompleteness,
  type ProfileImportTarget,
  type ProfilePortfolioFile,
  type UserProfile,
  type UserProfileVersion,
} from "../profile/profileApi";
import { draftPickFromCounts } from "../profile/profileDraftMerge";
import type { ConsentStatus } from "../auth/consentApi";
import { useConsent } from "../auth/ConsentContext";

interface EducationEntry {
  school: string;
  major: string;
  startDate: string;
  endDate: string;
  status: string;
}

interface CareerEntry {
  company: string;
  role: string;
  startDate: string;
  endDate: string;
  tasks: string;
  achievements: string;
}

interface ExperienceEntry {
  title: string;
  type: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
  result: string;
}

interface PreferencesForm {
  region: string;
  workType: string;
  salary: string;
  employmentType: string;
}

interface ProfileForm {
  desiredJob: string;
  desiredIndustry: string;
  skillsText: string;
  certificatesText: string;
  languagesText: string;
  portfolioLinksText: string;
  education: EducationEntry[];
  career: CareerEntry[];
  experiences: ExperienceEntry[];
  preferences: PreferencesForm;
  resumeText: string;
  selfIntro: string;
}

type AiToolType = "summary" | "skills" | "completeness";
type ProfileTab = "basic" | "resume" | "selfIntro" | "experience" | "skills" | "certificates" | "ai";
type TabRequirement = "필수" | "권장" | "선택";
type TabStatusTone = "done" | "warning" | "empty";

const profileTabValues: ProfileTab[] = ["basic", "resume", "selfIntro", "experience", "skills", "certificates", "ai"];

// 자기소개 한도는 문서 가져오기 서버 한도(MAX_IMPORT_CHARS=12000)와 동일해야 한다.
// 더 작으면 가져오기로 들어온 장문 자소서가 이후 모든 저장 검증에 걸려 계정이 저장 불능이 된다.
const SELF_INTRO_MAX_LENGTH = 12_000;
const RESUME_TEXT_MAX_LENGTH = 20_000;
const UNSAVED_PROFILE_MESSAGE = "저장하지 않은 프로필 변경사항이 있습니다. 이 페이지를 나가면 입력 내용이 사라집니다. 계속할까요?";
const ACTIVE_PROFILE_OPERATION_MESSAGE = "프로필 저장 또는 문서 처리가 진행 중입니다. 지금 나가면 완료 여부를 확인하기 어렵습니다. 계속할까요?";

const normalizeProfileTab = (value: string | null): ProfileTab => {
  if (value === "career") return "experience";
  if (value === "history") return "experience";
  return profileTabValues.includes(value as ProfileTab) ? (value as ProfileTab) : "basic";
};

const profileTabMeta: Record<ProfileTab, { label: string; requirement: TabRequirement; guide: string }> = {
  basic: {
    label: "기본 정보",
    requirement: "필수",
    guide: "희망 직무와 근무 조건은 AI가 어떤 기준으로 프로필을 평가할지 정하는 기준값입니다.",
  },
  resume: {
    label: "이력서 관리",
    requirement: "필수",
    guide: "이력서 원문은 경력 요약, 강점 추출, 직무 적합도 분석의 기본 자료로 사용됩니다.",
  },
  selfIntro: {
    label: "자기소개서 관리",
    requirement: "권장",
    guide: "자기소개서는 지원동기, 강점, 성장 가능성을 설명하는 보조 자료입니다.",
  },
  experience: {
    label: "경력/프로젝트 관리",
    requirement: "필수",
    guide: "경력과 프로젝트는 AI가 실제 수행 경험, 성과 근거, 직무 관련성을 판단하는 핵심 자료입니다.",
  },
  skills: {
    label: "기술/역량 관리",
    requirement: "필수",
    guide: "기술과 역량은 희망 직무에서 요구하는 능력과 현재 보유 역량을 비교할 때 사용됩니다.",
  },
  certificates: {
    label: "자격증/학력 관리",
    requirement: "선택",
    guide: "자격증, 어학, 학력은 직무 신뢰도와 추가 증빙을 보강하는 자료입니다.",
  },
  ai: {
    label: "AI 결과",
    requirement: "권장",
    guide: "AI 분석 결과를 확인하고 필요한 문장은 자기소개서나 스킬 목록에 반영할 수 있습니다.",
  },
};

const aiToolCopy: Record<AiToolType, { title: string; description: string; actionLabel: string }> = {
  summary: {
    title: "프로필 AI 요약",
    description: "내 이력의 핵심 방향과 지원 직무에 맞는 강점을 한눈에 정리합니다.",
    actionLabel: "요약 보기",
  },
  skills: {
    title: "역량 키워드 추출",
    description: "이력서와 활동 내용에서 직무에 연결할 수 있는 역량 키워드를 뽑아냅니다.",
    actionLabel: "키워드 보기",
  },
  completeness: {
    title: "완성도 진단",
    description: "직무군별 평가 기준과 가중치로 프로필의 보완 우선순위를 점검합니다.",
    actionLabel: "진단 보기",
  },
};

const createEducation = (): EducationEntry => ({ school: "", major: "", startDate: "", endDate: "", status: "" });
const createCareer = (): CareerEntry => ({ company: "", role: "", startDate: "", endDate: "", tasks: "", achievements: "" });
const createExperience = (): ExperienceEntry => ({ title: "", type: "", role: "", startDate: "", endDate: "", description: "", result: "" });
const createPreferences = (): PreferencesForm => ({ region: "", workType: "", salary: "", employmentType: "" });

const emptyForm: ProfileForm = {
  desiredJob: "",
  desiredIndustry: "",
  skillsText: "",
  certificatesText: "",
  languagesText: "",
  portfolioLinksText: "",
  education: [createEducation()],
  career: [createCareer()],
  experiences: [createExperience()],
  preferences: createPreferences(),
  resumeText: "",
  selfIntro: "",
};

const skillHints = [
  "커뮤니케이션",
  "문제 해결",
  "데이터 분석",
  "고객 응대",
  "영업",
  "마케팅",
  "회계",
  "문서 작성",
  "프로젝트 관리",
  "Java",
  "디자인 툴",
  "SQL",
];

const resumeTemplate = `이력서 요약:
- 희망 직무와 연결되는 경력/경험을 3~5줄로 요약합니다.

핵심 역량:
- 직무에 필요한 역량, 업무 도구, 강점을 한 줄씩 적습니다.

주요 경력/경험:
- 기관/회사/활동명:
- 담당 역할:
- 수행 업무:
- 사용 도구/업무 방식:

성과 또는 개선 결과:
- 수치, 개선 전후, 피드백, 결과물을 함께 적습니다.

자격/교육/기타:
- 자격증, 교육 수료, 어학, 포트폴리오 링크를 적습니다.`;

const selfIntroTemplate = `지원동기:
- 왜 이 직무에 지원하는지, 어떤 관심과 목표가 있는지 적습니다.

직무 관련 경험:
- 문제 상황, 맡은 역할, 해결 과정, 결과를 순서대로 적습니다.

나의 강점:
- 직무에 도움이 되는 성격, 태도, 업무 역량을 실제 사례와 연결합니다.

성과와 배운 점:
- 수치나 피드백이 있다면 함께 적고, 경험을 통해 배운 점을 정리합니다.

입사 후 포부:
- 입사 후 어떤 방식으로 기여하고 성장할지 적습니다.`;

export interface ProfilePageProps {
  mode?: "full" | "ai-analysis";
}

export function ProfilePage({ mode = "full" }: ProfilePageProps = {}) {
  const aiOnly = mode === "ai-analysis";
  const { status: consent } = useConsent();
  const [searchParams, setSearchParams] = useSearchParams();
  const [form, setForm] = useState<ProfileForm>(emptyForm);
  const formRef = useRef(form);
  formRef.current = form;
  const [loading, setLoading] = useState(true);
  const [profileLoaded, setProfileLoaded] = useState(false);
  const [profileWriteConflict, setProfileWriteConflict] = useState(false);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState<ProfileTab>(() => aiOnly ? "ai" : normalizeProfileTab(searchParams.get("tab")));
  const [activeAiView, setActiveAiView] = useState<AiToolType>("summary");
  const [aiLoading, setAiLoading] = useState<AiToolType | null>(null);
  // 프로필 AI(요약/스킬/완성도) 모델 선택. 기본 AUTO — 배경 자동 진단 호출엔 적용하지 않고 버튼 실행에만 쓴다.
  const [profileAiModel, setProfileAiModel] = useState<AiModelChoice>("AUTO");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [nonFatalWarning, setNonFatalWarning] = useState<string | null>(null);
  const [summaryResult, setSummaryResult] = useState<ProfileAiResponse | null>(null);
  const [skillsResult, setSkillsResult] = useState<ProfileAiResponse | null>(null);
  const [completeness, setCompleteness] = useState<ProfileCompleteness | null>(null);
  const [savedSnapshot, setSavedSnapshot] = useState(() => serializeProfileForm(emptyForm));
  // 탭 단위 저장의 기준본 — 서버에 마지막으로 반영된 폼. 다른 탭의 미저장(미검증) 편집이 함께 전송되는 걸 막는다.
  const lastSavedFormRef = useRef<ProfileForm>(emptyForm);
  const profileVersionNoRef = useRef<number | null>(null);
  const saveInFlightRef = useRef(false);
  const profileAnalysisEpochRef = useRef(0);
  const profileLoadEpochRef = useRef(0);
  const [docImporting, setDocImporting] = useState(false);
  const [analyzeDraft, setAnalyzeDraft] = useState<ProfileAnalyzeDraft | null>(null);
  const [analyzeStatus, setAnalyzeStatus] = useState<"idle" | "running" | "done" | "failed">("idle");
  const [analyzeError, setAnalyzeError] = useState<string | null>(null);
  const [portfolioFiles, setPortfolioFiles] = useState<ProfilePortfolioFile[]>([]);
  const [profileVersions, setProfileVersions] = useState<UserProfileVersion[]>([]);
  const [portfolioUploading, setPortfolioUploading] = useState(false);
  const [portfolioDeletingId, setPortfolioDeletingId] = useState<number | null>(null);
  const resumeFileRef = useRef<HTMLInputElement>(null);
  const selfIntroFileRef = useRef<HTMLInputElement>(null);
  const portfolioFileRef = useRef<HTMLInputElement>(null);

  const skillItems = useMemo(() => linesToArray(form.skillsText), [form.skillsText]);
  const selectedSkillSet = useMemo(() => new Set(skillItems.map((item) => item.toLowerCase())), [skillItems]);
  const isDirty = useMemo(() => serializeProfileForm(form) !== savedSnapshot, [form, savedSnapshot]);
  const profileExitGuardActive = isDirty || saving || docImporting;
  const profileExitMessage = saving || docImporting ? ACTIVE_PROFILE_OPERATION_MESSAGE : UNSAVED_PROFILE_MESSAGE;
  const tabStatuses = useMemo(() => getProfileTabStatuses(form, summaryResult, skillsResult, completeness), [form, summaryResult, skillsResult, completeness]);
  const aiConsentAgreed = consent?.aiDataAgreed === true;
  const resumeAnalysisAgreed = consent?.resumeAnalysisAgreed === true;
  const profileAiAllowed = aiConsentAgreed && resumeAnalysisAgreed;
  // 문서 import도 프로필 행/versionNo를 갱신하므로 수동 저장·AI와 직렬화한다.
  const saveDisabled = loading || !profileLoaded || saving || docImporting || profileWriteConflict;

  const clearProfileAiResults = () => {
    // 저장 전 프로필을 기준으로 만든 결과가 현재 프로필 결과처럼 보이지 않게 즉시 무효화한다.
    profileAnalysisEpochRef.current += 1;
    setSummaryResult(null);
    setSkillsResult(null);
    setCompleteness(null);
  };

  const load = async () => {
    const loadEpoch = ++profileLoadEpochRef.current;
    setLoading(true);
    setProfileLoaded(false);
    setProfileWriteConflict(false);
    profileVersionNoRef.current = null;
    setError(null);
    setNonFatalWarning(null);
    clearProfileAiResults();
    try {
      // 저장 가능 여부는 부가 목록이 아니라 프로필 본문 GET 성공을 기준으로 한다.
      const profile = await getProfile();
      if (loadEpoch !== profileLoadEpochRef.current) return;
      const nextForm = toForm(profile);
      setForm(nextForm);
      setSavedSnapshot(serializeProfileForm(nextForm));
      lastSavedFormRef.current = nextForm;
      profileVersionNoRef.current = typeof profile.versionNo === "number" ? profile.versionNo : null;
      setProfileLoaded(true);
      // 목록 API가 느리거나 실패해도 본문을 읽은 사용자는 즉시 편집·저장할 수 있어야 한다.
      setLoading(false);
      void Promise.allSettled([
        listProfilePortfolioFiles(),
        getProfileVersions(),
      ]).then(([portfolioResult, versionsResult]) => {
        if (loadEpoch !== profileLoadEpochRef.current) return;
        const warnings: string[] = [];
        if (portfolioResult.status === "fulfilled") {
          setPortfolioFiles(portfolioResult.value);
        } else {
          warnings.push("연결된 포트폴리오 파일 목록을 불러오지 못했습니다.");
        }
        if (versionsResult.status === "fulfilled") {
          setProfileVersions(versionsResult.value);
        } else {
          warnings.push("프로필 저장 이력을 불러오지 못했습니다.");
        }
        setNonFatalWarning(warnings.length ? `${warnings.join(" ")} 프로필 편집과 저장은 계속할 수 있습니다.` : null);
      });
    } catch (err) {
      if (loadEpoch !== profileLoadEpochRef.current) return;
      setProfileLoaded(false);
      setError(err instanceof Error ? err.message : "프로필을 불러오지 못했습니다.");
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  // 진입 시 AI를 자동 실행하지 않는다 — 완성도 진단은 AI 도구 버튼으로만 실행(무단 GPU 호출·알림 발송 방지).
  // 저장된 프로필 AI 분석을 불러와 화면에 시드한다(새로고침 후에도 최근 분석이 보이도록).
  useEffect(() => {
    if (!profileLoaded) return;
    const analysisEpoch = profileAnalysisEpochRef.current;
    void getProfileAiAnalysis()
      .then((saved) => {
        if (analysisEpoch !== profileAnalysisEpochRef.current) return;
        if (!saved.hasAnalysis) return;
        setSummaryResult((prev) => prev ?? {
          featureType: "PROFILE_SUMMARY",
          summary: saved.summary ?? "",
          extractedSkills: saved.extractedSkills ?? [],
          strengths: saved.strengths ?? [],
          gaps: saved.gaps ?? [],
          recommendations: saved.recommendations ?? [],
          completenessScore: saved.completenessScore ?? 0,
          jobFamily: saved.jobFamily ?? undefined,
          jobFamilyLabel: saved.jobFamilyLabel ?? undefined,
          criteria: saved.criteria ?? undefined,
          status: "SUCCESS",
          aiScore: saved.aiScore ?? undefined,
          qualityWarnings: saved.qualityWarnings ?? [],
          profileVersionId: saved.profileVersionId,
          profileVersionNo: saved.profileVersionNo,
        });
      })
      .catch(() => { /* 저장분 없음/조회 실패는 조용히 무시 — 기존 온디맨드 분석 흐름 유지 */ });
  }, [profileLoaded]);

  useEffect(() => {
    setActiveTab(aiOnly ? "ai" : normalizeProfileTab(searchParams.get("tab")));
  }, [aiOnly, searchParams]);

  // 미저장 변경이 있는 채로 새로고침·창 닫기를 하면 입력이 통째로 유실되므로 브라우저 이탈 확인을 띄운다.
  useEffect(() => {
    if (!profileExitGuardActive) return;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [profileExitGuardActive]);

  // history가 없는 네이티브 cold start/deep link에서는 하드웨어 back이 App.exitApp()으로 직행한다.
  // 이 경우에도 미저장 폼을 확인 없이 버리지 않도록 종료 직전 가드를 등록한다.
  useEffect(() => {
    if (!profileExitGuardActive) return;
    return registerNativeExitGuard(() => window.confirm(profileExitMessage));
  }, [profileExitGuardActive, profileExitMessage]);

  // React Router의 링크 이동, 브라우저 뒤로가기, Capacitor가 호출하는 global history.back을 같은 경계에서 막는다.
  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    profileExitGuardActive && currentLocation.pathname !== nextLocation.pathname,
  );

  useEffect(() => {
    if (blocker.state !== "blocked") return;
    if (window.confirm(profileExitMessage)) {
      blocker.proceed();
    } else {
      blocker.reset();
    }
  }, [blocker, profileExitMessage]);

  const refreshProfile = () => {
    if (docImporting) return;
    if (isDirty && !window.confirm(UNSAVED_PROFILE_MESSAGE)) return;
    void load();
  };

  const changeProfileTab = (tab: string) => {
    const nextTab = normalizeProfileTab(tab);
    // 독립 AI 분석 화면은 literal pathname에 고정한다. AI 결과를 프로필에 반영해도
    // 숨겨진 CRUD 탭이나 query-tab URL로 이동하지 않는다.
    if (aiOnly) {
      setActiveTab("ai");
      return;
    }
    setActiveTab(nextTab);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set("tab", nextTab);
    setSearchParams(nextParams, { replace: true });
  };

  const update = (key: keyof ProfileForm, value: string) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const updatePreferences = (key: keyof PreferencesForm, value: string) => {
    setForm((prev) => ({ ...prev, preferences: { ...prev.preferences, [key]: value } }));
  };

  const updateEducation = (index: number, key: keyof EducationEntry, value: string) => {
    setForm((prev) => ({ ...prev, education: updateList(prev.education, index, key, value) }));
  };

  const updateCareer = (index: number, key: keyof CareerEntry, value: string) => {
    setForm((prev) => ({ ...prev, career: updateList(prev.career, index, key, value) }));
  };

  const updateExperience = (index: number, key: keyof ExperienceEntry, value: string) => {
    setForm((prev) => ({ ...prev, experiences: updateList(prev.experiences, index, key, value) }));
  };

  const save = async (showSuccess = true, scope?: Exclude<ProfileTab, "ai">): Promise<boolean> => {
    if (profileWriteConflict) {
      setError("다른 화면의 프로필 변경과 현재 편집이 겹쳤습니다. 새로고침으로 최신 내용을 확인한 뒤 다시 저장해 주세요.");
      return false;
    }
    if (!profileLoaded || loading || docImporting || saveInFlightRef.current) {
      setError("프로필을 정상적으로 불러온 뒤 저장해 주세요.");
      return false;
    }
    saveInFlightRef.current = true;
    setSaving(true);
    setError(null);
    setNonFatalWarning(null);
    if (showSuccess) setMessage(null);
    try {
      if (scope) {
        const tabError = validateProfileTab(form, scope);
        if (tabError) {
          setError(`[${profileTabMeta[scope].label}] ${tabError}`);
          window.scrollTo({ top: 0, behavior: "smooth" });
          return false;
        }
      } else if (aiOnly) {
        // AI 결과 화면에서 바꿀 수 있는 필드는 스킬과 자기소개 메모뿐이다.
        // 다른 독립 CRUD 페이지의 필수값 때문에 AI 반영 저장이 막히지 않게 범위를 제한한다.
        const selfIntroError = validateProfileTab(form, "selfIntro");
        if (selfIntroError) {
          setError(`[${profileTabMeta.selfIntro.label}] ${selfIntroError}`);
          window.scrollTo({ top: 0, behavior: "smooth" });
          return false;
        }
      } else {
        const invalid = validateProfile(form);
        if (invalid) {
          setError(`[${profileTabMeta[invalid.tab].label}] ${invalid.message}`);
          changeProfileTab(invalid.tab);
          window.scrollTo({ top: 0, behavior: "smooth" });
          return false;
        }
      }
      // 탭 저장은 마지막 서버 반영본에 해당 탭 필드만 얹어 전송 — 다른 탭의 미검증 편집은 보내지 않는다.
      const payloadForm: ProfileForm = scope
        ? { ...lastSavedFormRef.current, ...pickTabFields(form, scope) }
        : aiOnly
          ? { ...lastSavedFormRef.current, skillsText: form.skillsText, selfIntro: form.selfIntro }
          : form;
      const savedProfile = await saveProfile(toRequest(payloadForm, profileVersionNoRef.current));
      if (typeof savedProfile.versionNo === "number") {
        profileVersionNoRef.current = savedProfile.versionNo;
      }
      lastSavedFormRef.current = payloadForm;
      setSavedSnapshot(serializeProfileForm(payloadForm));
      setProfileWriteConflict(false);
      clearProfileAiResults();
      // 저장 본문 성공을 먼저 확정한다. 이력 후조회 실패는 이미 완료된 저장을 실패로 되돌리지 않는다.
      if (showSuccess) {
        setMessage(scope ? `${profileTabMeta[scope].label} 탭이 저장되었습니다.` : aiOnly ? "AI 분석에서 반영한 프로필 내용을 저장했습니다." : "프로필이 저장되었습니다.");
      }
      void getProfileVersions()
        .then((versions) => setProfileVersions(versions))
        .catch(() => setNonFatalWarning("프로필 저장은 완료됐지만 저장 이력을 새로 불러오지 못했습니다. 다음 새로고침에서 다시 확인합니다."));
      return true;
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setProfileWriteConflict(true);
        setError("다른 화면에서 프로필을 먼저 변경했습니다. 현재 입력은 보존했습니다. 새로고침으로 최신 내용을 확인한 뒤 다시 저장해 주세요.");
      } else {
        setError(err instanceof Error ? err.message : "프로필 저장에 실패했습니다.");
      }
      window.scrollTo({ top: 0, behavior: "smooth" });
      return false;
    } finally {
      saveInFlightRef.current = false;
      setSaving(false);
    }
  };

  const uploadPortfolio = async (file: File) => {
    setPortfolioUploading(true);
    setError(null);
    setMessage(null);
    try {
      const uploaded = await uploadProfilePortfolioFile(file);
      setPortfolioFiles((current) => [uploaded, ...current.filter((item) => item.id !== uploaded.id)]);
      setMessage("포트폴리오 파일을 프로필에 연결했습니다. 텍스트를 추출할 수 있는 문서는 다음 AI 분석의 근거로 참고합니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "포트폴리오 업로드에 실패했습니다.");
    } finally {
      setPortfolioUploading(false);
    }
  };

  const deletePortfolio = async (file: ProfilePortfolioFile) => {
    if (!window.confirm(`'${file.originalName}' 파일을 영구 삭제할까요? 다음 AI 분석의 근거에서도 제거됩니다.`)) {
      return;
    }
    setPortfolioDeletingId(file.id);
    setError(null);
    setMessage(null);
    try {
      await deleteProfilePortfolioFile(file.id);
      setPortfolioFiles((current) => current.filter((item) => item.id !== file.id));
      setMessage("포트폴리오 파일을 삭제했습니다. 다음 AI 분석부터 이 파일은 근거로 사용되지 않습니다.");
    } catch (err) {
      // 서버 삭제가 실패한 경우 목록에서 먼저 빼지 않는다.
      setError(err instanceof Error ? err.message : "포트폴리오 파일 삭제에 실패했습니다.");
    } finally {
      setPortfolioDeletingId(null);
    }
  };

  const runAi = async (type: AiToolType, options: { saveBeforeRun?: boolean } = {}) => {
    if (aiLoading || !profileLoaded || loading) return;
    if (!profileAiAllowed) {
      setError(!aiConsentAgreed
        ? "AI 데이터 동의가 꺼져 있어 분석을 실행할 수 없습니다. 설정에서 다시 동의해 주세요."
        : "이력서 분석 개인정보 동의가 꺼져 있어 프로필 분석을 실행할 수 없습니다. 설정에서 다시 동의해 주세요.");
      return;
    }
    if (isDirty && !options.saveBeforeRun) {
      setActiveAiView(type);
      setError("저장하지 않은 변경사항이 있습니다. 저장 후 분석을 누르면 최신 프로필 기준으로 AI 분석을 실행합니다.");
      return;
    }
    // 클릭 즉시 '실행 중' 표시 — 선행 저장 구간에도 로딩·비활성이 걸려 연타(중복 실행)를 막는다.
    setAiLoading(type);
    if (isDirty && options.saveBeforeRun) {
      const saved = await save(false);
      if (!saved) {
        setAiLoading(null);
        return;
      }
    }
    setActiveAiView(type);
    changeProfileTab("ai");
    setError(null);
    setMessage(null);
    const analysisEpoch = profileAnalysisEpochRef.current;
    const acceptAnalysisResult = (resultVersionNo?: number | null) => {
      if (analysisEpoch !== profileAnalysisEpochRef.current) return false;
      const currentVersionNo = profileVersionNoRef.current;
      if (currentVersionNo != null && resultVersionNo != null && currentVersionNo !== resultVersionNo) {
        return false;
      }
      // GET 당시 행이 없었다면 ref는 null로 유지한다. 분석 사이에 온보딩 등이 실데이터 v1을
      // 만들었을 때 빈 화면이 base v1로 저장돼 그 값을 덮는 일을 막고, 다음 저장을 409로 안전하게 닫는다.
      return true;
    };
    const refreshVersionsAfterAnalysis = () => {
      void getProfileVersions()
        .then((versions) => setProfileVersions(versions))
        .catch(() => setNonFatalWarning("AI 분석은 완료됐지만 저장 이력을 새로 불러오지 못했습니다. 다음 새로고침에서 다시 확인합니다."));
    };
    const discardStaleAnalysis = () => {
      setNonFatalWarning("AI 분석 도중 프로필이 변경되어 이전 버전의 결과는 표시하지 않았습니다. 최신 프로필로 다시 실행해 주세요.");
    };
    try {
      if (type === "summary") {
        const result = await summarizeProfile(profileAiModel);
        if (!acceptAnalysisResult(result.profileVersionNo)) {
          discardStaleAnalysis();
          return;
        }
        setSummaryResult(result);
        setMessage("프로필 핵심 요약을 생성했습니다. AI 결과 탭에서 확인해 주세요.");
        refreshVersionsAfterAnalysis();
      } else if (type === "skills") {
        const result = await extractProfileSkills(profileAiModel);
        if (!acceptAnalysisResult(result.profileVersionNo)) {
          discardStaleAnalysis();
          return;
        }
        setSkillsResult(result);
        setMessage("이력에서 직무 역량 키워드를 추출했습니다. AI 결과 탭에서 확인해 주세요.");
        refreshVersionsAfterAnalysis();
      } else {
        const result = await diagnoseProfileCompleteness(profileAiModel);
        if (!acceptAnalysisResult(result.profileVersionNo)) {
          discardStaleAnalysis();
          return;
        }
        setCompleteness(result);
        setMessage("프로필 완성도와 보완 우선순위를 진단했습니다. AI 결과 탭에서 확인해 주세요.");
        refreshVersionsAfterAnalysis();
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 기능 실행에 실패했습니다. AI 데이터 동의 상태를 확인해 주세요.");
    } finally {
      setAiLoading(null);
    }
  };

  const toggleSkill = (skill: string) => {
    const hasSkill = skillItems.some((item) => item.toLowerCase() === skill.toLowerCase());
    update("skillsText", arrayToLines(hasSkill ? skillItems.filter((item) => item.toLowerCase() !== skill.toLowerCase()) : [...skillItems, skill]));
  };

  const addSkills = (skills: string[]) => {
    const next = mergeUniqueLines(form.skillsText, skills);
    update("skillsText", arrayToLines(next));
    if (!aiOnly) changeProfileTab("skills");
    setMessage(aiOnly
      ? "선택한 역량 키워드를 반영했습니다. 상단의 반영 내용 저장을 눌러 확정해 주세요."
      : "선택한 역량 키워드를 내 스킬 목록에 추가했습니다. 저장을 눌러 반영해 주세요.");
  };

  const removeSkill = (skill: string) => {
    update("skillsText", arrayToLines(skillItems.filter((item) => item !== skill)));
  };

  const appendToSelfIntro = (title: string, values: string[]) => {
    const cleaned = values.map((value) => value.trim()).filter(Boolean);
    if (!cleaned.length) return;
    const block = [`[${title}]`, ...cleaned.map((value) => `- ${value}`)].join("\n");
    update("selfIntro", [form.selfIntro.trim(), block].filter(Boolean).join("\n\n"));
    if (!aiOnly) changeProfileTab("selfIntro");
    setMessage(aiOnly
      ? "AI 결과를 자기소개 메모에 반영했습니다. 상단의 반영 내용 저장을 눌러 확정해 주세요."
      : "AI 결과를 자기소개/강점 메모에 추가했습니다. 저장을 눌러 반영해 주세요.");
  };

  /** draft → 폼 state 만 반영(DB 저장은 사용자가 저장 버튼). 빈 배열은 건너뜀. */
  const mergeDraftIntoForm = (
    draft: ProfileAnalyzeDraft,
    opts: {
      education: boolean;
      career: boolean;
      projects: boolean;
      skills: boolean;
      portfolioLinks: boolean;
    },
  ) => {
    const nonEmptyArray = (v: unknown): v is unknown[] => Array.isArray(v) && v.length > 0;
    setForm((prev) => {
      const next = { ...prev };
      if (opts.education && nonEmptyArray(draft.education)) {
        next.education = parseEntries(draft.education, createEducation);
      }
      if (opts.career && nonEmptyArray(draft.career)) {
        next.career = parseEntries(draft.career, createCareer);
      }
      if (opts.projects && nonEmptyArray(draft.projects)) {
        next.experiences = parseEntries(draft.projects, createExperience);
      }
      if (opts.skills && Array.isArray(draft.skills) && draft.skills.length) {
        next.skillsText = arrayToLines(mergeUniqueLines(prev.skillsText, draft.skills));
      }
      if (opts.portfolioLinks && Array.isArray(draft.portfolioLinks) && draft.portfolioLinks.length) {
        next.portfolioLinksText = arrayToLines(
          mergeUniqueLines(prev.portfolioLinksText, draft.portfolioLinks),
        );
      }
      return next;
    });
  };

  /**
   * 문서 첨부: upload → import(텍스트 덤프) → analyze 폴링 → 폼 자동 반영 + 확인 카드.
   * 이력서/자소서 탭 모두 구조화 분석을 돌린다(파일에 학력·경력이 있는 경우 대비).
   */
  const handleDocumentAttach = async (file: File, target: ProfileImportTarget) => {
    if (!profileLoaded || loading || profileWriteConflict) {
      setError("프로필을 정상적으로 불러온 뒤 문서를 가져와 주세요.");
      return;
    }
    if (!resumeAnalysisAgreed) {
      setError("이력서 분석 개인정보 수집·이용 동의가 필요합니다. 설정에서 동의한 뒤 파일을 가져와 주세요.");
      return;
    }
    const existing =
      target === "RESUME_TEXT" ? form.resumeText.trim() : form.selfIntro.trim();
    if (existing) {
      const ok = window.confirm("기존 내용을 대체합니다. 계속할까요?");
      if (!ok) return;
    }
    const importBaseline = lastSavedFormRef.current;
    const importLocalStart = form;
    const importedField: keyof ProfileForm = target === "RESUME_TEXT" ? "resumeText" : "selfIntro";
    setDocImporting(true);
    setError(null);
    setMessage(null);
    setAnalyzeError(null);
    setAnalyzeDraft(null);
    let uploadedFileId: number | null = null;
    try {
      const uploaded = await uploadProfileFile(file, "RESUME");
      uploadedFileId = uploaded.id;
      const imported = await importProfileDocument(
        uploaded.id,
        target,
        profileVersionNoRef.current,
      );
      clearProfileAiResults();
      setNonFatalWarning(null);
      // 서버 응답에는 잠금 RMW 이후의 최신 전체 프로필이 들어 있다. 로컬이 기존 기준본과 같은 필드는
      // 서버 최신값을 채택하고, import 중 사용자가 바꾼 필드만 보존하는 3-way merge로 동시 변경을 지킨다.
      const serverForm = toForm(imported.profile);
      const mergeResult = mergeServerProfileForm(
        formRef.current,
        importBaseline,
        serverForm,
        importLocalStart,
        importedField,
      );
      setSavedSnapshot(serializeProfileForm(serverForm));
      lastSavedFormRef.current = serverForm;
      setForm(mergeResult.form);
      setProfileWriteConflict(mergeResult.conflicted);
      if (mergeResult.conflicted) {
        // 최신 versionNo를 채택하면 다음 저장이 동시 변경을 덮을 수 있으므로 기존 기준 버전을 유지한다.
        setNonFatalWarning("문서를 가져오는 동안 다른 화면과 같은 프로필 항목이 함께 변경됐습니다. 서버 데이터는 보존했습니다. 새로고침으로 최신 내용을 확인해 주세요.");
      } else if (typeof imported.profile.versionNo === "number") {
        profileVersionNoRef.current = imported.profile.versionNo;
      }
      void getProfileVersions()
        .then((versions) => setProfileVersions(versions))
        .catch(() => setNonFatalWarning("문서 원문은 저장됐지만 저장 이력을 새로 불러오지 못했습니다. 다음 새로고침에서 다시 확인합니다."));
      const dumpMsg = imported.truncated
        ? "일부만 저장했습니다. 긴 문서는 앞부분만 반영됩니다."
        : target === "RESUME_TEXT"
          ? "이력서 원문을 가져왔습니다."
          : "자기소개서 원문을 가져왔습니다.";
      setMessage(`${dumpMsg} 학력·경력 분석 중…`);

      // 항상 구조화 분석 (이전: RESUME_TEXT 만 → 자소서 탭/경로에서 analyze 누락)
      setAnalyzeStatus("running");
      try {
        const started = await startProfileAnalyze(uploaded.id);
        if (!started?.jobId) {
          throw new Error("분석 작업을 시작하지 못했습니다.");
        }
        const finished = await pollProfileAnalyze(started.jobId);
        if (finished.status === "DONE" && finished.draft) {
          setAnalyzeDraft(finished.draft);
          setAnalyzeStatus("done");
          if (draftHasStructuredFields(finished.draft)) {
            // 자동 반영하지 않는다 — 확인 카드에서 항목을 골라 적용(온보딩 경로와 동일한 승인 방식).
            // LLM 오추출이 기존 학력·경력을 승인 없이 덮어쓰는 사고 방지.
            setMessage(
              "원문을 저장하고 학력·경력·스킬을 추출했어요. 카드에서 적용할 항목을 골라 주세요.",
            );
          } else {
            setMessage(`${dumpMsg} 구조 항목은 비어 있어요. 원문만 반영됐습니다.`);
          }
        } else {
          setAnalyzeStatus("failed");
          setAnalyzeError(finished.errorMessage || "구조화 분석은 실패했어요. 폼을 직접 채워주세요.");
          setMessage(dumpMsg);
        }
      } catch (analyzeErr) {
        setAnalyzeStatus("failed");
        setAnalyzeError(
          analyzeErr instanceof Error
            ? analyzeErr.message
            : "구조화 분석은 실패했어요. 폼을 직접 채워주세요.",
        );
        setMessage(dumpMsg);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "첨부에 실패했어요. 다시 시도해 주세요.");
    } finally {
      if (uploadedFileId !== null) {
        try {
          await deleteUnlinkedProfileFile(uploadedFileId);
        } catch {
          // 브라우저/앱 강제 종료를 포함해 즉시 삭제하지 못한 원본은 서버가 24시간 TTL로 회수한다.
          setNonFatalWarning((current) => current
            ?? "처리한 문서 원본을 즉시 정리하지 못했습니다. 서버가 24시간 후 자동으로 정리합니다.");
        }
      }
      setDocImporting(false);
    }
  };

  /** 확인 카드에서 선택 항목만 폼에 반영. DB 는 저장 버튼으로 확정. */
  const applyAnalyzeDraft = (opts: {
    education: boolean;
    career: boolean;
    projects: boolean;
    skills: boolean;
    portfolioLinks: boolean;
  }) => {
    if (!analyzeDraft) return;
    mergeDraftIntoForm(analyzeDraft, opts);
    setAnalyzeDraft(null);
    setAnalyzeStatus("idle");
    setMessage("추출 결과를 폼에 반영했습니다. 저장을 눌러 확정해 주세요.");
  };

  const dismissAnalyzeDraft = () => {
    setAnalyzeDraft(null);
    setAnalyzeStatus("idle");
    setAnalyzeError(null);
  };

  const insertResumeTemplate = () => {
    const before = countTemplateCopies(form.resumeText, resumeTemplate);
    const next = insertGuideTemplate(form.resumeText, resumeTemplate, {
      alreadyHas: "이력서 입력 틀이 이미 있어요.",
      appendConfirm: "작성 중인 내용 뒤에 입력 틀을 붙일까요?",
    });
    if (next == null) {
      setMessage("이력서 입력 틀이 이미 있어요. 「파일에서 가져오기」와는 다른 버튼입니다.");
      return;
    }
    update("resumeText", next);
    if (before >= 2) setMessage("중복으로 붙어 있던 입력 틀을 하나로 정리했어요.");
  };

  const insertSelfIntroTemplate = () => {
    const before = countTemplateCopies(form.selfIntro, selfIntroTemplate);
    const next = insertGuideTemplate(form.selfIntro, selfIntroTemplate, {
      alreadyHas: "자기소개 입력 틀이 이미 있어요.",
      appendConfirm: "작성 중인 내용 뒤에 입력 틀을 붙일까요?",
    });
    if (next == null) {
      setMessage("자기소개 입력 틀이 이미 있어요. 「파일에서 가져오기」와는 다른 버튼입니다.");
      return;
    }
    update("selfIntro", next);
    if (before >= 2) setMessage("중복으로 붙어 있던 입력 틀을 하나로 정리했어요.");
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1400px] space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-950">
              <User className="size-6 text-blue-600" />
              {aiOnly ? "AI 프로필 분석" : "프로필/이력서 관리"}
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              {aiOnly
                ? "저장된 프로필을 기준으로 핵심 요약, 역량 키워드와 완성도를 분석합니다."
                : "지원서 분석과 AI 추천에 사용할 기본 프로필, 이력서, 직무 역량을 관리합니다."}
            </p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={refreshProfile} disabled={loading || saving || docImporting}>
              <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
              새로고침
            </Button>
            {!aiOnly && activeTab !== "ai" && (
              <Button variant="outline" onClick={() => void save(true, activeTab as Exclude<ProfileTab, "ai">)} disabled={saveDisabled}>
                <Save className="size-4" />
                이 탭만 저장
              </Button>
            )}
            {(!aiOnly || isDirty) && (
              <Button onClick={() => void save()} disabled={saveDisabled} className="bg-blue-600 text-white hover:bg-blue-700">
                <Save className="size-4" />
                {saving ? "저장 중..." : aiOnly ? "반영 내용 저장" : "전체 저장"}
              </Button>
            )}
          </div>
        </div>

        {error && <StatusBox tone="error" text={error} />}
        {message && <StatusBox tone="success" text={message} />}
        {nonFatalWarning && <StatusBox tone="warning" text={nonFatalWarning} />}
        {isDirty && (
          <StatusBox tone="warning" text={aiOnly
            ? "AI 결과에서 프로필에 반영한 내용이 아직 저장되지 않았습니다. 상단의 반영 내용 저장을 눌러 확정해 주세요."
            : "저장하지 않은 변경사항이 있습니다. AI 분석은 저장된 프로필 기준으로 실행되므로, 분석 전 저장을 권장합니다."} />
        )}

        <div className="grid gap-5 lg:grid-cols-[320px_1fr]">
          <aside className="space-y-5">
            <Card className="border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="text-base">완성도</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <div className="text-3xl font-black text-blue-600">{completeness?.score ?? 0}%</div>
                  <Progress value={completeness?.score ?? 0} className="mt-2 h-2" />
                  <p className="mt-2 text-xs leading-5 text-slate-500">{describeCompletenessScore(completeness?.score)}</p>
                </div>
                {completeness?.jobFamilyLabel && (
                  <div className="rounded-md border border-blue-100 bg-blue-50 px-3 py-2">
                    <div className="text-xs font-bold text-blue-500">평가 직무군</div>
                    <div className="mt-1 text-sm font-bold text-blue-900">{completeness.jobFamilyLabel}</div>
                    <div className="mt-1 text-xs text-blue-700">
                      {formatModel(completeness.model)} · {formatStatus(completeness.status)}
                    </div>
                  </div>
                )}
                <Checklist title="완료" items={completeness?.completed ?? []} done />
                <Checklist title="보강 필요" items={completeness?.missing ?? []} />
              </CardContent>
            </Card>

            <Card className="border-slate-200 bg-card">
              <CardHeader>
                <CardTitle className="text-base">AI 도구</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                <ConsentStatusBox consent={consent} />
                {isDirty && (
                  <Button variant="outline" className="w-full justify-start border-amber-200 bg-amber-50 text-amber-800 hover:bg-amber-100" onClick={() => void save()} disabled={saveDisabled}>
                    <Save className="size-4" />
                    저장 후 분석 준비
                  </Button>
                )}
                <AiToolButton
                  type="summary"
                  active={activeAiView === "summary"}
                  loading={aiLoading === "summary"}
                  disabled={saveDisabled || !!aiLoading || !profileAiAllowed}
                  icon={<Sparkles className="size-4" />}
                  onClick={() => void runAi("summary", { saveBeforeRun: isDirty })}
                />
                <AiToolButton
                  type="skills"
                  active={activeAiView === "skills"}
                  loading={aiLoading === "skills"}
                  disabled={saveDisabled || !!aiLoading || !profileAiAllowed}
                  icon={<Brain className="size-4" />}
                  onClick={() => void runAi("skills", { saveBeforeRun: isDirty })}
                />
                <AiToolButton
                  type="completeness"
                  active={activeAiView === "completeness"}
                  loading={aiLoading === "completeness"}
                  disabled={saveDisabled || !!aiLoading || !profileAiAllowed}
                  icon={<CheckCircle2 className="size-4" />}
                  onClick={() => void runAi("completeness", { saveBeforeRun: isDirty })}
                />
                <div className="flex items-center justify-between pt-1">
                  <span className="text-xs text-slate-500">AI 모델</span>
                  <ModelPicker value={profileAiModel} onChange={setProfileAiModel} disabled={saveDisabled || !!aiLoading || !profileAiAllowed} />
                </div>
              </CardContent>
            </Card>
          </aside>

          {/* min-w-0: 그리드/플렉스 자식이 내용(탭 목록) 최소폭으로 부풀어 페이지 가로 오버플로를 만드는 것 방지 */}
          <section className="min-w-0 space-y-5">
            <Tabs value={activeTab} onValueChange={changeProfileTab}>
              {!aiOnly && (
                <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-card p-1">
                  {profileTabValues.map((tab) => (
                    <TabsTrigger key={tab} value={tab} className="h-auto min-w-fit flex-col items-start gap-1 px-3 py-2">
                      <span className="flex items-center gap-1.5">
                        <span>{profileTabMeta[tab].label}</span>
                        <RequirementBadge requirement={profileTabMeta[tab].requirement} />
                      </span>
                      <TabStatusBadge status={tabStatuses[tab]} />
                    </TabsTrigger>
                  ))}
                </TabsList>
              )}

              {activeTab === "basic" && (
                <TabsContent value="basic" className="mt-5">
                <TabGuide tab="basic" status={tabStatuses.basic} />
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="text-base">희망 조건</CardTitle>
                  </CardHeader>
                  <CardContent className="grid gap-4 md:grid-cols-2">
                    <Field label="희망 직무">
                      <Input maxLength={80} value={form.desiredJob} onChange={(event) => update("desiredJob", event.target.value)} placeholder="예: 마케팅 AE, 간호사, 회계 담당자, 서비스 기획자" />
                    </Field>
                    <Field label="희망 산업">
                      <Input maxLength={80} value={form.desiredIndustry} onChange={(event) => update("desiredIndustry", event.target.value)} placeholder="예: 병원, 금융, 교육, 제조, IT" />
                    </Field>
                    <Field label="희망 지역">
                      <Input maxLength={80} value={form.preferences.region} onChange={(event) => updatePreferences("region", event.target.value)} placeholder="예: 서울, 경기, 원격" />
                    </Field>
                    <Field label="근무 형태">
                      <Input maxLength={80} value={form.preferences.workType} onChange={(event) => updatePreferences("workType", event.target.value)} placeholder="예: 사무실 근무, 교대 근무, 재택 병행" />
                    </Field>
                    <Field label="고용 형태">
                      <Input maxLength={80} value={form.preferences.employmentType} onChange={(event) => updatePreferences("employmentType", event.target.value)} placeholder="예: 정규직, 계약직, 인턴" />
                    </Field>
                    <Field label="희망 연봉">
                      <Input maxLength={80} value={form.preferences.salary} onChange={(event) => updatePreferences("salary", event.target.value)} placeholder="예: 회사 내규, 3,200만원 이상" />
                    </Field>
                  </CardContent>
                </Card>
                </TabsContent>
              )}

              {(analyzeStatus === "running" || analyzeDraft || analyzeError) && (
                <div className="mb-4">
                  <AnalyzeConfirmCard
                    status={analyzeStatus}
                    draft={analyzeDraft}
                    error={analyzeError}
                    onApply={applyAnalyzeDraft}
                    onDismiss={dismissAnalyzeDraft}
                  />
                </div>
              )}

              {activeTab === "resume" && (
                <TabsContent value="resume" className="mt-5">
                <TabGuide tab="resume" status={tabStatuses.resume} />
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-base">
                      <FileText className="size-4 text-blue-600" />
                      이력서 관리
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="flex flex-wrap gap-2">
                      <Button variant="outline" size="sm" onClick={insertResumeTemplate}>
                        이력서 입력 틀 추가
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={docImporting || !resumeAnalysisAgreed || loading || !profileLoaded || saving || profileWriteConflict}
                        onClick={() => resumeFileRef.current?.click()}
                      >
                        {docImporting ? (
                          <Loader2 className="size-3.5 animate-spin" />
                        ) : (
                          <Upload className="size-3.5" />
                        )}
                        파일에서 가져오기
                      </Button>
                      <input
                        ref={resumeFileRef}
                        type="file"
                        accept={PROFILE_DOC_ACCEPT}
                        className="hidden"
                        onChange={(event) => {
                          const file = event.target.files?.[0];
                          event.target.value = "";
                          if (file) void handleDocumentAttach(file, "RESUME_TEXT");
                        }}
                      />
                    </div>
                    <p className="text-xs text-slate-500">
                      .pdf / .docx / .txt / .md 지원 (.doc 미지원). 텍스트를 읽어 원문에 넣고, 학력·경력 등은 확인 후 반영합니다.
                    </p>
                    <Field label="이력서 원문">
                      <Textarea value={form.resumeText} onChange={(event) => update("resumeText", event.target.value)} rows={12} placeholder="파일 첨부 또는 이력서 내용을 직접 붙여넣어 관리합니다." />
                    </Field>
                  </CardContent>
                </Card>
                </TabsContent>
              )}

              {activeTab === "selfIntro" && (
                <TabsContent value="selfIntro" className="mt-5">
                <TabGuide tab="selfIntro" status={tabStatuses.selfIntro} />
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="text-base">자기소개서 관리</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="flex flex-wrap gap-2">
                      <Button variant="outline" size="sm" onClick={insertSelfIntroTemplate}>
                        자기소개 입력 틀 추가
                      </Button>
                      <Button
                        variant="outline"
                        size="sm"
                        disabled={docImporting || !resumeAnalysisAgreed || loading || !profileLoaded || saving || profileWriteConflict}
                        onClick={() => selfIntroFileRef.current?.click()}
                      >
                        {docImporting ? (
                          <Loader2 className="size-3.5 animate-spin" />
                        ) : (
                          <Upload className="size-3.5" />
                        )}
                        파일에서 가져오기
                      </Button>
                      <input
                        ref={selfIntroFileRef}
                        type="file"
                        accept={PROFILE_DOC_ACCEPT}
                        className="hidden"
                        onChange={(event) => {
                          const file = event.target.files?.[0];
                          event.target.value = "";
                          if (file) void handleDocumentAttach(file, "SELF_INTRO");
                        }}
                      />
                    </div>
                    <Field label="자기소개/강점">
                      <Textarea value={form.selfIntro} onChange={(event) => update("selfIntro", event.target.value)} rows={8} placeholder="지원 직무와 연결되는 경험, 강점, 협업 사례를 정리하세요." />
                      <p className={`mt-1 text-right text-xs ${form.selfIntro.length > SELF_INTRO_MAX_LENGTH ? "font-semibold text-red-600" : "text-slate-400"}`}>
                        {form.selfIntro.length.toLocaleString("ko-KR")} / {SELF_INTRO_MAX_LENGTH.toLocaleString("ko-KR")}자
                      </p>
                    </Field>
                  </CardContent>
                </Card>
                </TabsContent>
              )}

              {activeTab === "skills" && (
                <TabsContent value="skills" className="mt-5">
                <TabGuide tab="skills" status={tabStatuses.skills} />
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="text-base">직무 역량/스킬 관리</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="flex flex-wrap gap-2">
                      {skillHints.map((skill) => (
                        <button
                          key={skill}
                          type="button"
                          className={`rounded-md border px-3 py-1.5 text-sm font-semibold transition-colors ${
                            selectedSkillSet.has(skill.toLowerCase())
                              ? "border-blue-600 bg-blue-600 text-white"
                              : "border-slate-200 bg-card text-slate-700 hover:border-blue-300"
                          }`}
                          onClick={() => toggleSkill(skill)}
                        >
                          {skill}
                        </button>
                      ))}
                    </div>
                    <Field label="직무 역량/스킬">
                      <Textarea value={form.skillsText} onChange={(event) => update("skillsText", event.target.value)} rows={8} placeholder="직무에 필요한 역량, 도구, 업무 스킬을 한 줄에 하나씩 입력" />
                    </Field>
                    <SkillTagList skills={skillItems} onRemove={removeSkill} />
                  </CardContent>
                </Card>
                </TabsContent>
              )}

              {activeTab === "experience" && (
                <TabsContent value="experience" className="mt-5 space-y-5">
                <TabGuide tab="experience" status={tabStatuses.experience} />
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <CardTitle className="text-base">포트폴리오/활동 링크와 파일</CardTitle>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        disabled={portfolioUploading || loading || !profileLoaded || saving}
                        onClick={() => portfolioFileRef.current?.click()}
                      >
                        {portfolioUploading ? <Loader2 className="size-4 animate-spin" /> : <Upload className="size-4" />}
                        파일 추가
                      </Button>
                      <input
                        ref={portfolioFileRef}
                        type="file"
                        className="hidden"
                        accept=".txt,.md,.pdf,.docx,image/*"
                        onChange={(event) => {
                          const file = event.target.files?.[0];
                          if (file) void uploadPortfolio(file);
                          event.target.value = "";
                        }}
                      />
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <Field label="링크 목록">
                      <Textarea value={form.portfolioLinksText} onChange={(event) => update("portfolioLinksText", event.target.value)} placeholder="노션, 블로그, 작업물, 활동 기록 링크를 한 줄에 하나씩 입력" rows={4} />
                    </Field>
                    <div>
                      <div className="mb-2 text-sm font-semibold text-slate-700">연결된 파일</div>
                      {portfolioFiles.length > 0 ? (
                        <div className="space-y-2">
                          {portfolioFiles.map((file) => (
                            <div
                              key={file.id}
                              className="flex w-full items-center gap-1 rounded-lg border border-slate-200 p-1 transition-colors hover:border-blue-300 hover:bg-blue-50/50"
                            >
                              <button
                                type="button"
                                onClick={() => void downloadProfilePortfolioFile(file).catch((err) => {
                                  setError(err instanceof Error ? err.message : "파일을 내려받지 못했습니다.");
                                })}
                                className="flex min-w-0 flex-1 items-center gap-3 rounded-md px-2 py-1 text-left"
                              >
                                <FileText className="size-4 shrink-0 text-blue-600" />
                                <span className="min-w-0 flex-1 truncate text-sm font-semibold text-slate-800">{file.originalName}</span>
                                <span className="shrink-0 text-xs text-slate-500">{formatFileSize(file.sizeBytes)}</span>
                              </button>
                              <Button
                                type="button"
                                size="sm"
                                variant="ghost"
                                aria-label={`${file.originalName} 삭제`}
                                disabled={portfolioDeletingId != null || loading || !profileLoaded || saving}
                                onClick={() => void deletePortfolio(file)}
                                className="shrink-0 text-red-600 hover:bg-red-50 hover:text-red-700"
                              >
                                {portfolioDeletingId === file.id
                                  ? <Loader2 className="size-4 animate-spin" />
                                  : <Trash2 className="size-4" />}
                                <span className="sr-only">삭제</span>
                              </Button>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <p className="text-sm text-slate-500">연결된 포트폴리오 파일이 없습니다.</p>
                      )}
                    </div>
                  </CardContent>
                </Card>

                <EntrySection title="경력" onAdd={() => setForm((prev) => ({ ...prev, career: [...prev.career, createCareer()] }))}>
                  {form.career.map((item, index) => (
                    <EntryCard key={index} title={`경력 ${index + 1}`} onRemove={() => setForm((prev) => ({ ...prev, career: removeAt(prev.career, index, createCareer) }))}>
                      <div className="grid gap-4 md:grid-cols-2">
                        <Field label="회사/기관명">
                          <Input maxLength={80} value={item.company} onChange={(event) => updateCareer(index, "company", event.target.value)} placeholder="예: ABC 병원, OO카페, 스타트업" />
                        </Field>
                        <Field label="직무/역할">
                          <Input maxLength={80} value={item.role} onChange={(event) => updateCareer(index, "role", event.target.value)} placeholder="예: 고객 상담, 마케팅 인턴, 회계 보조" />
                        </Field>
                        <Field label="시작월">
                          <Input type="month" value={item.startDate} onChange={(event) => updateCareer(index, "startDate", event.target.value)} />
                        </Field>
                        <Field label="종료월">
                          <Input type="month" value={item.endDate} onChange={(event) => updateCareer(index, "endDate", event.target.value)} disabled={isOngoing(item.startDate, item.endDate)} />
                        </Field>
                        <CurrentCheckbox
                          label="현재 재직 중"
                          checked={isOngoing(item.startDate, item.endDate)}
                          disabled={!item.startDate}
                          onChange={(checked) => updateCareer(index, "endDate", checked ? "" : currentMonth())}
                        />
                        <Field label="주요 업무" className="md:col-span-2">
                          <Textarea value={item.tasks} onChange={(event) => updateCareer(index, "tasks", event.target.value)} rows={4} placeholder="담당했던 업무를 구체적으로 적어주세요." />
                        </Field>
                        <Field label="성과/배운 점" className="md:col-span-2">
                          <Textarea value={item.achievements} onChange={(event) => updateCareer(index, "achievements", event.target.value)} rows={4} placeholder="수치, 개선 결과, 배운 점을 적어주세요." />
                        </Field>
                      </div>
                    </EntryCard>
                  ))}
                </EntrySection>

                <EntrySection title="경험/프로젝트/활동" onAdd={() => setForm((prev) => ({ ...prev, experiences: [...prev.experiences, createExperience()] }))}>
                  {form.experiences.map((item, index) => (
                    <EntryCard key={index} title={`경험 ${index + 1}`} onRemove={() => setForm((prev) => ({ ...prev, experiences: removeAt(prev.experiences, index, createExperience) }))}>
                      <div className="grid gap-4 md:grid-cols-2">
                        <Field label="활동명">
                          <Input maxLength={100} value={item.title} onChange={(event) => updateExperience(index, "title", event.target.value)} placeholder="예: 공모전, 실습, 캠페인, 개발 프로젝트" />
                        </Field>
                        <Field label="유형">
                          <select value={item.type} onChange={(event) => updateExperience(index, "type", event.target.value)} className="h-10 w-full rounded-md border border-slate-200 bg-card px-3 text-sm">
                            <option value="">선택</option>
                            <option value="공모전">공모전</option>
                            <option value="동아리">동아리</option>
                            <option value="실습">실습</option>
                            <option value="아르바이트">아르바이트</option>
                            <option value="개인 프로젝트">개인 프로젝트</option>
                            <option value="팀 프로젝트">팀 프로젝트</option>
                            <option value="봉사활동">봉사활동</option>
                            <option value="기타">기타</option>
                          </select>
                        </Field>
                        <Field label="역할">
                          <Input maxLength={80} value={item.role} onChange={(event) => updateExperience(index, "role", event.target.value)} placeholder="예: 팀장, 콘텐츠 제작, 고객 응대, 데이터 정리" />
                        </Field>
                        <Field label="시작월">
                          <Input type="month" value={item.startDate} onChange={(event) => updateExperience(index, "startDate", event.target.value)} />
                        </Field>
                        <Field label="종료월">
                          <Input type="month" value={item.endDate} onChange={(event) => updateExperience(index, "endDate", event.target.value)} disabled={isOngoing(item.startDate, item.endDate)} />
                        </Field>
                        <CurrentCheckbox
                          label="현재 진행 중"
                          checked={isOngoing(item.startDate, item.endDate)}
                          disabled={!item.startDate}
                          onChange={(checked) => updateExperience(index, "endDate", checked ? "" : currentMonth())}
                        />
                        <Field label="내용" className="md:col-span-2">
                          <Textarea value={item.description} onChange={(event) => updateExperience(index, "description", event.target.value)} rows={4} placeholder="문제 상황, 맡은 일, 진행 과정을 적어주세요." />
                        </Field>
                        <Field label="결과/성과" className="md:col-span-2">
                          <Textarea value={item.result} onChange={(event) => updateExperience(index, "result", event.target.value)} rows={4} placeholder="성과, 수치, 피드백, 배운 점을 적어주세요." />
                        </Field>
                      </div>
                    </EntryCard>
                  ))}
                </EntrySection>
                </TabsContent>
              )}

              {activeTab === "certificates" && (
                <TabsContent value="certificates" className="mt-5 space-y-5">
                <TabGuide tab="certificates" status={tabStatuses.certificates} />
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="text-base">자격증/어학 관리</CardTitle>
                  </CardHeader>
                  <CardContent className="grid gap-4 md:grid-cols-2">
                    <Field label="자격증">
                      <Textarea value={form.certificatesText} onChange={(event) => update("certificatesText", event.target.value)} rows={6} placeholder="예: 컴퓨터활용능력, 전산회계, 간호사 면허, TOEIC" />
                    </Field>
                    <Field label="언어">
                      <Textarea value={form.languagesText} onChange={(event) => update("languagesText", event.target.value)} rows={6} placeholder="예: TOEIC 850, 일본어 회화 가능" />
                    </Field>
                  </CardContent>
                </Card>

                <EntrySection title="학력" onAdd={() => setForm((prev) => ({ ...prev, education: [...prev.education, createEducation()] }))}>
                  {form.education.map((item, index) => (
                    <EntryCard key={index} title={`학력 ${index + 1}`} onRemove={() => setForm((prev) => ({ ...prev, education: removeAt(prev.education, index, createEducation) }))}>
                      <div className="grid gap-4 md:grid-cols-2">
                        <Field label="학교/기관명">
                          <Input maxLength={80} value={item.school} onChange={(event) => updateEducation(index, "school", event.target.value)} placeholder="예: 한국대학교" />
                        </Field>
                        <Field label="전공/과정">
                          <Input maxLength={80} value={item.major} onChange={(event) => updateEducation(index, "major", event.target.value)} placeholder="예: 경영학과, 간호학과, 직업훈련 과정" />
                        </Field>
                        <Field label="시작월">
                          <Input type="month" value={item.startDate} onChange={(event) => updateEducation(index, "startDate", event.target.value)} />
                        </Field>
                        <Field label="종료월">
                          <Input type="month" value={item.endDate} onChange={(event) => updateEducation(index, "endDate", event.target.value)} disabled={isOngoing(item.startDate, item.endDate)} />
                        </Field>
                        <CurrentCheckbox
                          label="현재 재학/진행 중"
                          checked={isOngoing(item.startDate, item.endDate)}
                          disabled={!item.startDate}
                          onChange={(checked) => updateEducation(index, "endDate", checked ? "" : currentMonth())}
                        />
                        <Field label="상태" className="md:col-span-2">
                          <select value={item.status} onChange={(event) => updateEducation(index, "status", event.target.value)} className="h-10 w-full rounded-md border border-slate-200 bg-card px-3 text-sm">
                            <option value="">선택</option>
                            <option value="재학">재학</option>
                            <option value="졸업예정">졸업예정</option>
                            <option value="졸업">졸업</option>
                            <option value="수료">수료</option>
                            <option value="중퇴">중퇴</option>
                          </select>
                        </Field>
                      </div>
                    </EntryCard>
                  ))}
                </EntrySection>
                </TabsContent>
              )}

              {activeTab === "ai" && (
                <TabsContent value="ai" className="mt-5">
                <TabGuide tab="ai" status={tabStatuses.ai} />
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="text-base">AI 분석 결과</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-5">
                    <ProfileVersionHistory
                      versions={profileVersions}
                      analyzedVersionNo={
                        summaryResult?.profileVersionNo
                        ?? skillsResult?.profileVersionNo
                        ?? completeness?.profileVersionNo
                        ?? null
                      }
                    />
                    <div className="grid gap-2 md:grid-cols-3">
                      {(["summary", "skills", "completeness"] as AiToolType[]).map((type) => (
                        <button
                          key={type}
                          type="button"
                          className={`rounded-lg border px-4 py-3 text-left transition-colors ${
                            activeAiView === type
                              ? "border-blue-500 bg-blue-50 text-blue-950"
                              : "border-slate-200 bg-card text-slate-700 hover:border-blue-200"
                          }`}
                          onClick={() => setActiveAiView(type)}
                        >
                          <div className="text-sm font-bold">{aiToolCopy[type].title}</div>
                          <div className="mt-1 text-xs leading-5 text-slate-500">{aiToolCopy[type].actionLabel}</div>
                        </button>
                      ))}
                    </div>

                    {activeAiView === "summary" && (
                      <SummaryResultPanel
                        result={summaryResult}
                        onApply={(result) => appendToSelfIntro("AI 요약 참고", [result.summary, ...result.strengths, ...result.recommendations])}
                      />
                    )}
                    {activeAiView === "skills" && <SkillsResultPanel result={skillsResult} onAddSkills={addSkills} />}
                    {activeAiView === "completeness" && (
                      <CompletenessResultPanel
                        result={completeness}
                        onApply={(result) => appendToSelfIntro("프로필 보완 메모", [...result.missing, ...result.recommendations])}
                      />
                    )}
                  </CardContent>
                </Card>
                </TabsContent>
              )}
            </Tabs>
          </section>
        </div>
      </div>
    </div>
  );
}

/** `/profile/ai-analysis` 전용 엔트리. 기존 프로필 AI 파이프라인을 AI 결과 화면에 고정해 재사용한다. */
export function ProfileAiAnalysisPage() {
  return <ProfilePage mode="ai-analysis" />;
}

function ProfileVersionHistory({
  versions,
  analyzedVersionNo,
}: {
  versions: UserProfileVersion[];
  analyzedVersionNo: number | null;
}) {
  return (
    <section className="rounded-lg border border-border bg-muted/40 p-4" aria-label="프로필 버전 이력">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="text-sm font-bold text-foreground">분석 기준 프로필 버전</div>
          <p className="mt-1 text-xs leading-5 text-muted-foreground">
            저장할 때마다 입력 스냅샷을 보존합니다. AI 결과가 어떤 프로필을 사용했는지 아래에서 대조할 수 있습니다.
          </p>
        </div>
        <Badge variant="outline" className="bg-background text-foreground">
          {analyzedVersionNo ? `최근 결과 v${analyzedVersionNo}` : versions[0] ? `현재 v${versions[0].versionNo}` : "저장 이력 없음"}
        </Badge>
      </div>
      {versions.length > 0 ? (
        <div className="mt-3 max-h-72 space-y-2 overflow-y-auto pr-1">
          {versions.map((version) => (
            <details key={version.id} className="rounded-md border border-border bg-background px-3 py-2">
              <summary className="cursor-pointer text-sm font-semibold text-foreground">
                v{version.versionNo} · {profileVersionSourceLabel(version.source)} · {formatProfileVersionDate(version.createdAt)}
                {analyzedVersionNo === version.versionNo ? " · 분석 사용" : ""}
              </summary>
              <pre className="mt-3 max-h-64 overflow-auto whitespace-pre-wrap break-words rounded-md bg-muted p-3 text-xs leading-5 text-foreground">
                {JSON.stringify({
                  desiredJob: version.desiredJob ?? null,
                  desiredIndustry: version.desiredIndustry ?? null,
                  education: version.education ?? null,
                  career: version.career ?? null,
                  projects: version.projects ?? null,
                  skills: version.skills ?? null,
                  certificates: version.certificates ?? null,
                  languages: version.languages ?? null,
                  portfolioLinks: version.portfolioLinks ?? null,
                  resumeText: version.resumeText ?? null,
                  selfIntro: version.selfIntro ?? null,
                  preferences: version.preferences ?? null,
                }, null, 2)}
              </pre>
            </details>
          ))}
        </div>
      ) : (
        <p className="mt-3 rounded-md border border-dashed border-border bg-background px-3 py-4 text-center text-sm text-muted-foreground">
          프로필을 처음 저장하면 v1 스냅샷이 생성됩니다.
        </p>
      )}
    </section>
  );
}

function profileVersionSourceLabel(source: string): string {
  if (source === "MANUAL_SAVE") return "직접 저장";
  if (source === "DOCUMENT_IMPORT") return "문서 가져오기";
  if (source === "AI_ANALYSIS") return "분석 전 기준 생성";
  if (source === "MIGRATION") return "기존 프로필 이관";
  return source;
}

function formatProfileVersionDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "short" }).format(date);
}

function AiToolButton({
  type,
  active,
  loading,
  disabled,
  icon,
  onClick,
}: {
  type: AiToolType;
  active: boolean;
  loading: boolean;
  disabled: boolean;
  icon: React.ReactNode;
  onClick(): void;
}) {
  const copy = aiToolCopy[type];
  return (
    <Button
      variant="outline"
      className={`h-auto w-full justify-start gap-3 whitespace-normal rounded-lg px-3 py-3 text-left ${
        active ? "border-blue-500 bg-blue-50 text-blue-950 hover:bg-blue-50" : ""
      }`}
      onClick={onClick}
      disabled={disabled}
    >
      <span className="mt-0.5 shrink-0 text-blue-600">{loading ? <RefreshCw className="size-4 animate-spin" /> : icon}</span>
      <span className="min-w-0 flex-1">
        <span className="block text-sm font-bold">{copy.title}</span>
        <span className="mt-1 block text-xs leading-5 text-slate-500">{copy.description}</span>
      </span>
      <span
        className={`ml-2 shrink-0 self-center rounded-md px-2.5 py-1 text-xs font-semibold ${
          disabled ? "bg-slate-200 text-slate-500" : "bg-blue-600 text-white"
        }`}
      >
        {loading ? "실행 중" : "실행"}
      </span>
    </Button>
  );
}

function SummaryResultPanel({ result, onApply }: { result: ProfileAiResponse | null; onApply(result: ProfileAiResponse): void }) {
  if (!result) {
    return (
      <AiEmptyState
        title="아직 요약 결과가 없습니다."
        description="왼쪽의 프로필 AI 요약을 실행하면 내 이력이 어떤 방향으로 읽히는지 한 문단으로 정리해 줍니다."
      />
    );
  }
  return (
    <div className="space-y-4">
      <AiMeta jobFamilyLabel={result.jobFamilyLabel} model={result.model} status={result.status} score={result.completenessScore} />
      <QualityScoreBreakdown
        finalScore={result.completenessScore}
        aiScore={result.aiScore}
        qualityPenalty={result.qualityPenalty}
        warnings={result.qualityWarnings}
        recommendations={result.qualityRecommendations}
      />
      <ResultBlock
        title="내 프로필은 이렇게 읽혀요"
        value={result.summary}
        helper="이 문장은 이력서 첫 소개나 자기소개서 방향을 잡을 때 참고하면 좋습니다."
      />
      <TagBlock title="현재 잘 드러나는 강점" values={result.strengths} />
      <ListBlock title="요약을 더 설득력 있게 만드는 방법" values={result.recommendations} />
      <Button variant="outline" onClick={() => onApply(result)}>
        자기소개 메모에 반영
      </Button>
    </div>
  );
}

function SkillsResultPanel({ result, onAddSkills }: { result: ProfileAiResponse | null; onAddSkills(skills: string[]): void }) {
  if (!result) {
    return (
      <AiEmptyState
        title="아직 추출된 역량 키워드가 없습니다."
        description="왼쪽의 역량 키워드 추출을 실행하면 이력서에 넣기 좋은 직무 키워드와 부족한 키워드를 나눠서 보여줍니다."
      />
    );
  }
  return (
    <div className="space-y-4">
      <AiMeta jobFamilyLabel={result.jobFamilyLabel} model={result.model} status={result.status} score={result.completenessScore} />
      <QualityScoreBreakdown
        finalScore={result.completenessScore}
        aiScore={result.aiScore}
        qualityPenalty={result.qualityPenalty}
        warnings={result.qualityWarnings}
        recommendations={result.qualityRecommendations}
      />
      <ResultBlock
        title="키워드 추출 기준"
        value={result.summary}
        helper="AI가 어떤 기준으로 역량 키워드를 뽑았는지 간단히 설명합니다."
      />
      <TagBlock title="이력서에 활용하기 좋은 역량 키워드" values={result.extractedSkills} />
      <TagBlock title="강점으로 강조할 수 있는 부분" values={result.strengths} />
      <TagBlock title="추가하면 좋은 보완 키워드" values={result.gaps} tone="amber" />
      <ListBlock title="키워드 보강 방법" values={result.recommendations} />
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" onClick={() => onAddSkills(result.extractedSkills)}>
          추출 키워드를 내 스킬에 추가
        </Button>
        <Button variant="outline" onClick={() => onAddSkills([...result.extractedSkills, ...result.gaps])}>
          보완 키워드까지 함께 추가
        </Button>
      </div>
    </div>
  );
}

function CompletenessResultPanel({ result, onApply }: { result: ProfileCompleteness | null; onApply(result: ProfileCompleteness): void }) {
  if (!result) {
    return (
      <AiEmptyState
        title="아직 완성도 진단 결과가 없습니다."
        description="왼쪽의 완성도 진단을 실행하면 현재 프로필에서 충분한 부분과 먼저 보완할 부분을 점수와 함께 확인할 수 있습니다."
      />
    );
  }
  return (
    <div className="space-y-4">
      <div className="rounded-lg border border-blue-100 bg-blue-50 p-4">
        <div className="text-sm font-bold text-blue-500">현재 프로필 완성도</div>
        <div className="mt-2 flex flex-wrap items-end justify-between gap-3">
          <div>
            <div className="text-4xl font-black text-blue-700">{result.score}%</div>
            <div className="mt-1 text-sm text-blue-900">{result.jobFamilyLabel ?? "직무군 분석 전"} 기준으로 계산했습니다.</div>
          </div>
          <Badge className="bg-blue-100 text-blue-700">{formatStatus(result.status)}</Badge>
        </div>
        <Progress value={result.score} className="mt-3 h-2" />
        <div className="mt-2 text-xs text-blue-700">{formatModel(result.model)}</div>
      </div>
      <QualityScoreBreakdown
        finalScore={result.score}
        aiScore={result.aiScore}
        qualityPenalty={result.qualityPenalty}
        warnings={result.qualityWarnings}
        recommendations={result.qualityRecommendations}
      />
      <div className="grid gap-4 md:grid-cols-2">
        <Checklist title="이미 잘 채워진 항목" items={result.completed} done />
        <Checklist title="먼저 보완할 항목" items={result.missing} />
      </div>
      <ListBlock title="다음에 하면 좋은 보완 작업" values={result.recommendations} />
      <CriterionScoreList values={result.criteria ?? []} />
      <Button variant="outline" onClick={() => onApply(result)}>
        보완 항목을 자기소개 메모에 추가
      </Button>
    </div>
  );
}

function ConsentStatusBox({ consent }: { consent: ConsentStatus | null }) {
  const aiAgreed = consent?.aiDataAgreed === true;
  const resumeAgreed = consent?.resumeAnalysisAgreed === true;
  const agreed = aiAgreed && resumeAgreed;
  return (
    <div className={`rounded-lg border px-3 py-3 text-sm ${agreed ? "border-green-200 bg-green-50" : "border-amber-200 bg-amber-50"}`}>
      <div className={`font-bold ${agreed ? "text-green-700" : "text-amber-800"}`}>
        프로필 AI 처리 동의: {agreed ? "사용 가능" : "동의 필요"}
      </div>
      <p className={`mt-1 text-xs leading-5 ${agreed ? "text-green-700" : "text-amber-700"}`}>
        {agreed
          ? "AI 데이터 이용 동의와 이력서 분석 동의가 모두 활성화되어 있습니다."
          : `${!aiAgreed ? "AI 데이터 이용 동의" : ""}${!aiAgreed && !resumeAgreed ? "와 " : ""}${!resumeAgreed ? "이력서 분석 동의" : ""}가 필요합니다. 수동 프로필 편집은 계속 사용할 수 있습니다.`}
      </p>
      {!agreed && (
        <Link className="mt-2 inline-flex text-xs font-bold text-blue-600 hover:underline" to={aiAgreed ? "/settings/privacy" : "/settings/ai-consent"}>
          동의 설정으로 이동
        </Link>
      )}
    </div>
  );
}

function SkillTagList({ skills, onRemove }: { skills: string[]; onRemove(skill: string): void }) {
  if (!skills.length) return null;
  return (
    <div>
      <div className="mb-2 text-xs font-bold text-slate-500">현재 등록된 역량</div>
      <div className="flex flex-wrap gap-2">
        {skills.map((skill) => (
          <button
            key={skill}
            type="button"
            className="inline-flex items-center gap-1 rounded-md bg-slate-100 px-2.5 py-1.5 text-sm font-semibold text-slate-700 hover:bg-slate-200"
            onClick={() => onRemove(skill)}
            title={`${skill} 삭제`}
          >
            {skill}
            <X className="size-3.5" />
          </button>
        ))}
      </div>
    </div>
  );
}

function CurrentCheckbox({
  label,
  checked,
  disabled,
  onChange,
}: {
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange(checked: boolean): void;
}) {
  return (
    <label className={`flex items-center gap-2 rounded-md border border-slate-200 bg-card px-3 py-2 text-sm ${disabled ? "text-slate-400" : "text-slate-700"}`}>
      <input
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(event) => onChange(event.target.checked)}
        className="size-4 rounded border-slate-300"
      />
      {label}
      {disabled && <span className="text-xs text-slate-400">시작월 입력 후 선택</span>}
    </label>
  );
}

function AiEmptyState({ title, description }: { title: string; description: string }) {
  return (
    <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-5">
      <div className="font-bold text-slate-900">{title}</div>
      <p className="mt-2 text-sm leading-6 text-slate-600">{description}</p>
    </div>
  );
}

function RequirementBadge({ requirement }: { requirement: TabRequirement }) {
  const className =
    requirement === "필수"
      ? "border-blue-200 bg-blue-50 text-blue-700"
      : requirement === "권장"
        ? "border-amber-200 bg-amber-50 text-amber-700"
        : "border-slate-200 bg-slate-50 text-slate-500";
  return <span className={`rounded-full border px-1.5 py-0.5 text-[11px] font-bold ${className}`}>{requirement}</span>;
}

function TabStatusBadge({ status }: { status: ReturnType<typeof getProfileTabStatuses>[ProfileTab] }) {
  const className =
    status.tone === "done"
      ? "bg-green-50 text-green-700"
      : status.tone === "warning"
        ? "bg-amber-50 text-amber-700"
        : "bg-slate-100 text-slate-500";
  return <span className={`rounded-full px-2 py-0.5 text-[11px] font-bold ${className}`}>{status.label}</span>;
}

function TabGuide({ tab, status }: { tab: ProfileTab; status: ReturnType<typeof getProfileTabStatuses>[ProfileTab] }) {
  const meta = profileTabMeta[tab];
  return (
    <div className="mb-4 rounded-lg border border-slate-200 bg-card px-4 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <div className="text-sm font-black text-slate-900">{meta.label}</div>
        <RequirementBadge requirement={meta.requirement} />
        <TabStatusBadge status={status} />
      </div>
      <p className="mt-2 text-sm leading-6 text-slate-600">{meta.guide}</p>
      {status.help && <p className="mt-1 text-xs font-semibold text-blue-600">{status.help}</p>}
    </div>
  );
}

/** 구조화 추출 결과 확인 카드 — 승인 필드만 폼에 반영(자동 커밋 금지). */
function AnalyzeConfirmCard({
  status,
  draft,
  error,
  onApply,
  onDismiss,
}: {
  status: "idle" | "running" | "done" | "failed";
  draft: ProfileAnalyzeDraft | null;
  error: string | null;
  onApply: (opts: {
    education: boolean;
    career: boolean;
    projects: boolean;
    skills: boolean;
    portfolioLinks: boolean;
  }) => void;
  onDismiss: () => void;
}) {
  const eduCount = Array.isArray(draft?.education) ? (draft.education as unknown[]).length : 0;
  const careerCount = Array.isArray(draft?.career) ? (draft.career as unknown[]).length : 0;
  const projectCount = Array.isArray(draft?.projects) ? (draft.projects as unknown[]).length : 0;
  const skillCount = Array.isArray(draft?.skills) ? draft.skills.length : 0;
  const linkCount = Array.isArray(draft?.portfolioLinks) ? draft.portfolioLinks.length : 0;
  const hasAny = eduCount + careerCount + projectCount + skillCount + linkCount > 0;

  const [pick, setPick] = useState(() =>
    draftPickFromCounts({
      education: eduCount,
      career: careerCount,
      projects: projectCount,
      skills: skillCount,
      portfolioLinks: linkCount,
    }),
  );

  useEffect(() => {
    setPick(
      draftPickFromCounts({
        education: eduCount,
        career: careerCount,
        projects: projectCount,
        skills: skillCount,
        portfolioLinks: linkCount,
      }),
    );
  }, [eduCount, careerCount, projectCount, skillCount, linkCount, draft]);

  return (
    <Card className="mb-4 border-blue-200 bg-blue-50/40">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center justify-between gap-2 text-base text-slate-900">
          <span className="flex items-center gap-2">
            <Sparkles className="size-4 text-blue-600" />
            이력서 추출 결과
          </span>
          <button type="button" className="rounded p-1 text-slate-500 hover:bg-white" onClick={onDismiss} aria-label="닫기">
            <X className="size-4" />
          </button>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 text-sm text-slate-700">
        {status === "running" && (
          <div className="flex items-center gap-2 text-slate-600">
            <Loader2 className="size-4 animate-spin" />
            Ollama(qwen3)로 학력·경력 분석 중… 원문은 이미 저장됐습니다. 1~2분 걸릴 수 있어요.
          </div>
        )}
        {status === "failed" && (
          <p className="text-amber-800">{error || "구조화 분석은 실패했어요. 폼을 직접 채워주세요."}</p>
        )}
        {status === "done" && !hasAny && (
          <p className="text-slate-600">추출된 구조 항목이 없습니다. 원문만 반영됐습니다. (표·스캔 PDF는 인식이 약합니다)</p>
        )}
        {status === "done" && hasAny && (
          <>
            <p className="text-slate-600">
              적용할 항목만 고르세요. 적용 후 <b>저장</b>을 눌러야 DB에 확정됩니다.
            </p>
            <div className="flex flex-col gap-2">
              {eduCount > 0 && (
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={pick.education} onChange={(e) => setPick((p) => ({ ...p, education: e.target.checked }))} />
                  학력 {eduCount}건
                </label>
              )}
              {careerCount > 0 && (
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={pick.career} onChange={(e) => setPick((p) => ({ ...p, career: e.target.checked }))} />
                  경력 {careerCount}건
                </label>
              )}
              {projectCount > 0 && (
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={pick.projects} onChange={(e) => setPick((p) => ({ ...p, projects: e.target.checked }))} />
                  프로젝트 {projectCount}건
                </label>
              )}
              {skillCount > 0 && (
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={pick.skills} onChange={(e) => setPick((p) => ({ ...p, skills: e.target.checked }))} />
                  기술 {skillCount}개
                </label>
              )}
              {linkCount > 0 && (
                <label className="flex items-center gap-2">
                  <input type="checkbox" checked={pick.portfolioLinks} onChange={(e) => setPick((p) => ({ ...p, portfolioLinks: e.target.checked }))} />
                  포트폴리오 링크 {linkCount}개
                </label>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              <Button
                size="sm"
                onClick={() => onApply(pick)}
                disabled={!pick.education && !pick.career && !pick.projects && !pick.skills && !pick.portfolioLinks}
              >
                선택한 항목 적용
              </Button>
              <Button size="sm" variant="outline" onClick={onDismiss}>
                무시
              </Button>
            </div>
          </>
        )}
      </CardContent>
    </Card>
  );
}

function Field({ label, className = "", children }: { label: string; className?: string; children: React.ReactNode }) {
  return (
    <div className={`space-y-2 ${className}`}>
      <Label className="text-sm font-semibold text-slate-700">{label}</Label>
      {children}
    </div>
  );
}

function EntrySection({ title, onAdd, children }: { title: string; onAdd(): void; children: React.ReactNode }) {
  return (
    <Card className="border-slate-200 bg-card">
      <CardHeader className="flex flex-row items-center justify-between gap-3">
        <CardTitle className="text-base">{title}</CardTitle>
        <Button variant="outline" size="sm" onClick={onAdd}>
          <Plus className="size-4" />
          추가
        </Button>
      </CardHeader>
      <CardContent className="space-y-4">{children}</CardContent>
    </Card>
  );
}

function EntryCard({ title, onRemove, children }: { title: string; onRemove(): void; children: React.ReactNode }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="text-sm font-bold text-slate-800">{title}</div>
        <Button variant="ghost" size="sm" onClick={onRemove}>
          <Trash2 className="size-4 text-red-500" />
          삭제
        </Button>
      </div>
      {children}
    </div>
  );
}

function StatusBox({ tone, text }: { tone: "success" | "error" | "warning"; text: string }) {
  const cls =
    tone === "success"
      ? "border-green-200 bg-green-50 text-green-700"
      : tone === "warning"
        ? "border-amber-200 bg-amber-50 text-amber-800"
        : "border-red-200 bg-red-50 text-red-700";
  return <div className={`rounded-lg border px-4 py-3 text-sm ${cls}`}>{text}</div>;
}

function Checklist({ title, items, done = false }: { title: string; items: string[]; done?: boolean }) {
  if (items.length === 0) return null;
  const Icon = done ? CheckCircle2 : AlertCircle;
  return (
    <div className="space-y-2">
      <div className="text-xs font-bold text-slate-500">{title}</div>
      {items.map((item) => (
        <div key={item} className="flex items-center gap-2 text-sm text-slate-700">
          <Icon className={`size-4 ${done ? "text-green-500" : "text-amber-500"}`} />
          {item}
        </div>
      ))}
    </div>
  );
}

function ResultBlock({ title, value, helper }: { title: string; value: string; helper?: string }) {
  return (
    <div>
      <div className="mb-2 text-xs font-bold text-slate-500">{title}</div>
      <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm leading-6 text-slate-700">{value || "-"}</p>
      {helper && <p className="mt-2 text-xs leading-5 text-slate-500">{helper}</p>}
    </div>
  );
}

function TagBlock({ title, values, tone = "blue" }: { title: string; values: string[]; tone?: "blue" | "amber" }) {
  if (!values.length) return null;
  const cls = tone === "amber" ? "bg-amber-100 text-amber-700" : "bg-blue-100 text-blue-700";
  return (
    <div>
      <div className="mb-2 text-xs font-bold text-slate-500">{title}</div>
      <div className="flex flex-wrap gap-2">
        {values.map((value) => (
          <Badge key={value} className={cls}>{value}</Badge>
        ))}
      </div>
    </div>
  );
}

/**
 * 「입력 틀 추가」는 파일 추출이 아니다. 기존 구현은 클릭마다 template 을 append 해서
 * 같은 안내 문단이 여러 번 쌓였다. 빈 칸이면 1회 삽입, 이미 틀이 있으면 중복 제거/유지.
 */
function countTemplateCopies(text: string, template: string): number {
  const body = text ?? "";
  const marker = template.trim().slice(0, 40);
  if (!marker || !body.includes(marker)) return 0;
  let count = 0;
  let from = 0;
  while (from < body.length) {
    const idx = body.indexOf(marker, from);
    if (idx < 0) break;
    count += 1;
    from = idx + marker.length;
  }
  return count;
}

function insertGuideTemplate(
  current: string,
  template: string,
  copy: { alreadyHas: string; appendConfirm: string },
): string | null {
  const trimmed = (current ?? "").trim();
  const tpl = template.trim();
  const copies = countTemplateCopies(trimmed, tpl);

  // 틀이 2번 이상 붙어 있으면 한 덩어리로 정리(사용자 작성 문장 앞에 틀이 여러 번인 경우)
  if (copies >= 2) {
    // 첫 틀 위치 이전 사용자 텍스트 + 틀 1개 + 마지막 틀 이후 텍스트는 보수적으로 틀만 1회로
    const marker = tpl.slice(0, 40);
    const first = trimmed.indexOf(marker);
    const prefix = first > 0 ? trimmed.slice(0, first).trim() : "";
    // 모든 틀 블록 제거 후 접두 + 틀 1회
    let rest = trimmed;
    while (rest.includes(marker)) {
      const i = rest.indexOf(marker);
      // template 전체 길이에 가깝게 잘라 냄
      const end = i + tpl.length;
      const before = rest.slice(0, i);
      const after = rest.slice(Math.min(end, rest.length));
      rest = (before + after).replace(/\n{3,}/g, "\n\n").trim();
    }
    const userOnly = [prefix, rest].filter(Boolean).join("\n\n").trim();
    return userOnly ? `${userOnly}\n\n${tpl}` : tpl;
  }

  if (!trimmed) {
    return tpl;
  }
  if (copies === 1) {
    // 이미 한 번 있음 — 또 붙이지 않음
    return null;
  }
  // 사용자 본문만 있을 때: 확인 후 뒤에 1회 append
  if (typeof window !== "undefined" && !window.confirm(copy.appendConfirm)) {
    return null;
  }
  return `${trimmed}\n\n${tpl}`;
}

function ListBlock({ title, values }: { title: string; values: string[] }) {
  if (!values.length) return null;
  return (
    <div>
      <div className="mb-2 text-xs font-bold text-slate-500">{title}</div>
      <ul className="space-y-1 text-sm leading-6 text-slate-700">
        {values.map((value) => (
          <li key={value} className="flex gap-2">
            <span className="mt-2 size-1.5 shrink-0 rounded-full bg-blue-400" />
            <span>{value}</span>
          </li>
        ))}
      </ul>
    </div>
  );
}

function AiMeta({ jobFamilyLabel, model, status, score }: { jobFamilyLabel?: string; model?: string; status?: string; score?: number }) {
  return (
    <div className="grid gap-3 rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm md:grid-cols-4">
      <div>
        <div className="text-xs font-bold text-blue-500">AI가 판단한 직무군</div>
        <div className="mt-1 font-bold text-blue-950">{jobFamilyLabel ?? "-"}</div>
      </div>
      <div>
        <div className="text-xs font-bold text-blue-500">참고 점수</div>
        <div className="mt-1 font-bold text-blue-950">{score ?? 0}점</div>
      </div>
      <div>
        <div className="text-xs font-bold text-blue-500">분석 방식</div>
        <div className="mt-1 font-bold text-blue-950">{formatModel(model)}</div>
      </div>
      <div>
        <div className="text-xs font-bold text-blue-500">처리 상태</div>
        <div className="mt-1 font-bold text-blue-950">{formatStatus(status)}</div>
      </div>
    </div>
  );
}

function QualityScoreBreakdown({
  finalScore,
  aiScore,
  qualityPenalty,
  warnings,
  recommendations,
}: {
  finalScore: number;
  aiScore?: number;
  qualityPenalty?: number;
  warnings?: string[];
  recommendations?: string[];
}) {
  const penalty = qualityPenalty ?? 0;
  const hasQualityNotes = (warnings?.length ?? 0) > 0 || (recommendations?.length ?? 0) > 0;
  if (aiScore == null && penalty <= 0 && !hasQualityNotes) {
    return null;
  }

  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
      <div className="text-sm font-bold text-amber-900">점수 산정 방식</div>
      <div className="mt-3 grid gap-3 text-sm md:grid-cols-3">
        <div className="rounded-md bg-white/70 p-3">
          <div className="text-xs font-bold text-amber-700">AI 내용 평가</div>
          <div className="mt-1 text-xl font-black text-slate-900">{aiScore ?? finalScore}점</div>
        </div>
        <div className="rounded-md bg-white/70 p-3">
          <div className="text-xs font-bold text-amber-700">입력 품질 보정</div>
          <div className="mt-1 text-xl font-black text-slate-900">{penalty > 0 ? `-${penalty}점` : "0점"}</div>
        </div>
        <div className="rounded-md bg-white/70 p-3">
          <div className="text-xs font-bold text-amber-700">최종 참고 점수</div>
          <div className="mt-1 text-xl font-black text-blue-700">{finalScore}점</div>
        </div>
      </div>
      {hasQualityNotes && (
        <div className="mt-3 grid gap-3 md:grid-cols-2">
          <ListBlock title="감점 또는 보정 사유" values={warnings ?? []} />
          <ListBlock title="점수를 올리려면" values={recommendations ?? []} />
        </div>
      )}
      <p className="mt-3 text-xs leading-5 text-amber-800">
        AI가 먼저 내용을 평가하고, 서버가 무의미한 입력·직무 관련성·성과 근거 부족 여부를 다시 확인해 최종 점수에 반영합니다.
      </p>
    </div>
  );
}

function CriterionScoreList({ values }: { values: NonNullable<ProfileAiResponse["criteria"]> }) {
  if (!values.length) return null;
  return (
    <div>
      <div className="mb-2 text-xs font-bold text-slate-500">항목별 진단</div>
      <div className="space-y-3">
        {values.map((item) => (
          <div key={item.criterion} className="rounded-lg border border-slate-200 bg-card p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="font-bold text-slate-900">{item.label}</div>
              <Badge className="bg-slate-100 text-slate-700">
                현재 {item.rawScore}점 · 중요도 {item.weight}%
              </Badge>
            </div>
            <Progress value={item.rawScore} className="mt-2 h-2" />
            {item.evidence && (
              <p className="mt-2 text-sm leading-6 text-slate-600">
                <span className="font-semibold text-slate-800">판단 근거: </span>
                {item.evidence}
              </p>
            )}
            {item.improvement && (
              <p className="mt-1 text-sm leading-6 text-amber-700">
                <span className="font-semibold">보완 방법: </span>
                {item.improvement}
              </p>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

function formatStatus(status?: string): string {
  if (status === "SUCCESS") return "완료";
  if (status === "FALLBACK") return "기본 분석으로 완료";
  if (status === "FAILED") return "실패";
  return status ?? "-";
}

function formatModel(model?: string): string {
  if (!model) return "-";
  if (model.includes("qwen3-profile-lora")) return "맞춤 AI 분석";
  if (model.includes("mock")) return "화면 미리보기 데이터";
  if (model.includes("rule")) return "기본 분석";
  if (model.toLowerCase().includes("openai")) return "AI 분석";
  return model;
}

function describeCompletenessScore(score?: number): string {
  if (score == null) return "프로필을 저장하면 완성도와 보완 항목을 확인할 수 있습니다.";
  if (score >= 85) return "지원에 필요한 정보가 잘 정리되어 있습니다. 성과 근거만 더 다듬으면 좋습니다.";
  if (score >= 70) return "기본 정보는 충분합니다. 직무와 연결되는 성과와 구체적인 사례를 보강해 주세요.";
  if (score >= 50) return "핵심 정보가 일부 부족합니다. 경력, 활동, 스킬을 더 구체적으로 채우는 것이 좋습니다.";
  return "아직 AI가 판단할 정보가 부족합니다. 희망 직무, 이력서 원문, 주요 경험부터 입력해 주세요.";
}

function getProfileTabStatuses(
  form: ProfileForm,
  summaryResult: ProfileAiResponse | null,
  skillsResult: ProfileAiResponse | null,
  completeness: ProfileCompleteness | null,
): Record<ProfileTab, { label: string; tone: TabStatusTone; help?: string }> {
  const hasBasicCore = hasText(form.desiredJob) && hasText(form.desiredIndustry);
  const hasBasicDetail = hasText(form.preferences.region) || hasText(form.preferences.workType) || hasText(form.preferences.employmentType) || hasText(form.preferences.salary);
  const hasCareer = form.career.some((item) => hasText(item.company) || hasText(item.role) || hasText(item.tasks) || hasText(item.achievements));
  const hasExperience = form.experiences.some((item) => hasText(item.title) || hasText(item.role) || hasText(item.description) || hasText(item.result));
  const hasEducation = form.education.some((item) => hasText(item.school) || hasText(item.major));
  const hasAiResult = Boolean(summaryResult || skillsResult || completeness);

  return {
    basic: hasBasicCore
      ? { label: hasBasicDetail ? "완료" : "기본 완료", tone: "done", help: hasBasicDetail ? undefined : "희망 지역이나 근무 조건을 추가하면 매칭 기준이 더 명확해집니다." }
      : { label: "미작성", tone: "empty", help: "희망 직무와 희망 산업은 먼저 입력하는 것이 좋습니다." },
    resume: hasText(form.resumeText, 80)
      ? { label: "완료", tone: "done" }
      : hasText(form.resumeText)
        ? { label: "부족", tone: "warning", help: "이력서 원문이 너무 짧습니다. 담당 업무와 성과를 함께 적어 주세요." }
        : { label: "미작성", tone: "empty", help: "AI 분석 품질을 위해 이력서 원문을 입력해 주세요." },
    selfIntro: hasText(form.selfIntro, 120)
      ? { label: "완료", tone: "done" }
      : hasText(form.selfIntro)
        ? { label: "보완 권장", tone: "warning", help: "지원동기, 직무 경험, 입사 후 포부를 나눠 적으면 좋습니다." }
        : { label: "권장 작성", tone: "empty", help: "자기소개서는 필수는 아니지만 지원서 설득력을 높입니다." },
    experience: hasCareer && hasExperience
      ? { label: "완료", tone: "done" }
      : hasCareer || hasExperience || hasText(form.portfolioLinksText)
        ? { label: "일부 작성", tone: "warning", help: "경력과 프로젝트 중 비어 있는 항목을 보강해 주세요." }
        : { label: "미작성", tone: "empty", help: "성과와 역할을 판단하려면 경력 또는 프로젝트 정보가 필요합니다." },
    skills: linesToArray(form.skillsText).length >= 3
      ? { label: "완료", tone: "done" }
      : linesToArray(form.skillsText).length > 0
        ? { label: "부족", tone: "warning", help: "희망 직무와 연결되는 역량을 3개 이상 적어 주세요." }
        : { label: "미작성", tone: "empty", help: "직무 역량이나 사용 가능한 도구를 한 줄씩 입력해 주세요." },
    certificates: hasText(form.certificatesText) || hasText(form.languagesText) || hasEducation
      ? { label: "작성됨", tone: "done" }
      : { label: "선택", tone: "empty", help: "해당되는 자격, 어학, 학력이 있다면 추가해 주세요." },
    ai: hasAiResult
      ? { label: "결과 있음", tone: "done" }
      : { label: "실행 전", tone: "empty", help: "프로필을 저장한 뒤 AI 요약, 역량 추출, 완성도 진단을 실행할 수 있습니다." },
  };
}

function hasText(value: string | null | undefined, minLength = 1): boolean {
  return (value ?? "").trim().length >= minLength;
}

function serializeProfileForm(form: ProfileForm): string {
  return JSON.stringify(toRequest(form));
}

/** 서버 최신 기준본에 import 도중 생긴 로컬 편집만 다시 얹는다. */
function mergeServerProfileForm(
  local: ProfileForm,
  baseline: ProfileForm,
  server: ProfileForm,
  localAtImportStart: ProfileForm,
  importedField: keyof ProfileForm,
): { form: ProfileForm; conflicted: boolean } {
  const merged: ProfileForm = { ...server };
  let conflicted = false;
  for (const key of Object.keys(emptyForm) as (keyof ProfileForm)[]) {
    // 사용자가 '기존 내용 대체'를 확인한 import 대상은 서버 결과를 채택한다. 다만 요청 대기 중
    // 새로 편집한 값은 보존한다. 비대상 필드는 저장 기준본 이후의 모든 로컬 편집을 보존한다.
    const comparison = key === importedField ? localAtImportStart[key] : baseline[key];
    const localChanged = JSON.stringify(local[key]) !== JSON.stringify(comparison);
    const serverChanged = JSON.stringify(server[key]) !== JSON.stringify(baseline[key]);
    if (localChanged) {
      Object.assign(merged, { [key]: local[key] });
    }
    if (localChanged && serverChanged && JSON.stringify(local[key]) !== JSON.stringify(server[key])) {
      conflicted = true;
    }
  }
  return { form: merged, conflicted };
}

function mergeUniqueLines(currentText: string, nextItems: string[]): string[] {
  const seen = new Set<string>();
  const merged: string[] = [];
  for (const item of [...linesToArray(currentText), ...nextItems]) {
    const trimmed = item.trim();
    const key = trimmed.toLowerCase();
    if (!trimmed || seen.has(key)) continue;
    seen.add(key);
    merged.push(trimmed);
  }
  return merged;
}

function isOngoing(startDate: string, endDate: string): boolean {
  return Boolean(startDate && !endDate);
}

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function formatFileSize(size: number | null | undefined): string {
  if (size == null || size <= 0) return "크기 정보 없음";
  if (size >= 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  return `${Math.max(1, Math.round(size / 1024))} KB`;
}

function toForm(profile: UserProfile): ProfileForm {
  return {
    desiredJob: profile.desiredJob ?? "",
    desiredIndustry: profile.desiredIndustry ?? "",
    skillsText: arrayToLines(profile.skills),
    certificatesText: arrayToLines(profile.certificates),
    languagesText: arrayToLines(profile.languages),
    portfolioLinksText: arrayToLines(profile.portfolioLinks),
    education: parseEntries(profile.education, createEducation),
    career: parseEntries(profile.career, createCareer),
    experiences: parseEntries(profile.projects, createExperience),
    preferences: parsePreferences(profile.preferences),
    resumeText: profile.resumeText ?? "",
    selfIntro: profile.selfIntro ?? "",
  };
}

function toRequest(form: ProfileForm, versionNo?: number | null): UserProfile {
  return {
    desiredJob: blankToNull(form.desiredJob),
    desiredIndustry: blankToNull(form.desiredIndustry),
    skills: linesToArray(form.skillsText),
    certificates: linesToArray(form.certificatesText),
    languages: linesToArray(form.languagesText),
    portfolioLinks: linesToArray(form.portfolioLinksText),
    education: stripEmpty(form.education),
    career: stripEmpty(form.career),
    projects: stripEmpty(form.experiences),
    preferences: stripEmptyObject(form.preferences),
    resumeText: blankToNull(form.resumeText),
    selfIntro: blankToNull(form.selfIntro),
    ...(versionNo == null ? {} : { versionNo }),
  };
}

function updateList<T extends object>(items: T[], index: number, key: keyof T, value: string): T[] {
  return items.map((item, itemIndex) => (itemIndex === index ? ({ ...item, [key]: value } as T) : item));
}

function removeAt<T>(items: T[], index: number, createEmpty: () => T): T[] {
  const next = items.filter((_, itemIndex) => itemIndex !== index);
  return next.length ? next : [createEmpty()];
}

function parseEntries<T extends object>(value: unknown, createEmpty: () => T): T[] {
  const parsed = normalizeUnknown(value);
  if (!Array.isArray(parsed)) return [createEmpty()];
  const entries = parsed
    .filter((item): item is Record<string, unknown> => item !== null && typeof item === "object" && !Array.isArray(item))
    .map((item) => {
      const next = { ...createEmpty() };
      for (const key of Object.keys(next) as Array<keyof T>) {
        next[key] = String(item[key as string] ?? "") as T[keyof T];
      }
      if ("period" in item && "startDate" in next && "endDate" in next) {
        const [startDate, endDate] = splitPeriod(String(item.period ?? ""));
        next.startDate = String(item.startDate ?? startDate) as T[keyof T];
        next.endDate = String(item.endDate ?? endDate) as T[keyof T];
      }
      return next;
    });
  return entries.length ? entries : [createEmpty()];
}

function parsePreferences(value: unknown): PreferencesForm {
  const parsed = normalizeUnknown(value);
  const next = createPreferences();
  if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
    const source = parsed as Record<string, unknown>;
    next.region = String(source.region ?? "");
    next.workType = String(source.workType ?? "");
    next.salary = String(source.salary ?? "");
    next.employmentType = String(source.employmentType ?? "");
  }
  return next;
}

function normalizeUnknown(value: unknown): unknown {
  if (typeof value !== "string") return value;
  try {
    return JSON.parse(value);
  } catch {
    return value;
  }
}

function stripEmpty<T extends object>(items: T[]): T[] {
  return items
    .map((item) => {
      const next = { ...item } as Record<string, string>;
      for (const key of Object.keys(next)) next[key] = String(next[key] ?? "").trim();
      if ("startDate" in next || "endDate" in next) {
        next.period = formatPeriod(next.startDate, next.endDate);
      }
      return next as T;
    })
    .filter((item) => Object.values(item as Record<string, string>).some((value) => value.trim().length > 0));
}

function stripEmptyObject<T extends object>(value: T): Partial<T> | null {
  const next: Partial<T> = {};
  for (const key of Object.keys(value) as Array<keyof T>) {
    const trimmed = String(value[key] ?? "").trim();
    if (trimmed) next[key] = trimmed as T[keyof T];
  }
  return Object.keys(next).length ? next : null;
}

function linesToArray(text: string): string[] {
  return text.split(/\r?\n/).map((item) => item.trim()).filter(Boolean);
}

function arrayToLines(value: unknown): string {
  const parsed = normalizeUnknown(value);
  if (Array.isArray(parsed)) return parsed.map(String).join("\n");
  if (typeof parsed === "string") return parsed;
  return "";
}

function blankToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

// 탭 저장 시 함께 전송할 필드 — 각 탭이 편집하는 필드와 1:1.
const TAB_FIELDS: Record<Exclude<ProfileTab, "ai">, (keyof ProfileForm)[]> = {
  basic: ["desiredJob", "desiredIndustry", "preferences"],
  resume: ["resumeText"],
  selfIntro: ["selfIntro"],
  skills: ["skillsText"],
  experience: ["career", "experiences", "portfolioLinksText"],
  certificates: ["certificatesText", "languagesText", "education"],
};

function pickTabFields(form: ProfileForm, tab: Exclude<ProfileTab, "ai">): Partial<ProfileForm> {
  const picked: Partial<ProfileForm> = {};
  for (const key of TAB_FIELDS[tab]) {
    (picked as Record<string, unknown>)[key] = form[key];
  }
  return picked;
}

function validateProfileTab(form: ProfileForm, tab: ProfileTab): string | null {
  switch (tab) {
    case "basic":
      if (!form.desiredJob.trim()) return "희망 직무는 필수입니다.";
      if (form.desiredJob.trim().length > 80 || form.desiredIndustry.trim().length > 80) {
        return "희망 직무와 산업은 80자 이하로 입력해 주세요.";
      }
      return null;
    case "resume":
      return form.resumeText.length > RESUME_TEXT_MAX_LENGTH
        ? `이력서 원문이 ${form.resumeText.length.toLocaleString("ko-KR")}자입니다. ${RESUME_TEXT_MAX_LENGTH.toLocaleString("ko-KR")}자 이하로 줄여 주세요.`
        : null;
    case "selfIntro":
      return form.selfIntro.length > SELF_INTRO_MAX_LENGTH
        ? `자기소개가 ${form.selfIntro.length.toLocaleString("ko-KR")}자입니다. ${SELF_INTRO_MAX_LENGTH.toLocaleString("ko-KR")}자 이하로 줄여 주세요.`
        : null;
    case "experience": {
      if (form.career.some((item) => !isValidRange(item.startDate, item.endDate))) {
        return "경력 종료월은 시작월보다 빠를 수 없습니다.";
      }
      if (form.experiences.some((item) => !isValidRange(item.startDate, item.endDate))) {
        return "경험/프로젝트/활동 종료월은 시작월보다 빠를 수 없습니다.";
      }
      return null;
    }
    case "certificates":
      return form.education.some((item) => !isValidRange(item.startDate, item.endDate))
        ? "학력 종료월은 시작월보다 빠를 수 없습니다."
        : null;
    default:
      return null;
  }
}

function validateProfile(form: ProfileForm): { tab: ProfileTab; message: string } | null {
  for (const tab of profileTabValues) {
    const message = validateProfileTab(form, tab);
    if (message) return { tab, message };
  }
  return null;
}

function isValidRange(startDate: string, endDate: string): boolean {
  if (!startDate || !endDate) return true;
  return startDate <= endDate;
}

function splitPeriod(period: string): [string, string] {
  const matches = period.match(/\d{4}[.-]\d{1,2}/g);
  if (!matches?.length) return ["", ""];
  const normalized = matches.map((value) => {
    const [year, month] = value.replace(".", "-").split("-");
    return `${year}-${month.padStart(2, "0")}`;
  });
  return [normalized[0] ?? "", normalized[1] ?? ""];
}

function formatPeriod(startDate: string, endDate: string): string {
  if (startDate && endDate) return `${startDate} - ${endDate}`;
  if (startDate) return `${startDate} - 현재`;
  if (endDate) return endDate;
  return "";
}
