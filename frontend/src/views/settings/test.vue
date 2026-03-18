<template>
  <div class="test-page">
    <header class="test-header">
      <el-input v-model="caseSetId" placeholder="输入用例集 ID" style="width:280px" clearable @keyup.enter="loadData" />
      <el-button type="primary" :loading="loading" @click="loadData">加载数据</el-button>
      <el-divider direction="vertical" />
      <el-radio-group v-model="engine" :disabled="!loaded">
        <el-radio-button value="standard">标准模式</el-radio-button>
        <el-radio-button value="perf">性能模式 ⚡</el-radio-button>
        <el-radio-button value="g6">G6 Canvas</el-radio-button>
        <el-radio-button value="svg">SVG 自研</el-radio-button>
      </el-radio-group>
      <el-button @click="doRender" :disabled="!loaded" plain>重新渲染</el-button>
    </header>

    <div class="stats-bar" v-if="stats.nodeCount">
      <span>节点: <b>{{ stats.nodeCount }}</b></span>
      <el-divider direction="vertical" />
      <span>API: <b>{{ stats.apiTime }}ms</b></span>
      <el-divider direction="vertical" />
      <span>渲染: <b class="render-time">{{ stats.renderTime >= 0 ? stats.renderTime + 'ms' : '计算中…' }}</b></span>
      <el-divider direction="vertical" />
      <span class="engine-label">{{ engineDesc }}</span>
      <span v-if="renderError" class="render-error">{{ renderError }}</span>
    </div>

    <div v-if="loaded" class="editor-toolbar">
      <el-tooltip content="添加子节点(Tab)"><el-button text @click="cmdAddChild"><el-icon><Plus /></el-icon></el-button></el-tooltip>
      <el-tooltip content="添加同级(Enter)"><el-button text @click="cmdAddSibling"><el-icon><CirclePlus /></el-icon></el-button></el-tooltip>
      <el-tooltip content="删除(Delete)"><el-button text @click="cmdDelete"><el-icon><Delete /></el-icon></el-button></el-tooltip>
      <el-divider direction="vertical" />
      <el-tooltip content="全部展开"><el-button text @click="cmdExpandAll"><el-icon><ArrowDown /></el-icon></el-button></el-tooltip>
      <el-tooltip content="全部折叠"><el-button text @click="cmdCollapseAll"><el-icon><ArrowRight /></el-icon></el-button></el-tooltip>
      <el-divider direction="vertical" />
      <el-tooltip content="复制(Ctrl+C)"><el-button text @click="cmdCopy"><el-icon><CopyDocument /></el-icon></el-button></el-tooltip>
      <el-tooltip content="粘贴(Ctrl+V)"><el-button text @click="cmdPaste"><el-icon><DocumentCopy /></el-icon></el-button></el-tooltip>
    </div>

    <div class="editor-body">
      <div class="mm-area">
        <div v-if="!loaded && !loading" class="placeholder-text">输入用例集 ID 点击加载，对比不同引擎的渲染速度和编辑体验</div>
        <div v-if="loading" class="placeholder-text"><el-icon class="is-loading" :size="22"><Loading /></el-icon><span style="margin-left:8px">加载中…</span></div>
        <div ref="renderBox" class="render-box"></div>
        <div v-if="isSMM && loaded" class="mm-zoom-bar">
          <button class="mm-zoom-btn" @click="zoomOut">−</button>
          <span class="mm-zoom-label" @click="zoomReset">{{ zoomLevel }}%</span>
          <button class="mm-zoom-btn" @click="zoomIn">+</button>
        </div>
        <div v-if="loaded" class="tips-bar">
          左键点选 · {{ isSMM ? '左键拖拽框选 · 右键拖拽平移' : '拖拽平移' }} · {{ isSMM ? 'Ctrl+' : '' }}滚轮缩放 · 双击编辑
        </div>
      </div>

      <div v-if="rightPanelOpen" class="right-panel">
        <div class="rp-header">
          <el-radio-group v-model="panelTab" size="small">
            <el-radio-button value="props">属性</el-radio-button>
            <el-radio-button value="comments">评论</el-radio-button>
          </el-radio-group>
          <el-button text size="small" @click="rightPanelOpen = false"><el-icon><Close /></el-icon></el-button>
        </div>
        <div class="rp-body">
          <template v-if="panelTab === 'props'">
            <template v-if="editForm">
              <div class="prop-field">
                <label>内容</label>
                <el-input v-model="editForm.text" type="textarea" :autosize="{ minRows: 2 }" @blur="syncToNode()" @keyup.enter.exact.prevent="syncToNode()" />
              </div>
              <div class="prop-field">
                <label>节点类型</label>
                <el-select v-model="editForm.nodeType" clearable @change="syncToNode(true)" style="width:100%" placeholder="可不设置">
                  <el-option v-for="opt in nodeTypeOptions" :key="opt.value" :value="opt.value" :label="opt.label" />
                </el-select>
              </div>
              <div class="prop-field">
                <label>标记</label>
                <el-select :model-value="editForm.properties?.mark || 'NONE'" @change="(v) => { if (editForm) { editForm.properties.mark = v === 'NONE' ? undefined : v; syncToNode(); } }" style="width:100%">
                  <el-option value="NONE" label="无" /><el-option value="待完成" label="待完成" /><el-option value="待确认" label="待确认" /><el-option value="待修改" label="待修改" />
                </el-select>
              </div>
              <el-divider style="margin: 8px 0" />
              <div style="color: #1677ff; font-weight: 600; font-size: 12px; margin-bottom: 8px">动态属性</div>
              <div v-for="attr in filteredEditAttrs" :key="attr.id" class="prop-field">
                <label>{{ attr.name }}</label>
                <div v-if="attr.displayType === 'TILE' && !attr.multiSelect" class="tile-group">
                  <span v-for="o in attr.options" :key="o" class="tile-tag" :class="[editForm?.properties[attr.name] === o ? 'active' : '', attr.name === '优先级' ? `priority-${o.toLowerCase()}` : '']" @click="toggleSingleTile(attr.name, o)">{{ o }}</span>
                </div>
                <div v-else-if="attr.multiSelect && attr.displayType === 'TILE'" class="tile-group">
                  <span v-for="o in attr.options" :key="o" class="tile-tag" :class="{ active: (editForm?.properties[attr.name] || []).includes(o) }" @click="toggleMultiTile(attr.name, o)">{{ o }}</span>
                </div>
                <el-select v-else-if="attr.multiSelect" multiple :model-value="Array.isArray(editForm?.properties[attr.name]) ? editForm?.properties[attr.name] : []" @change="(v) => { if (editForm) { editForm.properties[attr.name] = v; syncToNode(); } }" style="width:100%">
                  <el-option v-for="o in attr.options" :key="o" :value="o" :label="o" />
                </el-select>
                <el-select v-else :model-value="editForm?.properties[attr.name] || undefined" @change="(v) => { if (editForm) { editForm.properties[attr.name] = v; syncToNode(); } }" clearable style="width:100%">
                  <el-option v-for="o in attr.options" :key="o" :value="o" :label="o" />
                </el-select>
              </div>
              <template v-if="descendantAttrs.length">
                <el-divider style="margin: 8px 0" />
                <div style="color: #722ed1; font-weight: 600; font-size: 12px; margin-bottom: 4px">批量设置子孙属性</div>
                <div v-for="dattr in descendantAttrs" :key="dattr.id" class="prop-field">
                  <label>{{ dattr.name }} <span style="color:#722ed1;font-size:10px">({{ fmtTypeLimit(dattr.nodeTypeLimit) }})</span></label>
                  <el-select v-if="dattr.multiSelect" multiple :model-value="[]" @change="(v) => applyToDescendants(dattr, v)" style="width:100%" placeholder="选择后批量设置"><el-option v-for="o in dattr.options" :key="o" :value="o" :label="o" /></el-select>
                  <el-select v-else :model-value="undefined" @change="(v) => applyToDescendants(dattr, v)" clearable style="width:100%" placeholder="选择后批量设置"><el-option v-for="o in dattr.options" :key="o" :value="o" :label="o" /></el-select>
                </div>
              </template>
            </template>
            <div v-else class="empty-hint">选择节点查看属性</div>
          </template>
          <template v-if="panelTab === 'comments'">
            <CommentPanel ref="commentPanelRef" :node-id="activeNodeId" :case-set-id="caseSetId" :show-all-tab="true" @navigate="navigateToCommentNode" @count-changed="onCommentCountChanged" />
          </template>
        </div>
      </div>
    </div>

    <div v-if="customEdit.visible" class="custom-edit-overlay" :style="{ left: customEdit.x + 'px', top: customEdit.y + 'px' }">
      <textarea ref="customEditRef" v-model="customEdit.text" class="custom-edit-input" rows="2"
        @blur="finishCustomEdit" @keydown.enter.exact.prevent="finishCustomEdit" @keydown.escape="customEdit.visible = false" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onBeforeUnmount, watch } from 'vue';
import { Plus, CirclePlus, Delete, ArrowDown, ArrowRight, Close, Loading, CopyDocument, DocumentCopy } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { mindNodeApi, customAttributeApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { MindNodeData, CustomAttribute } from '../../types';
import { NODE_TYPE_LABEL, NODE_TYPE_COLOR, MARK_COLOR, addAllCustomLabels, setupMouseOverrides } from '../../composables/useMindMap';
import CommentPanel from '../../components/CommentPanel.vue';

const store = useAppStore();

// ════════════════ 共享状态 ════════════════
const caseSetId = ref('2033923489382203394');
const engine = ref<'standard' | 'perf' | 'g6' | 'svg'>('standard');
const loading = ref(false);
const loaded = ref(false);
const renderError = ref('');
const renderBox = ref<HTMLDivElement | null>(null);
const stats = reactive({ nodeCount: 0, apiTime: 0, renderTime: -1 });
const zoomLevel = ref(100);
const rightPanelOpen = ref(false);
const panelTab = ref<'props' | 'comments'>('props');
const editForm = ref<{ text: string; nodeType: string | null; properties: Record<string, any> } | null>(null);
const activeNodeInstance = ref<any>(null);
const projectAttributes = ref<CustomAttribute[]>([]);
const commentPanelRef = ref<InstanceType<typeof CommentPanel> | null>(null);
const nodeTypeOptions = [
  { value: 'TITLE', label: '用例标题' }, { value: 'PRECONDITION', label: '前置条件' },
  { value: 'STEP', label: '步骤' }, { value: 'EXPECTED', label: '预期结果' },
];
let rawTree: MindNodeData[] = [];
let smmInst: any = null;
let g6Inst: any = null;
let cleanupMouse: (() => void) | null = null;
let pluginsReady = false;
let clipboardNodes: any[] = [];

// G6 + SVG 共享
let selectedId: string | null = null;
const nodeMap = new Map<string, MindNodeData>();
const parentMap = new Map<string, string>();
const customEdit = reactive({ visible: false, text: '', nodeId: '', x: 0, y: 0 });
const customEditRef = ref<HTMLTextAreaElement | null>(null);
let svgCleanupFns: (() => void)[] = [];
let svgPan = { active: false, startX: 0, startY: 0, dx: 0, dy: 0, scale: 1, didDrag: false };

// ════════════════ 计算属性 ════════════════
const isSMM = computed(() => engine.value === 'standard' || engine.value === 'perf');
const isCustom = computed(() => engine.value === 'g6' || engine.value === 'svg');
const activeNodeId = computed(() => {
  if (isSMM.value) return activeNodeInstance.value?.nodeData?.data?._raw?.id ?? null;
  return selectedId;
});
const engineDesc = computed(() => ({
  standard: 'Simple-Mind-Map 标准 — 完整编辑，与编辑器一致',
  perf: 'Simple-Mind-Map 性能模式 — openPerformance:true，完整编辑',
  g6: '@antv/G6 Canvas — 方框节点，完整编辑',
  svg: 'SVG 自研 — 方框节点+类型标签+属性+标记边框+评论角标，完整编辑',
}[engine.value]));
const filteredEditAttrs = computed(() => {
  if (!editForm.value) return [];
  const nt = editForm.value.nodeType;
  return projectAttributes.value.filter(a => {
    if (a.name === '标记') return false;
    if (!a.nodeTypeLimit) return true;
    return nt ? a.nodeTypeLimit.split(',').includes(nt) : false;
  });
});
const descendantAttrs = computed(() => {
  if (!editForm.value) return [];
  const directIds = new Set(filteredEditAttrs.value.map(a => a.id));
  let children: MindNodeData[] = [];
  if (isSMM.value && activeNodeInstance.value) children = (activeNodeInstance.value.children || []).map(smmToMind);
  else if (isCustom.value && selectedId) children = nodeMap.get(selectedId)?.children || [];
  return projectAttributes.value.filter(a => {
    if (a.name === '标记') return false;
    if (directIds.has(a.id) || !a.nodeTypeLimit) return false;
    return hasDescType(children, a.nodeTypeLimit.split(','));
  });
});
function smmToMind(n: any): MindNodeData { const r = n.nodeData?.data?._raw || {}; return { id: r.id, text: r.text, nodeType: r.nodeType, sortOrder: 0, children: (n.children || []).map(smmToMind), properties: r.properties }; }
function hasDescType(nodes: MindNodeData[], types: string[]): boolean { for (const c of nodes) { if (c.nodeType && types.includes(c.nodeType)) return true; if (hasDescType(c.children || [], types)) return true; } return false; }
function fmtTypeLimit(l: string | null): string { return l ? l.split(',').map(t => NODE_TYPE_LABEL[t] || t).join('、') : ''; }

// ════════════════ 数据加载 ════════════════
function countNodes(ns: MindNodeData[]): number { let c = 0; for (const n of ns) c += 1 + countNodes(n.children || []); return c; }
function buildMaps(tree: MindNodeData[]) {
  nodeMap.clear(); parentMap.clear();
  (function walk(ns: MindNodeData[], pid?: string) { for (const n of ns) { if (n.id) { nodeMap.set(n.id, n); if (pid) parentMap.set(n.id, pid); } walk(n.children || [], n.id); } })(tree);
}
async function loadData() {
  if (!caseSetId.value) { ElMessage.warning('请输入用例集 ID'); return; }
  loading.value = true; renderError.value = ''; cleanup();
  try {
    const t0 = performance.now();
    const [treeRes, attrRes] = await Promise.all([mindNodeApi.tree(caseSetId.value), store.currentProject ? customAttributeApi.list(store.currentProject.id) : Promise.resolve({ data: [] })]);
    stats.apiTime = Math.round(performance.now() - t0);
    rawTree = treeRes.data.tree || [];
    projectAttributes.value = (attrRes as any).data || [];
    stats.nodeCount = countNodes(rawTree);
    buildMaps(rawTree);
    loaded.value = true; await nextTick(); await doRender();
  } catch (e: any) { ElMessage.error('加载失败: ' + (e.message || e)); } finally { loading.value = false; }
}

// ════════════════ 渲染调度 ════════════════
async function doRender() {
  if (!rawTree.length) return;
  cleanup(); stats.renderTime = -1; renderError.value = '';
  editForm.value = null; activeNodeInstance.value = null; selectedId = null; rightPanelOpen.value = false;
  await nextTick(); await new Promise(r => requestAnimationFrame(r));
  try {
    if (engine.value === 'standard') await renderSMM(false);
    else if (engine.value === 'perf') await renderSMM(true);
    else if (engine.value === 'g6') await renderG6();
    else await renderSVG();
  } catch (e: any) { console.error('[render error]', e); renderError.value = '渲染出错: ' + (e.message || String(e)).slice(0, 200); }
}
watch(engine, () => { if (loaded.value) doRender(); });

// ════════════════ SMM 渲染 ════════════════
async function renderSMM(perfMode: boolean) {
  const MindMap = (await import('simple-mind-map')).default;
  if (!pluginsReady) { const [S, D, Sb] = await Promise.all([import('simple-mind-map/src/plugins/Select'), import('simple-mind-map/src/plugins/Drag'), import('simple-mind-map/src/plugins/Scrollbar')]); MindMap.usePlugin(S.default); MindMap.usePlugin(D.default); MindMap.usePlugin(Sb.default); pluginsReady = true; }
  const el = renderBox.value; if (!el) return;
  const t0 = performance.now();
  smmInst = new MindMap({ el, data: toSMM(rawTree[0]), theme: 'classic4', layout: 'logicalStructure', mousewheelAction: 'move', textAutoWrapWidth: 300, enableFreeDrag: false, initRootNodePosition: [10, 'center'], enableShortcutOnlyWhenMouseInSvg: true, useLeftKeySelectionRightKeyDrag: true, enableNodeTransitionMove: false, createNodePrefixContent: false, isShowExpandNum: false, openPerformance: perfMode, performanceConfig: { time: 250, padding: 200, removeNodeWhenOutCanvas: true }, themeConfig: { second: { marginX: 80, marginY: 60 }, node: { marginX: 50, marginY: 50 } } } as any);
  smmInst.on('node_active', (_: any, nodes: any[]) => {
    if (nodes.length === 1) { const node = nodes[0]; activeNodeInstance.value = node; const raw = node.nodeData?.data?._raw || {}; editForm.value = { text: raw.text ?? node.nodeData?.data?.text ?? '', nodeType: raw.nodeType || null, properties: raw.properties ? JSON.parse(JSON.stringify(raw.properties)) : {} }; if (!rightPanelOpen.value) { rightPanelOpen.value = true; panelTab.value = 'props'; } if (panelTab.value === 'comments') nextTick(() => commentPanelRef.value?.refresh()); }
    else { activeNodeInstance.value = null; editForm.value = null; if (nodes.length === 0 && panelTab.value === 'props') rightPanelOpen.value = false; }
  });
  smmInst.on('node_text_edit_end', (node: any, text: string) => { const raw = node.nodeData?.data?._raw; if (raw) raw.text = text; if (editForm.value && activeNodeInstance.value === node) editForm.value.text = text; });
  smmInst.on('data_change', () => { scheduleRefreshLabels(); });
  smmInst.on('scale', (s: number) => { zoomLevel.value = Math.round(s * 100); });
  await new Promise<void>(resolve => { const done = () => { stats.renderTime = Math.round(performance.now() - t0); try { addAllCustomLabels(smmInst); } catch {} resolve(); }; smmInst.on('node_tree_render_end', done); setTimeout(() => { if (stats.renderTime < 0) done(); }, 30000); });
  cleanupMouse = setupMouseOverrides(el, () => smmInst);
}
function toSMM(node: MindNodeData): any { const id = node.id || genId(); return { data: { text: node.text, uid: id, _raw: { id, text: node.text, nodeType: node.nodeType || null, sortOrder: node.sortOrder || 0, isRoot: node.isRoot, properties: node.properties, commentCount: node.commentCount || 0 } }, children: (node.children || []).map(c => toSMM(c)) }; }
let refreshScheduled = false;
function scheduleRefreshLabels() { if (refreshScheduled) return; refreshScheduled = true; requestAnimationFrame(() => { addAllCustomLabels(smmInst); refreshScheduled = false; }); }
function zoomIn() { smmInst?.view?.enlarge(); }
function zoomOut() { smmInst?.view?.narrow(); }
function zoomReset() { smmInst?.view?.reset(); }

// ════════════════ 通用树布局算法（G6 + SVG 共享） ════════════════
interface LI { id: string; x: number; y: number; w: number; h: number; }
const LAYOUT_HGAP = 50;
const LAYOUT_VGAP = 50;

function txtW(text: string): number {
  let w = 0;
  for (let i = 0; i < text.length; i++) w += text.charCodeAt(i) > 255 ? 13 : 7;
  return w;
}
function calcLayout(root: MindNodeData): LI[] {
  const items: LI[] = [];
  function nW(n: MindNodeData) { return Math.min(Math.max(txtW(n.text || '') + 28, 60), 300); }
  function nH(n: MindNodeData) {
    const w = nW(n);
    const innerW = Math.max(w - 28, 30);
    const tw = txtW(n.text || '');
    const lines = Math.ceil(tw / innerW) || 1;
    return Math.max(lines * 22 + 20, 40);
  }
  function subH(n: MindNodeData): number {
    const ch = n.children || [];
    if (!ch.length) return nH(n);
    let t = 0;
    for (let i = 0; i < ch.length; i++) { t += subH(ch[i]); if (i < ch.length - 1) t += LAYOUT_VGAP; }
    return Math.max(nH(n), t);
  }
  function lay(n: MindNodeData, x: number, ys: number, ye: number) {
    const w = nW(n), h = nH(n), cy = (ys + ye) / 2;
    items.push({ id: n.id!, x, y: cy - h / 2, w, h });
    const ch = n.children || [];
    if (!ch.length) return;
    const cx = x + w + LAYOUT_HGAP;
    const hs = ch.map(subH);
    const tot = hs.reduce((a, b) => a + b, 0) + (ch.length - 1) * LAYOUT_VGAP;
    let curY = cy - tot / 2;
    for (let i = 0; i < ch.length; i++) { lay(ch[i], cx, curY, curY + hs[i]); curY += hs[i] + LAYOUT_VGAP; }
  }
  lay(root, 0, 0, subH(root));
  return items;
}

// ════════════════ SVG 共享绘制工具 ════════════════

function mkPath(d: string, container: SVGElement) {
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', d); p.setAttribute('fill', 'none'); p.setAttribute('stroke', '#a1aab4'); p.setAttribute('stroke-width', '1.5');
  container.appendChild(p);
}

function drawOrthEdges(nd: MindNodeData, lm: Map<string, LI>, container: SVGElement) {
  const pl = lm.get(nd.id!);
  if (!pl) return;
  const children = (nd.children || []).filter(c => lm.has(c.id!));
  if (!children.length) return;

  const midX = pl.x + pl.w + LAYOUT_HGAP / 2;
  const py = pl.y + pl.h / 2;
  mkPath(`M${pl.x + pl.w},${py} H${midX}`, container);

  const cYs = children.map(c => { const cl = lm.get(c.id!)!; return cl.y + cl.h / 2; });
  const allYs = [py, ...cYs];
  const minY = Math.min(...allYs), maxY = Math.max(...allYs);
  if (minY !== maxY) mkPath(`M${midX},${minY} V${maxY}`, container);

  for (let i = 0; i < children.length; i++) {
    const cl = lm.get(children[i].id!)!;
    mkPath(`M${midX},${cYs[i]} H${cl.x}`, container);
  }
  for (const c of children) drawOrthEdges(c, lm, container);
}

function drawSvgNode(it: LI, container: SVGElement, sel: boolean) {
  const data = nodeMap.get(it.id);
  if (!data) return;
  const mark = data.properties?.mark;
  const mc = mark ? (MARK_COLOR[mark] || null) : null;
  const isR = !!data.isRoot;

  const g = document.createElementNS('http://www.w3.org/2000/svg', 'g');
  g.setAttribute('transform', `translate(${it.x},${it.y})`); g.style.cursor = 'pointer';

  const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
  rect.setAttribute('width', String(it.w)); rect.setAttribute('height', String(it.h)); rect.setAttribute('rx', '4');
  rect.setAttribute('fill', isR ? '#1677ff' : '#fff');
  rect.setAttribute('stroke', sel ? '#1677ff' : (mc || '#d9d9d9'));
  rect.setAttribute('stroke-width', sel ? '2' : (mc ? '2.5' : '1'));
  if (sel) rect.setAttribute('filter', 'drop-shadow(0 0 6px rgba(22,119,255,0.3))');
  g.appendChild(rect);

  const fo = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
  fo.setAttribute('x', '0'); fo.setAttribute('y', '0');
  fo.setAttribute('width', String(it.w)); fo.setAttribute('height', String(it.h));
  const td = document.createElement('div');
  td.style.cssText = `font-size:13px;color:${isR ? '#fff' : '#333'};display:flex;align-items:center;height:100%;word-break:break-all;line-height:1.6;padding:10px 14px;box-sizing:border-box;`;
  td.textContent = data.text || '';
  fo.appendChild(td); g.appendChild(fo);

  if (data.nodeType && NODE_TYPE_LABEL[data.nodeType]) {
    const tfo = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
    tfo.setAttribute('x', '0'); tfo.setAttribute('y', '-20');
    tfo.setAttribute('width', String(Math.max(it.w, 150))); tfo.setAttribute('height', '20');
    tfo.style.overflow = 'visible'; tfo.style.pointerEvents = 'none';
    const div = document.createElement('div');
    div.style.cssText = `font-size:11px;font-weight:600;color:${NODE_TYPE_COLOR[data.nodeType] || '#1677ff'};line-height:18px;white-space:nowrap;`;
    div.textContent = NODE_TYPE_LABEL[data.nodeType];
    tfo.appendChild(div); g.appendChild(tfo);
  }

  const ps = buildPropsStr(data.properties);
  if (ps) {
    const pfo = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
    pfo.setAttribute('x', '0'); pfo.setAttribute('y', String(it.h + 2));
    pfo.setAttribute('width', String(Math.max(it.w, 400))); pfo.setAttribute('height', '22');
    pfo.style.overflow = 'visible'; pfo.style.pointerEvents = 'none';
    const div = document.createElement('div');
    div.style.cssText = 'display:flex;gap:4px;line-height:18px;white-space:nowrap;flex-wrap:wrap;';
    ps.split(' | ').forEach(p => { const sp = document.createElement('span'); sp.style.cssText = 'font-size:10px;background:rgba(0,0,0,0.06);color:#595959;border-radius:2px;padding:0 5px;'; sp.textContent = p; div.appendChild(sp); });
    pfo.appendChild(div); g.appendChild(pfo);
  }

  const cc = data.commentCount || 0;
  if (cc > 0) {
    const badge = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    badge.setAttribute('cx', String(it.w - 2)); badge.setAttribute('cy', '-4'); badge.setAttribute('r', '9'); badge.setAttribute('fill', '#ff4d4f');
    g.appendChild(badge);
    const bt = document.createElementNS('http://www.w3.org/2000/svg', 'text');
    bt.setAttribute('x', String(it.w - 2)); bt.setAttribute('y', '-1');
    bt.setAttribute('text-anchor', 'middle'); bt.setAttribute('font-size', '10'); bt.setAttribute('font-weight', 'bold'); bt.setAttribute('fill', '#fff');
    bt.textContent = cc > 99 ? '99+' : String(cc); g.appendChild(bt);
  }

  g.addEventListener('click', (e) => { e.stopPropagation(); selectCustomNode(it.id); });
  g.addEventListener('dblclick', (e) => { e.stopPropagation(); startCustomEdit(it.id, e.clientX, e.clientY); });
  container.appendChild(g);
}

// ════════════════ G6 Canvas 渲染 ════════════════
function buildG6Style(data: MindNodeData, layout: LI, sel: boolean) {
  const mark = data.properties?.mark;
  const mc = mark ? (MARK_COLOR[mark] || null) : null;
  const isR = !!data.isRoot;
  const badges: any[] = [];
  if (data.nodeType && NODE_TYPE_LABEL[data.nodeType]) badges.push({ text: NODE_TYPE_LABEL[data.nodeType], placement: 'top', offsetY: -4, fill: NODE_TYPE_COLOR[data.nodeType] || '#666', fontSize: 10, fontWeight: 600, backgroundFill: 'transparent', padding: [0, 0] });
  const ps = buildPropsStr(data.properties);
  if (ps) badges.push({ text: ps.length > 35 ? ps.slice(0, 35) + '…' : ps, placement: 'bottom', offsetY: 4, fill: '#8c8c8c', fontSize: 9, backgroundFill: 'rgba(0,0,0,0.04)', backgroundRadius: 2, padding: [1, 4] });
  if ((data.commentCount || 0) > 0) badges.push({ text: String(data.commentCount! > 99 ? '99+' : data.commentCount), placement: 'right-top', fill: '#fff', fontSize: 8, fontWeight: 600, backgroundFill: '#ff4d4f', backgroundRadius: 8, padding: [1, 4] });
  return { x: layout.x + layout.w / 2, y: layout.y + layout.h / 2, size: [layout.w, layout.h], radius: 4, fill: isR ? '#1677ff' : '#fff', stroke: sel ? '#1677ff' : (mc || '#d9d9d9'), lineWidth: sel ? 2 : (mc ? 2.5 : 1), shadowColor: sel ? 'rgba(22,119,255,0.25)' : undefined, shadowBlur: sel ? 10 : 0, labelText: data.text || '', labelFontSize: 13, labelFill: isR ? '#fff' : '#333', labelPlacement: 'center', labelWordWrap: true, labelMaxWidth: layout.w - 24, badges };
}
async function renderG6() {
  const { Graph } = await import('@antv/g6');
  const el = renderBox.value; if (!el) return;
  let w = el.clientWidth || el.offsetWidth; let h = el.clientHeight || el.offsetHeight;
  if (!w || !h) { await new Promise(r => setTimeout(r, 200)); w = el.clientWidth || 1200; h = el.clientHeight || 800; }
  const lis = calcLayout(rawTree[0]); const lm = new Map(lis.map(it => [it.id, it]));
  const nodes: any[] = []; const edges: any[] = [];
  (function build(nd: MindNodeData) { const l = lm.get(nd.id!); if (l) nodes.push({ id: String(nd.id), data: { text: nd.text, nodeType: nd.nodeType, isRoot: !!nd.isRoot, properties: nd.properties, commentCount: nd.commentCount || 0 }, style: buildG6Style(nd, l, false) }); for (const c of (nd.children || [])) { edges.push({ id: `e_${nd.id}_${c.id}`, source: String(nd.id), target: String(c.id) }); build(c); } })(rawTree[0]);
  const t0 = performance.now();
  g6Inst = new Graph({ container: el, width: w, height: h, autoFit: 'view', padding: [40, 40, 40, 40], data: { nodes, edges }, node: { type: 'rect' }, edge: { type: 'cubic-horizontal', style: { stroke: '#c0c4cc', lineWidth: 1 } }, behaviors: ['drag-canvas', 'zoom-canvas'], animation: false });
  await g6Inst.render();
  stats.renderTime = Math.round(performance.now() - t0);
  g6Inst.on('node:click', (e: any) => { const id = e.target?.id; if (id) selectCustomNode(String(id)); });
  g6Inst.on('node:dblclick', (e: any) => { const id = e.target?.id; const de = e.nativeEvent || e.event || e; if (id) startCustomEdit(String(id), de?.clientX || 0, de?.clientY || 0); });
}
function refreshG6() {
  if (!g6Inst || !rawTree.length) return;
  buildMaps(rawTree); const lis = calcLayout(rawTree[0]); const lm = new Map(lis.map(it => [it.id, it]));
  const nodes: any[] = []; const edges: any[] = [];
  (function build(nd: MindNodeData) { const l = lm.get(nd.id!); if (l) nodes.push({ id: String(nd.id), data: { text: nd.text, nodeType: nd.nodeType, isRoot: !!nd.isRoot, properties: nd.properties, commentCount: nd.commentCount || 0 }, style: buildG6Style(nd, l, nd.id === selectedId) }); for (const c of (nd.children || [])) { edges.push({ id: `e_${nd.id}_${c.id}`, source: String(nd.id), target: String(c.id) }); build(c); } })(rawTree[0]);
  try { g6Inst.setData({ nodes, edges }); g6Inst.render(); } catch (e) { console.error('[G6] refresh failed', e); }
}

// ════════════════ SVG 自研渲染（完整编辑 — 类型标签+属性+标记边框+评论角标） ════════════════
function renderSVG() {
  const el = renderBox.value; if (!el) return;
  const t0 = performance.now();
  const lis = calcLayout(rawTree[0]); const lm = new Map(lis.map(it => [it.id, it]));
  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
  for (const it of lis) { minX = Math.min(minX, it.x - 25); minY = Math.min(minY, it.y - 25); maxX = Math.max(maxX, it.x + it.w + 25); maxY = Math.max(maxY, it.y + it.h + 30); }

  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('width', '100%'); svg.setAttribute('height', '100%'); svg.style.background = '#fafafa'; el.appendChild(svg);
  const vp = document.createElementNS('http://www.w3.org/2000/svg', 'g');
  svg.appendChild(vp);

  const cW = maxX - minX, cH = maxY - minY;
  const sr = el.getBoundingClientRect();
  const fitS = Math.min(sr.width / cW, sr.height / cH, 1) * 0.9;
  svgPan = { active: false, startX: 0, startY: 0, dx: (sr.width - cW * fitS) / 2 - minX * fitS, dy: (sr.height - cH * fitS) / 2 - minY * fitS, scale: fitS, didDrag: false };
  vp.setAttribute('transform', `translate(${svgPan.dx},${svgPan.dy}) scale(${svgPan.scale})`);

  drawOrthEdges(rawTree[0], lm, vp);
  for (const it of lis) drawSvgNode(it, vp, it.id === selectedId);

  // 平移/缩放
  const onDown = (e: MouseEvent) => { if (e.button !== 0) return; svgPan.active = true; svgPan.didDrag = false; svgPan.startX = e.clientX - svgPan.dx; svgPan.startY = e.clientY - svgPan.dy; svg.style.cursor = 'grabbing'; };
  const onMove = (e: MouseEvent) => { if (!svgPan.active) return; svgPan.didDrag = true; svgPan.dx = e.clientX - svgPan.startX; svgPan.dy = e.clientY - svgPan.startY; vp.setAttribute('transform', `translate(${svgPan.dx},${svgPan.dy}) scale(${svgPan.scale})`); };
  const onUp = () => { svgPan.active = false; svg.style.cursor = ''; };
  const onWheel = (e: WheelEvent) => { e.preventDefault(); svgPan.scale *= e.deltaY > 0 ? 0.95 : 1.05; svgPan.scale = Math.max(0.1, Math.min(5, svgPan.scale)); vp.setAttribute('transform', `translate(${svgPan.dx},${svgPan.dy}) scale(${svgPan.scale})`); };
  svg.addEventListener('mousedown', onDown); window.addEventListener('mousemove', onMove); window.addEventListener('mouseup', onUp); svg.addEventListener('wheel', onWheel, { passive: false });
  svg.addEventListener('click', (e) => { if (svgPan.didDrag || e.target !== svg) return; selectedId = null; editForm.value = null; if (panelTab.value === 'props') rightPanelOpen.value = false; });
  svgCleanupFns.push(() => { svg.removeEventListener('mousedown', onDown); window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); svg.removeEventListener('wheel', onWheel); });

  stats.renderTime = Math.round(performance.now() - t0);
}

function refreshSVG() {
  const el = renderBox.value; if (!el || !rawTree.length) return;
  const saved = { ...svgPan };
  svgCleanupFns.forEach(fn => fn()); svgCleanupFns = [];
  while (el.firstChild) el.removeChild(el.firstChild);
  buildMaps(rawTree);

  const lis = calcLayout(rawTree[0]); const lm = new Map(lis.map(it => [it.id, it]));
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('width', '100%'); svg.setAttribute('height', '100%'); svg.style.background = '#fafafa'; el.appendChild(svg);
  const vp = document.createElementNS('http://www.w3.org/2000/svg', 'g');
  svg.appendChild(vp);
  svgPan = { ...saved, active: false, didDrag: false };
  vp.setAttribute('transform', `translate(${svgPan.dx},${svgPan.dy}) scale(${svgPan.scale})`);

  drawOrthEdges(rawTree[0], lm, vp);
  for (const it of lis) drawSvgNode(it, vp, it.id === selectedId);
  const onDown = (e: MouseEvent) => { if (e.button !== 0) return; svgPan.active = true; svgPan.didDrag = false; svgPan.startX = e.clientX - svgPan.dx; svgPan.startY = e.clientY - svgPan.dy; svg.style.cursor = 'grabbing'; };
  const onMove = (e: MouseEvent) => { if (!svgPan.active) return; svgPan.didDrag = true; svgPan.dx = e.clientX - svgPan.startX; svgPan.dy = e.clientY - svgPan.startY; vp.setAttribute('transform', `translate(${svgPan.dx},${svgPan.dy}) scale(${svgPan.scale})`); };
  const onUp = () => { svgPan.active = false; svg.style.cursor = ''; };
  const onWheel = (e: WheelEvent) => { e.preventDefault(); svgPan.scale *= e.deltaY > 0 ? 0.95 : 1.05; svgPan.scale = Math.max(0.1, Math.min(5, svgPan.scale)); vp.setAttribute('transform', `translate(${svgPan.dx},${svgPan.dy}) scale(${svgPan.scale})`); };
  svg.addEventListener('mousedown', onDown); window.addEventListener('mousemove', onMove); window.addEventListener('mouseup', onUp); svg.addEventListener('wheel', onWheel, { passive: false });
  svg.addEventListener('click', (e) => { if (svgPan.didDrag || e.target !== svg) return; selectedId = null; editForm.value = null; if (panelTab.value === 'props') rightPanelOpen.value = false; });
  svgCleanupFns.push(() => { svg.removeEventListener('mousedown', onDown); window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); svg.removeEventListener('wheel', onWheel); });
}

// ════════════════ G6+SVG 共享：选择 / 行内编辑 / 树操作 ════════════════
function refreshCustom() { if (engine.value === 'g6') refreshG6(); else refreshSVG(); }

function selectCustomNode(nodeId: string) {
  selectedId = nodeId;
  const data = nodeMap.get(nodeId); if (!data) return;
  editForm.value = { text: data.text || '', nodeType: data.nodeType || null, properties: data.properties ? JSON.parse(JSON.stringify(data.properties)) : {} };
  if (!rightPanelOpen.value) { rightPanelOpen.value = true; panelTab.value = 'props'; }
  if (panelTab.value === 'comments') nextTick(() => commentPanelRef.value?.refresh());
  refreshCustom();
}
function startCustomEdit(nodeId: string, cx: number, cy: number) {
  const data = nodeMap.get(nodeId); if (!data) return;
  customEdit.visible = true; customEdit.nodeId = nodeId; customEdit.text = data.text || '';
  customEdit.x = cx - 80; customEdit.y = cy - 20;
  nextTick(() => customEditRef.value?.focus());
}
function finishCustomEdit() {
  if (!customEdit.visible) return;
  const data = nodeMap.get(customEdit.nodeId);
  if (data && data.text !== customEdit.text) { data.text = customEdit.text; if (editForm.value && selectedId === customEdit.nodeId) editForm.value.text = customEdit.text; refreshCustom(); }
  customEdit.visible = false;
}
function customAddChild() {
  if (!selectedId) { ElMessage.warning('请先选中节点'); return; }
  const parent = nodeMap.get(selectedId); if (!parent) return;
  const nid = genId(); const nn: MindNodeData = { id: nid, text: '新节点', nodeType: null, sortOrder: (parent.children?.length || 0), children: [] };
  if (!parent.children) parent.children = []; parent.children.push(nn);
  buildMaps(rawTree); refreshCustom(); nextTick(() => selectCustomNode(nid));
}
function customAddSibling() {
  if (!selectedId) { ElMessage.warning('请先选中节点'); return; }
  const pid = parentMap.get(selectedId); if (!pid) { ElMessage.warning('根节点不能添加同级'); return; }
  const parent = nodeMap.get(pid); if (!parent?.children) return;
  const idx = parent.children.findIndex(c => c.id === selectedId);
  const nid = genId(); const nn: MindNodeData = { id: nid, text: '新节点', nodeType: null, sortOrder: idx + 1, children: [] };
  parent.children.splice(idx + 1, 0, nn);
  buildMaps(rawTree); refreshCustom(); nextTick(() => selectCustomNode(nid));
}
function customDeleteNode() {
  if (!selectedId) { ElMessage.warning('请先选中节点'); return; }
  const pid = parentMap.get(selectedId); if (!pid) { ElMessage.warning('不能删除根节点'); return; }
  const parent = nodeMap.get(pid); if (parent?.children) parent.children = parent.children.filter(c => c.id !== selectedId);
  selectedId = null; editForm.value = null; rightPanelOpen.value = false;
  buildMaps(rawTree); refreshCustom();
}

// ════════════════ 统一命令分发 ════════════════
function cmdAddChild() { if (isSMM.value) smmInst?.execCommand('INSERT_CHILD_NODE'); else customAddChild(); }
function cmdAddSibling() { if (isSMM.value) smmInst?.execCommand('INSERT_NODE'); else customAddSibling(); }
function cmdDelete() { if (isSMM.value) smmInst?.execCommand('REMOVE_NODE'); else customDeleteNode(); }
function cmdExpandAll() { if (isSMM.value) smmInst?.execCommand('EXPAND_ALL'); }
function cmdCollapseAll() { if (isSMM.value) smmInst?.execCommand('UNEXPAND_ALL'); }
function cmdCopy() {
  if (isSMM.value) { const sel: any[] = smmInst?.renderer?.activeNodeList || []; if (!sel.length) { ElMessage.warning('请先选中节点'); return; } const ss = new Set(sel); clipboardNodes = sel.filter((n: any) => !ss.has(n.parent)).map(extractClipSMM); ElMessage.success(`已复制 ${clipboardNodes.length} 个节点`); }
  else if (isCustom.value && selectedId) { const nd = nodeMap.get(selectedId); if (nd) { clipboardNodes = [JSON.parse(JSON.stringify(nd))]; ElMessage.success('已复制 1 个节点'); } }
}
function cmdPaste() {
  if (!clipboardNodes.length) { ElMessage.warning('剪贴板为空'); return; }
  if (isSMM.value) { const t = activeNodeInstance.value; if (!t) { ElMessage.warning('请先选中目标节点'); return; } const cp = JSON.parse(JSON.stringify(clipboardNodes)); cp.forEach(assignFreshIds); for (const c of cp) smmInst.execCommand('INSERT_CHILD_NODE', false, [t], c.data, c.children); scheduleRefreshLabels(); }
  else if (isCustom.value) { if (!selectedId) { ElMessage.warning('请先选中目标节点'); return; } const p = nodeMap.get(selectedId); if (!p) return; if (!p.children) p.children = []; const cp: MindNodeData[] = JSON.parse(JSON.stringify(clipboardNodes)); function regen(n: MindNodeData) { n.id = genId(); (n.children || []).forEach(regen); } cp.forEach(regen); p.children.push(...cp); buildMaps(rawTree); refreshCustom(); }
  ElMessage.success(`已粘贴 ${clipboardNodes.length} 个节点`);
}
function extractClipSMM(rn: any): any { const d = rn.nodeData?.data || {}; return { data: { text: d.text || '节点', _raw: d._raw ? { ...d._raw, id: undefined } : { text: d.text || '节点' } }, children: (rn.children || []).map(extractClipSMM) }; }
function assignFreshIds(node: any) { const id = genId(); if (node.data?._raw) node.data._raw.id = id; node.data.uid = id; node.children?.forEach(assignFreshIds); }

// ════════════════ 属性面板同步 ════════════════
function syncToNode(checkAutoChain = false) { if (isSMM.value) syncSMM(checkAutoChain); else syncCustom(checkAutoChain); }
function syncSMM(ck: boolean) {
  if (!editForm.value || !activeNodeInstance.value || !smmInst) return;
  const f = editForm.value, n = activeNodeInstance.value; if (!n.nodeData?.data) return;
  const prev = n.nodeData.data._raw ? JSON.parse(JSON.stringify(n.nodeData.data._raw)) : { text: '', nodeType: null, properties: {} };
  if (prev.text !== f.text) smmInst.execCommand('SET_NODE_TEXT', n, f.text);
  if (!n.nodeData.data._raw) n.nodeData.data._raw = prev;
  if (ck && f.nodeType !== prev.nodeType) { for (const a of projectAttributes.value) { if (a.name === '标记') continue; if (a.nodeTypeLimit) { const al = a.nodeTypeLimit.split(','); if (!f.nodeType || !al.includes(f.nodeType)) delete f.properties[a.name]; } } }
  const cp: Record<string, any> = {}; for (const [k, v] of Object.entries(f.properties)) { if (v !== undefined && v !== null) cp[k] = v; }
  const raw = n.nodeData.data._raw; raw.id = prev.id; raw.text = f.text; raw.nodeType = f.nodeType; raw.sortOrder = prev.sortOrder; raw.isRoot = prev.isRoot; raw.properties = cp;
  if (ck && f.nodeType === 'TITLE') autoChainSMM(n);
  scheduleRefreshLabels();
}
function syncCustom(ck: boolean) {
  if (!editForm.value || !selectedId) return;
  const f = editForm.value; const d = nodeMap.get(selectedId); if (!d) return;
  if (ck && f.nodeType !== d.nodeType) { for (const a of projectAttributes.value) { if (a.name === '标记') continue; if (a.nodeTypeLimit) { const al = a.nodeTypeLimit.split(','); if (!f.nodeType || !al.includes(f.nodeType)) delete f.properties[a.name]; } } }
  const cp: Record<string, any> = {}; for (const [k, v] of Object.entries(f.properties)) { if (v !== undefined && v !== null) cp[k] = v; }
  d.text = f.text; d.nodeType = f.nodeType; d.properties = cp;
  if (ck && f.nodeType === 'TITLE') autoChainTree(d);
  refreshCustom();
}
function autoChainSMM(tn: any) { const ch = ['PRECONDITION', 'STEP', 'EXPECTED']; function assign(ns: any[], d: number) { if (d >= ch.length || !ns?.length) return; for (const n of ns) { if (!n.nodeData?.data?._raw) { if (!n.nodeData) n.nodeData = {}; if (!n.nodeData.data) n.nodeData.data = {}; n.nodeData.data._raw = { text: '', nodeType: null, properties: {} }; } n.nodeData.data._raw.nodeType = ch[d]; if (n.children?.length) assign(n.children, d + 1); } } if (tn.children?.length) assign(tn.children, 0); scheduleRefreshLabels(); }
function autoChainTree(tn: MindNodeData) { const ch = ['PRECONDITION', 'STEP', 'EXPECTED']; function assign(ns: MindNodeData[], d: number) { if (d >= ch.length || !ns?.length) return; for (const n of ns) { n.nodeType = ch[d]; if (n.children?.length) assign(n.children, d + 1); } } if (tn.children?.length) assign(tn.children, 0); }
function toggleSingleTile(a: string, o: string) { if (!editForm.value) return; editForm.value.properties[a] = editForm.value.properties[a] === o ? undefined : o; syncToNode(); }
function toggleMultiTile(a: string, o: string) { if (!editForm.value) return; const c = editForm.value.properties[a]; const arr: string[] = Array.isArray(c) ? [...c] : []; const i = arr.indexOf(o); if (i >= 0) arr.splice(i, 1); else arr.push(o); editForm.value.properties[a] = arr; syncToNode(); }
function applyToDescendants(attr: CustomAttribute, value: any) {
  const al = attr.nodeTypeLimit ? attr.nodeTypeLimit.split(',') : []; let cnt = 0;
  if (isSMM.value && activeNodeInstance.value) { (function w(n: any) { for (const c of (n.children || [])) { const r = c.nodeData?.data?._raw; if (r?.nodeType && al.includes(r.nodeType)) { if (!r.properties) r.properties = {}; r.properties[attr.name] = value; cnt++; } w(c); } })(activeNodeInstance.value); scheduleRefreshLabels(); }
  else if (isCustom.value && selectedId) { const nd = nodeMap.get(selectedId); if (nd) { (function w(ns: MindNodeData[]) { for (const c of ns) { if (c.nodeType && al.includes(c.nodeType)) { if (!c.properties) c.properties = {}; c.properties[attr.name] = value; cnt++; } w(c.children || []); } })(nd.children || []); refreshCustom(); } }
  ElMessage.success(`已为 ${cnt} 个子孙节点设置「${attr.name}」`);
}

// ════════════════ 评论 ════════════════
function navigateToCommentNode(nodeId: string) {
  if (isSMM.value && smmInst?.renderer?.root) { function find(n: any): any { const r = n.nodeData?.data?._raw; if (r?.id === nodeId) return n; for (const c of (n.children || [])) { const f = find(c); if (f) return f; } return null; } const t = find(smmInst.renderer.root); if (t) { t.active(); smmInst.renderer.moveNodeToCenter(t); } }
  else if (isCustom.value && nodeId) selectCustomNode(nodeId);
}
function onCommentCountChanged(nodeId: string, count: number) {
  if (isSMM.value && smmInst?.renderer?.root) { (function wu(n: any) { const r = n.nodeData?.data?._raw; if (r && r.id === nodeId) r.commentCount = count; (n.children || []).forEach(wu); })(smmInst.renderer.root); scheduleRefreshLabels(); }
  const nd = nodeMap.get(nodeId); if (nd) { nd.commentCount = count; if (isCustom.value) refreshCustom(); }
}
watch(panelTab, (t) => { if (t === 'comments') nextTick(() => commentPanelRef.value?.refresh()); });

// ════════════════ 工具 ════════════════
function genId() { return 'n_' + Date.now() + '_' + Math.random().toString(36).substring(2, 10); }
function buildPropsStr(props: Record<string, any> | undefined): string {
  if (!props) return ''; const parts: string[] = [];
  for (const [k, v] of Object.entries(props)) { if (k === 'mark' || v == null || v === '') continue; if (Array.isArray(v) && v.length) parts.push(v.join(',')); else if (!Array.isArray(v)) parts.push(String(v)); }
  return parts.join(' | ');
}

// ════════════════ 清理 ════════════════
function cleanup() {
  if (cleanupMouse) { cleanupMouse(); cleanupMouse = null; }
  if (smmInst) { try { smmInst.destroy(); } catch {} smmInst = null; }
  if (g6Inst) { try { g6Inst.destroy(); } catch {} g6Inst = null; }
  svgCleanupFns.forEach(fn => fn()); svgCleanupFns = [];
  const el = renderBox.value; if (el) while (el.firstChild) el.removeChild(el.firstChild);
  activeNodeInstance.value = null; editForm.value = null; selectedId = null; customEdit.visible = false;
}
onBeforeUnmount(() => cleanup());
</script>

<style scoped>
.test-page { height: 100%; display: flex; flex-direction: column; background: #f5f5f5; }
.test-header { display: flex; align-items: center; gap: 12px; padding: 10px 20px; background: #fff; border-bottom: 1px solid #e8e8e8; flex-shrink: 0; flex-wrap: wrap; }
.stats-bar { display: flex; align-items: center; gap: 8px; padding: 6px 20px; background: #fafafa; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; font-size: 13px; color: #666; flex-wrap: wrap; }
.stats-bar b { color: #333; } .render-time { color: #1677ff; font-size: 15px; } .engine-label { color: #8c8c8c; font-style: italic; } .render-error { color: #f56c6c; margin-left: 12px; }
.editor-toolbar { display: flex; align-items: center; gap: 4px; padding: 4px 20px; background: #fff; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; }
.editor-body { flex: 1; position: relative; overflow: hidden; display: flex; }
.mm-area { flex: 1; position: relative; overflow: hidden; background: #fafafa; }
.render-box { position: absolute; top: 0; left: 0; right: 0; bottom: 0; }
.placeholder-text { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); color: #999; font-size: 14px; display: flex; align-items: center; z-index: 5; pointer-events: none; }
.right-panel { width: 320px; flex-shrink: 0; border-left: 1px solid #f0f0f0; background: #fff; display: flex; flex-direction: column; overflow: hidden; z-index: 100; box-shadow: -2px 0 8px rgba(0,0,0,0.06); }
.rp-header { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; }
.rp-body { flex: 1; overflow-y: auto; padding: 12px; }
.prop-field { margin-bottom: 10px; } .prop-field label { display: block; font-size: 12px; color: #999; margin-bottom: 3px; }
.tile-group { display: flex; flex-wrap: wrap; gap: 6px; }
.tile-tag { display: inline-flex; align-items: center; justify-content: center; padding: 2px 12px; border-radius: 16px; font-size: 12px; cursor: pointer; border: 1px solid #d9d9d9; background: #fff; color: #595959; transition: all 0.2s; user-select: none; line-height: 22px; }
.tile-tag:hover { border-color: #1677ff; color: #1677ff; }
.tile-tag.active { background: #1677ff; border-color: #1677ff; color: #fff; }
.tile-tag.priority-p0.active { background: #f5222d; border-color: #f5222d; } .tile-tag.priority-p1.active { background: #fa8c16; border-color: #fa8c16; } .tile-tag.priority-p2.active { background: #1677ff; border-color: #1677ff; } .tile-tag.priority-p3.active { background: #8c8c8c; border-color: #8c8c8c; }
.empty-hint { display: flex; align-items: center; justify-content: center; height: 200px; color: #ccc; font-size: 14px; }
.mm-zoom-bar { position: absolute; right: 16px; bottom: 20px; z-index: 60; display: flex; align-items: center; gap: 2px; background: #fff; border: 1px solid #e4e7ed; border-radius: 6px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); padding: 2px 4px; user-select: none; }
.mm-zoom-btn { width: 28px; height: 28px; border: none; background: transparent; font-size: 16px; font-weight: 600; cursor: pointer; border-radius: 4px; display: flex; align-items: center; justify-content: center; color: #595959; } .mm-zoom-btn:hover { background: #f5f5f5; color: #1677ff; }
.mm-zoom-label { font-size: 12px; min-width: 42px; text-align: center; cursor: pointer; color: #595959; line-height: 28px; } .mm-zoom-label:hover { color: #1677ff; }
.tips-bar { position: absolute; bottom: 16px; left: 50%; transform: translateX(-50%); background: rgba(0,0,0,0.45); color: #fff; font-size: 11px; padding: 4px 14px; border-radius: 4px; pointer-events: none; white-space: nowrap; }
.custom-edit-overlay { position: fixed; z-index: 2000; background: #fff; border: 2px solid #1677ff; border-radius: 4px; padding: 4px; min-width: 160px; max-width: 320px; box-shadow: 0 4px 16px rgba(0,0,0,0.15); }
.custom-edit-input { width: 100%; border: none; outline: none; font-size: 13px; line-height: 1.6; resize: vertical; font-family: inherit; padding: 4px; }
</style>

<style>
.smm-node .smm-node-shape { stroke: #c9ced6; stroke-width: 1px; }
.smm-node.active .smm-node-shape { stroke: #1677ff; stroke-width: 2px; }
.smm-node:hover .smm-node-shape { stroke: #4096ff; }
.smm-root-node .smm-node-shape { stroke: #1677ff; stroke-width: 2px; }
</style>
