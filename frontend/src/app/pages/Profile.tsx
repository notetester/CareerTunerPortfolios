import { useEffect, useMemo, useState } from "react";
import { AlertCircle, Brain, CheckCircle2, FileText, Plus, RefreshCw, Save, Sparkles, Trash2, User, X } from "lucide-react";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Progress } from "../components/ui/progress";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "../components/ui/tabs";
import { Textarea } from "../components/ui/textarea";
import {
  diagnoseProfileCompleteness,
  extractProfileSkills,
  getProfile,
  saveProfile,
  summarizeProfile,
  type ProfileAiResponse,
  type ProfileCompleteness,
  type UserProfile,
} from "../profile/profileApi";
import { getMyConsents, type ConsentStatus } from "../auth/consentApi";

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
  preferredRegionsText: string;
  workTypesText: string;
  employmentTypesText: string;
  desiredSalaryMin: string;
  desiredSalaryMax: string;
  availableStartDate: string;
}

interface ProfileForm {
  loginId: string;
  phoneNumber: string;
  nickname: string;
  militaryStatus: string;
  veteranStatus: string;
  disabilityStatus: string;
  chatNicknamesText: string;
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
const createPreferences = (): PreferencesForm => ({
  region: "",
  workType: "",
  salary: "",
  employmentType: "",
  preferredRegionsText: "",
  workTypesText: "",
  employmentTypesText: "",
  desiredSalaryMin: "",
  desiredSalaryMax: "",
  availableStartDate: "",
});

const emptyForm: ProfileForm = {
  loginId: "",
  phoneNumber: "",
  nickname: "",
  militaryStatus: "",
  veteranStatus: "",
  disabilityStatus: "",
  chatNicknamesText: "",
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

const resumeTemplate = `담당 업무:
- 

사용 도구/업무 방식:
- 

성과 또는 개선 결과:
- 

배운 점:
- `;

const selfIntroTemplate = `지원 직무와 연결되는 강점:

대표 경험:

성과와 배운 점:

앞으로 보완하고 싶은 부분:
`;

export function ProfilePage() {
  const [form, setForm] = useState<ProfileForm>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [activeTab, setActiveTab] = useState("basic");
  const [activeAiView, setActiveAiView] = useState<AiToolType>("summary");
  const [aiLoading, setAiLoading] = useState<AiToolType | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [summaryResult, setSummaryResult] = useState<ProfileAiResponse | null>(null);
  const [skillsResult, setSkillsResult] = useState<ProfileAiResponse | null>(null);
  const [completeness, setCompleteness] = useState<ProfileCompleteness | null>(null);
  const [consent, setConsent] = useState<ConsentStatus | null>(null);
  const [savedSnapshot, setSavedSnapshot] = useState(() => serializeProfileForm(emptyForm));

  const skillItems = useMemo(() => linesToArray(form.skillsText), [form.skillsText]);
  const selectedSkillSet = useMemo(() => new Set(skillItems.map((item) => item.toLowerCase())), [skillItems]);
  const isDirty = useMemo(() => serializeProfileForm(form) !== savedSnapshot, [form, savedSnapshot]);
  const aiConsentAgreed = consent?.aiDataAgreed === true;

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const [profile, consentStatus] = await Promise.all([getProfile(), getMyConsents().catch(() => null)]);
      const nextForm = toForm(profile);
      setForm(nextForm);
      setSavedSnapshot(serializeProfileForm(nextForm));
      setConsent(consentStatus);
      setCompleteness(await diagnoseProfileCompleteness().catch(() => null));
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로필을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

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

  const save = async (showSuccess = true): Promise<boolean> => {
    setSaving(true);
    setError(null);
    if (showSuccess) setMessage(null);
    try {
      const validationError = validateProfile(form);
      if (validationError) {
        setError(validationError);
        return false;
      }
      await saveProfile(toRequest(form));
      setSavedSnapshot(serializeProfileForm(form));
      const nextCompleteness = await diagnoseProfileCompleteness().catch(() => null);
      setCompleteness(nextCompleteness);
      if (showSuccess) setMessage("프로필이 저장되었습니다.");
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로필 저장에 실패했습니다.");
      return false;
    } finally {
      setSaving(false);
    }
  };

  const runAi = async (type: AiToolType, options: { saveBeforeRun?: boolean } = {}) => {
    if (!aiConsentAgreed) {
      setError("AI 데이터 동의가 꺼져 있어 AI 분석을 실행할 수 없습니다. 설정에서 AI 데이터 활용 동의를 켜 주세요.");
      return;
    }
    if (isDirty && !options.saveBeforeRun) {
      setActiveAiView(type);
      setError("저장하지 않은 변경사항이 있습니다. 저장 후 분석을 누르면 최신 프로필 기준으로 AI 분석을 실행합니다.");
      return;
    }
    if (isDirty && options.saveBeforeRun) {
      const saved = await save(false);
      if (!saved) return;
    }
    setActiveAiView(type);
    setActiveTab("ai");
    setAiLoading(type);
    setError(null);
    setMessage(null);
    try {
      if (type === "summary") {
        setSummaryResult(await summarizeProfile());
        setMessage("프로필 핵심 요약을 생성했습니다. AI 결과 탭에서 확인해 주세요.");
      } else if (type === "skills") {
        setSkillsResult(await extractProfileSkills());
        setMessage("이력에서 직무 역량 키워드를 추출했습니다. AI 결과 탭에서 확인해 주세요.");
      } else {
        setCompleteness(await diagnoseProfileCompleteness());
        setMessage("프로필 완성도와 보완 우선순위를 진단했습니다. AI 결과 탭에서 확인해 주세요.");
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
    setActiveTab("skills");
    setMessage("선택한 역량 키워드를 내 스킬 목록에 추가했습니다. 저장을 눌러 반영해 주세요.");
  };

  const removeSkill = (skill: string) => {
    update("skillsText", arrayToLines(skillItems.filter((item) => item !== skill)));
  };

  const appendToSelfIntro = (title: string, values: string[]) => {
    const cleaned = values.map((value) => value.trim()).filter(Boolean);
    if (!cleaned.length) return;
    const block = [`[${title}]`, ...cleaned.map((value) => `- ${value}`)].join("\n");
    update("selfIntro", [form.selfIntro.trim(), block].filter(Boolean).join("\n\n"));
    setActiveTab("resume");
    setMessage("AI 결과를 자기소개/강점 메모에 추가했습니다. 저장을 눌러 반영해 주세요.");
  };

  const insertResumeTemplate = () => {
    update("resumeText", [form.resumeText.trim(), resumeTemplate].filter(Boolean).join("\n\n"));
  };

  const insertSelfIntroTemplate = () => {
    update("selfIntro", [form.selfIntro.trim(), selfIntroTemplate].filter(Boolean).join("\n\n"));
  };

  return (
    <div className="min-h-screen bg-slate-50">
      <div className="mx-auto w-full max-w-[1280px] space-y-6 px-4 py-8 sm:px-6">
        <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
          <div>
            <h1 className="flex items-center gap-2 text-2xl font-black text-slate-950">
              <User className="size-6 text-blue-600" />
              프로필/이력서 관리
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              지원서 분석과 AI 추천에 사용할 기본 프로필, 이력서, 직무 역량을 관리합니다.
            </p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => void load()} disabled={loading}>
              <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} />
              새로고침
            </Button>
            <Button onClick={() => void save()} disabled={saving} className="bg-blue-600 text-white hover:bg-blue-700">
              <Save className="size-4" />
              {saving ? "저장 중..." : "저장"}
            </Button>
          </div>
        </div>

        {error && <StatusBox tone="error" text={error} />}
        {message && <StatusBox tone="success" text={message} />}
        {isDirty && (
          <StatusBox tone="warning" text="저장하지 않은 변경사항이 있습니다. AI 분석은 저장된 프로필 기준으로 실행되므로, 분석 전 저장을 권장합니다." />
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
                  <Button variant="outline" className="w-full justify-start border-amber-200 bg-amber-50 text-amber-800 hover:bg-amber-100" onClick={() => void save()}>
                    <Save className="size-4" />
                    저장 후 분석 준비
                  </Button>
                )}
                <AiToolButton
                  type="summary"
                  active={activeAiView === "summary"}
                  loading={aiLoading === "summary"}
                  disabled={!!aiLoading || !aiConsentAgreed}
                  icon={<Sparkles className="size-4" />}
                  onClick={() => void runAi("summary", { saveBeforeRun: isDirty })}
                />
                <AiToolButton
                  type="skills"
                  active={activeAiView === "skills"}
                  loading={aiLoading === "skills"}
                  disabled={!!aiLoading || !aiConsentAgreed}
                  icon={<Brain className="size-4" />}
                  onClick={() => void runAi("skills", { saveBeforeRun: isDirty })}
                />
                <AiToolButton
                  type="completeness"
                  active={activeAiView === "completeness"}
                  loading={aiLoading === "completeness"}
                  disabled={!!aiLoading || !aiConsentAgreed}
                  icon={<CheckCircle2 className="size-4" />}
                  onClick={() => void runAi("completeness", { saveBeforeRun: isDirty })}
                />
              </CardContent>
            </Card>
          </aside>

          <section className="space-y-5">
            <Tabs value={activeTab} onValueChange={setActiveTab}>
              <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-card p-1">
                <TabsTrigger value="account">계정</TabsTrigger>
                <TabsTrigger value="basic">기본</TabsTrigger>
                <TabsTrigger value="resume">이력서</TabsTrigger>
                <TabsTrigger value="skills">직무 역량</TabsTrigger>
                <TabsTrigger value="history">학력/경력/활동</TabsTrigger>
                <TabsTrigger value="ai">AI 결과</TabsTrigger>
              </TabsList>

              <TabsContent value="account" className="mt-5">
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="text-base">계정/연락처</CardTitle>
                  </CardHeader>
                  <CardContent className="grid gap-4 md:grid-cols-2">
                    <Field label="아이디">
                      <Input maxLength={40} value={form.loginId} onChange={(event) => update("loginId", event.target.value)} placeholder="로그인/식별용 아이디" />
                    </Field>
                    <Field label="전화번호">
                      <Input maxLength={30} value={form.phoneNumber} onChange={(event) => update("phoneNumber", event.target.value)} placeholder="예: 010-1234-5678" />
                    </Field>
                    <Field label="기본 닉네임">
                      <Input maxLength={40} value={form.nickname} onChange={(event) => update("nickname", event.target.value)} placeholder="커뮤니티와 채팅에서 사용할 대표 닉네임" />
                    </Field>
                    <Field label="병역">
                      <select value={form.militaryStatus} onChange={(event) => update("militaryStatus", event.target.value)} className="h-10 w-full rounded-md border border-slate-200 bg-card px-3 text-sm">
                        <option value="">선택 안 함</option>
                        <option value="해당 없음">해당 없음</option>
                        <option value="군필">군필</option>
                        <option value="미필">미필</option>
                        <option value="면제">면제</option>
                        <option value="복무 중">복무 중</option>
                      </select>
                    </Field>
                    <Field label="보훈 여부">
                      <select value={form.veteranStatus} onChange={(event) => update("veteranStatus", event.target.value)} className="h-10 w-full rounded-md border border-slate-200 bg-card px-3 text-sm">
                        <option value="">선택 안 함</option>
                        <option value="대상 아님">대상 아님</option>
                        <option value="대상">대상</option>
                      </select>
                    </Field>
                    <Field label="장애 여부">
                      <select value={form.disabilityStatus} onChange={(event) => update("disabilityStatus", event.target.value)} className="h-10 w-full rounded-md border border-slate-200 bg-card px-3 text-sm">
                        <option value="">선택 안 함</option>
                        <option value="해당 없음">해당 없음</option>
                        <option value="해당">해당</option>
                      </select>
                    </Field>
                    <Field label="채팅용 닉네임 프로필" className="md:col-span-2">
                      <Textarea value={form.chatNicknamesText} onChange={(event) => update("chatNicknamesText", event.target.value)} placeholder="채팅방에서 선택할 닉네임을 한 줄에 하나씩 입력" rows={4} />
                    </Field>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="basic" className="mt-5">
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
                    <Field label="상세 희망 지역">
                      <Textarea value={form.preferences.preferredRegionsText} onChange={(event) => updatePreferences("preferredRegionsText", event.target.value)} placeholder="서울 강남권, 경기 남부, 원격 가능 등 한 줄에 하나씩 입력" rows={4} />
                    </Field>
                    <Field label="상세 근무 형태">
                      <Textarea value={form.preferences.workTypesText} onChange={(event) => updatePreferences("workTypesText", event.target.value)} placeholder="재택 병행, 탄력근무, 교대 불가 등 한 줄에 하나씩 입력" rows={4} />
                    </Field>
                    <Field label="상세 고용 형태">
                      <Textarea value={form.preferences.employmentTypesText} onChange={(event) => updatePreferences("employmentTypesText", event.target.value)} placeholder="정규직, 전환형 인턴, 계약직 가능 조건 등" rows={4} />
                    </Field>
                    <div className="grid gap-4 md:grid-cols-3">
                      <Field label="희망 연봉 하한">
                        <Input maxLength={20} value={form.preferences.desiredSalaryMin} onChange={(event) => updatePreferences("desiredSalaryMin", event.target.value)} placeholder="예: 3200" />
                      </Field>
                      <Field label="희망 연봉 상한">
                        <Input maxLength={20} value={form.preferences.desiredSalaryMax} onChange={(event) => updatePreferences("desiredSalaryMax", event.target.value)} placeholder="예: 4500" />
                      </Field>
                      <Field label="입사 가능일">
                        <Input type="date" value={form.preferences.availableStartDate} onChange={(event) => updatePreferences("availableStartDate", event.target.value)} />
                      </Field>
                    </div>
                    <Field label="포트폴리오/활동 링크" className="md:col-span-2">
                      <Textarea value={form.portfolioLinksText} onChange={(event) => update("portfolioLinksText", event.target.value)} placeholder="노션, 블로그, 작업물, 활동 기록 링크를 한 줄에 하나씩 입력" rows={4} />
                    </Field>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="resume" className="mt-5">
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-base">
                      <FileText className="size-4 text-blue-600" />
                      이력서/자기소개
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="flex flex-wrap gap-2">
                      <Button variant="outline" size="sm" onClick={insertResumeTemplate}>
                        이력서 입력 틀 추가
                      </Button>
                      <Button variant="outline" size="sm" onClick={insertSelfIntroTemplate}>
                        자기소개 입력 틀 추가
                      </Button>
                    </div>
                    <Field label="이력서 원문">
                      <Textarea value={form.resumeText} onChange={(event) => update("resumeText", event.target.value)} rows={12} placeholder="PDF 업로드 전까지는 이력서 내용을 직접 붙여넣어 관리합니다." />
                    </Field>
                    <Field label="자기소개/강점">
                      <Textarea value={form.selfIntro} onChange={(event) => update("selfIntro", event.target.value)} rows={8} placeholder="지원 직무와 연결되는 경험, 강점, 협업 사례를 정리하세요." />
                    </Field>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="skills" className="mt-5">
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
                    <div className="grid gap-4 md:grid-cols-2">
                      <Field label="자격증">
                        <Textarea value={form.certificatesText} onChange={(event) => update("certificatesText", event.target.value)} rows={6} placeholder="예: 컴퓨터활용능력, 전산회계, 간호사 면허, TOEIC" />
                      </Field>
                      <Field label="언어">
                        <Textarea value={form.languagesText} onChange={(event) => update("languagesText", event.target.value)} rows={6} placeholder="예: TOEIC 850, 일본어 회화 가능" />
                      </Field>
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="history" className="mt-5 space-y-5">
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

              <TabsContent value="ai" className="mt-5">
                <Card className="border-slate-200 bg-card">
                  <CardHeader>
                    <CardTitle className="text-base">AI 분석 결과</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-5">
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
            </Tabs>
          </section>
        </div>
      </div>
    </div>
  );
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
      <span className="min-w-0">
        <span className="block text-sm font-bold">{copy.title}</span>
        <span className="mt-1 block text-xs leading-5 text-slate-500">{copy.description}</span>
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
  const agreed = consent?.aiDataAgreed === true;
  return (
    <div className={`rounded-lg border px-3 py-3 text-sm ${agreed ? "border-green-200 bg-green-50" : "border-amber-200 bg-amber-50"}`}>
      <div className={`font-bold ${agreed ? "text-green-700" : "text-amber-800"}`}>
        AI 데이터 동의 상태: {agreed ? "동의함" : "동의 필요"}
      </div>
      <p className={`mt-1 text-xs leading-5 ${agreed ? "text-green-700" : "text-amber-700"}`}>
        {agreed
          ? "프로필 요약, 역량 추출, 완성도 진단을 실행할 수 있습니다."
          : "동의가 꺼져 있으면 프로필 저장은 가능하지만 AI 분석은 제한됩니다."}
      </p>
      {!agreed && (
        <a className="mt-2 inline-flex text-xs font-bold text-blue-600 hover:underline" href="/settings">
          동의 설정으로 이동
        </a>
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

function serializeProfileForm(form: ProfileForm): string {
  return JSON.stringify(toRequest(form));
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

function toForm(profile: UserProfile): ProfileForm {
  const personalInfo = parseObject(profile.personalInfo);
  const jobPreferences = parseObject(profile.jobPreferences);
  return {
    loginId: profile.loginId ?? "",
    phoneNumber: profile.phoneNumber ?? "",
    nickname: getString(personalInfo, "nickname"),
    militaryStatus: getString(personalInfo, "militaryStatus"),
    veteranStatus: getString(personalInfo, "veteranStatus"),
    disabilityStatus: getString(personalInfo, "disabilityStatus"),
    chatNicknamesText: chatProfilesToLines(profile.chatProfiles),
    desiredJob: profile.desiredJob ?? "",
    desiredIndustry: profile.desiredIndustry ?? "",
    skillsText: arrayToLines(profile.skills),
    certificatesText: arrayToLines(profile.certificates),
    languagesText: arrayToLines(profile.languages),
    portfolioLinksText: arrayToLines(profile.portfolioLinks),
    education: parseEntries(profile.education, createEducation),
    career: parseEntries(profile.career, createCareer),
    experiences: parseEntries(profile.projects, createExperience),
    preferences: parsePreferences(profile.preferences, jobPreferences),
    resumeText: profile.resumeText ?? "",
    selfIntro: profile.selfIntro ?? "",
  };
}

function toRequest(form: ProfileForm): UserProfile {
  const personalInfo = stripEmptyObject({
    nickname: form.nickname,
    militaryStatus: form.militaryStatus,
    veteranStatus: form.veteranStatus,
    disabilityStatus: form.disabilityStatus,
  });
  const jobPreferences = stripEmptyObject({
    preferredRegions: linesToArray(form.preferences.preferredRegionsText),
    workTypes: linesToArray(form.preferences.workTypesText),
    employmentTypes: linesToArray(form.preferences.employmentTypesText),
    desiredSalaryMin: form.preferences.desiredSalaryMin,
    desiredSalaryMax: form.preferences.desiredSalaryMax,
    availableStartDate: form.preferences.availableStartDate,
  });
  return {
    loginId: blankToNull(form.loginId),
    phoneNumber: blankToNull(form.phoneNumber),
    desiredJob: blankToNull(form.desiredJob),
    desiredIndustry: blankToNull(form.desiredIndustry),
    skills: linesToArray(form.skillsText),
    certificates: linesToArray(form.certificatesText),
    languages: linesToArray(form.languagesText),
    portfolioLinks: linesToArray(form.portfolioLinksText),
    education: stripEmpty(form.education),
    career: stripEmpty(form.career),
    projects: stripEmpty(form.experiences),
    preferences: stripEmptyObject({
      region: form.preferences.region,
      workType: form.preferences.workType,
      salary: form.preferences.salary,
      employmentType: form.preferences.employmentType,
    }),
    jobPreferences,
    personalInfo,
    chatProfiles: linesToArray(form.chatNicknamesText).map((nickname, index) => ({ nickname, defaultProfile: index === 0 })),
    resumeText: blankToNull(form.resumeText),
    selfIntro: blankToNull(form.selfIntro),
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

function parsePreferences(value: unknown, jobPreferenceValue?: Record<string, unknown>): PreferencesForm {
  const parsed = normalizeUnknown(value);
  const next = createPreferences();
  if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
    const source = parsed as Record<string, unknown>;
    next.region = String(source.region ?? "");
    next.workType = String(source.workType ?? "");
    next.salary = String(source.salary ?? "");
    next.employmentType = String(source.employmentType ?? "");
  }
  const jobPreferences = jobPreferenceValue ?? parseObject(undefined);
  next.preferredRegionsText = arrayToLines(jobPreferences.preferredRegions);
  next.workTypesText = arrayToLines(jobPreferences.workTypes);
  next.employmentTypesText = arrayToLines(jobPreferences.employmentTypes);
  next.desiredSalaryMin = getString(jobPreferences, "desiredSalaryMin");
  next.desiredSalaryMax = getString(jobPreferences, "desiredSalaryMax");
  next.availableStartDate = getString(jobPreferences, "availableStartDate");
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

function parseObject(value: unknown): Record<string, unknown> {
  const parsed = normalizeUnknown(value);
  if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) return parsed as Record<string, unknown>;
  return {};
}

function getString(source: Record<string, unknown>, key: string): string {
  const value = source[key];
  return value == null ? "" : String(value);
}

function chatProfilesToLines(value: unknown): string {
  const parsed = normalizeUnknown(value);
  if (!Array.isArray(parsed)) return "";
  return parsed
    .map((item) => {
      if (typeof item === "string") return item;
      if (item && typeof item === "object") {
        const source = item as Record<string, unknown>;
        return String(source.nickname ?? source.displayName ?? "");
      }
      return "";
    })
    .filter(Boolean)
    .join("\n");
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
    const raw = value[key];
    if (Array.isArray(raw)) {
      if (raw.length) next[key] = raw as T[keyof T];
      continue;
    }
    const trimmed = String(raw ?? "").trim();
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

function validateProfile(form: ProfileForm): string | null {
  if (!form.desiredJob.trim()) {
    return "희망 직무는 필수입니다.";
  }
  if (form.desiredJob.trim().length > 80 || form.desiredIndustry.trim().length > 80) {
    return "희망 직무와 산업은 80자 이하로 입력해 주세요.";
  }
  if (form.loginId.trim() && !/^[A-Za-z0-9._-]{4,40}$/.test(form.loginId.trim())) {
    return "아이디는 영문, 숫자, 점, 밑줄, 하이픈만 사용해 4~40자로 입력해 주세요.";
  }
  if (form.phoneNumber.trim() && form.phoneNumber.trim().length < 8) {
    return "전화번호를 입력할 때는 최소 8자 이상으로 입력해 주세요.";
  }
  if (form.resumeText.length > 20000 || form.selfIntro.length > 10000) {
    return "이력서 원문은 20,000자 이하, 자기소개는 10,000자 이하로 입력해 주세요.";
  }
  const invalidEducation = form.education.find((item) => !isValidRange(item.startDate, item.endDate));
  if (invalidEducation) return "학력 종료월은 시작월보다 빠를 수 없습니다.";
  const invalidCareer = form.career.find((item) => !isValidRange(item.startDate, item.endDate));
  if (invalidCareer) return "경력 종료월은 시작월보다 빠를 수 없습니다.";
  const invalidExperience = form.experiences.find((item) => !isValidRange(item.startDate, item.endDate));
  if (invalidExperience) return "경험/프로젝트/활동 종료월은 시작월보다 빠를 수 없습니다.";
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
