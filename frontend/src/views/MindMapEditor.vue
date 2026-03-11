<template>
  <a-layout style="height: 100vh; overflow: hidden">
    <!-- 顶部工具栏 -->
    <div class="editor-header">
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
        <span style="color: #999; font-size: 12px">({{ caseCount }}条用例)</span>
      </a-space>
      <a-space>
        <a-tooltip title="添加子节点(Tab)"><a-button type="text" @click="execCmd('INSERT_CHILD_NODE')"><PlusOutlined /></a-button></a-tooltip>
        <a-tooltip title="添加同级(Enter)"><a-button type="text" @click="execCmd('INSERT_NODE')"><PlusSquareOutlined /></a-button></a-tooltip>
        <a-tooltip title="删除(Delete)"><a-button type="text" danger @click="execCmd('REMOVE_NODE')"><DeleteOutlined /></a-button></a-tooltip>
        <a-divider type="vertical" />
        <a-tooltip title="查找替换"><a-button type="text" @click="toggleSearch"><SearchOutlined /></a-button></a-tooltip>
        <a-tooltip title="规范检查"><a-button type="text" @click="openValidation"><ToolOutlined /></a-button></a-tooltip>
        <a-tooltip title="评论"><a-button type="text" @click="openComments"><CommentOutlined /></a-button></a-tooltip>
        <a-button type="primary" :loading="saving" @click="handleSave"><SaveOutlined /> 保存</a-button>
        <a-tooltip title="历史版本"><a-button type="text" @click="openHistory"><HistoryOutlined /></a-button></a-tooltip>
      </a-space>
    </div>

    <!-- 查找替换栏 -->
    <div v-if="showSearch" class="search-bar">
      <a-input size="small" v-model:value="searchText" placeholder="查找" style="width: 150px"
        @pressEnter="findNext" @change="onSearchChange" />
      <a-input size="small" v-model:value="replaceText" placeholder="替换" style="width: 150px" />
      <a-button size="small" @click="findPrev">上一个</a-button>
      <a-button size="small" @click="findNext">下一个</a-button>
      <a-button size="small" @click="replaceOneFn">替换</a-button>
      <a-button size="small" @click="replaceAllFn">全部替换</a-button>
      <span v-if="searchMatches.length" style="color: #666; font-size: 12px; min-width: 50px">
        {{ searchIdx + 1 }}/{{ searchMatches.length }}
      </span>
      <a-button size="small" type="text" @click="showSearch = false; searchMatches = []">×</a-button>
    </div>

    <!-- 主体: 思维导图 + 可选右侧面板 -->
    <div class="editor-body">
      <!-- 思维导图区域 -->
      <div class="mm-area">
        <div ref="mindMapContainer" style="width: 100%; height: 100%"></div>
        <div class="tips-bar">
          左键点选 | 左键拖拽框选 | 右键拖拽平移 | 滚轮缩放 | 双击编辑
        </div>
      </div>

      <!-- 右侧面板 (按需出现) -->
      <div v-if="rightPanelOpen" class="right-panel">
        <div class="rp-header">
          <a-radio-group v-model:value="panelTab" button-style="solid" size="small">
            <a-radio-button value="props">属性</a-radio-button>
            <a-radio-button value="validate">检查</a-radio-button>
            <a-radio-button value="comments">评论</a-radio-button>
          </a-radio-group>
          <a-button size="small" type="text" @click="closePanel"><CloseOutlined /></a-button>
        </div>

        <div class="rp-body">
          <!-- ===== 属性 Tab ===== -->
          <template v-if="panelTab === 'props'">
            <template v-if="editForm">
              <div class="prop-field">
                <label>内容</label>
                <a-textarea v-model:value="editForm.text" :auto-size="{ minRows: 2 }" placeholder="节点文本" />
              </div>
              <div class="prop-field">
                <label>节点类型</label>
                <a-select v-model:value="editForm.nodeType" allow-clear
                  :options="nodeTypeOptions" style="width: 100%" placeholder="可不设置" />
              </div>
              <a-divider style="margin: 8px 0" />
              <div style="color: #1677ff; font-weight: 600; font-size: 12px; margin-bottom: 8px">动态属性</div>
              <template v-for="attr in filteredEditAttrs" :key="attr.id">
                <div class="prop-field">
                  <label>{{ attr.name }}</label>
                  <a-radio-group v-if="attr.displayType === 'TILE' && !attr.multiSelect"
                    :value="editForm?.properties[attr.name]"
                    @change="(e: any) => { if (editForm) editForm.properties[attr.name] = e.target.value }">
                    <a-radio-button v-for="o in attr.options" :key="o" :value="o"
                      :class="attr.name === '优先级' ? `priority-${o.toLowerCase()}` : ''">{{ o }}</a-radio-button>
                  </a-radio-group>
                  <a-select v-else-if="attr.multiSelect" mode="multiple"
                    :value="Array.isArray(editForm?.properties[attr.name]) ? editForm?.properties[attr.name] : []"
                    @change="(v: any) => { if (editForm) editForm.properties[attr.name] = v }"
                    :options="attr.options.map((o: string) => ({ value: o, label: o }))" style="width: 100%" />
                  <a-select v-else
                    :value="editForm?.properties[attr.name] || undefined"
                    @change="(v: any) => { if (editForm) editForm.properties[attr.name] = v }"
                    allow-clear :options="attr.options.map((o: string) => ({ value: o, label: o }))" style="width: 100%" />
                </div>
              </template>
              <a-button type="primary" block style="margin-top: 12px" @click="confirmEdit">
                <CheckOutlined /> 确定
              </a-button>
            </template>
            <div v-else class="empty-hint">选择节点查看属性</div>
          </template>

          <!-- ===== 规范检查 Tab ===== -->
          <template v-if="panelTab === 'validate'">
            <a-button type="primary" size="small" block style="margin-bottom: 12px" @click="runValidation">
              <ToolOutlined /> 开始检查
            </a-button>
            <div v-if="!valErrors.length && valChecked" style="text-align: center; padding: 16px; color: #52c41a">
              <CheckCircleOutlined style="font-size: 24px" />
              <p>所有分支符合规范</p>
            </div>
            <div v-if="valErrors.length">
              <div style="color: #ff4d4f; font-size: 12px; margin-bottom: 8px">
                共{{ valErrors.length }}条错误，点击定位到分支末尾节点
              </div>
              <div class="val-list">
                <div v-for="(err, idx) in valErrors" :key="idx"
                  class="val-item" :class="{ active: valIdx === idx }" @click="navigateToError(idx)">
                  <span class="val-idx">{{ idx + 1 }}</span>
                  <span class="val-msg">{{ err.message }}</span>
                </div>
              </div>
            </div>
          </template>

          <!-- ===== 评论 Tab ===== -->
          <template v-if="panelTab === 'comments'">
            <a-space style="margin-bottom: 12px">
              <a-button :type="commentTab === 'node' ? 'primary' : 'default'" size="small"
                @click="commentTab = 'node'; loadNodeComments()">当前节点</a-button>
              <a-button :type="commentTab === 'all' ? 'primary' : 'default'" size="small"
                @click="commentTab = 'all'; loadAllComments()">全部评论</a-button>
            </a-space>
            <a-list :data-source="commentTab === 'node' ? nodeComments : allComments"
              :locale="{ emptyText: '暂无评论' }" size="small">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :description="item.content">
                    <template #title>
                      <strong>{{ item.displayName }}</strong>
                      <span style="color: #999; font-size: 11px"> {{ item.createdAt }}</span>
                    </template>
                  </a-list-item-meta>
                  <template #actions v-if="commentTab === 'node'">
                    <a-button type="link" size="small" @click="resolveComment(item.id)">
                      {{ item.resolved ? '已解决' : '标记解决' }}
                    </a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
            <div style="margin-top: 12px">
              <a-textarea v-model:value="newComment" placeholder="输入评论..." :auto-size="{ minRows: 2 }" />
              <a-button type="primary" size="small" style="margin-top: 8px" @click="addComment">发送</a-button>
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 历史版本抽屉 -->
    <a-drawer :open="showHistory" @close="showHistory = false" title="历史版本" :width="360">
      <a-list :data-source="versions" :locale="{ emptyText: '暂无历史版本' }">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="`版本 ${item.id.substring(0, 8)}`" :description="item.createdAt" />
            <template #actions>
              <a-popconfirm title="确认恢复?" @confirm="restoreVersion(item.id)">
                <a-button type="link" size="small">恢复</a-button>
              </a-popconfirm>
            </template>
          </a-list-item>
        </template>
      </a-list>
    </a-drawer>

    <!-- 评审人弹窗 -->
    <a-modal v-model:open="showReviewModal" title="选择评审人" @ok="submitReview">
      <a-select mode="multiple" style="width: 100%" v-model:value="selectedReviewers" placeholder="选择评审人"
        :options="users.filter((u: any) => u.id !== store.user?.id).map((u: any) => ({ value: u.id, label: u.displayName }))" />
    </a-modal>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  ArrowLeftOutlined, SaveOutlined, HistoryOutlined, SearchOutlined,
  ToolOutlined, CommentOutlined, CheckCircleOutlined,
  PlusOutlined, PlusSquareOutlined, DeleteOutlined, CheckOutlined, CloseOutlined,
} from '@ant-design/icons-vue';
import MindMap from 'simple-mind-map';
import Select from 'simple-mind-map/src/plugins/Select';
import Drag from 'simple-mind-map/src/plugins/Drag';
import { caseSetApi, mindNodeApi, commentApi, caseHistoryApi, userApi, customAttributeApi } from '../api';
import { useAppStore } from '../stores/app';
import type { CaseSet, MindNodeData, CaseHistory, User, CustomAttribute } from '../types';

MindMap.usePlugin(Select);
MindMap.usePlugin(Drag);

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const caseSetId = String(route.params.caseSetId);

const mindMapContainer = ref<HTMLDivElement | null>(null);
let mindMapInstance: any = null;

// === 基础状态 ===
const caseSet = ref<CaseSet | null>(null);
const caseCount = ref(0);
const saving = ref(false);

// === 右侧面板 ===
const rightPanelOpen = ref(false);
const panelTab = ref<'props' | 'validate' | 'comments'>('props');

// === 编辑表单 (缓冲, 点确定才生效) ===
const editForm = ref<{ text: string; nodeType: string | null; properties: Record<string, any> } | null>(null);
const activeNodeInstance = ref<any>(null);

// === 搜索 ===
const showSearch = ref(false);
const searchText = ref('');
const replaceText = ref('');
const searchMatches = ref<any[]>([]);
const searchIdx = ref(-1);

// === 规范检查 ===
const valErrors = ref<{ uid: string; message: string }[]>([]);
const valIdx = ref(-1);
const valChecked = ref(false);

// === 评论 ===
const commentTab = ref<'node' | 'all'>('node');
const nodeComments = ref<any[]>([]);
const allComments = ref<any[]>([]);
const newComment = ref('');

// === 历史 ===
const showHistory = ref(false);
const versions = ref<CaseHistory[]>([]);

// === 用户/属性/评审 ===
const users = ref<User[]>([]);
const projectAttributes = ref<CustomAttribute[]>([]);
const showReviewModal = ref(false);
const selectedReviewers = ref<string[]>([]);

let autoSaveTimer: ReturnType<typeof setInterval> | null = null;
let historyTimer: ReturnType<typeof setInterval> | null = null;

// === 状态计算 ===
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
const nodeTypeLabel: Record<string, string> = {
  TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果',
};
const nodeTypeColor: Record<string, string> = {
  TITLE: '#1677ff', PRECONDITION: '#722ed1', STEP: '#13c2c2', EXPECTED: '#52c41a',
};

const filteredEditAttrs = computed(() => {
  if (!editForm.value) return [];
  const nt = editForm.value.nodeType;
  return projectAttributes.value.filter(a => {
    if (!a.nodeTypeLimit) return true;
    if (!nt) return false;
    return a.nodeTypeLimit.split(',').includes(nt);
  });
});

// 面板关闭/打开后通知思维导图重新计算尺寸
watch(rightPanelOpen, () => {
  nextTick(() => { if (mindMapInstance) mindMapInstance.resize(); });
});

// =============================================
// 右侧面板控制
// =============================================

function openValidation() {
  rightPanelOpen.value = true;
  panelTab.value = 'validate';
  runValidation();
}

function openComments() {
  rightPanelOpen.value = true;
  panelTab.value = 'comments';
  if (activeNodeInstance.value) loadNodeComments();
  else loadAllComments();
}

function openHistory() {
  showHistory.value = true;
  caseHistoryApi.list(caseSetId, 10).then(res => { versions.value = res.data; });
}

function closePanel() {
  rightPanelOpen.value = false;
}

// =============================================
// 数据格式转换 (不使用 tag, 自定义SVG渲染)
// =============================================

function nodeToMM(node: MindNodeData): any {
  return {
    data: {
      text: node.text,
      uid: node.id || ('n_' + Math.random().toString(36).substring(2, 10)),
      _raw: { ...node, children: undefined },
    },
    children: (node.children || []).map(c => nodeToMM(c)),
  };
}

function mmToTree(mmNode: any): MindNodeData {
  const raw = mmNode.data?._raw || {};
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

// =============================================
// 自定义节点标签 (SVG foreignObject)
// 类型 → 节点框上方  属性 → 节点框下方
// =============================================

function getGroupEl(node: any): SVGGElement | null {
  if (!node?.group) return null;
  if (node.group.node instanceof SVGElement) return node.group.node as SVGGElement;
  if (node.group instanceof SVGElement) return node.group as SVGGElement;
  return null;
}

function addNodeLabels(node: any) {
  const groupEl = getGroupEl(node);
  if (!groupEl) return;

  groupEl.querySelectorAll('.mm-extra-label').forEach(el => el.remove());

  const raw = node.nodeData?.data?._raw;
  if (!raw) return;

  const w = node.width || 120;
  const h = node.height || 30;

  // 类型标签 → 节点框正上方
  if (raw.nodeType && nodeTypeLabel[raw.nodeType]) {
    const fo = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
    fo.setAttribute('class', 'mm-extra-label');
    fo.setAttribute('x', '0');
    fo.setAttribute('y', String(-20));
    fo.setAttribute('width', String(Math.max(w, 150)));
    fo.setAttribute('height', '20');
    fo.style.overflow = 'visible';
    fo.style.pointerEvents = 'none';
    const div = document.createElement('div');
    div.style.cssText = `font-size:11px; font-weight:600; color:${nodeTypeColor[raw.nodeType] || '#1677ff'}; line-height:18px; white-space:nowrap;`;
    div.textContent = nodeTypeLabel[raw.nodeType];
    fo.appendChild(div);
    groupEl.appendChild(fo);
  }

  // 属性标签 → 节点框正下方
  const parts: string[] = [];
  if (raw.properties?.['优先级']) parts.push(String(raw.properties['优先级']));
  if (raw.properties?.['标记'] && raw.properties['标记'] !== '无') parts.push(raw.properties['标记']);
  if (raw.properties?.['标签'] && Array.isArray(raw.properties['标签'])) parts.push(...raw.properties['标签']);

  if (parts.length) {
    const fo = document.createElementNS('http://www.w3.org/2000/svg', 'foreignObject');
    fo.setAttribute('class', 'mm-extra-label');
    fo.setAttribute('x', '0');
    fo.setAttribute('y', String(h + 2));
    fo.setAttribute('width', String(Math.max(w, 250)));
    fo.setAttribute('height', '22');
    fo.style.overflow = 'visible';
    fo.style.pointerEvents = 'none';
    const div = document.createElement('div');
    div.style.cssText = 'display:flex; gap:4px; line-height:18px; white-space:nowrap;';
    parts.forEach(p => {
      const span = document.createElement('span');
      span.textContent = p;
      span.style.cssText = 'font-size:10px; background:rgba(0,0,0,0.06); color:#595959; border-radius:2px; padding:0 5px;';
      div.appendChild(span);
    });
    fo.appendChild(div);
    groupEl.appendChild(fo);
  }
}

function addAllCustomLabels() {
  if (!mindMapInstance?.renderer?.root) return;
  function walk(node: any) {
    addNodeLabels(node);
    (node.children || []).forEach(walk);
  }
  walk(mindMapInstance.renderer.root);
}

// =============================================
// 节点导航
// =============================================

function findNodeByUid(node: any, uid: string): any {
  if (!node) return null;
  const nodeUid = node.nodeData?.data?.uid || node.data?.uid;
  if (nodeUid === uid) return node;
  const children = node.children || node._children || [];
  for (const child of children) {
    const found = findNodeByUid(child, uid);
    if (found) return found;
  }
  return null;
}

function navigateToNode(uid: string) {
  if (!mindMapInstance) return;
  const root = mindMapInstance.renderer.root;
  const target = findNodeByUid(root, uid);
  if (target) {
    target.active();
    mindMapInstance.renderer.moveNodeToCenter(target);
  }
}

// =============================================
// 确定按钮 - 应用编辑到节点
// =============================================

function confirmEdit() {
  if (!editForm.value || !activeNodeInstance.value || !mindMapInstance) return;
  const form = editForm.value;
  const node = activeNodeInstance.value;

  let rawRef = node.nodeData?.data?._raw;
  if (!rawRef) {
    rawRef = { text: '', nodeType: null, properties: {} };
    if (node.nodeData?.data) node.nodeData.data._raw = rawRef;
  }
  rawRef.text = form.text;
  rawRef.nodeType = form.nodeType;
  rawRef.properties = { ...form.properties };

  mindMapInstance.execCommand('SET_NODE_TEXT', node, form.text);
  // 标签由 addAllCustomLabels 在重渲染后自动添加
  requestAnimationFrame(() => addAllCustomLabels());
  message.success('已更新');
}

// =============================================
// 初始化思维导图
// =============================================

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
    useLeftKeySelectionRightKeyDrag: true,
  });

  // 节点选中 → 打开右侧属性面板
  mindMapInstance.on('node_active', (_: any, nodes: any[]) => {
    if (nodes.length > 0) {
      const node = nodes[0];
      activeNodeInstance.value = node;
      const raw = node.nodeData?.data?._raw || node.data?._raw;
      const currentText = raw?.text ?? node.nodeData?.data?.text ?? node.data?.text ?? '';
      editForm.value = {
        text: currentText,
        nodeType: raw?.nodeType || null,
        properties: raw?.properties ? JSON.parse(JSON.stringify(raw.properties)) : {},
      };
      if (!rightPanelOpen.value) {
        rightPanelOpen.value = true;
        panelTab.value = 'props';
      }
    } else {
      activeNodeInstance.value = null;
      editForm.value = null;
      if (panelTab.value === 'props') {
        rightPanelOpen.value = false;
      }
    }
  });

  // 行内编辑完成 → 同步 _raw
  mindMapInstance.on('node_text_edit_end', (node: any, text: string) => {
    const raw = node.nodeData?.data?._raw || node.data?._raw;
    if (raw) raw.text = text;
    if (editForm.value && activeNodeInstance.value === node) {
      editForm.value.text = text;
    }
  });

  // 数据变化 → 更新计数
  mindMapInstance.on('data_change', () => {
    const tree = getFullTree();
    if (tree.length > 0) caseCount.value = countValid(tree[0]);
  });

  // 渲染完成 → 注入自定义标签
  mindMapInstance.on('node_tree_render_end', () => {
    requestAnimationFrame(addAllCustomLabels);
  });
}

function execCmd(cmd: string) {
  if (mindMapInstance) mindMapInstance.execCommand(cmd);
}

// =============================================
// 数据加载
// =============================================

async function loadData() {
  const [csRes, treeRes] = await Promise.all([caseSetApi.get(caseSetId), mindNodeApi.tree(caseSetId)]);
  caseSet.value = csRes.data;
  const mmData = treeRes.data.length
    ? nodeToMM(treeRes.data[0])
    : { data: { text: caseSet.value?.name || '新用例集' }, children: [] };
  if (mindMapInstance) mindMapInstance.setData(mmData);
  caseCount.value = treeRes.data.length > 0 ? countValid(treeRes.data[0]) : 0;
}

// =============================================
// 保存
// =============================================

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

// =============================================
// 查找替换 (逐个定位到节点)
// =============================================

function toggleSearch() {
  showSearch.value = !showSearch.value;
  if (!showSearch.value) { searchMatches.value = []; searchIdx.value = -1; }
}

function collectMatchNodes(): any[] {
  if (!mindMapInstance || !searchText.value) return [];
  const matches: any[] = [];
  function walk(node: any) {
    const text = node.nodeData?.data?.text || node.data?.text || '';
    if (text.includes(searchText.value)) matches.push(node);
    (node.children || []).forEach(walk);
  }
  walk(mindMapInstance.renderer.root);
  return matches;
}

function onSearchChange() {
  if (!searchText.value) { searchMatches.value = []; searchIdx.value = -1; return; }
  searchMatches.value = collectMatchNodes();
  searchIdx.value = searchMatches.value.length ? 0 : -1;
  if (searchMatches.value.length) {
    const n = searchMatches.value[0];
    n.active();
    mindMapInstance.renderer.moveNodeToCenter(n);
  }
}

function findNext() {
  if (!searchMatches.value.length) { onSearchChange(); return; }
  searchIdx.value = (searchIdx.value + 1) % searchMatches.value.length;
  const n = searchMatches.value[searchIdx.value];
  n.active();
  mindMapInstance.renderer.moveNodeToCenter(n);
}

function findPrev() {
  if (!searchMatches.value.length) return;
  searchIdx.value = (searchIdx.value - 1 + searchMatches.value.length) % searchMatches.value.length;
  const n = searchMatches.value[searchIdx.value];
  n.active();
  mindMapInstance.renderer.moveNodeToCenter(n);
}

function replaceOneFn() {
  if (searchIdx.value < 0 || !searchMatches.value.length) return;
  const node = searchMatches.value[searchIdx.value];
  const oldText = node.nodeData?.data?.text || '';
  const newText = oldText.replace(searchText.value, replaceText.value);
  mindMapInstance.execCommand('SET_NODE_TEXT', node, newText);
  const raw = node.nodeData?.data?._raw || node.data?._raw;
  if (raw) raw.text = newText;
  searchMatches.value = collectMatchNodes();
  if (searchIdx.value >= searchMatches.value.length) searchIdx.value = Math.max(0, searchMatches.value.length - 1);
  message.success('已替换');
}

function replaceAllFn() {
  if (!searchText.value || !mindMapInstance) return;
  const data = mindMapInstance.getData();
  function walk(n: any) {
    if (n.data?.text) n.data.text = n.data.text.replaceAll(searchText.value, replaceText.value);
    if (n.data?._raw?.text) n.data._raw.text = n.data._raw.text.replaceAll(searchText.value, replaceText.value);
    if (n.children) n.children.forEach(walk);
  }
  walk(data);
  mindMapInstance.setData(data);
  searchMatches.value = [];
  searchIdx.value = -1;
  message.success('全部替换完成');
}

// =============================================
// 规范检查 (客户端验证, 定位到分支末尾节点)
// =============================================

function runValidation() {
  valErrors.value = [];
  valIdx.value = -1;
  valChecked.value = true;

  if (!mindMapInstance?.renderer?.root) return;
  const root = mindMapInstance.renderer.root;
  const errors: { uid: string; message: string }[] = [];

  function walk(node: any, path: any[]) {
    const currentPath = [...path, node];
    const children = node.children || [];

    if (children.length === 0) {
      const leafUid = node.nodeData?.data?.uid || '';
      const pathNames = currentPath.map((n: any) => n.nodeData?.data?.text || '?');
      const pathStr = pathNames.join(' → ');

      if (currentPath.length < 5) {
        errors.push({ uid: leafUid, message: `路径层数不足(${currentPath.length}层,至少5层): ${pathStr}` });
        return;
      }

      const len = currentPath.length;
      const getType = (n: any) => n.nodeData?.data?._raw?.nodeType;
      const expected = ['TITLE', 'PRECONDITION', 'STEP', 'EXPECTED'] as const;
      const actual = [getType(currentPath[len-4]), getType(currentPath[len-3]), getType(currentPath[len-2]), getType(currentPath[len-1])];

      const issues: string[] = [];
      for (let i = 0; i < 4; i++) {
        if (actual[i] !== expected[i]) {
          const actualLabel = actual[i] ? nodeTypeLabel[actual[i]] : '未设置';
          issues.push(`第${len - 4 + i + 1}层应为"${nodeTypeLabel[expected[i]]}"(当前: ${actualLabel})`);
        }
      }

      if (issues.length) {
        errors.push({ uid: leafUid, message: `${pathStr}\n${issues.join('; ')}` });
      } else {
        const props = currentPath[len-1].nodeData?.data?._raw?.properties;
        if (!props?.['优先级']) {
          errors.push({ uid: leafUid, message: `${pathStr}\n预期结果节点未设置优先级` });
        }
      }
    } else {
      children.forEach((c: any) => walk(c, currentPath));
    }
  }

  walk(root, []);
  valErrors.value = errors;

  if (errors.length) {
    message.warning(`共${errors.length}条不符合规范`);
  } else {
    message.success('所有分支符合规范');
  }
}

function navigateToError(idx: number) {
  valIdx.value = idx;
  const err = valErrors.value[idx];
  if (err?.uid) navigateToNode(err.uid);
}

// =============================================
// 状态/评审
// =============================================

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

// =============================================
// 评论
// =============================================

async function loadNodeComments() {
  const raw = activeNodeInstance.value?.nodeData?.data?._raw;
  if (!raw?.id) return;
  nodeComments.value = (await commentApi.nodeComments(raw.id)).data;
}
async function loadAllComments() {
  allComments.value = (await commentApi.allComments(caseSetId)).data;
}
async function addComment() {
  const raw = activeNodeInstance.value?.nodeData?.data?._raw;
  if (!newComment.value.trim() || !raw?.id) return;
  await commentApi.add(raw.id, caseSetId, newComment.value.trim());
  newComment.value = '';
  loadNodeComments();
}
async function resolveComment(id: string) {
  await commentApi.resolve(id);
  loadNodeComments();
}

// =============================================
// 历史版本
// =============================================

async function restoreVersion(id: string) {
  await caseHistoryApi.restore(id);
  message.success('版本已恢复');
  loadData();
  showHistory.value = false;
}

// =============================================
// 生命周期
// =============================================

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
  height: 48px; line-height: 48px; flex-shrink: 0;
}
.search-bar {
  background: #fff; padding: 8px 16px; border-bottom: 1px solid #f0f0f0;
  display: flex; gap: 8px; align-items: center; flex-shrink: 0;
}

/* 主体布局: 左侧导图 + 右侧面板 */
.editor-body {
  flex: 1; display: flex; overflow: hidden;
}
.mm-area {
  flex: 1; position: relative; overflow: hidden; background: #fafafa;
}
.right-panel {
  width: 320px; flex-shrink: 0; border-left: 1px solid #f0f0f0;
  background: #fff; display: flex; flex-direction: column; overflow: hidden;
}
.rp-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 10px 12px; border-bottom: 1px solid #f0f0f0; flex-shrink: 0;
}
.rp-body {
  flex: 1; overflow-y: auto; padding: 12px;
}

.prop-field { margin-bottom: 10px; }
.prop-field label { display: block; font-size: 12px; color: #999; margin-bottom: 3px; }

.empty-hint {
  display: flex; align-items: center; justify-content: center;
  height: 200px; color: #ccc; font-size: 14px;
}
.tips-bar {
  position: absolute; bottom: 8px; left: 50%; transform: translateX(-50%);
  background: rgba(0,0,0,0.45); color: #fff; font-size: 11px;
  padding: 4px 14px; border-radius: 4px; pointer-events: none; white-space: nowrap;
}

/* 规范检查错误列表 */
.val-list { max-height: 500px; overflow-y: auto; }
.val-item {
  display: flex; align-items: flex-start; gap: 6px; padding: 8px;
  border-radius: 4px; cursor: pointer; font-size: 12px; line-height: 1.6;
  border: 1px solid transparent; margin-bottom: 4px; transition: all 0.15s;
}
.val-item:hover { background: #fff2f0; }
.val-item.active { background: #fff2f0; border-color: #ffccc7; }
.val-idx {
  background: #ff4d4f; color: #fff; border-radius: 50%; min-width: 20px; height: 20px;
  display: flex; align-items: center; justify-content: center; font-size: 10px; flex-shrink: 0;
}
.val-msg { color: #333; word-break: break-all; white-space: pre-line; }
</style>

<style>
/* 全局: simple-mind-map 节点边框 */
.smm-node .smm-node-shape { stroke: #c9ced6 !important; stroke-width: 1px !important; }
.smm-node.active .smm-node-shape { stroke: #1677ff !important; stroke-width: 2px !important; }
.smm-node:hover .smm-node-shape { stroke: #4096ff !important; }
.smm-root-node .smm-node-shape { stroke: #1677ff !important; stroke-width: 2px !important; }
</style>
