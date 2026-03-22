<template>
  <div class="plan-form" v-loading="loading">
    <div class="detail-top">
      <el-button text @click="$router.push('/api-auto/plans')"><el-icon><ArrowLeft /></el-icon> 返回列表</el-button>
      <span class="detail-title">{{ isCreate ? '新建测试计划' : '编辑测试计划' }}</span>
      <el-button type="primary" size="small" @click="doSave" :loading="saving" style="margin-left:auto">保存计划</el-button>
    </div>

    <!-- 基本信息 -->
    <el-card shadow="never" class="section-card">
      <template #header><span class="card-title">计划信息</span></template>
      <el-form :model="form" label-width="95px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="计划名称" required><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="执行环境" required>
              <el-select v-model="form.environmentId" style="width:100%" placeholder="选择执行环境" filterable>
                <el-option v-for="e in envList" :key="e.id" :label="e.name + ' (' + e.baseUrl + ')'" :value="e.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="执行模式">
              <el-radio-group v-model="form.parallel">
                <el-radio :value="0">串行执行</el-radio>
                <el-radio :value="1">并行执行</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </el-card>

    <!-- 关联场景 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">关联场景 ({{ selectedScenarios.length }})</span>
          <el-button type="primary" size="small" @click="scenarioDialog = true"><el-icon><Plus /></el-icon> 添加场景</el-button>
        </div>
      </template>

      <div v-if="!selectedScenarios.length" class="empty-hint">暂未关联场景，点击上方按钮添加</div>

      <el-table v-else :data="selectedScenarios" border style="width:100%" row-key="id">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="scenario-expand">
              <div v-if="row.description" class="expand-desc">{{ row.description }}</div>
              <div v-if="!row._steps?.length" class="expand-empty">暂无步骤信息</div>
              <div v-else class="expand-steps">
                <div v-for="(st, si) in row._steps" :key="si" class="expand-step-row">
                  <span class="expand-step-num">#{{ si + 1 }}</span>
                  <el-tag v-if="st.stepType === 'SCRIPT'" type="warning" size="small" effect="plain">脚本</el-tag>
                  <el-tag v-else-if="st.stepType === 'WAIT'" type="info" size="small" effect="plain">等待 {{ st.delayMs || 0 }}ms</el-tag>
                  <template v-else>
                    <el-tag v-if="st._apiMethod" :type="methodTagType(st._apiMethod)" size="small" effect="dark" style="margin-right:2px">{{ st._apiMethod }}</el-tag>
                    <span class="expand-step-name">{{ st._apiName || '' }} / {{ st._caseName || st.caseId }}</span>
                    <code v-if="st._apiPath" class="expand-step-path">{{ st._apiPath }}</code>
                    <el-button v-if="st.caseId" text type="primary" size="small" style="margin-left:8px" @click.stop="showCaseDetail(st)">详情</el-button>
                  </template>
                  <el-tag v-if="!st.enabled" type="info" size="small" style="margin-left:4px">已禁用</el-tag>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">{{ $index + 1 }}</template>
        </el-table-column>
        <el-table-column label="场景名称" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <a class="link-text" @click.stop="goScenario(row.id)">{{ row.name }}</a>
          </template>
        </el-table-column>
        <el-table-column label="步骤数" width="80" align="center">
          <template #default="{ row }"><el-tag size="small" round>{{ row.stepCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column label="失败策略" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.failStrategy === 'STOP' ? 'danger' : 'success'" size="small">{{ row.failStrategy === 'STOP' ? '遇错停止' : '继续执行' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标签" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="t in (row.tags || [])" :key="t" size="small" type="info" round style="margin:0 4px 2px 0">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ $index }">
            <el-button text type="primary" size="small" @click="moveScenario($index, -1)" :disabled="$index === 0">
              <el-icon><Top /></el-icon>
            </el-button>
            <el-button text type="primary" size="small" @click="moveScenario($index, 1)" :disabled="$index === selectedScenarios.length - 1">
              <el-icon><Bottom /></el-icon>
            </el-button>
            <el-button text type="danger" size="small" @click="removeScenario($index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 选择场景弹窗 -->
    <el-dialog v-model="scenarioDialog" title="选择测试场景" width="750px" destroy-on-close>
      <el-input v-model="scenarioSearchKw" placeholder="搜索场景名称" clearable style="margin-bottom:12px" />
      <el-table :data="filteredScenarios" border max-height="400" style="width:100%"
        @selection-change="onScenarioSelectionChange" ref="scenarioTableRef">
        <el-table-column type="selection" width="45" />
        <el-table-column prop="name" label="场景名称" min-width="200" show-overflow-tooltip />
        <el-table-column label="步骤数" width="80" align="center">
          <template #default="{ row }"><el-tag size="small" round>{{ row.stepCount || 0 }}</el-tag></template>
        </el-table-column>
        <el-table-column label="标签" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="t in (row.tags || [])" :key="t" size="small" type="info" round style="margin:0 4px 2px 0">{{ t }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdByName" label="创建人" width="100" />
      </el-table>
      <template #footer>
        <el-button @click="scenarioDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddScenarios">确认添加 ({{ scenarioSelection.length }})</el-button>
      </template>
    </el-dialog>

    <!-- 用例详情弹窗 -->
    <el-dialog v-model="caseDetailDialog" :title="'用例详情'" width="700px" destroy-on-close>
      <template v-if="caseDetailData">
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="用例名称">{{ caseDetailData.name }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ caseDetailData.priority }}</el-descriptions-item>
          <el-descriptions-item label="Body类型">{{ caseDetailData.bodyType || 'NONE' }}</el-descriptions-item>
          <el-descriptions-item label="启用">{{ caseDetailData.enabled ? '是' : '否' }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="caseDetailData.bodyContent" style="margin-bottom:12px">
          <div class="detail-label">Body</div>
          <pre class="detail-code">{{ formatJson(caseDetailData.bodyContent) }}</pre>
        </div>
        <div v-if="caseDetailData.headers?.length" style="margin-bottom:12px">
          <div class="detail-label">Headers</div>
          <div v-for="h in caseDetailData.headers" :key="h.key" class="detail-kv">{{ h.key }}: {{ h.value }}</div>
        </div>
        <div v-if="caseDetailData.queryParams?.length" style="margin-bottom:12px">
          <div class="detail-label">Params</div>
          <div v-for="p in caseDetailData.queryParams" :key="p.key" class="detail-kv">{{ p.key }}={{ p.value }}</div>
        </div>
        <div v-if="caseDetailData.assertions?.length" style="margin-bottom:12px">
          <div class="detail-label">断言 ({{ caseDetailData.assertions.length }})</div>
          <div v-for="(a, i) in caseDetailData.assertions" :key="i" class="detail-assertion">
            {{ a.type }}{{ a.expression ? ' ' + a.expression : '' }} {{ a.operator }} {{ a.expectedValue || '' }}
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, Plus, Delete, Top, Bottom } from '@element-plus/icons-vue';
import { apiPlanApi, apiEnvApi, apiScenarioApi, apiDefApi, apiCaseApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { ApiEnv, ApiScenarioItem, ApiDef } from '../../types';

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const planId = route.params.id as string;
const isCreate = !planId || planId === 'create';

const loading = ref(false);
const saving = ref(false);
const form = reactive<any>({
  name: '', description: '', environmentId: '', parallel: 0,
  projectId: store.currentProject?.id || '',
  directoryId: (route.query.directoryId as string) || undefined,
});
const envList = ref<ApiEnv[]>([]);
const selectedScenarios = ref<any[]>([]);

const scenarioDialog = ref(false);
const scenarioSearchKw = ref('');
const allScenarios = ref<ApiScenarioItem[]>([]);
const scenarioSelection = ref<ApiScenarioItem[]>([]);
const scenarioTableRef = ref<any>(null);

const filteredScenarios = computed(() => {
  const existing = new Set(selectedScenarios.value.map(s => s.id));
  let list = allScenarios.value.filter(s => !existing.has(s.id));
  if (scenarioSearchKw.value) {
    const kw = scenarioSearchKw.value.toLowerCase();
    list = list.filter(s => s.name.toLowerCase().includes(kw));
  }
  return list;
});

async function loadEnvs() {
  if (!store.currentProject) return;
  const res = await apiEnvApi.list(store.currentProject.id);
  envList.value = res.data;
}

async function loadAllScenarios() {
  if (!store.currentProject) return;
  const res = await apiScenarioApi.list({ projectId: store.currentProject.id, page: 1, size: 500 });
  allScenarios.value = res.data.records;
}

async function loadPlan() {
  if (isCreate) return;
  loading.value = true;
  try {
    const res = await apiPlanApi.detail(planId);
    const p = res.data.plan;
    form.name = p.name;
    form.description = p.description || '';
    form.environmentId = p.environmentId;
    form.parallel = p.parallel || 0;
    form.projectId = p.projectId;
    form.directoryId = p.directoryId;

    const scenarios: any[] = res.data.scenarios || [];
    selectedScenarios.value = scenarios.map(ps => {
      const full = allScenarios.value.find(s => s.id === ps.scenarioId);
      return {
        id: ps.scenarioId,
        name: ps.scenarioName || full?.name || '',
        stepCount: ps.stepCount || full?.stepCount || 0,
        failStrategy: full?.failStrategy || 'STOP',
        tags: full?.tags || [],
        description: full?.description || '',
        _steps: [],
      };
    });
    loadScenarioSteps();
  } finally { loading.value = false; }
}

function onScenarioSelectionChange(rows: ApiScenarioItem[]) {
  scenarioSelection.value = rows;
}

async function confirmAddScenarios() {
  for (const s of scenarioSelection.value) {
    selectedScenarios.value.push({
      id: s.id, name: s.name, stepCount: s.stepCount || 0,
      failStrategy: s.failStrategy || 'STOP', tags: s.tags || [], description: s.description || '',
      _steps: [],
    });
  }
  scenarioDialog.value = false;
  scenarioSelection.value = [];
  loadScenarioSteps();
}

function removeScenario(idx: number) {
  selectedScenarios.value.splice(idx, 1);
}

function moveScenario(idx: number, dir: number) {
  const target = idx + dir;
  if (target < 0 || target >= selectedScenarios.value.length) return;
  const tmp = selectedScenarios.value[idx];
  selectedScenarios.value[idx] = selectedScenarios.value[target];
  selectedScenarios.value[target] = tmp;
  selectedScenarios.value = [...selectedScenarios.value];
}

const allDefs = ref<ApiDef[]>([]);

async function loadDefs() {
  if (!store.currentProject) return;
  try {
    const res = await apiDefApi.listAll(store.currentProject.id);
    allDefs.value = res.data.records;
  } catch {}
}

const caseDetailDialog = ref(false);
const caseDetailData = ref<any>(null);

async function showCaseDetail(step: any) {
  if (!step.caseId) return;
  try {
    const res = await apiCaseApi.detail(step.caseId);
    caseDetailData.value = res.data;
    caseDetailDialog.value = true;
  } catch { ElMessage.error('加载用例详情失败'); }
}

function formatJson(s: string) {
  if (!s) return '';
  try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; }
}

async function loadScenarioSteps() {
  for (const scn of selectedScenarios.value) {
    try {
      const res = await apiScenarioApi.detail(scn.id);
      const steps = res.data.steps || [];
      scn._steps = steps.map((st: any) => {
        const d = allDefs.value.find(x => x.id === st._apiId);
        return {
          ...st,
          _apiMethod: st.apiMethod || d?.method || '',
          _apiName: st.apiName || d?.name || '',
          _apiPath: st.apiPath || d?.path || '',
          _caseName: st.caseName || '',
        };
      });
      scn.stepCount = steps.length;
    } catch {}
  }
}

function goScenario(id: string) {
  router.push('/api-auto/scenario/' + id);
}

function methodTagType(m: string): any {
  return ({ GET: 'success', POST: 'warning', PUT: '', DELETE: 'danger' } as any)[m] || 'info';
}

async function doSave() {
  if (!form.name?.trim()) { ElMessage.warning('请输入计划名称'); return; }
  if (!form.environmentId) { ElMessage.warning('请选择执行环境'); return; }
  saving.value = true;
  const payload = {
    ...form,
    scenarios: selectedScenarios.value.map(s => ({ scenarioId: s.id })),
  };
  try {
    if (isCreate) {
      const res = await apiPlanApi.create(payload);
      ElMessage.success('计划创建成功');
      router.replace('/api-auto/plan/' + res.data.id + '/edit');
    } else {
      await apiPlanApi.update(planId, payload);
      ElMessage.success('计划保存成功');
    }
  } finally { saving.value = false; }
}

onMounted(async () => {
  await Promise.all([loadEnvs(), loadAllScenarios(), loadDefs()]);
  await loadPlan();
});
</script>

<style scoped>
.plan-form { padding: 16px 24px; max-width: 1100px; margin: 0 auto; height: 100%; overflow: auto; }
.detail-top { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.detail-title { font-size: 18px; font-weight: 600; }
.section-card { margin-bottom: 16px; }
.card-title { font-weight: 600; font-size: 14px; }
.card-header-row { display: flex; align-items: center; justify-content: space-between; }
.empty-hint { color: #c0c4cc; text-align: center; padding: 32px 0; }

.link-text { color: #409eff; cursor: pointer; font-weight: 500; }
.link-text:hover { text-decoration: underline; }

.scenario-expand { padding: 8px 12px; }
.expand-desc { font-size: 12px; color: #909399; margin-bottom: 8px; }
.expand-empty { font-size: 12px; color: #c0c4cc; }
.expand-steps { display: flex; flex-direction: column; gap: 4px; }
.expand-step-row { display: flex; align-items: center; gap: 6px; font-size: 12px; padding: 4px 8px; background: #f9fafb; border-radius: 4px; }
.expand-step-num { color: #909399; font-weight: 600; min-width: 24px; }
.expand-step-name { color: #303133; }
.expand-step-path { color: #909399; font-size: 11px; font-family: 'Consolas', monospace; margin-left: 4px; }

.detail-label { font-size: 13px; font-weight: 600; color: #303133; margin-bottom: 6px; }
.detail-code { background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; font-size: 12px; max-height: 200px; overflow: auto; white-space: pre-wrap; word-break: break-all; font-family: 'Consolas', monospace; margin: 0; }
.detail-kv { font-size: 12px; font-family: monospace; color: #606266; padding: 2px 0; }
.detail-assertion { font-size: 12px; color: #606266; padding: 3px 8px; background: #f4f4f5; border-radius: 4px; margin-bottom: 4px; border: 1px solid #e9e9eb; }
</style>
