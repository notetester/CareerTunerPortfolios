import { Link, useLocation, useSearchParams } from "react-router";
import {
  ArrowLeft,
  ArrowRight,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  Camera,
  CheckCircle2,
  Clock3,
  Code2,
  Database,
  HeartHandshake,
  MessageCircle,
  Newspaper,
  Radio,
  Rocket,
  Send,
  ShieldCheck,
  Sparkles,
  Users,
  Video,
} from "lucide-react";
import { Badge } from "@/app/components/ui/badge";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import {
  blogPosts,
  openRoles,
  pressReleases,
  socialChannels,
  type PublicArticle,
} from "../data/publicCompanyContent";

const sectionMeta = {
  "/company/about": { eyebrow: "About CareerTuner", title: "지원 준비의 맥락을 잃지 않는 제품을 만듭니다", icon: Building2 },
  "/company/team": { eyebrow: "Team", title: "제품·AI·운영을 수직 기능으로 함께 책임지는 팀", icon: Users },
  "/company/careers": { eyebrow: "Careers", title: "검토 가능한 AI 취업 경험을 함께 설계합니다", icon: Rocket },
  "/company/blog": { eyebrow: "Product Blog", title: "제품을 만들며 확인한 문제와 결정을 기록합니다", icon: Newspaper },
  "/company/press": { eyebrow: "Newsroom", title: "CareerTuner의 공식 발표와 프로젝트 소식", icon: Radio },
  "/company/social": { eyebrow: "Official Channels", title: "채널마다 다른 형식으로 제품 소식을 전합니다", icon: MessageCircle },
} as const;

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("ko-KR", { year: "numeric", month: "long", day: "numeric" })
    .format(new Date(`${value}T00:00:00`));
}

export function CompanyPage() {
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const pathname = location.pathname as keyof typeof sectionMeta;
  const meta = sectionMeta[pathname] ?? sectionMeta["/company/about"];
  const Icon = meta.icon;

  return (
    <div className="min-h-screen bg-slate-50 text-slate-900">
      <section className="border-b border-slate-200 bg-card">
        <div className="mx-auto max-w-[1180px] px-4 py-10 sm:px-6 sm:py-14">
          <div className="flex items-center gap-2 text-sm font-bold text-teal-700">
            <Icon className="size-4" />
            {meta.eyebrow}
          </div>
          <h1 className="mt-3 max-w-4xl text-3xl font-black leading-tight sm:text-4xl">{meta.title}</h1>
          <nav className="mt-7 flex flex-wrap gap-2" aria-label="회사 정보 메뉴">
            {Object.entries(sectionMeta).map(([href, item]) => (
              <Button key={href} asChild size="sm" variant={pathname === href ? "default" : "outline"}>
                <Link to={href}>{item.eyebrow}</Link>
              </Button>
            ))}
          </nav>
        </div>
      </section>

      <main className="mx-auto w-full max-w-[1180px] px-4 py-10 sm:px-6">
        {pathname === "/company/about" && <AboutContent />}
        {pathname === "/company/team" && <TeamContent />}
        {pathname === "/company/careers" && <CareersContent selectedSlug={searchParams.get("role")} />}
        {pathname === "/company/blog" && <ArticleCollection kind="blog" selectedSlug={searchParams.get("post")} />}
        {pathname === "/company/press" && <ArticleCollection kind="press" selectedSlug={searchParams.get("post")} />}
        {pathname === "/company/social" && <SocialContent selectedId={searchParams.get("channel")} />}
      </main>
    </div>
  );
}

function AboutContent() {
  const values = [
    { icon: BriefcaseBusiness, title: "지원 건 중심", body: "공고, 프로필, 분석, 면접과 첨삭을 기업·직무별 하나의 작업공간에 연결합니다." },
    { icon: ShieldCheck, title: "사용자가 통제하는 AI", body: "처리 범위를 분리해 동의를 받고, 철회 즉시 관련 기능을 중단하며 결과 근거를 함께 남깁니다." },
    { icon: Code2, title: "검토 가능한 구현", body: "사용자·관리자 화면, API, 데이터 이력과 배포 산출물을 같은 기능 단위로 완성합니다." },
  ];

  return (
    <div className="space-y-12">
      <section className="grid gap-8 lg:grid-cols-[minmax(0,1.45fr)_minmax(260px,0.55fr)] lg:items-start">
        <div>
          <Badge className="bg-teal-100 text-teal-800">CareerTuner 프로젝트</Badge>
          <h2 className="mt-4 text-2xl font-black">채용공고를 저장하는 도구를 넘어, 지원 준비 전체를 연결합니다</h2>
          <p className="mt-4 max-w-3xl text-base leading-8 text-slate-600">
            CareerTuner는 지원자가 실제로 준비하는 단위인 지원 건을 중심으로 공고 분석, 스펙 비교,
            학습 과제, 면접과 첨삭을 연결하는 AI 취업 전략 플랫폼입니다. 현재 공개본은 제품·기술 심사를 위한
            포트폴리오 데모이며, 실제 취업 결과를 보장하지 않습니다.
          </p>
          <div className="mt-6 flex flex-wrap gap-3">
            <Button asChild><Link to="/features">기능 살펴보기 <ArrowRight className="size-4" /></Link></Button>
            <Button asChild variant="outline"><Link to="/company/blog">제작 기록 읽기</Link></Button>
          </div>
        </div>
        <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-slate-200 bg-slate-200">
          {[
            ["1", "지원 건 중심 흐름"],
            ["6", "수직 기능 담당 영역"],
            ["4", "배포 채널"],
            ["110", "공개 근거 노드"],
          ].map(([value, label]) => (
            <div key={label} className="bg-card p-5">
              <dt className="text-xs font-semibold text-slate-500">{label}</dt>
              <dd className="mt-2 text-2xl font-black text-slate-900">{value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section>
        <h2 className="text-xl font-black">제품 원칙</h2>
        <div className="mt-5 grid gap-4 md:grid-cols-3">
          {values.map((value) => (
            <Card key={value.title} className="border-slate-200 bg-card">
              <CardHeader>
                <value.icon className="size-5 text-teal-700" />
                <CardTitle className="text-base">{value.title}</CardTitle>
              </CardHeader>
              <CardContent className="text-sm leading-6 text-slate-600">{value.body}</CardContent>
            </Card>
          ))}
        </div>
      </section>

      <section className="border-y border-slate-200 py-8">
        <h2 className="text-xl font-black">현재 공개 단계</h2>
        <div className="mt-5 grid gap-5 md:grid-cols-3">
          {["공개 베타와 포트폴리오 심사", "mock/full-stack 이중 실행", "웹·모바일·데스크톱 검증"].map((item, index) => (
            <div key={item} className="flex gap-3">
              <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-teal-100 text-xs font-black text-teal-800">{index + 1}</span>
              <div className="text-sm font-semibold leading-6 text-slate-700">{item}</div>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

function TeamContent() {
  const teams = [
    ["A", "회원·프로필", "인증, 프로필, 동의와 사용자 설정", Users],
    ["B", "공고·기업 분석", "공고 추출, 기업 근거와 관리자 검토", Building2],
    ["C", "적합도·전략", "스펙 비교, 학습 과제와 장기 취업 분석", Database],
    ["D", "AI 면접", "질문 생성, 답변 평가와 음성·비언어 경험", Video],
    ["E", "첨삭·결제", "문서 첨삭, 크레딧과 구독 운영", Sparkles],
    ["F", "커뮤니티·신뢰", "고객센터, 콘텐츠, 법적 문서와 운영", HeartHandshake],
  ] as const;

  return (
    <div className="space-y-10">
      <section className="max-w-3xl">
        <h2 className="text-2xl font-black">기능을 화면 하나가 아니라 end-to-end 결과로 소유합니다</h2>
        <p className="mt-4 text-base leading-8 text-slate-600">
          각 담당 영역은 사용자 화면, Spring API, MyBatis 데이터, 관리자 운영 화면과 AI 실험을 함께 봅니다.
          공통 라우팅·인증·데이터 계약은 팀 합의로 변경하고, 기능별 결정은 문서와 테스트로 남깁니다.
        </p>
      </section>
      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
        {teams.map(([code, title, body, Icon]) => (
          <Card key={code} className="border-slate-200 bg-card">
            <CardHeader>
              <div className="flex items-center justify-between">
                <span className="flex size-8 items-center justify-center rounded-md bg-slate-900 text-sm font-black text-white">{code}</span>
                <Icon className="size-5 text-teal-700" />
              </div>
              <CardTitle className="text-base">{title}</CardTitle>
            </CardHeader>
            <CardContent className="text-sm leading-6 text-slate-600">{body}</CardContent>
          </Card>
        ))}
      </section>
      <section className="flex flex-col gap-4 border-t border-slate-200 pt-8 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="font-black">함께 만들 영역이 궁금한가요?</h2>
          <p className="mt-1 text-sm text-slate-600">현재 공개된 프로젝트 협업 역할과 기대 결과를 확인할 수 있습니다.</p>
        </div>
        <Button asChild><Link to="/company/careers">채용·협업 역할 보기 <ArrowRight className="size-4" /></Link></Button>
      </section>
    </div>
  );
}

function CareersContent({ selectedSlug }: { selectedSlug: string | null }) {
  const selected = openRoles.find((role) => role.slug === selectedSlug);
  if (selected) {
    return (
      <article className="mx-auto max-w-3xl">
        <Button asChild variant="ghost" className="mb-5 px-0"><Link to="/company/careers"><ArrowLeft className="size-4" /> 역할 목록</Link></Button>
        <Badge className="bg-teal-100 text-teal-800">{selected.team}</Badge>
        <h2 className="mt-4 text-3xl font-black">{selected.title}</h2>
        <div className="mt-3 flex flex-wrap gap-2 text-sm text-slate-500">
          <span>{selected.location}</span><span>·</span><span>{selected.employmentType}</span>
        </div>
        <p className="mt-6 text-base leading-8 text-slate-600">{selected.summary}</p>
        <div className="mt-9 grid gap-8 md:grid-cols-2">
          <RoleList title="함께 할 일" items={selected.responsibilities} />
          <RoleList title="이런 분을 찾습니다" items={selected.qualifications} />
        </div>
        <div className="mt-10 border-t border-slate-200 pt-6">
          <p className="text-sm leading-6 text-slate-600">현재 페이지는 프로젝트 협업 역할을 소개하는 포트폴리오용 채용 안내입니다. 지원 문의는 고객센터를 통해 접수합니다.</p>
          <Button asChild className="mt-4"><Link to="/support/contact">협업 문의하기 <ArrowRight className="size-4" /></Link></Button>
        </div>
      </article>
    );
  }

  return (
    <div className="space-y-8">
      <p className="max-w-3xl text-base leading-8 text-slate-600">
        CareerTuner는 현재 포트폴리오·공개 베타 단계입니다. 아래 역할은 실제 법인 채용공고가 아니라
        프로젝트가 필요로 하는 협업 역할과 책임을 투명하게 설명하는 안내입니다.
      </p>
      <div className="grid gap-4 lg:grid-cols-3">
        {openRoles.map((role) => (
          <Card key={role.slug} className="border-slate-200 bg-card">
            <CardHeader>
              <Badge variant="outline" className="w-fit">{role.team}</Badge>
              <CardTitle className="text-lg">{role.title}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              <p className="text-sm leading-6 text-slate-600">{role.summary}</p>
              <div className="text-xs text-slate-500">{role.location} · {role.employmentType}</div>
              <Button asChild variant="outline" className="w-full"><Link to={`/company/careers?role=${role.slug}`}>역할 상세 <ArrowRight className="size-4" /></Link></Button>
            </CardContent>
          </Card>
        ))}
      </div>
      <section className="border-t border-slate-200 pt-8">
        <h2 className="text-lg font-black">협업 절차</h2>
        <ol className="mt-5 grid gap-4 md:grid-cols-4">
          {["역할과 작업물 확인", "기술·제품 대화", "작은 범위 공동 작업", "회고 후 범위 확정"].map((step, index) => (
            <li key={step} className="flex gap-3 text-sm text-slate-700"><b className="text-teal-700">{index + 1}</b>{step}</li>
          ))}
        </ol>
      </section>
    </div>
  );
}

function RoleList({ title, items }: { title: string; items: string[] }) {
  return (
    <section>
      <h3 className="font-black">{title}</h3>
      <ul className="mt-4 space-y-3">
        {items.map((item) => <li key={item} className="flex gap-2 text-sm leading-6 text-slate-600"><CheckCircle2 className="mt-1 size-4 shrink-0 text-teal-700" />{item}</li>)}
      </ul>
    </section>
  );
}

function ArticleCollection({ kind, selectedSlug }: { kind: "blog" | "press"; selectedSlug: string | null }) {
  const articles = kind === "blog" ? blogPosts : pressReleases;
  const basePath = kind === "blog" ? "/company/blog" : "/company/press";
  const selected = articles.find((article) => article.slug === selectedSlug);
  if (selected) return <ArticleDetail article={selected} basePath={basePath} />;

  return (
    <div className="space-y-8">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <p className="max-w-2xl text-base leading-8 text-slate-600">
          {kind === "blog" ? "제품 설계, AI 신뢰와 엔지니어링 과정을 팀의 언어로 기록합니다." : "공개 베타와 기술 공개를 포함한 프로젝트 팀의 공식 소식입니다."}
        </p>
        <span className="text-sm text-slate-500">총 {articles.length}건</span>
      </div>
      <section className="grid gap-4 lg:grid-cols-3">
        {articles.map((article) => (
          <Card key={article.slug} className="border-slate-200 bg-card">
            <CardHeader>
              <Badge variant="outline" className="w-fit">{article.category}</Badge>
              <CardTitle className="text-lg leading-7">{article.title}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-5">
              <p className="text-sm leading-6 text-slate-600">{article.summary}</p>
              <div className="flex items-center gap-3 text-xs text-slate-500">
                <span className="inline-flex items-center gap-1"><CalendarDays className="size-3.5" />{formatDate(article.publishedAt)}</span>
                <span className="inline-flex items-center gap-1"><Clock3 className="size-3.5" />{article.readMinutes}분</span>
              </div>
              <Button asChild variant="outline" className="w-full"><Link to={`${basePath}?post=${article.slug}`}>본문 읽기 <ArrowRight className="size-4" /></Link></Button>
            </CardContent>
          </Card>
        ))}
      </section>
    </div>
  );
}

function ArticleDetail({ article, basePath }: { article: PublicArticle; basePath: string }) {
  return (
    <article className="mx-auto max-w-3xl">
      <Button asChild variant="ghost" className="mb-5 px-0"><Link to={basePath}><ArrowLeft className="size-4" /> 목록</Link></Button>
      <Badge variant="outline">{article.category}</Badge>
      <h2 className="mt-4 text-3xl font-black leading-tight">{article.title}</h2>
      <div className="mt-4 flex items-center gap-4 text-sm text-slate-500">
        <span>{formatDate(article.publishedAt)}</span><span>{article.readMinutes}분 읽기</span>
      </div>
      <p className="mt-7 border-y border-slate-200 py-6 text-lg font-semibold leading-8 text-slate-700">{article.summary}</p>
      <div className="mt-7 space-y-6">
        {article.body.map((paragraph) => <p key={paragraph} className="text-base leading-8 text-slate-700">{paragraph}</p>)}
      </div>
      <div className="mt-10 flex flex-wrap gap-3 border-t border-slate-200 pt-6">
        <Button asChild><Link to="/features">제품 기능 보기</Link></Button>
        <Button asChild variant="outline"><Link to="/support/contact">문의하기</Link></Button>
      </div>
    </article>
  );
}

const channelIcons = { youtube: Video, instagram: Camera, twitter: Send, kakao: MessageCircle } as const;

function SocialContent({ selectedId }: { selectedId: string | null }) {
  const selected = socialChannels.find((channel) => channel.id === selectedId) ?? socialChannels[0];
  const Icon = channelIcons[selected.id];

  return (
    <div className="grid gap-8 lg:grid-cols-[260px_minmax(0,1fr)]">
      <nav className="space-y-2" aria-label="공식 채널 선택">
        {socialChannels.map((channel) => {
          const ChannelIcon = channelIcons[channel.id];
          return (
            <Button key={channel.id} asChild variant={channel.id === selected.id ? "default" : "outline"} className="w-full justify-start">
              <Link to={`/company/social?channel=${channel.id}`}><ChannelIcon className="size-4" />{channel.label}</Link>
            </Button>
          );
        })}
      </nav>
      <section>
        <div className="flex items-start gap-4 border-b border-slate-200 pb-6">
          <div className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-slate-900 text-white"><Icon className="size-5" /></div>
          <div>
            <h2 className="text-2xl font-black">{selected.label}</h2>
            <p className="mt-1 text-sm font-semibold text-teal-700">{selected.handle}</p>
            <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-600">{selected.description}</p>
            <div className="mt-3 text-xs text-slate-500">업데이트 주기: {selected.cadence}</div>
          </div>
        </div>
        <div className="mt-6 space-y-4">
          {selected.posts.map((post) => (
            <Card key={post.title} className="border-slate-200 bg-card">
              <CardHeader className="pb-2"><CardTitle className="text-base">{post.title}</CardTitle></CardHeader>
              <CardContent>
                <p className="text-sm leading-6 text-slate-600">{post.body}</p>
                <div className="mt-4 text-xs text-slate-400">{formatDate(post.publishedAt)}</div>
              </CardContent>
            </Card>
          ))}
        </div>
        <p className="mt-6 text-xs leading-5 text-slate-500">현재 채널 화면은 공개 포트폴리오에서 공식 콘텐츠 운영 경험을 보여주는 내부 데모입니다. 외부 SNS 계정을 사칭하지 않습니다.</p>
      </section>
    </div>
  );
}
