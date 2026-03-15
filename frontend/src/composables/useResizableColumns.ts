/**
 * Element Plus el-table 列宽持久化。
 *
 * el-table 开启 border 后，拖动列头只改变当前列宽度，其他列完全不变——这是 el-table 的原生行为。
 * 本 composable 仅负责：
 *   1. 从 localStorage 同步恢复上次保存的列宽（初始渲染时就是正确宽度，无闪跳）。
 *   2. 监听 @header-dragend 事件，把新宽度写回 localStorage。
 *
 * 用法：
 *   const { cw, onHeaderDragend } = useResizableColumns('page-key', { name: 200, action: 120 })
 *   <el-table border @header-dragend="onHeaderDragend">
 *     <el-table-column column-key="name" :width="cw('name')" ...>
 */
import { ref } from 'vue';

export function useResizableColumns(pageKey: string, defaultWidths: Record<string, number>) {
  const storageKey = `col_widths_${pageKey}`;

  let saved: Record<string, number> = {};
  try {
    const raw = localStorage.getItem(storageKey);
    if (raw) saved = JSON.parse(raw);
  } catch { /* ignore */ }

  const widths = ref<Record<string, number>>({ ...defaultWidths, ...saved });

  /** el-table @header-dragend 事件：仅更新被拖动列，其余列不变 */
  function onHeaderDragend(newWidth: number, _: number, column: any) {
    const key = column.columnKey || column.label;
    if (!key) return;
    widths.value[key] = Math.round(newWidth);
    try {
      localStorage.setItem(storageKey, JSON.stringify(widths.value));
    } catch { /* ignore */ }
  }

  /** 获取某列当前宽度（优先 localStorage，其次默认值） */
  function cw(key: string): number {
    return widths.value[key] ?? defaultWidths[key] ?? 120;
  }

  return { cw, onHeaderDragend };
}
