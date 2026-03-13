<template>
  <div class="exec-page">
    <!-- ========== 顶栏 ========== -->
    <div class="exec-header">
      <div class="header-left">
        <a-button type="text" @click="$router.push('/test-plans')"><ArrowLeftOutlined /></a-button>
        <strong class="plan-name">{{ plan?.name }}</strong>
        <span class="stat-group">
          已执行 <b>{{ stats.executed }}/{{ stats.total }}</b>
          <span class="stat-fail">不通过 {{ stats.fail }}</span>
          <span class="stat-pass">通过 {{ stats.pass }}</span>
          <span class="stat-skip">跳过 {{ stats.skip }}</span>
        </span>
      </div>
      <div class="header-right">
        <span class="exec-progress">执行进度</span>
        <a-progress :percent="stats.total ? Math.round(stats.executed / stats.total * 100) : 0"
          :stroke-color="'#52c41a'" :size="'small'" style="width: 120px" />
        <span style="color:#1677ff;margin-left:4px">#通过 {{ stats.pass }}</span>
        <a-button type="link" @click="showReport = true">查看测试报告</a-button>
        <a-button type="link" @click="loadData"><ReloadOutlined /> 刷新用例</a-button>
      </div>
    </div>

    <!-- ========== 搜索栏 ========== -->
    <div class="exec-filter">
      <a-input v-model:value="filterKeyword" placeholder="用例名称" allow-clear style="width: 200px" />
      <a-select v-model:value="filterExecutor" placeholder="全部执行人" allow-clear style="width: 150px"
        :options="executorOptions" />
      <a-select v-model:value="filterResult" placeholder="全部结果" allow-clear style="width: 150px"
        :options="[{value:'PENDING',label:'待执行'},{value:'PASS',label:'通过'},{value:'FAIL',label:'不通过'},{value:'SKIP',label:'跳过'}]" />
    </div>

    <!-- ========== 主体 ========== -->
    <div class="exec-body">
      <!-- 左侧用例列表 -->
      <div class="exec-left">
        <a-table :columns="caseColumns" :data-source="filteredCases" row-key="id" size="small"
          :pagination="false" :scroll="{ y: 'calc(100vh - 200px)' }" :row-class-name="rowClassName"
          :custom-row="(r: any) => ({ onClick: () => selectCase(r) })" :expandable="{ childrenColumnName: 'children', defaultExpandAllRows: true }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">
              <span v-if="record._isChild" style="color: #888">
                <a-tag v-if="record.nodeType" :color="nodeTypeColor(record.nodeType)" style="font-size: 11px">{{ nodeTypeLabel(record.nodeType) }}</a-tag>
                {{ record.text }}
              </span>
              <span v-else>{{ record.title || record.text }}</span>
            </template>
            <template v-if="column.key === 'executorName'">{{ record.executorName }}</template>
            <template v-if="column.key === 'result'">
              <a-tag v-if="record.result" :color="resultColor(record.result)">{{ resultLabel(record.result) }}</a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-popconfirm v-if="!record._isChild" title="确认移除该用例？" @confirm.stop="removeCase(record.id)">
                <a-button type="link" size="small" danger @click.stop>移除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
      </div>

      <!-- 右侧详情面板 -->
      <div class="exec-right" v-if="selectedCase">
        <div class="detail-header">
          <span>用例详情</span>
          <a-tag :color="resultColor(selectedCase.result)">{{ resultLabel(selectedCase.result) }}</a-tag>
        </div>
        <div class="detail-body">
          <div class="detail-field">
            <label>用例标题</label>
            <div>{{ selectedCase.title }}</div>
          </div>
          <template v-for="child in selectedCase.children || []" :key="child.id">
            <div class="detail-field">
              <label>{{ nodeTypeLabel(child.nodeType) }}</label>
              <div>{{ child.text || '无' }}</div>
            </div>
            <template v-for="sub in child.children || []" :key="sub.id">
              <div class="detail-field sub">
                <label>{{ nodeTypeLabel(sub.nodeType) || '子节点' }}</label>
                <div>{{ sub.text }}</div>
              </div>
            </template>
          </template>
          <div class="detail-field" v-if="selectedCase.properties && Object.keys(selectedCase.properties).length">
            <label>属性</label>
            <div class="props-list">
              <span v-for="(v, k) in selectedCase.properties" :key="k" class="prop-item">
                <b>{{ k }}</b>: {{ Array.isArray(v) ? v.join(', ') : v }}
              </span>
            </div>
          </div>
          <div class="detail-field" v-if="selectedCase.reason">
            <label>原因</label>
            <div>{{ selectedCase.reason }}</div>
          </div>
          <a-divider style="margin: 12px 0" />
          <a-textarea v-model:value="reason" placeholder="不通过/跳过请填写原因（可选）" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </div>
        <div class="detail-footer">
          <div class="nav-btns">
            <a-button size="small" :disabled="currentIndex <= 0" @click="goPrev"><LeftOutlined /> 上一条</a-button>
            <a-button size="small" :disabled="currentIndex >= topCases.length - 1" @click="goNext">下一条 <RightOutlined /></a-button>
          </div>
          <div class="action-btns">
            <a-button class="btn-pass" @click="execute('PASS')">通过</a-button>
            <a-button class="btn-fail" @click="execute('FAIL')">不通过</a-button>
            <a-button class="btn-skip" @click="execute('SKIP')">跳过</a-button>
          </div>
        </div>
      </div>
      <div class="exec-right empty" v-else>
        <a-empty description="请选择用例" />
      </div>
    </div>

    <!-- ========== 测试报告弹窗 ========== -->
    <a-modal v-model:open="showReport" title="测试报告" :footer="null" width="680px">
      <div class="report-section">
        <h4>整体执行进度</h4>
        <div class="report-overview">
          <div class="report-ring">
            <a-progress type="circle" :percent="stats.total ? Math.round(stats.executed / stats.total * 100) : 0"
              :size="100" :stroke-color="'#52c41a'" />
          </div>
          <div class="report-nums">
            <div class="num-row"><span class="num-val">{{ stats.total }}</span><span class="num-label">总用例</span></div>
            <div class="num-row"><span class="num-val">{{ stats.executed }}</span><span class="num-label">已执行</span></div>
            <div class="num-row"><span class="num-val">{{ stats.pass }}</span><span class="num-label">通过</span></div>
          </div>
        </div>
        <div class="report-sub-nums">
          <span class="stat-fail">{{ stats.fail }} 不通过</span>
          <span class="stat-skip">{{ stats.skip }} 跳过</span>
          <span style="color:#999">{{ stats.pending }} 待执行</span>
        </div>
      </div>
      <a-divider />
      <div class="report-section">
        <h4>执行人进度</h4>
        <div v-for="exec in executorProgress" :key="exec.name" class="executor-row">
          <span class="executor-name">{{ exec.name }}</span>
          <a-progress :percent="exec.total ? Math.round(exec.executed / exec.total * 100) : 0"
            :stroke-color="'#52c41a'" style="flex:1;margin:0 12px" />
          <span class="executor-detail">{{ Math.round(exec.executed / (exec.total||1) * 100) }}% ({{ exec.executed }}/{{ exec.total }})</span>
        </div>
        <div v-for="exec in executorProgress" :key="'d'+exec.name" class="executor-detail-row">
          通过 {{ exec.pass }} &nbsp; 不通过 {{ exec.fail }} &nbsp; 跳过 {{ exec.skip }} &nbsp; 待执行 {{ exec.pending }}
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import { ArrowLeftOutlined, ReloadOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons-vue';
import { testPlanApi } from '../api';
import type { TestPlan } from '../types';

const route = useRoute();
const planId = String(route.params.planId);
const plan = ref<TestPlan | null>(null);
const cases = ref<any[]>([]);
const selectedCase = ref<any>(null);
const reason = ref('');
const showReport = ref(false);

const filterKeyword = ref('');
const filterExecutor = ref<string | undefined>();
const filterResult = ref<string | undefined>();

const caseColumns = [
  { title: '测试用例', key: 'title', ellipsis: true },
  { title: '执行人', key: 'executorName', width: 100 },
  { title: '执行结果', key: 'result', width: 100 },
  { title: '操作', key: 'action', width: 80 },
];

/** 顶层用例（非子行） */
const topCases = computed(() => cases.value);

/** 为 table 的树形展示添加子行标记 */
function buildTableData(list: any[]): any[] {
  return list.map(c => {
    const childRows = (c.children || []).map((ch: any) => ({
      ...ch, _isChild: true, id: ch.id,
      children: (ch.children || []).map((sub: any) => ({ ...sub, _isChild: true, id: sub.id })),
    }));
    return { ...c, children: childRows.length ? childRows : undefined };
  });
}

/** 过滤后的用例列表 */
const filteredCases = computed(() => {
  let list = topCases.value;
  if (filterKeyword.value) {
    const kw = filterKeyword.value.toLowerCase();
    list = list.filter(c => (c.title || '').toLowerCase().includes(kw));
  }
  if (filterExecutor.value) list = list.filter(c => c.executorId === filterExecutor.value);
  if (filterResult.value) list = list.filter(c => c.result === filterResult.value);
  return buildTableData(list);
});

/** 统计 */
const stats = computed(() => {
  const all = topCases.value;
  const pass = all.filter(c => c.result === 'PASS').length;
  const fail = all.filter(c => c.result === 'FAIL').length;
  const skip = all.filter(c => c.result === 'SKIP').length;
  return { total: all.length, pass, fail, skip, pending: all.length - pass - fail - skip, executed: pass + fail + skip };
});

/** 执行人下拉选项 */
const executorOptions = computed(() => {
  const map = new Map<string, string>();
  topCases.value.forEach(c => { if (c.executorId && c.executorName) map.set(c.executorId, c.executorName); });
  return Array.from(map, ([value, label]) => ({ value, label }));
});

/** 执行人进度（报告弹窗用） */
const executorProgress = computed(() => {
  const map = new Map<string, { name: string; total: number; pass: number; fail: number; skip: number; pending: number; executed: number }>();
  topCases.value.forEach(c => {
    const name = c.executorName || '未分配';
    const key = c.executorId || '_none';
    if (!map.has(key)) map.set(key, { name, total: 0, pass: 0, fail: 0, skip: 0, pending: 0, executed: 0 });
    const m = map.get(key)!;
    m.total++;
    if (c.result === 'PASS') { m.pass++; m.executed++; }
    else if (c.result === 'FAIL') { m.fail++; m.executed++; }
    else if (c.result === 'SKIP') { m.skip++; m.executed++; }
    else m.pending++;
  });
  return Array.from(map.values());
});

/** 当前选中用例在顶层列表中的索引 */
const currentIndex = computed(() => selectedCase.value ? topCases.value.findIndex(c => c.id === selectedCase.value.id) : -1);

function resultLabel(r: string) { return r === 'PASS' ? '通过' : r === 'FAIL' ? '不通过' : r === 'SKIP' ? '跳过' : '待执行'; }
function resultColor(r: string) { return r === 'PASS' ? 'success' : r === 'FAIL' ? 'error' : r === 'SKIP' ? 'warning' : 'default'; }
function nodeTypeLabel(t: string) {
  const m: Record<string, string> = { TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' };
  return m[t] || t || '';
}
function nodeTypeColor(t: string) {
  const m: Record<string, string> = { PRECONDITION: 'blue', STEP: 'green', EXPECTED: 'orange' };
  return m[t] || 'default';
}

function selectCase(record: any) {
  if (record._isChild) return;
  selectedCase.value = topCases.value.find(c => c.id === record.id) || null;
  reason.value = '';
}

function rowClassName(record: any) {
  if (record._isChild) return 'child-row';
  return selectedCase.value?.id === record.id ? 'selected-row' : '';
}

function goPrev() {
  const idx = currentIndex.value;
  if (idx > 0) { selectedCase.value = topCases.value[idx - 1]; reason.value = ''; }
}
function goNext() {
  const idx = currentIndex.value;
  if (idx < topCases.value.length - 1) { selectedCase.value = topCases.value[idx + 1]; reason.value = ''; }
}

async function loadData() {
  const [pRes, cRes] = await Promise.all([testPlanApi.get(planId), testPlanApi.getCases(planId)]);
  plan.value = pRes.data; cases.value = cRes.data;
  if (selectedCase.value) {
    selectedCase.value = cases.value.find(c => c.id === selectedCase.value!.id) || cases.value[0] || null;
  } else if (cases.value.length) {
    selectedCase.value = cases.value[0];
  }
}
onMounted(loadData);

async function execute(result: string) {
  if (!selectedCase.value) return;
  await testPlanApi.executeCase(selectedCase.value.id, result, reason.value || undefined);
  message.success('已记录'); reason.value = '';
  await loadData();
}

async function removeCase(id: string) {
  await testPlanApi.removeCase(id);
  message.success('已移除');
  await loadData();
}
</script>

<style scoped>
.exec-page { display: flex; flex-direction: column; height: 100vh; background: #f5f5f5; }

.exec-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #e8e8e8; padding: 10px 20px; flex-shrink: 0;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.header-right { display: flex; align-items: center; gap: 8px; }
.plan-name { font-size: 15px; }
.stat-group { font-size: 13px; color: #666; display: flex; gap: 12px; margin-left: 8px; }
.stat-pass { color: #52c41a; font-weight: 600; }
.stat-fail { color: #ff4d4f; font-weight: 600; }
.stat-skip { color: #faad14; font-weight: 600; }
.exec-progress { font-size: 13px; color: #999; }

.exec-filter {
  display: flex; gap: 12px; padding: 10px 20px; background: #fff;
  border-bottom: 1px solid #f0f0f0; flex-shrink: 0;
}

.exec-body { display: flex; flex: 1; overflow: hidden; }
.exec-left { flex: 1; overflow: auto; background: #fff; border-right: 1px solid #f0f0f0; }
.exec-right { width: 380px; flex-shrink: 0; display: flex; flex-direction: column; background: #fff; border-left: 1px solid #f0f0f0; }
.exec-right.empty { justify-content: center; align-items: center; }

.detail-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 16px; border-bottom: 1px solid #f0f0f0; font-weight: 600; font-size: 14px;
}
.detail-body { flex: 1; overflow: auto; padding: 12px 16px; }
.detail-field { margin-bottom: 12px; }
.detail-field.sub { padding-left: 16px; }
.detail-field label { display: block; font-size: 12px; color: #999; margin-bottom: 2px; }
.detail-field div { font-size: 13px; color: #333; }
.props-list { display: flex; flex-wrap: wrap; gap: 6px; }
.prop-item { background: #f5f5f5; padding: 2px 8px; border-radius: 4px; font-size: 12px; }

.detail-footer {
  padding: 12px 16px; border-top: 1px solid #f0f0f0;
  display: flex; justify-content: space-between; align-items: center;
}
.nav-btns { display: flex; gap: 8px; }
.action-btns { display: flex; gap: 8px; }
.btn-pass { background: #52c41a; border-color: #52c41a; color: #fff; }
.btn-pass:hover { background: #73d13d; border-color: #73d13d; color: #fff; }
.btn-fail { background: #ff4d4f; border-color: #ff4d4f; color: #fff; }
.btn-fail:hover { background: #ff7875; border-color: #ff7875; color: #fff; }
.btn-skip { background: #1677ff; border-color: #1677ff; color: #fff; }
.btn-skip:hover { background: #4096ff; border-color: #4096ff; color: #fff; }

:deep(.selected-row) td { background: #e6f4ff !important; }
:deep(.child-row) td { background: #fafafa !important; color: #888; }

.report-section h4 { margin-bottom: 12px; font-size: 15px; }
.report-overview { display: flex; align-items: center; gap: 32px; }
.report-ring { flex-shrink: 0; }
.report-nums { display: flex; gap: 24px; }
.num-row { display: flex; flex-direction: column; align-items: center; }
.num-val { font-size: 22px; font-weight: 700; color: #333; }
.num-label { font-size: 12px; color: #999; }
.report-sub-nums { display: flex; gap: 16px; margin-top: 12px; font-size: 13px; }

.executor-row { display: flex; align-items: center; margin-bottom: 4px; }
.executor-name { width: 80px; font-size: 13px; flex-shrink: 0; }
.executor-detail { font-size: 12px; color: #666; white-space: nowrap; }
.executor-detail-row { font-size: 12px; color: #999; margin-bottom: 8px; padding-left: 80px; }
</style>
