const DEFAULT_RETRY_DELAYS_MS = Object.freeze([0, 100, 250, 500, 1_000, 2_000]);

const waitFor = (delayMs) => new Promise((resolve) => setTimeout(resolve, delayMs));

/**
 * Capacitor App 플러그인이 WebView 첫 렌더 직후 아직 준비되지 않은 경우를 흡수한다.
 * 리스너와 콜드 시작 URL을 독립적으로 재시도하고, 같은 intent가 두 경로로 전달될 때는
 * 짧은 시간 안의 중복 라우팅만 제거한다.
 */
export async function initializeDeepLinkRuntime({
  appPlugin,
  onUrl,
  retryDelaysMs = DEFAULT_RETRY_DELAYS_MS,
  wait = waitFor,
  now = Date.now,
  duplicateWindowMs = 2_000,
}) {
  let listenerRegistered = false;
  let listenerHandle = null;
  let launchUrlCaptured = false;
  let lastDeliveredUrl = "";
  let lastDeliveredAt = Number.NEGATIVE_INFINITY;
  const handledLaunchUrls = new Set();

  const deliver = (rawUrl, source) => {
    const url = typeof rawUrl === "string" ? rawUrl.trim() : "";
    if (!url) return;

    if (source === "launch") {
      if (handledLaunchUrls.has(url)) return;
      handledLaunchUrls.add(url);
    }

    const deliveredAt = now();
    if (url === lastDeliveredUrl && deliveredAt - lastDeliveredAt < duplicateWindowMs) return;
    lastDeliveredUrl = url;
    lastDeliveredAt = deliveredAt;
    onUrl(url);
  };

  for (const delayMs of retryDelaysMs) {
    if (delayMs > 0) await wait(delayMs);

    if (!listenerRegistered) {
      try {
        listenerHandle = await appPlugin.addListener("appUrlOpen", ({ url } = {}) => {
          deliver(url, "event");
        });
        listenerRegistered = true;
      } catch {
        // WebView/플러그인 준비 전이면 다음 제한된 시도에서 다시 등록한다.
      }
    }

    if (!launchUrlCaptured) {
      try {
        const launch = await appPlugin.getLaunchUrl();
        if (launch?.url) {
          launchUrlCaptured = true;
          deliver(launch.url, "launch");
        }
      } catch {
        // MainActivity의 최초 intent가 bridge에 반영되기 전이면 다시 조회한다.
      }
    }

    if (listenerRegistered && launchUrlCaptured) break;
  }

  return { listenerRegistered, launchUrlCaptured, listenerHandle };
}

export const deepLinkRetryDelaysMs = DEFAULT_RETRY_DELAYS_MS;
