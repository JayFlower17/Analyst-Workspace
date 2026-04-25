"use client";

import type {
  AnalysisResult,
  AuthResponse,
  ApiEnvelope,
  ChatMessage,
  ChatSession,
  Dataset,
  MessageResponse,
  Workspace,
} from "@/lib/types";

function resolveApiBase() {
  if (process.env.NEXT_PUBLIC_API_BASE) {
    return process.env.NEXT_PUBLIC_API_BASE;
  }

  if (typeof window !== "undefined") {
    const { hostname, protocol } = window.location;
    if (hostname === "localhost" || hostname === "127.0.0.1") {
      return `${protocol}//${hostname}:8080/api`;
    }
  }

  return "/backend-api";
}

const API_BASE = resolveApiBase();

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = typeof window !== "undefined" ? localStorage.getItem("token") : null;
  const headers = new Headers(init?.headers ?? {});
  if (!headers.has("Content-Type") && !(init?.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
    cache: "no-store",
  });

  let payload: unknown = null;
  try {
    payload = await res.json();
  } catch {
    payload = null;
  }

  if (!res.ok) {
    if (res.status === 401 && typeof window !== "undefined" && !path.startsWith("/auth/")) {
      localStorage.removeItem("token");
      localStorage.removeItem("user");
      window.location.href = "/";
    }
    const errorMessage =
      (payload as { message?: string } | null)?.message ??
      `Request failed: ${res.status}`;
    throw new Error(errorMessage);
  }
  return payload as T;
}

export const chatApi = {
  createSession(title?: string) {
    return request<ApiEnvelope<ChatSession>>("/chat/sessions", {
      method: "POST",
      body: JSON.stringify({ title }),
    });
  },
  getSessions() {
    return request<ApiEnvelope<ChatSession[]>>("/chat/sessions");
  },
  getMessages(sessionId: number) {
    return request<ApiEnvelope<ChatMessage[]>>(`/chat/sessions/${sessionId}/messages`);
  },
  getDatasets(sessionId: number) {
    return request<ApiEnvelope<Dataset[]>>(`/chat/sessions/${sessionId}/datasets`);
  },
  uploadDataset(sessionId: number, file: File, name?: string) {
    const form = new FormData();
    form.append("file", file);
    if (name) form.append("name", name);
    return request<ApiEnvelope<Dataset>>(`/chat/sessions/${sessionId}/upload`, {
      method: "POST",
      body: form,
    });
  },
  analyze(sessionId: number, query: string, datasetId?: number) {
    return request<AnalysisResult>(`/chat/sessions/${sessionId}/analyze`, {
      method: "POST",
      body: JSON.stringify({ query, datasetId }),
    });
  },
  promote(sessionId: number, workspaceName: string, description?: string) {
    return request<ApiEnvelope<Workspace>>(`/chat/sessions/${sessionId}/promote-to-workspace`, {
      method: "POST",
      body: JSON.stringify({ workspaceName, description }),
    });
  },
};

export const workplaceApi = {
  listWorkspaces() {
    return request<ApiEnvelope<Workspace[]>>("/groups");
  },
  getDatasets(groupId: number) {
    return request<ApiEnvelope<Dataset[]>>(`/groups/${groupId}/datasets`);
  },
  analyze(groupId: number, query: string, focusDatasetIds: number[] = []) {
    return request<AnalysisResult>("/analysis/query", {
      method: "POST",
      body: JSON.stringify({ groupId, query, focusDatasetIds }),
    });
  },
  getRelations(groupId: number) {
    return request<ApiEnvelope<unknown[]>>(`/groups/${groupId}/relations`);
  },
};

export const datasetApi = {
  list(groupId?: number) {
    const query = groupId ? `?groupId=${groupId}` : "";
    return request<ApiEnvelope<Dataset[]>>(`/datasets${query}`);
  },
  upload(file: File, name?: string, groupId?: number) {
    const form = new FormData();
    form.append("file", file);
    if (name) form.append("name", name);
    if (groupId) form.append("groupId", String(groupId));
    return request<ApiEnvelope<Dataset>>("/datasets/upload", {
      method: "POST",
      body: form,
    });
  },
};

export const authApi = {
  signin(username: string, password: string) {
    return request<AuthResponse>("/auth/signin", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  },
  signup(username: string, email: string, password: string, confirmPassword: string) {
    return request<MessageResponse>("/auth/signup", {
      method: "POST",
      body: JSON.stringify({ username, email, password, confirmPassword }),
    });
  },
};
