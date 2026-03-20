<template>
  <div class="scenario-detail" v-loading="loading">
    <div class="detail-top">
      <el-button text @click="$router.push('/api-auto/scenarios')"><el-icon><ArrowLeft /></el-icon> 返回列表</el-button>
      <span class="detail-title">{{ scenario.name || '场景编排' }}</span>
      <el-button type="primary" size="small" @click="doSave" :loading="saving" style="margin-left:auto">保存场景</el-button>
    </div>

    <!-- 基本信息 -->
    <el-card shadow="never" class="section-card">
      <template #header><span class="card-title">场景信息</span></template>
      <el-form :model="scenario" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="10"><el-form-item label="场景名称" required><el-input v-model="scenario.name" /></el-form-item></el-col>
          <el-col :span="7">
            <el-form-item label="失败策略">
              <el-select v-model="scenario.failStrategy" style="width:100%">
                <el-option value="STOP" label="遇到失败停止" />
                <el-option value="CONTINUE" label="继续执行" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="7">
            <el-form-item label="超时(ms)">
              <el-input-number v-model="scenario.timeoutMs" :min="0" :step="1000" controls-position="right" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标签">
          <el-select v-model="scenario.tags" multiple filterable allow-create default-first-option placeholder="输入后回车" style="width:100%" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="scenario.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </el-card>

    <!-- 步骤编排 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">步骤编排 ({{ steps.length }})</span>
          <div class="step-add-bar">
            <el-dropdown trigger="click" @command="addStep">
              <el-button type="primary" size="small"><el-icon><Plus /></el-icon> 添加步骤</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="API_CASE"><el-icon><Connection /></el-icon> 引用 API 用例</el-dropdown-item>
                  <el-dropdown-item command="SCRIPT"><el-icon><Document /></el-icon> Groovy 脚本</el-dropdown-item>
                  <el-dropdown-item command="WAIT"><el-icon><Clock /></el-icon> 等待时间</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>

      <div v-if="!steps.length" class="empty-hint">暂无步骤，点击上方按钮添加</div>

      <div class="step-list">
        <div v-for="(step, idx) in steps" :key="idx" :class="['step-card', { active: editingStepIdx === idx }]">
          <div class="step-header" @click="toggleEditStep(idx)">
            <div class="step-drag-handle" @mousedown.stop="startDrag(idx, $event)">
              <el-icon><Rank /></el-icon>
            </div>
            <span class="step-num">#{{ idx + 1 }}</span>
            <el-tag v-if="step.stepType === 'API_CASE'" type="" size="small" effect="plain" class="step-type-tag">
              <el-icon style="vertical-align:-2px"><Connection /></el-icon> API
            </el-tag>
            <el-tag v-else-if="step.stepType === 'SCRIPT'" type="warning" size="small" effect="plain" class="step-type-tag">
              <el-icon style="vertical-align:-2px"><Document /></el-icon> 脚本
            </el-tag>
            <el-tag v-else type="info" size="small" effect="plain" class="step-type-tag">
              <el-icon style="vertical-align:-2px"><Clock /></el-icon> 等待
            </el-tag>

            <span class="step-desc" v-if="step.stepType === 'API_CASE'">
              <el-tag v-if="step.apiMethod" :type="methodTagType(step.apiMethod)" size="small" effect="dark" style="margin-right:4px">{{ step.apiMethod }}</el-tag>
              {{ step.apiName || '未选择' }} / {{ step.caseName || '未选择' }}
            </span>
            <span class="step-desc" v-else-if="step.stepType === 'SCRIPT'">
              {{ step.scriptContent ? step.scriptContent.substring(0, 60) + '...' : '空脚本' }}
            </span>
            <span class="step-desc" v-else>等待 {{ step.delayMs || 0 }}ms</span>

            <div class="step-right">
              <el-switch v-model="step.enabled" :active-value="1" :inactive-value="0" size="small" @click.stop />
              <el-button text type="primary" size="small" @click.stop="moveUp(idx)" :disabled="idx === 0">
                <el-icon><Top /></el-icon>
              </el-button>
              <el-button text type="primary" size="small" @click.stop="moveDown(idx)" :disabled="idx === steps.length - 1">
                <el-icon><Bottom /></el-icon>
              </el-button>
              <el-button text type="danger" size="small" @click.stop="removeStep(idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>

          <!-- 展开编辑区 -->
          <div v-if="editingStepIdx === idx" class="step-body">
            <!-- API_CASE 步骤 -->
            <template v-if="step.stepType === 'API_CASE'">
              <el-form label-width="90px">
                <el-form-item label="选择接口">
                  <el-select v-model="step._selectedApiId" style="width:100%" placeholder="搜索选择接口" filterable
                    @change="onApiChange(step)">
                    <el-option v-for="d in allDefs" :key="d.id" :label="d.method + ' ' + d.name + ' ' + d.path" :value="d.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="选择用例">
                  <el-select v-model="step.caseId" style="width:100%" placeholder="选择用例" filterable
                    @change="onCaseChange(step)">
                    <el-option v-for="c in (step._casesForApi || [])" :key="c.id"
                      :label="c.priority + ' | ' + c.name" :value="c.id" />
                  </el-select>
                </el-form-item>
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="延迟(ms)">
                      <el-input-number v-model="step.delayMs" :min="0" :step="500" controls-position="right" style="width:100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="重试次数">
                      <el-input-number v-model="step.retryCount" :min="0" :max="5" controls-position="right" style="width:100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="覆盖Body">
                  <el-input v-model="step.overrideBody" type="textarea" :rows="3" placeholder="留空则使用用例原始 Body" class="code-input" />
                </el-form-item>
              </el-form>
            </template>

            <!-- SCRIPT 步骤 -->
            <template v-else-if="step.stepType === 'SCRIPT'">
              <div class="script-toolbar">
                <span class="hint-text" style="margin:0">Groovy 脚本，可用变量: vars(Map), log(Logger)</span>
                <el-dropdown trigger="click" @command="(code: string) => { step.scriptContent = (step.scriptContent || '') + code }">
                  <el-button size="small" type="primary" text>插入模板</el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item :command="`vars.put('key', 'value')\n`">设置变量</el-dropdown-item>
                      <el-dropdown-item :command="`def val = vars.get('key')\nlog.info('val=' + val)\n`">获取变量</el-dropdown-item>
                      <el-dropdown-item :command="`vars.put('timestamp', String.valueOf(System.currentTimeMillis()))\n`">生成时间戳</el-dropdown-item>
                      <el-dropdown-item :command="`vars.put('uuid', UUID.randomUUID().toString())\n`">生成 UUID</el-dropdown-item>
                      <el-dropdown-item :command="`Thread.sleep(1000)\n`">等待1秒</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
              <el-input v-model="step.scriptContent" type="textarea" :rows="10" placeholder="// Groovy 脚本" class="code-input" />
            </template>

            <!-- WAIT 步骤 -->
            <template v-else>
              <el-form label-width="90px">
                <el-form-item label="等待时间">
                  <el-input-number v-model="step.delayMs" :min="0" :step="500" controls-position="right" style="width:200px" />
                  <span class="hint-text" style="margin-left:8px">毫秒</span>
                </el-form-item>
              </el-form>
            </template>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 选择 API 用例弹窗 -->
    <el-dialog v-model="caseSelectDialog" title="选择 API 用例" width="700px" destroy-on-close>
      <el-form inline>
        <el-form-item label="接口">
          <el-select v-model="csSelectedApi" filterable placeholder="选择接口" style="width:300px" @change="loadCasesForApi(csSelectedApi)">
            <el-option v-for="d in allDefs" :key="d.id" :label="d.method + ' ' + d.name + ' ' + d.path" :value="d.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-table :data="csCases" border max-height="350" style="width:100%;margin-top:8px" @row-click="confirmSelectCase">
        <el-table-column prop="name" label="用例名称" min-width="200" />
        <el-table-column prop="priority" label="优先级" width="80" align="center" />
        <el-table-column label="启用" width="70" align="center">
          <template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '是' : '否' }}</el-tag></template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, Plus, Delete, Connection, Document, Clock, Rank, Top, Bottom } from '@element-plus/icons-vue';
import { apiScenarioApi, apiDefApi, apiCaseApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { ApiDef, ApiCaseItem, ApiScenarioStepItem } from '../../types';

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const scenarioId = String(route.params.id);
const isCreate = scenarioId === 'create';

const loading = ref(false);
const saving = ref(false);
const scenario = reactive<any>({
  name: '', description: '', failStrategy: 'STOP', timeoutMs: 0, tags: [],
  projectId: store.currentProject?.id || '',
  directoryId: (route.query.directoryId as string) || undefined,
});
const steps = ref<any[]>([]);
const editingStepIdx = ref<number | null>(null);
const allDefs = ref<ApiDef[]>([]);

// Drag state
const dragIdx = ref(-1);

function toggleEditStep(idx: number) {
  editingStepIdx.value = editingStepIdx.value === idx ? null : idx;
}

function methodTagType(m: string): any {
  return ({ GET: 'success', POST: 'warning', PUT: '', DELETE: 'danger' } as any)[m] || 'info';
}

async function loadScenario() {
  if (isCreate) return;
  loading.value = true;
  try {
    const res = await apiScenarioApi.detail(scenarioId);
    const s = res.data;
    scenario.name = s.name;
    scenario.description = s.description || '';
    scenario.failStrategy = s.failStrategy || 'STOP';
    scenario.timeoutMs = s.timeoutMs || 0;
    scenario.tags = s.tags || [];
    scenario.projectId = s.projectId;
    scenario.directoryId = s.directoryId;
    steps.value = (s.steps || []).map((st: any) => ({
      ...st,
      stepType: st.stepType || 'API_CASE',
      enabled: st.enabled ?? 1,
      delayMs: st.delayMs || 0,
      retryCount: st.retryCount || 0,
      scriptContent: st.scriptContent || '',
      _selectedApiId: null,
      _casesForApi: [],
    }));
    for (const step of steps.value) {
      if (step.stepType === 'API_CASE' && step.caseId) {
        try {
          const caseRes = await apiCaseApi.detail(step.caseId);
          step._selectedApiId = caseRes.data.apiId;
          const casesRes = await apiCaseApi.list(caseRes.data.apiId);
          step._casesForApi = casesRes.data;
        } catch {}
      }
    }
  } finally { loading.value = false; }
}

async function loadDefs() {
  if (!store.currentProject) return;
  const res = await apiDefApi.listAll(store.currentProject.id);
  allDefs.value = res.data.records;
}

async function onApiChange(step: any) {
  step.caseId = undefined;
  step.caseName = '';
  step.apiName = '';
  step.apiMethod = '';
  step.apiPath = '';
  if (!step._selectedApiId) return;
  const d = allDefs.value.find(x => x.id === step._selectedApiId);
  if (d) { step.apiName = d.name; step.apiMethod = d.method; step.apiPath = d.path; }
  const res = await apiCaseApi.list(step._selectedApiId);
  step._casesForApi = res.data;
}

function onCaseChange(step: any) {
  const c = (step._casesForApi || []).find((x: any) => x.id === step.caseId);
  if (c) step.caseName = c.name;
}

function addStep(type: string) {
  const step: any = { stepType: type, enabled: 1, delayMs: 0, retryCount: 0, scriptContent: '', _selectedApiId: null, _casesForApi: [] };
  if (type === 'WAIT') step.delayMs = 1000;
  steps.value.push(step);
  editingStepIdx.value = steps.value.length - 1;

  if (type === 'API_CASE') {
    caseSelectDialog.value = true;
    pendingStepIdx.value = steps.value.length - 1;
  }
}

function removeStep(idx: number) {
  steps.value.splice(idx, 1);
  if (editingStepIdx.value === idx) editingStepIdx.value = null;
  else if (editingStepIdx.value !== null && editingStepIdx.value > idx) editingStepIdx.value--;
}

function moveUp(idx: number) {
  if (idx <= 0) return;
  const tmp = steps.value[idx];
  steps.value[idx] = steps.value[idx - 1];
  steps.value[idx - 1] = tmp;
  steps.value = [...steps.value];
  if (editingStepIdx.value === idx) editingStepIdx.value = idx - 1;
  else if (editingStepIdx.value === idx - 1) editingStepIdx.value = idx;
}

function moveDown(idx: number) {
  if (idx >= steps.value.length - 1) return;
  const tmp = steps.value[idx];
  steps.value[idx] = steps.value[idx + 1];
  steps.value[idx + 1] = tmp;
  steps.value = [...steps.value];
  if (editingStepIdx.value === idx) editingStepIdx.value = idx + 1;
  else if (editingStepIdx.value === idx + 1) editingStepIdx.value = idx;
}

function startDrag(idx: number, e: MouseEvent) {
  dragIdx.value = idx;
  const onMove = (me: MouseEvent) => { me.preventDefault(); };
  const onUp = () => {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    dragIdx.value = -1;
  };
  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
}

// Case selection dialog
const caseSelectDialog = ref(false);
const csSelectedApi = ref('');
const csCases = ref<ApiCaseItem[]>([]);
const pendingStepIdx = ref(-1);

async function loadCasesForApi(apiId: string) {
  if (!apiId) { csCases.value = []; return; }
  const res = await apiCaseApi.list(apiId);
  csCases.value = res.data;
}

function confirmSelectCase(row: ApiCaseItem) {
  if (pendingStepIdx.value >= 0 && pendingStepIdx.value < steps.value.length) {
    const step = steps.value[pendingStepIdx.value];
    step.caseId = row.id;
    step.caseName = row.name;
    step._selectedApiId = row.apiId;
    const d = allDefs.value.find(x => x.id === row.apiId);
    if (d) { step.apiName = d.name; step.apiMethod = d.method; step.apiPath = d.path; }
    step._casesForApi = [...csCases.value];
  }
  caseSelectDialog.value = false;
}

async function doSave() {
  if (!scenario.name?.trim()) { ElMessage.warning('请输入场景名称'); return; }
  saving.value = true;
  const payload: any = {
    name: scenario.name,
    description: scenario.description,
    failStrategy: scenario.failStrategy,
    timeoutMs: scenario.timeoutMs,
    tags: scenario.tags,
    steps: steps.value.map((s, i) => ({
      stepType: s.stepType || 'API_CASE',
      caseId: s.caseId || null,
      sortOrder: i,
      delayMs: s.delayMs || 0,
      retryCount: s.retryCount || 0,
      enabled: s.enabled ?? 1,
      overrideBody: s.overrideBody || null,
      scriptContent: s.scriptContent || null,
    })),
  };
  try {
    if (isCreate) {
      payload.projectId = scenario.projectId;
      payload.directoryId = scenario.directoryId;
      const res = await apiScenarioApi.create(payload);
      ElMessage.success('场景创建成功');
      router.replace('/api-auto/scenario/' + res.data.id);
    } else {
      await apiScenarioApi.update(scenarioId, payload);
      ElMessage.success('场景保存成功');
    }
  } finally { saving.value = false; }
}

onMounted(() => { loadDefs(); loadScenario(); });
</script>

<style scoped>
.scenario-detail { padding: 16px 24px; max-width: 1100px; margin: 0 auto; height: 100%; overflow: auto; }
.detail-top { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.detail-title { font-size: 18px; font-weight: 600; }
.section-card { margin-bottom: 16px; }
.card-title { font-weight: 600; font-size: 14px; }
.card-header-row { display: flex; align-items: center; justify-content: space-between; }
.empty-hint { color: #c0c4cc; text-align: center; padding: 32px 0; }
.hint-text { font-size: 12px; color: #909399; }

.step-list { display: flex; flex-direction: column; gap: 6px; }
.step-card { border: 1px solid #ebeef5; border-radius: 8px; overflow: hidden; transition: all 0.15s; }
.step-card:hover { border-color: #c6e2ff; }
.step-card.active { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64,158,255,0.1); }

.step-header { display: flex; align-items: center; gap: 8px; padding: 10px 14px; cursor: pointer; background: #fafbfc; }
.step-header:hover { background: #f5f7fa; }
.step-drag-handle { cursor: grab; color: #c0c4cc; display: flex; align-items: center; }
.step-drag-handle:active { cursor: grabbing; }
.step-num { font-size: 12px; font-weight: 600; color: #909399; min-width: 26px; flex-shrink: 0; }
.step-type-tag { white-space: nowrap; flex-shrink: 0; padding: 0 10px; height: 24px; line-height: 22px; display: inline-flex; align-items: center; gap: 2px; }
.step-desc { flex: 1; font-size: 13px; color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.step-right { display: flex; align-items: center; gap: 2px; flex-shrink: 0; }

.step-body { padding: 16px; border-top: 1px solid #ebeef5; background: #fff; }
.step-add-bar { display: flex; gap: 8px; }

.code-input :deep(textarea) { font-family: 'Consolas', 'Monaco', 'Courier New', monospace !important; font-size: 13px !important; line-height: 1.6 !important; }
.script-toolbar { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
</style>
