import { useEffect, useMemo, useState } from "react";
import { AlertCircle, Brain, CheckCircle2, FileText, Plus, RefreshCw, Save, Sparkles, Trash2, User } from "lucide-react";
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

export function ProfilePage() {
  const [form, setForm] = useState<ProfileForm>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [aiLoading, setAiLoading] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [aiResult, setAiResult] = useState<ProfileAiResponse | null>(null);
  const [completeness, setCompleteness] = useState<ProfileCompleteness | null>(null);

  const selectedSkillSet = useMemo(() => new Set(linesToArray(form.skillsText).map((item) => item.toLowerCase())), [form.skillsText]);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const profile = await getProfile();
      setForm(toForm(profile));
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

  const save = async () => {
    setSaving(true);
    setError(null);
    setMessage(null);
    try {
      const validationError = validateProfile(form);
      if (validationError) {
        setError(validationError);
        return;
      }
      await saveProfile(toRequest(form));
      const nextCompleteness = await diagnoseProfileCompleteness().catch(() => null);
      setCompleteness(nextCompleteness);
      setMessage("프로필이 저장되었습니다.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로필 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const runAi = async (type: "summary" | "skills" | "completeness") => {
    setAiLoading(type);
    setError(null);
    setMessage(null);
    try {
      if (type === "summary") {
        setAiResult(await summarizeProfile());
        setMessage("프로필 AI 요약을 생성했습니다.");
      } else if (type === "skills") {
        setAiResult(await extractProfileSkills());
        setMessage("프로필에서 직무 역량 키워드를 추출했습니다.");
      } else {
        setCompleteness(await diagnoseProfileCompleteness());
        setMessage("프로필 완성도를 진단했습니다.");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "AI 기능 실행에 실패했습니다. AI 데이터 동의 상태를 확인해 주세요.");
    } finally {
      setAiLoading(null);
    }
  };

  const toggleSkill = (skill: string) => {
    const current = linesToArray(form.skillsText);
    const hasSkill = current.some((item) => item.toLowerCase() === skill.toLowerCase());
    update("skillsText", arrayToLines(hasSkill ? current.filter((item) => item.toLowerCase() !== skill.toLowerCase()) : [...current, skill]));
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

        <div className="grid gap-5 lg:grid-cols-[320px_1fr]">
          <aside className="space-y-5">
            <Card className="border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="text-base">완성도</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div>
                  <div className="text-3xl font-black text-blue-600">{completeness?.score ?? 0}%</div>
                  <Progress value={completeness?.score ?? 0} className="mt-2 h-2" />
                </div>
                {completeness?.jobFamilyLabel && (
                  <div className="rounded-md border border-blue-100 bg-blue-50 px-3 py-2">
                    <div className="text-xs font-bold text-blue-500">평가 직무군</div>
                    <div className="mt-1 text-sm font-bold text-blue-900">{completeness.jobFamilyLabel}</div>
                    <div className="mt-1 text-xs text-blue-700">
                      {completeness.model ?? "-"} · {completeness.status ?? "-"}
                    </div>
                  </div>
                )}
                <Checklist title="완료" items={completeness?.completed ?? []} done />
                <Checklist title="보강 필요" items={completeness?.missing ?? []} />
              </CardContent>
            </Card>

            <Card className="border-slate-200 bg-white">
              <CardHeader>
                <CardTitle className="text-base">AI 도구</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                <Button variant="outline" className="w-full justify-start" onClick={() => void runAi("summary")} disabled={!!aiLoading}>
                  <Sparkles className="size-4" />
                  프로필 AI 요약
                </Button>
                <Button variant="outline" className="w-full justify-start" onClick={() => void runAi("skills")} disabled={!!aiLoading}>
                  <Brain className="size-4" />
                  역량 키워드 추출
                </Button>
                <Button variant="outline" className="w-full justify-start" onClick={() => void runAi("completeness")} disabled={!!aiLoading}>
                  <CheckCircle2 className="size-4" />
                  완성도 진단
                </Button>
                <p className="text-xs leading-5 text-slate-500">
                  AI 기능은 `AI_DATA` 동의가 켜져 있어야 실행됩니다. 동의를 철회하면 저장은 가능하지만 AI 분석은 제한됩니다.
                </p>
              </CardContent>
            </Card>
          </aside>

          <section className="space-y-5">
            <Tabs defaultValue="basic">
              <TabsList className="h-auto w-full justify-start overflow-x-auto border border-slate-200 bg-white p-1">
                <TabsTrigger value="basic">기본</TabsTrigger>
                <TabsTrigger value="resume">이력서</TabsTrigger>
                <TabsTrigger value="skills">직무 역량</TabsTrigger>
                <TabsTrigger value="history">학력/경력/활동</TabsTrigger>
                <TabsTrigger value="ai">AI 결과</TabsTrigger>
              </TabsList>

              <TabsContent value="basic" className="mt-5">
                <Card className="border-slate-200 bg-white">
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
                    <Field label="포트폴리오/활동 링크" className="md:col-span-2">
                      <Textarea value={form.portfolioLinksText} onChange={(event) => update("portfolioLinksText", event.target.value)} placeholder="노션, 블로그, 작업물, 활동 기록 링크를 한 줄에 하나씩 입력" rows={4} />
                    </Field>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="resume" className="mt-5">
                <Card className="border-slate-200 bg-white">
                  <CardHeader>
                    <CardTitle className="flex items-center gap-2 text-base">
                      <FileText className="size-4 text-blue-600" />
                      이력서/자기소개
                    </CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
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
                <Card className="border-slate-200 bg-white">
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
                              : "border-slate-200 bg-white text-slate-700 hover:border-blue-300"
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
                          <Input type="month" value={item.endDate} onChange={(event) => updateEducation(index, "endDate", event.target.value)} />
                        </Field>
                        <Field label="상태" className="md:col-span-2">
                          <select value={item.status} onChange={(event) => updateEducation(index, "status", event.target.value)} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm">
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
                          <Input type="month" value={item.endDate} onChange={(event) => updateCareer(index, "endDate", event.target.value)} />
                        </Field>
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
                          <select value={item.type} onChange={(event) => updateExperience(index, "type", event.target.value)} className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm">
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
                          <Input type="month" value={item.endDate} onChange={(event) => updateExperience(index, "endDate", event.target.value)} />
                        </Field>
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
                <Card className="border-slate-200 bg-white">
                  <CardHeader>
                    <CardTitle className="text-base">AI 분석 결과</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-5">
                    {!aiResult && !completeness && <p className="text-sm text-slate-500">왼쪽 AI 도구를 실행하면 결과가 표시됩니다.</p>}
                    {aiResult && (
                      <div className="space-y-4">
                        <AiMeta
                          jobFamilyLabel={aiResult.jobFamilyLabel}
                          model={aiResult.model}
                          status={aiResult.status}
                          score={aiResult.completenessScore}
                        />
                        <ResultBlock title="요약" value={aiResult.summary} />
                        <TagBlock title="추출 역량" values={aiResult.extractedSkills} />
                        <TagBlock title="강점" values={aiResult.strengths} />
                        <TagBlock title="보강점" values={aiResult.gaps} tone="amber" />
                        <ListBlock title="추천 액션" values={aiResult.recommendations} />
                        <CriterionScoreList values={aiResult.criteria ?? []} />
                      </div>
                    )}
                    {completeness && (
                      <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                        <div className="font-bold text-slate-900">
                          완성도 {completeness.score}% · {completeness.jobFamilyLabel ?? "직무군 분석 전"}
                        </div>
                        <ListBlock title="추천 보강" values={completeness.recommendations} />
                        <CriterionScoreList values={completeness.criteria ?? []} />
                      </div>
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
    <Card className="border-slate-200 bg-white">
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

function StatusBox({ tone, text }: { tone: "success" | "error"; text: string }) {
  const cls = tone === "success" ? "border-green-200 bg-green-50 text-green-700" : "border-red-200 bg-red-50 text-red-700";
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

function ResultBlock({ title, value }: { title: string; value: string }) {
  return (
    <div>
      <div className="mb-2 text-xs font-bold text-slate-500">{title}</div>
      <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm leading-6 text-slate-700">{value || "-"}</p>
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
        {values.map((value) => <li key={value}>- {value}</li>)}
      </ul>
    </div>
  );
}

function AiMeta({ jobFamilyLabel, model, status, score }: { jobFamilyLabel?: string; model?: string; status?: string; score?: number }) {
  return (
    <div className="grid gap-3 rounded-lg border border-blue-100 bg-blue-50 p-4 text-sm md:grid-cols-4">
      <div>
        <div className="text-xs font-bold text-blue-500">직무군</div>
        <div className="mt-1 font-bold text-blue-950">{jobFamilyLabel ?? "-"}</div>
      </div>
      <div>
        <div className="text-xs font-bold text-blue-500">평가 점수</div>
        <div className="mt-1 font-bold text-blue-950">{score ?? 0}점</div>
      </div>
      <div>
        <div className="text-xs font-bold text-blue-500">모델</div>
        <div className="mt-1 font-bold text-blue-950">{model ?? "-"}</div>
      </div>
      <div>
        <div className="text-xs font-bold text-blue-500">상태</div>
        <div className="mt-1 font-bold text-blue-950">{status ?? "-"}</div>
      </div>
    </div>
  );
}

function CriterionScoreList({ values }: { values: NonNullable<ProfileAiResponse["criteria"]> }) {
  if (!values.length) return null;
  return (
    <div>
      <div className="mb-2 text-xs font-bold text-slate-500">평가 기준별 점수</div>
      <div className="space-y-3">
        {values.map((item) => (
          <div key={item.criterion} className="rounded-lg border border-slate-200 bg-white p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="font-bold text-slate-900">{item.label}</div>
              <Badge className="bg-slate-100 text-slate-700">
                원점수 {item.rawScore} · 가중치 {item.weight}%
              </Badge>
            </div>
            <Progress value={item.rawScore} className="mt-2 h-2" />
            {item.evidence && <p className="mt-2 text-sm leading-6 text-slate-600">{item.evidence}</p>}
            {item.improvement && <p className="mt-1 text-sm leading-6 text-amber-700">{item.improvement}</p>}
          </div>
        ))}
      </div>
    </div>
  );
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

function toRequest(form: ProfileForm): UserProfile {
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

function validateProfile(form: ProfileForm): string | null {
  if (!form.desiredJob.trim()) {
    return "희망 직무는 필수입니다.";
  }
  if (form.desiredJob.trim().length > 80 || form.desiredIndustry.trim().length > 80) {
    return "희망 직무와 산업은 80자 이하로 입력해 주세요.";
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
