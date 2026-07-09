import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";

interface RxMenuProps {
  /** 접근성 라벨 (트리거 aria-label) */
  label: string;
  triggerClassName: string;
  triggerContent: ReactNode;
  triggerStyle?: CSSProperties;
  align?: "left" | "right";
  width?: number;
  /** close 를 받아 항목 클릭 후 메뉴를 닫는다. */
  children: (close: () => void) => ReactNode;
}

/**
 * 상세 v3 반응/작성자 메뉴용 경량 드롭다운 — rx-* 스타일.
 * 바깥 클릭·Esc 로 닫힌다. 앱 공용 primitive 대신 rx-menu 룩을 그대로 쓰기 위해 로컬 구현.
 */
export function RxMenu({ label, triggerClassName, triggerContent, triggerStyle, align = "left", width = 200, children }: RxMenuProps) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  return (
    <span className="rx-anchor" ref={ref}>
      <button
        type="button"
        className={triggerClassName}
        style={triggerStyle}
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
      >
        {triggerContent}
      </button>
      {open && (
        <div className="rx-menu" style={{ width, [align === "right" ? "right" : "left"]: 0 }} role="menu">
          {children(() => setOpen(false))}
        </div>
      )}
    </span>
  );
}

interface RxMenuItemProps {
  icon: ReactNode;
  label: string;
  desc?: string;
  danger?: boolean;
  on?: boolean;
  onClick: () => void;
}

export function RxMenuItem({ icon, label, desc, danger, on, onClick }: RxMenuItemProps) {
  return (
    <button
      type="button"
      role="menuitem"
      className={"rx-mi" + (danger ? " danger" : "") + (on ? " on" : "")}
      onClick={onClick}
    >
      {icon}
      <span style={{ minWidth: 0 }}>
        <span className="rx-mi__t">{label}</span>
        {desc && <span className="rx-mi__d">{desc}</span>}
      </span>
    </button>
  );
}
