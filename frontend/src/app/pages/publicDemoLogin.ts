export type PublicDemoRole = "user" | "admin";

export interface PublicDemoAccount {
  role: PublicDemoRole;
  label: string;
  description: string;
  email: string;
  password: string;
  defaultDestination: "/dashboard" | "/admin";
}

export interface PublicDemoAutoEntry {
  role: PublicDemoRole;
  returnTo: string | null;
  searchAfterConsumption: string;
}

export const PUBLIC_DEMO_PASSWORD = "demo1234";

const PUBLIC_DEMO_ACCOUNTS: readonly PublicDemoAccount[] = [
  {
    role: "user",
    label: "사용자 데모",
    description: "지원 현황, AI 분석, 면접과 커리어 플랜을 체험합니다.",
    email: "demo@careertuner.dev",
    password: PUBLIC_DEMO_PASSWORD,
    defaultDestination: "/dashboard",
  },
  {
    role: "admin",
    label: "관리자 데모",
    description: "권한이 적용된 운영 대시보드와 관리 기능을 체험합니다.",
    email: "admin@careertuner.dev",
    password: PUBLIC_DEMO_PASSWORD,
    defaultDestination: "/admin",
  },
] as const;

export function getPublicDemoAccounts(mockEnabled: boolean): readonly PublicDemoAccount[] {
  return mockEnabled ? PUBLIC_DEMO_ACCOUNTS : [];
}

export function getPublicDemoAccount(role: PublicDemoRole): PublicDemoAccount {
  return PUBLIC_DEMO_ACCOUNTS.find((account) => account.role === role) as PublicDemoAccount;
}

export function safePublicDemoReturnTo(returnTo: string | null, fallback: string): string {
  if (!returnTo || !returnTo.startsWith("/") || returnTo.startsWith("//")) return fallback;
  if (returnTo.includes("\\") || /[\u0000-\u001f\u007f]/.test(returnTo)) return fallback;

  try {
    const parsed = new URL(returnTo, "https://careertuner.local");
    if (parsed.origin !== "https://careertuner.local") return fallback;

    const decodedPath = decodeURIComponent(parsed.pathname);
    if (decodedPath.startsWith("//") || decodedPath.includes("\\")) return fallback;

    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return fallback;
  }
}

export function resolvePublicDemoDestination(role: PublicDemoRole, returnTo: string | null): string {
  const account = getPublicDemoAccount(role);
  return safePublicDemoReturnTo(returnTo, account.defaultDestination);
}

export function consumePublicDemoAutoEntry(
  search: string,
  mockEnabled: boolean,
  alreadyAttempted: boolean,
): PublicDemoAutoEntry | null {
  if (!mockEnabled || alreadyAttempted) return null;

  const params = new URLSearchParams(search);
  const role = params.get("demo");
  if (role !== "user" && role !== "admin") return null;

  const returnTo = params.get("returnTo");
  params.delete("demo");
  const remainingSearch = params.toString();

  return {
    role,
    returnTo,
    searchAfterConsumption: remainingSearch ? `?${remainingSearch}` : "",
  };
}
