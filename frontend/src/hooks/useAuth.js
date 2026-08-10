import { useEffect, useState } from "react";
import { ensureSession, login, register, logout } from "../auth.js";

/**
 * Owns the guest/user session lifecycle: minting a guest session on load,
 * and the login/register/logout flows that can upgrade or reset it.
 *
 * Pure extraction from App.jsx — no behavior changes.
 */
export function useAuth() {
  const [playerId, setPlayerId] = useState(null);
  const [authToken, setAuthToken] = useState(null);
  const [authRole, setAuthRole] = useState("GUEST");
  const [authReady, setAuthReady] = useState(false);
  const [authInitError, setAuthInitError] = useState(null);

  const [showLogin, setShowLogin] = useState(false);
  const [loginUsername, setLoginUsername] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [authError, setAuthError] = useState(null);

  
  useEffect(() => {
    ensureSession()
      .then((session) => {
        setPlayerId(session.playerId);
        setAuthToken(session.token);
        setAuthRole(session.role);
        setAuthReady(true);
      })
      .catch((err) => setAuthInitError(err.message));
  }, []);

  const applySession = (session) => {
    setPlayerId(session.playerId);
    setAuthToken(session.token);
    setAuthRole(session.role);
    setShowLogin(false);
    setAuthError(null);
  };

  const handleLogin = () => {
    setAuthError(null);
    login(loginUsername.trim(), loginPassword)
      .then(applySession)
      .catch((err) => setAuthError(err.message));
  };

  const handleRegister = () => {
    setAuthError(null);
    register(loginUsername.trim(), loginPassword)
      .then(applySession)
      .catch((err) => setAuthError(err.message));
  };

  const handleLogout = () => {
    logout();
    setShowLogin(false);
    ensureSession().then(applySession);
  };

  return {
    playerId,
    authToken,
    authRole,
    authReady,
    authInitError,
    showLogin,
    setShowLogin,
    loginUsername,
    setLoginUsername,
    loginPassword,
    setLoginPassword,
    authError,
    handleLogin,
    handleRegister,
    handleLogout,
  };
}