import { useEffect } from "react";
import { RouterProvider } from "react-router";
import { ThemeProvider } from "next-themes";
import { AuthProvider } from "./auth/AuthContext";
import { AppLockGate } from "./components/AppLockGate";
import { router } from "./routes";
import { initNativeShell } from "@/platform/nativeShell";

export default function App() {
  // 네이티브(Capacitor) 셸 초기화 — 상태바·스플래시·키보드·하드웨어 뒤로가기. 웹은 무해 no-op.
  // 첫 렌더 후 호출해야 스플래시가 빈 화면이 아닌 실제 UI 위에서 사라진다.
  useEffect(() => {
    // 웹에서 ?home/?ob 로 앱을 미리보면, 그 세션 동안 앱 컨텍스트로 취급(로고·네비 분기 확인용).
    const sp = new URLSearchParams(window.location.search);
    if (sp.has("home") || sp.has("ob")) {
      try {
        sessionStorage.setItem("ct.appPreview", "1");
      } catch {
        /* ignore */
      }
    }
    initNativeShell();
  }, []);

  return (
    <ThemeProvider attribute="class" defaultTheme="dark" enableSystem={false} disableTransitionOnChange>
      <AuthProvider>
        <AppLockGate>
          <RouterProvider router={router} />
        </AppLockGate>
      </AuthProvider>
    </ThemeProvider>
  );
}
