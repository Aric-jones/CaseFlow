/**
 * 自研 SVG 思维导图引擎 —— 兼容 simple-mind-map API
 * 直角正交连线、白色方框节点、类型标签、属性标签、标记边框、评论角标
 * 交互：右键拖拽平移、左键点选/多选/框选、左键拖拽节点移动、双击编辑
 *       滚轮上下、Shift+滚轮左右、Ctrl+滚轮缩放
 *       Delete 删除、Tab 添加子节点、Enter 添加同级
 *       Ctrl+Z 撤销、Ctrl+Y 重做
 *       滚动条显示当前视口位置并可拖拽
 */
import type { MindNodeData } from '../types';
import { NODE_TYPE_LABEL, NODE_TYPE_COLOR, MARK_COLOR } from './useMindMap';

const HGAP = 50;
const VGAP = 50;
const MAX_W = 300;

function genId(): string { return 'n_' + Date.now() + '_' + Math.random().toString(36).substring(2, 10); }
function txtW(text: string): number { let w = 0; for (let i = 0; i < text.length; i++) w += text.charCodeAt(i) > 255 ? 13 : 7; return w; }
function buildPropsStr(props: Record<string, any> | undefined): string {
  if (!props) return '';
  const parts: string[] = [];
  for (const [k, v] of Object.entries(props)) { if (k === 'mark' || v == null || v === '') continue; if (Array.isArray(v) && v.length) parts.push(v.join(',')); else if (!Array.isArray(v)) parts.push(String(v)); }
  return parts.join(' | ');
}

// ════════════════ 布局 ════════════════
interface LI { id: string; x: number; y: number; w: number; h: number; }

function nW(n: MindNodeData) { return Math.min(Math.max(txtW(n.text || '') + 28, 60), MAX_W); }
function nH(n: MindNodeData) { const w = nW(n); const iw = Math.max(w - 28, 30); const lines = Math.ceil(txtW(n.text || '') / iw) || 1; return Math.max(lines * 22 + 20, 40); }

function calcLayout(root: MindNodeData, collapsed: Set<string>): LI[] {
  const items: LI[] = [];
  function ch(n: MindNodeData) { return collapsed.has(n.id!) ? [] : (n.children || []); }
  function subH(n: MindNodeData): number {
    const c = ch(n); if (!c.length) return nH(n);
    let t = 0; for (let i = 0; i < c.length; i++) { t += subH(c[i]); if (i < c.length - 1) t += VGAP; }
    return Math.max(nH(n), t);
  }
  function lay(n: MindNodeData, x: number, ys: number, ye: number) {
    const w = nW(n), h = nH(n), cy = (ys + ye) / 2;
    items.push({ id: n.id!, x, y: cy - h / 2, w, h });
    const c = ch(n); if (!c.length) return;
    const cx = x + w + HGAP, hs = c.map(subH), tot = hs.reduce((a, b) => a + b, 0) + (c.length - 1) * VGAP;
    let curY = cy - tot / 2;
    for (let i = 0; i < c.length; i++) { lay(c[i], cx, curY, curY + hs[i]); curY += hs[i] + VGAP; }
  }
  lay(root, 0, 0, subH(root)); return items;
}

// ════════════════ SVG 绘制 ════════════════
function mkPath(d: string, vp: SVGElement) {
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', d); p.setAttribute('fill', 'none'); p.setAttribute('stroke', '#a1aab4'); p.setAttribute('stroke-width', '1.5');
  vp.appendChild(p);
}
function drawEdges(nd: MindNodeData, lm: Map<string, LI>, vp: SVGElement, collapsed: Set<string>) {
  const pl = lm.get(nd.id!); if (!pl) return;
  const kids = collapsed.has(nd.id!) ? [] : (nd.children || []).filter(c => lm.has(c.id!));
  if (!kids.length) return;
  const midX = pl.x + pl.w + HGAP / 2, py = pl.y + pl.h / 2;
  mkPath(`M${pl.x + pl.w},${py} H${midX}`, vp);
  const cYs = kids.map(c => { const cl = lm.get(c.id!)!; return cl.y + cl.h / 2; });
  const allYs = [py, ...cYs]; const minY = Math.min(...allYs), maxY = Math.max(...allYs);
  if (minY !== maxY) mkPath(`M${midX},${minY} V${maxY}`, vp);
  for (let i = 0; i < kids.length; i++) { const cl = lm.get(kids[i].id!)!; mkPath(`M${midX},${cYs[i]} H${cl.x}`, vp); }
  for (const c of kids) drawEdges(c, lm, vp, collapsed);
}

function drawNode(
  it: LI, data: MindNodeData, vp: SVGElement, sel: boolean, hasCh: boolean, isColl: boolean,
  onClick: (id: string, e: MouseEvent) => void,
  onDblClick: (id: string, e: MouseEvent) => void,
  onToggle: (id: string) => void,
  onMouseDown: (id: string, e: MouseEvent) => void,
): SVGGElement {
  const mark = data.properties?.mark; const mc = mark ? (MARK_COLOR[mark] || null) : null;
  const isR = !!data.isRoot;
  const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
  g.setAttribute('transform', `translate(${it.x},${it.y})`); g.style.cursor = 'pointer';
  g.setAttribute('data-node-id', it.id);

  const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
  rect.setAttribute('width', String(it.w)); rect.setAttribute('height', String(it.h)); rect.setAttribute('rx', '4');
  rect.setAttribute('fill', isR ? '#1677ff' : '#fff');
  rect.setAttribute('stroke', sel ? '#1677ff' : (mc || '#d9d9d9'));
  rect.setAttribute('stroke-width', sel ? '2' : (mc ? '2.5' : '1'));
  if (sel) rect.setAttribute('filter', 'drop-shadow(0 0 6px rgba(22,119,255,0.3))');
  g.appendChild(rect);

  const fo = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
  fo.setAttribute('x', '0'); fo.setAttribute('y', '0'); fo.setAttribute('width', String(it.w)); fo.setAttribute('height', String(it.h));
  const td = document.createElement('div');
  td.style.cssText = `font-size:13px;color:${isR ? '#fff' : '#333'};display:flex;align-items:center;height:100%;word-break:break-all;line-height:1.6;padding:10px 14px;box-sizing:border-box;user-select:none;-webkit-user-select:none;`;
  td.textContent = data.text || ''; fo.appendChild(td); g.appendChild(fo);

  if (data.nodeType && NODE_TYPE_LABEL[data.nodeType]) {
    const tfo = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
    tfo.setAttribute('x', '0'); tfo.setAttribute('y', '-20'); tfo.setAttribute('width', String(Math.max(it.w, 150))); tfo.setAttribute('height', '20');
    tfo.style.overflow = 'visible'; tfo.style.pointerEvents = 'none';
    const div = document.createElement('div');
    div.style.cssText = `font-size:11px;font-weight:600;color:${NODE_TYPE_COLOR[data.nodeType] || '#1677ff'};line-height:18px;white-space:nowrap;user-select:none;-webkit-user-select:none;`;
    div.textContent = NODE_TYPE_LABEL[data.nodeType]; tfo.appendChild(div); g.appendChild(tfo);
  }
  const ps = buildPropsStr(data.properties);
  if (ps) {
    const pfo = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
    pfo.setAttribute('x', '0'); pfo.setAttribute('y', String(it.h + 2)); pfo.setAttribute('width', String(Math.max(it.w, 400))); pfo.setAttribute('height', '22');
    pfo.style.overflow = 'visible'; pfo.style.pointerEvents = 'none';
    const div = document.createElement('div'); div.style.cssText = 'display:flex;gap:4px;line-height:18px;white-space:nowrap;flex-wrap:wrap;user-select:none;-webkit-user-select:none;';
    ps.split(' | ').forEach(p => { const sp = document.createElement('span'); sp.style.cssText = 'font-size:10px;background:rgba(0,0,0,0.06);color:#595959;border-radius:2px;padding:0 5px;'; sp.textContent = p; div.appendChild(sp); });
    pfo.appendChild(div); g.appendChild(pfo);
  }
  const cc = data.commentCount || 0;
  if (cc > 0) {
    const badge = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    badge.setAttribute('cx', String(it.w - 2)); badge.setAttribute('cy', '-4'); badge.setAttribute('r', '9'); badge.setAttribute('fill', '#ff4d4f'); g.appendChild(badge);
    const bt = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    bt.setAttribute('x', String(it.w - 2)); bt.setAttribute('y', '-1'); bt.setAttribute('text-anchor', 'middle'); bt.setAttribute('font-size', '10'); bt.setAttribute('font-weight', 'bold'); bt.setAttribute('fill', '#fff');
    bt.textContent = cc > 99 ? '99+' : String(cc); g.appendChild(bt);
  }
  if (hasCh) {
    const cx = it.w + 8, cy = it.h / 2;
    const ig = document.createElementNS('http://www.w3.org/2000/svg', 'g'); ig.style.cursor = 'pointer';
    const ci = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    ci.setAttribute('cx', String(cx)); ci.setAttribute('cy', String(cy)); ci.setAttribute('r', '7'); ci.setAttribute('fill', '#f5f5f5'); ci.setAttribute('stroke', '#d9d9d9'); ci.setAttribute('stroke-width', '1'); ig.appendChild(ci);
    const tx = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    tx.setAttribute('x', String(cx)); tx.setAttribute('y', String(cy + 1)); tx.setAttribute('text-anchor', 'middle'); tx.setAttribute('dominant-baseline', 'central');
    tx.setAttribute('font-size', '10'); tx.setAttribute('fill', '#999'); tx.textContent = isColl ? '+' : '−'; ig.appendChild(tx);
    ig.addEventListener('click', (e) => { e.stopPropagation(); onToggle(it.id); }); g.appendChild(ig);
  }

  g.addEventListener('mouseenter', () => { if (!rect.getAttribute('filter')) { rect.setAttribute('stroke', '#91caff'); rect.setAttribute('stroke-width', '1.5'); } });
  g.addEventListener('mouseleave', () => { if (!rect.getAttribute('filter')) { rect.setAttribute('stroke', mc || '#d9d9d9'); rect.setAttribute('stroke-width', mc ? '2.5' : '1'); } });
  g.addEventListener('mousedown', (e) => { if (e.button === 0) { e.stopPropagation(); onMouseDown(it.id, e); } });
  g.addEventListener('click', (e) => { e.stopPropagation(); onClick(it.id, e); });
  g.addEventListener('dblclick', (e) => { e.stopPropagation(); onDblClick(it.id, e); });
  vp.appendChild(g); return g;
}

// ════════════════ SvgMindNode（兼容 simple-mind-map 节点接口） ════════════════
export class SvgMindNode {
  _data: MindNodeData;
  _engine: SvgMindMapEngine;
  _parent: SvgMindNode | null;
  _children: SvgMindNode[] = [];
  _svgGroup: SVGGElement | null = null;
  _layout: LI | null = null;

  constructor(data: MindNodeData, parent: SvgMindNode | null, engine: SvgMindMapEngine) {
    this._data = data; this._parent = parent; this._engine = engine;
  }
  get nodeData() {
    return {
      data: { text: this._data.text, uid: this._data.id, _raw: this._data },
      children: (this._data.children || []).map(c => ({ data: { text: c.text, uid: c.id, _raw: c }, children: (c.children || []).map(cc => ({ data: { text: cc.text, uid: cc.id, _raw: cc } })) })),
    };
  }
  get children() { return this._children; }
  get parent() { return this._parent; }
  get width() { return this._layout?.w || 0; }
  get height() { return this._layout?.h || 0; }
  get group() { return this._svgGroup; }
  get data() { return { text: this._data.text, uid: this._data.id, _raw: this._data }; }
  active() { this._engine._selectNode(this._data.id!); }
}

// ════════════════ SvgMindMapEngine（兼容 simple-mind-map 主类接口） ════════════════
export class SvgMindMapEngine {
  _isSvgEngine = true;
  _el: HTMLElement;
  _readonly: boolean;
  _opts: any;
  _rawTree: MindNodeData[] = [];
  _nodeMap = new Map<string, MindNodeData>();
  _parentMap = new Map<string, string>();
  _root: SvgMindNode | null = null;
  _rnMap = new Map<string, SvgMindNode>();
  _selectedNodes: SvgMindNode[] = [];
  _collapsed = new Set<string>();
  _layoutMap = new Map<string, LI>();
  _svg: SVGSVGElement | null = null;
  _vp: SVGGElement | null = null;
  _pan = { active: false, sx: 0, sy: 0, dx: 0, dy: 0, scale: 1, didDrag: false };
  _fns: (() => void)[] = [];
  _handlers = new Map<string, Function[]>();
  _editEl: HTMLTextAreaElement | null = null;
  _editId: string | null = null;
  _contentBounds = { x0: 0, y0: 0, x1: 0, y1: 0 };
  _suppressClick = false;

  // 撤销/重做
  _history: string[] = [];
  _historyIdx = -1;

  // 节点拖拽状态
  _dragState: {
    nodeId: string; startX: number; startY: number;
    active: boolean; ghost: SVGGElement | null; dropTarget: string | null;
    offsetX: number; offsetY: number;
  } | null = null;

  // 框选状态
  _boxState: {
    startX: number; startY: number; active: boolean; rect: SVGRectElement | null;
  } | null = null;

  // 滚动条拖拽状态
  _scrollDrag: {
    direction: 'horizontal' | 'vertical'; startMouse: number; startPan: number;
  } | null = null;

  static usePlugin(_: any) {}

  constructor(opts: any) {
    this._el = opts.el;
    this._readonly = opts.readonly || false;
    this._opts = opts;
    if (opts.data) this._setFromSMM(opts.data);
    this._history = [JSON.stringify(this._rawTree)];
    this._historyIdx = 0;
    requestAnimationFrame(() => this._render(true));
  }

  // ── 事件 ──
  on(ev: string, fn: Function) { if (!this._handlers.has(ev)) this._handlers.set(ev, []); this._handlers.get(ev)!.push(fn); }
  off(ev: string, fn: Function) { const h = this._handlers.get(ev); if (h) { const i = h.indexOf(fn); if (i >= 0) h.splice(i, 1); } }
  _emit(ev: string, ...a: any[]) { for (const fn of (this._handlers.get(ev) || [])) { try { fn(...a); } catch (e) { console.error(`[svg-mm] ${ev}`, e); } } }

  // ── 撤销/重做 ──
  _saveHistory() {
    const snap = JSON.stringify(this._rawTree);
    if (this._historyIdx < this._history.length - 1) {
      this._history = this._history.slice(0, this._historyIdx + 1);
    }
    this._history.push(snap);
    if (this._history.length > 50) { this._history.shift(); }
    this._historyIdx = this._history.length - 1;
  }
  _undo() {
    if (this._historyIdx <= 0) return;
    this._historyIdx--;
    this._rawTree = JSON.parse(this._history[this._historyIdx]);
    this._selectedNodes = [];
    this._buildMaps(); this._buildRN(); this.refresh();
    this._emit('node_active', null, []);
  }
  _redo() {
    if (this._historyIdx >= this._history.length - 1) return;
    this._historyIdx++;
    this._rawTree = JSON.parse(this._history[this._historyIdx]);
    this._selectedNodes = [];
    this._buildMaps(); this._buildRN(); this.refresh();
    this._emit('node_active', null, []);
  }

  // ── 数据 ──
  _setFromSMM(d: any) {
    this._rawTree = d ? [this._toMind(d)] : [];
    this._buildMaps(); this._buildRN();
  }
  _toMind(n: any): MindNodeData {
    const r = n.data?._raw || {}; const id = r.id || n.data?.uid || genId();
    return { id, text: n.data?.text || r.text || '', nodeType: r.nodeType || null, sortOrder: r.sortOrder || 0, isRoot: r.isRoot, properties: r.properties, commentCount: r.commentCount || 0, children: (n.children || []).map((c: any) => this._toMind(c)) };
  }
  _toSMM(n: MindNodeData): any {
    return { data: { text: n.text, uid: n.id, _raw: n }, children: (n.children || []).map(c => this._toSMM(c)) };
  }
  _buildMaps() {
    this._nodeMap.clear(); this._parentMap.clear();
    const walk = (ns: MindNodeData[], pid?: string) => { for (const n of ns) { if (n.id) { this._nodeMap.set(n.id, n); if (pid) this._parentMap.set(n.id, pid); } walk(n.children || [], n.id); } };
    walk(this._rawTree);
  }
  _buildRN() {
    this._rnMap.clear();
    this._root = this._rawTree.length ? this._mkRN(this._rawTree[0], null) : null;
  }
  _mkRN(d: MindNodeData, p: SvgMindNode | null): SvgMindNode {
    const n = new SvgMindNode(d, p, this); this._rnMap.set(d.id!, n);
    const vis = this._collapsed.has(d.id!) ? [] : (d.children || []);
    n._children = vis.map(c => this._mkRN(c, n)); return n;
  }
  setData(d: any) {
    this._selectedNodes = []; this._setFromSMM(d);
    this._history = [JSON.stringify(this._rawTree)]; this._historyIdx = 0;
    this._render(false); this._emit('node_tree_render_end');
  }
  getData() { return this._rawTree.length ? this._toSMM(this._rawTree[0]) : { data: { text: '' }, children: [] }; }

  // ── 渲染 ──
  _render(fit: boolean) {
    this._cleanDom();
    if (!this._rawTree.length || !this._el) return;
    this._buildRN();
    const lis = calcLayout(this._rawTree[0], this._collapsed);
    this._layoutMap = new Map(lis.map(i => [i.id, i]));
    for (const [, rn] of this._rnMap) rn._layout = this._layoutMap.get(rn._data.id!) || null;
    this._updateContentBounds(lis);

    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('width', '100%'); svg.setAttribute('height', '100%');
    svg.style.background = '#fafafa'; svg.setAttribute('tabindex', '0'); svg.style.outline = 'none'; svg.style.userSelect = 'none'; (svg.style as any).webkitUserSelect = 'none';
    this._el.appendChild(svg); this._svg = svg;
    const vp = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    svg.appendChild(vp); this._vp = vp;

    if (fit) {
      const sr = this._el.getBoundingClientRect();
      const rootLi = lis[0];
      if (rootLi) {
        const leftPad = this._opts?.initRootNodePosition?.[0] ?? 10;
        this._pan = { active: false, sx: 0, sy: 0, dx: leftPad - rootLi.x, dy: sr.height / 2 - (rootLi.y + rootLi.h / 2), scale: 1, didDrag: false };
      }
    }
    this._applyTf();
    this._drawContent(vp, lis);
    this._setupInteractions(svg);
    this._emitScrollbar();
    if (fit) requestAnimationFrame(() => { if (this._svg === svg) svg.focus(); });
    this._emit('node_tree_render_end');
  }

  refresh() {
    const saved = { ...this._pan };
    this._cleanDom(); if (!this._rawTree.length || !this._el) return;
    this._buildMaps(); this._buildRN();
    const lis = calcLayout(this._rawTree[0], this._collapsed);
    this._layoutMap = new Map(lis.map(i => [i.id, i]));
    for (const [, rn] of this._rnMap) rn._layout = this._layoutMap.get(rn._data.id!) || null;
    this._updateContentBounds(lis);

    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('width', '100%'); svg.setAttribute('height', '100%');
    svg.style.background = '#fafafa'; svg.setAttribute('tabindex', '0'); svg.style.outline = 'none'; svg.style.userSelect = 'none'; (svg.style as any).webkitUserSelect = 'none';
    this._el.appendChild(svg); this._svg = svg;
    const vp = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    svg.appendChild(vp); this._vp = vp;
    this._pan = { ...saved, active: false, didDrag: false };
    this._applyTf();
    this._drawContent(vp, lis);
    this._setupInteractions(svg);
    this._emitScrollbar();
    this._emit('node_tree_render_end');
    this._emit('data_change');
  }

  _drawContent(vp: SVGGElement, lis: LI[]) {
    drawEdges(this._rawTree[0], this._layoutMap, vp, this._collapsed);
    for (const it of lis) {
      const d = this._nodeMap.get(it.id); if (!d) continue;
      const sel = this._selectedNodes.some(n => n._data.id === it.id);
      const hasCh = (d.children || []).length > 0;
      const isColl = this._collapsed.has(it.id);
      const sg = drawNode(it, d, vp, sel, hasCh, isColl,
        (id, e) => this._handleNodeClick(id, e),
        (id, e) => { if (!this._readonly) this._startEdit(id, e.clientX, e.clientY); },
        (id) => this._toggle(id),
        (id, e) => this._handleNodeMouseDown(id, e),
      );
      const rn = this._rnMap.get(it.id); if (rn) rn._svgGroup = sg;
    }
  }

  _applyTf() { if (this._vp) this._vp.setAttribute('transform', `translate(${this._pan.dx},${this._pan.dy}) scale(${this._pan.scale})`); }

  _updateContentBounds(lis: LI[]) {
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (const i of lis) { x0 = Math.min(x0, i.x - 25); y0 = Math.min(y0, i.y - 40); x1 = Math.max(x1, i.x + i.w + 25); y1 = Math.max(y1, i.y + i.h + 30); }
    this._contentBounds = { x0, y0, x1, y1 };
  }

  // ── 仅更新选中样式（不重建 DOM，O(changed) 级别） ──
  _updateSelectionVisual(prevIds: Set<string>, newIds: Set<string>) {
    if (!this._svg) return;
    for (const id of prevIds) {
      if (newIds.has(id)) continue;
      const g = this._svg.querySelector(`g[data-node-id="${id}"]`);
      if (!g) continue;
      const rect = g.children[0] as SVGRectElement;
      if (!rect || rect.tagName !== 'rect') continue;
      const d = this._nodeMap.get(id);
      const mark = d?.properties?.mark;
      const mc = mark ? (MARK_COLOR[mark] || null) : null;
      rect.setAttribute('stroke', mc || '#d9d9d9');
      rect.setAttribute('stroke-width', mc ? '2.5' : '1');
      rect.removeAttribute('filter');
    }
    for (const id of newIds) {
      if (prevIds.has(id)) continue;
      const g = this._svg.querySelector(`g[data-node-id="${id}"]`);
      if (!g) continue;
      const rect = g.children[0] as SVGRectElement;
      if (!rect || rect.tagName !== 'rect') continue;
      rect.setAttribute('stroke', '#1677ff');
      rect.setAttribute('stroke-width', '2');
      rect.setAttribute('filter', 'drop-shadow(0 0 6px rgba(22,119,255,0.3))');
    }
  }

  // ── 滚动条计算 ──
  _emitScrollbar() {
    const sr = this._el.getBoundingClientRect();
    if (!sr.width || !sr.height) return;
    const { x0, y0, x1, y1 } = this._contentBounds;
    const cW = x1 - x0, cH = y1 - y0;
    if (cW <= 0 || cH <= 0) return;
    const s = this._pan.scale;
    const vx = -this._pan.dx / s, vy = -this._pan.dy / s;
    const vw = sr.width / s, vh = sr.height / s;
    this._emit('scrollbar_change', {
      horizontal: {
        left: Math.max(0, Math.min(100, ((vx - x0) / cW) * 100)),
        width: Math.max(5, Math.min(100, (vw / cW) * 100)),
      },
      vertical: {
        top: Math.max(0, Math.min(100, ((vy - y0) / cH) * 100)),
        height: Math.max(5, Math.min(100, (vh / cH) * 100)),
      },
    });
  }

  // ── 交互 ──
  _setupInteractions(svg: SVGSVGElement) {
    const svgDown = (e: MouseEvent) => {
      if (e.button === 2) {
        e.preventDefault();
        this._pan.active = true; this._pan.didDrag = false;
        this._pan.sx = e.clientX - this._pan.dx; this._pan.sy = e.clientY - this._pan.dy;
        svg.style.cursor = 'grabbing';
        return;
      }
      if (e.button === 0) {
        const tgt = e.target as Element;
        if (tgt === svg || tgt.tagName === 'svg') {
          this._boxState = { startX: e.clientX, startY: e.clientY, active: false, rect: null };
        }
      }
    };

    const onMove = (e: MouseEvent) => {
      if (this._pan.active) {
        e.preventDefault();
        this._pan.didDrag = true;
        this._pan.dx = e.clientX - this._pan.sx; this._pan.dy = e.clientY - this._pan.sy;
        this._applyTf(); this._emitScrollbar();
        return;
      }
      if (this._scrollDrag) {
        const sr = this._el.getBoundingClientRect();
        const { x0, y0, x1, y1 } = this._contentBounds;
        const cW = x1 - x0, cH = y1 - y0;
        const s = this._pan.scale;
        if (this._scrollDrag.direction === 'horizontal') {
          this._pan.dx = this._scrollDrag.startPan - ((e.clientX - this._scrollDrag.startMouse) / sr.width) * cW * s;
        } else {
          this._pan.dy = this._scrollDrag.startPan - ((e.clientY - this._scrollDrag.startMouse) / sr.height) * cH * s;
        }
        this._applyTf(); this._emitScrollbar();
        return;
      }
      if (this._dragState && !this._readonly) {
        e.preventDefault();
        const dx = e.clientX - this._dragState.startX, dy = e.clientY - this._dragState.startY;
        if (!this._dragState.active && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
          this._dragState.active = true;
          this._startNodeDrag();
        }
        if (this._dragState.active) this._updateNodeDrag(e.clientX, e.clientY);
        return;
      }
      if (this._boxState) {
        e.preventDefault();
        const dx = e.clientX - this._boxState.startX, dy = e.clientY - this._boxState.startY;
        if (!this._boxState.active && (Math.abs(dx) > 5 || Math.abs(dy) > 5)) {
          this._boxState.active = true;
          const r = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
          r.setAttribute('fill', 'rgba(22,119,255,0.08)'); r.setAttribute('stroke', '#1677ff');
          r.setAttribute('stroke-width', '1'); r.setAttribute('stroke-dasharray', '4');
          svg.appendChild(r); this._boxState.rect = r;
        }
        if (this._boxState.active && this._boxState.rect) {
          const sr = this._el.getBoundingClientRect();
          const bx = Math.min(this._boxState.startX, e.clientX) - sr.left;
          const by = Math.min(this._boxState.startY, e.clientY) - sr.top;
          this._boxState.rect.setAttribute('x', String(bx));
          this._boxState.rect.setAttribute('y', String(by));
          this._boxState.rect.setAttribute('width', String(Math.abs(dx)));
          this._boxState.rect.setAttribute('height', String(Math.abs(dy)));
        }
      }
    };

    const onUp = (e: MouseEvent) => {
      if (this._pan.active) { this._pan.active = false; svg.style.cursor = ''; return; }
      if (this._scrollDrag) { this._scrollDrag = null; return; }
      if (this._dragState) {
        if (this._dragState.active) { this._finishNodeDrag(); this._suppressClick = true; setTimeout(() => { this._suppressClick = false; }, 0); }
        this._dragState = null; return;
      }
      if (this._boxState) {
        if (this._boxState.active) {
          this._finishBoxSelect(e);
          this._suppressClick = true; setTimeout(() => { this._suppressClick = false; }, 0);
        } else {
          this._deselect();
        }
        if (this._boxState.rect) this._boxState.rect.remove();
        this._boxState = null;
      }
    };

    const onContextMenu = (e: MouseEvent) => { e.preventDefault(); };

    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      if (e.ctrlKey || e.metaKey) {
        this._pan.scale *= e.deltaY > 0 ? 0.95 : 1.05;
        this._pan.scale = Math.max(0.1, Math.min(5, this._pan.scale));
        this._applyTf(); this._emit('scale', this._pan.scale);
      } else if (e.shiftKey) {
        this._pan.dx -= e.deltaY; this._applyTf();
      } else {
        this._pan.dy -= e.deltaY; this._applyTf();
      }
      this._emitScrollbar();
    };

    // 键盘快捷键注册在 window 上，确保不管焦点在哪都能响应
    const onKeyDown = (e: KeyboardEvent) => {
      if (this._editId) return;
      if (!this._svg || !document.body.contains(this._svg)) return;
      const t = e.target as HTMLElement;
      if (t instanceof HTMLInputElement || t instanceof HTMLTextAreaElement || t.isContentEditable) return;

      if ((e.ctrlKey || e.metaKey) && e.key === 'z' && !this._readonly) { e.preventDefault(); this._undo(); return; }
      if ((e.ctrlKey || e.metaKey) && e.key === 'y' && !this._readonly) { e.preventDefault(); this._redo(); return; }

      if (!this._readonly && this._selectedNodes.length) {
        if (e.key === 'Delete' || e.key === 'Backspace') { e.preventDefault(); this.execCommand('REMOVE_NODE'); return; }
        if (e.key === 'Tab') { e.preventDefault(); this.execCommand('INSERT_CHILD_NODE'); return; }
        if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); this.execCommand('INSERT_NODE'); return; }
      }
    };

    svg.addEventListener('mousedown', svgDown);
    svg.addEventListener('contextmenu', onContextMenu);
    svg.addEventListener('wheel', onWheel, { passive: false });
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    window.addEventListener('keydown', onKeyDown, true);
    this._fns.push(() => {
      svg.removeEventListener('mousedown', svgDown);
      svg.removeEventListener('contextmenu', onContextMenu);
      svg.removeEventListener('wheel', onWheel);
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      window.removeEventListener('keydown', onKeyDown, true);
    });
  }

  // ── 节点点击（Ctrl 多选）—— 仅更新样式，不重建 DOM ──
  _handleNodeClick(id: string, e: MouseEvent) {
    if (this._suppressClick) { this._suppressClick = false; return; }
    this._svg?.focus();
    const rn = this._rnMap.get(id); if (!rn) return;
    const prevIds = new Set(this._selectedNodes.map(n => n._data.id!));
    if (e.ctrlKey || e.metaKey) {
      const idx = this._selectedNodes.findIndex(n => n._data.id === id);
      if (idx >= 0) this._selectedNodes.splice(idx, 1); else this._selectedNodes.push(rn);
    } else {
      this._selectedNodes = [rn];
    }
    const newIds = new Set(this._selectedNodes.map(n => n._data.id!));
    this._updateSelectionVisual(prevIds, newIds);
    const first = this._selectedNodes.length === 1 ? this._selectedNodes[0] : (this._selectedNodes.length ? rn : null);
    this._emit('node_active', first, [...this._selectedNodes]);
    this._emit('node_click', rn);
  }

  // ── 节点 mousedown（准备拖拽） ──
  _handleNodeMouseDown(id: string, e: MouseEvent) {
    if (this._readonly) return;
    this._dragState = { nodeId: id, startX: e.clientX, startY: e.clientY, active: false, ghost: null, dropTarget: null, offsetX: 0, offsetY: 0 };
  }

  // ── 节点拖拽 ──
  _startNodeDrag() {
    if (!this._dragState || !this._vp) return;
    const li = this._layoutMap.get(this._dragState.nodeId);
    if (!li) return;
    const sr = this._el.getBoundingClientRect();
    const sx = (this._dragState.startX - sr.left - this._pan.dx) / this._pan.scale;
    const sy = (this._dragState.startY - sr.top - this._pan.dy) / this._pan.scale;
    this._dragState.offsetX = sx - li.x;
    this._dragState.offsetY = sy - li.y;

    // 将原始节点变半透明
    const origG = this._svg?.querySelector(`g[data-node-id="${this._dragState.nodeId}"]`) as SVGGElement | null;
    if (origG) origG.style.opacity = '0.3';

    const ghost = document.createElementNS('http://www.w3.org/2000/svg', 'g');
    ghost.style.pointerEvents = 'none';
    const r = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    r.setAttribute('width', String(li.w)); r.setAttribute('height', String(li.h)); r.setAttribute('rx', '4');
    r.setAttribute('fill', '#e6f4ff'); r.setAttribute('fill-opacity', '0.85');
    r.setAttribute('stroke', '#1677ff'); r.setAttribute('stroke-width', '2');
    ghost.appendChild(r);
    const ft = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
    ft.setAttribute('x', '0'); ft.setAttribute('y', '0'); ft.setAttribute('width', String(li.w)); ft.setAttribute('height', String(li.h));
    const td = document.createElement('div');
    td.style.cssText = 'font-size:13px;color:#1677ff;display:flex;align-items:center;height:100%;word-break:break-all;line-height:1.6;padding:10px 14px;box-sizing:border-box;';
    td.textContent = (this._nodeMap.get(this._dragState.nodeId)?.text || '').slice(0, 50);
    ft.appendChild(td); ghost.appendChild(ft);
    this._vp.appendChild(ghost);
    this._dragState.ghost = ghost;
  }

  _updateNodeDrag(cx: number, cy: number) {
    if (!this._dragState?.ghost || !this._svg) return;
    const sr = this._el.getBoundingClientRect();
    const x = (cx - sr.left - this._pan.dx) / this._pan.scale - this._dragState.offsetX;
    const y = (cy - sr.top - this._pan.dy) / this._pan.scale - this._dragState.offsetY;
    this._dragState.ghost.setAttribute('transform', `translate(${x},${y})`);

    const descendants = new Set<string>();
    const walkDesc = (nid: string) => { descendants.add(nid); const d = this._nodeMap.get(nid); (d?.children || []).forEach(c => { if (c.id) walkDesc(c.id); }); };
    walkDesc(this._dragState.nodeId);

    let closestId: string | null = null, closestDist = Infinity;
    for (const [id, li] of this._layoutMap) {
      if (descendants.has(id)) continue;
      const dist = Math.sqrt((x - li.x - li.w / 2) ** 2 + (y - li.y - li.h / 2) ** 2);
      if (dist < closestDist && dist < 80) { closestDist = dist; closestId = id; }
    }

    if (this._dragState.dropTarget && this._dragState.dropTarget !== closestId) {
      const prev = this._svg.querySelector(`g[data-node-id="${this._dragState.dropTarget}"] > rect`);
      if (prev) { prev.setAttribute('stroke', '#d9d9d9'); prev.setAttribute('stroke-width', '1'); }
    }
    if (closestId) {
      const tg = this._svg.querySelector(`g[data-node-id="${closestId}"] > rect`);
      if (tg) { tg.setAttribute('stroke', '#52c41a'); tg.setAttribute('stroke-width', '3'); }
    }
    this._dragState.dropTarget = closestId;
  }

  _finishNodeDrag() {
    if (!this._dragState) return;
    const { nodeId, dropTarget, ghost } = this._dragState;
    const origG = this._svg?.querySelector(`g[data-node-id="${nodeId}"]`) as SVGGElement | null;
    if (origG) origG.style.opacity = '';
    if (ghost) ghost.remove();
    if (dropTarget && dropTarget !== nodeId) {
      this._saveHistory();
      const pid = this._parentMap.get(nodeId);
      if (pid) { const p = this._nodeMap.get(pid); if (p?.children) p.children = p.children.filter(c => c.id !== nodeId); }
      const tgt = this._nodeMap.get(dropTarget), moving = this._nodeMap.get(nodeId);
      if (tgt && moving) {
        if (!tgt.children) tgt.children = [];
        tgt.children.push(moving);
        this._collapsed.delete(dropTarget);
        this._buildMaps(); this.refresh();
      }
    }
    this._dragState = null;
  }

  // ── 框选 ──
  _finishBoxSelect(e: MouseEvent) {
    if (!this._boxState) return;
    const sr = this._el.getBoundingClientRect(); const s = this._pan.scale;
    const bx1 = (Math.min(this._boxState.startX, e.clientX) - sr.left - this._pan.dx) / s;
    const by1 = (Math.min(this._boxState.startY, e.clientY) - sr.top - this._pan.dy) / s;
    const bx2 = (Math.max(this._boxState.startX, e.clientX) - sr.left - this._pan.dx) / s;
    const by2 = (Math.max(this._boxState.startY, e.clientY) - sr.top - this._pan.dy) / s;
    const prevIds = new Set(this._selectedNodes.map(n => n._data.id!));
    const selected: SvgMindNode[] = [];
    for (const [id, li] of this._layoutMap) {
      if (li.x + li.w >= bx1 && li.x <= bx2 && li.y + li.h >= by1 && li.y <= by2) {
        const rn = this._rnMap.get(id); if (rn) selected.push(rn);
      }
    }
    this._selectedNodes = selected;
    const newIds = new Set(selected.map(n => n._data.id!));
    this._updateSelectionVisual(prevIds, newIds);
    const first = selected.length === 1 ? selected[0] : (selected.length ? selected[0] : null);
    this._emit('node_active', first, [...selected]);
  }

  // ── 选中（仅更新样式，不重建 DOM） ──
  _selectNode(id: string) {
    const rn = this._rnMap.get(id); if (!rn) return;
    const prevIds = new Set(this._selectedNodes.map(n => n._data.id!));
    this._selectedNodes = [rn];
    this._updateSelectionVisual(prevIds, new Set([id]));
    this._emit('node_active', rn, [rn]);
  }
  _deselect() {
    if (!this._selectedNodes.length) return;
    const prevIds = new Set(this._selectedNodes.map(n => n._data.id!));
    this._selectedNodes = [];
    this._updateSelectionVisual(prevIds, new Set());
    this._emit('node_active', null, []);
  }

  // ── 行内编辑 ──
  _startEdit(id: string, cx: number, cy: number) {
    const d = this._nodeMap.get(id); if (!d) return;
    this._editId = id;
    if (!this._editEl) {
      this._editEl = document.createElement('textarea');
      this._editEl.style.cssText = 'position:fixed;z-index:2000;background:#fff;border:2px solid #1677ff;border-radius:4px;padding:8px 12px;min-width:160px;max-width:320px;box-shadow:0 4px 16px rgba(0,0,0,0.15);font-size:13px;line-height:1.6;resize:vertical;font-family:inherit;outline:none;';
      document.body.appendChild(this._editEl);
    }
    this._editEl.value = d.text || '';
    this._editEl.style.left = (cx - 80) + 'px'; this._editEl.style.top = (cy - 20) + 'px';
    this._editEl.style.display = 'block'; this._editEl.focus();
    this._editEl.onblur = () => this._finishEdit();
    this._editEl.onkeydown = (e: KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); this._finishEdit(); }
      if (e.key === 'Escape') { this._editEl!.style.display = 'none'; this._editId = null; }
    };
  }
  _finishEdit() {
    if (!this._editEl || !this._editId) return;
    const newTxt = this._editEl.value; const eid = this._editId;
    this._editEl.style.display = 'none'; this._editId = null;
    const d = this._nodeMap.get(eid);
    if (d && d.text !== newTxt) {
      this._saveHistory();
      d.text = newTxt;
      const rn = this._rnMap.get(eid); if (rn) this._emit('node_text_edit_end', rn, newTxt);
      this.refresh();
    }
  }

  // ── 折叠/展开 ──
  _toggle(id: string) { if (this._collapsed.has(id)) this._collapsed.delete(id); else this._collapsed.add(id); this.refresh(); }

  _centerOnRoot() {
    const root = this._rawTree[0]; if (!root?.id) return;
    const rootLi = this._layoutMap.get(root.id); if (!rootLi) return;
    const sr = this._el.getBoundingClientRect();
    const leftPad = this._opts?.initRootNodePosition?.[0] ?? 10;
    this._pan.dx = leftPad - rootLi.x * this._pan.scale;
    this._pan.dy = sr.height / 2 - (rootLi.y + rootLi.h / 2) * this._pan.scale;
    this._applyTf(); this._emitScrollbar();
  }

  // ── 命令 ──
  execCommand(cmd: string, ...args: any[]) {
    const mutating = ['INSERT_CHILD_NODE', 'INSERT_NODE', 'REMOVE_NODE', 'SET_NODE_TEXT'];
    if (mutating.includes(cmd)) this._saveHistory();

    switch (cmd) {
      case 'INSERT_CHILD_NODE': {
        let target: SvgMindNode | null = null; let cd: any = null; let cc: any[] = [];
        if (args.length >= 3 && args[1]?.length) { target = args[1][0]; cd = args[2]; cc = args[3] || []; }
        else { if (!this._selectedNodes.length) return; target = this._selectedNodes[0]; }
        if (!target) return;
        const pd = target._data; if (!pd.children) pd.children = [];
        if (cd) { pd.children.push(this._toMind({ data: cd, children: cc })); }
        else { pd.children.push({ id: genId(), text: '新节点', nodeType: null, sortOrder: pd.children.length, children: [] }); }
        this._collapsed.delete(pd.id!);
        this._buildMaps(); this.refresh(); break;
      }
      case 'INSERT_NODE': {
        if (!this._selectedNodes.length) return;
        const sel = this._selectedNodes[0]; const pid = this._parentMap.get(sel._data.id!);
        if (!pid) return;
        const p = this._nodeMap.get(pid); if (!p?.children) return;
        const idx = p.children.findIndex(c => c.id === sel._data.id);
        p.children.splice(idx + 1, 0, { id: genId(), text: '新节点', nodeType: null, sortOrder: idx + 1, children: [] });
        this._buildMaps(); this.refresh(); break;
      }
      case 'REMOVE_NODE': {
        for (const n of this._selectedNodes) {
          const pid = this._parentMap.get(n._data.id!); if (!pid) continue;
          const p = this._nodeMap.get(pid); if (p?.children) p.children = p.children.filter(c => c.id !== n._data.id);
        }
        this._selectedNodes = []; this._buildMaps(); this.refresh();
        this._emit('node_active', null, []); break;
      }
      case 'SET_NODE_TEXT': {
        const node = args[0] as SvgMindNode; const txt = args[1] as string;
        if (node?._data) { node._data.text = txt; this.refresh(); } break;
      }
      case 'EXPAND_ALL': this._collapsed.clear(); this.refresh(); break;
      case 'UNEXPAND_ALL': {
        this._collapsed.clear();
        const walk = (ns: MindNodeData[], depth: number) => {
          for (const n of ns) {
            if ((n.children || []).length && depth >= 1) this._collapsed.add(n.id!);
            walk(n.children || [], depth + 1);
          }
        };
        walk(this._rawTree, 0);
        this.refresh();
        this._centerOnRoot();
        break;
      }
    }
  }

  // ── 兼容 API ──
  get renderer() {
    const self = this;
    return {
      get root() { return self._root; },
      get activeNodeList() { return self._selectedNodes; },
      moveNodeToCenter(node: SvgMindNode) {
        if (!node._layout || !self._el) return;
        const sr = self._el.getBoundingClientRect();
        self._pan.dx = sr.width / 2 - (node._layout.x + node._layout.w / 2) * self._pan.scale;
        self._pan.dy = sr.height / 2 - (node._layout.y + node._layout.h / 2) * self._pan.scale;
        self._applyTf(); self._emitScrollbar();
      },
    };
  }
  get view() {
    const self = this;
    return {
      translateXY(dx: number, dy: number) { self._pan.dx += dx; self._pan.dy += dy; self._applyTf(); self._emitScrollbar(); },
      enlarge() { self._pan.scale = Math.min(self._pan.scale * 1.1, 5); self._applyTf(); self._emit('scale', self._pan.scale); self._emitScrollbar(); },
      narrow() { self._pan.scale = Math.max(self._pan.scale * 0.9, 0.1); self._applyTf(); self._emit('scale', self._pan.scale); self._emitScrollbar(); },
      reset() { self._pan.scale = 1; self._pan.dx = 0; self._pan.dy = 0; self._applyTf(); self._emit('scale', 1); self._emitScrollbar(); },
    };
  }
  get scrollbar() {
    const self = this;
    return {
      setScrollBarWrapSize(_w: number, _h: number) {},
      onClick(e: MouseEvent, dir: 'horizontal' | 'vertical') {
        const sr = self._el.getBoundingClientRect();
        const { x0, y0, x1, y1 } = self._contentBounds;
        const cW = x1 - x0, cH = y1 - y0, s = self._pan.scale;
        if (dir === 'horizontal') {
          const ratio = (e.clientX - sr.left) / sr.width;
          self._pan.dx = -(x0 + ratio * cW - sr.width / s / 2) * s;
        } else {
          const ratio = (e.clientY - sr.top) / sr.height;
          self._pan.dy = -(y0 + ratio * cH - sr.height / s / 2) * s;
        }
        self._applyTf(); self._emitScrollbar();
      },
      onMousedown(e: MouseEvent, dir: 'horizontal' | 'vertical') {
        e.preventDefault(); e.stopPropagation();
        self._scrollDrag = {
          direction: dir,
          startMouse: dir === 'horizontal' ? e.clientX : e.clientY,
          startPan: dir === 'horizontal' ? self._pan.dx : self._pan.dy,
        };
      },
    };
  }
  get opt() { return { mousewheelMoveStep: 100 }; }

  // ── 清理 ──
  _cleanDom() { this._fns.forEach(fn => fn()); this._fns = []; if (this._el) while (this._el.firstChild) this._el.removeChild(this._el.firstChild); this._svg = null; this._vp = null; }
  destroy() { this._cleanDom(); if (this._editEl) { this._editEl.remove(); this._editEl = null; } this._handlers.clear(); this._rawTree = []; this._nodeMap.clear(); this._parentMap.clear(); this._rnMap.clear(); this._root = null; this._selectedNodes = []; this._history = []; }
}
