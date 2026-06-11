import { api } from "@/app/lib/api";
import type { AdminPromptView } from "./types";

export function getProfilePromptView(): Promise<AdminPromptView> {
  return api<AdminPromptView>("/admin/prompts/profile", { method: "GET" });
}
