<template>
  <div class="exec-page">
    <!-- 顶栏 -->
    <div class="exec-header">
      <div class="header-left">
        <el-button text @click="$router.push('/test-plans')">
          <el-icon><ArrowLeft /></el-icon>
        </el-button>
        <strong class="plan-name">{{ plan?.name }}</strong>
        <div class="stat-group">
          <span>已执行 <b>{{ stats.executed }}/{{ stats.total }}</b></span>
          <span class="c-fail">不通过 {{ stats.fail }}</span>
          <span class="c-pass">通过 {{ stats.pass }}</span>
          <span class="c-skip">跳过 {{ stats.skip }}</span>
        </div>
      </div>
      <div class="header-right">
        <span style="font-size:13px;color:#909399">执行进度</span>
        <el-progress :percentage="progressPct" color="#52c41a" :show-text="false" style="width:120px" />
        <el-button text type="primary" @click="showReport = true">查看测试报告</el-button>
        <el-button text type="primary" :loading="refreshing" @click="doRefresh">
          <el-icon><Refresh /></el-icon> 刷新用例
        </el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="exec-filter">
      <span class="filter-label">用例名称</span>
      <el-input v-model="filterKeyword" placeholder="搜索" clearable style="width:200px" @input="rebuildTree" />
      <span class="filter-label">执行结果</span>
      <el-select v-model="filterResult" placeholder="全部结果" clearable style="width:140px" @change="rebuildTree">
        <el-option v-for="o in resultOpts" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
    </div>

    <!-- 主体 -->
    <div class="exec-body">
      <!-- 左侧树表格 -->
      <div class="exec-left">
        <el-table ref="treeTableRef" :data="displayTree" row-key="_key" border
          :tree-props="{ children: 'children' }" default-expand-all
          :row-class-name="rowClass" @row-click="onRowClick" style="width:100%">
          <el-table-column label="测试用例" min-width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="row._nodeType" :type="ntType(row._nodeType)"
                size="small" style="margin-right:4px;font-size:11px">{{ ntLabel(row._nodeType) }}</el-tag>
              <span :class="{ 'font-bold': row._isGroup }">{{ row._text }}</span>
            </template>
          </el-table-column>
          <el-table-column label="执行人" width="120">
            <template #default="{ row }">
              <span v-if="row._isLeaf" class="executor-text">{{ row._executorName || '未分配' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="执行结果" width="110">
            <template #default="{ row }">
              <span v-if="row._isLeaf" :class="'result-text result-' + (row._result || 'PENDING').toLowerCase()">
                {{ resLabel(row._result) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" :resizable="false">
            <template #default="{ row }">
              <el-popconfirm v-if="row._isLeaf" title="确认移除该用例？" @confirm.stop="removeCase(row._caseId)">
                <template #reference>
                  <el-button text type="danger" size="small" @click.stop>移除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 右侧详情 -->
      <div class="exec-right" v-if="selectedCase">
        <div class="detail-header">
          <span>用例详情</span>
          <el-tag :type="resType(selectedCase.result)" size="small">{{ resLabel(selectedCase.result) }}</el-tag>
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
                  <el-tag :type="markType(item.val)" size="small" round>{{ markLabel(item.val) }}</el-tag>
                </template>
                <template v-else-if="Array.isArray(item.val)">
                  <el-tag v-for="v in item.val" :key="v" type="primary" size="small" round>{{ v }}</el-tag>
                </template>
                <template v-else>
                  <el-tag type="primary" size="small" round>{{ item.val }}</el-tag>
                </template>
              </li>
            </ul>
          </div>
          <div class="detail-section" v-if="selectedCase.reason">
            <div class="section-label">{{ selectedCase.result === 'FAIL' ? '不通过原因' : '跳过原因' }}</div>
            <div class="reason-value">{{ selectedCase.reason }}</div>
          </div>
          <div style="margin-top:16px">
            <a class="link-edit" @click="goToMindMap">去修改用例 →</a>
          </div>
        </div>
        <div class="detail-footer">
          <div class="footer-nav">
            <el-button size="small" :disabled="curIdx <= 0" @click="goPrev">
              <el-icon><ArrowLeft /></el-icon> 上一条
            </el-button>
            <el-button size="small" :disabled="curIdx >= cases.length - 1" @click="goNext">
              下一条 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
          <div class="footer-actions">
            <el-button class="btn-pass" :loading="locks.execute" @click="doExecute('PASS')">通过</el-button>
            <el-button class="btn-fail" :disabled="locks.execute" @click="openReasonModal('FAIL')">不通过</el-button>
            <el-button class="btn-skip" :disabled="locks.execute" @click="openReasonModal('SKIP')">跳过</el-button>
          </div>
        </div>
      </div>
      <div class="exec-right empty" v-else>
        <el-empty description="请选择用例" />
      </div>
    </div>

    <!-- 原因弹窗 -->
    <el-dialog v-model="reasonModal.visible" :title="reasonModal.action === 'FAIL' ? '不通过原因' : '跳过原因'" width="480px">
      <el-input v-model="reasonModal.text" type="textarea" :rows="3"
        :placeholder="'请填写' + (reasonModal.action === 'FAIL' ? '不通过' : '跳过') + '原因（必填）'" />
      <template #footer>
        <el-button @click="reasonModal.visible = false">取消</el-button>
        <el-button type="primary" :loading="locks.submitReason" @click="submitReason">确认</el-button>
      </template>
    </el-dialog>

    <!-- 测试报告弹窗 -->
    <el-dialog v-model="showReport" title="测试报告" width="680px">
      <div class="rpt-section">
        <h4>整体执行进度</h4>
        <div class="rpt-overview">
          <el-progress type="circle" :percentage="progressPct" color="#52c41a" :width="100" />
          <div class="rpt-nums">
            <div class="num"><span class="val">{{ stats.total }}</span><span class="lbl">总用例</span></div>
            <div class="num"><span class="val">{{ stats.executed }}</span><span class="lbl">已执行</span></div>
            <div class="num"><span class="val c-pass">{{ stats.pass }}</span><span class="lbl">通过</span></div>
          </div>
        </div>
        <div class="rpt-sub">
          <span class="c-fail">{{ stats.fail }} 不通过</span>
          <span class="c-skip">{{ stats.skip }} 跳过</span>
          <span style="color:#909399">{{ stats.pending }} 待执行</span>
        </div>
      </div>
      <el-divider />
      <div class="rpt-section">
        <h4>执行人进度</h4>
        <div v-for="ep in executorProgress" :key="ep.name" style="margin-bottom:12px">
          <div class="ep-row">
            <span class="ep-name">{{ ep.name }}</span>
            <el-progress :percentage="ep.total ? Math.round(ep.executed/ep.total*100) : 0"
              color="#52c41a" style="flex:1;margin:0 12px" :show-text="false" />
            <span class="ep-pct">{{ ep.total ? Math.round(ep.executed/ep.total*100) : 0 }}% ({{ ep.executed }}/{{ ep.total }})</span>
          </div>
          <div class="ep-detail">通过 {{ ep.pass }} &nbsp; 不通过 {{ ep.fail }} &nbsp; 跳过 {{ ep.skip }} &nbsp; 待执行 {{ ep.pending }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showReport = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useGuard } from '../composables/useGuard';
import { testPlanApi } from '../api';
import type { TestPlan } from '../types';

const route = useRoute();
const router = useRouter();
const planId = String(route.params.planId);

const plan = ref<TestPlan | null>(null);
const cases = ref<any[]>([]);
const displayTree = ref<any[]>([]);
const selectedCase = ref<any>(null);
const showReport = ref(false);
const refreshing = ref(false);
const filterKeyword = ref('');
const filterResult = ref<string | undefined>();
const reasonModal = ref({ visible: false, action: '', text: '' });
const { locks, run } = useGuard();
const planExecutors = ref<{ userId: string; displayName: string }[]>([]);

const resultOpts = [
  { value: 'PENDING', label: '待执行' }, { value: 'PASS', label: '通过' },
  { value: 'FAIL', label: '不通过' }, { value: 'SKIP', label: '跳过' },
];

const stats = computed(() => {
  const a = cases.value;
  const p = a.filter((c: any) => c.result === 'PASS').length;
  const f = a.filter((c: any) => c.result === 'FAIL').length;
  const s = a.filter((c: any) => c.result === 'SKIP').length;
  return { total: a.length, pass: p, fail: f, skip: s, pending: a.length - p - f - s, executed: p + f + s };
});
const progressPct = computed(() => stats.value.total ? Math.round(stats.value.executed / stats.value.total * 100) : 0);
const curIdx = computed(() => selectedCase.value ? cases.value.findIndex((c: any) => c.id === selectedCase.value.id) : -1);
const executorOptions = computed(() => planExecutors.value.map(e => ({ value: e.userId, label: e.displayName })));

const executorProgress = computed(() => {
  const m = new Map<string, any>();
  cases.value.forEach((c: any) => {
    const k = c.executorId || '_', n = c.executorName || '未分配';
    if (!m.has(k)) m.set(k, { name: n, total: 0, pass: 0, fail: 0, skip: 0, pending: 0, executed: 0 });
    const e = m.get(k); e.total++;
    if (c.result === 'PASS') { e.pass++; e.executed++; }
    else if (c.result === 'FAIL') { e.fail++; e.executed++; }
    else if (c.result === 'SKIP') { e.skip++; e.executed++; }
    else e.pending++;
  });
  return Array.from(m.values());
});

const caseDetail = computed(() => {
  const sc = selectedCase.value;
  if (!sc?.pathSnapshot?.length) return {} as any;
  const path: any[] = sc.pathSnapshot;
  const len = path.length;
  if (len < 4) return {} as any;
  const moduleNodes = path.slice(1, len - 4);
  return {
    modulePath: moduleNodes.map((n: any) => n.text).join(' → ') || '',
    title: path[len - 4]?.text || '',
    precondition: path[len - 3]?.text || '',
    step: path[len - 2]?.text || '',
    expected: path[len - 1]?.text || '',
    props: path[len - 1]?.properties || {},
  };
});

const filteredProps = computed(() => {
  const props = caseDetail.value.props;
  if (!props || typeof props !== 'object') return [];
  return Object.entries(props).filter(([, v]) => {
    if (v == null || v === '' || v === 'NONE') return false;
    if (Array.isArray(v) && v.length === 0) return false;
    return true;
  }).map(([k, v]) => ({ key: k, val: v }));
});


function resLabel(r: string) { return ({ PASS: '通过', FAIL: '不通过', SKIP: '跳过' } as any)[r] || '待执行'; }
function resType(r: string): any { return ({ PASS: 'success', FAIL: 'danger', SKIP: 'warning' } as any)[r] || 'info'; }
function ntLabel(t: string) { return ({ TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' } as any)[t] || t || ''; }
function ntType(t: string): any { return ({ TITLE: 'primary', PRECONDITION: 'info', STEP: 'success', EXPECTED: 'warning' } as any)[t] || ''; }
function propLabel(k: string) { return ({ mark: '标记', priority: '优先级', category: '分类', coverage: '覆盖集', device: '设备集' } as any)[k] || k; }
function markLabel(v: any) { return ({ PENDING: '待完成', TO_CONFIRM: '待确认', TO_MODIFY: '待修改', '待完成': '待完成', '待确认': '待确认', '待修改': '待修改' } as any)[v] || v; }
function markType(v: any): any { return ({ PENDING: 'danger', TO_CONFIRM: 'warning', TO_MODIFY: '', '待完成': 'danger', '待确认': 'warning', '待修改': '' } as any)[v] || 'info'; }

async function loadData() {
  const [pRes, cRes, eRes] = await Promise.all([testPlanApi.get(planId), testPlanApi.getCases(planId), testPlanApi.getExecutors(planId)]);
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

async function doRefresh() {
  refreshing.value = true;
  try { await testPlanApi.refreshCases(planId); await loadData(); ElMessage.success('用例已刷新'); }
  finally { refreshing.value = false; }
}

function rebuildTree() {
  let filtered = cases.value;
  if (filterKeyword.value) {
    const kw = filterKeyword.value.toLowerCase();
    filtered = filtered.filter((c: any) => (c.pathSnapshot || []).some((n: any) => (n.text || '').toLowerCase().includes(kw)));
  }
  if (filterResult.value) filtered = filtered.filter((c: any) => c.result === filterResult.value);

  const byCsId = new Map<string, any[]>();
  for (const c of filtered) {
    if (!byCsId.has(c.caseSetId)) byCsId.set(c.caseSetId, []);
    byCsId.get(c.caseSetId)!.push(c);
  }
  const tree: any[] = [];
  for (const [csId, csList] of byCsId) {
    const csName = csList[0]?.caseSetName || csId;
    const merged = mergePaths(csList, csId);
    tree.push({ _key: `cs-${csId}`, _text: csName, _isGroup: true, children: merged.length ? merged : undefined });
  }
  displayTree.value = tree;
}

function mergePaths(csList: any[], csId: string): any[] {
  interface TN { _key: string; _text: string; _nodeType: string | null; _isLeaf?: boolean; _caseId?: string; _result?: string; _executorId?: string; _executorName?: string; children?: TN[]; _childMap?: Map<string, TN>; }
  const vr: TN = { _key: 'vr', _text: '', _nodeType: null, _childMap: new Map() };
  for (const c of csList) {
    const path: any[] = c.pathSnapshot || [];
    if (!path.length) continue;
    let cur = vr;
    for (let i = 0; i < path.length; i++) {
      const node = path[i];
      const nodeId = node.id || `anon-${i}`;
      const isLast = i === path.length - 1;
      if (!cur._childMap) cur._childMap = new Map();
      let child = cur._childMap.get(nodeId);
      if (!child) {
        child = { _key: `p-${csId}-${nodeId}`, _text: node.text || '', _nodeType: node.nodeType || null };
        if (isLast) { child._isLeaf = true; child._caseId = c.id; child._result = c.result; child._executorId = c.executorId; child._executorName = c.executorName; }
        cur._childMap.set(nodeId, child);
      } else if (isLast) { child._isLeaf = true; child._caseId = c.id; child._result = c.result; child._executorId = c.executorId; child._executorName = c.executorName; }
      cur = child;
    }
  }
  function toArr(node: TN): TN[] {
    if (!node._childMap || node._childMap.size === 0) return [];
    const arr: TN[] = [];
    for (const child of node._childMap.values()) {
      const kids = toArr(child);
      if (kids.length) child.children = kids;
      delete child._childMap;
      arr.push(child);
    }
    return arr;
  }
  const rootChildren = toArr(vr);
  if (rootChildren.length === 1 && !rootChildren[0]._isLeaf) return rootChildren[0].children || [];
  return rootChildren;
}

function rowClass({ row }: any) {
  if (row._isLeaf && selectedCase.value?.id === row._caseId) return 'selected-row';
  if (row._isGroup) return 'group-row';
  return '';
}
function onRowClick(row: any) {
  if (row._caseId) selectedCase.value = cases.value.find((c: any) => c.id === row._caseId) || null;
}
function goPrev() { if (curIdx.value > 0) selectedCase.value = cases.value[curIdx.value - 1]; }
function goNext() { if (curIdx.value < cases.value.length - 1) selectedCase.value = cases.value[curIdx.value + 1]; }
function goToMindMap() { if (selectedCase.value?.caseSetId) router.push(`/mind-map/${selectedCase.value.caseSetId}`); }

async function doExecute(result: string, reasonText?: string) {
  if (!selectedCase.value) return;
  const caseId = selectedCase.value.id;
  await run('execute', async () => {
    await testPlanApi.executeCase(caseId, result, reasonText);
    ElMessage.success('已记录');
    const c = cases.value.find((x: any) => x.id === caseId);
    if (c) { c.result = result; c.reason = reasonText || null; c.executedAt = new Date().toISOString(); }
    rebuildTree();
    selectedCase.value = { ...selectedCase.value!, result, reason: reasonText || null };
    const idx = cases.value.findIndex((x: any) => x.id === caseId);
    if (idx >= 0 && idx < cases.value.length - 1) selectedCase.value = cases.value[idx + 1];
  });
}

function openReasonModal(action: string) {
  if (!selectedCase.value) return;
  reasonModal.value = { visible: true, action, text: '' };
}
async function submitReason() {
  if (!reasonModal.value.text.trim()) { ElMessage.error('请填写原因'); return; }
  reasonModal.value.visible = false;
  await run('submitReason', async () => {
    await doExecute(reasonModal.value.action, reasonModal.value.text.trim());
  });
}
async function removeCase(caseId: string) {
  await run('removeCase', async () => {
    await testPlanApi.removeCase(caseId); ElMessage.success('已移除'); await loadData();
  });
}

onMounted(loadData);
</script>

<style scoped>
.exec-page { display:flex; flex-direction:column; height:100vh; background:#f5f7fa; }
.exec-header { display:flex; align-items:center; justify-content:space-between; background:#fff; border-bottom:1px solid #e4e7ed; padding:10px 20px; flex-shrink:0; gap:12px; }
.header-left { display:flex; align-items:center; gap:12px; }
.header-right { display:flex; align-items:center; gap:8px; }
.plan-name { font-size:15px; font-weight:600; }
.stat-group { display:flex; gap:12px; font-size:13px; color:#606266; }
.c-pass { color:#52c41a; font-weight:600; }
.c-fail { color:#f56c6c; font-weight:600; }
.c-skip { color:#e6a23c; font-weight:600; }
.exec-filter { display:flex; gap:8px; align-items:center; padding:10px 20px; background:#fff; border-bottom:1px solid #e4e7ed; flex-shrink:0; }
.filter-label { font-size:13px; color:#606266; }
.exec-body { display:flex; flex:1; overflow:hidden; }
.exec-left { flex:1; overflow:auto; background:#fff; }
.exec-right { width:400px; flex-shrink:0; display:flex; flex-direction:column; background:#fff; border-left:1px solid #e4e7ed; }
.exec-right.empty { justify-content:center; align-items:center; }
.font-bold { font-weight:600; }
:deep(.selected-row) td { background:#ecf5ff !important; }
:deep(.group-row) td { background:#fafafa !important; font-weight:600; }

/* 执行人文本 */
.executor-text { font-size:13px; color:#606266; }
/* 执行结果文本颜色 */
.result-text { font-size:13px; font-weight:600; }
.result-text.result-pending { color:#909399; }
.result-text.result-pass { color:#52c41a; }
.result-text.result-fail { color:#f56c6c; }
.result-text.result-skip { color:#e6a23c; }

/* 详情面板 */
.detail-header { display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #e4e7ed; font-weight:600; }
.detail-body { flex:1; overflow:auto; padding:16px 20px; }
.detail-section { margin-bottom:18px; }
.section-label { font-size:12px; color:#909399; margin-bottom:6px; }
.section-value { font-size:14px; color:#303133; line-height:1.7; }
.title-value { font-weight:600; font-size:15px; }
.path-value { font-size:13px; color:#606266; }
.attr-list { list-style:none; padding:0; margin:0; }
.attr-item { display:flex; align-items:center; gap:8px; font-size:13px; padding:4px 0; flex-wrap:wrap; }
.attr-item::before { content:'•'; color:#409eff; font-weight:bold; }
.attr-name { color:#606266; white-space:nowrap; min-width:56px; }
.reason-value { color:#f56c6c; background:#fef0f0; padding:8px 12px; border-radius:6px; border:1px solid #fbc4c4; font-size:13px; line-height:1.6; white-space:pre-wrap; }
.link-edit { font-size:13px; color:#409eff; cursor:pointer; }
.link-edit:hover { text-decoration:underline; }
.detail-footer { padding:12px 16px; border-top:1px solid #e4e7ed; }
.footer-nav { display:flex; justify-content:center; gap:12px; margin-bottom:10px; }
.footer-actions { display:flex; justify-content:center; gap:10px; }
.btn-pass { background:#52c41a !important; border-color:#52c41a !important; color:#fff !important; }
.btn-fail { background:#f56c6c !important; border-color:#f56c6c !important; color:#fff !important; }
.btn-skip { background:#e6a23c !important; border-color:#e6a23c !important; color:#fff !important; }
.rpt-section h4 { margin-bottom:12px; font-size:15px; }
.rpt-overview { display:flex; align-items:center; gap:32px; margin-bottom:12px; }
.rpt-nums { display:flex; gap:24px; }
.num { display:flex; flex-direction:column; align-items:center; }
.val { font-size:22px; font-weight:700; color:#303133; }
.lbl { font-size:12px; color:#909399; }
.rpt-sub { display:flex; gap:16px; font-size:13px; }
.ep-row { display:flex; align-items:center; }
.ep-name { width:80px; font-size:13px; flex-shrink:0; }
.ep-pct { font-size:12px; color:#606266; white-space:nowrap; }
.ep-detail { font-size:12px; color:#909399; padding-left:80px; margin-top:2px; }
</style>
