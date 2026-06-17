/** 페이지 번호 목록 생성 — 8페이지 초과 시 양 끝 + 현재 주변만 두고 …로 축약 */
function pageList(cur: number, total: number): (number | "…")[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const out: (number | "…")[] = [1];
  const lo = Math.max(2, cur - 1);
  const hi = Math.min(total - 1, cur + 1);
  if (lo > 2) out.push("…");
  for (let i = lo; i <= hi; i++) out.push(i);
  if (hi < total - 1) out.push("…");
  out.push(total);
  return out;
}

interface PagerProps {
  page: number;
  totalPages: number;
  onPage: (p: number) => void;
}

export function Pager({ page, totalPages, onPage }: PagerProps) {
  if (totalPages <= 1) return null;
  return (
    <nav className="cv-pager" aria-label="페이지">
      <button className="nav" onClick={() => onPage(page - 1)} disabled={page === 1}>‹ 이전</button>
      {pageList(page, totalPages).map((p, i) =>
        p === "…" ? (
          <span className="gap" key={"g" + i}>…</span>
        ) : (
          <button
            key={p}
            className={p === page ? "on" : ""}
            onClick={() => onPage(p)}
            aria-current={p === page ? "page" : undefined}
          >
            {p}
          </button>
        ),
      )}
      <button className="nav" onClick={() => onPage(page + 1)} disabled={page === totalPages}>다음 ›</button>
    </nav>
  );
}
