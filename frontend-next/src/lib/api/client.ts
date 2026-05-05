"use client";

import type {
  AnalysisResult,
  Artifact,
  ArtifactDetail,
  ArtifactMemory,
  ContextTrace,
  AuthResponse,
  ApiEnvelope,
  ChatMessage,
  ChatSession,
  Dataset,
  DatasetInfo,
  DatasetPreviewRow,
  DatasetRelation,
  DocumentAsset,
  DocumentChunk,
  DocumentSearchResult,
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
  updateSession(sessionId: number, title: string) {
    return request<ApiEnvelope<ChatSession>>(`/chat/sessions/${sessionId}`, {
      method: "PATCH",
      body: JSON.stringify({ title }),
    });
  },
  deleteSession(sessionId: number) {
    return request<ApiEnvelope<MessageResponse>>(`/chat/sessions/${sessionId}`, {
      method: "DELETE",
    });
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
  createWorkspace(name: string, description?: string) {
    return request<ApiEnvelope<Workspace>>("/groups", {
      method: "POST",
      body: JSON.stringify({ name, description }),
    });
  },
  getWorkspace(groupId: number) {
    return request<ApiEnvelope<Workspace>>(`/groups/${groupId}`);
  },
  getDatasets(groupId: number) {
    return request<ApiEnvelope<Dataset[]>>(`/groups/${groupId}/datasets`);
  },
  updateDescription(groupId: number, descriptionMd: string) {
    return request<ApiEnvelope<Workspace>>(`/groups/${groupId}/description`, {
      method: "PUT",
      body: JSON.stringify({ descriptionMd }),
    });
  },
  analyze(groupId: number, query: string, focusDatasetIds: number[] = []) {
    return request<AnalysisResult>("/analysis/query", {
      method: "POST",
      body: JSON.stringify({ groupId, query, focusDatasetIds }),
    });
  },
  getRelations(groupId: number) {
    return request<ApiEnvelope<DatasetRelation[]>>(`/groups/${groupId}/relations`);
  },
  autoDetectRelations(groupId: number) {
    return request<ApiEnvelope<DatasetRelation[]>>(`/groups/${groupId}/relations/auto-detect`, {
      method: "POST",
    });
  },
  createRelation(
    groupId: number,
    payload: Omit<DatasetRelation, "id" | "groupId">
  ) {
    return request<ApiEnvelope<DatasetRelation>>(`/groups/${groupId}/relations`, {
      method: "POST",
      body: JSON.stringify(payload),
    });
  },
  updateRelation(
    groupId: number,
    relationId: number,
    payload: Omit<DatasetRelation, "id" | "groupId">
  ) {
    return request<ApiEnvelope<DatasetRelation>>(`/groups/${groupId}/relations/${relationId}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    });
  },
  deleteRelation(groupId: number, relationId: number) {
    return request<ApiEnvelope<MessageResponse>>(`/groups/${groupId}/relations/${relationId}`, {
      method: "DELETE",
    });
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
  get(datasetId: number) {
    return request<ApiEnvelope<Dataset>>(`/datasets/${datasetId}`);
  },
  getMetadata(datasetId: number) {
    return request<ApiEnvelope<DatasetInfo>>(`/datasets/${datasetId}/metadata`);
  },
  updateDescription(datasetId: number, descriptionMd: string) {
    return request<ApiEnvelope<Dataset>>(`/datasets/${datasetId}/description`, {
      method: "PUT",
      body: JSON.stringify({ descriptionMd }),
    });
  },
  delete(datasetId: number) {
    return request<ApiEnvelope<MessageResponse>>(`/datasets/${datasetId}`, {
      method: "DELETE",
    });
  },
  preview(datasetId: number, limit = 100) {
    return request<ApiEnvelope<DatasetPreviewRow[]>>(`/analysis/preview/${datasetId}?limit=${limit}`);
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

export const documentApi = {
  list(groupId: number) {
    return request<ApiEnvelope<DocumentAsset[]>>(`/documents?groupId=${groupId}`);
  },
  upload(groupId: number, file: File, name?: string) {
    const form = new FormData();
    form.append("file", file);
    form.append("groupId", String(groupId));
    if (name) form.append("name", name);
    return request<ApiEnvelope<DocumentAsset>>("/documents/upload", {
      method: "POST",
      body: form,
    });
  },
  getChunks(documentId: number) {
    return request<ApiEnvelope<DocumentChunk[]>>(`/documents/${documentId}/chunks`);
  },
  search(groupId: number, query: string, topK = 5) {
    const params = new URLSearchParams({
      groupId: String(groupId),
      query,
      topK: String(topK),
    });
    return request<ApiEnvelope<DocumentSearchResult[]>>(`/documents/search?${params.toString()}`);
  },
  delete(documentId: number) {
    return request<ApiEnvelope<MessageResponse>>(`/documents/${documentId}`, {
      method: "DELETE",
    });
  },
};

export const artifactApi = {
  recent({
    sessionId,
    groupId,
    limit = 5,
    status = "ACTIVE",
  }: {
    sessionId?: number;
    groupId?: number;
    limit?: number;
    status?: "ACTIVE" | "ARCHIVED" | "DELETED";
  }) {
    const params = new URLSearchParams();
    if (sessionId) params.set("sessionId", String(sessionId));
    if (groupId) params.set("groupId", String(groupId));
    params.set("limit", String(limit));
    params.set("status", status);
    return request<ApiEnvelope<Artifact[]>>(`/artifacts/recent?${params.toString()}`);
  },
  detail(id: number) {
    return request<ApiEnvelope<ArtifactDetail>>(`/artifacts/${id}`);
  },
  archive(id: number) {
    return request<ApiEnvelope<ArtifactDetail>>(`/artifacts/${id}/archive`, { method: "PATCH" });
  },
  restore(id: number) {
    return request<ApiEnvelope<ArtifactDetail>>(`/artifacts/${id}/restore`, { method: "PATCH" });
  },
  delete(id: number) {
    return request<ApiEnvelope<ArtifactDetail>>(`/artifacts/${id}`, { method: "DELETE" });
  },
};

export const contextTraceApi = {
  detail(id: number) {
    return request<ApiEnvelope<ContextTrace>>(`/context-traces/${id}`);
  },
  recent(groupId: number, limit = 5) {
    const params = new URLSearchParams({
      groupId: String(groupId),
      limit: String(limit),
    });
    return request<ApiEnvelope<ContextTrace[]>>(`/context-traces/recent?${params.toString()}`);
  },
};

export const artifactMemoryApi = {
  recent({
    groupId,
    limit = 10,
    status = "ACTIVE",
  }: {
    groupId: number;
    limit?: number;
    status?: "ACTIVE" | "ARCHIVED" | "SUPERSEDED" | "DELETED";
  }) {
    const params = new URLSearchParams({
      groupId: String(groupId),
      limit: String(limit),
      status,
    });
    return request<ApiEnvelope<ArtifactMemory[]>>(`/artifact-memories/recent?${params.toString()}`);
  },
  detail(id: number) {
    return request<ApiEnvelope<ArtifactMemory>>(`/artifact-memories/${id}`);
  },
  archive(id: number) {
    return request<ApiEnvelope<ArtifactMemory>>(`/artifact-memories/${id}/archive`, { method: "PATCH" });
  },
  restore(id: number) {
    return request<ApiEnvelope<ArtifactMemory>>(`/artifact-memories/${id}/restore`, { method: "PATCH" });
  },
  supersede(id: number) {
    return request<ApiEnvelope<ArtifactMemory>>(`/artifact-memories/${id}/supersede`, { method: "PATCH" });
  },
  updateImportance(id: number, importance: number) {
    const params = new URLSearchParams({ importance: String(importance) });
    return request<ApiEnvelope<ArtifactMemory>>(`/artifact-memories/${id}/importance?${params.toString()}`, {
      method: "PATCH",
    });
  },
  delete(id: number) {
    return request<ApiEnvelope<ArtifactMemory>>(`/artifact-memories/${id}`, { method: "DELETE" });
  },
};
