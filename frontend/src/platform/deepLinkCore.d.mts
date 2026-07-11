export function toAppPath(rawUrl: string): string | null;
export function isNativeOAuthCallbackPath(path: string | null): boolean;
export function isNativeSocialLinkCallbackPath(path: string | null): boolean;
export const canonicalAppLinkHost: string;
export const nativeOAuthCallbackPath: string;
export const nativeSocialLinkCallbackPath: string;
