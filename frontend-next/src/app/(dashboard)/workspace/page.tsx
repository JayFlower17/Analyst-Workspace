"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Loader2, Network, Sparkles } from "lucide-react";
import { toast } from "sonner";
import { workplaceApi } from "@/lib/api/client";
import type { AnalysisResult, Dataset, Workspace } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { useLanguage } from "@/components/providers/language-provider";

export default function WorkspacePage() {
  const { t } = useLanguage();

  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [groupId, setGroupId] = useState<number | null>(null);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [focusIds, setFocusIds] = useState<number[]>([]);
  const [query, setQuery] = useState("");
  const [loadingWorkspaces, setLoadingWorkspaces] = useState(true);
  const [loadingDatasets, setLoadingDatasets] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const [analysis, setAnalysis] = useState<AnalysisResult | null>(null);

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

  const loadDatasets = useCallback(
    async (nextGroupId: number) => {
      setLoadingDatasets(true);
      try {
        const res = await workplaceApi.getDatasets(nextGroupId);
        setDatasets(res.data ?? []);
        setFocusIds([]);
      } catch (error) {
        toast.error((error as Error).message || t("failed_load_datasets"));
      } finally {
        setLoadingDatasets(false);
      }
    },
    [t]
  );

  useEffect(() => {
    void loadWorkspaces();
  }, [loadWorkspaces]);

  useEffect(() => {
    if (!groupId) return;
    void loadDatasets(groupId);
  }, [groupId, loadDatasets]);

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
      if (!result.success) {
        toast.error(result.message || t("analysis_failed"));
      }
    } catch (error) {
      toast.error((error as Error).message || t("analysis_request_failed"));
    } finally {
      setAnalyzing(false);
    }
  }

  const rows = useMemo(() => analysis?.data ?? [], [analysis]);
  const columns = useMemo(() => (rows.length ? Object.keys(rows[0]) : []), [rows]);

  return (
    <div className="space-y-6">
      <div className="page-heading">
        <p className="page-eyebrow">{t("nav_workplace")}</p>
        <h2>{t("page_workplace_dashboard")}</h2>
        <p className="page-description">{t("workplace_page_description")}</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t("context_scope")}</CardTitle>
          <CardDescription>{t("workplace_context_description")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {loadingWorkspaces ? (
            <Skeleton className="h-10 w-full" />
          ) : (
            <select
              className="control-select"
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
            {loadingDatasets ? (
              <>
                <Skeleton className="h-9 w-28" />
                <Skeleton className="h-9 w-28" />
              </>
            ) : (
              datasets.map((dataset) => {
                const active = focusIds.includes(dataset.id);
                return (
                  <button
                    key={dataset.id}
                    type="button"
                    data-active={active}
                    className="pill-toggle"
                    onClick={() =>
                      setFocusIds((prev) =>
                        prev.includes(dataset.id) ? prev.filter((id) => id !== dataset.id) : [...prev, dataset.id]
                      )
                    }
                  >
                    {dataset.tableName}
                  </button>
                );
              })
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("compose_prompt_title")}</CardTitle>
          <CardDescription>{t("ask_agent_workspace_placeholder")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <Textarea
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={t("ask_agent_workspace_placeholder")}
            rows={5}
          />
          <div className="flex items-center justify-between gap-3">
            <div className="text-sm text-[color:var(--color-text-secondary)]">
              {analysis?.executionTime ? `${analysis.executionTime} ms` : t("ready")}
            </div>
            <Button onClick={() => void runAnalysis()} disabled={analyzing || !groupId || !query.trim()}>
              {analyzing ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
              {t("analyze")}
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t("latest_result_snapshot")}</CardTitle>
          <CardDescription>{analysis?.summary || t("run_analysis_time_series")}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {analysis?.generatedCodeOrSql ? (
            <pre className="overflow-x-auto rounded-[12px] bg-[color:var(--color-background-primary)] p-4 text-sm text-[color:var(--color-text-secondary)]">
              <code>{analysis.generatedCodeOrSql}</code>
            </pre>
          ) : (
            <div className="rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-4 py-3 text-sm text-[color:var(--color-text-secondary)]">
              {t("no_generated_sql_python")}
            </div>
          )}

          {rows.length ? (
            <div className="rounded-[12px] bg-[color:var(--color-background-primary)]">
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
            <div className="flex items-center gap-2 rounded-[12px] border [border-width:0.5px] border-[color:var(--color-border-tertiary)] px-4 py-3 text-sm text-[color:var(--color-text-secondary)]">
              <Network className="h-4 w-4" />
              <span>{analysis?.message || t("run_analysis_time_series")}</span>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
