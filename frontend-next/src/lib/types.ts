export type ApiEnvelope<T> = {
  success: boolean;
  message?: string;
  data?: T;
};

export type ChatSession = {
  id: number;
  title?: string;
  status?: string;
  createdAt?: string;
  updatedAt?: string;
  expiresAt?: string;
};

export type ChatMessage = {
  id: number;
  sessionId: number;
  role: "USER" | "ASSISTANT" | "SYSTEM" | string;
  content: string;
  createdAt?: string;
};

export type Dataset = {
  id: number;
  groupId?: number | null;
  name: string;
  tableName: string;
  originalFileName?: string;
  rowCount?: number;
  columnCount?: number;
  descriptionMd?: string;
  createdAt?: string;
};

export type Workspace = {
  id: number;
  name: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type AuthResponse = {
  token: string;
  type?: string;
  id?: number;
  username?: string;
  email?: string;
  role?: string;
};

export type MessageResponse = {
  message?: string;
};

export type AnalysisResult = {
  success: boolean;
  message?: string;
  data: Record<string, unknown>[];
  generatedSql?: string;
  generatedCodeOrSql?: string;
  summary?: string;
  recommendedChart?: string;
  executionTime?: number;
  artifactId?: number;
};
