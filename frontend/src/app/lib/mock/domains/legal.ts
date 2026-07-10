import { mockLegalDocuments } from "@/features/legal/data/mockLegalApi";
import { isLegalDocType } from "@/features/legal/api/legalApi";
import type { MockRoute } from "../registry";

export const legalRoutes: MockRoute[] = [
  {
    method: "GET",
    pattern: /^\/legal\/([^/]+)$/,
    handler: ({ params }) => {
      const docType = params[0] ?? "";
      return isLegalDocType(docType) ? mockLegalDocuments[docType] : null;
    },
  },
];
