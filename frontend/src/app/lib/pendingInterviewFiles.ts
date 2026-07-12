import { api } from "./api";
import {
  PendingInterviewFileRegistry,
  type PendingInterviewFileCleanupResult,
} from "./pendingInterviewFilesCore";

const registry = new PendingInterviewFileRegistry((fileId, keepalive) =>
  api<void>(`/file/${fileId}`, { method: "DELETE", keepalive }));

export function trackPendingInterviewUpload<T extends { id: number }>(
  request: Promise<T>,
  controller: AbortController,
): Promise<T> {
  return registry.trackUpload(request, controller);
}

export function registerPendingInterviewFile(fileId: number): void {
  registry.register(fileId);
}

export function protectPendingInterviewFile(fileId: number): void {
  registry.protect(fileId);
}

export function releasePendingInterviewFile(fileId: number): void {
  registry.release(fileId);
}

export function markPendingInterviewFileLinked(fileId: number): void {
  registry.markLinked(fileId);
}

export function isPendingInterviewFileProtected(fileId: number): boolean {
  return registry.isProtected(fileId);
}

export function deleteTrackedPendingInterviewFile(fileId: number, keepalive = false): Promise<boolean> {
  return registry.delete(fileId, keepalive);
}

export function discardPendingInterviewFiles(
  keepalive = false,
): Promise<PendingInterviewFileCleanupResult> {
  return registry.abortUploadsAndDiscard(keepalive);
}

export function forgetPendingInterviewFiles(): void {
  registry.forget();
}
