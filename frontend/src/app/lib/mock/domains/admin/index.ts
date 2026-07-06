import type { MockRoute } from "../../registry";
// admin 도메인 라우트 집합(그룹별 파일을 여기서 합친다). 공통 인프라, additive.
import { adminCoreRoutes } from "./core";
import { adminUsersRoutes } from "./users";
import { adminContentRoutes } from "./content";
import { adminBillingRoutes } from "./billing";
import { adminAnalysisOpsRoutes } from "./analysisOps";
import { adminInterviewOpsRoutes } from "./interviewOps";
import { adminPromptsRoutes } from "./prompts";
import { adminPermissionRoutes } from "./permission";
import { adminCorrectionRoutes } from "./corrections";
import { adminCreditRoutes } from "./credits";

export const adminRoutes: MockRoute[] = [
  ...adminCoreRoutes,
  ...adminPermissionRoutes,
  ...adminUsersRoutes,
  ...adminContentRoutes,
  ...adminBillingRoutes,
  ...adminAnalysisOpsRoutes,
  ...adminInterviewOpsRoutes,
  ...adminPromptsRoutes,
  ...adminCorrectionRoutes,
  ...adminCreditRoutes,
];
