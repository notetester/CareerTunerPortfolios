export interface AdminUserProfile {
  id?: number;
  userId?: number;
  desiredJob?: string | null;
  desiredIndustry?: string | null;
  education?: unknown;
  career?: unknown;
  projects?: unknown;
  skills?: unknown;
  certificates?: unknown;
  languages?: unknown;
  portfolioLinks?: unknown;
  resumeText?: string | null;
  selfIntro?: string | null;
  preferences?: unknown;
  versionNo?: number | null;
  updatedAt?: string | null;
}

export interface AdminUserProfileVersion extends AdminUserProfile {
  id: number;
  userId: number;
  versionNo: number;
  source: string;
  createdAt: string;
}
