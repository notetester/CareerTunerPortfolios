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
    initNativeShell();
  }, []);

  return (
    <ThemeProvider attribute="class" defaultTheme="light" enableSystem={false} disableTransitionOnChange>
      <AuthProvider>
        <AppLockGate>
          <RouterProvider router={router} />
        </AppLockGate>
      </AuthProvider>
    </ThemeProvider>
  );
}
