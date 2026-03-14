<template>
  <div class="exec-page">
    <!-- ===== 顶栏 ===== -->
    <div class="exec-header">
      <div class="header-left">
        <a-button type="text" @click="$router.push('/test-plans')"><ArrowLeftOutlined /></a-button>
        <strong class="plan-name">{{ plan?.name }}</strong>
        <span class="stat-group">
          已执行 <b>{{ stats.executed }}/{{ stats.total }}</b>
          <span class="c-fail">不通过 {{ stats.fail }}</span>
          <span class="c-pass">通过 {{ stats.pass }}</span>
          <span class="c-skip">跳过 {{ stats.skip }}</span>
        </span>
      </div>
      <div class="header-right">
        <span class="label">执行进度</span>
        <a-progress :percent="progressPct" :stroke-color="'#52c41a'" size="small" style="width:120px" />
        <a-button type="link" @click="showReport = true">查看测试报告</a-button>
        <a-button type="link" :loading="refreshing" @click="doRefresh"><ReloadOutlined /> 刷新用例</a-button>
      </div>
    </div>

    <!-- ===== 搜索栏 ===== -->
    <div class="exec-filter">
      <span class="filter-label">用例名称</span>
      <a-input v-model:value="filterKeyword" placeholder="搜索" allow-clear style="width:180px" @change="rebuildTree" />
      <span class="filter-label">执行人</span>
      <a-select v-model:value="filterExecutor" placeholder="全部执行人" allow-clear style="width:140px"
        :options="executorOptions" @change="rebuildTree" />
      <span class="filter-label">执行结果</span>
      <a-select v-model:value="filterResult" placeholder="全部结果" allow-clear style="width:140px"
        :options="resultOpts" @change="rebuildTree" />
    </div>

    <!-- ===== 主体 ===== -->
    <div class="exec-body">
      <div class="exec-left">
        <a-table :columns="cols" :data-source="displayTree" row-key="_key" size="small"
          :pagination="false" :scroll="{ y: 'calc(100vh - 200px)' }"
          :row-class-name="rowClass" :custom-row="customRow"
          :expanded-row-keys="expandedKeys" @expand="onExpand"
          :expandable="{ childrenColumnName: 'children' }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">
              <span :class="{ 'leaf-text': record._isLeaf }">
                <a-tag v-if="record._nodeType" :color="ntColor(record._nodeType)" style="font-size:11px">{{ ntLabel(record._nodeType) }}</a-tag>
                {{ record._text }}
              </span>
            </template>
            <template v-if="column.key === 'executor'">
              <a-select v-if="record._isLeaf" :value="record._executorId || undefined" placeholder="未分配"
                allow-clear show-search option-filter-prop="label" size="big" style="width:100%"
                :options="executorOptions"
                @click.stop
                @change="(v: any) => onExecutorChange(record._caseId, v)" />
            </template>
            <template v-if="column.key === 'result'">
              <a-select v-if="record._isLeaf" :value="record._result" size="big"
                :class="'res-select res-' + (record._result || 'PENDING').toLowerCase()"
                style="width:100%"
                :options="resultOpts"
                @click.stop
                @change="(v: any) => onResultChange(record._caseId, v, record)" />
            </template>
            <template v-if="column.key === 'action'">
              <a-popconfirm v-if="record._isLeaf" title="确认移除该用例？" @confirm.stop="removeCase(record._caseId)">
                <a-button type="link" size="small" danger @click.stop>移除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </div>

      <!-- 右侧详情 -->
      <div class="exec-right" v-if="selectedCase">
        <div class="detail-header">
          <span>用例详情</span>
          <a-tag :color="resColor(selectedCase.result)">{{ resLabel(selectedCase.result) }}</a-tag>
        </div>
        <div class="detail-body">
          <div class="detail-section" v-if="caseDetail.modulePath">
            <div class="section-label">前置模块</div>
            <div class="section-value path-value">{{ caseDetail.modulePath }}</div>
          </div>
          <div class="detail-section">
            <div class="section-label">用例标题</div>
            <div class="section-value title-value">{{ caseDetail.title }}</div>
          </div>
          <div class="detail-section" v-if="caseDetail.precondition">
            <div class="section-label">前置条件</div>
            <div class="section-value">{{ caseDetail.precondition }}</div>
          </div>
          <div class="detail-section" v-if="caseDetail.step">
            <div class="section-label">步骤</div>
            <div class="section-value" style="white-space:pre-wrap">{{ caseDetail.step }}</div>
          </div>
          <div class="detail-section" v-if="caseDetail.expected">
            <div class="section-label">预期结果</div>
            <div class="section-value" style="white-space:pre-wrap">{{ caseDetail.expected }}</div>
          </div>
          <div class="detail-section" v-if="filteredProps.length">
            <div class="section-label">属性</div>
            <ul class="attr-list">
              <li v-for="item in filteredProps" :key="item.key" class="attr-item">
                <span class="attr-name">{{ propLabel(item.key) }}</span>
                <template v-if="item.key === 'mark'">
                  <a-tag :color="markColor(item.val)" size="small">{{ markLabel(item.val) }}</a-tag>
                </template>
                <template v-else-if="Array.isArray(item.val)">
                  <a-tag v-for="v in item.val" :key="v" color="blue" size="small">{{ v }}</a-tag>
                </template>
                <template v-else>
                  <a-tag color="blue" size="small">{{ item.val }}</a-tag>
                </template>
              </li>
            </ul>
          </div>
          <div class="detail-section" v-if="selectedCase.reason">
            <div class="section-label">{{ selectedCase.result === 'FAIL' ? '不通过原因' : '跳过原因' }}</div>
            <div class="section-value reason-value">{{ selectedCase.reason }}</div>
          </div>
          <div style="margin-top:16px">
            <a class="link-edit" @click="goToMindMap">去修改用例 →</a>
          </div>
        </div>
        <div class="detail-footer">
          <div class="footer-nav">
            <a-button size="small" :disabled="curIdx <= 0" @click="goPrev"><LeftOutlined /> 上一条</a-button>
            <a-button size="small" :disabled="curIdx >= cases.length - 1" @click="goNext">下一条 <RightOutlined /></a-button>
          </div>
          <div class="footer-actions">
            <a-button class="btn-pass" @click="doExecute('PASS')">通过</a-button>
            <a-button class="btn-fail" @click="openReasonModal('FAIL')">不通过</a-button>
            <a-button class="btn-skip" @click="openReasonModal('SKIP')">跳过</a-button>
          </div>
        </div>
      </div>
      <div class="exec-right empty" v-else><a-empty description="请选择用例" /></div>
    </div>

    <!-- 原因弹窗 -->
    <a-modal v-model:open="reasonModal.visible" :title="reasonModal.action === 'FAIL' ? '不通过原因' : '跳过原因'" @ok="submitReason" ok-text="确认">
      <a-textarea v-model:value="reasonModal.text" :placeholder="'请填写' + (reasonModal.action === 'FAIL' ? '不通过' : '跳过') + '原因（必填）'"
        :auto-size="{ minRows: 3, maxRows: 6 }" />
    </a-modal>

    <!-- 测试报告弹窗 -->
    <a-modal v-model:open="showReport" title="测试报告" :footer="null" width="680px">
      <div class="rpt-section">
        <h4>整体执行进度</h4>
        <div class="rpt-overview">
          <a-progress type="circle" :percent="progressPct" :size="100" :stroke-color="'#52c41a'" />
          <div class="rpt-nums">
            <div class="num"><span class="val">{{ stats.total }}</span><span class="lbl">总用例</span></div>
            <div class="num"><span class="val">{{ stats.executed }}</span><span class="lbl">已执行</span></div>
            <div class="num"><span class="val">{{ stats.pass }}</span><span class="lbl">通过</span></div>
          </div>
        </div>
        <div class="rpt-sub">
          <span class="c-fail">{{ stats.fail }} 不通过</span>
          <span class="c-skip">{{ stats.skip }} 跳过</span>
          <span style="color:#999">{{ stats.pending }} 待执行</span>
        </div>
      </div>
      <a-divider />
      <div class="rpt-section">
        <h4>执行人进度</h4>
        <div v-for="ep in executorProgress" :key="ep.name" style="margin-bottom:12px">
          <div class="ep-row">
            <span class="ep-name">{{ ep.name }}</span>
            <a-progress :percent="ep.total ? Math.round(ep.executed/ep.total*100) : 0" :stroke-color="'#52c41a'" style="flex:1;margin:0 12px" />
            <span class="ep-pct">{{ ep.total ? Math.round(ep.executed/ep.total*100) : 0 }}% ({{ ep.executed }}/{{ ep.total }})</span>
          </div>
          <div class="ep-detail">通过 {{ ep.pass }} &nbsp; 不通过 {{ ep.fail }} &nbsp; 跳过 {{ ep.skip }} &nbsp; 待执行 {{ ep.pending }}</div>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { ArrowLeftOutlined, ReloadOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue';
import { testPlanApi } from '../api';
import type { TestPlan } from '../types';

const route = useRoute();
const router = useRouter();
const planId = String(route.params.planId);

const plan = ref<TestPlan | null>(null);
const cases = ref<any[]>([]);
const displayTree = ref<any[]>([]);
const expandedKeys = ref<string[]>([]);
const selectedCase = ref<any>(null);
const showReport = ref(false);
const refreshing = ref(false);
const filterKeyword = ref('');
const filterExecutor = ref<string | undefined>();
const filterResult = ref<string | undefined>();
const reasonModal = ref({ visible: false, action: '', text: '' });

/** 计划关联的执行人列表 {userId, displayName} */
const planExecutors = ref<{userId: string; displayName: string}[]>([]);

const cols = [
  { title: '测试用例', key: 'title', ellipsis: true },
  { title: '执行人', key: 'executor', width: 140 },
  { title: '执行结果', key: 'result', width: 120 },
  { title: '操作', key: 'action', width: 80 },
];
const resultOpts = [
  { value: 'PENDING', label: '待执行' }, { value: 'PASS', label: '通过' },
  { value: 'FAIL', label: '不通过' }, { value: 'SKIP', label: '跳过' },
];

// ── 统计 ──────────────────────────────────────────────
const stats = computed(() => {
  const a = cases.value;
  const p = a.filter((c: any) => c.result === 'PASS').length;
  const f = a.filter((c: any) => c.result === 'FAIL').length;
  const s = a.filter((c: any) => c.result === 'SKIP').length;
  return { total: a.length, pass: p, fail: f, skip: s, pending: a.length - p - f - s, executed: p + f + s };
});
const progressPct = computed(() => stats.value.total ? Math.round(stats.value.executed / stats.value.total * 100) : 0);
const curIdx = computed(() => selectedCase.value ? cases.value.findIndex((c: any) => c.id === selectedCase.value.id) : -1);

const executorOptions = computed(() => {
  return planExecutors.value.map(e => ({ value: e.userId, label: e.displayName }));
});

const executorProgress = computed(() => {
  const m = new Map<string, { name: string; total: number; pass: number; fail: number; skip: number; pending: number; executed: number }>();
  cases.value.forEach((c: any) => {
    const k = c.executorId || '_', n = c.executorName || '未分配';
    if (!m.has(k)) m.set(k, { name: n, total: 0, pass: 0, fail: 0, skip: 0, pending: 0, executed: 0 });
    const e = m.get(k)!; e.total++;
    if (c.result === 'PASS') { e.pass++; e.executed++; }
    else if (c.result === 'FAIL') { e.fail++; e.executed++; }
    else if (c.result === 'SKIP') { e.skip++; e.executed++; }
    else e.pending++;
  });
  return Array.from(m.values());
});

/** 从 pathSnapshot 中提取详情 */
const caseDetail = computed(() => {
  const sc = selectedCase.value;
  if (!sc?.pathSnapshot?.length) return {};
  const path: any[] = sc.pathSnapshot;
  const len = path.length;
  if (len < 4) return {};
  const titleNode = path[len - 4];
  const preNode = path[len - 3];
  const stepNode = path[len - 2];
  const expectedNode = path[len - 1];
  // 前置模块：从 index 1 (跳过root) 到 TITLE 之前
  const moduleNodes = path.slice(1, len - 4);
  return {
    modulePath: moduleNodes.map((n: any) => n.text).join(' → ') || '',
    title: titleNode?.text || '',
    precondition: preNode?.text || '',
    step: stepNode?.text || '',
    expected: expectedNode?.text || '',
    props: expectedNode?.properties || {},
  };
});

/** 过滤掉 null / undefined / 空字符串 / mark=NONE 的属性 */
const filteredProps = computed(() => {
  const props = caseDetail.value.props;
  if (!props || typeof props !== 'object') return [];
  return Object.entries(props).filter(([k, v]) => {
    if (v == null || v === '' || v === 'NONE') return false;
    if (Array.isArray(v) && v.length === 0) return false;
    return true;
  }).map(([k, v]) => ({ key: k, val: v }));
});

/** 行内修改执行人 */
async function onExecutorChange(caseId: string, executorId: string | null) {
  if (!caseId) return;
  await testPlanApi.updateCaseExecutor(caseId, executorId || null);
  const c = cases.value.find((x: any) => x.id === caseId);
  if (c) {
    c.executorId = executorId || null;
    c.executorName = planExecutors.value.find(e => e.userId === executorId)?.displayName || '';
  }
  rebuildTree();
}

/** 行内修改执行结果 */
async function onResultChange(caseId: string, result: string, record: any) {
  if (!caseId) return;
  if (result === 'FAIL' || result === 'SKIP') {
    selectedCase.value = cases.value.find((x: any) => x.id === caseId) || null;
    reasonModal.value = { visible: true, action: result, text: '' };
    return;
  }
  await testPlanApi.executeCase(caseId, result);
  const c = cases.value.find((x: any) => x.id === caseId);
  if (c) { c.result = result; c.executedAt = new Date().toISOString(); }
  rebuildTree();
  if (selectedCase.value?.id === caseId) {
    selectedCase.value = { ...selectedCase.value, result };
  }
}

// ── 工具函数 ──────────────────────────────────────────
function resLabel(r: string) { return ({ PASS: '通过', FAIL: '不通过', SKIP: '跳过' } as any)[r] || '待执行'; }
function resColor(r: string) { return ({ PASS: 'success', FAIL: 'error', SKIP: 'warning' } as any)[r] || 'default'; }
function ntLabel(t: string) { return ({ TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' } as any)[t] || t || ''; }
function ntColor(t: string) { return ({ TITLE: 'purple', PRECONDITION: 'blue', STEP: 'green', EXPECTED: 'orange' } as any)[t] || 'default'; }
function propLabel(k: string) { return ({ mark: '标记', priority: '优先级', category: '分类', coverage: '覆盖集', device: '设备集' } as any)[k] || k; }
function markLabel(v: any) { return ({ PENDING: '待完成', TO_CONFIRM: '待确认', TO_MODIFY: '待修改', '待完成': '待完成', '待确认': '待确认', '待修改': '待修改' } as any)[v] || v; }
function markColor(v: any) { return ({ PENDING: 'red', TO_CONFIRM: 'orange', TO_MODIFY: 'purple', '待完成': 'red', '待确认': 'orange', '待修改': 'purple' } as any)[v] || 'default'; }

// ── 数据加载 ─────────────────────────────────────────
async function loadData() {
  const [pRes, cRes, eRes] = await Promise.all([
    testPlanApi.get(planId),
    testPlanApi.getCases(planId),
    testPlanApi.getExecutors(planId),
  ]);
  plan.value = pRes.data;
  cases.value = cRes.data;
  planExecutors.value = eRes.data || [];
  rebuildTree();
  if (selectedCase.value) {
    selectedCase.value = cases.value.find((c: any) => c.id === selectedCase.value.id) || cases.value[0] || null;
  } else if (cases.value.length) {
    selectedCase.value = cases.value[0];
  }
}

/** 刷新用例：先让后端回源重新拍快照，再重新加载 */
async function doRefresh() {
  refreshing.value = true;
  try {
    await testPlanApi.refreshCases(planId);
    await loadData();
    message.success('用例已刷新');
  } finally { refreshing.value = false; }
}

/**
 * 从快照数据构建合并树：
 * 多条 case 的 pathSnapshot 共享相同的祖先节点(通过 id 匹配)，
 * 合并成一棵树用于 Table 展示。
 */
function rebuildTree() {
  // 先按筛选条件过滤
  let filtered = cases.value;
  if (filterKeyword.value) {
    const kw = filterKeyword.value.toLowerCase();
    filtered = filtered.filter((c: any) => {
      const path: any[] = c.pathSnapshot || [];
      return path.some((n: any) => (n.text || '').toLowerCase().includes(kw));
    });
  }
  if (filterExecutor.value) filtered = filtered.filter((c: any) => c.executorId === filterExecutor.value);
  if (filterResult.value) filtered = filtered.filter((c: any) => c.result === filterResult.value);

  // 按 caseSetId 分组
  const byCsId = new Map<string, any[]>();
  for (const c of filtered) {
    if (!byCsId.has(c.caseSetId)) byCsId.set(c.caseSetId, []);
    byCsId.get(c.caseSetId)!.push(c);
  }

  const tree: any[] = [];
  const allKeys: string[] = [];

  for (const [csId, csList] of byCsId) {
    const csName = csList[0]?.caseSetName || csId;
    const csKey = `cs-${csId}`;
    allKeys.push(csKey);

    // 合并该用例集下所有路径快照为一棵树
    const mergedChildren = mergePaths(csList, csId, allKeys);
    tree.push({ _key: csKey, _text: csName, _isGroup: true, children: mergedChildren.length ? mergedChildren : undefined });
  }

  displayTree.value = tree;
  expandedKeys.value = [...allKeys];
}

/**
 * 将同一个用例集的多条路径快照合并成树：
 * 共享祖先节点(通过原始 node id 去重)，
 * 叶节点(EXPECTED)挂载执行信息。
 */
function mergePaths(csList: any[], csId: string, allKeys: string[]): any[] {
  interface TreeNode {
    _key: string; _text: string; _nodeType: string | null;
    _isLeaf?: boolean; _caseId?: string; _result?: string;
    _executorId?: string; _executorName?: string;
    children?: TreeNode[];
    _childMap?: Map<string, TreeNode>;
  }
  const virtualRoot: TreeNode = { _key: 'vr', _text: '', _nodeType: null, _childMap: new Map() };

  for (const c of csList) {
    const path: any[] = c.pathSnapshot || [];
    if (!path.length) continue;

    let current = virtualRoot;
    for (let i = 0; i < path.length; i++) {
      const node = path[i];
      const nodeId = node.id || `anon-${i}`;
      const key = `p-${csId}-${nodeId}`;
      const isLast = i === path.length - 1;

      if (!current._childMap) current._childMap = new Map();
      let child = current._childMap.get(nodeId);
      if (!child) {
        child = { _key: key, _text: node.text || '', _nodeType: node.nodeType || null };
        if (isLast) {
          child._isLeaf = true;
          child._caseId = c.id;
          child._result = c.result;
          child._executorId = c.executorId;
          child._executorName = c.executorName;
        }
        current._childMap.set(nodeId, child);
        allKeys.push(key);
      } else if (isLast) {
        child._isLeaf = true;
        child._caseId = c.id;
        child._result = c.result;
        child._executorId = c.executorId;
        child._executorName = c.executorName;
      }
      current = child;
    }
  }

  // 将 _childMap 转为 children 数组（递归）
  function toArray(node: TreeNode): TreeNode[] {
    if (!node._childMap || node._childMap.size === 0) return [];
    const arr: TreeNode[] = [];
    for (const child of node._childMap.values()) {
      const kids = toArray(child);
      if (kids.length) child.children = kids;
      delete child._childMap;
      arr.push(child);
    }
    return arr;
  }

  // 跳过根节点（index 0 是 mind map root），直接返回其子节点
  const rootChildren = toArray(virtualRoot);
  if (rootChildren.length === 1 && !rootChildren[0]._isLeaf) {
    // 如果只有一个根节点且不是叶子，跳过它直接返回子
    return rootChildren[0].children || [];
  }
  return rootChildren;
}

// ── 表格交互 ─────────────────────────────────────────
function onExpand(_expanded: boolean, record: any) {
  const k = record._key;
  const i = expandedKeys.value.indexOf(k);
  if (i >= 0) expandedKeys.value.splice(i, 1); else expandedKeys.value.push(k);
}

function customRow(record: any) {
  return {
    onClick: () => {
      if (record._caseId) selectedCase.value = cases.value.find((c: any) => c.id === record._caseId) || null;
      if (record.children?.length) {
        const k = record._key;
        const i = expandedKeys.value.indexOf(k);
        if (i >= 0) expandedKeys.value.splice(i, 1); else expandedKeys.value.push(k);
      }
    },
  };
}

function rowClass(record: any) {
  if (record._isLeaf && selectedCase.value?.id === record._caseId) return 'selected-row';
  if (record._isGroup) return 'group-row';
  return '';
}

function goPrev() { if (curIdx.value > 0) selectedCase.value = cases.value[curIdx.value - 1]; }
function goNext() { if (curIdx.value < cases.value.length - 1) selectedCase.value = cases.value[curIdx.value + 1]; }
function goToMindMap() { if (selectedCase.value?.caseSetId) router.push(`/mind-map/${selectedCase.value.caseSetId}`); }

// ── 执行操作 ─────────────────────────────────────────
async function doExecute(result: string, reasonText?: string) {
  if (!selectedCase.value) return;
  const caseId = selectedCase.value.id;
  await testPlanApi.executeCase(caseId, result, reasonText);
  message.success('已记录');
  // 本地更新，避免全量刷新
  const c = cases.value.find((x: any) => x.id === caseId);
  if (c) {
    c.result = result;
    c.reason = reasonText || null;
    c.executedAt = new Date().toISOString();
  }
  rebuildTree();
  selectedCase.value = { ...selectedCase.value, result, reason: reasonText || null };
  // 自动跳到下一条
  const idx = cases.value.findIndex((x: any) => x.id === caseId);
  if (idx >= 0 && idx < cases.value.length - 1) selectedCase.value = cases.value[idx + 1];
}

function openReasonModal(action: string) {
  if (!selectedCase.value) return;
  reasonModal.value = { visible: true, action, text: '' };
}

async function submitReason() {
  if (!reasonModal.value.text.trim()) { message.error('请填写原因'); return; }
  reasonModal.value.visible = false;
  await doExecute(reasonModal.value.action, reasonModal.value.text.trim());
}

async function removeCase(caseId: string) {
  await testPlanApi.removeCase(caseId);
  message.success('已移除');
  await loadData();
}

onMounted(loadData);
</script>

<style scoped>
.exec-page { display:flex; flex-direction:column; height:100vh; background:#f5f5f5; }
.exec-header { display:flex; align-items:center; justify-content:space-between; background:#fff; border-bottom:1px solid #e8e8e8; padding:10px 20px; flex-shrink:0; }
.header-left { display:flex; align-items:center; gap:12px; }
.header-right { display:flex; align-items:center; gap:8px; }
.plan-name { font-size:15px; }
.stat-group { font-size:13px; color:#666; display:flex; gap:12px; margin-left:8px; }
.c-pass { color:#52c41a; font-weight:600; }
.c-fail { color:#ff4d4f; font-weight:600; }
.c-skip { color:#faad14; font-weight:600; }
.label { font-size:13px; color:#999; }
.exec-filter { display:flex; gap:8px; align-items:center; padding:10px 20px; background:#fff; border-bottom:1px solid #f0f0f0; flex-shrink:0; }
.filter-label { font-size:13px; color:#666; }
.exec-body { display:flex; flex:1; overflow:hidden; }
.exec-left { flex:1; overflow:auto; background:#fff; }
.exec-left :deep(.ant-table-row) { cursor:pointer; }
.exec-right { width:400px; flex-shrink:0; display:flex; flex-direction:column; background:#fff; border-left:1px solid #f0f0f0; }
.exec-right.empty { justify-content:center; align-items:center; }
.leaf-text { font-weight:normal; }
:deep(.selected-row) td { background:#e6f4ff !important; }
:deep(.group-row) td { background:#fafafa !important; font-weight:600; }

/* 执行结果下拉框字体颜色 */
:deep(.res-select.res-pending) .ant-select-selection-item { color: #292929; }
:deep(.res-select.res-pass) .ant-select-selection-item { color:#52c41a;  }
:deep(.res-select.res-fail) .ant-select-selection-item { color:#ff4d4f;  }
:deep(.res-select.res-skip) .ant-select-selection-item { color:#faad14;}

/* 用例详情样式 */
.detail-header { display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #f0f0f0; font-weight:600; font-size:14px; }
.detail-body { flex:1; overflow:auto; padding:16px 20px; }
.detail-section { margin-bottom:18px; }
.section-label { font-size:13px; color:#8c8c8c; margin-bottom:6px; }
.section-value { font-size:14px; color:#333; line-height:1.7; }
.title-value { font-weight:600; font-size:15px; color:#222; }
.path-value { color:#333; font-size:13px; }

/* 属性列表 */
.attr-list { list-style:none; padding:0; margin:0; }
.attr-item { display:flex; align-items:center; gap:8px; font-size:13px; padding:5px 0; }
.attr-item::before { content:'•'; color:#1677ff; font-size:16px; font-weight:bold; }
.attr-name { color:#595959; white-space:nowrap; min-width:60px; }
.attr-item :deep(.ant-tag) { border-radius:10px; }

.reason-value { color:#ff4d4f; background:#fff2f0; padding:8px 12px; border-radius:6px; border:1px solid #ffccc7; font-size:13px; line-height:1.6; white-space:pre-wrap; }
.link-edit { font-size:13px; color:#1677ff; cursor:pointer; }
.link-edit:hover { text-decoration:underline; }
.detail-footer { padding:12px 16px; border-top:1px solid #f0f0f0; }
.footer-nav { display:flex; justify-content:center; gap:12px; margin-bottom:10px; }
.footer-actions { display:flex; justify-content:center; gap:10px; }
.btn-pass { background:#52c41a; border-color:#52c41a; color:#fff; }
.btn-pass:hover { background:#73d13d; border-color:#73d13d; color:#fff; }
.btn-fail { background:#ff4d4f; border-color:#ff4d4f; color:#fff; }
.btn-fail:hover { background:#ff7875; border-color:#ff7875; color:#fff; }
.btn-skip { background:#faad14; border-color:#faad14; color:#fff; }
.btn-skip:hover { background: #f4c261; border-color: #f6c96e; color:#fff; }
.rpt-section h4 { margin-bottom:12px; font-size:15px; }
.rpt-overview { display:flex; align-items:center; gap:32px; }
.rpt-nums { display:flex; gap:24px; }
.num { display:flex; flex-direction:column; align-items:center; }
.val { font-size:22px; font-weight:700; color:#333; }
.lbl { font-size:12px; color:#999; }
.rpt-sub { display:flex; gap:16px; margin-top:12px; font-size:13px; }
.ep-row { display:flex; align-items:center; }
.ep-name { width:80px; font-size:13px; flex-shrink:0; }
.ep-pct { font-size:12px; color:#666; white-space:nowrap; }
.ep-detail { font-size:12px; color:#999; padding-left:80px; }
</style>
