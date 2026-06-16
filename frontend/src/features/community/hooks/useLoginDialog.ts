import { useState, useCallback } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "@/app/auth/AuthContext";

export function useLoginDialog() {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [showLoginDialog, setShowLoginDialog] = useState(false);

  const requireAuth = useCallback(
    (action: () => void) => {
      if (isAuthenticated) {
        action();
      } else {
        setShowLoginDialog(true);
      }
    },
    [isAuthenticated],
  );

  const onLoginConfirm = useCallback(() => {
    setShowLoginDialog(false);
    navigate("/login");
  }, [navigate]);

  const onLoginCancel = useCallback(() => {
    setShowLoginDialog(false);
  }, []);

  return { showLoginDialog, requireAuth, onLoginConfirm, onLoginCancel, isAuthenticated };
}
