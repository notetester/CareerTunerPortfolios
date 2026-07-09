// W6 복수 닉네임 프로필 + 채팅방 전용 프로필 + 계정 확충 + 이력서 상세 스펙 타입.

// ── 복수 닉네임 프로필 ──
export interface NicknameProfile {
  id: number;
  userId: number;
  nickname: string;
  avatarFileId: number | null;
  bio: string | null;
  isDefault: boolean;
  status: "ACTIVE" | "HIDDEN";
  updatedAt: string | null;
}

export interface NicknameProfilePayload {
  nickname: string;
  avatarFileId?: number | null;
  bio?: string | null;
}

// ── 채팅방 전용 프로필 ──
export interface ConversationProfile {
  conversationId: number;
  userId: number;
  nicknameProfileId: number | null;
  nickname: string | null;
  anonymous: boolean;
  resolved: boolean;
}

// ── 계정 정보 ──
export interface AccountInfo {
  userId: number;
  email: string | null;
  name: string;
  loginId: string | null;
  loginIdSet: boolean;
  phone: string | null;
  phoneVerified: boolean;
  emailVerified: boolean;
  temporaryEmail: boolean;
  emailRegistrationRequired: boolean;
  passwordEnabled: boolean;
  passwordSetupRequired: boolean;
  linkedProviders: string[];
}

// ── 이력서 상세 스펙(사람인/잡코리아식) ──
export interface ResumeEducation {
  school: string;
  major: string;
  gpa: string;
  gpaScale: string;
  graduationStatus: string;
  startDate: string;
  endDate: string;
}

export interface ResumeCareer {
  company: string;
  role: string;
  employmentType: string;
  startDate: string;
  endDate: string;
  description: string;
}

export interface ResumeCertificate {
  name: string;
  issuer: string;
  acquiredAt: string;
}

export interface ResumeLanguage {
  test: string;
  score: string;
  acquiredAt: string;
}

export interface ResumeAward {
  title: string;
  host: string;
  awardedAt: string;
  description: string;
}

export interface ResumeActivity {
  title: string;
  organization: string;
  role: string;
  startDate: string;
  endDate: string;
  description: string;
}

export interface ResumePortfolio {
  label: string;
  url: string;
}

export interface ResumeDesiredCondition {
  jobCategoryLarge: string;
  jobCategoryMedium: string;
  employmentType: string;
  region: string;
  salaryMin: string;
  salaryMax: string;
  remote: boolean;
}

export interface ResumeDetail {
  userId: number;
  education: ResumeEducation[] | null;
  career: ResumeCareer[] | null;
  certificates: ResumeCertificate[] | null;
  languages: ResumeLanguage[] | null;
  awards: ResumeAward[] | null;
  activities: ResumeActivity[] | null;
  skills: string[] | null;
  portfolios: ResumePortfolio[] | null;
  desiredCondition: ResumeDesiredCondition | null;
  updatedAt: string | null;
}

export interface ResumeDetailPayload {
  education: ResumeEducation[];
  career: ResumeCareer[];
  certificates: ResumeCertificate[];
  languages: ResumeLanguage[];
  awards: ResumeAward[];
  activities: ResumeActivity[];
  skills: string[];
  portfolios: ResumePortfolio[];
  desiredCondition: ResumeDesiredCondition;
}

export const PROVIDER_LABELS: Record<string, string> = {
  KAKAO: "카카오",
  NAVER: "네이버",
  GOOGLE: "구글",
};
