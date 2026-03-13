<template>
  <a-layout style="height: 100vh">
    <div class="review-header">
      <a-space>
        <a-button type="text" @click="$router.push('/cases')"><ArrowLeftOutlined /></a-button>
        <a-tag :color="headerStatusColor">{{ headerStatusText }}</a-tag>
        <strong>{{ caseSet?.name }}</strong>
      </a-space>
      <a-space>
        <a-button v-if="myReview" type="text" @click="openReviewPanel"><CheckCircleOutlined /> 评审结果</a-button>
        <a-button type="text" @click="openCommentPanel"><CommentOutlined /> 全部评论</a-button>
      </a-space>
    </div>
    <div class="review-body">
      <div class="tips-bar">左键点选 · Ctrl+左键拖拽平移 · 滚轮缩放</div>
      <div ref="mindMapContainer" class="review-mm"></div>
      <div v-if="rightPanelOpen" class="review-panel">
        <div class="rp-header">
          <a-segmented v-model:value="panelTab" :options="panelTabs" size="small" />
          <a-button type="text" size="small" @click="rightPanelOpen = false">×</a-button>
        </div>
        <div class="rp-body">

          <!-- 节点属性 -->
          <template v-if="panelTab === 'node'">
            <template v-if="selectedNodeRaw">
              <div class="prop-field"><label>节点文本</label><div>{{ selectedNodeRaw.text }}</div></div>
              <div class="prop-field"><label>节点类型</label>
                <a-tag v-if="selectedNodeRaw.nodeType" :color="NODE_TYPE_COLOR[selectedNodeRaw.nodeType]">{{ NODE_TYPE_LABEL[selectedNodeRaw.nodeType] }}</a-tag>
                <span v-else style="color: #ccc">未设置</span>
              </div>
              <div class="prop-field"><label>标记</label>
                <a-select :value="currentMark" @change="(v: any) => handleMark(v)" style="width: 100%"
                  :options="markOptions" />
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
            <CommentPanel
              ref="commentPanelRef"
              :node-id="selectedNodeRaw?.id ?? null"
              :case-set-id="id"
              :show-all-tab="true"
              @navigate="navigateToCommentNode"
              @count-changed="onCommentCountChanged"
            />
          </template>

          <!-- 评审结果 -->
          <template v-if="panelTab === 'review'">
            <div v-for="r in reviewers" :key="r.id" class="reviewer-card">
              <div class="reviewer-name">{{ getUserName(r.reviewerId) }}</div>
              <a-select v-if="r.reviewerId === store.user?.id" :value="r.status" @change="(v: any) => updateReview(r.id, v)" style="width: 100%"
                :options="[{value:'PENDING',label:'未评审'},{value:'APPROVED',label:'通过'},{value:'REJECTED',label:'不通过'},{value:'NEED_MODIFY',label:'待修改'}]" />
              <a-tag v-else :color="revColor[r.status] || 'default'">{{ revLabel[r.status] || r.status }}</a-tag>
            </div>
          </template>

        </div>
      </div>
    </div>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import { ArrowLeftOutlined, CheckCircleOutlined, CommentOutlined } from '@ant-design/icons-vue';
import MindMap from 'simple-mind-map';
import { caseSetApi, mindNodeApi, reviewApi, userApi, customAttributeApi } from '../api';
import { useAppStore } from '../stores/app';
import type { CaseSet, MindNodeData, ReviewAssignment, User, CustomAttribute } from '../types';
import { NODE_TYPE_LABEL, NODE_TYPE_COLOR, addAllCustomLabels, setupMouseOverrides } from '../composables/useMindMap';
import CommentPanel from '../components/CommentPanel.vue';

const route = useRoute();
const store = useAppStore();
const id = String(route.params.caseSetId);

const mindMapContainer = ref<HTMLDivElement | null>(null);
let mindMapInstance: any = null;
let initialViewSet = false;
const caseSet = ref<CaseSet | null>(null);
const reviewers = ref<ReviewAssignment[]>([]);
const users = ref<User[]>([]);
const projectAttrs = ref<CustomAttribute[]>([]);
const selectedNodeRaw = ref<any>(null);
const selectedNodeInst = ref<any>(null);
const commentPanelRef = ref<InstanceType<typeof CommentPanel> | null>(null);
const rightPanelOpen = ref(false);
const panelTab = ref<string>('node');
const panelTabs = [
  { value: 'node', label: '节点' },
  { value: 'comments', label: '评论' },
  { value: 'review', label: '评审' },
];
const markOptions = [{value:'NONE',label:'无'},{value:'PENDING',label:'待完成'},{value:'TO_CONFIRM',label:'待确认'},{value:'TO_MODIFY',label:'待修改'}];
let cleanupMouseOverrides: (() => void) | null = null;

const revLabel: Record<string, string> = { PENDING: '未评审', APPROVED: '通过', REJECTED: '不通过', NEED_MODIFY: '待修改' };
const revColor: Record<string, string> = { PENDING: 'default', APPROVED: 'success', REJECTED: 'error', NEED_MODIFY: 'warning' };

const myReview = computed(() => reviewers.value.find(r => r.reviewerId === store.user?.id));
const allApproved = computed(() => reviewers.value.length > 0 && reviewers.value.every(r => r.status === 'APPROVED'));
const headerStatusText = computed(() => {
  if (allApproved.value || caseSet.value?.status === 'APPROVED') return '审核通过';
  const m: Record<string, string> = { WRITING: '编写中', PENDING_REVIEW: '待评审', NO_REVIEW: '无需评审' };
  return m[caseSet.value?.status || ''] || caseSet.value?.status || '';
});
const headerStatusColor = computed(() => {
  if (allApproved.value || caseSet.value?.status === 'APPROVED') return 'success';
  const m: Record<string, string> = { WRITING: 'processing', PENDING_REVIEW: 'warning' };
  return m[caseSet.value?.status || ''] || 'default';
});
const currentMark = computed(() => {
  const m = selectedNodeRaw.value?.properties?.mark;
  return (m && m !== 'NONE') ? m : 'NONE';
});
function getUserName(uid: string) { return users.value.find(u => u.id === uid)?.displayName || uid; }

// === Tab 切换自动加载 ===
watch(panelTab, (tab) => {
  if (tab === 'comments') nextTick(() => commentPanelRef.value?.refresh());
});

// === 思维导图数据转换 ===
function nodeToMM(node: MindNodeData): any {
  return {
    data: { text: node.text, uid: node.id || ('n_' + Math.random().toString(36).substring(2, 10)), _raw: { ...node, children: undefined } },
    children: (node.children || []).map(c => nodeToMM(c)),
  };
}

// === 节点选中 ===
function getRawFromInst(n: any) {
  return n.nodeData?.data?._raw || n.getData?.('_raw') || n.data?._raw || null;
}

function handleNodeSelect(n: any) {
  selectedNodeInst.value = n;
  const raw = getRawFromInst(n);
  selectedNodeRaw.value = raw ? { ...raw, properties: raw.properties ? { ...raw.properties } : {} } : null;
  if (!rightPanelOpen.value) { rightPanelOpen.value = true; panelTab.value = 'node'; }
  if (raw?.id && panelTab.value === 'comments') nextTick(() => commentPanelRef.value?.refresh());
}

// === 评论角标刷新（由 CommentPanel 触发）===
function onCommentCountChanged(nodeId: string, count: number) {
  if (!mindMapInstance?.renderer?.root) return;
  (function walkUpdate(n: any) {
    const r = n.nodeData?.data?._raw;
    if (r && r.id === nodeId) r.commentCount = count;
    (n.children || []).forEach(walkUpdate);
  })(mindMapInstance.renderer.root);
  requestAnimationFrame(() => addAllCustomLabels(mindMapInstance));
}

// === 导航到评论节点 ===
function navigateToCommentNode(nodeId: string) {
  if (!mindMapInstance?.renderer?.root || !nodeId) return;
  function findByRawId(n: any): any {
    const raw = n.nodeData?.data?._raw;
    if (raw?.id === nodeId) return n;
    for (const c of (n.children || [])) { const found = findByRawId(c); if (found) return found; }
    return null;
  }
  const target = findByRawId(mindMapInstance.renderer.root);
  if (target) { target.active(); mindMapInstance.renderer.moveNodeToCenter(target); }
}

// === 初始化 ===
onMounted(async () => {
  const [csRes, treeRes, revRes] = await Promise.all([caseSetApi.get(id), mindNodeApi.tree(id), reviewApi.list(id)]);
  caseSet.value = csRes.data;
  reviewers.value = revRes.data;
  users.value = (await userApi.listAll()).data;
  if (caseSet.value?.projectId) projectAttrs.value = (await customAttributeApi.list(caseSet.value.projectId)).data;
  await nextTick();
  mindMapInstance = new MindMap({
    el: mindMapContainer.value!,
    data: treeRes.data.length ? nodeToMM(treeRes.data[0]) : { data: { text: '空' }, children: [] },
    theme: 'classic4', layout: 'logicalStructure', readonly: true, mousewheelAction: 'zoom',
    enableFreeDrag: false, useLeftKeySelectionRightKeyDrag: true,
    themeConfig: {
      second: { marginX: 80, marginY: 60 },
      node: { marginX: 50, marginY: 50 },
    },
  });
  mindMapInstance.on('node_active', (_: any, nodes: any[]) => {
    if (nodes.length === 1) handleNodeSelect(nodes[0]);
    else if (nodes.length === 0) { selectedNodeRaw.value = null; selectedNodeInst.value = null; }
  });
  mindMapInstance.on('node_click', (n: any) => { handleNodeSelect(n); });
  mindMapInstance.on('node_tree_render_end', () => {
    requestAnimationFrame(() => addAllCustomLabels(mindMapInstance));
    if (!initialViewSet && mindMapContainer.value) {
      initialViewSet = true;
      const W = mindMapContainer.value.clientWidth;
      const rootW = mindMapInstance.renderer?.root?.width || 0;
      // 根节点右边缘距容器右侧 100px
      // 初始时根节点中心在 W/2，向右平移 dx 使右边缘到 W-100
      // 获取屏幕宽度
      const screenWidth = window.innerWidth;
      const dx = 0 - screenWidth/2 + rootW/2;
      mindMapInstance.view.translateXY(dx, 0);
    }
  });
  cleanupMouseOverrides = setupMouseOverrides(mindMapContainer.value!, () => mindMapInstance);
});
onUnmounted(() => {
  if (mindMapInstance) mindMapInstance.destroy();
  cleanupMouseOverrides?.();
});

function openReviewPanel() { rightPanelOpen.value = true; panelTab.value = 'review'; }
function openCommentPanel() {
  rightPanelOpen.value = true;
  panelTab.value = 'comments';
  nextTick(() => commentPanelRef.value?.switchTab('all'));
}

// === 标记 ===
async function handleMark(mark: string) {
  if (!selectedNodeRaw.value?.id) return;
  const nodeId = selectedNodeRaw.value.id;

  // 构建干净的 properties (不含 undefined)
  const cleanProps: Record<string, any> = {};
  for (const [k, v] of Object.entries(selectedNodeRaw.value.properties || {})) {
    if (k !== 'mark' && v !== undefined && v !== null) cleanProps[k] = v;
  }
  if (mark && mark !== 'NONE') cleanProps.mark = mark;

  try {
    // 1) 发请求保存到数据库
    await mindNodeApi.update(nodeId, { properties: cleanProps });

    // 2) 更新渲染树中对应节点的 _raw
    if (mindMapInstance?.renderer?.root) {
      (function walkMark(n: any) {
        const r = n.nodeData?.data?._raw;
        if (r && r.id === nodeId) {
          r.properties = { ...cleanProps };
        }
        (n.children || []).forEach(walkMark);
      })(mindMapInstance.renderer.root);
    }

    // 3) 更新面板显示
    selectedNodeRaw.value = { ...selectedNodeRaw.value, properties: { ...cleanProps } };

    // 4) 重绘边框和标签
    requestAnimationFrame(() => addAllCustomLabels(mindMapInstance));
    message.success('标记已更新');
  } catch {
    message.error('标记更新失败');
  }
}


// === 评审 ===
async function updateReview(rid: string, status: string) {
  const res = await reviewApi.updateStatus(rid, status);
  const data = res.data as any;
  if (data?.reviewers) reviewers.value = data.reviewers;
  else reviewers.value = (await reviewApi.list(id)).data;
  if (data?.allApproved) { message.success('全部审核通过！'); caseSet.value = (await caseSetApi.get(id)).data; }
  else message.success('评审状态已更新');
}
</script>

<style scoped>
.review-header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #f0f0f0; padding: 0 16px; height: 48px; }
.review-body { flex: 1; position: relative; overflow: hidden; display: flex; flex-direction: column; }
.review-mm { flex: 1; width: 100%; }
.tips-bar { padding: 3px 12px; font-size: 11px; color: #aaa; background: #fafafa; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; }
.review-panel {
  position: absolute; right: 0; top: 0; bottom: 0;
  width: 360px; background: #fff; border-left: 1px solid #f0f0f0;
  display: flex; flex-direction: column; z-index: 100; box-shadow: -2px 0 8px rgba(0,0,0,0.06);
}
.rp-header { display: flex; justify-content: space-between; align-items: center; padding: 10px 12px; border-bottom: 1px solid #f0f0f0; flex-shrink: 0; }
.rp-body { flex: 1; overflow-y: auto; padding: 12px; }
.prop-field { margin-bottom: 10px; }
.prop-field label { display: block; font-size: 12px; color: #999; margin-bottom: 3px; }
.empty-hint { display: flex; align-items: center; justify-content: center; height: 160px; color: #bbb; font-size: 13px; }
.reviewer-card { margin-bottom: 12px; padding: 10px; border: 1px solid #f0f0f0; border-radius: 8px; }
.reviewer-name { margin-bottom: 6px; font-weight: 600; }

/* ===== 评论样式 ===== */
.comments-section { overflow-y: auto; }
.cmt-card { margin-bottom: 4px; padding: 10px 0; border-bottom: 1px solid #f5f5f5; }
.cmt-card:last-child { border-bottom: none; }
.cmt-node-link {
  font-size: 12px; color: #1677ff; cursor: pointer; margin-bottom: 6px;
  padding: 4px 8px; background: #f0f5ff; border-radius: 4px; display: flex; align-items: flex-start;
  overflow: hidden; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; line-height: 1.5;
}
.cmt-node-link:hover { background: #e6f0ff; }
.cmt-main { display: flex; gap: 8px; }
.cmt-avatar {
  width: 28px; height: 28px; border-radius: 50%; background: #1677ff; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 600; flex-shrink: 0;
}
.cmt-avatar.sm { width: 22px; height: 22px; font-size: 10px; background: #8c8c8c; }
.cmt-content { flex: 1; min-width: 0; }
.cmt-meta { display: flex; align-items: baseline; gap: 6px; }
.cmt-meta strong { font-size: 13px; }
.cmt-time { font-size: 11px; color: #999; }
.cmt-text { font-size: 13px; color: #333; margin: 3px 0; white-space: pre-wrap; word-break: break-all; line-height: 1.5; }
.cmt-actions { display: flex; gap: 10px; margin-top: 2px; }
.cmt-actions span { font-size: 12px; color: #8c8c8c; cursor: pointer; }
.cmt-actions span:hover { color: #1677ff; }
.cmt-actions .danger { color: #ff4d4f; }
.cmt-actions .danger:hover { color: #cf1322; }
.cmt-actions .resolve-btn { color: #1677ff; }
.cmt-actions .resolve-btn:hover { color: #0958d9; }
.cmt-actions .resolved { color: #52c41a; }
.cmt-replies { padding-left: 36px; margin-top: 6px; }
.cmt-reply { margin-top: 6px; }
.cmt-reply-box { padding-left: 36px; margin-top: 6px; }
.cmt-input-area { margin-top: 8px; border-top: 1px solid #f0f0f0; padding-top: 8px; flex-shrink: 0; }
</style>
