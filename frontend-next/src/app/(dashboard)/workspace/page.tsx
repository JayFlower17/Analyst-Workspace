"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  FileSearch,
  FileText,
  FolderTree,
  GitBranch,
  Loader2,
  PencilLine,
  Plus,
  Sparkles,
  Trash2,
  Upload,
  WandSparkles,
} from "lucide-react";
import { toast } from "sonner";
import { artifactApi, datasetApi, documentApi, workplaceApi } from "@/lib/api/client";
import type {
  AnalysisResult,
  Artifact,
  Dataset,
  DatasetColumnInfo,
  DatasetRelation,
  DocumentAsset,
  DocumentChunk,
  DocumentSearchResult,
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

  const [workspaceInfoOpen, setWorkspaceInfoOpen] = useState(false);
  const [assetDialogOpen, setAssetDialogOpen] = useState(false);
  const [assetView, setAssetView] = useState<"tables" | "documents" | "artifacts">("tables");
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
  const rows = useMemo(() => analysis?.data ?? [], [analysis]);
  const columns = useMemo(() => (rows.length ? Object.keys(rows[0]) : []), [rows]);

  const metrics = useMemo(
    () => [
      { label: "数据表", value: datasets.length },
      { label: "文档", value: documents.length },
      { label: "关系", value: relations.length },
      { label: "焦点", value: focusIds.length },
    ],
    [datasets.length, documents.length, relations.length, focusIds.length]
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
        const [workspaceRes, datasetRes, relationRes, documentRes, artifactRes] = await Promise.all([
          workplaceApi.getWorkspace(nextGroupId),
          workplaceApi.getDatasets(nextGroupId),
          workplaceApi.getRelations(nextGroupId),
          documentApi.list(nextGroupId),
          artifactApi.recent({ groupId: nextGroupId, limit: 8 }),
        ]);
        setActiveWorkspace(workspaceRes.data ?? null);
        setDatasets(datasetRes.data ?? []);
        setRelations(relationRes.data ?? []);
        setDocuments(documentRes.data ?? []);
        setRecentArtifacts(artifactRes.data ?? []);
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
    [t]
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

  function applyArtifact(artifact: Artifact) {
    setQuery(artifact.userQuery ?? "");
    setAnalysis({
      success: true,
      message: undefined,
      data: artifact.resultPreviewJson ? JSON.parse(artifact.resultPreviewJson) : [],
      generatedCodeOrSql: artifact.generatedCodeOrSql,
      summary: artifact.summary,
      recommendedChart: artifact.chartType,
      artifactId: artifact.id,
    });
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
      const artifactRes = await artifactApi.recent({ groupId, limit: 8 });
      setRecentArtifacts(artifactRes.data ?? []);
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
                {analysis?.summary || "分析摘要会显示在这里。"}
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="rounded-[14px]">
          <CardHeader>
            <CardTitle>执行代码</CardTitle>
          </CardHeader>
          <CardContent>
            <pre className="min-h-[320px] overflow-x-auto rounded-[12px] bg-[color:var(--color-background-secondary)] p-4 text-sm leading-6 text-[color:var(--color-text-secondary)]">
              <code>{analysis?.generatedCodeOrSql || "SQL / Python 将显示在这里。"}</code>
            </pre>
          </CardContent>
        </Card>
      </div>

      <Card className="rounded-[14px]">
        <CardHeader>
          <CardTitle>历史结果</CardTitle>
        </CardHeader>
        <CardContent>
          {recentArtifacts.length ? (
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {recentArtifacts.map((artifact) => (
                <button
                  key={artifact.id}
                  type="button"
                  onClick={() => applyArtifact(artifact)}
                  className="subtle-panel text-left px-4 py-4 transition-colors hover:border-[color:rgba(242,201,76,0.28)] hover:bg-[color:#202020]"
                >
                  <div className="line-clamp-2 text-sm font-medium text-[color:var(--color-text-primary)]">
                    {artifact.userQuery || "分析记录"}
                  </div>
                  <div className="mt-3 line-clamp-4 text-sm leading-6 text-[color:var(--color-text-secondary)]">
                    {artifact.summary || "无摘要"}
                  </div>
                  <div className="mt-4 text-xs text-[color:var(--color-text-tertiary)]">
                    {artifact.createdAt ? new Date(artifact.createdAt).toLocaleString() : ""}
                  </div>
                </button>
              ))}
            </div>
          ) : (
            <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">还没有历史结果。</div>
          )}
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
            ].map(([key, label]) => (
              <button
                key={key}
                type="button"
                data-active={assetView === key}
                className="pill-toggle"
                onClick={() => setAssetView(key as "tables" | "documents" | "artifacts")}
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
            recentArtifacts.length ? (
              <div className="grid gap-3 md:grid-cols-2">
                {recentArtifacts.map((artifact) => (
                  <button
                    key={artifact.id}
                    type="button"
                    onClick={() => applyArtifact(artifact)}
                    className="subtle-panel text-left px-4 py-4 transition-colors hover:border-[color:rgba(242,201,76,0.28)] hover:bg-[color:#202020]"
                  >
                    <div className="line-clamp-2 text-sm font-medium text-[color:var(--color-text-primary)]">
                      {artifact.userQuery || t("artifact_query")}
                    </div>
                    <div className="mt-3 line-clamp-4 text-sm leading-6 text-[color:var(--color-text-secondary)]">
                      {artifact.summary || "无摘要"}
                    </div>
                    <div className="mt-4 text-xs text-[color:var(--color-text-tertiary)]">
                      {artifact.createdAt ? new Date(artifact.createdAt).toLocaleString() : ""}
                    </div>
                  </button>
                ))}
              </div>
            ) : (
              <div className="subtle-panel px-4 py-4 text-sm text-[color:var(--color-text-secondary)]">{t("no_recent_artifacts")}</div>
            )
          ) : null}
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
