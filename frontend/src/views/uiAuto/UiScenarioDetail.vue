<template>
  <div class="detail-page" v-loading="loading">
    <div class="detail-top">
      <el-button text @click="$router.push('/ui-auto/scenarios')"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
      <span class="detail-title">{{ scenario.name || '场景详情' }}</span>
      <el-button type="primary" size="small" :loading="saving" style="margin-left:auto" @click="doSave">保存</el-button>
    </div>

    <div class="content-card">
      <div class="section-label">场景信息</div>
      <el-form :model="scenario" label-width="90px" class="scenario-form">
        <el-row :gutter="16">
          <el-col :span="10"><el-form-item label="名称"><el-input v-model="scenario.name" /></el-form-item></el-col>
          <el-col :span="8">
            <el-form-item label="失败策略">
              <el-select v-model="scenario.failStrategy" style="width:100%">
                <el-option value="STOP" label="遇错停止" />
                <el-option value="CONTINUE" label="继续执行" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述"><el-input v-model="scenario.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </div>

    <div class="content-card list-card">
      <div class="list-head">
        <span class="section-label">关联用例 ({{ caseRows.length }})</span>
        <el-button type="primary" size="small" :icon="Plus" @click="openCasePicker">添加用例</el-button>
      </div>
      <div v-if="!caseRows.length" class="empty-hint">暂无关联用例</div>
      <div v-for="(row, idx) in caseRows" :key="row.caseId + '_' + idx" class="case-item">
        <span class="sort-badge">{{ idx + 1 }}</span>
        <div class="case-main">
          <div class="case-name">{{ row.caseName || row.caseId }}</div>
          <el-tag size="small" round type="info">步骤 {{ row.stepCount ?? 0 }}</el-tag>
        </div>
        <el-button text type="primary" size="small" @click="openCaseDetailDialog(row.caseId)">详情</el-button>
        <span class="case-enable-label">启用</span>
        <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" size="small" />
        <el-button text type="primary" size="small" :disabled="idx === 0" @click="moveCase(idx, -1)"><el-icon><Top /></el-icon></el-button>
        <el-button text type="primary" size="small" :disabled="idx === caseRows.length - 1" @click="moveCase(idx, 1)"><el-icon><Bottom /></el-icon></el-button>
        <el-button text type="danger" size="small" @click="caseRows.splice(idx, 1)"><el-icon><Delete /></el-icon></el-button>
      </div>
    </div>

    <!-- 用例详情弹窗 -->
    <el-dialog v-model="caseDetailDialogVisible" :title="caseDetailData?.name || '用例详情'" width="760px" destroy-on-close>
      <template v-if="caseDetailData">
        <el-descriptions :column="3" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="浏览器">{{ caseDetailData.browserType }}</el-descriptions-item>
          <el-descriptions-item label="驱动">{{ caseDetailData.driverType }}</el-descriptions-item>
          <el-descriptions-item label="无头模式">{{ caseDetailData.headless ? '是' : '否' }}</el-descriptions-item>
        </el-descriptions>
        <div class="dialog-section-label">步骤 ({{ caseDetailData.steps?.length || 0 }})</div>
        <div v-if="!caseDetailData.steps?.length" class="dialog-empty">暂无步骤</div>
        <div v-else class="dialog-steps">
          <div v-for="(st, si) in caseDetailData.steps" :key="si" class="dialog-step-row">
            <span class="step-num">#{{ si + 1 }}</span>
            <el-tag size="small" :type="stepTagType(st.stepType)" effect="plain">{{ stepLabel(st.stepType) }}</el-tag>
            <span class="step-desc">{{ st.description || stepSummary(st) }}</span>
            <el-tag v-if="st.elementName" size="small" type="info" style="margin-left:4px">{{ st.elementName }}</el-tag>
            <el-tag v-if="!st.enabled" size="small" style="margin-left:4px">已禁用</el-tag>
          </div>
        </div>
        <div style="margin-top:16px;text-align:right">
          <el-button type="primary" size="small" @click="openCaseInNewPage(caseDetailData.id)">编辑步骤</el-button>
        </div>
      </template>
      <div v-else v-loading="caseDetailLoading" style="min-height:100px" />
    </el-dialog>

    <el-dialog v-model="pickerOpen" title="选择 UI 用例" width="720px" destroy-on-close>
      <el-input v-model="caseKw" placeholder="搜索用例名称" clearable style="margin-bottom:12px" />
      <el-table :data="filteredCases" border max-height="400" style="width:100%" @selection-change="onPickChange">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="步骤数" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" round>{{ row.stepCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="browserType" label="浏览器" width="100" />
        <el-table-column prop="createdByName" label="创建人" width="100" />
      </el-table>
      <template #footer>
        <el-button @click="pickerOpen = false">取消</el-button>
        <el-button type="primary" @click="confirmPick">添加选中</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, Plus, Top, Bottom, Delete } from '@element-plus/icons-vue';
import { uiScenarioApi, uiCaseApi } from '../../api';
import { useRouter } from 'vue-router';
import { useAppStore } from '../../stores/app';
import type { UiScenarioCaseItem, UiTestCaseItem } from '../../types';

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const scenarioId = route.params.id as string;
const projectId = () => store.currentProject?.id || '';

const loading = ref(false);
const saving = ref(false);
const scenario = reactive({
  name: '',
  description: '',
  failStrategy: 'STOP',
});
const caseRows = ref<(UiScenarioCaseItem & { caseName?: string; stepCount?: number })[]>([]);

const pickerOpen = ref(false);
const caseKw = ref('');
const allCases = ref<UiTestCaseItem[]>([]);
const pickSelection = ref<UiTestCaseItem[]>([]);

const filteredCases = computed(() => {
  const ids = new Set(caseRows.value.map(c => c.caseId));
  let list = allCases.value.filter(c => !ids.has(c.id));
  if (caseKw.value.trim()) {
    const kw = caseKw.value.toLowerCase();
    list = list.filter(c => c.name.toLowerCase().includes(kw));
  }
  return list;
});

async function loadCasesPool() {
  if (!projectId()) return;
  const res = await uiCaseApi.list({ projectId: projectId(), page: 1, size: 500 });
  allCases.value = res.data.records || [];
}

async function load() {
  loading.value = true;
  try {
    await loadCasesPool();
    const res = await uiScenarioApi.detail(scenarioId);
    const s = res.data;
    scenario.name = s.name || '';
    scenario.description = s.description || '';
    scenario.failStrategy = s.failStrategy || 'STOP';
    caseRows.value = (s.cases || []).map((c: UiScenarioCaseItem) => ({
      caseId: c.caseId,
      caseName: c.caseName,
      stepCount: c.stepCount,
      enabled: c.enabled ?? 1,
    }));
  } finally {
    loading.value = false;
  }
}

function openCasePicker() {
  caseKw.value = '';
  pickSelection.value = [];
  pickerOpen.value = true;
}

function onPickChange(rows: UiTestCaseItem[]) {
  pickSelection.value = rows;
}

function confirmPick() {
  for (const c of pickSelection.value) {
    caseRows.value.push({
      caseId: c.id,
      caseName: c.name,
      stepCount: c.stepCount || 0,
      enabled: 1,
    });
  }
  pickerOpen.value = false;
}

function moveCase(i: number, d: number) {
  const j = i + d;
  if (j < 0 || j >= caseRows.value.length) return;
  const t = caseRows.value[i];
  caseRows.value[i] = caseRows.value[j];
  caseRows.value[j] = t;
}

async function doSave() {
  if (!scenario.name?.trim()) { ElMessage.warning('请输入场景名称'); return; }
  saving.value = true;
  try {
    await uiScenarioApi.update(scenarioId, {
      name: scenario.name,
      description: scenario.description || undefined,
      failStrategy: scenario.failStrategy,
      cases: caseRows.value.map(c => ({ caseId: c.caseId, enabled: c.enabled ?? 1 })),
    });
    ElMessage.success('已保存');
    await load();
  } finally {
    saving.value = false;
  }
}

const caseDetailDialogVisible = ref(false);
const caseDetailData = ref<any>(null);
const caseDetailLoading = ref(false);

async function openCaseDetailDialog(caseId: string) {
  caseDetailData.value = null;
  caseDetailLoading.value = true;
  caseDetailDialogVisible.value = true;
  try {
    const res = await uiCaseApi.detail(caseId);
    caseDetailData.value = res.data;
  } finally { caseDetailLoading.value = false; }
}

function stepTagType(t: string) {
  const m: Record<string, string> = { NAVIGATE: '', CLICK: 'success', WAIT: 'warning', ASSERT: 'danger', SCREENSHOT: 'info', SCRIPT: 'warning' };
  return m[t] || '';
}
function stepLabel(t: string) {
  const m: Record<string, string> = { NAVIGATE: '导航', CLICK: '点击', DOUBLE_CLICK: '双击', RIGHT_CLICK: '右键', INPUT: '输入', CLEAR: '清空', SELECT: '选择', HOVER: '悬停', KEY_PRESS: '按键', WAIT: '等待', SCREENSHOT: '截屏', ASSERT: '断言', SCRIPT: '脚本', SCROLL: '滚动' };
  return m[t] || t;
}
function stepSummary(st: any) {
  if (st.stepType === 'NAVIGATE') return st.targetUrl || '';
  if (st.stepType === 'WAIT') return (st.waitType || 'FIXED') + ' ' + (st.waitTimeoutMs || 0) + 'ms';
  if (st.stepType === 'ASSERT') return st.assertType || '';
  if (st.stepType === 'INPUT') return st.inputValue || '';
  return '';
}

function openCaseInNewPage(caseId: string) {
  const href = router.resolve('/ui-auto/case/' + caseId).href;
  window.open(href, '_blank');
}

watch(() => store.currentProject, () => { loadCasesPool(); load(); });
onMounted(load);
</script>

<style scoped>
.detail-page { padding: 16px 24px; max-width: 1200px; margin: 0 auto; height: 100%; overflow: auto; }
.detail-top { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.detail-title { font-size: 18px; font-weight: 600; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.section-label { font-size: 14px; font-weight: 600; color: #1f2329; }
.scenario-form { margin-top: 8px; }
.list-card { margin-top: 16px; }
.list-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.empty-hint { color: #909399; text-align: center; padding: 20px; }
.case-item {
  display: flex; align-items: center; gap: 12px; padding: 12px 14px; border: 1px solid #ebeef5; border-radius: 8px; margin-bottom: 10px;
  background: #fafafa;
}
.sort-badge { font-weight: 700; color: #909399; min-width: 28px; }
.case-main { flex: 1; display: flex; align-items: center; gap: 10px; min-width: 0; }
.case-name { font-weight: 500; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.case-enable-label { font-size: 12px; color: #909399; }
.dialog-section-label { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.dialog-empty { color: #c0c4cc; text-align: center; padding: 12px; }
.dialog-steps { display: flex; flex-direction: column; gap: 4px; max-height: 400px; overflow: auto; }
.dialog-step-row { display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: #fafafa; border-radius: 6px; font-size: 13px; }
.step-num { font-weight: 600; color: #909399; min-width: 28px; }
.step-desc { flex: 1; color: #606266; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
