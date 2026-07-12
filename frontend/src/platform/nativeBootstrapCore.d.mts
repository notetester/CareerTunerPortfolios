export function waitForNativePlatform(options: {
  isNative: () => boolean;
  retryDelaysMs?: readonly number[];
  wait?: (delayMs: number) => Promise<void>;
}): Promise<boolean>;

export function bootstrapNativeRuntime(options: {
  isNative: () => boolean;
  initializers: readonly Array<() => void>;
  retryDelaysMs?: readonly number[];
  wait?: (delayMs: number) => Promise<void>;
}): Promise<boolean>;

export const nativeReadyRetryDelaysMs: readonly number[];
