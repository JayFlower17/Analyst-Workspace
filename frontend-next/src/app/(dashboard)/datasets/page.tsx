"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Database, FileSpreadsheet, FolderTree, Loader2, RefreshCw, UploadCloud } from "lucide-react";
import { toast } from "sonner";
import { datasetApi, workplaceApi } from "@/lib/api/client";
import type { Dataset, Workspace } from "@/lib/types";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useLanguage } from "@/components/providers/language-provider";

function fmtDate(raw: string | undefined, localeTag: string) {
  if (!raw) return "-";
  const d = new Date(raw);
  if (Number.isNaN(d.valueOf())) return "-";
  return d.toLocaleString(localeTag);
}

export default function DatasetsPage() {
  const { t, localeTag } = useLanguage();

  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [scope, setScope] = useState<string>("all");
  const [displayName, setDisplayName] = useState("");
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const [dragActive, setDragActive] = useState(false);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const groupId = scope === "all" ? undefined : Number(scope);

  const workspaceMap = useMemo(
    () => new Map(workspaces.map((workspace) => [workspace.id, workspace.name])),
    [workspaces]
  );

  const numberLocale = localeTag === "zh-CN" ? "zh-CN" : "en-US";

  const metrics = useMemo(() => {
    const totalRows = datasets.reduce((sum, item) => sum + (item.rowCount ?? 0), 0);
    const totalColumns = datasets.reduce((sum, item) => sum + (item.columnCount ?? 0), 0);
    const connected = datasets.filter((item) => item.groupId).length;
    return {
      datasets: datasets.length,
      rows: totalRows.toLocaleString(numberLocale),
      columns: totalColumns.toLocaleString(numberLocale),
      connected,
    };
  }, [datasets, numberLocale]);

  const initialize = useCallback(async () => {
    setLoading(true);
    try {
      const res = await workplaceApi.listWorkspaces();
      setWorkspaces(res.data ?? []);
    } catch (error) {
      toast.error((error as Error).message || t("failed_load_workplaces"));
    } finally {
      setLoading(false);
    }
  }, [t]);

  const loadDatasets = useCallback(async () => {
    setLoading(true);
    try {
      const res = await datasetApi.list(groupId);
      setDatasets(res.data ?? []);
    } catch (error) {
      toast.error((error as Error).message || t("failed_load_datasets"));
    } finally {
      setLoading(false);
    }
  }, [groupId, t]);

  useEffect(() => {
    void initialize();
  }, [initialize]);

  useEffect(() => {
    void loadDatasets();
  }, [loadDatasets]);

  async function uploadDataset() {
    if (!pendingFile) {
      toast.error(t("select_file_first"));
      return;
    }
    setUploading(true);
    try {
      const res = await datasetApi.upload(pendingFile, displayName.trim() || pendingFile.name, groupId);
      if (!res.success) {
        toast.error(res.message || t("upload_failed"));
        return;
      }
      toast.success(t("dataset_uploaded"));
      setPendingFile(null);
      setDisplayName("");
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      await loadDatasets();
    } catch (error) {
      toast.error((error as Error).message || t("upload_failed"));
    } finally {
      setUploading(false);
    }
  }

  function onDropFile(event: React.DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragActive(false);
    const file = event.dataTransfer.files?.[0];
    if (file) {
      setPendingFile(file);
      setDisplayName(file.name.replace(/\.[^/.]+$/, ""));
    }
  }

  const uploadTarget =
    scope === "all" ? t("global_dataset_pool") : workspaceMap.get(Number(scope)) ?? t("global_dataset_pool");

  return (
    <div className="space-y-6">
      <div className="page-heading">
        <p className="page-eyebrow">{t("datasets")}</p>
        <h2>{t("page_dataset_manager")}</h2>
        <p className="page-description">{t("datasets_page_description")}</p>
      </div>

      <div className="grid gap-3 [grid-template-columns:repeat(auto-fit,minmax(160px,1fr))]">
        <MetricCard title={t("datasets")} value={String(metrics.datasets)} icon={Database} />
        <MetricCard title={t("workspace_linked")} value={String(metrics.connected)} icon={FolderTree} />
        <MetricCard title={t("total_rows")} value={metrics.rows} icon={FileSpreadsheet} />
        <MetricCard title={t("total_columns")} value={metrics.columns} icon={Database} />
      </div>

      <Card className="fade-reveal stagger-1">
        <CardHeader className="space-y-2">
          <CardTitle>{t("upload_dataset_title")}</CardTitle>
          <CardDescription>{t("upload_dataset_description")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
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
              "rounded-[var(--border-radius-lg)] p-6 text-center transition-[background-color] duration-150",
              dragActive
                ? "bg-[color:var(--color-background-info)]"
                : "bg-[color:var(--color-background-primary)]"
            )}
          >
            <UploadCloud className="mx-auto h-6 w-6 text-[color:var(--color-text-secondary)]" />
            <p className="mt-2 text-[14px] text-[color:var(--color-text-primary)]">{t("drag_hint")}</p>
            <p className="mt-1 text-[12px] font-normal text-[color:var(--color-text-secondary)]">
              {t("current_file", { name: pendingFile?.name ?? t("none") })}
            </p>
          </div>

          <input
            ref={fileInputRef}
            type="file"
            accept=".csv,.xlsx,.xls"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0] ?? null;
              setPendingFile(file);
              if (file) {
                setDisplayName(file.name.replace(/\.[^/.]+$/, ""));
              }
            }}
          />

          <div className="grid gap-3 md:grid-cols-2">
            <Input
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder={t("display_name_optional")}
            />
            <select value={scope} onChange={(event) => setScope(event.target.value)} className="control-select">
              <option value="all">{t("no_workplace_global")}</option>
              {workspaces.map((workspace) => (
                <option key={workspace.id} value={workspace.id}>
                  {workspace.name}
                </option>
              ))}
            </select>
          </div>

          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="text-[12px] font-normal text-[color:var(--color-text-secondary)]">
              {t("upload_target", { target: uploadTarget })}
            </div>
            <Button onClick={() => void uploadDataset()} disabled={uploading || !pendingFile}>
              {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <UploadCloud className="h-4 w-4" />}
              {t("upload")}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card className="fade-reveal stagger-2">
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <CardTitle>{t("dataset_inventory")}</CardTitle>
              <CardDescription>{t("dataset_inventory_description")}</CardDescription>
            </div>
            <Button variant="secondary" onClick={() => void loadDatasets()} disabled={loading}>
              {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
              {t("refresh")}
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : datasets.length ? (
            <div className="rounded-[var(--border-radius-lg)] bg-[color:var(--color-background-primary)]">
              <Table>
                <TableHeader>
                  <TableRow className="hover:bg-transparent">
                    <TableHead>{t("table_name")}</TableHead>
                    <TableHead>{t("table_table")}</TableHead>
                    <TableHead>{t("table_workplace")}</TableHead>
                    <TableHead>{t("table_rows")}</TableHead>
                    <TableHead>{t("table_cols")}</TableHead>
                    <TableHead>{t("table_created")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {datasets.map((dataset) => (
                    <TableRow key={dataset.id}>
                      <TableCell className="font-medium text-[color:var(--color-text-primary)]">{dataset.name}</TableCell>
                      <TableCell className="mono-note">{dataset.tableName}</TableCell>
                      <TableCell className="text-[14px] text-[color:var(--color-text-secondary)]">
                        {dataset.groupId ? workspaceMap.get(dataset.groupId) ?? `#${dataset.groupId}` : t("global")}
                      </TableCell>
                      <TableCell className="mono-note">{dataset.rowCount ?? "-"}</TableCell>
                      <TableCell className="mono-note">{dataset.columnCount ?? "-"}</TableCell>
                      <TableCell className="text-[14px] text-[color:var(--color-text-secondary)]">
                        {fmtDate(dataset.createdAt, localeTag)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : (
            <div className="metric-surface text-[14px] text-[color:var(--color-text-secondary)]">
              {t("no_dataset_found_scope")}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function MetricCard({
  title,
  value,
  icon: Icon,
}: {
  title: string;
  value: string;
  icon: React.ComponentType<{ className?: string }>;
}) {
  return (
    <div className="metric-surface fade-reveal">
      <div className="mb-3 flex items-center justify-between gap-3">
        <span className="metric-label">{title}</span>
        <Icon className="h-4 w-4 text-[color:var(--color-text-secondary)]" />
      </div>
      <div className="metric-value">{value}</div>
    </div>
  );
}
