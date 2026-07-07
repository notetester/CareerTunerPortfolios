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
  emailVerified: true,
  temporaryEmail: false,
  emailRegistrationRequired: false,
  passwordEnabled: true,
  passwordSetupRequired: false,
  linkedProviders: ["KAKAO"],
};

/* ── 전화번호 SMS OTP(데모: 항상 Mock 제공자로 devCode 반환) ── */

let mockOtp: { phone: string; code: string; attempts: number } | null = null;
const genOtpCode = () => String(Math.floor(Math.random() * 1_000_000)).padStart(6, "0");
const normalizeDigits = (raw: string) => (raw ?? "").replace(/[^0-9]/g, "");

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
  {
    method: "POST",
    pattern: /^\/account\/email-registration$/,
    handler: ({ body }) => {
      const req = body as { email?: string };
      account.email = (req?.email ?? account.email).trim().toLowerCase();
      account.emailVerified = true;
      account.temporaryEmail = false;
      account.emailRegistrationRequired = false;
      return null;
    },
  },
  {
    method: "POST",
    pattern: /^\/account\/social\/([^/]+)\/link-url$/,
    handler: ({ params }) => {
      const provider = String(params[0] ?? "").toUpperCase();
      if (!account.linkedProviders.includes(provider)) {
        account.linkedProviders = [...account.linkedProviders, provider];
      }
      return { url: `/profile/detail?socialLinked=${encodeURIComponent(provider)}&socialMock=1` };
    },
  },
  {
    method: "DELETE",
    pattern: /^\/account\/social\/([^/]+)$/,
    handler: ({ params }) => {
      const provider = String(params[0] ?? "").toUpperCase();
      const hasLocal = account.passwordEnabled && (!!account.loginId || (account.emailVerified && !account.temporaryEmail));
      const remaining = account.linkedProviders.filter((p) => p !== provider);
      if (!hasLocal && remaining.length === 0) {
        throw new Error("연동 해제 후 사용할 수 있는 로그인 수단이 남아 있지 않습니다.");
      }
      account.linkedProviders = remaining;
      return account;
    },
  },
  { method: "POST", pattern: /^\/auth\/password\/reset-request$/, handler: () => null },
  { method: "POST", pattern: /^\/auth\/find-id\/request$/, handler: () => null },
  { method: "GET", pattern: /^\/auth\/find-id\/verify$/, handler: () => ({ loginId: account.loginId ?? "dem***01" }) },
  { method: "GET", pattern: /^\/auth\/check\/email$/, handler: ({ query }) => ({ duplicate: query.get("value") === account.email }) },
  { method: "GET", pattern: /^\/auth\/check\/login-id$/, handler: ({ query }) => ({ duplicate: query.get("value") === account.loginId }) },

  // ── 전화번호 SMS OTP(데모: 실 키 없이 발송→입력→검증 완결) ──
  { method: "GET", pattern: /^\/auth\/phone\/status$/, handler: () => ({ phone: account.phone, phoneVerified: account.phoneVerified }) },
  {
    method: "POST",
    pattern: /^\/auth\/phone\/request-otp$/,
    handler: ({ body }) => {
      const req = body as { phone?: string };
      const phone = normalizeDigits(req?.phone ?? "");
      const code = genOtpCode();
      mockOtp = { phone, code, attempts: 0 };
      // 데모는 항상 Mock 제공자 → devCode 노출.
      return { sent: true, provider: "mock", realSending: false, devCode: code, validitySeconds: 300, cooldownSeconds: 60 };
    },
  },
  {
    method: "POST",
    pattern: /^\/auth\/phone\/verify-otp$/,
    handler: ({ body }) => {
      const req = body as { phone?: string; code?: string };
      const phone = normalizeDigits(req?.phone ?? "");
      const code = (req?.code ?? "").trim();
      if (!mockOtp || mockOtp.phone !== phone) {
        throw new Error("유효한 인증번호가 없습니다. 인증번호를 다시 요청해 주세요.");
      }
      mockOtp.attempts += 1;
      if (mockOtp.code !== code) {
        throw new Error("인증번호가 일치하지 않습니다.");
      }
      account.phone = phone;
      account.phoneVerified = true;
      mockOtp = null;
      return { verified: true, phone };
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
