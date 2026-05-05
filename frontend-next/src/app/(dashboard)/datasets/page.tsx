"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Database, Eye, FileSearch, FileText, FolderTree, Loader2, PencilLine, RefreshCw, Trash2, UploadCloud } from "lucide-react";
import { toast } from "sonner";
import { artifactApi, datasetApi, documentApi, workplaceApi } from "@/lib/api/client";
import type { Artifact, Dataset, DatasetPreviewRow, DocumentAsset, DocumentChunk, Workspace } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { useLanguage } from "@/components/providers/language-provider";

type WarehouseTab = "tables" | "documents" | "workspaces" | "artifacts";

function fmtDate(raw: string | undefined, localeTag: string) {
  if (!raw) return "-";
  const date = new Date(raw);
  if (Number.isNaN(date.valueOf())) return "-";
  return date.toLocaleString(localeTag, { month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

export default function DatasetsPage() {
  const { t, localeTag } = useLanguage();

  const [activeTab, setActiveTab] = useState<WarehouseTab>("tables");
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [scope, setScope] = useState<string>("all");
  const [search, setSearch] = useState("");

  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [documents, setDocuments] = useState<DocumentAsset[]>([]);
  const [artifacts, setArtifacts] = useState<Artifact[]>([]);

  const [displayName, setDisplayName] = useState("");
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [uploadingDataset, setUploadingDataset] = useState(false);
  const [uploadingDocument, setUploadingDocument] = useState(false);
  const [dragActive, setDragActive] = useState(false);

  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewRows, setPreviewRows] = useState<DatasetPreviewRow[]>([]);
  const [previewDataset, setPreviewDataset] = useState<Dataset | null>(null);

  const [descriptionOpen, setDescriptionOpen] = useState(false);
  const [editingDataset, setEditingDataset] = useState<Dataset | null>(null);
  const [descriptionDraft, setDescriptionDraft] = useState("");
  const [descriptionSaving, setDescriptionSaving] = useState(false);

  const [documentChunksOpen, setDocumentChunksOpen] = useState(false);
  const [documentChunksLoading, setDocumentChunksLoading] = useState(false);
  const [documentChunks, setDocumentChunks] = useState<DocumentChunk[]>([]);
  const [activeDocument, setActiveDocument] = useState<DocumentAsset | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const groupId = scope === "all" ? undefined : Number(scope);

  const workspaceMap = useMemo(() => new Map(workspaces.map((workspace) => [workspace.id, workspace.name])), [workspaces]);
  const numberLocale = localeTag === "zh-CN" ? "zh-CN" : "en-US";

  const filteredDatasets = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return datasets.filter((dataset) => {
      if (groupId && dataset.groupId !== groupId) return false;
      if (!keyword) return true;
      return [dataset.name, dataset.tableName, workspaceMap.get(dataset.groupId ?? -1) ?? ""]
        .join(" ")
        .toLowerCase()
        .includes(keyword);
    });
  }, [datasets, groupId, search, workspaceMap]);

  const filteredDocuments = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return documents.filter((document) => {
      if (groupId && document.groupId !== groupId) return false;
      if (!keyword) return true;
      return [document.name, document.originalFileName ?? "", workspaceMap.get(document.groupId) ?? ""]
        .join(" ")
        .toLowerCase()
        .includes(keyword);
    });
  }, [documents, groupId, search, workspaceMap]);

  const filteredWorkspaces = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return workspaces.filter((workspace) => {
      if (groupId && workspace.id !== groupId) return false;
      if (!keyword) return true;
      return [workspace.name, workspace.description ?? ""].join(" ").toLowerCase().includes(keyword);
    });
  }, [groupId, search, workspaces]);

  const filteredArtifacts = useMemo(() => {
    const keyword = search.trim().toLowerCase();
    return artifacts.filter((artifact) => {
      if (groupId && artifact.groupId !== groupId) return false;
      if (!keyword) return true;
      return [artifact.userQuery ?? "", artifact.summary ?? "", workspaceMap.get(artifact.groupId ?? -1) ?? ""]
        .join(" ")
        .toLowerCase()
        .includes(keyword);
    });
  }, [artifacts, groupId, search, workspaceMap]);

  const metrics = useMemo(
    () => [
      { label: "数据表", value: datasets.length.toLocaleString(numberLocale), icon: Database },
      { label: "文档", value: documents.length.toLocaleString(numberLocale), icon: FileText },
      { label: "工作区", value: workspaces.length.toLocaleString(numberLocale), icon: FolderTree },
      { label: "结果", value: artifacts.length.toLocaleString(numberLocale), icon: FileSearch },
    ],
    [artifacts.length, datasets.length, documents.length, numberLocale, workspaces.length]
  );

  const loadWarehouse = useCallback(
    async (showSpinner = true) => {
      if (showSpinner) setLoading(true);
      else setRefreshing(true);

      try {
        const workspaceRes = await workplaceApi.listWorkspaces();
        const workspaceList = workspaceRes.data ?? [];
        setWorkspaces(workspaceList);

        const [datasetRes, documentsByWorkspace, artifactsByWorkspace] = await Promise.all([
          datasetApi.list(),
          Promise.all(workspaceList.map(async (workspace) => ({ groupId: workspace.id, res: await documentApi.list(workspace.id) }))),
          Promise.all(workspaceList.map(async (workspace) => ({ groupId: workspace.id, res: await artifactApi.recent({ groupId: workspace.id, limit: 20 }) }))),
        ]);

        setDatasets(datasetRes.data ?? []);
        setDocuments(documentsByWorkspace.flatMap((entry) => entry.res.data ?? []));
        setArtifacts(
          artifactsByWorkspace
            .flatMap((entry) => entry.res.data ?? [])
            .sort((a, b) => new Date(b.createdAt ?? 0).valueOf() - new Date(a.createdAt ?? 0).valueOf())
        );
      } catch (error) {
        toast.error((error as Error).message || t("failed_load_datasets"));
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [t]
  );

  useEffect(() => {
    void loadWarehouse(true);
  }, [loadWarehouse]);

  async function uploadDataset() {
    if (!pendingFile) {
      toast.error(t("select_file_first"));
      return;
    }
    setUploadingDataset(true);
    try {
      const res = await datasetApi.upload(pendingFile, displayName.trim() || pendingFile.name, groupId);
      if (!res.success) {
        toast.error(res.message || t("upload_failed"));
        return;
      }
      toast.success(t("dataset_uploaded"));
      setPendingFile(null);
      setDisplayName("");
      if (fileInputRef.current) fileInputRef.current.value = "";
      await loadWarehouse(false);
    } catch (error) {
      toast.error((error as Error).message || t("upload_failed"));
    } finally {
      setUploadingDataset(false);
    }
  }

  async function uploadDocument() {
    if (!groupId) {
      toast.error(t("select_workplace_first"));
      return;
    }
    if (!pendingFile) {
      toast.error(t("upload_document_first"));
      return;
    }
    setUploadingDocument(true);
    try {
      const res = await documentApi.upload(groupId, pendingFile, displayName.trim() || pendingFile.name);
      if (!res.success) {
        toast.error(res.message || t("document_upload_failed"));
        return;
      }
      toast.success(t("document_uploaded"));
      setPendingFile(null);
      setDisplayName("");
      if (fileInputRef.current) fileInputRef.current.value = "";
      await loadWarehouse(false);
    } catch (error) {
      toast.error((error as Error).message || t("document_upload_failed"));
    } finally {
      setUploadingDocument(false);
    }
  }

  function onDropFile(event: React.DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragActive(false);
    const file = event.dataTransfer.files?.[0];
    if (!file) return;
    setPendingFile(file);
    setDisplayName(file.name.replace(/\.[^/.]+$/, ""));
  }

  async function openPreview(dataset: Dataset) {
    setPreviewDataset(dataset);
    setPreviewRows([]);
    setPreviewOpen(true);
    setPreviewLoading(true);
    try {
      const res = await datasetApi.preview(dataset.id, 100);
      setPreviewRows(res.data ?? []);
    } catch (error) {
      toast.error((error as Error).message || t("failed_load_datasets"));
    } finally {
      setPreviewLoading(false);
    }
  }

  function openDescriptionEditor(dataset: Dataset) {
    setEditingDataset(dataset);
    setDescriptionDraft(dataset.descriptionMd ?? "");
    setDescriptionOpen(true);
  }

  async function saveDescription() {
    if (!editingDataset) return;
    setDescriptionSaving(true);
    try {
      const res = await datasetApi.updateDescription(editingDataset.id, descriptionDraft);
      if (!res.success) {
        toast.error(res.message || t("save_failed"));
        return;
      }
      toast.success(t("description_saved"));
      setDescriptionOpen(false);
      await loadWarehouse(false);
    } catch (error) {
      toast.error((error as Error).message || t("save_failed"));
    } finally {
      setDescriptionSaving(false);
    }
  }

  async function deleteDataset(dataset: Dataset) {
    if (!window.confirm(t("delete_dataset_confirm"))) return;
    try {
      const res = await datasetApi.delete(dataset.id);
      if (!res.success) {
        toast.error(res.message || t("delete_failed"));
        return;
      }
      toast.success(t("dataset_deleted"));
      await loadWarehouse(false);
    } catch (error) {
      toast.error((error as Error).message || t("delete_failed"));
    }
  }

  async function deleteDocument(documentId: number) {
    if (!window.confirm(t("document_delete_confirm"))) return;
    try {
      const res = await documentApi.delete(documentId);
      if (!res.success) {
        toast.error(res.message || t("document_delete_failed"));
        return;
      }
      toast.success(t("document_deleted"));
      await loadWarehouse(false);
    } catch (error) {
      toast.error((error as Error).message || t("document_delete_failed"));
    }
  }

  async function previewDocumentChunks(document: DocumentAsset) {
    setActiveDocument(document);
    setDocumentChunks([]);
    setDocumentChunksOpen(true);
    setDocumentChunksLoading(true);
    try {
      const res = await documentApi.getChunks(document.id);
      setDocumentChunks(res.data ?? []);
    } catch (error) {
      toast.error((error as Error).message || t("document_search_failed"));
    } finally {
      setDocumentChunksLoading(false);
    }
  }

  const activeUploadAction = activeTab === "documents" ? uploadDocument : uploadDataset;
  const uploadButtonDisabled =
    activeTab === "documents" ? uploadingDocument || !pendingFile || !groupId : uploadingDataset || !pendingFile;
  const uploadLabel = activeTab === "documents" ? "上传文档" : "上传数据表";

  return (
    <div className="space-y-6">
      <div className="page-heading">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="space-y-2">
            <p className="page-eyebrow">Warehouse</p>
            <h1 className="text-[30px] font-medium tracking-[0.01em] text-[color:var(--color-text-primary)]">数据仓</h1>
            <p className="max-w-[680px] text-sm leading-7 text-[color:var(--color-text-secondary)]">
              统一查看数据表、文档、工作区和分析沉淀。这里是全部资产的总表。
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => void loadWarehouse(false)} disabled={refreshing}>
              {refreshing ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
              刷新
            </Button>
            <Button onClick={() => void activeUploadAction()} disabled={uploadButtonDisabled}>
              {(uploadingDataset || uploadingDocument) ? <Loader2 className="h-4 w-4 animate-spin" /> : <UploadCloud className="h-4 w-4" />}
              {uploadLabel}
            </Button>
          </div>
        </div>
      </div>

      <div className="grid gap-3 [grid-template-columns:repeat(auto-fit,minmax(180px,1fr))]">
        {metrics.map(({ label, value, icon: Icon }) => (
          <div key={label} className="metric-surface fade-reveal">
            <div className="mb-3 flex items-center justify-between gap-3">
              <span className="metric-label">{label}</span>
              <Icon className="h-4 w-4 text-[color:var(--color-text-secondary)]" />
            </div>
            <div className="metric-value">{value}</div>
          </div>
        ))}
      </div>

      <div className="grid gap-4 xl:grid-cols-[340px_minmax(0,1fr)]">
        <aside className="surface-card space-y-4 p-4">
          <div className="space-y-2">
            <div className="text-xs uppercase tracking-[0.12em] text-[color:var(--color-text-tertiary)]">Upload</div>
            <div
              role="button"
              tabIndex={0}
              onClick={() => fileInputRef.current?.click()}
              onDragOver={(event) => {
                event.preventDefault();
                setDragActive(true);
              }}
              onDragLeave={() => setDragActive(false)}
              onDrop={onDropFile}
              className={cn(
                "rounded-[14px] border px-4 py-8 text-center transition-colors",
                dragActive
                  ? "border-[color:var(--color-border-info)] bg-[color:var(--color-background-info)]"
                  : "border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)]"
              )}
            >
              <UploadCloud className="mx-auto h-5 w-5 text-[color:var(--color-text-secondary)]" />
              <div className="mt-3 text-sm text-[color:var(--color-text-primary)]">拖入文件或点击选择</div>
              <div className="mt-2 text-xs text-[color:var(--color-text-secondary)]">
                {pendingFile?.name || "支持 CSV / Excel / TXT / Markdown / PDF / DOCX"}
              </div>
            </div>

            <input
              ref={fileInputRef}
              type="file"
              accept=".csv,.xlsx,.xls,.txt,.md,.pdf,.docx"
              className="hidden"
              onChange={(event) => {
                const file = event.target.files?.[0] ?? null;
                setPendingFile(file);
                if (file) setDisplayName(file.name.replace(/\.[^/.]+$/, ""));
              }}
            />

            <Input value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder="显示名称（可选）" />
            <select className="control-select" value={scope} onChange={(event) => setScope(event.target.value)}>
              <option value="all">全部工作区</option>
              {workspaces.map((workspace) => (
                <option key={workspace.id} value={workspace.id}>
                  {workspace.name}
                </option>
              ))}
            </select>
            <div className="text-xs text-[color:var(--color-text-tertiary)]">
              {activeTab === "documents" ? "文档上传必须绑定工作区。" : "数据表可进入共享池，也可绑定工作区。"}
            </div>
          </div>

          <div className="space-y-2">
            <div className="text-xs uppercase tracking-[0.12em] text-[color:var(--color-text-tertiary)]">Search</div>
            <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="搜索当前 tab 里的资产..." />
          </div>

          <div className="space-y-2">
            {[
              { key: "tables", label: "数据表" },
              { key: "documents", label: "文档" },
              { key: "workspaces", label: "工作区" },
              { key: "artifacts", label: "分析结果" },
            ].map((item) => {
              const active = activeTab === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  onClick={() => setActiveTab(item.key as WarehouseTab)}
                  className={cn(
                    "w-full rounded-[12px] border px-3 py-3 text-left text-sm transition-colors",
                    active
                      ? "border-[color:var(--color-border-info)] bg-[color:var(--color-background-info)] text-[color:var(--color-text-primary)]"
                      : "border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)] text-[color:var(--color-text-secondary)] hover:bg-[color:var(--color-sidebar-hover-background)]"
                  )}
                >
                  {item.label}
                </button>
              );
            })}
          </div>
        </aside>

        <section className="surface-card space-y-4 p-4">
          {loading ? (
            <div className="space-y-3">
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
              <Skeleton className="h-12 w-full" />
            </div>
          ) : null}

          {!loading && activeTab === "tables" ? (
            filteredDatasets.length ? (
              <div className="overflow-hidden rounded-[14px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)]">
                <Table>
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead>{t("table_name")}</TableHead>
                      <TableHead>{t("table_table")}</TableHead>
                      <TableHead>{t("table_workplace")}</TableHead>
                      <TableHead>{t("table_rows")}</TableHead>
                      <TableHead>{t("table_cols")}</TableHead>
                      <TableHead>{t("table_created")}</TableHead>
                      <TableHead className="text-right">{t("settings")}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredDatasets.map((dataset) => (
                      <TableRow key={dataset.id}>
                        <TableCell className="font-medium text-[color:var(--color-text-primary)]">{dataset.name}</TableCell>
                        <TableCell className="mono-note">{dataset.tableName}</TableCell>
                        <TableCell className="text-sm text-[color:var(--color-text-secondary)]">
                          {dataset.groupId ? workspaceMap.get(dataset.groupId) ?? `#${dataset.groupId}` : t("global")}
                        </TableCell>
                        <TableCell className="mono-note">{dataset.rowCount ?? "-"}</TableCell>
                        <TableCell className="mono-note">{dataset.columnCount ?? "-"}</TableCell>
                        <TableCell className="text-sm text-[color:var(--color-text-secondary)]">
                          {fmtDate(dataset.createdAt, localeTag)}
                        </TableCell>
                        <TableCell>
                          <div className="flex justify-end gap-2">
                            <Button variant="outline" size="sm" onClick={() => void openPreview(dataset)}>
                              <Eye className="h-4 w-4" />
                              {t("preview")}
                            </Button>
                            <Button variant="outline" size="sm" onClick={() => openDescriptionEditor(dataset)}>
                              <PencilLine className="h-4 w-4" />
                              {t("describe")}
                            </Button>
                            <Button variant="outline" size="sm" onClick={() => void deleteDataset(dataset)}>
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
              <div className="subtle-panel px-4 py-6 text-sm text-[color:var(--color-text-secondary)]">当前范围没有数据表。</div>
            )
          ) : null}

          {!loading && activeTab === "documents" ? (
            filteredDocuments.length ? (
              <div className="overflow-hidden rounded-[14px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)]">
                <Table>
                  <TableHeader>
                    <TableRow className="hover:bg-transparent">
                      <TableHead>{t("table_name")}</TableHead>
                      <TableHead>{t("table_workplace")}</TableHead>
                      <TableHead>{t("document_type")}</TableHead>
                      <TableHead>{t("document_status")}</TableHead>
                      <TableHead>{t("document_chunks")}</TableHead>
                      <TableHead className="text-right">{t("settings")}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {filteredDocuments.map((document) => (
                      <TableRow key={document.id}>
                        <TableCell>
                          <div className="space-y-1">
                            <div className="font-medium text-[color:var(--color-text-primary)]">{document.name}</div>
                            <div className="text-xs text-[color:var(--color-text-secondary)]">{document.originalFileName}</div>
                          </div>
                        </TableCell>
                        <TableCell className="text-sm text-[color:var(--color-text-secondary)]">{workspaceMap.get(document.groupId) ?? `#${document.groupId}`}</TableCell>
                        <TableCell className="mono-note">{document.fileType ?? "-"}</TableCell>
                        <TableCell className="mono-note">{document.processingStatus ?? "-"}</TableCell>
                        <TableCell className="mono-note">{document.chunkCount ?? 0}</TableCell>
                        <TableCell>
                          <div className="flex justify-end gap-2">
                            <Button variant="outline" size="sm" onClick={() => void previewDocumentChunks(document)}>
                              <Eye className="h-4 w-4" />
                              Chunks
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
              <div className="subtle-panel px-4 py-6 text-sm text-[color:var(--color-text-secondary)]">当前范围没有文档。</div>
            )
          ) : null}

          {!loading && activeTab === "workspaces" ? (
            filteredWorkspaces.length ? (
              <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                {filteredWorkspaces.map((workspace) => {
                  const workspaceDatasets = datasets.filter((dataset) => dataset.groupId === workspace.id).length;
                  const workspaceDocuments = documents.filter((document) => document.groupId === workspace.id).length;
                  const workspaceArtifacts = artifacts.filter((artifact) => artifact.groupId === workspace.id).length;
                  return (
                    <button
                      key={workspace.id}
                      type="button"
                      onClick={() => (window.location.href = `/workspace?groupId=${workspace.id}`)}
                      className="subtle-panel px-4 py-4 text-left transition-colors hover:border-[color:var(--color-border-info)] hover:bg-[color:var(--color-sidebar-hover-background)]"
                    >
                      <div className="text-[16px] font-medium text-[color:var(--color-text-primary)]">{workspace.name}</div>
                      <div className="mt-3 line-clamp-3 text-sm leading-6 text-[color:var(--color-text-secondary)]">
                        {workspace.description || "暂无描述"}
                      </div>
                      <div className="mt-4 grid grid-cols-3 gap-2 text-xs text-[color:var(--color-text-tertiary)]">
                        <span>{workspaceDatasets} 表</span>
                        <span>{workspaceDocuments} 文档</span>
                        <span>{workspaceArtifacts} 结果</span>
                      </div>
                    </button>
                  );
                })}
              </div>
            ) : (
              <div className="subtle-panel px-4 py-6 text-sm text-[color:var(--color-text-secondary)]">当前范围没有工作区。</div>
            )
          ) : null}

          {!loading && activeTab === "artifacts" ? (
            filteredArtifacts.length ? (
              <div className="grid gap-3 lg:grid-cols-2">
                {filteredArtifacts.map((artifact) => (
                  <button
                    key={artifact.id}
                    type="button"
                    onClick={() => {
                      if (artifact.groupId) window.location.href = `/workspace?groupId=${artifact.groupId}`;
                    }}
                    className="subtle-panel px-4 py-4 text-left transition-colors hover:border-[color:var(--color-border-info)] hover:bg-[color:var(--color-sidebar-hover-background)]"
                  >
                    <div className="line-clamp-2 text-sm font-medium text-[color:var(--color-text-primary)]">
                      {artifact.userQuery || t("artifact_query")}
                    </div>
                    <div className="mt-3 line-clamp-4 text-sm leading-6 text-[color:var(--color-text-secondary)]">
                      {artifact.summary || "无摘要"}
                    </div>
                    <div className="mt-4 flex flex-wrap gap-3 text-xs text-[color:var(--color-text-tertiary)]">
                      <span>{workspaceMap.get(artifact.groupId ?? -1) ?? "未绑定工作区"}</span>
                      <span>{fmtDate(artifact.createdAt, localeTag)}</span>
                    </div>
                  </button>
                ))}
              </div>
            ) : (
              <div className="subtle-panel px-4 py-6 text-sm text-[color:var(--color-text-secondary)]">当前范围没有分析结果。</div>
            )
          ) : null}
        </section>
      </div>

      <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
        <DialogContent className="max-w-6xl">
          <DialogHeader>
            <DialogTitle>
              {t("preview_dataset_title")} {previewDataset ? `· ${previewDataset.name}` : ""}
            </DialogTitle>
            <DialogDescription>{t("rows_shown", { count: String(previewRows.length) })}</DialogDescription>
          </DialogHeader>
          {previewLoading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : previewRows.length ? (
            <div className="max-h-[60vh] overflow-auto rounded-[14px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] bg-[color:var(--color-background-secondary)]">
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    {Object.keys(previewRows[0]).map((column) => (
                      <TableHead key={column}>{column}</TableHead>
                    ))}
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {previewRows.map((row, index) => (
                    <TableRow key={index}>
                      {Object.keys(previewRows[0]).map((column) => (
                        <TableCell key={column}>{String(row[column] ?? "")}</TableCell>
                      ))}
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : (
            <div className="subtle-panel px-4 py-6 text-sm text-[color:var(--color-text-secondary)]">{t("preview_empty")}</div>
          )}
        </DialogContent>
      </Dialog>

      <Dialog open={descriptionOpen} onOpenChange={setDescriptionOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              {t("dataset_description_title")} {editingDataset ? `· ${editingDataset.name}` : ""}
            </DialogTitle>
            <DialogDescription>{t("dataset_description_hint")}</DialogDescription>
          </DialogHeader>
          <Textarea rows={12} value={descriptionDraft} onChange={(event) => setDescriptionDraft(event.target.value)} />
          <DialogFooter>
            <Button variant="outline" onClick={() => setDescriptionOpen(false)}>
              {t("cancel")}
            </Button>
            <Button onClick={() => void saveDescription()} disabled={descriptionSaving}>
              {descriptionSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : <PencilLine className="h-4 w-4" />}
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
          <div className="max-h-[70vh] space-y-3 overflow-y-auto">
            {documentChunksLoading ? (
              <>
                <Skeleton className="h-24 w-full" />
                <Skeleton className="h-24 w-full" />
              </>
            ) : documentChunks.length ? (
              documentChunks.map((chunk) => (
                <div key={chunk.id} className="subtle-panel px-4 py-4">
                  <div className="mb-2 text-xs uppercase tracking-[0.12em] text-[color:var(--color-text-tertiary)]">
                    chunk #{chunk.chunkIndex}
                  </div>
                  <div className="whitespace-pre-wrap text-sm leading-7 text-[color:var(--color-text-secondary)]">{chunk.chunkText}</div>
                </div>
              ))
            ) : (
              <div className="subtle-panel px-4 py-6 text-sm text-[color:var(--color-text-secondary)]">{t("no_chunks_available")}</div>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}
