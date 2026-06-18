import { useLayoutEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { ChevronDown, MoreHorizontal } from "lucide-react";
import type { LucideIcon } from "lucide-react";

// [공통 영역 변경 사유] 데스크톱 헤더 메뉴가 폭이 줄어드는 구간(xl~)에서 항목이 한 줄에
// 다 안 들어가 2줄로 깨지던 문제를 해결한다. 들어갈 수 있는 만큼만 인라인으로 보이고,
// 넘치는 항목은 오른쪽 '더보기' 드롭다운으로 빠진다(폭 측정 기반, ResizeObserver).
// 메뉴 데이터/스타일은 Header.tsx 그대로 두고 렌더링만 분리해 영향 범위를 최소화함.

export type NavChild = { label: string; href: string };
export type NavItem = { label: string; href: string; icon: LucideIcon; children?: NavChild[] };

const itemClass = (active: boolean) =>
  `flex items-center gap-1 px-2.5 py-2 text-sm font-medium rounded-md transition-colors whitespace-nowrap ${
    active ? "text-primary bg-accent-soft" : "text-muted-foreground hover:text-foreground hover:bg-accent"
  }`;

export function HeaderNav({ items, pathname }: { items: NavItem[]; pathname: string }) {
  const outerRef = useRef<HTMLDivElement>(null);
  const measureRef = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(items.length);
  const [openMenu, setOpenMenu] = useState<string | null>(null);
  const [moreOpen, setMoreOpen] = useState(false);

  useLayoutEffect(() => {
    const outer = outerRef.current;
    const measure = measureRef.current;
    if (!outer || !measure) return;
    const widths = Array.from(measure.children).map((el) => (el as HTMLElement).getBoundingClientRect().width);
    const GAP = 4; // gap-1
    const MORE = 92; // '더보기' 버튼 + 여유

    const compute = () => {
      const avail = outer.getBoundingClientRect().width;
      const fit = (reserve: number) => {
        let used = 0;
        let n = 0;
        for (let i = 0; i < widths.length; i++) {
          const w = widths[i] + (i > 0 ? GAP : 0);
          if (used + w + reserve <= avail) {
            used += w;
            n++;
          } else break;
        }
        return n;
      };
      let n = fit(0);
      if (n < items.length) n = fit(MORE); // 넘치면 '더보기' 자리 확보 후 재계산
      setVisible(Math.max(1, n));
    };

    compute();
    const ro = new ResizeObserver(compute);
    ro.observe(outer);
    return () => ro.disconnect();
  }, [items]);

  const isActive = (href: string) => pathname === href.split("?")[0];
  const shown = items.slice(0, visible);
  const overflow = items.slice(visible);

  return (
    <>
      {/* 폭 측정용 숨김 사본(항상 전체 항목, 레이아웃에 영향 없음) */}
      <div ref={measureRef} aria-hidden className="absolute invisible flex gap-1 pointer-events-none" style={{ left: -9999, top: 0 }}>
        {items.map((item) => (
          <span key={item.href} className={itemClass(false)}>
            {item.label}
            {item.children && <ChevronDown className="size-3.5 opacity-60" />}
          </span>
        ))}
      </div>

      {/* 실제 네비(가용 폭만큼) */}
      <nav ref={outerRef} className="hidden xl:flex items-center gap-1 min-w-0 flex-1 justify-end">
        {shown.map((item) => (
          <div
            key={item.href}
            className="relative"
            onMouseEnter={() => setOpenMenu(item.label)}
            onMouseLeave={() => setOpenMenu(null)}
          >
            <Link to={item.href} className={itemClass(isActive(item.href))}>
              {item.label}
              {item.children && <ChevronDown className="size-3.5 opacity-60" />}
            </Link>
            {item.children && openMenu === item.label && (
              <div className="absolute top-full left-0 mt-0 w-52 bg-popover rounded-xl border border-border py-2 z-50 shadow-[var(--shadow-pop)]">
                <div className="px-3 py-1.5 mb-1 border-b border-border">
                  <div className="flex items-center gap-2 text-xs font-semibold text-muted-foreground uppercase tracking-wide">
                    <item.icon className="size-3.5" />
                    {item.label}
                  </div>
                </div>
                {item.children.map((child) => (
                  <Link key={child.href} to={child.href} className="flex items-center gap-2 px-4 py-2 text-sm text-foreground hover:bg-accent transition-colors">
                    {child.label}
                  </Link>
                ))}
              </div>
            )}
          </div>
        ))}

        {overflow.length > 0 && (
          <div className="relative" onMouseEnter={() => setMoreOpen(true)} onMouseLeave={() => setMoreOpen(false)}>
            <button type="button" className={itemClass(false)} aria-label="더보기 메뉴" aria-expanded={moreOpen}>
              <MoreHorizontal className="size-4" />
              더보기
              <ChevronDown className="size-3.5 opacity-60" />
            </button>
            {moreOpen && (
              <div className="absolute top-full right-0 mt-0 w-64 max-h-[70vh] overflow-y-auto bg-popover rounded-xl border border-border py-2 z-50 shadow-[var(--shadow-pop)]">
                {overflow.map((item) => (
                  <div key={item.href} className="px-1">
                    <Link to={item.href} className="flex items-center gap-2 px-3 py-2 text-sm font-medium text-foreground rounded-md hover:bg-accent">
                      <item.icon className="size-4 text-muted-foreground" />
                      {item.label}
                    </Link>
                    {item.children && (
                      <div className="pb-1 pl-9 pr-2">
                        {item.children.map((child) => (
                          <Link key={child.href} to={child.href} className="block rounded-md px-2 py-1 text-xs text-muted-foreground hover:bg-accent hover:text-foreground">
                            {child.label}
                          </Link>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </nav>
    </>
  );
}
