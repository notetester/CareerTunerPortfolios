export const FRONTEND_CLIENT_HEADER = "X-CareerTuner-Frontend-Client";

export function applyFrontendClientHeader(headers, native) {
  if (native && !headers.has(FRONTEND_CLIENT_HEADER)) {
    headers.set(FRONTEND_CLIENT_HEADER, "native");
  }
  return headers;
}
