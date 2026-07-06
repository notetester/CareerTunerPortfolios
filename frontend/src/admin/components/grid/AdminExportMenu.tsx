import { useState, type ReactElement } from "react";
import { Download } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/app/components/ui/dropdown-menu";
import { apiBase } from "@/app/lib/apiBase";
import { getAccessToken } from "@/app/lib/tokenStore";
import { toast } from "@/features/notification/components/toast";
import {
  adminListQueryString,
  type AdminExportFormat,
  type AdminExportScope,
  type AdminListParams,
} from "./types";

/**
 * 내보내기 메뉴 — 형식(CSV/Excel) 선택 × 범위(전체/검색 결과/선택/현재 페이지) 드롭다운.
 * 인증 헤더가 필요해 <a href> 대신 fetch → Blob 다운로드로 처리한다.
 */
interface AdminExportMenuProps {
  /** 내보내기 API 경로. 예: "/admin/users/export" */
  exportPath: string;
  /** scope 별 현재 검색·정렬 파라미터 스냅샷(useAdminList.exportParams). */
  getParams: (scope: AdminExportScope) => AdminListParams;
  selectedIds?: number[];
  disabled?: boolean;
}

const SCOPES: Array<{ scope: AdminExportScope; label: string }> = [
  { scope: "page", label: "현재 페이지" },
  { scope: "search", label: "검색 결과" },
  { scope: "selected", label: "선택 항목" },
  { scope: "all", label: "전체" },
];

export function AdminExportMenu({
  exportPath,
  getParams,
  selectedIds = [],
  disabled = false,
}: AdminExportMenuProps): ReactElement {
  const [format, setFormat] = useState<AdminExportFormat>("csv");
  const [downloading, setDownloading] = useState(false);

  const download = async (scope: AdminExportScope) => {
    if (scope === "selected" && selectedIds.length === 0) {
      toast.warning("선택된 항목이 없습니다.");
      return;
    }
    setDownloading(true);
    try {
      const sp = adminListQueryString(getParams(scope));
      sp.set("scope", scope);
      sp.set("format", format);
      if (scope === "selected") {
        selectedIds.forEach((id) => sp.append("ids", String(id)));
      }
      const token = getAccessToken();
      const res = await fetch(`${apiBase()}${exportPath}?${sp.toString()}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : undefined,
      });
      if (!res.ok) {
        throw new Error(`내보내기 요청에 실패했습니다 (${res.status})`);
      }
      const blob = await res.blob();
      const disposition = res.headers.get("Content-Disposition") ?? "";
      const match = /filename="?([^";]+)"?/.exec(disposition);
      const filename = match?.[1] ?? `export.${format === "excel" ? "xlsx" : "csv"}`;
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
      toast.success("내보내기 파일을 내려받았습니다.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "내보내기에 실패했습니다.");
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="flex items-center gap-1.5">
      <select
        value={format}
        onChange={(event) => setFormat(event.target.value as AdminExportFormat)}
        className="h-9 rounded-md border border-slate-200 bg-card px-2 text-sm"
        aria-label="내보내기 형식"
        disabled={disabled || downloading}
      >
        <option value="csv">CSV</option>
        <option value="excel">Excel</option>
      </select>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button type="button" variant="outline" size="sm" className="h-9" disabled={disabled || downloading}>
            <Download className="size-4" />
            내보내기
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuLabel>내보낼 범위</DropdownMenuLabel>
          <DropdownMenuSeparator />
          {SCOPES.map(({ scope, label }) => (
            <DropdownMenuItem
              key={scope}
              disabled={scope === "selected" && selectedIds.length === 0}
              onSelect={() => void download(scope)}
            >
              {label}
              {scope === "selected" && selectedIds.length > 0 && ` (${selectedIds.length}건)`}
            </DropdownMenuItem>
          ))}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
