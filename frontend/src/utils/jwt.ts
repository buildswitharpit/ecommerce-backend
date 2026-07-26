import type { Role } from "@/types/api";

export interface DecodedAccessToken {
  uid: number;
  email: string;
  role: Role;
  exp: number;
}

/**
 * Decodes the access token's payload for display purposes only (name/role/expiry).
 * This is not a security boundary -- every request is still verified server-side.
 */
export function decodeAccessToken(token: string): DecodedAccessToken | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const json = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return {
      uid: Number(json.uid),
      email: String(json.sub),
      role: json.role as Role,
      exp: Number(json.exp),
    };
  } catch {
    return null;
  }
}

export function isTokenExpired(exp: number): boolean {
  return Date.now() >= exp * 1000;
}
