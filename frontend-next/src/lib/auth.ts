"use client";

import type { AuthResponse } from "@/lib/types";

export type StoredUser = {
  id?: number;
  username?: string;
  email?: string;
  role?: string;
};

export function getStoredToken() {
  if (typeof window === "undefined") return null;
  return localStorage.getItem("token");
}

export function getStoredUser(): StoredUser | null {
  if (typeof window === "undefined") return null;
  const raw = localStorage.getItem("user");
  if (!raw) return null;

  try {
    return JSON.parse(raw) as StoredUser;
  } catch {
    return null;
  }
}

export function persistAuth(res: AuthResponse, fallbackUsername: string) {
  localStorage.setItem("token", res.token);
  localStorage.setItem(
    "user",
    JSON.stringify({
      id: res.id,
      username: res.username ?? fallbackUsername,
      email: res.email,
      role: res.role,
    })
  );
}

export function clearAuth() {
  if (typeof window === "undefined") return;
  localStorage.removeItem("token");
  localStorage.removeItem("user");
}
