export type DeepLinkAppPlugin = {
  addListener(
    eventName: "appUrlOpen",
    listener: (event?: { url?: string | null }) => void,
  ): Promise<unknown>;
  getLaunchUrl(): Promise<{ url?: string | null } | undefined>;
};

export type DeepLinkRuntimeResult = {
  listenerRegistered: boolean;
  launchUrlCaptured: boolean;
  listenerHandle: unknown;
};

export function initializeDeepLinkRuntime(options: {
  appPlugin: DeepLinkAppPlugin;
  onUrl: (url: string) => void;
  retryDelaysMs?: readonly number[];
  wait?: (delayMs: number) => Promise<void>;
  now?: () => number;
  duplicateWindowMs?: number;
}): Promise<DeepLinkRuntimeResult>;

export const deepLinkRetryDelaysMs: readonly number[];
