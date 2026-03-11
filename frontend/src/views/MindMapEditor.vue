<template>
  <a-layout style="height: 100vh">
    <!-- 顶部工具栏 -->
    <a-layout-header class="editor-header">
      <a-space>
        <a-button type="text" @click="$router.push('/cases')"><ArrowLeftOutlined /></a-button>
        <a-dropdown>
          <a-tag :color="statusColor" style="cursor: pointer">{{ statusLabel }}</a-tag>
          <template #overlay>
            <a-menu @click="(info: any) => handleStatusChange(info.key)">
              <a-menu-item key="WRITING">编写中</a-menu-item>
              <a-menu-item key="PENDING_REVIEW">待评审</a-menu-item>
              <a-menu-item key="NO_REVIEW">无需评审</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <strong>{{ caseSet?.name }}</strong>
        <a-typography-text type="secondary">({{ caseCount }}条用例)</a-typography-text>
      </a-space>
      <a-space>
        <a-tooltip title="查找替换"><a-button type="text" @click="showSearch = !showSearch"><SearchOutlined /></a-button></a-tooltip>
        <a-tooltip title="规范检查"><a-button type="text" @click="handleValidate"><ToolOutlined /></a-button></a-tooltip>
        <a-tooltip title="评论"><a-button type="text" @click="drawerType = 'comments'"><CommentOutlined /></a-button></a-tooltip>
        <a-button type="primary" :loading="saving" @click="handleSave"><SaveOutlined /> 保存</a-button>
        <a-tooltip title="历史版本"><a-button type="text" @click="loadVersions"><HistoryOutlined /></a-button></a-tooltip>
      </a-space>
    </a-layout-header>

    <!-- 查找替换栏 -->
    <div v-if="showSearch" class="search-bar">
      <a-input size="small" v-model:value="searchText" placeholder="查找" style="width: 180px" />
      <a-input size="small" v-model:value="replaceText" placeholder="替换" style="width: 180px" />
      <a-button size="small" @click="handleReplace">全部替换</a-button>
      <a-button size="small" type="text" @click="showSearch = false">关闭</a-button>
    </div>

    <!-- 主体 -->
    <a-layout style="flex: 1; overflow: hidden">
      <!-- 思维导图容器 -->
      <a-layout-content style="position: relative">
        <div ref="mindMapContainer" style="width: 100%; height: 100%"></div>
        <!-- 右键菜单 -->
        <div v-if="ctxMenu.visible" class="ctx-overlay"
          :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
          <div class="ctx-item" @click="execCmd('INSERT_CHILD_NODE')"><PlusOutlined /> 添加子节点</div>
          <div class="ctx-item" @click="execCmd('INSERT_NODE')"><PlusOutlined /> 添加同级节点</div>
          <div class="ctx-item" @click="execCmd('COPY_NODE')"><CopyOutlined /> 复制</div>
          <div class="ctx-item" @click="execCmd('PASTE_NODE')"><SnippetsOutlined /> 粘贴</div>
          <div class="ctx-item" @click="execCmd('CUT_NODE')"><ScissorOutlined /> 剪切</div>
          <div class="ctx-item danger" @click="execCmd('REMOVE_NODE')"><DeleteOutlined /> 删除</div>
        </div>
      </a-layout-content>

      <!-- 右侧属性面板 -->
      <div v-if="activeNodeData" class="prop-panel">
        <h4 style="margin-bottom: 12px; border-bottom: 1px solid #f0f0f0; padding-bottom: 8px">节点属性</h4>

        <div class="prop-field">
          <label>内容</label>
          <a-textarea :value="activeNodeData.text" @change="(e: any) => updateNodeText(e.target.value)"
            :auto-size="{ minRows: 2 }" />
        </div>

        <div class="prop-field">
          <label>节点类型</label>
          <a-select :value="activeNodeData.nodeType || undefined" @change="(v: any) => updateNodeType(v)"
            allow-clear :options="nodeTypeOptions" style="width: 100%" placeholder="可不设置" />
        </div>

        <a-divider style="margin: 12px 0" />
        <span style="color: #1677ff; font-weight: 600; font-size: 13px">动态属性</span>

        <!-- 根据项目自定义属性动态渲染 -->
        <template v-for="attr in filteredAttributes" :key="attr.id">
          <div class="prop-field">
            <label>{{ attr.name }}</label>
            <template v-if="attr.displayType === 'TILE'">
              <a-radio-group v-if="!attr.multiSelect"
                :value="getProperty(attr.name)"
                @change="(e: any) => setProperty(attr.name, e.target.value)">
                <a-radio-button v-for="o in attr.options" :key="o" :value="o" :class="priorityClass(attr.name, o)">{{ o }}</a-radio-button>
              </a-radio-group>
            </template>
            <template v-else>
              <a-select v-if="attr.multiSelect"
                mode="multiple" :value="getPropertyArray(attr.name)"
                @change="(v: any) => setProperty(attr.name, v)"
                :options="attr.options.map((o: string) => ({ value: o, label: o }))"
                style="width: 100%" />
              <a-select v-else
                :value="getProperty(attr.name) || undefined"
                @change="(v: any) => setProperty(attr.name, v)"
                allow-clear
                :options="attr.options.map((o: string) => ({ value: o, label: o }))"
                style="width: 100%" />
            </template>
          </div>
        </template>
      </div>
    </a-layout>

    <!-- 抽屉 -->
    <a-drawer :open="!!drawerType" @close="drawerType = null" :title="drawerTitle" :width="380">
      <template v-if="drawerType === 'validate'">
        <div v-if="validationErrors.length" style="margin-top: 8px">
          <a-typography-text type="danger">共{{ validationErrors.length }}条不符合规范</a-typography-text>
          <div style="margin-top: 8px; display: flex; gap: 8px; align-items: center">
            <a-button size="small" :disabled="valIdx <= 0" @click="valIdx--">上一个</a-button>
            <span>{{ valIdx + 1 }}/{{ validationErrors.length }}</span>
            <a-button size="small" :disabled="valIdx >= validationErrors.length - 1" @click="valIdx++">下一个</a-button>
          </div>
          <div style="margin-top: 8px; padding: 8px; background: #fff2f0; border-radius: 6px">
            <ExclamationCircleOutlined style="color: #ff4d4f; margin-right: 8px" />{{ validationErrors[valIdx]?.message }}
          </div>
        </div>
        <div v-else style="margin-top: 16px; text-align: center"><CheckCircleOutlined style="color: #52c41a; font-size: 32px" /><p>用例符合规范</p></div>
      </template>

      <template v-if="drawerType === 'comments'">
        <a-space style="margin-bottom: 12px">
          <a-button :type="commentTab === 'node' ? 'primary' : 'default'" size="small" @click="commentTab = 'node'; loadNodeComments()">当前节点</a-button>
          <a-button :type="commentTab === 'all' ? 'primary' : 'default'" size="small" @click="commentTab = 'all'; loadAllComments()">全部评论</a-button>
        </a-space>
        <a-list :data-source="commentTab === 'node' ? nodeComments : allComments" :locale="{ emptyText: '暂无评论' }">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta :description="item.content">
                <template #title><strong>{{ item.displayName }}</strong> <span style="color: #999; font-size: 11px">{{ item.createdAt }}</span></template>
              </a-list-item-meta>
              <template #actions v-if="commentTab === 'node'">
                <a-button type="link" size="small" @click="resolveComment(item.id)">{{ item.resolved ? '已解决' : '标记解决' }}</a-button>
              </template>
            </a-list-item>
          </template>
        </a-list>
        <div style="margin-top: 12px">
          <a-textarea v-model:value="newComment" placeholder="输入评论..." :auto-size="{ minRows: 2 }" />
          <a-button type="primary" size="small" style="margin-top: 8px" @click="addComment">发送</a-button>
        </div>
      </template>

      <template v-if="drawerType === 'history'">
        <a-list :data-source="versions" :locale="{ emptyText: '暂无历史版本' }">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta :title="`版本 ${item.id.substring(0,8)}`" :description="item.createdAt" />
              <template #actions><a-popconfirm title="确认恢复?" @confirm="restoreVersion(item.id)"><a-button type="link" size="small">恢复</a-button></a-popconfirm></template>
            </a-list-item>
          </template>
        </a-list>
      </template>
    </a-drawer>

    <!-- 评审人弹窗 -->
    <a-modal v-model:open="showReviewModal" title="选择评审人" @ok="submitReview">
      <a-select mode="multiple" style="width: 100%" v-model:value="selectedReviewers" placeholder="选择评审人"
        :options="users.filter((u: any) => u.id !== store.user?.id).map((u: any) => ({ value: u.id, label: u.displayName }))" />
    </a-modal>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  ArrowLeftOutlined, SaveOutlined, HistoryOutlined, SearchOutlined,
  ToolOutlined, CommentOutlined, CheckCircleOutlined, ExclamationCircleOutlined,
  PlusOutlined, DeleteOutlined, CopyOutlined, SnippetsOutlined, ScissorOutlined,
} from '@ant-design/icons-vue';
import MindMap from 'simple-mind-map';
import { caseSetApi, mindNodeApi, commentApi, caseHistoryApi, userApi, customAttributeApi } from '../api';
import { useAppStore } from '../stores/app';
import type { CaseSet, MindNodeData, CaseHistory, User, CustomAttribute } from '../types';

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const caseSetId = String(route.params.caseSetId);

const mindMapContainer = ref<HTMLDivElement | null>(null);
let mindMapInstance: any = null;

const caseSet = ref<CaseSet | null>(null);
const caseCount = ref(0);
const saving = ref(false);
const showSearch = ref(false);
const searchText = ref('');
const replaceText = ref('');

const activeNodeData = ref<MindNodeData | null>(null);
const activeNodeInstance = ref<any>(null);

const drawerType = ref<string | null>(null);
const drawerTitle = computed(() =>
  drawerType.value === 'validate' ? '规范检查' : drawerType.value === 'comments' ? '评论' : '历史版本'
);

const commentTab = ref<'node' | 'all'>('node');
const nodeComments = ref<any[]>([]);
const allComments = ref<any[]>([]);
const newComment = ref('');

const validationErrors = ref<{ nodeId: string; message: string }[]>([]);
const valIdx = ref(0);

const versions = ref<CaseHistory[]>([]);
const users = ref<User[]>([]);
const projectAttributes = ref<CustomAttribute[]>([]);
const showReviewModal = ref(false);
const selectedReviewers = ref<string[]>([]);

const ctxMenu = ref({ visible: false, x: 0, y: 0 });

let autoSaveTimer: ReturnType<typeof setInterval> | null = null;
let historyTimer: ReturnType<typeof setInterval> | null = null;

const statusLabel = computed(() => {
  const s = caseSet.value?.status;
  return s === 'WRITING' ? '编写中' : s === 'PENDING_REVIEW' ? '待评审' : '无需评审';
});
const statusColor = computed(() => {
  const s = caseSet.value?.status;
  return s === 'WRITING' ? 'processing' : s === 'PENDING_REVIEW' ? 'warning' : 'default';
});

const nodeTypeOptions = [
  { value: 'TITLE', label: '用例标题' },
  { value: 'PRECONDITION', label: '前置条件' },
  { value: 'STEP', label: '步骤' },
  { value: 'EXPECTED', label: '预期结果' },
];

const nodeTypeLabel: Record<string, string> = { TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' };
const nodeTypeColor: Record<string, string> = { TITLE: '#1677ff', PRECONDITION: '#722ed1', STEP: '#13c2c2', EXPECTED: '#52c41a' };

const filteredAttributes = computed(() => {
  if (!activeNodeData.value) return [];
  const nt = activeNodeData.value.nodeType;
  return projectAttributes.value.filter(a => {
    if (!a.nodeTypeLimit) return true;
    if (!nt) return false;
    return a.nodeTypeLimit.split(',').includes(nt);
  });
});

function getProperty(name: string): any {
  return activeNodeData.value?.properties?.[name] ?? null;
}
function getPropertyArray(name: string): string[] {
  const v = activeNodeData.value?.properties?.[name];
  return Array.isArray(v) ? v : [];
}
function setProperty(name: string, value: any) {
  if (!activeNodeData.value) return;
  if (!activeNodeData.value.properties) activeNodeData.value.properties = {};
  activeNodeData.value.properties[name] = value;
  syncNodeDisplay();
}

function priorityClass(attrName: string, value: string): string {
  if (attrName !== '优先级') return '';
  return `priority-${value.toLowerCase()}`;
}

// === Mind Map Data Conversion ===

function nodeToMM(node: MindNodeData): any {
  const tags: string[] = [];
  if (node.nodeType) tags.push(nodeTypeLabel[node.nodeType] || node.nodeType);
  if (node.properties) {
    const p = node.properties;
    if (p['优先级']) tags.push(String(p['优先级']));
    if (p['标签'] && Array.isArray(p['标签'])) tags.push(...p['标签']);
  }
  return {
    data: {
      text: node.text,
      uid: node.id || String(Math.random()),
      tag: tags.length ? tags : undefined,
      _raw: node,
    },
    children: (node.children || []).map(c => nodeToMM(c)),
  };
}

function mmToTree(mmNode: any): MindNodeData {
  const raw: MindNodeData = mmNode.data?._raw || {};
  return {
    id: raw.id,
    text: mmNode.data?.text ?? raw.text ?? '',
    nodeType: raw.nodeType || null,
    sortOrder: raw.sortOrder || 0,
    isRoot: raw.isRoot,
    properties: raw.properties || undefined,
    children: (mmNode.children || []).map((c: any) => mmToTree(c)),
  };
}

function getFullTree(): MindNodeData[] {
  if (!mindMapInstance) return [];
  const data = mindMapInstance.getData();
  const root = mmToTree(data);
  root.isRoot = 1;
  return [root];
}

function countValid(node: MindNodeData): number {
  let count = 0;
  function walk(n: MindNodeData, path: MindNodeData[]) {
    const p = [...path, n];
    if (!n.children || !n.children.length) {
      if (p.length >= 5) {
        const len = p.length;
        if (p[len-4].nodeType === 'TITLE' && p[len-3].nodeType === 'PRECONDITION'
            && p[len-2].nodeType === 'STEP' && p[len-1].nodeType === 'EXPECTED') count++;
      }
    } else { for (const c of n.children) walk(c, p); }
  }
  walk(node, []);
  return count;
}

function syncNodeDisplay() {
  if (!activeNodeInstance.value || !activeNodeData.value) return;
  const nd = activeNodeData.value;
  const tags: string[] = [];
  if (nd.nodeType) tags.push(nodeTypeLabel[nd.nodeType] || nd.nodeType);
  if (nd.properties?.['优先级']) tags.push(String(nd.properties['优先级']));
  if (nd.properties?.['标签'] && Array.isArray(nd.properties['标签'])) tags.push(...nd.properties['标签']);

  if (activeNodeInstance.value.data) {
    activeNodeInstance.value.data._raw = { ...nd };
  }

  mindMapInstance.execCommand('SET_NODE_TAG', activeNodeInstance.value, tags.length ? tags : null);
}

function updateNodeText(text: string) {
  if (!activeNodeData.value || !activeNodeInstance.value) return;
  activeNodeData.value.text = text;
  if (activeNodeInstance.value.data?._raw) activeNodeInstance.value.data._raw.text = text;
  mindMapInstance.execCommand('SET_NODE_TEXT', activeNodeInstance.value, text);
}

function updateNodeType(type: string | null) {
  if (!activeNodeData.value) return;
  activeNodeData.value.nodeType = type;
  if (activeNodeInstance.value?.data?._raw) activeNodeInstance.value.data._raw.nodeType = type;
  syncNodeDisplay();
}

function execCmd(cmd: string) {
  ctxMenu.value.visible = false;
  if (mindMapInstance) mindMapInstance.execCommand(cmd);
}

// === Data Loading ===

async function loadData() {
  const [csRes, treeRes] = await Promise.all([caseSetApi.get(caseSetId), mindNodeApi.tree(caseSetId)]);
  caseSet.value = csRes.data;
  const mmData = treeRes.data.length ? nodeToMM(treeRes.data[0]) : { data: { text: caseSet.value?.name || '新用例集' }, children: [] };
  if (mindMapInstance) mindMapInstance.setData(mmData);
  caseCount.value = treeRes.data.length > 0 ? countValid(treeRes.data[0]) : 0;
}

function initMindMap() {
  if (!mindMapContainer.value) return;

  mindMapInstance = new MindMap({
    el: mindMapContainer.value,
    data: { data: { text: '加载中...' }, children: [] },
    theme: 'classic4',
    layout: 'logicalStructure',
    mousewheelAction: 'zoom',
    enableFreeDrag: false,
    initRootNodePosition: ['center', 'center'],
    nodeTextEdit: true,
    enableShortcutOnlyWhenMouseInSvg: true,
    beforeTextEdit: (node: any) => {
      return true;
    },
  });

  mindMapInstance.on('node_active', (_: any, nodes: any[]) => {
    if (nodes.length > 0) {
      const node = nodes[0];
      activeNodeInstance.value = node;
      const raw = node.data?._raw;
      if (raw) {
        activeNodeData.value = { ...raw };
      } else {
        activeNodeData.value = {
          text: node.data?.text || '', nodeType: null, sortOrder: 0,
          properties: {}, children: [],
        };
      }
    } else {
      activeNodeData.value = null;
      activeNodeInstance.value = null;
    }
  });

  mindMapInstance.on('node_text_edit_end', (node: any, text: string) => {
    if (node.data?._raw) {
      node.data._raw.text = text;
    }
    if (activeNodeData.value && activeNodeInstance.value === node) {
      activeNodeData.value.text = text;
    }
  });

  mindMapInstance.on('data_change', () => {
    const tree = getFullTree();
    if (tree.length > 0) caseCount.value = countValid(tree[0]);
  });

  mindMapInstance.on('node_contextmenu', (e: MouseEvent, _node: any) => {
    e.preventDefault();
    const rect = mindMapContainer.value!.getBoundingClientRect();
    ctxMenu.value = { visible: true, x: e.clientX - rect.left, y: e.clientY - rect.top };
  });

  document.addEventListener('click', () => { ctxMenu.value.visible = false; });

  mindMapInstance.on('node_dblclick', (_node: any) => {
    // allow default text editing
  });
}

// === Actions ===

async function handleSave() {
  saving.value = true;
  try {
    const tree = getFullTree();
    await mindNodeApi.batchSave(caseSetId, tree);
    caseCount.value = tree.length > 0 ? countValid(tree[0]) : 0;
    await caseHistoryApi.save(caseSetId);
    message.success('保存成功');
  } catch { /* handled */ } finally { saving.value = false; }
}

async function autoSave() {
  try {
    const tree = getFullTree();
    if (tree.length > 0) await mindNodeApi.batchSave(caseSetId, tree);
  } catch { /* silent */ }
}

function handleReplace() {
  if (!searchText.value || !mindMapInstance) return;
  const data = mindMapInstance.getData();
  function walk(n: any) {
    if (n.data?.text) n.data.text = n.data.text.replaceAll(searchText.value, replaceText.value);
    if (n.data?._raw?.text) n.data._raw.text = n.data._raw.text.replaceAll(searchText.value, replaceText.value);
    if (n.children) n.children.forEach(walk);
  }
  walk(data);
  mindMapInstance.setData(data);
  message.success('替换完成');
}

async function handleValidate() {
  drawerType.value = 'validate';
  const res = await caseSetApi.validate(caseSetId);
  if (res.data.valid) {
    validationErrors.value = [];
    message.success('用例符合规范');
  } else {
    validationErrors.value = res.data.errors.map(e => ({ nodeId: e.nodeId, message: e.message }));
    valIdx.value = 0;
    message.warning(`共${res.data.errorCount}条不符合规范`);
  }
}

async function handleStatusChange(status: string) {
  if (status === 'PENDING_REVIEW') { showReviewModal.value = true; return; }
  await caseSetApi.updateStatus(caseSetId, status);
  if (caseSet.value) caseSet.value.status = status;
  message.success('状态已更新');
}

async function submitReview() {
  if (!selectedReviewers.value.length) { message.error('请选择评审人'); return; }
  try {
    await caseSetApi.updateStatus(caseSetId, 'PENDING_REVIEW', selectedReviewers.value);
    if (caseSet.value) caseSet.value.status = 'PENDING_REVIEW';
    showReviewModal.value = false;
    message.success('已提交评审');
  } catch { /* handled */ }
}

async function loadNodeComments() {
  if (!activeNodeData.value?.id) return;
  nodeComments.value = (await commentApi.nodeComments(activeNodeData.value.id)).data;
}
async function loadAllComments() {
  allComments.value = (await commentApi.allComments(caseSetId)).data;
}
async function addComment() {
  if (!newComment.value.trim() || !activeNodeData.value?.id) return;
  await commentApi.add(activeNodeData.value.id, caseSetId, newComment.value.trim());
  newComment.value = '';
  loadNodeComments();
}
async function resolveComment(id: string) {
  await commentApi.resolve(id);
  loadNodeComments();
}

async function loadVersions() {
  drawerType.value = 'history';
  versions.value = (await caseHistoryApi.list(caseSetId, 10)).data;
}
async function restoreVersion(id: string) {
  await caseHistoryApi.restore(id);
  message.success('版本已恢复');
  loadData();
  drawerType.value = null;
}

onMounted(async () => {
  const [uRes, attrRes] = await Promise.all([
    userApi.listAll(),
    store.currentProject ? customAttributeApi.list(store.currentProject.id) : Promise.resolve({ data: [] }),
  ]);
  users.value = uRes.data;
  projectAttributes.value = (attrRes as any).data || [];
  await nextTick();
  initMindMap();
  await loadData();
  autoSaveTimer = setInterval(autoSave, 10000);
  historyTimer = setInterval(() => caseHistoryApi.save(caseSetId).catch(() => {}), 15 * 60 * 1000);
});

onUnmounted(() => {
  if (autoSaveTimer) clearInterval(autoSaveTimer);
  if (historyTimer) clearInterval(historyTimer);
  if (mindMapInstance) { mindMapInstance.destroy(); mindMapInstance = null; }
});
</script>

<style scoped>
.editor-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #f0f0f0; padding: 0 16px;
  height: 48px; line-height: 48px;
}
.search-bar {
  background: #fff; padding: 8px 16px; border-bottom: 1px solid #f0f0f0;
  display: flex; gap: 8px; align-items: center;
}
.prop-panel {
  width: 300px; background: #fff; border-left: 1px solid #f0f0f0;
  padding: 16px; overflow-y: auto; flex-shrink: 0;
}
.prop-field { margin-bottom: 12px; }
.prop-field label { display: block; font-size: 12px; color: #999; margin-bottom: 4px; }
.ctx-overlay {
  position: absolute; background: #fff; border: 1px solid #e8e8e8;
  border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.12);
  padding: 4px 0; z-index: 1000; min-width: 150px;
}
.ctx-item {
  padding: 6px 16px; cursor: pointer; font-size: 13px;
  display: flex; align-items: center; gap: 8px;
}
.ctx-item:hover { background: #f5f5f5; }
.ctx-item.danger { color: #ff4d4f; }
</style>

<style>
/* 全局样式 - simple-mind-map 节点边框 */
.smm-node .smm-node-shape {
  stroke: #d9d9d9 !important;
  stroke-width: 1px !important;
}
.smm-node.active .smm-node-shape {
  stroke: #1677ff !important;
  stroke-width: 2px !important;
}
.smm-node .smm-node-tag-item {
  border-radius: 3px !important;
}
</style>
