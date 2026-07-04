// ② 고급 매트릭스 — 카테고리 아코디언(다이렉트/초대/콘텐츠 노출/프로필) × 관계 5열 (설계문서 §4-2).
// 베이스 행 셀은 클릭 시 허용↔차단 토글. 상세 행(초대 방유형×개설·속함×익명, 콘텐츠 익명 변형)은
// "펼치기"로 노출되며 3-상태(허용/차단/상위 따름 — 상위 따름이면 회색으로 현재 상속값 표시).
// 저장은 변경분만 PUT 하고, 빈 문자열("")로 명시값을 지우면 상위 따름으로 복원된다.
import { useMemo, useState } from "react";
import { ChevronDown, ChevronRight, Grid3x3, RotateCcw, Save } from "lucide-react";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/app/components/ui/accordion";
import { Button } from "@/app/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/app/components/ui/card";
import { cn } from "@/app/components/ui/utils";
import { updatePrivacyPolicy } from "../api/privacyApi";
import {
  RELATIONS,
  RELATION_LABELS,
  SURFACE_CATEGORIES,
  SURFACE_DETAIL_ROWS,
  SURFACE_LABELS,
  defaultPolicyValue,
  resolveSurface,
  type PolicyValue,
  type PrivacyPolicyResponse,
  type RelationKey,
} from "../types";

/** 관계별 pending 변경분: 표면키 → "allow" | "block" | ""(명시값 제거). */
type PendingChanges = Partial<Record<RelationKey, Record<string, string>>>;

export function AdvancedPolicyMatrix({
  policy,
  onPolicyChange,
  onError,
}: {
  policy: PrivacyPolicyResponse;
  onPolicyChange: (next: PrivacyPolicyResponse) => void;
  onError: (message: string) => void;
}) {
  const [pending, setPending] = useState<PendingChanges>({});
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [saving, setSaving] = useState(false);

  const pendingCount = useMemo(
    () => Object.values(pending).reduce((sum, row) => sum + Object.keys(row ?? {}).length, 0),
    [pending],
  );

  /** 저장된 오버라이드에 pending 변경분을 얹은(""=삭제) 관계별 값 맵. */
  const mergedFor = (relation: RelationKey): Record<string, string> => {
    const merged: Record<string, string> = { ...(policy.overrides[relation] ?? {}) };
    for (const [key, value] of Object.entries(pending[relation] ?? {})) {
      if (value === "") delete merged[key];
      else merged[key] = value;
    }
    return merged;
  };

  /** 해당 키에 명시값이 있으면 반환(pending 반영). 없으면 null. */
  const explicitOf = (relation: RelationKey, surface: string): PolicyValue | null => {
    const value = mergedFor(relation)[surface];
    return value === "allow" || value === "block" ? value : null;
  };

  /** 상속 포함 실효값. */
  const effectiveOf = (relation: RelationKey, surface: string): PolicyValue =>
    resolveSurface(mergedFor(relation), surface) ?? defaultPolicyValue(relation);

  /** 자기 명시값을 뺀 상속값(상위 따름일 때 회색 표기용). */
  const inheritedOf = (relation: RelationKey, surface: string): PolicyValue => {
    const merged = mergedFor(relation);
    delete merged[surface];
    return resolveSurface(merged, surface) ?? defaultPolicyValue(relation);
  };

  const stage = (relation: RelationKey, surface: string, value: string) => {
    setPending((current) => {
      const row = { ...(current[relation] ?? {}) };
      const stored = policy.overrides[relation]?.[surface];
      const storedNormalized = stored === "allow" || stored === "block" ? stored : "";
      if (value === storedNormalized) delete row[surface]; // 저장된 값으로 되돌리면 변경분에서 제외
      else row[surface] = value;
      return { ...current, [relation]: row };
    });
  };

  /** 베이스 행: 실효값 기준 허용↔차단 토글(항상 명시값 기록). */
  const toggleBase = (relation: RelationKey, surface: string) => {
    stage(relation, surface, effectiveOf(relation, surface) === "allow" ? "block" : "allow");
  };

  /** 상세 행: 상위 따름 → 허용 → 차단 → 상위 따름 3-상태 순환. */
  const cycleDetail = (relation: RelationKey, surface: string) => {
    const explicit = explicitOf(relation, surface);
    stage(relation, surface, explicit === null ? "allow" : explicit === "allow" ? "block" : "");
  };

  const isDirty = (relation: RelationKey, surface: string): boolean =>
    (pending[relation] ?? {})[surface] !== undefined;

  const toggleExpand = (surface: string) => {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(surface)) next.delete(surface);
      else next.add(surface);
      return next;
    });
  };

  const save = async () => {
    if (pendingCount === 0) return;
    setSaving(true);
    try {
      const next = await updatePrivacyPolicy({ relations: pending as Record<string, Record<string, string>> });
      onPolicyChange(next);
      setPending({});
    } catch (err) {
      onError(err instanceof Error ? err.message : "정책 저장에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const valueClass = (value: PolicyValue) =>
    value === "allow" ? "text-emerald-700 bg-emerald-50 border-emerald-200" : "text-red-700 bg-red-50 border-red-200";

  return (
    <Card className="border border-slate-200 bg-card">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle className="flex items-center gap-2 text-base">
            <Grid3x3 className="size-4 text-indigo-600" />
            고급 매트릭스
          </CardTitle>
          <div className="flex items-center gap-2">
            {pendingCount > 0 && <span className="text-xs text-amber-600">변경 {pendingCount}건</span>}
            <Button type="button" size="sm" variant="outline" onClick={() => setPending({})} disabled={pendingCount === 0 || saving}>
              <RotateCcw className="size-3.5" />
              되돌리기
            </Button>
            <Button type="button" size="sm" onClick={() => void save()} disabled={pendingCount === 0 || saving}>
              <Save className="size-3.5" />
              변경 저장
            </Button>
          </div>
        </div>
        <p className="text-xs text-slate-500">
          셀을 눌러 관계별 허용/차단을 바꿉니다. 상세 행은 값을 비워두면 상위 설정을 따릅니다(회색 표시).
          콘텐츠 노출 차단은 상대에게 알리지 않고 조용히 적용됩니다.
        </p>
      </CardHeader>
      <CardContent>
        <Accordion type="multiple" defaultValue={["direct"]}>
          {SURFACE_CATEGORIES.map((category) => (
            <AccordionItem key={category.key} value={category.key}>
              <AccordionTrigger className="text-sm font-semibold">{category.label}</AccordionTrigger>
              <AccordionContent>
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[640px] border-separate border-spacing-y-1 text-xs">
                    <thead>
                      <tr>
                        <th className="w-56 px-2 text-left font-medium text-slate-500">표면</th>
                        {RELATIONS.map((relation) => (
                          <th key={relation} className="px-1 text-center font-medium text-slate-500">
                            {RELATION_LABELS[relation]}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {category.surfaces.map((surface) => {
                        const details = SURFACE_DETAIL_ROWS[surface] ?? [];
                        const open = expanded.has(surface);
                        return (
                          <SurfaceRows
                            key={surface}
                            surface={surface}
                            details={details}
                            open={open}
                            onToggleExpand={() => toggleExpand(surface)}
                            renderBaseCell={(relation) => {
                              const value = effectiveOf(relation, surface);
                              return (
                                <button
                                  type="button"
                                  onClick={() => toggleBase(relation, surface)}
                                  className={cn(
                                    "w-full rounded-md border px-1.5 py-1 font-semibold transition-colors",
                                    valueClass(value),
                                    isDirty(relation, surface) && "ring-2 ring-amber-300",
                                  )}
                                  title={`${RELATION_LABELS[relation]} · ${SURFACE_LABELS[surface] ?? surface}: 클릭해 전환`}
                                >
                                  {value === "allow" ? "허용" : "차단"}
                                </button>
                              );
                            }}
                            renderDetailCell={(relation, detailKey) => {
                              const explicit = explicitOf(relation, detailKey);
                              const inherited = inheritedOf(relation, detailKey);
                              return (
                                <button
                                  type="button"
                                  onClick={() => cycleDetail(relation, detailKey)}
                                  className={cn(
                                    "w-full rounded-md border px-1.5 py-1 transition-colors",
                                    explicit
                                      ? cn("font-semibold", valueClass(explicit))
                                      : "border-slate-200 bg-slate-50 text-slate-400",
                                    isDirty(relation, detailKey) && "ring-2 ring-amber-300",
                                  )}
                                  title="클릭: 상위 따름 → 허용 → 차단 순환"
                                >
                                  {explicit
                                    ? explicit === "allow" ? "허용" : "차단"
                                    : `상위 따름(${inherited === "allow" ? "허용" : "차단"})`}
                                </button>
                              );
                            }}
                          />
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </CardContent>
    </Card>
  );
}

/** 베이스 표면 1행 + (펼침 시) 상세 행들. */
function SurfaceRows({
  surface,
  details,
  open,
  onToggleExpand,
  renderBaseCell,
  renderDetailCell,
}: {
  surface: string;
  details: Array<{ key: string; label: string }>;
  open: boolean;
  onToggleExpand: () => void;
  renderBaseCell: (relation: RelationKey) => React.ReactNode;
  renderDetailCell: (relation: RelationKey, detailKey: string) => React.ReactNode;
}) {
  return (
    <>
      <tr>
        <td className="px-2 py-1 text-sm font-medium text-slate-700">
          <div className="flex items-center gap-1">
            {details.length > 0 ? (
              <button
                type="button"
                onClick={onToggleExpand}
                className="inline-flex items-center gap-1 text-left hover:text-blue-600"
                aria-expanded={open}
              >
                {open ? <ChevronDown className="size-3.5" /> : <ChevronRight className="size-3.5" />}
                {SURFACE_LABELS[surface] ?? surface}
                <span className="text-[11px] font-normal text-slate-400">{open ? "접기" : "펼치기"}</span>
              </button>
            ) : (
              SURFACE_LABELS[surface] ?? surface
            )}
          </div>
        </td>
        {RELATIONS.map((relation) => (
          <td key={relation} className="px-1 text-center">
            {renderBaseCell(relation)}
          </td>
        ))}
      </tr>
      {open &&
        details.map((detail) => (
          <tr key={detail.key}>
            <td className="py-1 pl-8 pr-2 text-slate-500">{detail.label}</td>
            {RELATIONS.map((relation) => (
              <td key={relation} className="px-1 text-center">
                {renderDetailCell(relation, detail.key)}
              </td>
            ))}
          </tr>
        ))}
    </>
  );
}
