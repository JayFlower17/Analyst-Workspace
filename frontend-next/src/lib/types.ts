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

export type DatasetPreviewRow = Record<string, string | number | boolean | null>;

export type DatasetColumnInfo = {
  name: string;
  type?: string;
  distinctCount?: number;
  minValue?: string;
  maxValue?: string;
};

export type DatasetInfo = {
  id: number;
  name: string;
  tableName: string;
  rowCount?: number;
  columnCount?: number;
  columns?: DatasetColumnInfo[];
};

export type DatasetRelation = {
  id?: number;
  groupId?: number;
  sourceDatasetId: number;
  sourceTableName: string;
  sourceColumnName: string;
  targetDatasetId: number;
  targetTableName: string;
  targetColumnName: string;
  relationType?: string;
  confidence?: number | null;
};

export type DocumentAsset = {
  id: number;
  groupId: number;
  name: string;
  originalFileName?: string;
  storagePath?: string;
  fileType?: string;
  mimeType?: string;
  fileSize?: number;
  processingStatus?: string;
  processingError?: string;
  chunkCount?: number;
  createdAt?: string;
  updatedAt?: string;
};

export type DocumentChunk = {
  id: number;
  documentId: number;
  chunkIndex: number;
  chunkText: string;
  embeddingStatus?: string;
  createdAt?: string;
};

export type DocumentSearchResult = {
  documentId: number;
  documentName: string;
  fileType?: string;
  chunkIndex: number;
  endChunkIndex?: number;
  sourceChunkCount?: number;
  chunkText: string;
  score?: number;
  rerankScore?: number;
  rerankNotes?: string;
  retrievalMode?: string;
  queryIntent?: string;
  documentRole?: string;
};

export type Artifact = {
  id: number;
  mode?: string;
  sessionId?: number | null;
  groupId?: number | null;
  datasetId?: number | null;
  userQuery?: string;
  generatedCodeOrSql?: string;
  summary?: string;
  chartType?: string;
  resultPreviewJson?: string;
  artifactSchemaVersion?: number | null;
  analysisReportJson?: string | null;
  evidenceSummaryJson?: string | null;
  executionLogsJson?: string | null;
  validationReportJson?: string | null;
  riskNoticesJson?: string | null;
  artifactStatus?: "ACTIVE" | "ARCHIVED" | "DELETED" | string | null;
  archivedAt?: string | null;
  deletedAt?: string | null;
  updatedAt?: string | null;
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

export type AnalysisEvidenceSummary = {
  groupId?: number;
  datasetCount?: number;
  relationCount?: number;
  datasetNames?: string[];
  documentStrategy?: string;
  documentChunkCount?: number;
  documentNames?: string[];
  hasSemanticContext?: boolean;
};

export type AnalysisValidationFinding = {
  code?: string;
  severity?: string;
  message?: string;
};

export type AnalysisValidationReport = {
  passed?: boolean;
  findings?: AnalysisValidationFinding[];
};

export type RiskNotice = {
  code?: string;
  severity?: string;
  message?: string;
  source?: string;
};

export type ToolExecutionLog = {
  toolType?: string;
  toolName?: string;
  stepType?: string;
  success?: boolean;
  message?: string;
  durationMs?: number;
  input?: Record<string, unknown>;
  output?: Record<string, unknown>;
  inputSummary?: string;
  outputSummary?: string;
};

export type AnalysisReport = {
  summary?: string;
  data?: Record<string, unknown>[];
  rowCount?: number;
  recommendedChart?: string;
  generatedCodeOrSql?: string;
  generatedCodeOrSqlPresent?: boolean;
  evidence?: AnalysisEvidenceSummary;
  validationReport?: AnalysisValidationReport;
  riskNotices?: RiskNotice[];
  executionLogs?: ToolExecutionLog[];
};

export type ArtifactDetail = Artifact & {
  resultPreview?: Record<string, unknown>[];
  reportAvailable?: boolean;
  analysisReport?: AnalysisReport | null;
  evidence?: AnalysisEvidenceSummary | null;
  executionLogs?: ToolExecutionLog[];
  validationReport?: AnalysisValidationReport | null;
  riskNotices?: RiskNotice[];
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
  analysisReport?: AnalysisReport;
  validationReport?: AnalysisValidationReport;
  riskNotices?: RiskNotice[];
  executionLogs?: ToolExecutionLog[];
};
