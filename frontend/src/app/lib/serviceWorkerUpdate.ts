export function installServiceWorkerUpdateReload(
  serviceWorker = typeof navigator !== "undefined" && "serviceWorker" in navigator
    ? navigator.serviceWorker
    : null,
  reload = () => window.location.reload(),
) {
  if (!serviceWorker) return;

  let controller = serviceWorker.controller;
  let reloadStarted = false;

  serviceWorker.addEventListener("controllerchange", () => {
    const nextController = serviceWorker.controller;
    if (!nextController || nextController === controller) return;

    if (!controller) {
      controller = nextController;
      return;
    }
    controller = nextController;
    if (reloadStarted) return;

    reloadStarted = true;
    reload();
  });
}
