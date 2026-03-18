/**
 * 思维导图共享工具：
 * - 节点类型/颜色常量
 * - SVG 自定义标签渲染（类型标签、评论角标、属性标签、标记边框）
 * - Ctrl+左键拖拽平移（阻止中键/右键拖拽，Ctrl+左键调用 view.translateXY）
 */

export const NODE_TYPE_LABEL: Record<string, string> = {
  TITLE: '用例标题',
  PRECONDITION: '前置条件',
  STEP: '步骤',
  EXPECTED: '预期结果',
};

export const NODE_TYPE_COLOR: Record<string, string> = {
  TITLE: '#1677ff',
  PRECONDITION: '#722ed1',
  STEP: '#13c2c2',
  EXPECTED: '#52c41a',
};

export const MARK_COLOR: Record<string, string> = {
  '待完成': '#ff4d4f',
  '待确认': '#faad14',
  '待修改': '#722ed1',
  PENDING: '#ff4d4f',
  TO_CONFIRM: '#faad14',
  TO_MODIFY: '#722ed1',
};

// ── SVG 元素查找 ────────────────────────────────────────────────

export function getGroupEl(node: any): SVGGElement | null {
  if (!node?.group) return null;
  if (node.group.node instanceof SVGElement) return node.group.node as SVGGElement;
  if (node.group instanceof SVGElement) return node.group as SVGGElement;
  return null;
}

export function getNodeShapeEl(node: any): SVGElement | null {
  const direct = node.style?.rect?.node || node.shapeNode?.node || node._shapeNode?.node;
  if (direct instanceof SVGElement) return direct;
  const g = getGroupEl(node);
  if (g) {
    let el: Element | null = g;
    for (let i = 0; i < 5 && el; i++) {
      const shape = el.querySelector('.smm-node-shape') as SVGElement | null;
      if (shape) return shape;
      el = el.parentElement;
    }
  }
  return null;
}

// ── 标记边框 ────────────────────────────────────────────────────

export function applyMarkStyle(node: any) {
  const mark = node.nodeData?.data?._raw?.properties?.mark;
  const shape = getNodeShapeEl(node);
  if (!shape) return;
  const color = mark ? MARK_COLOR[mark] : null;
  if (color) {
    shape.style.setProperty('stroke', color, 'important');
    shape.style.setProperty('stroke-width', '2.5px', 'important');
  } else {
    shape.style.removeProperty('stroke');
    shape.style.removeProperty('stroke-width');
  }
}

// ── 节点标签（类型/评论角标/属性） ─────────────────────────────

function makeFO(x: number, y: number, width: number, height: number): SVGForeignObjectElement {
  const fo = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
  fo.setAttribute('class', 'mm-extra-label');
  fo.setAttribute('x', String(x));
  fo.setAttribute('y', String(y));
  fo.setAttribute('width', String(width));
  fo.setAttribute('height', String(height));
  fo.style.overflow = 'visible';
  fo.style.pointerEvents = 'none';
  return fo;
}

export function addNodeLabels(node: any, forceRefresh = false) {
  const groupEl = getGroupEl(node);
  if (!groupEl) return;

  const raw = node.nodeData?.data?._raw;
  if (!raw) return;

  const w = node.width || 120;
  const h = node.height || 30;

  const propStr = raw.properties ? JSON.stringify(raw.properties) : '';
  const fp = `${raw.nodeType}|${raw.commentCount || 0}|${propStr}|${w}|${h}`;
  if (!forceRefresh && (groupEl as any).__labelFp === fp) return;
  (groupEl as any).__labelFp = fp;

  groupEl.querySelectorAll('.mm-extra-label').forEach(el => el.remove());

  // 类型标签 → 节点框正上方
  if (raw.nodeType && NODE_TYPE_LABEL[raw.nodeType]) {
    const fo = makeFO(0, -20, Math.max(w, 150), 20);
    const div = document.createElement('div');
    div.style.cssText = `font-size:11px;font-weight:600;color:${NODE_TYPE_COLOR[raw.nodeType] || '#1677ff'};line-height:18px;white-space:nowrap;`;
    div.textContent = NODE_TYPE_LABEL[raw.nodeType];
    fo.appendChild(div);
    groupEl.appendChild(fo);
  }

  // 评论角标 → 右上角红色圆圈
  const count = raw.commentCount || 0;
  if (count > 0) {
    const fo = makeFO(w - 4, -10, 22, 22);
    const badge = document.createElement('div');
    badge.style.cssText = 'width:18px;height:18px;border-radius:50%;background:#ff4d4f;color:#fff;font-size:10px;display:flex;align-items:center;justify-content:center;font-weight:bold;';
    badge.textContent = count > 99 ? '99+' : String(count);
    fo.appendChild(badge);
    groupEl.appendChild(fo);
  }

  // 属性标签 → 节点框正下方
  const parts: string[] = [];
  if (raw.properties) {
    for (const [k, v] of Object.entries(raw.properties)) {
      if (k === 'mark' || v == null || v === '') continue;
      if (Array.isArray(v)) { if (v.length) parts.push(...v.map(String)); }
      else parts.push(String(v));
    }
  }
  if (parts.length) {
    const fo = makeFO(0, h + 2, Math.max(w, 400), 22);
    const div = document.createElement('div');
    div.style.cssText = 'display:flex;gap:4px;line-height:18px;white-space:nowrap;flex-wrap:wrap;';
    parts.forEach(p => {
      const sp = document.createElement('span');
      sp.style.cssText = 'font-size:10px;background:rgba(0,0,0,0.06);color:#595959;border-radius:2px;padding:0 5px;';
      sp.textContent = p;
      div.appendChild(sp);
    });
    fo.appendChild(div);
    groupEl.appendChild(fo);
  }

  applyMarkStyle(node);
}

export function addAllCustomLabels(mindMapInstance: any) {
  if (mindMapInstance?._isSvgEngine) return;
  if (!mindMapInstance?.renderer?.root) return;
  const t0 = performance.now();
  let count = 0;
  let skipped = 0;
  (function walk(n: any) {
    const before = (getGroupEl(n) as any)?.__labelFp;
    addNodeLabels(n);
    const after = (getGroupEl(n) as any)?.__labelFp;
    if (before === after && before !== undefined) skipped++; else count++;
    (n.children || []).forEach(walk);
  })(mindMapInstance.renderer.root);
  const elapsed = performance.now() - t0;
  if (elapsed > 20) {
    console.log(`[perf] addAllCustomLabels ${elapsed.toFixed(0)}ms | updated=${count} skipped=${skipped}`);
  }
}

// ── 右键拖拽平移 ───────────────────────────────────────────

/**
 * 在 container 的 capture 阶段拦截鼠标事件：
 * - 右键按下：接管 pan 逻辑，调用 view.translateXY()
 * - 中键：阻止冒泡
 * - 禁用右键菜单
 *
 * 返回清理函数，需在组件 onUnmounted 调用。
 */
export function setupMouseOverrides(
  container: HTMLElement,
  getMindMap: () => any,
): () => void {
  const mm = getMindMap();
  if (mm?._isSvgEngine) return () => {};

  let panActive = false;
  let lastX = 0;
  let lastY = 0;
  let didPan = false;

  const onMousedown = (e: MouseEvent) => {
    if (e.button === 1) {
      e.stopImmediatePropagation();
      return;
    }
    if (e.button === 2) {
      e.stopImmediatePropagation();
      e.preventDefault();
      panActive = true;
      didPan = false;
      lastX = e.clientX;
      lastY = e.clientY;
      container.style.cursor = 'grabbing';
    }
  };

  const onMousemove = (e: MouseEvent) => {
    if (!panActive) return;
    const dx = e.clientX - lastX;
    const dy = e.clientY - lastY;
    if (Math.abs(dx) > 1 || Math.abs(dy) > 1) didPan = true;
    getMindMap()?.view?.translateXY(dx, dy);
    lastX = e.clientX;
    lastY = e.clientY;
  };

  const onMouseup = (e: MouseEvent) => {
    if (!panActive) return;
    panActive = false;
    container.style.cursor = '';
  };

  const onContextmenu = (e: MouseEvent) => {
    e.preventDefault();
  };

  const onWheel = (e: WheelEvent) => {
    const mm = getMindMap();
    if (!mm?.view) return;
    if (e.ctrlKey || e.metaKey) {
      e.preventDefault();
      e.stopPropagation();
      if (e.deltaY < 0) mm.view.enlarge();
      else mm.view.narrow();
    } else if (e.shiftKey) {
      e.preventDefault();
      e.stopPropagation();
      const step = mm.opt?.mousewheelMoveStep || 100;
      mm.view.translateXY(e.deltaY > 0 ? -step : step, 0);
    }
  };

  container.addEventListener('mousedown', onMousedown, { capture: true });
  container.addEventListener('contextmenu', onContextmenu, { capture: true });
  container.addEventListener('wheel', onWheel, { capture: true, passive: false });
  window.addEventListener('mousemove', onMousemove);
  window.addEventListener('mouseup', onMouseup);

  return () => {
    container.removeEventListener('mousedown', onMousedown, { capture: true });
    container.removeEventListener('contextmenu', onContextmenu, { capture: true });
    container.removeEventListener('wheel', onWheel, { capture: true } as any);
    window.removeEventListener('mousemove', onMousemove);
    window.removeEventListener('mouseup', onMouseup);
  };
}
