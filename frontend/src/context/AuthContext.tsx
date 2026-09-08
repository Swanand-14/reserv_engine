import { createContext, useContext, useState, useEffect, type ReactNode } from "react";
import type { UserResponse } from "../types";
import * as authApi from "../api/auth";

interface AuthContextValue {
  user: UserResponse | null;
  loading: boolean; // true while the initial /users/me check is in flight
  login: (email: string, password: string) => Promise<void>;
  signup: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [loading, setLoading] = useState(true);

  // On every full page load, ask the backend who the cookie belongs to.
  // No localStorage read — the cookie is the only source of truth, and
  // the backend is the only party that can actually verify it.
  useEffect(() => {
    authApi.getCurrentUser()
      .then(setUser)
      .catch(() => setUser(null)) // no cookie, or expired/invalid — not logged in
      .finally(() => setLoading(false));
  }, []);

  async function login(email: string, password: string) {
    const u = await authApi.login(email, password);
    setUser(u);
  }

  async function signup(email: string, password: string) {
    await authApi.signup(email, password);
    await login(email, password);
  }

  async function logout() {
    await authApi.logout();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, login, signup, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}