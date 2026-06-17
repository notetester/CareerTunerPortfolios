import { RouterProvider } from "react-router";
import { AuthProvider } from "./auth/AuthContext";
import { AppLockGate } from "./components/AppLockGate";
import { router } from "./routes";

export default function App() {
  return (
    <AuthProvider>
      <AppLockGate>
        <RouterProvider router={router} />
      </AppLockGate>
    </AuthProvider>
  );
}
