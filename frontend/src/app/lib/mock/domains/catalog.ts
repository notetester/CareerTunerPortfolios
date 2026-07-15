// 데모/목: NCS 직무능력표준 · 자격증 통합 카탈로그 (/catalog/**).
// 실제 백엔드는 개발 DB(1,109 NCS / 61,141 자격)를 검색하지만, 데모는 대표 표본만 담는다.
// 응답은 api()가 기대하는 백엔드 raw data 형태(ApiResponse.data)로 반환한다.
import type { MockRoute } from "../registry";
import type {
  CertDetail,
  CertDetailResponse,
  CertSearchItem,
  CertType,
  NcsDetail,
  NcsSearchItem,
  NcsUnit,
} from "@/features/catalog/types/catalog";

interface NcsRow extends NcsSearchItem {
  searchText: string;
  units: NcsUnit[];
}

const NCS: NcsRow[] = [
  {
    id: 1, ncsCode: "20-01-02-11", majorName: "정보통신", middleName: "정보기술", minorName: "정보기술개발",
    subName: "데이터아키텍처", unitCount: 10, elementCount: 31, minLevel: 6, maxLevel: 8,
    searchText: "데이터아키텍처 데이터 모델 데이터베이스 표준 품질관리 빅데이터",
    units: [
      { unitNo: "u1", unitName: "데이터 아키텍처 구축 계획 수립", level: 7, elements: [
        { elementNo: "e1", elementName: "전사 아키텍처 프레임워크 확인하기",
          knowledge: ["전사 아키텍처 프레임워크", "EA 수립 프로세스"],
          skills: ["아키텍처 산출물 분석 능력", "요구사항 도출 능력"],
          attitudes: ["분석적 태도", "능동적 의사소통"] },
      ] },
      { unitNo: "u2", unitName: "데이터 표준 수립", level: 6, elements: [
        { elementNo: "e2", elementName: "데이터 표준화 원칙 정의하기",
          knowledge: ["데이터 표준화 개념", "메타데이터 관리"], skills: ["표준 사전 설계 능력"], attitudes: ["일관성 유지 태도"] },
      ] },
    ],
  },
  {
    id: 2, ncsCode: "20-01-07-06", majorName: "정보통신", middleName: "정보기술", minorName: "인공지능",
    subName: "인공지능학습데이터구축", unitCount: 11, elementCount: 38, minLevel: 3, maxLevel: 7,
    searchText: "인공지능 학습데이터 데이터라벨링 데이터수집 정제 가공 AI",
    units: [
      { unitNo: "u1", unitName: "학습데이터 수집 설계", level: 5, elements: [
        { elementNo: "e1", elementName: "데이터 수집 요건 정의하기",
          knowledge: ["데이터 수집 방법론", "저작권·개인정보 규정"], skills: ["수집 파이프라인 설계"], attitudes: ["윤리적 태도"] },
      ] },
    ],
  },
  {
    id: 3, ncsCode: "20-01-02-04", majorName: "정보통신", middleName: "정보기술", minorName: "정보기술개발",
    subName: "응용SW엔지니어링", unitCount: 12, elementCount: 45, minLevel: 3, maxLevel: 8,
    searchText: "응용SW 소프트웨어 개발 프로그래밍 애플리케이션 아키텍처 테스트",
    units: [
      { unitNo: "u1", unitName: "애플리케이션 설계", level: 6, elements: [
        { elementNo: "e1", elementName: "요구사항 분석하기",
          knowledge: ["요구공학", "UML"], skills: ["설계 도구 활용"], attitudes: ["사용자 중심 사고"] },
      ] },
    ],
  },
  {
    id: 4, ncsCode: "20-01-06-03", majorName: "정보통신", middleName: "정보보호", minorName: "정보보호관리·운영",
    subName: "정보보안", unitCount: 9, elementCount: 34, minLevel: 4, maxLevel: 7,
    searchText: "정보보안 보안 침해대응 취약점 암호 네트워크보안 관제",
    units: [
      { unitNo: "u1", unitName: "보안 위협 분석", level: 6, elements: [
        { elementNo: "e1", elementName: "취약점 진단하기",
          knowledge: ["보안 취약점 유형", "진단 도구"], skills: ["모의해킹", "로그 분석"], attitudes: ["책임감"] },
      ] },
    ],
  },
];

type CertRow = CertDetail & { searchText: string; schedules?: CertDetailResponse["schedules"] };

const CERTS: CertRow[] = [
  {
    id: 101, certType: "NATIONAL_TECH", name: "정보처리기사", grade: null, authority: "한국산업인력공단",
    issuerOrg: "한국산업인력공단", series: "기사", jmCd: "1320", regNo: null, official: null, status: "ACTIVE",
    ncsSubName: "응용SW엔지니어링", hasSchedule: 1,
    searchText: "정보처리기사 소프트웨어 개발 프로그래밍 데이터베이스 정보통신",
    description: `[국가기술자격] 계열: 기사.

■ 개요
컴퓨터를 효과적으로 활용하기 위해 우수한 프로그램을 개발하여 업무의 효율성을 높이고 정보처리 분야의 전문 인력을 양성하기 위해 제정된 국가기술자격.

■ 수행직무
정보시스템의 분석·설계·구현·시험·운영을 수행하며, 데이터베이스와 응용 소프트웨어를 개발·관리한다.

■ 진로 및 전망
소프트웨어 개발업체, SI업체, 정부기관·금융기관 전산실 등으로 진출한다. 정보화 확산으로 수요가 꾸준하다.

■ 취득방법
① 시행처: 한국산업인력공단
② 시험과목: (필기) 데이터베이스·운영체제·소프트웨어공학·데이터통신 등, (실기) 정보처리 실무
③ 합격기준: 필기 과목당 40점 이상 전과목 평균 60점 이상, 실기 60점 이상`,
    schedules: [
      { certName: "정보처리기사", year: 2026, roundName: "2026년 1회", docRegStart: "20260113", docRegEnd: "20260116",
        docExam: "20260215", docPass: "20260312", pracExamStart: "20260419", pracExamEnd: "20260506", pracPass: "20260605" },
      { certName: "정보처리기사", year: 2026, roundName: "2026년 2회", docRegStart: "20260421", docRegEnd: "20260424",
        docExam: "20260524", docPass: "20260618", pracExamStart: "20260726", pracExamEnd: "20260812", pracPass: "20260911" },
    ],
  },
  {
    id: 102, certType: "NATIONAL_TECH", name: "빅데이터분석기사", grade: null, authority: "한국산업인력공단",
    issuerOrg: "한국산업인력공단", series: "기사", jmCd: "2010", regNo: null, official: null, status: "ACTIVE",
    ncsSubName: "인공지능학습데이터구축", hasSchedule: 0,
    searchText: "빅데이터분석기사 데이터 분석 통계 머신러닝 시각화",
    description: `[국가기술자격] 계열: 기사.

■ 개요
대용량 데이터를 수집·저장·분석하고 그 결과를 시각화·활용하는 빅데이터 분석 전문 인력을 양성하기 위한 국가기술자격.

■ 수행직무
데이터 수집·전처리, 탐색적 분석, 통계·머신러닝 모델링, 결과 해석 및 시각화를 수행한다.

■ 진로 및 전망
데이터 분석가, ML 엔지니어 등 수요가 급증하는 분야로 진출 전망이 밝다.`,
  },
  {
    id: 103, certType: "NATIONAL_TECH", name: "정보보안기사", grade: null, authority: "한국인터넷진흥원",
    issuerOrg: "한국인터넷진흥원", series: "기사", jmCd: "1500", regNo: null, official: null, status: "ACTIVE",
    ncsSubName: "정보보안", hasSchedule: 0,
    searchText: "정보보안기사 보안 침해대응 취약점 관제 암호",
    description: `[국가기술자격] 계열: 기사.

■ 개요
정보시스템의 보안 위협에 대응하고 취약점을 진단·조치하는 정보보안 전문 인력을 양성하기 위한 국가기술자격.

■ 수행직무
보안 정책 수립, 취약점 진단, 침해사고 대응, 보안 관제 및 암호 기술 적용 업무를 수행한다.`,
  },
  {
    id: 104, certType: "NATIONAL_PROF", name: "가맹거래사", grade: null, authority: "한국산업인력공단",
    issuerOrg: "한국산업인력공단", series: "가맹거래사", jmCd: null, regNo: null, official: null, status: "ACTIVE",
    ncsSubName: null, hasSchedule: 1,
    searchText: "가맹거래사 프랜차이즈 가맹 계약 상담",
    description: "[국가전문자격] 계열: 가맹거래사.",
    schedules: [
      { certName: "가맹거래사", year: 2026, roundName: "2026년 1차", docRegStart: "20260126", docRegEnd: "20260227",
        docExam: "20260307", docPass: "20260415", pracExamStart: null, pracExamEnd: null, pracPass: null },
    ],
  },
  {
    id: 105, certType: "PRIVATE", name: "데이터분석전문가(ADP)", grade: "전문가", authority: "한국데이터산업진흥원",
    issuerOrg: "한국데이터산업진흥원", series: null, jmCd: null, regNo: "2013-0001", official: "공인", status: "등록완료",
    ncsSubName: null, hasSchedule: 0,
    searchText: "데이터분석전문가 ADP 데이터 분석 통계 R 파이썬 공인",
    description: `데이터 이해와 처리 기술에 대한 전문지식을 바탕으로 데이터 분석 기획·분석·시각화를 수행하는 전문가.

[직무내용] 데이터 분석 기획, 데이터 처리·분석, 데이터 시각화 및 결과 활용.`,
  },
  {
    id: 106, certType: "PRIVATE", name: "SQL전문가(SQLP)", grade: "전문가", authority: "한국데이터산업진흥원",
    issuerOrg: "한국데이터산업진흥원", series: null, jmCd: null, regNo: "2013-0002", official: "공인", status: "등록완료",
    ncsSubName: null, hasSchedule: 0,
    searchText: "SQL전문가 SQLP 데이터베이스 SQL 데이터모델링 공인",
    description: `데이터베이스와 데이터 모델링에 대한 전문지식을 바탕으로 SQL을 활용해 데이터를 처리·최적화하는 전문가.

[직무내용] 데이터 모델링, SQL 기본·활용, SQL 최적화.`,
  },
];

const includesQ = (hay: string, q: string) => hay.toLowerCase().includes(q.toLowerCase());

function toNcsItem(r: NcsRow): NcsSearchItem {
  const { searchText: _s, units: _u, ...item } = r;
  return item;
}

function toCertItem(r: CertRow): CertSearchItem {
  return {
    id: r.id, certType: r.certType, name: r.name, grade: r.grade, authority: r.authority,
    official: r.official, hasSchedule: r.hasSchedule,
    descriptionSnippet: (r.description ?? "").slice(0, 140),
  };
}

export const catalogRoutes: MockRoute[] = [
  {
    method: "GET",
    pattern: /^\/catalog\/ncs$/,
    handler: ({ query }) => {
      const q = (query.get("q") ?? "").trim();
      const rows = q
        ? NCS.filter((r) => includesQ(r.subName, q) || includesQ(r.searchText, q))
        : NCS;
      return rows.map(toNcsItem);
    },
  },
  {
    method: "GET",
    pattern: /^\/catalog\/ncs\/(\d+)$/,
    handler: ({ params }) => {
      const r = NCS.find((x) => x.id === Number(params[0]));
      if (!r) return null;
      const detail: NcsDetail = { ...toNcsItem(r), units: r.units };
      return detail;
    },
  },
  {
    method: "GET",
    pattern: /^\/catalog\/certificates$/,
    handler: ({ query }) => {
      const q = (query.get("q") ?? "").trim();
      const type = (query.get("type") ?? "") as CertType | "";
      let rows = CERTS;
      if (type) rows = rows.filter((r) => r.certType === type);
      if (q) rows = rows.filter((r) => includesQ(r.name, q) || includesQ(r.searchText, q));
      return rows.map(toCertItem);
    },
  },
  {
    method: "GET",
    pattern: /^\/catalog\/certificates\/(\d+)$/,
    handler: ({ params }) => {
      const r = CERTS.find((x) => x.id === Number(params[0]));
      if (!r) return null;
      const { searchText: _s, schedules, ...certificate } = r;
      const resp: CertDetailResponse = { certificate, schedules: schedules ?? [] };
      return resp;
    },
  },
];
