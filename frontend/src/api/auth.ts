import { apiClient } from "@/api/client";
import type {
  AuthResponse,
  LoginRequest,
  RefreshRequest,
  RegisterRequest,
  UserResponse,
} from "@/types/api";

export const authApi = {
  register: (body: RegisterRequest) =>
    apiClient.post<UserResponse>("/auth/register", body).then((r) => r.data),

  login: (body: LoginRequest) =>
    apiClient.post<AuthResponse>("/auth/login", body).then((r) => r.data),

  refresh: (body: RefreshRequest) =>
    apiClient.post<AuthResponse>("/auth/refresh", body).then((r) => r.data),

  logout: (body: RefreshRequest) => apiClient.post<void>("/auth/logout", body),
};
