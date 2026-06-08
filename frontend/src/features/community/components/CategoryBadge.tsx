import { CATEGORY_META } from "../types/community";

interface CategoryBadgeProps {
  label: string;
  className?: string;
}

export function CategoryBadge({ label, className }: CategoryBadgeProps) {
  const meta = CATEGORY_META[label];
  const variant = meta ? `ct-badge--${meta.variant}` : "ct-badge--cat-free";

  return (
    <span className={`ct-badge ${variant} ${className ?? ""}`}>
      {label}
    </span>
  );
}
