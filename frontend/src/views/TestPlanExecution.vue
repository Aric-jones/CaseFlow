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
        <span class="c-pass">#通过 {{ stats.pass }}</span>
        <a-button type="link" @click="showReport = true">查看测试报告</a-button>
        <a-button type="link" @click="loadData"><ReloadOutlined /> 刷新用例</a-button>
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
      <!-- 左侧：树形用例列表 -->
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
              <span v-if="record._isLeaf">{{ record._executorName || '未分配' }}</span>
            </template>
            <template v-if="column.key === 'result'">
              <a-tag v-if="record._isLeaf" :color="resColor(record._result)">{{ resLabel(record._result) }}</a-tag>
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
          <!-- 用例标题 -->
          <div class="field">
            <label>用例标题</label>
            <div>{{ selectedCase.title }}</div>
          </div>
          <!-- 前置条件 / 步骤 / 预期结果（从子树中提取） -->
          <div class="field" v-if="caseDetail.precondition !== undefined">
            <label>前置条件</label>
            <div>{{ caseDetail.precondition || '无' }}</div>
          </div>
          <div class="field" v-if="caseDetail.step !== undefined">
            <label>步骤</label>
            <div style="white-space:pre-wrap">{{ caseDetail.step || '无' }}</div>
          </div>
          <div class="field" v-if="caseDetail.expected !== undefined">
            <label>预期结果</label>
            <div style="white-space:pre-wrap">{{ caseDetail.expected || '无' }}</div>
          </div>
          <!-- 属性（分点展示，标记翻译中文） -->
          <div class="field" v-if="selectedCase.properties && Object.keys(selectedCase.properties).length">
            <label>属性</label>
            <ul class="props-ul">
              <li v-for="(v, k) in selectedCase.properties" :key="k">
                <b>{{ propLabel(String(k)) }}</b>&nbsp;
                <template v-if="String(k) === 'mark'">
                  <a-tag :color="markColor(v)">{{ markLabel(v) }}</a-tag>
                </template>
                <template v-else>{{ Array.isArray(v) ? v.join(', ') : v }}</template>
              </li>
            </ul>
          </div>
          <a-divider style="margin:12px 0" />
          <a class="link-edit" @click="goToMindMap">去修改用例 →</a>
        </div>
        <div class="detail-footer">
          <div class="footer-nav">
            <a-button size="small" :disabled="curIdx <= 0" @click="goPrev"><LeftOutlined /> 上一条</a-button>
            <a-button size="small" :disabled="curIdx >= leafCases.length - 1" @click="goNext">下一条 <RightOutlined /></a-button>
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

    <!-- ===== 不通过/跳过原因弹窗 ===== -->
    <a-modal v-model:open="reasonModal.visible" :title="reasonModal.action === 'FAIL' ? '不通过原因' : '跳过原因'" @ok="submitReason" ok-text="确认">
      <a-textarea v-model:value="reasonModal.text" :placeholder="'请填写' + (reasonModal.action === 'FAIL' ? '不通过' : '跳过') + '原因（必填）'"
        :auto-size="{ minRows: 3, maxRows: 6 }" />
    </a-modal>

    <!-- ===== 测试报告弹窗 ===== -->
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
import { testPlanApi, mindNodeApi, caseSetApi } from '../api';
import type { TestPlan, MindNodeData } from '../types';

const route = useRoute();
const router = useRouter();
const planId = String(route.params.planId);

// ── 状态 ──────────────────────────────────────────────
const plan = ref<TestPlan | null>(null);
/** 后端 getCasesRich 返回的用例 DTO 列表 */
const cases = ref<any[]>([]);
/** 缓存的原始思维导图树，按 caseSetId 索引 */
const rawTreeCache = ref<Map<string, MindNodeData[]>>(new Map());
const rawNameCache = ref<Map<string, string>>(new Map());
/** 最终展示在 Table 中的树形数据 */
const displayTree = ref<any[]>([]);
const expandedKeys = ref<string[]>([]);
const selectedCase = ref<any>(null);
const showReport = ref(false);
const filterKeyword = ref('');
const filterExecutor = ref<string | undefined>();
const filterResult = ref<string | undefined>();
const reasonModal = ref({ visible: false, action: '', text: '' });

const cols = [
  { title: '测试用例', key: 'title', ellipsis: true },
  { title: '执行人', key: 'executor', width: 100 },
  { title: '执行结果', key: 'result', width: 100 },
  { title: '操作', key: 'action', width: 80 },
];
const resultOpts = [
  { value: 'PENDING', label: '待执行' }, { value: 'PASS', label: '通过' },
  { value: 'FAIL', label: '不通过' }, { value: 'SKIP', label: '跳过' },
];

// ── 统计 ──────────────────────────────────────────────
const stats = computed(() => {
  const a = cases.value;
  const p = a.filter(c => c.result === 'PASS').length;
  const f = a.filter(c => c.result === 'FAIL').length;
  const s = a.filter(c => c.result === 'SKIP').length;
  return { total: a.length, pass: p, fail: f, skip: s, pending: a.length - p - f - s, executed: p + f + s };
});
const progressPct = computed(() => stats.value.total ? Math.round(stats.value.executed / stats.value.total * 100) : 0);
const leafCases = computed(() => cases.value);
const curIdx = computed(() => selectedCase.value ? leafCases.value.findIndex((c: any) => c.id === selectedCase.value.id) : -1);

/** 执行人下拉选项 */
const executorOptions = computed(() => {
  const m = new Map<string, string>();
  cases.value.forEach((c: any) => { if (c.executorId && c.executorName) m.set(c.executorId, c.executorName); });
  return Array.from(m, ([value, label]) => ({ value, label }));
});

/** 报告弹窗 - 执行人分组进度 */
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

/** 从选中用例的子树中提取前置条件 / 步骤 / 预期结果文本 */
const caseDetail = computed(() => {
  const sc = selectedCase.value;
  if (!sc) return {};
  const r: Record<string, string | undefined> = {};
  function walk(nodes: any[]) {
    for (const n of nodes) {
      if (n.nodeType === 'PRECONDITION') r.precondition = n.text;
      else if (n.nodeType === 'STEP') r.step = n.text;
      else if (n.nodeType === 'EXPECTED') r.expected = n.text;
      if (n.children?.length) walk(n.children);
    }
  }
  walk(sc.children || []);
  return r;
});

// ── 工具函数 ──────────────────────────────────────────
function resLabel(r: string) { return ({ PASS: '通过', FAIL: '不通过', SKIP: '跳过' } as any)[r] || '待执行'; }
function resColor(r: string) { return ({ PASS: 'success', FAIL: 'error', SKIP: 'warning' } as any)[r] || 'default'; }
function ntLabel(t: string) { return ({ TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' } as any)[t] || t || ''; }
function ntColor(t: string) { return ({ TITLE: 'purple', PRECONDITION: 'blue', STEP: 'green', EXPECTED: 'orange' } as any)[t] || 'default'; }
/** 属性 key 翻译 */
function propLabel(k: string) { return ({ mark: '标记', priority: '优先级', category: '分类', coverage: '覆盖集', device: '设备集' } as any)[k] || k; }
/** 标记值翻译 */
function markLabel(v: any) { return ({ P1: 'P1', P2: 'P2', P3: 'P3', not_reviewed: '未评审', pass: '通过', fail: '不通过' } as any)[v] || v; }
function markColor(v: any) { return ({ P1: 'red', P2: 'orange', P3: 'blue', pass: 'success', fail: 'error' } as any)[v] || 'default'; }

// ── 数据加载 ─────────────────────────────────────────
/** 首次/刷新加载：获取计划信息、用例列表、思维导图树 */
async function loadData() {
  const [pRes, cRes] = await Promise.all([testPlanApi.get(planId), testPlanApi.getCases(planId)]);
  plan.value = pRes.data;
  cases.value = cRes.data;

  // 按 caseSetId 分组
  const csMap = new Map<string, any[]>();
  for (const c of cases.value) { (csMap.get(c.caseSetId) || (() => { const a: any[] = []; csMap.set(c.caseSetId, a); return a; })()).push(c); }

  // 加载尚未缓存的思维导图树
  const csIds = [...csMap.keys()];
  const toLoad = csIds.filter(id => !rawTreeCache.value.has(id));
  if (toLoad.length) {
    const [trees, names] = await Promise.all([
      Promise.all(toLoad.map(id => mindNodeApi.tree(id).then(r => r.data).catch(() => [] as MindNodeData[]))),
      Promise.all(toLoad.map(id => caseSetApi.get(id).then(r => r.data.name).catch(() => id))),
    ]);
    toLoad.forEach((id, i) => { rawTreeCache.value.set(id, trees[i]); rawNameCache.value.set(id, names[i]); });
  }

  rebuildTree();

  // 恢复选中
  if (selectedCase.value) {
    selectedCase.value = cases.value.find((c: any) => c.id === selectedCase.value.id) || cases.value[0] || null;
  } else if (cases.value.length) {
    selectedCase.value = cases.value[0];
  }
}

/** 根据当前 cases + 缓存的思维导图树 + 筛选条件，重新组装展示树 */
function rebuildTree() {
  const csMap = new Map<string, any[]>();
  for (const c of cases.value) { (csMap.get(c.caseSetId) || (() => { const a: any[] = []; csMap.set(c.caseSetId, a); return a; })()).push(c); }

  const tree: any[] = [];
  const allKeys: string[] = [];

  for (const [csId, planCases] of csMap) {
    const mindTree = rawTreeCache.value.get(csId);
    if (!mindTree?.length) continue;

    const caseNodeIds = new Set(planCases.map((c: any) => c.nodeId));
    const caseMap = new Map<string, any>();
    planCases.forEach((c: any) => caseMap.set(c.nodeId, c));

    const filtered = filterBranches(mindTree[0]?.children || [], caseNodeIds, caseMap, csId);
    if (!filtered.length) continue;

    const csKey = `cs-${csId}`;
    allKeys.push(csKey);
    collectKeys(filtered, allKeys);
    tree.push({ _key: csKey, _text: rawNameCache.value.get(csId) || csId, _isGroup: true, children: filtered });
  }

  displayTree.value = tree;
  expandedKeys.value = [...allKeys];
}

/** 递归过滤思维导图树，只保留包含测试计划用例的分支 */
function filterBranches(nodes: MindNodeData[], caseIds: Set<string>, caseMap: Map<string, any>, csId: string): any[] {
  const out: any[] = [];
  for (const nd of nodes) {
    if (caseIds.has(nd.id!)) {
      // 用例 TITLE 节点：构建完整子树，叶节点挂执行信息
      const dto = caseMap.get(nd.id!)!;
      // 筛选条件检查
      if (filterKeyword.value && !(nd.text || '').toLowerCase().includes(filterKeyword.value.toLowerCase())) continue;
      if (filterExecutor.value && dto.executorId !== filterExecutor.value) continue;
      if (filterResult.value && dto.result !== filterResult.value) continue;
      out.push(buildCase(nd, dto, csId));
    } else {
      const sub = filterBranches(nd.children || [], caseIds, caseMap, csId);
      if (sub.length) out.push({ _key: `n-${csId}-${nd.id}`, _text: nd.text, _nodeType: nd.nodeType, children: sub });
    }
  }
  return out;
}

/** 将 TITLE 节点和子树构建为表格行，叶节点挂载执行信息 */
function buildCase(title: MindNodeData, dto: any, csId: string): any {
  const kids = buildKids(title.children || [], dto, csId);
  return { _key: `t-${csId}-${title.id}`, _text: title.text, _nodeType: title.nodeType || 'TITLE', _caseId: dto.id, children: kids.length ? kids : undefined };
}

/** 递归构建子行，最深叶节点标记 _isLeaf 并挂载执行数据 */
function buildKids(nodes: MindNodeData[], dto: any, csId: string): any[] {
  return nodes.map(n => {
    const sub = buildKids(n.children || [], dto, csId);
    const leaf = sub.length === 0;
    return {
      _key: `c-${csId}-${n.id}`, _text: n.text, _nodeType: n.nodeType,
      ...(leaf ? { _isLeaf: true, _caseId: dto.id, _result: dto.result, _executorName: dto.executorName } : {}),
      children: leaf ? undefined : sub,
    };
  });
}

/** 递归收集所有 key（用于默认全部展开） */
function collectKeys(nodes: any[], keys: string[]) {
  for (const n of nodes) { keys.push(n._key); if (n.children) collectKeys(n.children, keys); }
}

// ── 表格交互 ─────────────────────────────────────────
function onExpand(_expanded: boolean, record: any) {
  const k = record._key;
  const i = expandedKeys.value.indexOf(k);
  if (i >= 0) expandedKeys.value.splice(i, 1); else expandedKeys.value.push(k);
}

/** 点击行：选中对应用例 + 展开/收起 */
function customRow(record: any) {
  return {
    onClick: () => {
      // 选中用例
      if (record._caseId) selectedCase.value = cases.value.find((c: any) => c.id === record._caseId) || null;
      // 点击节点展开/收起
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

function goPrev() { if (curIdx.value > 0) selectedCase.value = leafCases.value[curIdx.value - 1]; }
function goNext() { if (curIdx.value < leafCases.value.length - 1) selectedCase.value = leafCases.value[curIdx.value + 1]; }
function goToMindMap() { if (selectedCase.value?.caseSetId) router.push(`/mind-map/${selectedCase.value.caseSetId}`); }

// ── 执行操作 ─────────────────────────────────────────
/** 执行通过（直接提交并自动下一条） */
async function doExecute(result: string, reasonText?: string) {
  if (!selectedCase.value) return;
  await testPlanApi.executeCase(selectedCase.value.id, result, reasonText);
  message.success('已记录');
  const prevId = selectedCase.value.id;
  await loadData();
  const idx = leafCases.value.findIndex((c: any) => c.id === prevId);
  if (idx >= 0 && idx < leafCases.value.length - 1) selectedCase.value = leafCases.value[idx + 1];
}

/** 打开不通过/跳过原因弹窗 */
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
  await testPlanApi.removeCase(caseId); message.success('已移除'); await loadData();
}

onMounted(loadData);
</script>

<style scoped>
.exec-page { display:flex; flex-direction:column; height:100vh; background:#f5f5f5; }

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
.exec-filter { display:flex; gap:8px; align-items:center; padding:10px 20px; background:#fff; border-bottom:1px solid #f0f0f0; flex-shrink:0; }
.filter-label { font-size:13px; color:#666; }

/* 主体 */
.exec-body { display:flex; flex:1; overflow:hidden; }
.exec-left { flex:1; overflow:auto; background:#fff; }
.exec-left :deep(.ant-table-row) { cursor:pointer; }
.exec-right { width:380px; flex-shrink:0; display:flex; flex-direction:column; background:#fff; border-left:1px solid #f0f0f0; }
.exec-right.empty { justify-content:center; align-items:center; }

/* 表格 */
.leaf-text { font-weight:normal; }
:deep(.selected-row) td { background:#e6f4ff !important; }
:deep(.group-row) td { background:#fafafa !important; font-weight:600; }

/* 详情面板 */
.detail-header { display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #f0f0f0; font-weight:600; font-size:14px; }
.detail-body { flex:1; overflow:auto; padding:12px 16px; }
.field { margin-bottom:14px; }
.field.indent { padding-left:16px; }
.field label { display:block; font-size:12px; color:#999; margin-bottom:4px; font-weight:600; }
.field div { font-size:13px; color:#333; line-height:1.6; }
.props-ul { list-style:disc; padding-left:20px; margin:0; }
.props-ul li { font-size:13px; margin-bottom:4px; }
.link-edit { font-size:13px; color:#1677ff; cursor:pointer; }
.link-edit:hover { text-decoration:underline; }

/* 底部按钮：分两行 */
.detail-footer { padding:12px 16px; border-top:1px solid #f0f0f0; }
.footer-nav { display:flex; justify-content:center; gap:12px; margin-bottom:10px; }
.footer-actions { display:flex; justify-content:center; gap:10px; }
.btn-pass { background:#52c41a; border-color:#52c41a; color:#fff; }
.btn-pass:hover { background:#73d13d; border-color:#73d13d; color:#fff; }
.btn-fail { background:#ff4d4f; border-color:#ff4d4f; color:#fff; }
.btn-fail:hover { background:#ff7875; border-color:#ff7875; color:#fff; }
.btn-skip { background:#1677ff; border-color:#1677ff; color:#fff; }
.btn-skip:hover { background:#4096ff; border-color:#4096ff; color:#fff; }

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
