import { Plus, Trash2 } from "lucide-react";
import { Button } from "@/app/components/ui/button";
import { Input } from "@/app/components/ui/input";

export interface StructuredRowsEditorField<T extends Record<string, string>> {
  key: keyof T & string;
  label: string;
  placeholder: string;
}

interface StructuredRowsEditorProps<T extends Record<string, string>> {
  title: string;
  rows: T[];
  fields: readonly StructuredRowsEditorField<T>[];
  emptyRow: T;
  onChange(rows: T[]): void;
}

export function StructuredRowsEditor<T extends Record<string, string>>({
  title,
  rows,
  fields,
  emptyRow,
  onChange,
}: StructuredRowsEditorProps<T>) {
  const visibleRows = rows.length > 0 ? rows : [{ ...emptyRow }];

  const updateRow = (rowIndex: number, key: keyof T & string, value: string) => {
    onChange(
      visibleRows.map((row, index) => (
        index === rowIndex ? ({ ...row, [key]: value } as T) : row
      )),
    );
  };

  const addRow = () => {
    onChange([...visibleRows, { ...emptyRow }]);
  };

  const removeRow = (rowIndex: number) => {
    const nextRows = visibleRows.filter((_, index) => index !== rowIndex);
    onChange(nextRows.length > 0 ? nextRows : [{ ...emptyRow }]);
  };

  return (
    <div className="rounded-lg border border-slate-200 bg-card p-3">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="text-sm font-semibold text-slate-900">{title}</div>
        <Button type="button" variant="outline" size="sm" className="w-full bg-card sm:w-auto" onClick={addRow}>
          <Plus className="size-4" />
          행 추가
        </Button>
      </div>
      <div className="mt-3 space-y-3">
        {visibleRows.map((row, rowIndex) => (
          <div
            key={rowIndex}
            className="grid gap-2 rounded-md border border-slate-200 bg-slate-50 p-3 sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_2.25rem] sm:items-end"
          >
            {fields.map((field) => (
              <label key={field.key} className="min-w-0 space-y-1">
                <span className="text-xs font-semibold text-slate-500">{field.label}</span>
                <Input
                  value={row[field.key] ?? ""}
                  onChange={(event) => updateRow(rowIndex, field.key, event.target.value)}
                  placeholder={field.placeholder}
                  className="bg-card"
                />
              </label>
            ))}
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="h-9 w-full bg-card text-slate-500 hover:text-red-600 sm:w-9"
              title="행 삭제"
              aria-label={`${title} ${rowIndex + 1}행 삭제`}
              onClick={() => removeRow(rowIndex)}
            >
              <Trash2 className="size-4" />
            </Button>
          </div>
        ))}
      </div>
    </div>
  );
}
