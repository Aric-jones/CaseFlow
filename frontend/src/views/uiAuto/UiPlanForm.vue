<template>
  <div class="detail-page" v-loading="loading">
    <div class="detail-top">
      <el-button text @click="$router.push('/ui-auto/plans')"><el-icon><ArrowLeft /></el-icon> 返回列表</el-button>
      <span class="detail-title">{{ isCreate ? '新建测试计划' : '编辑测试计划' }}</span>
      <el-button type="primary" size="small" :loading="saving" style="margin-left:auto" @click="doSave">保存</el-button>
    </div>

    <div class="content-card">
      <div class="section-label">计划信息</div>
      <el-form :model="form" label-width="100px" class="plan-form">
        <el-form-item label="名称" required><el-input v-model="form.name" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="浏览器">
              <el-select v-model="form.browserType" style="width:100%">
                <el-option value="CHROMIUM" label="Chromium" />
                <el-option value="FIREFOX" label="Firefox" />
                <el-option value="WEBKIT" label="WebKit" />
                <el-option value="EDGE" label="Edge" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="驱动">
              <el-select v-model="form.driverType" style="width:100%">
                <el-option value="PLAYWRIGHT" label="Playwright" />
                <el-option value="SELENIUM" label="Selenium" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="无头"><el-switch v-model="form.headless" :active-value="1" :inactive-value="0" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </div>

    <div class="content-card list-card">
      <div class="list-head">
        <span class="section-label">关联场景 ({{ selectedScenarios.length }})</span>
        <el-button type="primary" size="small" :icon="Plus" @click="scenarioDialog = true">添加场景</el-button>
      </div>
      <div v-if="!selectedScenarios.length" class="empty-hint">暂未关联场景</div>
      <el-table v-else :data="selectedScenarios" border style="width:100%" row-key="id" @expand-change="onExpandScenario">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-wrap" v-loading="row._loading">
              <div v-if="!row._cases?.length && !row._loading" class="expand-empty">暂无关联用例</div>
              <div v-else class="expand-cases">
                <div v-for="(tc, ci) in (row._cases || [])" :key="ci" class="expand-case-row">
                  <span class="expand-case-num">#{{ ci + 1 }}</span>
                  <span class="expand-case-name">{{ tc.caseName || tc.caseId }}</span>
                  <el-tag size="small" type="info">{{ tc.stepCount || 0 }} 步</el-tag>
                  <el-button text type="primary" size="small" @click="openCaseDetailDialog(tc.caseId)">详情</el-button>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column prop="name" label="场景名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="用例数" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" round>{{ row.caseCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column label="启用" width="100" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :active-value="1" :inactive-value="0" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ $index }">
            <el-button text type="primary" size="small" :disabled="$index === 0" @click="moveScenario($index, -1)"><el-icon><Top /></el-icon></el-button>
            <el-button text type="primary" size="small" :disabled="$index === selectedScenarios.length - 1" @click="moveScenario($index, 1)"><el-icon><Bottom /></el-icon></el-button>
            <el-button text type="danger" size="small" @click="removeScenario($index)"><el-icon><Delete /></el-icon></el-button>
          </template>
        </el-table-column>
      </el-table>
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
        <div v-if="!caseDetailData.steps?.length" class="expand-empty">暂无步骤</div>
        <div v-else class="dialog-steps">
          <div v-for="(st, si) in caseDetailData.steps" :key="si" class="dialog-step-row">
            <span class="expand-case-num">#{{ si + 1 }}</span>
            <el-tag size="small" :type="stepTagType(st.stepType)" effect="plain">{{ stepLabel(st.stepType) }}</el-tag>
            <span class="dialog-step-desc">{{ st.description || stepSummary(st) }}</span>
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

    <el-dialog v-model="scenarioDialog" title="选择场景" width="700px" destroy-on-close>
      <el-input v-model="scenarioSearchKw" placeholder="搜索场景名称" clearable style="margin-bottom:12px" />
      <el-table :data="filteredScenarios" border max-height="400" style="width:100%" @selection-change="onScenarioSelectionChange">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="name" label="场景名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="用例数" width="90" align="center">
          <template #default="{ row }"><el-tag size="small" round>{{ row.caseCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createdByName" label="创建人" width="100" />
      </el-table>
      <template #footer>
        <el-button @click="scenarioDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddScenarios">添加选中</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, Plus, Top, Bottom, Delete } from '@element-plus/icons-vue';
import { uiPlanApi, uiScenarioApi, uiCaseApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { UiScenarioItem } from '../../types';

const route = useRoute();
const router = useRouter();
const store = useAppStore();

const isCreate = computed(() => route.path.endsWith('/create'));
const planId = computed(() => (route.params.id as string) || '');

const loading = ref(false);
const saving = ref(false);
const form = reactive<any>({
  name: '',
  description: '',
  browserType: 'CHROMIUM',
  driverType: 'PLAYWRIGHT',
  headless: 1,
  projectId: store.currentProject?.id || '',
  directoryId: (route.query.directoryId as string) || undefined,
});

const selectedScenarios = ref<any[]>([]);
const scenarioDialog = ref(false);
const scenarioSearchKw = ref('');
const allScenarios = ref<UiScenarioItem[]>([]);
const scenarioSelection = ref<UiScenarioItem[]>([]);

const filteredScenarios = computed(() => {
  const existing = new Set(selectedScenarios.value.map(s => s.id));
  let list = allScenarios.value.filter(s => !existing.has(s.id));
  if (scenarioSearchKw.value.trim()) {
    const kw = scenarioSearchKw.value.toLowerCase();
    list = list.filter(s => s.name.toLowerCase().includes(kw));
  }
  return list;
});

async function loadAllScenarios() {
  if (!store.currentProject) return;
  const res = await uiScenarioApi.list({ projectId: store.currentProject.id, page: 1, size: 500 });
  allScenarios.value = res.data.records || [];
}

async function loadPlan() {
  if (isCreate.value) return;
  loading.value = true;
  try {
    const res = await uiPlanApi.detail(planId.value);
    const p = res.data.plan;
    form.name = p.name;
    form.description = p.description || '';
    form.browserType = p.browserType || 'CHROMIUM';
    form.driverType = p.driverType || 'PLAYWRIGHT';
    form.headless = p.headless ?? 1;
    form.projectId = p.projectId;
    form.directoryId = p.directoryId;

    const scenarios: any[] = res.data.scenarios || [];
    selectedScenarios.value = scenarios.map((ps: any) => {
      const full = allScenarios.value.find(s => s.id === ps.scenarioId);
      return {
        id: ps.scenarioId,
        name: ps.scenarioName || full?.name || '',
        caseCount: ps.caseCount ?? full?.caseCount ?? 0,
        enabled: ps.enabled ?? 1,
      };
    });
  } finally {
    loading.value = false;
  }
}

function onScenarioSelectionChange(rows: UiScenarioItem[]) {
  scenarioSelection.value = rows;
}

function confirmAddScenarios() {
  for (const s of scenarioSelection.value) {
    selectedScenarios.value.push({
      id: s.id,
      name: s.name,
      caseCount: s.caseCount || 0,
      enabled: 1,
    });
  }
  scenarioDialog.value = false;
  scenarioSelection.value = [];
}

function removeScenario(idx: number) {
  selectedScenarios.value.splice(idx, 1);
}

async function onExpandScenario(row: any, expandedRows: any[]) {
  if (!expandedRows.find((r: any) => r.id === row.id)) return;
  if (row._cases) return;
  row._loading = true;
  try {
    const res = await uiScenarioApi.detail(row.id);
    row._cases = res.data.cases || [];
  } catch { row._cases = []; }
  finally { row._loading = false; }
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

function moveScenario(idx: number, dir: number) {
  const target = idx + dir;
  if (target < 0 || target >= selectedScenarios.value.length) return;
  const arr = selectedScenarios.value;
  const tmp = arr[idx];
  arr[idx] = arr[target];
  arr[target] = tmp;
  selectedScenarios.value = [...arr];
}

async function doSave() {
  if (!form.name?.trim()) { ElMessage.warning('请输入计划名称'); return; }
  if (!store.currentProject) return;
  form.projectId = store.currentProject.id;
  saving.value = true;
  const payload = {
    ...form,
    scenarios: selectedScenarios.value.map(s => ({ scenarioId: s.id, enabled: s.enabled ?? 1 })),
  };
  try {
    if (isCreate.value) {
      const res = await uiPlanApi.create(payload);
      ElMessage.success('创建成功');
      router.replace('/ui-auto/plan/' + res.data.id + '/edit');
    } else {
      await uiPlanApi.update(planId.value, payload);
      ElMessage.success('保存成功');
    }
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  await loadAllScenarios();
  await loadPlan();
});
</script>

<style scoped>
.detail-page { padding: 16px 24px; max-width: 1200px; margin: 0 auto; height: 100%; overflow: auto; }
.detail-top { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.detail-title { font-size: 18px; font-weight: 600; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.section-label { font-size: 14px; font-weight: 600; color: #1f2329; }
.plan-form { margin-top: 8px; }
.list-card { margin-top: 16px; }
.list-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.empty-hint { color: #c0c4cc; text-align: center; padding: 28px 0; }
.expand-wrap { padding: 8px 16px; }
.expand-empty { color: #c0c4cc; text-align: center; padding: 12px; }
.expand-cases { display: flex; flex-direction: column; gap: 4px; }
.expand-case-row { display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: #fafafa; border-radius: 6px; font-size: 13px; }
.expand-case-num { font-weight: 600; color: #909399; min-width: 28px; }
.expand-case-name { flex: 1; color: #606266; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dialog-section-label { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.dialog-steps { display: flex; flex-direction: column; gap: 4px; max-height: 400px; overflow: auto; }
.dialog-step-row { display: flex; align-items: center; gap: 8px; padding: 6px 10px; background: #fafafa; border-radius: 6px; font-size: 13px; }
.dialog-step-desc { flex: 1; color: #606266; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
