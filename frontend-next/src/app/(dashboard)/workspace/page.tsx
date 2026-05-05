"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Archive,
  CheckCircle2,
  CircleAlert,
  Clock3,
  Database,
  Eye,
  FileSearch,
  FileText,
  FolderTree,
  GitBranch,
  ListChecks,
  Loader2,
  Minus,
  PencilLine,
  Plus,
  RefreshCw,
  RotateCcw,
  Search,
  Sparkles,
  Trash2,
  Upload,
  WandSparkles,
} from "lucide-react";
import { toast } from "sonner";
import { artifactApi, artifactMemoryApi, datasetApi, documentApi, workplaceApi } from "@/lib/api/client";
import type {
  AnalysisEvidenceSummary,
  AnalysisResult,
  AnalysisValidationReport,
  Artifact,
  ArtifactDetail,
  ArtifactMemory,
  ContextTrace,
  Dataset,
  DatasetColumnInfo,
  DatasetRelation,
  DocumentAsset,
  DocumentChunk,
  DocumentSearchResult,
  RiskNotice,
  ToolExecutionLog,
  Workspace,
} from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { useLanguage } from "@/components/providers/language-provider";

type RelationFormState = {
  sourceDatasetId: number | null;
  sourceColumnName: string;
  targetDatasetId: number | null;
  targetColumnName: string;
  relationType: string;
  confidence: string;
};

const emptyRelationForm: RelationFormState = {
  sourceDatasetId: null,
  sourceColumnName: "",
  targetDatasetId: null,
  targetColumnName: "",
  relationType: "",
  confidence: "",
};

type EvidenceSourcesPanelProps = {
  evidence?: AnalysisEvidenceSummary;
};

type AnalysisProcessPanelProps = {
  executionLogs?: ToolExecutionLog[];
  validationReport?: AnalysisValidationReport;
  riskNotices?: RiskNotice[];
};

type ContextTracePanelProps = {
  trace?: ContextTrace | null;
};

type TraceMemorySelection = {
  id?: number;
  memoryType?: string;
  scope?: string;
  retrievalMode?: string;
  retrievalScore?: number;
  retrievalReason?: string;
  importance?: number;
  confidence?: number;
  summary?: string;
};

type ArtifactSchemaFilter = "all" | "phase6" | "legacy";
type ArtifactStatusFilter = "ACTIVE" | "ARCHIVED" | "DELETED";
type MemoryStatusFilter = "ACTIVE" | "ARCHIVED" | "SUPERSEDED" | "DELETED";
type AssetView = "tables" | "documents" | "artifacts" | "memories";
type MemoryAction = "archive" | "restore" | "supersede" | "delete";

type ArtifactFilterControlsProps = {
  search: string;
  schemaFilter: ArtifactSchemaFilter;
  statusFilter: ArtifactStatusFilter;
  totalCount: number;
  filteredCount: number;
  refreshing: boolean;
  disabled?: boolean;
  onSearchChange: (value: string) => void;
  onSchemaFilterChange: (value: ArtifactSchemaFilter) => void;
  onStatusFilterChange: (value: ArtifactStatusFilter) => void;
  onRefresh: () => void;
};

type ArtifactCardGridProps = {
  artifacts: Artifact[];
  emptyText: string;
  gridClassName?: string;
  onOpenDetail: (artifact: Artifact) => void;
};

type MemoryManagementPanelProps = {
  memories: ArtifactMemory[];
  filteredMemories: ArtifactMemory[];
  search: string;
  statusFilter: MemoryStatusFilter;
  refreshing: boolean;
  actionLoadingId: number | null;
  disabled?: boolean;
  compact?: boolean;
  onSearchChange: (value: string) => void;
  onStatusFilterChange: (value: MemoryStatusFilter) => void;
  onRefresh: () => void;
  onStatusAction: (memory: ArtifactMemory, action: MemoryAction) => void;
  onImportanceChange: (memory: ArtifactMemory, delta: number) => void;
};

function parseArtifactRows(resultPreviewJson?: string): Record<string, unknown>[] {
  if (!resultPreviewJson) return [];
  try {
    const parsed = JSON.parse(resultPreviewJson) as unknown;
    return Array.isArray(parsed) ? (parsed as Record<string, unknown>[]) : [];
  } catch {
    return [];
  }
}

function parseJsonList(value?: string): string[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed.map((item) => String(item));
  } catch {
    return [];
  }
}

function parseTraceMemorySelections(value?: string): TraceMemorySelection[] {
  if (!value) return [];
  try {
    const parsed = JSON.parse(value) as unknown;
    if (!parsed || typeof parsed !== "object" || !("selectedMemories" in parsed)) return [];
    const selectedMemories = (parsed as { selectedMemories?: unknown }).selectedMemories;
    if (!Array.isArray(selectedMemories)) return [];
    return selectedMemories
      .filter((item): item is Record<string, unknown> => Boolean(item) && typeof item === "object")
      .map((item) => ({
        id: typeof item.id === "number" ? item.id : undefined,
        memoryType: typeof item.memoryType === "string" ? item.memoryType : undefined,
        scope: typeof item.scope === "string" ? item.scope : undefined,
        retrievalMode: typeof item.retrievalMode === "string" ? item.retrievalMode : undefined,
        retrievalScore: typeof item.retrievalScore === "number" ? item.retrievalScore : undefined,
        retrievalReason: typeof item.retrievalReason === "string" ? item.retrievalReason : undefined,
        importance: typeof item.importance === "number" ? item.importance : undefined,
        confidence: typeof item.confidence === "number" ? item.confidence : undefined,
        summary: typeof item.summary === "string" ? item.summary : undefined,
      }));
  } catch {
    return [];
  }
}

function formatToolType(toolType?: string) {
  if (!toolType) return "工具";
  const normalized = toolType.toUpperCase();
  if (normalized.includes("SQL")) return "SQL";
  if (normalized.includes("PYTHON")) return "Python";
  if (normalized.includes("RETRIEVAL") || normalized.includes("DOCUMENT")) return "文档检索";
  return toolType.replaceAll("_", " ");
}

function formatStepType(stepType?: string) {
  if (!stepType) return "执行步骤";
  return stepType.replaceAll("_", " ").toLowerCase();
}

function getArtifactSchemaLabel(artifact: Artifact) {
  return artifact.artifactSchemaVersion ? `schema v${artifact.artifactSchemaVersion}` : "legacy";
}

function getArtifactStatusLabel(status?: string | null) {
  if (status === "ARCHIVED") return "已归档";
  if (status === "DELETED") return "已删除";
  return "活跃";
}

function getMemoryStatusLabel(status?: string | null) {
  if (status === "ARCHIVED") return "已归档";
  if (status === "SUPERSEDED") return "已替代";
  if (status === "DELETED") return "已删除";
  return "活跃";
}

function formatMemoryScore(value?: number | null) {
  return value == null ? "-" : value.toFixed(2);
}

function clampMemoryImportance(value: number) {
  return Math.max(0, Math.min(1, Number(value.toFixed(2))));
}

function ArtifactFilterControls({
  search,
  schemaFilter,
  statusFilter,
  totalCount,
  filteredCount,
  refreshing,
  disabled,
  onSearchChange,
  onSchemaFilterChange,
  onStatusFilterChange,
  onRefresh,
}: ArtifactFilterControlsProps) {
  const schemaFilters: { value: ArtifactSchemaFilter; label: string }[] = [
    { value: "all", label: "全部" },
    { value: "phase6", label: "Phase 6" },
    { value: "legacy", label: "Legacy" },
  ];
  const statusFilters: { value: ArtifactStatusFilter; label: string }[] = [
    { value: "ACTIVE", label: "活跃" },
    { value: "ARCHIVED", label: "归档" },
    { value: "DELETED", label: "删除" },
  ];

  return (
    <div className="space-y-3">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div className="relative min-w-0 flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--color-text-tertiary)]" />
          <Input
            value={search}
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="搜索查询、摘要或代码"
            className="pl-9"
          />
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {schemaFilters.map((filter) => (
            <button
              key={filter.value}
              type="button"
              data-active={schemaFilter === filter.value}
              className="pill-toggle"
              onClick={() => onSchemaFilterChange(filter.value)}
            >
              {filter.label}
            </button>
          ))}
          {statusFilters.map((filter) => (
            <button
              key={filter.value}
              type="button"
              data-active={statusFilter === filter.value}
              className="pill-toggle"
              onClick={() => onStatusFilterChange(filter.value)}
            >
              {filter.label}
            </button>
          ))}
          <Button variant="outline" size="sm" onClick={onRefresh} disabled={disabled || refreshing}>
            {refreshing ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            刷新
          </Button>
        </div>
      </div>
      <div className="text-xs text-[color:var(--color-text-tertiary)]">
        当前显示 {filteredCount} / {totalCount} 条历史分析
      </div>
    </div>
  );
}

function ArtifactCardGrid({ artifacts, emptyText, gridClassName, onOpenDetail }: ArtifactCardGridProps) {
  if (!artifacts.length) {
    return <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">{emptyText}</div>;
  }

  return (
    <div className={gridClassName ?? "grid gap-3 md:grid-cols-2 xl:grid-cols-3"}>
      {artifacts.map((artifact) => (
        <button
          key={artifact.id}
          type="button"
          onClick={() => onOpenDetail(artifact)}
          className="subtle-panel text-left px-4 py-4 transition-colors hover:border-[color:var(--color-border-info)] hover:bg-[color:var(--color-sidebar-hover-background)]"
        >
          <div className="line-clamp-2 text-sm font-medium text-[color:var(--color-text-primary)]">
            {artifact.userQuery || "分析记录"}
          </div>
          <div className="mt-3 line-clamp-4 text-sm leading-6 text-[color:var(--color-text-secondary)]">
            {artifact.summary || "无摘要"}
          </div>
          <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-xs text-[color:var(--color-text-tertiary)]">
            <span>{artifact.createdAt ? new Date(artifact.createdAt).toLocaleString() : ""}</span>
            <span>
              {getArtifactSchemaLabel(artifact)} / {getArtifactStatusLabel(artifact.artifactStatus)}
            </span>
          </div>
          <div className="mt-3 flex items-center gap-2 text-xs font-medium text-[color:var(--color-accent)]">
            <Eye className="h-3.5 w-3.5" />
            查看详情
          </div>
        </button>
      ))}
    </div>
  );
}

function MemoryManagementPanel({
  memories,
  filteredMemories,
  search,
  statusFilter,
  refreshing,
  actionLoadingId,
  disabled,
  compact,
  onSearchChange,
  onStatusFilterChange,
  onRefresh,
  onStatusAction,
  onImportanceChange,
}: MemoryManagementPanelProps) {
  const statusFilters: { value: MemoryStatusFilter; label: string }[] = [
    { value: "ACTIVE", label: "活跃" },
    { value: "ARCHIVED", label: "归档" },
    { value: "SUPERSEDED", label: "替代" },
    { value: "DELETED", label: "删除" },
  ];

  return (
    <div className="space-y-4">
      <div className="space-y-3">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <div className="relative min-w-0 flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--color-text-tertiary)]" />
            <Input
              value={search}
              onChange={(event) => onSearchChange(event.target.value)}
              placeholder="搜索记忆摘要、类型或内容"
              className="pl-9"
            />
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {statusFilters.map((filter) => (
              <button
                key={filter.value}
                type="button"
                data-active={statusFilter === filter.value}
                className="pill-toggle"
                onClick={() => onStatusFilterChange(filter.value)}
              >
                {filter.label}
              </button>
            ))}
            <Button variant="outline" size="sm" onClick={onRefresh} disabled={disabled || refreshing}>
              {refreshing ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
              刷新
            </Button>
          </div>
        </div>
        <div className="text-xs text-[color:var(--color-text-tertiary)]">
          当前显示 {filteredMemories.length} / {memories.length} 条长期记忆
        </div>
      </div>

      {memories.length ? (
        <div className={compact ? "grid gap-3 md:grid-cols-2" : "grid gap-3 lg:grid-cols-2 xl:grid-cols-3"}>
          {filteredMemories.map((memory) => {
            const status = memory.status ?? "ACTIVE";
            const busy = actionLoadingId === memory.id;
            const body = memory.summary || memory.content || "无内容";
            return (
              <div key={memory.id} className="subtle-panel px-4 py-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-[11px] uppercase tracking-[0.08em] text-[color:var(--color-text-tertiary)]">
                      {memory.memoryType || "MEMORY"}
                    </div>
                    <div className="mt-2 line-clamp-4 break-words text-sm leading-6 text-[color:var(--color-text-primary)]">
                      {body}
                    </div>
                  </div>
                  <span className="rounded-[8px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-2 py-1 text-xs text-[color:var(--color-text-tertiary)]">
                    {getMemoryStatusLabel(status)}
                  </span>
                </div>

                <div className="mt-4 grid grid-cols-3 gap-2 text-xs text-[color:var(--color-text-tertiary)]">
                  <div>
                    <div>重要性</div>
                    <div className="mt-1 text-sm text-[color:var(--color-text-primary)]">
                      {formatMemoryScore(memory.importance)}
                    </div>
                  </div>
                  <div>
                    <div>置信度</div>
                    <div className="mt-1 text-sm text-[color:var(--color-text-primary)]">
                      {formatMemoryScore(memory.confidence)}
                    </div>
                  </div>
                  <div>
                    <div>复用</div>
                    <div className="mt-1 text-sm text-[color:var(--color-text-primary)]">{memory.useCount ?? 0}</div>
                  </div>
                </div>

                <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-[color:var(--color-text-tertiary)]">
                  <span>{memory.scope || "UNKNOWN_SCOPE"}</span>
                  {memory.datasetId ? <span>dataset #{memory.datasetId}</span> : null}
                  {memory.lastUsedAt ? <span>last used {new Date(memory.lastUsedAt).toLocaleString()}</span> : null}
                </div>

                <div className="mt-4 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-1">
                    <Button
                      variant="outline"
                      size="sm"
                      title="降低重要性"
                      aria-label="降低重要性"
                      disabled={busy || status === "DELETED"}
                      onClick={() => onImportanceChange(memory, -0.1)}
                    >
                      <Minus className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      title="提高重要性"
                      aria-label="提高重要性"
                      disabled={busy || status === "DELETED"}
                      onClick={() => onImportanceChange(memory, 0.1)}
                    >
                      <Plus className="h-4 w-4" />
                    </Button>
                  </div>
                  <div className="flex flex-wrap justify-end gap-2">
                    {status === "ACTIVE" ? (
                      <>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onStatusAction(memory, "archive")}
                          disabled={busy}
                        >
                          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Archive className="h-4 w-4" />}
                          归档
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => onStatusAction(memory, "supersede")}
                          disabled={busy}
                        >
                          {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}
                          替代
                        </Button>
                      </>
                    ) : (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onStatusAction(memory, "restore")}
                        disabled={busy}
                      >
                        {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}
                        恢复
                      </Button>
                    )}
                    {status !== "DELETED" ? (
                      <Button
                        variant="destructive"
                        size="sm"
                        onClick={() => onStatusAction(memory, "delete")}
                        disabled={busy}
                      >
                        {busy ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                        软删
                      </Button>
                    ) : null}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      ) : (
        <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">
          还没有长期记忆。完成一次分析后，系统会从结果中沉淀可复用的发现、风险和分析模式。
        </div>
      )}
    </div>
  );
}

function EvidenceSourcesPanel({ evidence }: EvidenceSourcesPanelProps) {
  const datasetNames = evidence?.datasetNames?.filter(Boolean) ?? [];
  const documentNames = evidence?.documentNames?.filter(Boolean) ?? [];
  const hasEvidence = Boolean(evidence);
  const hasDatasets = (evidence?.datasetCount ?? 0) > 0 || datasetNames.length > 0;
  const hasDocuments = (evidence?.documentChunkCount ?? 0) > 0 || documentNames.length > 0;

  return (
    <div className="subtle-panel px-4 py-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <FileSearch className="h-4 w-4 text-[color:var(--color-text-tertiary)]" />
          <div className="text-[11px] uppercase text-[color:var(--color-text-tertiary)]">证据来源</div>
        </div>
        {hasEvidence ? (
          <div className="text-xs text-[color:var(--color-text-tertiary)]">
            {evidence?.hasSemanticContext ? "已接入语义上下文" : "结构化上下文"}
          </div>
        ) : null}
      </div>

      {hasEvidence ? (
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3">
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-2 text-sm font-medium text-[color:var(--color-text-primary)]">
                <Database className="h-4 w-4 text-[color:var(--color-text-tertiary)]" />
                数据表
              </div>
              <div className="min-w-0 text-right text-xs text-[color:var(--color-text-tertiary)]">
                {(evidence?.datasetCount ?? datasetNames.length) || 0} 表 / {evidence?.relationCount ?? 0} 关系
              </div>
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              {hasDatasets ? (
                datasetNames.slice(0, 8).map((name) => (
                  <span
                    key={name}
                    className="max-w-full break-all rounded-[8px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-2 py-1 text-xs text-[color:var(--color-text-secondary)]"
                  >
                    {name}
                  </span>
                ))
              ) : (
                <span className="text-sm text-[color:var(--color-text-secondary)]">本次没有使用数据表证据。</span>
              )}
            </div>
          </div>

          <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3">
            <div className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 items-center gap-2 text-sm font-medium text-[color:var(--color-text-primary)]">
                <FileText className="h-4 w-4 text-[color:var(--color-text-tertiary)]" />
                文档片段
              </div>
              <div className="min-w-0 text-right text-xs text-[color:var(--color-text-tertiary)]">
                {evidence?.documentStrategy ?? "无检索策略"} / {evidence?.documentChunkCount ?? 0} 片段
              </div>
            </div>
            <div className="mt-3 flex flex-wrap gap-2">
              {hasDocuments ? (
                documentNames.slice(0, 8).map((name) => (
                  <span
                    key={name}
                    className="max-w-full break-all rounded-[8px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-2 py-1 text-xs text-[color:var(--color-text-secondary)]"
                  >
                    {name}
                  </span>
                ))
              ) : (
                <span className="text-sm text-[color:var(--color-text-secondary)]">本次没有使用文档证据。</span>
              )}
            </div>
          </div>
        </div>
      ) : (
        <p className="mt-3 text-sm leading-6 text-[color:var(--color-text-secondary)]">
          分析完成后会显示本次结论引用的数据表、关系和文档片段。
        </p>
      )}
    </div>
  );
}

function AnalysisProcessPanel({ executionLogs, validationReport, riskNotices }: AnalysisProcessPanelProps) {
  const logs = executionLogs ?? [];
  const findings = validationReport?.findings ?? [];
  const notices = riskNotices ?? [];
  const hasProcess = logs.length > 0 || findings.length > 0 || notices.length > 0;

  return (
    <div className="subtle-panel px-4 py-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <ListChecks className="h-4 w-4 text-[color:var(--color-text-tertiary)]" />
          <div className="text-[11px] uppercase text-[color:var(--color-text-tertiary)]">分析过程</div>
        </div>
        {hasProcess ? (
          <div className="text-xs text-[color:var(--color-text-tertiary)]">
            {logs.length} 步 / {notices.length || findings.length} 条提示
          </div>
        ) : null}
      </div>

      {hasProcess ? (
        <div className="mt-4 space-y-3">
          {logs.length ? (
            <div className="space-y-2">
              {logs.map((log, index) => (
                <div
                  key={`${log.toolType ?? "tool"}-${log.stepType ?? "step"}-${index}`}
                  className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3"
                >
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div className="flex min-w-0 items-center gap-2">
                      {log.success === false ? (
                        <CircleAlert className="h-4 w-4 text-[color:#f87171]" />
                      ) : (
                        <CheckCircle2 className="h-4 w-4 text-[color:#86efac]" />
                      )}
                      <div className="min-w-0 text-sm font-medium text-[color:var(--color-text-primary)]">
                        {index + 1}. {formatToolType(log.toolType)} · {formatStepType(log.stepType)}
                      </div>
                    </div>
                    <div className="flex items-center gap-1 text-xs text-[color:var(--color-text-tertiary)]">
                      <Clock3 className="h-3.5 w-3.5" />
                      {log.durationMs == null ? "-" : `${log.durationMs}ms`}
                    </div>
                  </div>
                  {log.message || log.outputSummary ? (
                    <div className="mt-2 break-words text-sm leading-6 text-[color:var(--color-text-secondary)]">
                      {log.message ?? log.outputSummary}
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          ) : (
            <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3 text-sm text-[color:var(--color-text-secondary)]">
              本次响应没有返回执行日志。
            </div>
          )}

          {notices.length || findings.length ? (
            <div className="rounded-[12px] border [border-width:0.5px] border-[color:rgba(248,113,113,0.34)] px-3 py-3">
              <div className="mb-2 flex items-center gap-2 text-sm font-medium text-[color:var(--color-text-primary)]">
                <CircleAlert className="h-4 w-4 text-[color:#fca5a5]" />
                风险与校验提示
              </div>
              <div className="space-y-2">
                {(notices.length ? notices : findings).slice(0, 6).map((item, index) => (
                  <div
                    key={`${item.code ?? "notice"}-${index}`}
                    className="break-words text-sm leading-6 text-[color:var(--color-text-secondary)]"
                  >
                    <span className="text-[color:var(--color-text-tertiary)]">{item.severity ?? "INFO"}</span>
                    {item.code ? ` · ${item.code}` : ""}: {item.message ?? "需要人工复核。"}
                  </div>
                ))}
              </div>
            </div>
          ) : (
            <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3 text-sm text-[color:var(--color-text-secondary)]">
              暂无校验 warning 或风险提示。
            </div>
          )}
        </div>
      ) : (
        <p className="mt-3 text-sm leading-6 text-[color:var(--color-text-secondary)]">
          分析完成后会显示本次执行过的检索、SQL 或 Python 步骤，以及校验提示。
        </p>
      )}
    </div>
  );
}

function ContextTracePanel({ trace }: ContextTracePanelProps) {
  const schemaIds = parseJsonList(trace?.selectedSchemaIdsJson);
  const documentRefs = parseJsonList(trace?.selectedDocumentChunkIdsJson);
  const memoryIds = parseJsonList(trace?.selectedMemoryIdsJson);
  const memorySelections = parseTraceMemorySelections(trace?.filteredItemsJson);
  const hasTrace = Boolean(trace);

  return (
    <div className="subtle-panel px-4 py-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex min-w-0 items-center gap-2">
          <FolderTree className="h-4 w-4 text-[color:var(--color-text-tertiary)]" />
          <div className="text-[11px] uppercase text-[color:var(--color-text-tertiary)]">上下文来源</div>
        </div>
        {trace?.createdAt ? (
          <div className="text-xs text-[color:var(--color-text-tertiary)]">
            {new Date(trace.createdAt).toLocaleString()}
          </div>
        ) : null}
      </div>

      {hasTrace ? (
        <div className="mt-4 space-y-3">
          <div className="grid gap-3 md:grid-cols-3">
            <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3">
              <div className="text-xs text-[color:var(--color-text-tertiary)]">Schema</div>
              <div className="mt-2 text-sm text-[color:var(--color-text-primary)]">
                {schemaIds.length ? schemaIds.join(", ") : "未记录"}
              </div>
            </div>
            <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3">
              <div className="text-xs text-[color:var(--color-text-tertiary)]">Document Chunks</div>
              <div className="mt-2 text-sm text-[color:var(--color-text-primary)]">
                {documentRefs.length ? documentRefs.join(", ") : "未使用"}
              </div>
            </div>
            <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3">
              <div className="text-xs text-[color:var(--color-text-tertiary)]">Memories</div>
              <div className="mt-2 text-sm text-[color:var(--color-text-primary)]">
                {memoryIds.length ? memoryIds.join(", ") : "未使用"}
              </div>
            </div>
          </div>
          {memorySelections.length ? (
            <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3">
              <div className="mb-3 text-xs text-[color:var(--color-text-tertiary)]">Memory Retrieval</div>
              <div className="space-y-3">
                {memorySelections.map((memory) => (
                  <div
                    key={memory.id ?? `${memory.memoryType}-${memory.summary}`}
                    className="rounded-[10px] bg-[color:var(--color-background-tertiary)] px-3 py-3"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="text-sm font-medium text-[color:var(--color-text-primary)]">
                        #{memory.id ?? "-"} · {memory.memoryType ?? "MEMORY"}
                      </div>
                      <div className="text-xs text-[color:var(--color-text-tertiary)]">
                        {memory.retrievalMode ?? "unknown"}
                        {memory.retrievalScore == null ? "" : ` / ${formatMemoryScore(memory.retrievalScore)}`}
                      </div>
                    </div>
                    {memory.summary ? (
                      <div className="mt-2 line-clamp-2 text-sm leading-6 text-[color:var(--color-text-secondary)]">
                        {memory.summary}
                      </div>
                    ) : null}
                    <div className="mt-2 flex flex-wrap gap-3 text-xs text-[color:var(--color-text-tertiary)]">
                      {memory.scope ? <span>{memory.scope}</span> : null}
                      <span>importance {formatMemoryScore(memory.importance)}</span>
                      <span>confidence {formatMemoryScore(memory.confidence)}</span>
                      {memory.retrievalReason ? <span>{memory.retrievalReason}</span> : null}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          ) : null}
          {trace?.packedContext ? (
            <details className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-3 py-3">
              <summary className="cursor-pointer text-sm font-medium text-[color:var(--color-text-primary)]">
                Packed Context
              </summary>
              <pre className="mt-3 max-h-72 overflow-auto whitespace-pre-wrap break-words rounded-[10px] bg-[color:var(--color-background-tertiary)] p-3 text-xs leading-6 text-[color:var(--color-text-secondary)]">
                {trace.packedContext}
              </pre>
            </details>
          ) : null}
        </div>
      ) : (
        <p className="mt-3 text-sm leading-6 text-[color:var(--color-text-secondary)]">
          当前历史结果没有关联上下文 trace。
        </p>
      )}
    </div>
  );
}

export default function WorkspacePage() {
  const { t } = useLanguage();

  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [groupId, setGroupId] = useState<number | null>(null);
  const [activeWorkspace, setActiveWorkspace] = useState<Workspace | null>(null);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [relations, setRelations] = useState<DatasetRelation[]>([]);
  const [documents, setDocuments] = useState<DocumentAsset[]>([]);
  const [focusIds, setFocusIds] = useState<number[]>([]);
  const [query, setQuery] = useState("");
  const [loadingWorkspaces, setLoadingWorkspaces] = useState(true);
  const [loadingWorkspaceState, setLoadingWorkspaceState] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysis, setAnalysis] = useState<AnalysisResult | null>(null);
  const [recentArtifacts, setRecentArtifacts] = useState<Artifact[]>([]);
  const [artifactDetailOpen, setArtifactDetailOpen] = useState(false);
  const [artifactDetailLoading, setArtifactDetailLoading] = useState(false);
  const [artifactDetail, setArtifactDetail] = useState<ArtifactDetail | null>(null);
  const [artifactSearch, setArtifactSearch] = useState("");
  const [artifactSchemaFilter, setArtifactSchemaFilter] = useState<ArtifactSchemaFilter>("all");
  const [artifactStatusFilter, setArtifactStatusFilter] = useState<ArtifactStatusFilter>("ACTIVE");
  const [artifactRefreshing, setArtifactRefreshing] = useState(false);
  const [artifactActionLoading, setArtifactActionLoading] = useState(false);
  const [recentMemories, setRecentMemories] = useState<ArtifactMemory[]>([]);
  const [memorySearch, setMemorySearch] = useState("");
  const [memoryStatusFilter, setMemoryStatusFilter] = useState<MemoryStatusFilter>("ACTIVE");
  const [memoryRefreshing, setMemoryRefreshing] = useState(false);
  const [memoryActionLoadingId, setMemoryActionLoadingId] = useState<number | null>(null);

  const [workspaceInfoOpen, setWorkspaceInfoOpen] = useState(false);
  const [assetDialogOpen, setAssetDialogOpen] = useState(false);
  const [assetView, setAssetView] = useState<AssetView>("tables");
  const [relationManagerOpen, setRelationManagerOpen] = useState(false);
  const [relationOpen, setRelationOpen] = useState(false);
  const [editingRelation, setEditingRelation] = useState<DatasetRelation | null>(null);
  const [relationForm, setRelationForm] = useState<RelationFormState>(emptyRelationForm);
  const [relationSaving, setRelationSaving] = useState(false);
  const [relationRefreshing, setRelationRefreshing] = useState(false);
  const [sourceColumns, setSourceColumns] = useState<DatasetColumnInfo[]>([]);
  const [targetColumns, setTargetColumns] = useState<DatasetColumnInfo[]>([]);
  const [columnLoading, setColumnLoading] = useState(false);

  const [descriptionOpen, setDescriptionOpen] = useState(false);
  const [descriptionDraft, setDescriptionDraft] = useState("");
  const [descriptionSaving, setDescriptionSaving] = useState(false);

  const [createWorkspaceOpen, setCreateWorkspaceOpen] = useState(false);
  const [createWorkspaceName, setCreateWorkspaceName] = useState("");
  const [createWorkspaceDescription, setCreateWorkspaceDescription] = useState("");
  const [creatingWorkspace, setCreatingWorkspace] = useState(false);

  const [documentUploadFile, setDocumentUploadFile] = useState<File | null>(null);
  const [documentUploadName, setDocumentUploadName] = useState("");
  const [documentUploading, setDocumentUploading] = useState(false);
  const [documentSearchQuery, setDocumentSearchQuery] = useState("");
  const [documentSearchResults, setDocumentSearchResults] = useState<DocumentSearchResult[]>([]);
  const [documentSearching, setDocumentSearching] = useState(false);
  const [documentChunksOpen, setDocumentChunksOpen] = useState(false);
  const [documentChunks, setDocumentChunks] = useState<DocumentChunk[]>([]);
  const [documentChunksLoading, setDocumentChunksLoading] = useState(false);
  const [activeDocument, setActiveDocument] = useState<DocumentAsset | null>(null);

  const datasetMap = useMemo(() => new Map(datasets.map((dataset) => [dataset.id, dataset])), [datasets]);
  const rows = useMemo(() => analysis?.analysisReport?.data ?? analysis?.data ?? [], [analysis]);
  const columns = useMemo(() => (rows.length ? Object.keys(rows[0]) : []), [rows]);
  const analysisSummary = analysis?.analysisReport?.summary ?? analysis?.summary;
  const generatedCodeOrSql =
    analysis?.analysisReport?.generatedCodeOrSql ?? analysis?.generatedCodeOrSql ?? analysis?.generatedSql;
  const evidenceSummary = analysis?.analysisReport?.evidence;
  const executionLogs = analysis?.analysisReport?.executionLogs ?? analysis?.executionLogs;
  const validationReport = analysis?.analysisReport?.validationReport ?? analysis?.validationReport;
  const riskNotices = analysis?.analysisReport?.riskNotices ?? analysis?.riskNotices;
  const artifactDetailRows = useMemo(
    () => artifactDetail?.resultPreview ?? parseArtifactRows(artifactDetail?.resultPreviewJson),
    [artifactDetail]
  );
  const artifactDetailColumns = useMemo(
    () => (artifactDetailRows.length ? Object.keys(artifactDetailRows[0]) : []),
    [artifactDetailRows]
  );
  const filteredArtifacts = useMemo(() => {
    const term = artifactSearch.trim().toLowerCase();

    return recentArtifacts.filter((artifact) => {
      const schemaVersion = artifact.artifactSchemaVersion ?? 0;
      if (artifactSchemaFilter === "phase6" && schemaVersion < 2) return false;
      if (artifactSchemaFilter === "legacy" && schemaVersion >= 2) return false;

      if (!term) return true;
      const searchable = [
        artifact.userQuery,
        artifact.summary,
        artifact.generatedCodeOrSql,
        artifact.chartType,
        artifact.createdAt,
        artifact.artifactStatus,
        getArtifactSchemaLabel(artifact),
        getArtifactStatusLabel(artifact.artifactStatus),
      ]
        .filter(Boolean)
        .join("\n")
        .toLowerCase();

      return searchable.includes(term);
    });
  }, [artifactSchemaFilter, artifactSearch, recentArtifacts]);
  const filteredMemories = useMemo(() => {
    const term = memorySearch.trim().toLowerCase();

    if (!term) return recentMemories;
    return recentMemories.filter((memory) => {
      const searchable = [
        memory.memoryType,
        memory.scope,
        memory.summary,
        memory.content,
        memory.status,
        memory.datasetId == null ? undefined : `dataset ${memory.datasetId}`,
        memory.artifactId == null ? undefined : `artifact ${memory.artifactId}`,
      ]
        .filter(Boolean)
        .join("\n")
        .toLowerCase();

      return searchable.includes(term);
    });
  }, [memorySearch, recentMemories]);

  const metrics = useMemo(
    () => [
      { label: "数据表", value: datasets.length },
      { label: "文档", value: documents.length },
      { label: "关系", value: relations.length },
      { label: "焦点", value: focusIds.length },
      { label: "记忆", value: recentMemories.length },
    ],
    [datasets.length, documents.length, focusIds.length, relations.length, recentMemories.length]
  );

  const relationRows = useMemo(
    () =>
      relations.map((relation) => {
        const sourceDataset = datasetMap.get(relation.sourceDatasetId);
        const targetDataset = datasetMap.get(relation.targetDatasetId);
        return {
          ...relation,
          sourceLabel: `${sourceDataset?.tableName ?? relation.sourceTableName}.${relation.sourceColumnName}`,
          targetLabel: `${targetDataset?.tableName ?? relation.targetTableName}.${relation.targetColumnName}`,
        };
      }),
    [datasetMap, relations]
  );

  const loadWorkspaces = useCallback(async () => {
    setLoadingWorkspaces(true);
    try {
      const res = await workplaceApi.listWorkspaces();
      const list = res.data ?? [];
      setWorkspaces(list);
      setGroupId((prev) => prev ?? list[0]?.id ?? null);
    } catch (error) {
      toast.error((error as Error).message || t("failed_load_workplaces"));
    } finally {
      setLoadingWorkspaces(false);
    }
  }, [t]);

  const loadWorkspaceState = useCallback(
    async (nextGroupId: number) => {
      setLoadingWorkspaceState(true);
      try {
        const [workspaceRes, datasetRes, relationRes, documentRes, artifactRes, memoryRes] = await Promise.all([
          workplaceApi.getWorkspace(nextGroupId),
          workplaceApi.getDatasets(nextGroupId),
          workplaceApi.getRelations(nextGroupId),
          documentApi.list(nextGroupId),
          artifactApi.recent({ groupId: nextGroupId, limit: 12, status: artifactStatusFilter }),
          artifactMemoryApi.recent({ groupId: nextGroupId, limit: 24, status: memoryStatusFilter }),
        ]);
        setActiveWorkspace(workspaceRes.data ?? null);
        setDatasets(datasetRes.data ?? []);
        setRelations(relationRes.data ?? []);
        setDocuments(documentRes.data ?? []);
        setRecentArtifacts(artifactRes.data ?? []);
        setRecentMemories(memoryRes.data ?? []);
        setFocusIds([]);
        setAnalysis(null);
        setDocumentSearchResults([]);
        setDocumentSearchQuery("");
      } catch (error) {
        toast.error((error as Error).message || t("failed_load_datasets"));
      } finally {
        setLoadingWorkspaceState(false);
      }
    },
    [artifactStatusFilter, memoryStatusFilter, t]
  );

  useEffect(() => {
    void loadWorkspaces();
  }, [loadWorkspaces]);

  useEffect(() => {
    if (!groupId) return;
    void loadWorkspaceState(groupId);
  }, [groupId, loadWorkspaceState]);

  useEffect(() => {
    if (!relationOpen) return;

    async function loadColumnOptions() {
      const sourceId = relationForm.sourceDatasetId;
      const targetId = relationForm.targetDatasetId;

      if (!sourceId && !targetId) {
        setSourceColumns([]);
        setTargetColumns([]);
        return;
      }

      setColumnLoading(true);
      try {
        const [sourceMeta, targetMeta] = await Promise.all([
          sourceId ? datasetApi.getMetadata(sourceId) : Promise.resolve({ data: undefined }),
          targetId ? datasetApi.getMetadata(targetId) : Promise.resolve({ data: undefined }),
        ]);
        setSourceColumns(sourceMeta.data?.columns ?? []);
        setTargetColumns(targetMeta.data?.columns ?? []);
      } catch {
        setSourceColumns([]);
        setTargetColumns([]);
      } finally {
        setColumnLoading(false);
      }
    }

    void loadColumnOptions();
  }, [relationOpen, relationForm.sourceDatasetId, relationForm.targetDatasetId]);

  function openDescriptionEditor() {
    setDescriptionDraft(activeWorkspace?.description ?? "");
    setDescriptionOpen(true);
  }

  function openRelationDialog(relation?: DatasetRelation) {
    if (relation) {
      setEditingRelation(relation);
      setRelationForm({
        sourceDatasetId: relation.sourceDatasetId,
        sourceColumnName: relation.sourceColumnName,
        targetDatasetId: relation.targetDatasetId,
        targetColumnName: relation.targetColumnName,
        relationType: relation.relationType ?? "",
        confidence: relation.confidence == null ? "" : String(relation.confidence),
      });
    } else {
      setEditingRelation(null);
      setRelationForm(emptyRelationForm);
    }
    setRelationOpen(true);
  }

  function applyArtifact(artifact: Artifact | ArtifactDetail) {
    const detail = artifact as ArtifactDetail;
    setQuery(artifact.userQuery ?? "");
    setAnalysis({
      success: true,
      message: undefined,
      data: detail.resultPreview ?? parseArtifactRows(artifact.resultPreviewJson),
      generatedCodeOrSql: artifact.generatedCodeOrSql,
      summary: artifact.summary,
      recommendedChart: artifact.chartType,
      artifactId: artifact.id,
      analysisReport: detail.analysisReport ?? undefined,
      executionLogs: detail.executionLogs,
      validationReport: detail.validationReport ?? undefined,
      riskNotices: detail.riskNotices,
    });
  }

  async function openArtifactDetail(artifact: Artifact) {
    setArtifactDetailOpen(true);
    setArtifactDetail({ ...artifact, resultPreview: parseArtifactRows(artifact.resultPreviewJson) });
    setArtifactDetailLoading(true);
    try {
      const res = await artifactApi.detail(artifact.id);
      setArtifactDetail(res.data ?? null);
    } catch (error) {
      toast.error((error as Error).message || "加载分析结果详情失败");
    } finally {
      setArtifactDetailLoading(false);
    }
  }

  async function refreshArtifacts(showToast = true, status = artifactStatusFilter) {
    if (!groupId) return;

    setArtifactRefreshing(true);
    try {
      const artifactRes = await artifactApi.recent({ groupId, limit: 12, status });
      setRecentArtifacts(artifactRes.data ?? []);
      if (showToast) {
        toast.success("历史结果已刷新");
      }
    } catch (error) {
      toast.error((error as Error).message || "刷新历史结果失败");
    } finally {
      setArtifactRefreshing(false);
    }
  }

  function changeArtifactStatusFilter(status: ArtifactStatusFilter) {
    setArtifactStatusFilter(status);
    void refreshArtifacts(false, status);
  }

  async function refreshMemories(showToast = true, status = memoryStatusFilter) {
    if (!groupId) return;

    setMemoryRefreshing(true);
    try {
      const memoryRes = await artifactMemoryApi.recent({ groupId, limit: 24, status });
      setRecentMemories(memoryRes.data ?? []);
      if (showToast) {
        toast.success("长期记忆已刷新");
      }
    } catch (error) {
      toast.error((error as Error).message || "刷新长期记忆失败");
    } finally {
      setMemoryRefreshing(false);
    }
  }

  function changeMemoryStatusFilter(status: MemoryStatusFilter) {
    setMemoryStatusFilter(status);
    void refreshMemories(false, status);
  }

  async function updateMemoryStatus(memory: ArtifactMemory, action: MemoryAction) {
    if (action === "delete" && typeof window !== "undefined") {
      const confirmed = window.confirm("确认软删除这条长期记忆？删除后可从“删除”筛选中恢复。");
      if (!confirmed) return;
    }

    setMemoryActionLoadingId(memory.id);
    try {
      const res =
        action === "archive"
          ? await artifactMemoryApi.archive(memory.id)
          : action === "restore"
            ? await artifactMemoryApi.restore(memory.id)
            : action === "supersede"
              ? await artifactMemoryApi.supersede(memory.id)
              : await artifactMemoryApi.delete(memory.id);
      const updatedMemory = res.data;
      if (updatedMemory) {
        setRecentMemories((prev) => prev.map((item) => (item.id === updatedMemory.id ? updatedMemory : item)));
      }
      const nextStatus = action === "restore" ? "ACTIVE" : memoryStatusFilter;
      if (action === "restore") {
        setMemoryStatusFilter("ACTIVE");
      }
      await refreshMemories(false, nextStatus);
      toast.success(
        action === "archive"
          ? "长期记忆已归档"
          : action === "restore"
            ? "长期记忆已恢复"
            : action === "supersede"
              ? "长期记忆已标记为替代"
              : "长期记忆已软删除"
      );
    } catch (error) {
      toast.error((error as Error).message || "更新长期记忆失败");
    } finally {
      setMemoryActionLoadingId(null);
    }
  }

  async function updateMemoryImportance(memory: ArtifactMemory, delta: number) {
    const current = memory.importance ?? 0.5;
    const nextImportance = clampMemoryImportance(current + delta);

    setMemoryActionLoadingId(memory.id);
    try {
      const res = await artifactMemoryApi.updateImportance(memory.id, nextImportance);
      const updatedMemory = res.data;
      if (updatedMemory) {
        setRecentMemories((prev) => prev.map((item) => (item.id === updatedMemory.id ? updatedMemory : item)));
      }
    } catch (error) {
      toast.error((error as Error).message || "更新记忆重要性失败");
    } finally {
      setMemoryActionLoadingId(null);
    }
  }

  async function updateArtifactStatus(action: "archive" | "restore" | "delete") {
    if (!artifactDetail) return;
    if (action === "delete" && typeof window !== "undefined") {
      const confirmed = window.confirm("确认软删除这个历史分析结果？删除后可从“删除”筛选中恢复。");
      if (!confirmed) return;
    }

    setArtifactActionLoading(true);
    try {
      const res =
        action === "archive"
          ? await artifactApi.archive(artifactDetail.id)
          : action === "restore"
            ? await artifactApi.restore(artifactDetail.id)
            : await artifactApi.delete(artifactDetail.id);
      if (res.data) {
        setArtifactDetail(res.data);
      }
      const nextStatus = action === "restore" ? "ACTIVE" : artifactStatusFilter;
      if (action === "restore") {
        setArtifactStatusFilter("ACTIVE");
      }
      await refreshArtifacts(false, nextStatus);
      toast.success(action === "archive" ? "历史结果已归档" : action === "restore" ? "历史结果已恢复" : "历史结果已软删除");
    } catch (error) {
      toast.error((error as Error).message || "更新历史结果状态失败");
    } finally {
      setArtifactActionLoading(false);
    }
  }

  function toggleFocus(datasetId: number) {
    setFocusIds((prev) => (prev.includes(datasetId) ? prev.filter((id) => id !== datasetId) : [...prev, datasetId]));
  }

  function askAboutDataset(dataset: Dataset) {
    setFocusIds([dataset.id]);
    setQuery(`请围绕 ${dataset.tableName} 做一次正式分析，总结关键模式、异常点和下一步值得追问的问题。`);
  }

  async function runAnalysis() {
    const prompt = query.trim();
    if (!groupId) {
      toast.error(t("select_workplace_first"));
      return;
    }
    if (!prompt) return;

    setAnalyzing(true);
    try {
      const result = await workplaceApi.analyze(groupId, prompt, focusIds);
      setAnalysis(result);
      setArtifactStatusFilter("ACTIVE");
      setMemoryStatusFilter("ACTIVE");
      const [artifactRes, memoryRes] = await Promise.all([
        artifactApi.recent({ groupId, limit: 12, status: "ACTIVE" }),
        artifactMemoryApi.recent({ groupId, limit: 24, status: "ACTIVE" }),
      ]);
      setRecentArtifacts(artifactRes.data ?? []);
      setRecentMemories(memoryRes.data ?? []);
      if (!result.success) {
        toast.error(result.message || t("analysis_failed"));
      }
    } catch (error) {
      toast.error((error as Error).message || t("analysis_request_failed"));
    } finally {
      setAnalyzing(false);
    }
  }

  async function saveWorkspaceDescription() {
    if (!groupId) return;
    setDescriptionSaving(true);
    try {
      const res = await workplaceApi.updateDescription(groupId, descriptionDraft);
      if (!res.success) {
        toast.error(res.message || t("save_failed"));
        return;
      }
      setActiveWorkspace(res.data ?? null);
      setDescriptionOpen(false);
      toast.success(t("description_saved"));
    } catch (error) {
      toast.error((error as Error).message || t("save_failed"));
    } finally {
      setDescriptionSaving(false);
    }
  }

  async function createWorkspace() {
    const name = createWorkspaceName.trim();
    if (!name) {
      toast.error(t("workspace_name_required"));
      return;
    }
    setCreatingWorkspace(true);
    try {
      const res = await workplaceApi.createWorkspace(name, createWorkspaceDescription.trim());
      if (!res.success || !res.data) {
        toast.error(res.message || t("workspace_create_failed"));
        return;
      }
      toast.success(t("workspace_created"));
      setCreateWorkspaceOpen(false);
      setCreateWorkspaceName("");
      setCreateWorkspaceDescription("");
      await loadWorkspaces();
      setGroupId(res.data.id);
    } catch (error) {
      toast.error((error as Error).message || t("workspace_create_failed"));
    } finally {
      setCreatingWorkspace(false);
    }
  }

  async function saveRelation() {
    if (!groupId || !relationForm.sourceDatasetId || !relationForm.targetDatasetId) {
      toast.error(t("relation_save_failed"));
      return;
    }

    const sourceDataset = datasetMap.get(relationForm.sourceDatasetId);
    const targetDataset = datasetMap.get(relationForm.targetDatasetId);
    if (!sourceDataset || !targetDataset || !relationForm.sourceColumnName.trim() || !relationForm.targetColumnName.trim()) {
      toast.error(t("relation_save_failed"));
      return;
    }

    const payload = {
      sourceDatasetId: relationForm.sourceDatasetId,
      sourceTableName: sourceDataset.tableName,
      sourceColumnName: relationForm.sourceColumnName.trim(),
      targetDatasetId: relationForm.targetDatasetId,
      targetTableName: targetDataset.tableName,
      targetColumnName: relationForm.targetColumnName.trim(),
      relationType: relationForm.relationType.trim() || undefined,
      confidence: relationForm.confidence.trim() ? Number(relationForm.confidence) : null,
    };

    setRelationSaving(true);
    try {
      const res = editingRelation?.id
        ? await workplaceApi.updateRelation(groupId, editingRelation.id, payload)
        : await workplaceApi.createRelation(groupId, payload);
      if (!res.success) {
        toast.error(res.message || t("relation_save_failed"));
        return;
      }
      toast.success(t("relation_saved"));
      setRelationOpen(false);
      await loadWorkspaceState(groupId);
    } catch (error) {
      toast.error((error as Error).message || t("relation_save_failed"));
    } finally {
      setRelationSaving(false);
    }
  }

  async function deleteRelation(relationId: number) {
    if (!groupId || !window.confirm(t("relation_delete_confirm"))) return;
    try {
      const res = await workplaceApi.deleteRelation(groupId, relationId);
      if (!res.success) {
        toast.error(res.message || t("relation_delete_failed"));
        return;
      }
      toast.success(t("relation_deleted"));
      await loadWorkspaceState(groupId);
    } catch (error) {
      toast.error((error as Error).message || t("relation_delete_failed"));
    }
  }

  async function autoDetectRelations() {
    if (!groupId) return;
    setRelationRefreshing(true);
    try {
      const res = await workplaceApi.autoDetectRelations(groupId);
      if (!res.success) {
        toast.error(res.message || t("relation_auto_detect_failed"));
        return;
      }
      setRelations(res.data ?? []);
      toast.success(res.message || t("relation_auto_detected"));
    } catch (error) {
      toast.error((error as Error).message || t("relation_auto_detect_failed"));
    } finally {
      setRelationRefreshing(false);
    }
  }

  async function uploadDocument() {
    if (!groupId) {
      toast.error(t("select_workplace_first"));
      return;
    }
    if (!documentUploadFile) {
      toast.error(t("upload_document_first"));
      return;
    }

    setDocumentUploading(true);
    try {
      const res = await documentApi.upload(groupId, documentUploadFile, documentUploadName.trim() || undefined);
      if (!res.success) {
        toast.error(res.message || t("document_upload_failed"));
        return;
      }
      toast.success(t("document_uploaded"));
      setDocumentUploadFile(null);
      setDocumentUploadName("");
      await loadWorkspaceState(groupId);
    } catch (error) {
      toast.error((error as Error).message || t("document_upload_failed"));
    } finally {
      setDocumentUploading(false);
    }
  }

  async function searchDocuments() {
    if (!groupId) {
      toast.error(t("select_workplace_first"));
      return;
    }
    if (!documentSearchQuery.trim()) {
      setDocumentSearchResults([]);
      return;
    }

    setDocumentSearching(true);
    try {
      const res = await documentApi.search(groupId, documentSearchQuery.trim(), 5);
      if (!res.success) {
        toast.error(res.message || t("document_search_failed"));
        return;
      }
      setDocumentSearchResults(res.data ?? []);
    } catch (error) {
      toast.error((error as Error).message || t("document_search_failed"));
    } finally {
      setDocumentSearching(false);
    }
  }

  async function previewDocumentChunks(document: DocumentAsset) {
    setActiveDocument(document);
    setDocumentChunksOpen(true);
    setDocumentChunksLoading(true);
    try {
      const res = await documentApi.getChunks(document.id);
      setDocumentChunks(res.data ?? []);
    } catch (error) {
      toast.error((error as Error).message || t("document_search_failed"));
      setDocumentChunks([]);
    } finally {
      setDocumentChunksLoading(false);
    }
  }

  async function deleteDocument(documentId: number) {
    if (!groupId || !window.confirm(t("document_delete_confirm"))) return;
    try {
      const res = await documentApi.delete(documentId);
      if (!res.success) {
        toast.error(res.message || t("document_delete_failed"));
        return;
      }
      toast.success(t("document_deleted"));
      await loadWorkspaceState(groupId);
    } catch (error) {
      toast.error((error as Error).message || t("document_delete_failed"));
    }
  }

  return (
    <div className="space-y-6">
      <div className="page-heading">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-2">
            <p className="page-eyebrow">Workspace</p>
            <h1 className="text-[30px] font-medium tracking-[0.01em] text-[color:var(--color-text-primary)]">
              {activeWorkspace?.name ?? "Workspace"}
            </h1>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => setWorkspaceInfoOpen(true)} disabled={!groupId}>
              <FolderTree className="h-4 w-4" />
              信息
            </Button>
            <Button
              variant="outline"
              onClick={() => {
                setAssetView("tables");
                setAssetDialogOpen(true);
              }}
              disabled={!groupId}
            >
              <FileText className="h-4 w-4" />
              资产
            </Button>
            <Button variant="outline" onClick={() => setRelationManagerOpen(true)} disabled={!groupId}>
              <GitBranch className="h-4 w-4" />
              关系
            </Button>
            <Button variant="secondary" onClick={() => setCreateWorkspaceOpen(true)}>
              <Plus className="h-4 w-4" />
              {t("create_workspace")}
            </Button>
          </div>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1.2fr)_340px]">
        <Card className="analysis-stage rounded-[14px]">
          <CardContent className="space-y-5 pt-5">
            {loadingWorkspaces ? (
              <Skeleton className="h-10 w-full" />
            ) : (
              <select
                className="control-select h-10"
                value={groupId ?? ""}
                onChange={(event) => setGroupId(Number(event.target.value))}
              >
                {workspaces.map((workspace) => (
                  <option key={workspace.id} value={workspace.id}>
                    {workspace.name}
                  </option>
                ))}
              </select>
            )}

            <div className="flex flex-wrap gap-2">
              {loadingWorkspaceState ? (
                <>
                  <Skeleton className="h-9 w-24" />
                  <Skeleton className="h-9 w-24" />
                  <Skeleton className="h-9 w-24" />
                </>
              ) : (
                datasets.map((dataset) => (
                  <button
                    key={dataset.id}
                    type="button"
                    data-active={focusIds.includes(dataset.id)}
                    className="pill-toggle"
                    onClick={() => toggleFocus(dataset.id)}
                  >
                    {dataset.tableName}
                  </button>
                ))
              )}
            </div>

            <Textarea
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="输入你的分析问题..."
              rows={7}
              className="min-h-[180px] rounded-[14px] text-[16px] leading-7"
            />

            <div className="flex items-center justify-between gap-3">
              <div className="text-sm text-[color:var(--color-text-secondary)]">
                {analysis?.executionTime ? `${analysis.executionTime} ms` : "Ready"}
              </div>
              <Button onClick={() => void runAnalysis()} disabled={analyzing || !groupId || !query.trim()} size="lg">
                {analyzing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
                执行分析
              </Button>
            </div>
          </CardContent>
        </Card>

        <div className="grid gap-3">
          {metrics.map((metric) => (
            <div key={metric.label} className="subtle-panel px-4 py-4">
              <div className="text-[11px] uppercase tracking-[0.08em] text-[color:var(--color-text-tertiary)]">
                {metric.label}
              </div>
              <div className="mt-2 text-[22px] font-medium text-[color:var(--color-text-primary)]">{metric.value}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_360px]">
        <Card className="rounded-[14px]">
          <CardHeader>
            <CardTitle>本次结果</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            {rows.length ? (
              <div className="overflow-hidden rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)]">
                <Table>
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      {columns.map((column) => (
                        <TableHead key={column}>{column}</TableHead>
                      ))}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rows.slice(0, 20).map((row, index) => (
                      <TableRow key={index}>
                        {columns.map((column) => (
                          <TableCell key={column}>{String(row[column] ?? "")}</TableCell>
                        ))}
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            ) : (
              <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">
                还没有结果，先发起一次分析。
              </div>
            )}

            <div className="subtle-panel px-4 py-4">
              <div className="text-[11px] uppercase tracking-[0.08em] text-[color:var(--color-text-tertiary)]">摘要</div>
              <div className="mt-3 whitespace-pre-wrap text-sm leading-7 text-[color:var(--color-text-secondary)]">
                {analysisSummary || "分析摘要会显示在这里。"}
              </div>
            </div>

            <EvidenceSourcesPanel evidence={evidenceSummary} />

            <AnalysisProcessPanel
              executionLogs={executionLogs}
              validationReport={validationReport}
              riskNotices={riskNotices}
            />
          </CardContent>
        </Card>

        <Card className="rounded-[14px]">
          <CardHeader>
            <CardTitle>执行代码</CardTitle>
          </CardHeader>
          <CardContent>
            <pre className="min-h-[320px] overflow-x-auto rounded-[12px] bg-[color:var(--color-background-secondary)] p-4 text-sm leading-6 text-[color:var(--color-text-secondary)]">
              <code>{generatedCodeOrSql || "SQL / Python 将显示在这里。"}</code>
            </pre>
          </CardContent>
        </Card>
      </div>

      <Card className="rounded-[14px]">
        <CardHeader>
          <CardTitle>历史结果</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <ArtifactFilterControls
            search={artifactSearch}
            schemaFilter={artifactSchemaFilter}
            statusFilter={artifactStatusFilter}
            totalCount={recentArtifacts.length}
            filteredCount={filteredArtifacts.length}
            refreshing={artifactRefreshing}
            disabled={!groupId}
            onSearchChange={setArtifactSearch}
            onSchemaFilterChange={setArtifactSchemaFilter}
            onStatusFilterChange={changeArtifactStatusFilter}
            onRefresh={() => void refreshArtifacts()}
          />
          {recentArtifacts.length ? (
            <ArtifactCardGrid
              artifacts={filteredArtifacts}
              emptyText="没有匹配的历史结果。"
              onOpenDetail={(artifact) => void openArtifactDetail(artifact)}
            />
          ) : (
            <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">还没有历史结果。</div>
          )}
        </CardContent>
      </Card>

      <Card className="rounded-[14px]">
        <CardHeader>
          <CardTitle>长期记忆</CardTitle>
        </CardHeader>
        <CardContent>
          <MemoryManagementPanel
            memories={recentMemories}
            filteredMemories={filteredMemories}
            search={memorySearch}
            statusFilter={memoryStatusFilter}
            refreshing={memoryRefreshing}
            actionLoadingId={memoryActionLoadingId}
            disabled={!groupId}
            onSearchChange={setMemorySearch}
            onStatusFilterChange={changeMemoryStatusFilter}
            onRefresh={() => void refreshMemories()}
            onStatusAction={(memory, action) => void updateMemoryStatus(memory, action)}
            onImportanceChange={(memory, delta) => void updateMemoryImportance(memory, delta)}
          />
        </CardContent>
      </Card>

      <Dialog open={workspaceInfoOpen} onOpenChange={setWorkspaceInfoOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>工作区信息</DialogTitle>
            <DialogDescription>{activeWorkspace?.name ?? ""}</DialogDescription>
          </DialogHeader>
          <div className="grid gap-3 md:grid-cols-2">
            {metrics.map((metric) => (
              <div key={metric.label} className="subtle-panel px-4 py-3">
                <div className="text-[11px] uppercase tracking-[0.08em] text-[color:var(--color-text-tertiary)]">
                  {metric.label}
                </div>
                <div className="mt-2 text-[16px] font-medium text-[color:var(--color-text-primary)]">{metric.value}</div>
              </div>
            ))}
          </div>
          <div className="subtle-panel px-4 py-4 text-sm leading-7 text-[color:var(--color-text-secondary)]">
            {(activeWorkspace?.description || "").trim() || t("workspace_empty_description")}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={openDescriptionEditor}>
              <PencilLine className="h-4 w-4" />
              {t("edit_description")}
            </Button>
            <Button variant="secondary" onClick={() => setWorkspaceInfoOpen(false)}>
              {t("close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={assetDialogOpen} onOpenChange={setAssetDialogOpen}>
        <DialogContent className="max-w-6xl">
          <DialogHeader>
            <DialogTitle>工作区资产</DialogTitle>
          </DialogHeader>
          <div className="flex flex-wrap gap-2">
            {[
              ["tables", "数据表"],
              ["documents", "文档"],
              ["artifacts", "分析结果"],
              ["memories", "长期记忆"],
            ].map(([key, label]) => (
              <button
                key={key}
                type="button"
                data-active={assetView === key}
                className="pill-toggle"
                onClick={() => setAssetView(key as AssetView)}
              >
                {label}
              </button>
            ))}
          </div>

          {assetView === "tables" ? (
            loadingWorkspaceState ? (
              <div className="space-y-2">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            ) : datasets.length ? (
              <div className="overflow-hidden rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)]">
                <Table>
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead>{t("table_name")}</TableHead>
                      <TableHead>{t("table_table")}</TableHead>
                      <TableHead>{t("table_rows")}</TableHead>
                      <TableHead>{t("table_cols")}</TableHead>
                      <TableHead>{t("description_optional")}</TableHead>
                      <TableHead className="text-right">{t("relation_actions")}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {datasets.map((dataset) => (
                      <TableRow key={dataset.id}>
                        <TableCell className="font-medium text-[color:var(--color-text-primary)]">{dataset.name}</TableCell>
                        <TableCell className="mono-note">{dataset.tableName}</TableCell>
                        <TableCell className="mono-note">{dataset.rowCount ?? "-"}</TableCell>
                        <TableCell className="mono-note">{dataset.columnCount ?? "-"}</TableCell>
                        <TableCell className="max-w-[280px] text-[14px] text-[color:var(--color-text-secondary)]">
                          <span className="line-clamp-2">{(dataset.descriptionMd || "").trim() || t("no_description_yet")}</span>
                        </TableCell>
                        <TableCell>
                          <div className="flex justify-end gap-2">
                            <Button variant="outline" size="sm" onClick={() => toggleFocus(dataset.id)}>
                              {t("focus_for_analysis")}
                            </Button>
                            <Button size="sm" onClick={() => askAboutDataset(dataset)}>
                              {t("ask_about_table")}
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            ) : (
              <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">{t("no_workspace_tables")}</div>
            )
          ) : null}

          {assetView === "documents" ? (
            <div className="space-y-5">
              <div className="grid gap-4 lg:grid-cols-[minmax(0,1.05fr)_minmax(0,0.95fr)]">
                <div className="subtle-panel space-y-4 p-4">
                  <div className="text-sm font-medium text-[color:var(--color-text-primary)]">{t("document_upload_title")}</div>
                  <Input
                    value={documentUploadName}
                    onChange={(event) => setDocumentUploadName(event.target.value)}
                    placeholder={t("document_name_optional")}
                  />
                  <Input
                    type="file"
                    accept=".txt,.md,.pdf,.docx"
                    onChange={(event) => setDocumentUploadFile(event.target.files?.[0] ?? null)}
                  />
                  <div className="flex items-center justify-between gap-3 text-sm text-[color:var(--color-text-secondary)]">
                    <span>{documentUploadFile?.name ?? t("none")}</span>
                    <Button onClick={() => void uploadDocument()} disabled={!groupId || documentUploading || !documentUploadFile}>
                      {documentUploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
                      {t("upload")}
                    </Button>
                  </div>
                </div>

                <div className="subtle-panel space-y-4 p-4">
                  <div className="text-sm font-medium text-[color:var(--color-text-primary)]">{t("search_documents")}</div>
                  <div className="flex gap-2">
                    <Input
                      value={documentSearchQuery}
                      onChange={(event) => setDocumentSearchQuery(event.target.value)}
                      placeholder={t("document_search_placeholder")}
                      onKeyDown={(event) => {
                        if (event.key === "Enter") {
                          event.preventDefault();
                          void searchDocuments();
                        }
                      }}
                    />
                    <Button variant="outline" onClick={() => void searchDocuments()} disabled={!groupId || documentSearching}>
                      {documentSearching ? <Loader2 className="h-4 w-4 animate-spin" /> : <FileSearch className="h-4 w-4" />}
                      {t("search_documents")}
                    </Button>
                  </div>
                  <div className="space-y-3">
                    {documentSearchResults.length ? (
                      documentSearchResults.map((result, index) => (
                        <div
                          key={`${result.documentId}-${result.chunkIndex}-${index}`}
                          className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-4 py-3"
                        >
                          <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                            <div className="font-medium text-[color:var(--color-text-primary)]">{result.documentName}</div>
                            <div className="text-[color:var(--color-text-secondary)]">
                              {t("retrieval_mode")}: {result.retrievalMode ?? "-"}
                            </div>
                          </div>
                          <div className="mt-2 text-xs text-[color:var(--color-text-secondary)]">
                            {t("rerank_score")}: {result.rerankScore ?? result.score ?? "-"}
                            {result.rerankNotes ? ` · ${t("rerank_notes")}: ${result.rerankNotes}` : ""}
                          </div>
                          <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-[color:var(--color-text-secondary)]">
                            {result.chunkText}
                          </p>
                        </div>
                      ))
                    ) : (
                      <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-4 py-3 text-sm text-[color:var(--color-text-secondary)]">
                        {t("document_search_empty")}
                      </div>
                    )}
                  </div>
                </div>
              </div>

              {documents.length ? (
                <div className="overflow-hidden rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)]">
                  <Table>
                    <TableHeader>
                      <TableRow className="hover:bg-transparent">
                        <TableHead>{t("table_name")}</TableHead>
                        <TableHead>{t("document_type")}</TableHead>
                        <TableHead>{t("document_status")}</TableHead>
                        <TableHead>{t("document_chunks")}</TableHead>
                        <TableHead>{t("table_created")}</TableHead>
                        <TableHead className="text-right">{t("relation_actions")}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {documents.map((document) => (
                        <TableRow key={document.id}>
                          <TableCell>
                            <div className="space-y-1">
                              <div className="font-medium text-[color:var(--color-text-primary)]">{document.name}</div>
                              <div className="text-xs text-[color:var(--color-text-secondary)]">{document.originalFileName}</div>
                            </div>
                          </TableCell>
                          <TableCell className="mono-note">{document.fileType ?? "-"}</TableCell>
                          <TableCell className="mono-note">{document.processingStatus ?? "-"}</TableCell>
                          <TableCell className="mono-note">{document.chunkCount ?? 0}</TableCell>
                          <TableCell className="text-sm text-[color:var(--color-text-secondary)]">
                            {document.createdAt ? new Date(document.createdAt).toLocaleString() : "-"}
                          </TableCell>
                          <TableCell>
                            <div className="flex justify-end gap-2">
                              <Button variant="outline" size="sm" onClick={() => void previewDocumentChunks(document)}>
                                <FileText className="h-4 w-4" />
                                {t("document_preview_chunks")}
                              </Button>
                              <Button variant="outline" size="sm" onClick={() => void deleteDocument(document.id)}>
                                <Trash2 className="h-4 w-4" />
                                {t("remove")}
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              ) : (
                <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">{t("no_workspace_documents")}</div>
              )}
            </div>
          ) : null}

          {assetView === "artifacts" ? (
            <div className="space-y-4">
              <ArtifactFilterControls
                search={artifactSearch}
                schemaFilter={artifactSchemaFilter}
                statusFilter={artifactStatusFilter}
                totalCount={recentArtifacts.length}
                filteredCount={filteredArtifacts.length}
                refreshing={artifactRefreshing}
                disabled={!groupId}
                onSearchChange={setArtifactSearch}
                onSchemaFilterChange={setArtifactSchemaFilter}
                onStatusFilterChange={changeArtifactStatusFilter}
                onRefresh={() => void refreshArtifacts()}
              />
              {recentArtifacts.length ? (
                <ArtifactCardGrid
                  artifacts={filteredArtifacts}
                  emptyText="没有匹配的历史结果。"
                  gridClassName="grid gap-3 md:grid-cols-2"
                  onOpenDetail={(artifact) => void openArtifactDetail(artifact)}
                />
              ) : (
                <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">
                  {t("no_recent_artifacts")}
                </div>
              )}
            </div>
          ) : null}

          {assetView === "memories" ? (
            <MemoryManagementPanel
              memories={recentMemories}
              filteredMemories={filteredMemories}
              search={memorySearch}
              statusFilter={memoryStatusFilter}
              refreshing={memoryRefreshing}
              actionLoadingId={memoryActionLoadingId}
              disabled={!groupId}
              compact
              onSearchChange={setMemorySearch}
              onStatusFilterChange={changeMemoryStatusFilter}
              onRefresh={() => void refreshMemories()}
              onStatusAction={(memory, action) => void updateMemoryStatus(memory, action)}
              onImportanceChange={(memory, delta) => void updateMemoryImportance(memory, delta)}
            />
          ) : null}
        </DialogContent>
      </Dialog>

      <Dialog
        open={artifactDetailOpen}
        onOpenChange={(open) => {
          setArtifactDetailOpen(open);
          if (!open) {
            setArtifactDetail(null);
          }
        }}
      >
        <DialogContent className="max-w-6xl">
          <DialogHeader>
            <DialogTitle>分析结果详情</DialogTitle>
            <DialogDescription>
              {artifactDetail?.createdAt ? new Date(artifactDetail.createdAt).toLocaleString() : "历史分析结果"}
            </DialogDescription>
          </DialogHeader>

          {artifactDetailLoading && !artifactDetail ? (
            <div className="space-y-3">
              <Skeleton className="h-28 w-full" />
              <Skeleton className="h-40 w-full" />
              <Skeleton className="h-40 w-full" />
            </div>
          ) : artifactDetail ? (
            <div className="max-h-[72vh] space-y-4 overflow-y-auto pr-1">
              <div className="subtle-panel px-4 py-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-[11px] uppercase tracking-[0.08em] text-[color:var(--color-text-tertiary)]">
                      查询
                    </div>
                    <div className="mt-2 break-words text-sm font-medium text-[color:var(--color-text-primary)]">
                      {artifactDetail.userQuery || "分析记录"}
                    </div>
                  </div>
                  <div className="rounded-[8px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-2 py-1 text-xs text-[color:var(--color-text-tertiary)]">
                    {artifactDetail.reportAvailable ? "report available" : "legacy artifact"} /{" "}
                    {getArtifactStatusLabel(artifactDetail.artifactStatus)}
                  </div>
                </div>
                <div className="mt-4 whitespace-pre-wrap text-sm leading-7 text-[color:var(--color-text-secondary)]">
                  {artifactDetail.analysisReport?.summary ?? artifactDetail.summary ?? "无摘要"}
                </div>
              </div>

              {artifactDetailRows.length ? (
                <div className="overflow-hidden rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)]">
                  <Table>
                    <TableHeader>
                      <TableRow className="hover:bg-transparent">
                        {artifactDetailColumns.map((column) => (
                          <TableHead key={column}>{column}</TableHead>
                        ))}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {artifactDetailRows.slice(0, 20).map((row, index) => (
                        <TableRow key={index}>
                          {artifactDetailColumns.map((column) => (
                            <TableCell key={column}>{String(row[column] ?? "")}</TableCell>
                          ))}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              ) : (
                <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">
                  这个历史结果没有保存结果预览。
                </div>
              )}

              <EvidenceSourcesPanel evidence={artifactDetail.evidence ?? artifactDetail.analysisReport?.evidence} />

              <AnalysisProcessPanel
                executionLogs={artifactDetail.executionLogs ?? artifactDetail.analysisReport?.executionLogs}
                validationReport={artifactDetail.validationReport ?? artifactDetail.analysisReport?.validationReport}
                riskNotices={artifactDetail.riskNotices ?? artifactDetail.analysisReport?.riskNotices}
              />

              <ContextTracePanel trace={artifactDetail.contextTrace} />

              <div className="subtle-panel px-4 py-4">
                <div className="text-[11px] uppercase tracking-[0.08em] text-[color:var(--color-text-tertiary)]">
                  生成代码
                </div>
                <pre className="mt-3 max-h-[260px] overflow-x-auto rounded-[12px] bg-[color:var(--color-background-secondary)] p-4 text-sm leading-6 text-[color:var(--color-text-secondary)]">
                  <code>
                    {artifactDetail.analysisReport?.generatedCodeOrSql ??
                      artifactDetail.generatedCodeOrSql ??
                      "无 SQL / Python 代码。"}
                  </code>
                </pre>
              </div>
            </div>
          ) : (
            <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">
              未能加载分析结果详情。
            </div>
          )}

          <DialogFooter>
            {artifactDetail?.artifactStatus !== "ARCHIVED" && artifactDetail?.artifactStatus !== "DELETED" ? (
              <Button
                variant="outline"
                onClick={() => void updateArtifactStatus("archive")}
                disabled={!artifactDetail || artifactActionLoading}
              >
                {artifactActionLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Archive className="h-4 w-4" />}
                归档
              </Button>
            ) : null}
            {artifactDetail?.artifactStatus === "ARCHIVED" || artifactDetail?.artifactStatus === "DELETED" ? (
              <Button
                variant="outline"
                onClick={() => void updateArtifactStatus("restore")}
                disabled={!artifactDetail || artifactActionLoading}
              >
                {artifactActionLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}
                恢复
              </Button>
            ) : null}
            {artifactDetail?.artifactStatus !== "DELETED" ? (
              <Button
                variant="destructive"
                onClick={() => void updateArtifactStatus("delete")}
                disabled={!artifactDetail || artifactActionLoading}
              >
                {artifactActionLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                软删除
              </Button>
            ) : null}
            <Button
              variant="outline"
              onClick={() => artifactDetail && applyArtifact(artifactDetail)}
              disabled={!artifactDetail}
            >
              <FileText className="h-4 w-4" />
              加载到本次结果
            </Button>
            <Button variant="secondary" onClick={() => setArtifactDetailOpen(false)}>
              {t("close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={relationManagerOpen} onOpenChange={setRelationManagerOpen}>
        <DialogContent className="max-w-5xl">
          <DialogHeader>
            <DialogTitle>{t("relation_manager")}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => void autoDetectRelations()} disabled={!groupId || relationRefreshing}>
              {relationRefreshing ? <Loader2 className="h-4 w-4 animate-spin" /> : <WandSparkles className="h-4 w-4" />}
              {t("auto_detect_relations")}
            </Button>
            <Button onClick={() => openRelationDialog()} disabled={!groupId || datasets.length < 2}>
              <Plus className="h-4 w-4" />
              {t("new_relation")}
            </Button>
          </div>
          {relations.length ? (
            <div className="overflow-hidden rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)]">
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead>{t("relation_source")}</TableHead>
                    <TableHead>{t("relation_target")}</TableHead>
                    <TableHead>{t("relation_type")}</TableHead>
                    <TableHead>{t("confidence_optional")}</TableHead>
                    <TableHead className="text-right">{t("relation_actions")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {relationRows.map((relation) => (
                    <TableRow key={relation.id ?? `${relation.sourceLabel}-${relation.targetLabel}`}>
                      <TableCell className="mono-note">{relation.sourceLabel}</TableCell>
                      <TableCell className="mono-note">{relation.targetLabel}</TableCell>
                      <TableCell className="text-[14px] text-[color:var(--color-text-secondary)]">{relation.relationType || "-"}</TableCell>
                      <TableCell className="mono-note">{relation.confidence == null ? "-" : relation.confidence}</TableCell>
                      <TableCell>
                        <div className="flex justify-end gap-2">
                          <Button variant="outline" size="sm" onClick={() => openRelationDialog(relation)}>
                            <PencilLine className="h-4 w-4" />
                            {t("edit_relation")}
                          </Button>
                          <Button variant="outline" size="sm" onClick={() => relation.id && void deleteRelation(relation.id)}>
                            <Trash2 className="h-4 w-4" />
                            {t("remove")}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : (
            <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">{t("no_relation_defined")}</div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={descriptionOpen} onOpenChange={setDescriptionOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              {t("workspace_description_title")} {activeWorkspace ? `· ${activeWorkspace.name}` : ""}
            </DialogTitle>
            <DialogDescription>{t("workspace_description_hint")}</DialogDescription>
          </DialogHeader>
          <Textarea rows={12} value={descriptionDraft} onChange={(event) => setDescriptionDraft(event.target.value)} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setDescriptionOpen(false)}>
              {t("cancel")}
            </Button>
            <Button onClick={() => void saveWorkspaceDescription()} disabled={descriptionSaving}>
              {descriptionSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <PencilLine className="h-4 w-4" />}
              {t("save_description")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={createWorkspaceOpen} onOpenChange={setCreateWorkspaceOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t("create_workspace")}</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <Input
              value={createWorkspaceName}
              onChange={(event) => setCreateWorkspaceName(event.target.value)}
              placeholder={t("workspace_name_placeholder")}
            />
            <Textarea
              rows={8}
              value={createWorkspaceDescription}
              onChange={(event) => setCreateWorkspaceDescription(event.target.value)}
              placeholder={t("description_optional")}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateWorkspaceOpen(false)}>
              {t("cancel")}
            </Button>
            <Button onClick={() => void createWorkspace()} disabled={creatingWorkspace}>
              {creatingWorkspace ? <Loader2 className="h-4 w-4 animate-spin" /> : <Plus className="h-4 w-4" />}
              {t("create_workspace")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={relationOpen} onOpenChange={setRelationOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{editingRelation ? t("edit_relation") : t("new_relation")}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 md:grid-cols-2">
            <label className="space-y-2 text-sm text-[color:var(--color-text-secondary)]">
              <span>{t("source_dataset")}</span>
              <select
                className="control-select"
                value={relationForm.sourceDatasetId ?? ""}
                onChange={(event) =>
                  setRelationForm((prev) => ({
                    ...prev,
                    sourceDatasetId: event.target.value ? Number(event.target.value) : null,
                  }))
                }
              >
                <option value="">{t("source_dataset")}</option>
                {datasets.map((dataset) => (
                  <option key={dataset.id} value={dataset.id}>
                    {dataset.name} ({dataset.tableName})
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-2 text-sm text-[color:var(--color-text-secondary)]">
              <span>{t("target_dataset")}</span>
              <select
                className="control-select"
                value={relationForm.targetDatasetId ?? ""}
                onChange={(event) =>
                  setRelationForm((prev) => ({
                    ...prev,
                    targetDatasetId: event.target.value ? Number(event.target.value) : null,
                  }))
                }
              >
                <option value="">{t("target_dataset")}</option>
                {datasets.map((dataset) => (
                  <option key={dataset.id} value={dataset.id}>
                    {dataset.name} ({dataset.tableName})
                  </option>
                ))}
              </select>
            </label>

            <label className="space-y-2 text-sm text-[color:var(--color-text-secondary)]">
              <span>{t("source_column")}</span>
              <div className="space-y-2">
                <Input
                  list="relation-source-columns"
                  value={relationForm.sourceColumnName}
                  onChange={(event) => setRelationForm((prev) => ({ ...prev, sourceColumnName: event.target.value }))}
                  placeholder={t("column_name_placeholder")}
                />
                <datalist id="relation-source-columns">
                  {sourceColumns.map((column) => (
                    <option key={column.name} value={column.name} />
                  ))}
                </datalist>
                <div className="text-xs text-[color:var(--color-text-tertiary)]">
                  {columnLoading ? t("loading") : t("relation_source_columns")}
                  {sourceColumns.length ? `: ${sourceColumns.map((column) => column.name).slice(0, 8).join(", ")}` : ""}
                </div>
              </div>
            </label>

            <label className="space-y-2 text-sm text-[color:var(--color-text-secondary)]">
              <span>{t("target_column")}</span>
              <div className="space-y-2">
                <Input
                  list="relation-target-columns"
                  value={relationForm.targetColumnName}
                  onChange={(event) => setRelationForm((prev) => ({ ...prev, targetColumnName: event.target.value }))}
                  placeholder={t("column_name_placeholder")}
                />
                <datalist id="relation-target-columns">
                  {targetColumns.map((column) => (
                    <option key={column.name} value={column.name} />
                  ))}
                </datalist>
                <div className="text-xs text-[color:var(--color-text-tertiary)]">
                  {columnLoading ? t("loading") : t("relation_target_columns")}
                  {targetColumns.length ? `: ${targetColumns.map((column) => column.name).slice(0, 8).join(", ")}` : ""}
                </div>
              </div>
            </label>

            <label className="space-y-2 text-sm text-[color:var(--color-text-secondary)]">
              <span>{t("relation_type")}</span>
              <Input
                value={relationForm.relationType}
                onChange={(event) => setRelationForm((prev) => ({ ...prev, relationType: event.target.value }))}
                placeholder={t("relation_type_placeholder")}
              />
            </label>

            <label className="space-y-2 text-sm text-[color:var(--color-text-secondary)]">
              <span>{t("confidence_optional")}</span>
              <Input
                type="number"
                step="0.01"
                min="0"
                max="1"
                value={relationForm.confidence}
                onChange={(event) => setRelationForm((prev) => ({ ...prev, confidence: event.target.value }))}
                placeholder="0.95"
              />
            </label>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRelationOpen(false)}>
              {t("cancel")}
            </Button>
            <Button onClick={() => void saveRelation()} disabled={relationSaving}>
              {relationSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <GitBranch className="h-4 w-4" />}
              {t("save_description")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={documentChunksOpen}
        onOpenChange={(open) => {
          setDocumentChunksOpen(open);
          if (!open) {
            setActiveDocument(null);
            setDocumentChunks([]);
          }
        }}
      >
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>
              {t("document_chunks_title")}
              {activeDocument ? ` · ${activeDocument.name}` : ""}
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-3 max-h-[70vh] overflow-y-auto">
            {documentChunksLoading ? (
              <>
                <Skeleton className="h-24 w-full" />
                <Skeleton className="h-24 w-full" />
              </>
            ) : documentChunks.length ? (
              documentChunks.map((chunk) => (
                <div
                  key={chunk.id}
                  className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] p-4"
                >
                  <div className="mb-2 text-xs font-medium uppercase tracking-[0.08em] text-[color:var(--color-text-tertiary)]">
                    chunk #{chunk.chunkIndex}
                  </div>
                  <p className="whitespace-pre-wrap text-sm leading-6 text-[color:var(--color-text-secondary)]">
                    {chunk.chunkText}
                  </p>
                </div>
              ))
            ) : (
              <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">{t("no_chunks_available")}</div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDocumentChunksOpen(false)}>
              {t("close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
