export interface ApplicationCase {
  id: number;
  companyName: string;
  jobTitle: string;
  postingDate: string | null;
  sourceType: string;
  status: string;
  favorite: boolean;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface JobPosting {
  id: number;
  applicationCaseId: number;
  originalText: string | null;
  uploadedFileUrl: string | null;
  extractedText: string | null;
  sourceType: string;
  createdAt: string | null;
}

export interface JobAnalysis {
  id: number;
  applicationCaseId: number;
  employmentType: string | null;
  experienceLevel: string | null;
  requiredSkills: string | null;
  preferredSkills: string | null;
  duties: string | null;
  qualifications: string | null;
  difficulty: string | null;
  summary: string | null;
  createdAt: string | null;
}

export interface ApplicationFitAnalysis {
  id: number;
  applicationCaseId: number;
  fitScore: number | null;
  matchedSkills: string | null;
  missingSkills: string | null;
  recommendedStudy: string | null;
  recommendedCertificates: string | null;
  strategy: string | null;
  createdAt: string | null;
}

export interface ApplicationAnalysis {
  applicationCase: ApplicationCase;
  jobAnalysis: JobAnalysis | null;
  fitAnalysis: ApplicationFitAnalysis | null;
}
