import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { authApi } from "@/api/auth";
import { setOnAuthExpired, tokenStorage } from "@/api/client";
import type { LoginRequest, RegisterRequest, Role } from "@/types/api";
import { decodeAccessToken, isTokenExpired } from "@/utils/jwt";

export interface AuthUser {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

interface AuthContextValue {
  user: AuthUser | null;
  isLoading: boolean;
  login: (body: LoginRequest) => Promise<void>;
  register: (body: RegisterRequest) => Promise<void>;
  logout: () => Promise<void>;
}

// eslint-disable-next-line react-refresh/only-export-components
export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function userFromAccessToken(accessToken: string, fullName?: string): AuthUser | null {
  const decoded = decodeAccessToken(accessToken);
  if (!decoded || isTokenExpired(decoded.exp)) return null;
  return {
    id: decoded.uid,
    email: decoded.email,
    role: decoded.role,
    fullName: fullName ?? decoded.email,
  };
}

const FULL_NAME_KEY = "ecommerce.fullName";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const accessToken = tokenStorage.getAccessToken();
    const storedFullName = localStorage.getItem(FULL_NAME_KEY) ?? undefined;
    if (accessToken) {
      setUser(userFromAccessToken(accessToken, storedFullName));
    }
    setIsLoading(false);

    setOnAuthExpired(() => {
      tokenStorage.clear();
      localStorage.removeItem(FULL_NAME_KEY);
      setUser(null);
    });
  }, []);

  const login = useCallback(async (body: LoginRequest) => {
    const auth = await authApi.login(body);
    tokenStorage.setTokens(auth.accessToken, auth.refreshToken);
    setUser(userFromAccessToken(auth.accessToken));
  }, []);

  const register = useCallback(async (body: RegisterRequest) => {
    await authApi.register(body);
    const auth = await authApi.login({ email: body.email, password: body.password });
    tokenStorage.setTokens(auth.accessToken, auth.refreshToken);
    localStorage.setItem(FULL_NAME_KEY, body.fullName);
    setUser(userFromAccessToken(auth.accessToken, body.fullName));
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = tokenStorage.getRefreshToken();
    try {
      if (refreshToken) {
        await authApi.logout({ refreshToken });
      }
    } finally {
      tokenStorage.clear();
      localStorage.removeItem(FULL_NAME_KEY);
      setUser(null);
    }
  }, []);

  const value = useMemo(
    () => ({ user, isLoading, login, register, logout }),
    [user, isLoading, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
