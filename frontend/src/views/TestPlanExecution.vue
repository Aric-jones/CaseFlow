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
        <a-progress :percent="progressPercent" :stroke-color="'#52c41a'" size="small" style="width:120px" />
        <span class="c-pass">#通过 {{ stats.pass }}</span>
        <a-button type="link" @click="showReport = true">查看测试报告</a-button>
        <a-button type="link" @click="loadData"><ReloadOutlined /> 刷新用例</a-button>
      </div>
    </div>

    <!-- ===== 搜索栏 ===== -->
    <div class="exec-filter">
      <a-input v-model:value="filterKeyword" placeholder="用例名称" allow-clear style="width:200px" @change="applyFilter" />
      <a-select v-model:value="filterExecutor" placeholder="全部执行人" allow-clear style="width:150px"
        :options="executorOptions" @change="applyFilter" />
      <a-select v-model:value="filterResult" placeholder="全部结果" allow-clear style="width:150px"
        :options="resultOptions" @change="applyFilter" />
    </div>

    <!-- ===== 主体 ===== -->
    <div class="exec-body">
      <!-- 左侧：树形用例列表 -->
      <div class="exec-left">
        <a-table :columns="cols" :data-source="displayTree" row-key="_key" size="small"
          :pagination="false" :scroll="{ y: 'calc(100vh - 200px)' }"
          :row-class-name="rowClass" :custom-row="customRow"
          :expanded-row-keys="expandedKeys" @expand="onExpand"
          :expandable="{ childrenColumnName: 'children' }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">
              <span :style="{ fontWeight: record._isLeaf ? 'normal' : '500' }">
                <a-tag v-if="record._nodeType && record._nodeType !== 'TITLE'"
                  :color="ntColor(record._nodeType)" style="font-size:11px">{{ ntLabel(record._nodeType) }}</a-tag>
                {{ record._text }}
              </span>
            </template>
            <template v-if="column.key === 'executor'">
              <span v-if="record._isLeaf">{{ record._executorName }}</span>
            </template>
            <template v-if="column.key === 'result'">
              <a-tag v-if="record._isLeaf && record._result" :color="resColor(record._result)">{{ resLabel(record._result) }}</a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-popconfirm v-if="record._isLeaf" title="确认移除该用例？" @confirm.stop="removeCase(record._caseId)">
                <a-button type="link" size="small" danger @click.stop>移除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </div>

      <!-- 右侧：用例详情 -->
      <div class="exec-right" v-if="selectedCase">
        <div class="detail-header">
          <span>用例详情</span>
          <a-tag :color="resColor(selectedCase.result)">{{ resLabel(selectedCase.result) }}</a-tag>
        </div>
        <div class="detail-body">
          <div class="field"><label>用例标题</label><div>{{ selectedCase.title }}</div></div>
          <template v-for="ch in selectedCase.children || []" :key="ch.id">
            <div class="field"><label>{{ ntLabel(ch.nodeType) }}</label><div>{{ ch.text || '无' }}</div></div>
            <template v-for="sub in ch.children || []" :key="sub.id">
              <div class="field indent"><label>{{ ntLabel(sub.nodeType) || '子节点' }}</label><div>{{ sub.text }}</div></div>
            </template>
          </template>
          <div class="field" v-if="selectedCase.properties && Object.keys(selectedCase.properties).length">
            <label>属性</label>
            <div class="props-list">
              <span v-for="(v, k) in selectedCase.properties" :key="k" class="prop-tag">
                <b>{{ k }}</b>&nbsp;{{ Array.isArray(v) ? v.join(', ') : v }}
              </span>
            </div>
          </div>
          <div class="field" v-if="selectedCase.reason"><label>原因</label><div>{{ selectedCase.reason }}</div></div>
          <div class="field" v-if="selectedCase.caseSetName"><label>所属用例集</label><div>{{ selectedCase.caseSetName }}</div></div>
          <a-divider style="margin:12px 0" />
          <a style="font-size:13px" @click="goToMindMap">去修改用例 →</a>
        </div>
        <div class="detail-footer">
          <div class="nav-btns">
            <a-button size="small" :disabled="curIdx <= 0" @click="goPrev"><LeftOutlined /> 上一条</a-button>
            <a-button size="small" :disabled="curIdx >= leafCases.length - 1" @click="goNext">下一条 <RightOutlined /></a-button>
          </div>
          <div class="action-btns">
            <a-button class="btn-pass" @click="doExecute('PASS')">通过</a-button>
            <a-button class="btn-fail" @click="openReasonModal('FAIL')">不通过</a-button>
            <a-button class="btn-skip" @click="openReasonModal('SKIP')">跳过</a-button>
          </div>
        </div>
      </div>
      <div class="exec-right empty" v-else><a-empty description="请选择用例" /></div>
    </div>

    <!-- ===== 不通过/跳过原因弹窗 ===== -->
    <a-modal v-model:open="reasonModal.visible" :title="reasonModal.action === 'FAIL' ? '不通过原因' : '跳过原因'" @ok="submitReason" ok-text="确认">
      <a-textarea v-model:value="reasonModal.text" :placeholder="'请填写' + (reasonModal.action === 'FAIL' ? '不通过' : '跳过') + '原因'"
        :auto-size="{ minRows: 3, maxRows: 6 }" />
    </a-modal>

    <!-- ===== 测试报告弹窗 ===== -->
    <a-modal v-model:open="showReport" title="测试报告" :footer="null" width="680px">
      <div class="rpt-section">
        <h4>整体执行进度</h4>
        <div class="rpt-overview">
          <a-progress type="circle" :percent="progressPercent" :size="100" :stroke-color="'#52c41a'" />
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
import { ref, computed, onMounted, nextTick } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { ArrowLeftOutlined, ReloadOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue';
import { testPlanApi, mindNodeApi, caseSetApi } from '../api';
import type { TestPlan, MindNodeData } from '../types';

const route = useRoute();
const router = useRouter();
const planId = String(route.params.planId);

// ── 状态 ──────────────────────────────────────────────
const plan = ref<TestPlan | null>(null);
const cases = ref<any[]>([]);            // getCasesRich 返回的 DTO 列表
const displayTree = ref<any[]>([]);      // 组装后的树形数据（供 table）
const expandedKeys = ref<string[]>([]);  // 展开的行 key
const selectedCase = ref<any>(null);     // 当前选中的用例 DTO
const showReport = ref(false);
const filterKeyword = ref('');
const filterExecutor = ref<string | undefined>();
const filterResult = ref<string | undefined>();
const reasonModal = ref({ visible: false, action: '' as string, text: '' });

const cols = [
  { title: '测试用例', key: 'title', ellipsis: true },
  { title: '执行人', key: 'executor', width: 100 },
  { title: '执行结果', key: 'result', width: 100 },
  { title: '操作', key: 'action', width: 80 },
];
const resultOptions = [
  { value: 'PENDING', label: '待执行' }, { value: 'PASS', label: '通过' },
  { value: 'FAIL', label: '不通过' }, { value: 'SKIP', label: '跳过' },
];

// ── 统计 ──────────────────────────────────────────────
const stats = computed(() => {
  const all = cases.value;
  const p = all.filter(c => c.result === 'PASS').length;
  const f = all.filter(c => c.result === 'FAIL').length;
  const s = all.filter(c => c.result === 'SKIP').length;
  return { total: all.length, pass: p, fail: f, skip: s, pending: all.length - p - f - s, executed: p + f + s };
});
const progressPercent = computed(() => stats.value.total ? Math.round(stats.value.executed / stats.value.total * 100) : 0);

/** 叶节点用例列表（用于上/下一条导航） */
const leafCases = computed(() => cases.value);
const curIdx = computed(() => selectedCase.value ? leafCases.value.findIndex((c: any) => c.id === selectedCase.value.id) : -1);

/** 执行人下拉选项（从用例数据中提取） */
const executorOptions = computed(() => {
  const map = new Map<string, string>();
  cases.value.forEach((c: any) => { if (c.executorId && c.executorName) map.set(c.executorId, c.executorName); });
  return Array.from(map, ([value, label]) => ({ value, label }));
});

/** 报告弹窗 - 按执行人分组的进度 */
const executorProgress = computed(() => {
  const m = new Map<string, { name: string; total: number; pass: number; fail: number; skip: number; pending: number; executed: number }>();
  cases.value.forEach((c: any) => {
    const k = c.executorId || '_none', n = c.executorName || '未分配';
    if (!m.has(k)) m.set(k, { name: n, total: 0, pass: 0, fail: 0, skip: 0, pending: 0, executed: 0 });
    const e = m.get(k)!; e.total++;
    if (c.result === 'PASS') { e.pass++; e.executed++; }
    else if (c.result === 'FAIL') { e.fail++; e.executed++; }
    else if (c.result === 'SKIP') { e.skip++; e.executed++; }
    else e.pending++;
  });
  return Array.from(m.values());
});

// ── 工具函数 ──────────────────────────────────────────
function resLabel(r: string) { return r === 'PASS' ? '通过' : r === 'FAIL' ? '不通过' : r === 'SKIP' ? '跳过' : '待执行'; }
function resColor(r: string) { return r === 'PASS' ? 'success' : r === 'FAIL' ? 'error' : r === 'SKIP' ? 'warning' : 'default'; }
function ntLabel(t: string) { return ({ TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' } as any)[t] || t || ''; }
function ntColor(t: string) { return ({ PRECONDITION: 'blue', STEP: 'green', EXPECTED: 'orange', TITLE: 'purple' } as any)[t] || 'default'; }

// ── 数据加载 + 树形组装 ──────────────────────────────
/** 加载计划信息 + 用例列表 + 思维导图树，并组装展示树 */
async function loadData() {
  const [pRes, cRes] = await Promise.all([testPlanApi.get(planId), testPlanApi.getCases(planId)]);
  plan.value = pRes.data;
  cases.value = cRes.data;

  // 按 caseSetId 分组
  const csMap = new Map<string, any[]>();
  for (const c of cases.value) {
    const arr = csMap.get(c.caseSetId) || [];
    arr.push(c); csMap.set(c.caseSetId, arr);
  }

  // 并行加载每个用例集的思维导图树和名称
  const csIds = [...csMap.keys()];
  const [treeResults, csNames] = await Promise.all([
    Promise.all(csIds.map(id => mindNodeApi.tree(id).then(r => r.data).catch(() => []))),
    Promise.all(csIds.map(id => caseSetApi.get(id).then(r => r.data.name).catch(() => id))),
  ]);

  const tree: any[] = [];
  const allKeys: string[] = [];

  for (let i = 0; i < csIds.length; i++) {
    const csId = csIds[i];
    const planCases = csMap.get(csId)!;
    const caseNodeIds = new Set(planCases.map((c: any) => c.nodeId));
    // nodeId -> case DTO 映射
    const caseMap = new Map<string, any>();
    planCases.forEach((c: any) => caseMap.set(c.nodeId, c));

    const mindTree = treeResults[i];
    if (!mindTree.length) continue;

    // 过滤思维导图树，只保留包含测试计划用例的分支
    const csKey = `cs-${csId}`;
    const filteredChildren = filterMindTree(mindTree[0]?.children || [], caseNodeIds, caseMap, csId);
    if (!filteredChildren.length) continue;

    allKeys.push(csKey);
    collectKeys(filteredChildren, allKeys);

    tree.push({
      _key: csKey, _text: `* ${csNames[i]}`, _isGroup: true,
      children: applyFilter2(filteredChildren),
    });
  }

  displayTree.value = tree;
  expandedKeys.value = allKeys;

  // 恢复选中
  if (selectedCase.value) {
    selectedCase.value = cases.value.find(c => c.id === selectedCase.value.id) || cases.value[0] || null;
  } else if (cases.value.length) {
    selectedCase.value = cases.value[0];
  }
}

/** 递归过滤思维导图树，只保留包含测试计划用例节点的分支 */
function filterMindTree(nodes: MindNodeData[], caseNodeIds: Set<string>, caseMap: Map<string, any>, csId: string): any[] {
  const result: any[] = [];
  for (const node of nodes) {
    const isCaseTitle = caseNodeIds.has(node.id!);
    if (isCaseTitle) {
      // 这是一个测试计划用例的 TITLE 节点，渲染它的完整子树
      const caseDto = caseMap.get(node.id!)!;
      result.push(buildCaseTree(node, caseDto, csId));
    } else {
      // 不是用例节点，递归检查子节点是否包含用例
      const childResult = filterMindTree(node.children || [], caseNodeIds, caseMap, csId);
      if (childResult.length) {
        const key = `node-${csId}-${node.id}`;
        result.push({ _key: key, _text: `* ${node.text}`, _nodeType: node.nodeType, children: childResult });
      }
    }
  }
  return result;
}

/** 将一个测试用例 TITLE 节点构建为表格的子树，叶节点挂载执行信息 */
function buildCaseTree(titleNode: MindNodeData, caseDto: any, csId: string): any {
  const titleKey = `title-${csId}-${titleNode.id}`;
  const children = buildChildNodes(titleNode.children || [], caseDto, csId);
  return { _key: titleKey, _text: titleNode.text, _nodeType: 'TITLE', _caseId: caseDto.id, children: children.length ? children : undefined };
}

/** 递归构建子节点行，找到最深的叶节点挂载执行信息 */
function buildChildNodes(nodes: MindNodeData[], caseDto: any, csId: string): any[] {
  return nodes.map(n => {
    const key = `child-${csId}-${n.id}`;
    const subChildren = buildChildNodes(n.children || [], caseDto, csId);
    const isLeaf = subChildren.length === 0;
    return {
      _key: key, _text: n.text, _nodeType: n.nodeType,
      // 叶节点挂载执行信息
      ...(isLeaf ? { _isLeaf: true, _caseId: caseDto.id, _result: caseDto.result, _executorName: caseDto.executorName } : {}),
      children: isLeaf ? undefined : subChildren,
    };
  });
}

/** 收集所有 key 用于默认展开 */
function collectKeys(nodes: any[], keys: string[]) {
  for (const n of nodes) { keys.push(n._key); if (n.children) collectKeys(n.children, keys); }
}

/** 搜索过滤后重新构建展示树（在已加载的数据上过滤） */
function applyFilter() { nextTick(() => loadData()); }

/** 对已过滤的子树再应用关键词/执行人/结果过滤 */
function applyFilter2(nodes: any[]): any[] {
  if (!filterKeyword.value && !filterExecutor.value && !filterResult.value) return nodes;
  return nodes.map(n => {
    if (n._isLeaf) {
      const dto = cases.value.find((c: any) => c.id === n._caseId);
      if (!dto) return null;
      if (filterKeyword.value && !(dto.title || '').toLowerCase().includes(filterKeyword.value.toLowerCase())) return null;
      if (filterExecutor.value && dto.executorId !== filterExecutor.value) return null;
      if (filterResult.value && dto.result !== filterResult.value) return null;
      return n;
    }
    const filtered = applyFilter2(n.children || []);
    if (!filtered.length) return null;
    return { ...n, children: filtered };
  }).filter(Boolean);
}

// ── 交互 ──────────────────────────────────────────────
function onExpand(_: boolean, record: any) {
  const k = record._key;
  const idx = expandedKeys.value.indexOf(k);
  if (idx >= 0) expandedKeys.value.splice(idx, 1); else expandedKeys.value.push(k);
}

/** 点击行选中对应的用例 DTO */
function customRow(record: any) {
  return {
    onClick: () => {
      if (record._caseId) {
        selectedCase.value = cases.value.find((c: any) => c.id === record._caseId) || null;
      }
    },
  };
}

function rowClass(record: any) {
  if (record._isLeaf && selectedCase.value?.id === record._caseId) return 'selected-row';
  if (record._isGroup) return 'group-row';
  return '';
}

function goPrev() { const i = curIdx.value; if (i > 0) selectedCase.value = leafCases.value[i - 1]; }
function goNext() { const i = curIdx.value; if (i < leafCases.value.length - 1) selectedCase.value = leafCases.value[i + 1]; }
function goToMindMap() { if (selectedCase.value?.caseSetId) router.push(`/mind-map/${selectedCase.value.caseSetId}`); }

/** 通过：直接执行并跳转下一条 */
async function doExecute(result: string, reasonText?: string) {
  if (!selectedCase.value) return;
  await testPlanApi.executeCase(selectedCase.value.id, result, reasonText);
  message.success('已记录');
  const prevId = selectedCase.value.id;
  await loadData();
  // 自动跳转到下一条
  const idx = leafCases.value.findIndex((c: any) => c.id === prevId);
  if (idx >= 0 && idx < leafCases.value.length - 1) {
    selectedCase.value = leafCases.value[idx + 1];
  }
}

/** 打开不通过/跳过的原因弹窗 */
function openReasonModal(action: string) {
  if (!selectedCase.value) return;
  reasonModal.value = { visible: true, action, text: '' };
}

/** 提交原因并执行 */
async function submitReason() {
  if (!reasonModal.value.text.trim()) { message.error('请填写原因'); return; }
  reasonModal.value.visible = false;
  await doExecute(reasonModal.value.action, reasonModal.value.text.trim());
}

/** 移除用例 */
async function removeCase(caseId: string) {
  await testPlanApi.removeCase(caseId);
  message.success('已移除');
  await loadData();
}

onMounted(loadData);
</script>

<style scoped>
.exec-page { display: flex; flex-direction: column; height: 100vh; background: #f5f5f5; }

/* 顶栏 */
.exec-header { display:flex; align-items:center; justify-content:space-between; background:#fff; border-bottom:1px solid #e8e8e8; padding:10px 20px; flex-shrink:0; }
.header-left { display:flex; align-items:center; gap:12px; }
.header-right { display:flex; align-items:center; gap:8px; }
.plan-name { font-size:15px; }
.stat-group { font-size:13px; color:#666; display:flex; gap:12px; margin-left:8px; }
.c-pass { color:#52c41a; font-weight:600; }
.c-fail { color:#ff4d4f; font-weight:600; }
.c-skip { color:#faad14; font-weight:600; }
.label { font-size:13px; color:#999; }

/* 搜索栏 */
.exec-filter { display:flex; gap:12px; padding:10px 20px; background:#fff; border-bottom:1px solid #f0f0f0; flex-shrink:0; }

/* 主体 */
.exec-body { display:flex; flex:1; overflow:hidden; }
.exec-left { flex:1; overflow:auto; background:#fff; }
.exec-right { width:380px; flex-shrink:0; display:flex; flex-direction:column; background:#fff; border-left:1px solid #f0f0f0; }
.exec-right.empty { justify-content:center; align-items:center; }

/* 详情 */
.detail-header { display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #f0f0f0; font-weight:600; font-size:14px; }
.detail-body { flex:1; overflow:auto; padding:12px 16px; }
.field { margin-bottom:12px; }
.field.indent { padding-left:16px; }
.field label { display:block; font-size:12px; color:#999; margin-bottom:2px; }
.field div { font-size:13px; color:#333; }
.props-list { display:flex; flex-wrap:wrap; gap:6px; }
.prop-tag { background:#f5f5f5; padding:2px 8px; border-radius:4px; font-size:12px; }
.detail-footer { padding:12px 16px; border-top:1px solid #f0f0f0; display:flex; justify-content:space-between; align-items:center; }
.nav-btns { display:flex; gap:8px; }
.action-btns { display:flex; gap:8px; }
.btn-pass { background:#52c41a; border-color:#52c41a; color:#fff; }
.btn-pass:hover { background:#73d13d; border-color:#73d13d; color:#fff; }
.btn-fail { background:#ff4d4f; border-color:#ff4d4f; color:#fff; }
.btn-fail:hover { background:#ff7875; border-color:#ff7875; color:#fff; }
.btn-skip { background:#1677ff; border-color:#1677ff; color:#fff; }
.btn-skip:hover { background:#4096ff; border-color:#4096ff; color:#fff; }

/* 表格行样式 */
:deep(.selected-row) td { background:#e6f4ff !important; }
:deep(.group-row) td { background:#fafafa !important; font-weight:600; }

/* 报告弹窗 */
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
