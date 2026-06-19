import { useState } from "react";
import { CheckCircle2, Loader2, Plus, Trash2 } from "lucide-react";
import {
  addDashboardTodo,
  deleteDashboardTodo,
  updateDashboardTodo,
  updateDerivedDashboardTodo,
} from "../api/dashboardApi";
import type { DashboardTodo } from "../types/dashboardSummary";

interface TodoChecklistProps {
  todos: DashboardTodo[];
  onTodosChange: (todos: DashboardTodo[]) => void;
}

function todoKey(todo: DashboardTodo) {
  return todo.derivedKey ?? `user-${todo.id}`;
}

/**
 * 오늘의 할 일 체크리스트(디자인 분석 §6.4: 완료 처리 액션).
 * 파생(자동 계산) 항목은 derivedKey로, 사용자가 추가한 항목은 id로 완료 처리한다.
 * 터치 환경을 고려해 행 전체를 토글 버튼으로 사용한다(모바일 고려 §6.1).
 */
export function TodoChecklist({ todos, onTodosChange }: TodoChecklistProps) {
  const [pendingKey, setPendingKey] = useState<string | null>(null);
  const [newTask, setNewTask] = useState("");
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (key: string, request: () => Promise<DashboardTodo[]>) => {
    setPendingKey(key);
    setError(null);
    try {
      onTodosChange(await request());
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "할 일 처리에 실패했습니다.");
    } finally {
      setPendingKey(null);
    }
  };

  const handleToggle = (todo: DashboardTodo) => {
    const key = todoKey(todo);
    if (todo.source === "USER" && todo.id != null) {
      void run(key, () => updateDashboardTodo(todo.id as number, !todo.done));
    } else if (todo.derivedKey) {
      void run(key, () => updateDerivedDashboardTodo(todo, !todo.done));
    }
  };

  const handleDelete = (todo: DashboardTodo) => {
    if (todo.id == null) return;
    void run(`delete-${todo.id}`, () => deleteDashboardTodo(todo.id as number));
  };

  const handleAdd = async () => {
    const task = newTask.trim();
    if (!task || adding) return;
    setAdding(true);
    setError(null);
    try {
      onTodosChange(await addDashboardTodo(task, "오늘"));
      setNewTask("");
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "할 일 추가에 실패했습니다.");
    } finally {
      setAdding(false);
    }
  };

  return (
    <div className="space-y-2.5">
      {todos.map((todo) => {
        const key = todoKey(todo);
        const busy = pendingKey === key || pendingKey === `delete-${todo.id}`;
        return (
          <div key={key} className="flex items-start gap-1 rounded-lg bg-slate-50 hover:bg-slate-100 transition-colors">
            <button
              type="button"
              onClick={() => handleToggle(todo)}
              disabled={busy}
              aria-label={todo.done ? `${todo.task} 완료 해제` : `${todo.task} 완료 처리`}
              className="flex flex-1 items-start gap-2.5 p-3 text-left disabled:opacity-60"
            >
              <span
                className={`mt-0.5 flex size-5 shrink-0 items-center justify-center rounded-md ${
                  todo.done ? "bg-green-50 text-green-600" : "border-2 border-slate-300 bg-card"
                }`}
              >
                {busy ? (
                  <Loader2 className="size-3 animate-spin text-slate-400" />
                ) : (
                  todo.done && <CheckCircle2 className="size-3.5 text-green-600" />
                )}
              </span>
              <span className="min-w-0">
                <span className={`block text-sm leading-5 ${todo.done ? "line-through text-muted-foreground" : "text-slate-800"}`}>
                  {todo.task}
                </span>
                <span className="mt-0.5 block text-xs text-slate-400">
                  {todo.time}
                  {todo.source === "USER" && " · 직접 추가"}
                </span>
              </span>
            </button>
            {todo.source === "USER" && todo.id != null && (
              <button
                type="button"
                onClick={() => handleDelete(todo)}
                disabled={busy}
                aria-label={`${todo.task} 삭제`}
                className="p-3 text-slate-400 transition-colors hover:text-red-500 disabled:opacity-60"
              >
                <Trash2 className="size-4" />
              </button>
            )}
          </div>
        );
      })}

      {todos.length === 0 && (
        <div className="rounded-lg bg-slate-50 p-4 text-sm text-slate-500">오늘 등록된 할 일이 없습니다.</div>
      )}

      <div className="flex items-center gap-2 pt-1">
        <input
          type="text"
          value={newTask}
          onChange={(event) => setNewTask(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") void handleAdd();
          }}
          placeholder="할 일 직접 추가"
          aria-label="할 일 직접 추가"
          className="h-10 min-w-0 flex-1 rounded-lg border border-slate-200 bg-card px-3 text-sm text-slate-800 placeholder:text-slate-400 focus:border-blue-400 focus:outline-none"
        />
        <button
          type="button"
          onClick={() => void handleAdd()}
          disabled={adding || !newTask.trim()}
          aria-label="할 일 추가"
          className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-blue-600 text-white transition-colors hover:bg-blue-700 disabled:opacity-50"
        >
          {adding ? <Loader2 className="size-4 animate-spin" /> : <Plus className="size-4" />}
        </button>
      </div>

      {error && <p className="text-xs text-red-600">{error}</p>}
    </div>
  );
}
