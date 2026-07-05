// 데모/목: W6 복수 닉네임 프로필 + 채팅방 전용 프로필 + 계정 확충 + 이력서 상세 스펙.
// 백엔드 의미론 미러:
//   - 닉네임 프로필: 계정당 여러 개, 첫 프로필 자동 기본, 전역 UNIQUE, 마지막 삭제 금지
//   - 채팅방 프로필: 방별 매핑, nicknameProfileId=null → 익명 참가
//   - 계정: 로그인 아이디 최초 1회 설정, 전화번호 저장
//   - 이력서 상세: JSON 섹션 upsert
import type {
  AccountInfo,
  ConversationProfile,
  NicknameProfile,
  ResumeDetail,
} from "@/features/profile/types/nicknameProfile";
import type { MockRoute } from "../registry";

const DEMO_USER_ID = 9001;
const nowIso = () => new Date().toISOString();

/* ── 닉네임 프로필 상태 ── */

let profileSeq = 5202;
const profiles: NicknameProfile[] = [
  {
    id: 5201,
    userId: DEMO_USER_ID,
    nickname: "취준생김데모",
    avatarFileId: null,
    bio: "백엔드 신입 준비 중입니다.",
    isDefault: true,
    status: "ACTIVE",
    updatedAt: nowIso(),
  },
  {
    id: 5200,
    userId: DEMO_USER_ID,
    nickname: "익명의개발자",
    avatarFileId: null,
    bio: null,
    isDefault: false,
    status: "ACTIVE",
    updatedAt: nowIso(),
  },
];

function activeProfiles(): NicknameProfile[] {
  return profiles.filter((p) => p.status === "ACTIVE").sort((a, b) => Number(b.isDefault) - Number(a.isDefault) || a.id - b.id);
}

function nicknameTaken(nickname: string, excludeId?: number): boolean {
  return profiles.some((p) => p.status === "ACTIVE" && p.nickname === nickname && p.id !== excludeId);
}

/* ── 채팅방 전용 프로필 매핑 ── */

const conversationProfiles = new Map<number, { nicknameProfileId: number | null; anonymous: boolean }>();

function resolveConversation(conversationId: number): ConversationProfile {
  const mapping = conversationProfiles.get(conversationId);
  if (!mapping) {
    return { conversationId, userId: DEMO_USER_ID, nicknameProfileId: null, nickname: null, anonymous: false, resolved: false };
  }
  const nickname = mapping.anonymous || mapping.nicknameProfileId == null
    ? "익명"
    : profiles.find((p) => p.id === mapping.nicknameProfileId)?.nickname ?? "익명";
  return {
    conversationId,
    userId: DEMO_USER_ID,
    nicknameProfileId: mapping.nicknameProfileId,
    nickname,
    anonymous: mapping.anonymous,
    resolved: true,
  };
}

/* ── 계정 정보 ── */

const account: AccountInfo = {
  userId: DEMO_USER_ID,
  email: "demo@careertuner.dev",
  name: "김데모",
  loginId: null,
  loginIdSet: false,
  phone: null,
  phoneVerified: false,
  passwordEnabled: true,
  linkedProviders: ["KAKAO"],
};

/* ── 이력서 상세 ── */

let resumeDetail: ResumeDetail = {
  userId: DEMO_USER_ID,
  education: [{ school: "한국대학교", major: "컴퓨터공학", gpa: "3.8", gpaScale: "4.5", graduationStatus: "졸업예정", startDate: "2021-03", endDate: "2025-02" }],
  career: [],
  certificates: [{ name: "정보처리기사", issuer: "한국산업인력공단", acquiredAt: "2024-06" }],
  languages: [{ test: "TOEIC", score: "870", acquiredAt: "2024-09" }],
  awards: [],
  activities: [],
  skills: ["Java", "Spring Boot", "React"],
  portfolios: [{ label: "GitHub", url: "https://github.com/demo" }],
  desiredCondition: { jobCategoryLarge: "개발", jobCategoryMedium: "백엔드", employmentType: "정규직", region: "서울", salaryMin: "3200", salaryMax: "4000", remote: false },
  updatedAt: nowIso(),
};

export const nicknameProfileRoutes: MockRoute[] = [
  // ── 닉네임 프로필 ──
  { method: "GET", pattern: /^\/nicknames$/, handler: () => activeProfiles() },
  {
    method: "POST",
    pattern: /^\/nicknames$/,
    handler: ({ body }) => {
      const req = body as { nickname?: string; bio?: string | null };
      const nickname = (req?.nickname ?? "").trim();
      const created: NicknameProfile = {
        id: ++profileSeq,
        userId: DEMO_USER_ID,
        nickname,
        avatarFileId: null,
        bio: req?.bio ?? null,
        isDefault: activeProfiles().length === 0,
        status: "ACTIVE",
        updatedAt: nowIso(),
      };
      profiles.unshift(created);
      return created;
    },
  },
  {
    method: "PUT",
    pattern: /^\/nicknames\/(\d+)$/,
    handler: ({ params, body }) => {
      const id = Number(params[0]);
      const req = body as { nickname?: string; bio?: string | null };
      const profile = profiles.find((p) => p.id === id);
      if (profile) {
        profile.nickname = (req?.nickname ?? profile.nickname).trim();
        profile.bio = req?.bio ?? null;
        profile.updatedAt = nowIso();
      }
      return profile;
    },
  },
  {
    method: "DELETE",
    pattern: /^\/nicknames\/(\d+)$/,
    handler: ({ params }) => {
      const id = Number(params[0]);
      const profile = profiles.find((p) => p.id === id);
      if (profile) {
        profile.status = "HIDDEN";
        if (profile.isDefault) {
          const next = activeProfiles()[0];
          if (next) {
            profiles.forEach((p) => (p.isDefault = false));
            next.isDefault = true;
          }
        }
      }
      return null;
    },
  },
  {
    method: "POST",
    pattern: /^\/nicknames\/(\d+)\/default$/,
    handler: ({ params }) => {
      const id = Number(params[0]);
      profiles.forEach((p) => (p.isDefault = p.id === id));
      return profiles.find((p) => p.id === id);
    },
  },
  {
    method: "GET",
    pattern: /^\/nicknames\/availability$/,
    handler: ({ query }) => {
      const nickname = (query.get("nickname") ?? "").trim();
      const excludeId = query.get("excludeProfileId");
      return !nicknameTaken(nickname, excludeId ? Number(excludeId) : undefined);
    },
  },

  // ── 채팅방 전용 프로필 ──
  {
    method: "GET",
    pattern: /^\/nicknames\/conversations\/(\d+)$/,
    handler: ({ params }) => resolveConversation(Number(params[0])),
  },
  {
    method: "PUT",
    pattern: /^\/nicknames\/conversations\/(\d+)$/,
    handler: ({ params, body }) => {
      const conversationId = Number(params[0]);
      const req = body as { nicknameProfileId?: number | null };
      const nicknameProfileId = req?.nicknameProfileId ?? null;
      conversationProfiles.set(conversationId, { nicknameProfileId, anonymous: nicknameProfileId == null });
      return resolveConversation(conversationId);
    },
  },

  // ── 계정 정보 ──
  { method: "GET", pattern: /^\/account$/, handler: () => account },
  {
    method: "POST",
    pattern: /^\/account\/login-id$/,
    handler: ({ body }) => {
      const req = body as { loginId?: string };
      account.loginId = (req?.loginId ?? "").trim().toLowerCase();
      account.loginIdSet = true;
      return account;
    },
  },
  {
    method: "POST",
    pattern: /^\/account\/phone$/,
    handler: ({ body }) => {
      const req = body as { phone?: string };
      const digits = (req?.phone ?? "").replace(/[^0-9]/g, "");
      account.phone = digits.length === 11
        ? `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
        : (req?.phone ?? null);
      return account;
    },
  },

  // ── 이력서 상세 ──
  { method: "GET", pattern: /^\/resume-detail$/, handler: () => resumeDetail },
  {
    method: "PUT",
    pattern: /^\/resume-detail$/,
    handler: ({ body }) => {
      const req = body as Partial<ResumeDetail>;
      resumeDetail = { ...resumeDetail, ...req, userId: DEMO_USER_ID, updatedAt: nowIso() };
      return resumeDetail;
    },
  },
];
