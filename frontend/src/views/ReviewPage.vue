<template>
  <div class="review-wrap" style="height:100vh;display:flex;flex-direction:column">
    <!-- 顶栏 -->
    <div class="review-header">
      <div style="display:flex;align-items:center;gap:10px">
        <el-button text @click="$router.push('/cases')"><el-icon><ArrowLeft /></el-icon></el-button>
        <el-tag :type="headerStatusType">{{ headerStatusText }}</el-tag>
        <strong>{{ caseSet?.name }}</strong>
      </div>
      <div style="display:flex;align-items:center;gap:8px">
        <el-tooltip content="全部展开"><el-button text @click="mindMapInstance?.execCommand('EXPAND_ALL')"><el-icon><Expand /></el-icon></el-button></el-tooltip>
        <el-tooltip content="全部折叠"><el-button text @click="mindMapInstance?.execCommand('UNEXPAND_ALL')"><el-icon><Fold /></el-icon></el-button></el-tooltip>
        <el-button text v-if="myReview" @click="openReviewPanel">
          <el-icon><CircleCheck /></el-icon> 评审结果
        </el-button>
        <el-button text @click="openCommentPanel">
          <el-icon><ChatLineRound /></el-icon> 全部评论
        </el-button>
      </div>
    </div>

    <!-- 主体 -->
    <div class="review-body">
      <div class="tips-bar">左键点选 · 右键拖拽平移 · 滚轮上下滑动 · Ctrl+滚轮缩放</div>
      <div class="review-mm-wrap">
        <div ref="mindMapContainer" class="review-mm"></div>

        <!-- 加载遮罩 -->
        <div v-if="mindMapLoading" class="mm-loading-overlay">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <span style="margin-top:12px;color:#666;font-size:13px">加载思维导图中…</span>
        </div>

        <!-- 水平滚动条 -->
        <div class="mm-scrollbar mm-scrollbar-h"
          @click="(e: MouseEvent) => mindMapInstance?.scrollbar?.onClick(e, 'horizontal')">
          <div class="mm-scrollbar-thumb"
            :style="{ left: scrollbarH.left + '%', width: scrollbarH.width + '%' }"
            @mousedown.stop="(e: MouseEvent) => mindMapInstance?.scrollbar?.onMousedown(e, 'horizontal')"></div>
        </div>

        <!-- 垂直滚动条 -->
        <div class="mm-scrollbar mm-scrollbar-v"
          @click="(e: MouseEvent) => mindMapInstance?.scrollbar?.onClick(e, 'vertical')">
          <div class="mm-scrollbar-thumb"
            :style="{ top: scrollbarV.top + '%', height: scrollbarV.height + '%' }"
            @mousedown.stop="(e: MouseEvent) => mindMapInstance?.scrollbar?.onMousedown(e, 'vertical')"></div>
        </div>

        <!-- 右下角缩放控件 -->
        <div class="mm-zoom-bar">
          <button class="mm-zoom-btn" @click="zoomOut" title="缩小">−</button>
          <span class="mm-zoom-label" @click="zoomReset" title="重置缩放">{{ zoomLevel }}%</span>
          <button class="mm-zoom-btn" @click="zoomIn" title="放大">+</button>
        </div>
      </div>

      <!-- 右侧面板 -->
      <div v-if="rightPanelOpen" class="review-panel">
        <div class="rp-header">
          <el-radio-group v-model="panelTab" size="small">
            <el-radio-button label="node">节点</el-radio-button>
            <el-radio-button label="comments">评论</el-radio-button>
            <el-radio-button label="review">评审</el-radio-button>
          </el-radio-group>
          <el-button text size="small" @click="rightPanelOpen = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div class="rp-body">

          <!-- 节点属性 -->
          <template v-if="panelTab === 'node'">
            <template v-if="selectedNodeRaw">
              <div class="prop-field">
                <label>节点文本</label>
                <div>{{ selectedNodeRaw.text }}</div>
              </div>
              <div class="prop-field">
                <label>节点类型</label>
                <el-tag v-if="selectedNodeRaw.nodeType" :type="nodeTypeElType(selectedNodeRaw.nodeType)" size="small">
                  {{ NODE_TYPE_LABEL[selectedNodeRaw.nodeType] }}
                </el-tag>
                <span v-else style="color:#c0c4cc">未设置</span>
              </div>
              <div class="prop-field">
                <label>标记</label>
                <el-select :model-value="currentMark" @change="(v: any) => handleMark(v)" style="width:100%" size="small" :loading="markLoading" :disabled="markLoading">
                  <el-option value="NONE" label="无" />
                  <el-option value="待完成" label="待完成" />
                  <el-option value="待确认" label="待确认" />
                  <el-option value="待修改" label="待修改" />
                </el-select>
              </div>
              <template v-if="selectedNodeRaw.properties">
                <template v-for="[pKey, pVal] in Object.entries(selectedNodeRaw.properties || {})" :key="pKey">
                  <div v-if="pKey !== 'mark' && pVal !== undefined && pVal !== null && pVal !== ''" class="prop-field">
                    <label>{{ pKey }}</label>
                    <div>{{ Array.isArray(pVal) ? pVal.join(', ') : pVal }}</div>
                  </div>
                </template>
              </template>
            </template>
            <div v-else class="empty-hint">点击节点查看详情</div>
          </template>

          <!-- 评论 -->
          <template v-if="panelTab === 'comments'">
            <CommentPanel ref="commentPanelRef" :node-id="selectedNodeRaw?.id ?? null"
              :case-set-id="id" :show-all-tab="true"
              @navigate="navigateToCommentNode" @count-changed="onCommentCountChanged" />
          </template>

          <!-- 评审结果 -->
          <template v-if="panelTab === 'review'">
            <div v-for="r in reviewers" :key="r.id" class="reviewer-card">
              <div class="reviewer-name">{{ getUserName(r.reviewerId) }}</div>
              <el-select v-if="r.reviewerId === store.user?.id" :model-value="r.status"
                @change="(v: any) => updateReview(r.id, v)" style="width:100%" size="small">
                <el-option value="PENDING" label="未评审" />
                <el-option value="APPROVED" label="通过" />
                <el-option value="REJECTED" label="不通过" />
                <el-option value="NEED_MODIFY" label="待修改" />
              </el-select>
              <el-tag v-else :type="revElType(r.status)" size="small">{{ revLabel[r.status] || r.status }}</el-tag>
            </div>
          </template>

        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import MindMap from 'simple-mind-map';
import Scrollbar from 'simple-mind-map/src/plugins/Scrollbar';
import { caseSetApi, mindNodeApi, reviewApi, userApi, customAttributeApi } from '../api';
import { useAppStore } from '../stores/app';
import type { CaseSet, MindNodeData, ReviewAssignment, User, CustomAttribute } from '../types';
import { NODE_TYPE_LABEL, addAllCustomLabels, setupMouseOverrides } from '../composables/useMindMap';
import CommentPanel from '../components/CommentPanel.vue';

MindMap.usePlugin(Scrollbar);

const route = useRoute();
const store = useAppStore();
const id = String(route.params.caseSetId);
const mindMapContainer = ref<HTMLDivElement | null>(null);
let mindMapInstance: any = null;
const caseSet = ref<CaseSet | null>(null);

// 缩放 & 滚动条
const zoomLevel = ref(100);
const scrollbarH = reactive({ left: 0, width: 100 });
const scrollbarV = reactive({ top: 0, height: 100 });
function zoomIn() { mindMapInstance?.view?.enlarge(); }
function zoomOut() { mindMapInstance?.view?.narrow(); }
function zoomReset() { mindMapInstance?.view?.reset(); }
const reviewers = ref<ReviewAssignment[]>([]);
const users = ref<User[]>([]);
const selectedNodeRaw = ref<any>(null);
const commentPanelRef = ref<InstanceType<typeof CommentPanel> | null>(null);
const rightPanelOpen = ref(false);
const panelTab = ref<string>('node');
const mindMapLoading = ref(true);
const markLoading = ref(false);
const reviewLoading = ref(false);

const revLabel: Record<string, string> = { PENDING: '未评审', APPROVED: '通过', REJECTED: '不通过', NEED_MODIFY: '待修改' };
const myReview = computed(() => reviewers.value.find(r => r.reviewerId === store.user?.id));
const allApproved = computed(() => reviewers.value.length > 0 && reviewers.value.every(r => r.status === 'APPROVED'));

const headerStatusText = computed(() => {
  if (allApproved.value || caseSet.value?.status === 'APPROVED') return '审核通过';
  return ({ WRITING: '编写中', PENDING_REVIEW: '待评审', NO_REVIEW: '无需评审' } as any)[caseSet.value?.status || ''] || '';
});
const headerStatusType = computed((): any => {
  if (allApproved.value || caseSet.value?.status === 'APPROVED') return 'success';
  return ({ WRITING: 'primary', PENDING_REVIEW: 'warning' } as any)[caseSet.value?.status || ''] || 'info';
});
const currentMark = computed(() => {
  const m = selectedNodeRaw.value?.properties?.mark;
  return (m && m !== 'NONE') ? m : 'NONE';
});
function getUserName(uid: string) {
  const reviewer = reviewers.value.find(r => r.reviewerId === uid);
  if (reviewer?.reviewerName) return reviewer.reviewerName;
  return users.value.find(u => u.id === uid)?.displayName || uid;
}
function nodeTypeElType(t: string): any { return ({ TITLE: 'primary', PRECONDITION: 'info', STEP: 'success', EXPECTED: 'warning' } as any)[t] || ''; }
function revElType(s: string): any { return ({ PENDING: 'info', APPROVED: 'success', REJECTED: 'danger', NEED_MODIFY: 'warning' } as any)[s] || 'info'; }

watch(panelTab, (tab) => { if (tab === 'comments') nextTick(() => commentPanelRef.value?.refresh()); });

function nodeToMM(node: MindNodeData): any {
  const uid = node.id || ('n_' + Math.random().toString(36).substring(2, 10));
  return {
    data: {
      text: node.text,
      uid,
      _raw: {
        id: uid,
        text: node.text,
        nodeType: node.nodeType || null,
        sortOrder: node.sortOrder || 0,
        isRoot: node.isRoot,
        properties: node.properties,
        commentCount: node.commentCount || 0,
      },
    },
    children: (node.children || []).map(c => nodeToMM(c)),
  };
}

function handleNodeSelect(n: any) {
  const raw = n.nodeData?.data?._raw || n.getData?.('_raw') || n.data?._raw || null;
  selectedNodeRaw.value = raw ? { ...raw, properties: raw.properties ? { ...raw.properties } : {} } : null;
  if (!rightPanelOpen.value) { rightPanelOpen.value = true; panelTab.value = 'node'; }
  if (raw?.id && panelTab.value === 'comments') nextTick(() => commentPanelRef.value?.refresh());
}

function onCommentCountChanged(nodeId: string, count: number) {
  if (!mindMapInstance?.renderer?.root) return;
  (function walkUpdate(n: any) {
    const r = n.nodeData?.data?._raw;
    if (r && r.id === nodeId) r.commentCount = count;
    (n.children || []).forEach(walkUpdate);
  })(mindMapInstance.renderer.root);
  requestAnimationFrame(() => addAllCustomLabels(mindMapInstance));
}

function navigateToCommentNode(nodeId: string) {
  if (!mindMapInstance?.renderer?.root || !nodeId) return;
  function findByRawId(n: any): any {
    const raw = n.nodeData?.data?._raw;
    if (raw?.id === nodeId) return n;
    for (const c of (n.children || [])) { const found = findByRawId(c); if (found) return found; }
    return null;
  }
  const target = findByRawId(mindMapInstance.renderer.root);
  if (target) {
    target.active();
    mindMapInstance.renderer.moveNodeToCenter(target);
    handleNodeSelect(target);
  }
}

let cleanupMouseOverrides: (() => void) | null = null;

onMounted(async () => {
  const [csRes, treeRes, revRes, uRes] = await Promise.all([
    caseSetApi.get(id), mindNodeApi.tree(id), reviewApi.list(id), userApi.listAll()
  ]);
  caseSet.value = csRes.data;
  reviewers.value = revRes.data;
  users.value = uRes.data;
  await nextTick();
  const mmData = (treeRes.data as any).tree?.length ? nodeToMM((treeRes.data as any).tree[0]) : { data: { text: '空' }, children: [] };
  mindMapInstance = new MindMap({
    el: mindMapContainer.value!,
    data: mmData,
    theme: 'classic4', layout: 'logicalStructure', readonly: true, mousewheelAction: 'move',
    textAutoWrapWidth: 300,
    enableFreeDrag: false, useLeftKeySelectionRightKeyDrag: true,
    initRootNodePosition: [10, 'center'],
    enableNodeTransitionMove: false,
    createNodePrefixContent: false,
    isShowExpandNum: false,
    openPerformance: false,
    performanceConfig: { time: 250, padding: 200, removeNodeWhenOutCanvas: true },
    themeConfig: { second: { marginX: 80, marginY: 60 }, node: { marginX: 50, marginY: 50 } },
  });
  mindMapInstance.on('node_active', (_: any, nodes: any[]) => {
    if (nodes.length === 1) handleNodeSelect(nodes[0]);
    else if (nodes.length === 0) { selectedNodeRaw.value = null; }
  });
  mindMapInstance.on('node_click', (n: any) => { handleNodeSelect(n); });
  mindMapInstance.on('node_tree_render_end', () => {
    requestAnimationFrame(() => addAllCustomLabels(mindMapInstance));
    if (mindMapLoading.value) mindMapLoading.value = false;
  });
  mindMapInstance.on('scale', (scale: number) => { zoomLevel.value = Math.round(scale * 100); });
  mindMapInstance.on('scrollbar_change', (data: any) => {
    scrollbarH.left = data.horizontal.left;
    scrollbarH.width = data.horizontal.width;
    scrollbarV.top = data.vertical.top;
    scrollbarV.height = data.vertical.height;
  });
  nextTick(() => {
    if (mindMapContainer.value) {
      const rect = mindMapContainer.value.getBoundingClientRect();
      mindMapInstance.scrollbar?.setScrollBarWrapSize(rect.width, rect.height);
    }
  });
  cleanupMouseOverrides = setupMouseOverrides(mindMapContainer.value!, () => mindMapInstance);

  // 处理通知跳转：?nodeId=xxx&openComment=1 → 定位节点 + 打开当前节点评论
  const queryNodeId = route.query.nodeId as string;
  const openComment = route.query.openComment as string;
  if (queryNodeId) {
    setTimeout(() => {
      navigateToCommentNode(queryNodeId);
      if (openComment === '1') {
        rightPanelOpen.value = true;
        panelTab.value = 'comments';
        nextTick(() => {
          commentPanelRef.value?.switchTab('node');
          commentPanelRef.value?.refresh();
        });
      }
    }, 500);
  }
});
onUnmounted(() => { if (mindMapInstance) mindMapInstance.destroy(); cleanupMouseOverrides?.(); });

function openReviewPanel() { rightPanelOpen.value = true; panelTab.value = 'review'; }
function openCommentPanel() {
  rightPanelOpen.value = true; panelTab.value = 'comments';
  nextTick(() => commentPanelRef.value?.switchTab('all'));
}

async function handleMark(mark: string) {
  if (!selectedNodeRaw.value?.id || markLoading.value) return;
  markLoading.value = true;
  const nodeId = selectedNodeRaw.value.id;
  const cleanProps: Record<string, any> = {};
  for (const [k, v] of Object.entries(selectedNodeRaw.value.properties || {})) {
    if (k !== 'mark' && v !== undefined && v !== null) cleanProps[k] = v;
  }
  if (mark && mark !== 'NONE') cleanProps.mark = mark;
  try {
    await mindNodeApi.update(nodeId, { properties: cleanProps });
    if (mindMapInstance?.renderer?.root) {
      (function walkMark(n: any) {
        const r = n.nodeData?.data?._raw;
        if (r && r.id === nodeId) r.properties = { ...cleanProps };
        (n.children || []).forEach(walkMark);
      })(mindMapInstance.renderer.root);
    }
    selectedNodeRaw.value = { ...selectedNodeRaw.value, properties: { ...cleanProps } };
    requestAnimationFrame(() => addAllCustomLabels(mindMapInstance));
    ElMessage.success('标记已更新');
  } catch { ElMessage.error('标记更新失败'); } finally { markLoading.value = false; }
}

async function updateReview(rid: string, status: string) {
  if (reviewLoading.value) return;
  reviewLoading.value = true;
  try {
    const res = await reviewApi.updateStatus(rid, status);
    const data = res.data as any;
    if (data?.reviewers) reviewers.value = data.reviewers;
    else reviewers.value = (await reviewApi.list(id)).data;
    if (data?.caseSetStatus && caseSet.value) {
      caseSet.value.status = data.caseSetStatus;
    }
    if (data?.allApproved) { ElMessage.success('全部审核通过！'); }
    else ElMessage.success('评审状态已更新');
  } catch { ElMessage.error('更新失败'); } finally { reviewLoading.value = false; }
}
</script>

<style scoped>
.review-header { display:flex; align-items:center; justify-content:space-between; background:#fff; border-bottom:1px solid #e4e7ed; padding:0 16px; height:48px; flex-shrink:0; }
.review-body { flex:1; position:relative; overflow:hidden; display:flex; flex-direction:column; }
.review-mm-wrap { flex:1; position:relative; overflow:hidden; }
.review-mm { width:100%; height:100%; }
.tips-bar { padding:3px 12px; font-size:11px; color:#909399; background:#fafafa; border-bottom:1px solid #e4e7ed; flex-shrink:0; }
.review-panel { position:absolute; right:0; top:0; bottom:0; width:360px; background:#fff; border-left:1px solid #e4e7ed; display:flex; flex-direction:column; z-index:100; box-shadow:-2px 0 8px rgba(0,0,0,0.06); }
.rp-header { display:flex; justify-content:space-between; align-items:center; padding:10px 12px; border-bottom:1px solid #e4e7ed; flex-shrink:0; }
.rp-body { flex:1; overflow-y:auto; padding:12px; }
.prop-field { margin-bottom:12px; }
.prop-field label { display:block; font-size:12px; color:#909399; margin-bottom:3px; }
.empty-hint { display:flex; align-items:center; justify-content:center; height:160px; color:#c0c4cc; font-size:13px; }
.reviewer-card { margin-bottom:12px; padding:10px; border:1px solid #e4e7ed; border-radius:8px; }
.reviewer-name { margin-bottom:6px; font-weight:600; font-size:13px; }
.mm-loading-overlay {
  position: absolute; inset: 0; z-index: 200;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: rgba(250, 250, 250, 0.85); backdrop-filter: blur(2px);
}

/* 滚动条 */
.mm-scrollbar { position: absolute; z-index: 50; }
.mm-scrollbar-h { left: 0; bottom: 0; right: 14px; height: 10px; cursor: pointer; }
.mm-scrollbar-v { right: 0; top: 0; bottom: 14px; width: 10px; cursor: pointer; }
.mm-scrollbar-thumb { position: absolute; border-radius: 5px; background: rgba(0,0,0,0.15); transition: background 0.2s; }
.mm-scrollbar-thumb:hover, .mm-scrollbar-thumb:active { background: rgba(0,0,0,0.35); }
.mm-scrollbar-h .mm-scrollbar-thumb { height: 100%; min-width: 30px; }
.mm-scrollbar-v .mm-scrollbar-thumb { width: 100%; min-height: 30px; }

/* 缩放控件 */
.mm-zoom-bar {
  position: absolute; right: 16px; bottom: 20px; z-index: 60;
  display: flex; align-items: center; gap: 2px;
  background: #fff; border: 1px solid #e4e7ed; border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08); padding: 2px 4px; user-select: none;
}
.mm-zoom-btn {
  width: 28px; height: 28px; border: none; background: transparent;
  font-size: 16px; font-weight: 600; cursor: pointer; border-radius: 4px;
  display: flex; align-items: center; justify-content: center; color: #595959;
}
.mm-zoom-btn:hover { background: #f5f5f5; color: #1677ff; }
.mm-zoom-label { font-size: 12px; min-width: 42px; text-align: center; cursor: pointer; color: #595959; line-height: 28px; }
.mm-zoom-label:hover { color: #1677ff; }
</style>
