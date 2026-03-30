<template>
  <div class="detail-page" v-loading="loading">
    <div class="detail-top">
      <el-button text @click="$router.push('/ui-auto/cases')"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
      <span class="detail-title">{{ caseInfo.name || '用例详情' }}</span>
      <el-button type="success" size="small" :loading="running" @click="showEnvDialog">运行</el-button>
      <el-button type="primary" size="small" :loading="saving" style="margin-left:auto" @click="doSave">保存</el-button>
    </div>

    <div class="content-card">
      <div class="section-label">用例信息</div>
      <el-form :model="caseInfo" label-width="100px" class="case-form">
        <el-form-item label="名称"><el-input v-model="caseInfo.name" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="浏览器">
              <el-select v-model="caseInfo.browserType" style="width:100%">
                <el-option value="CHROMIUM" label="Chromium" />
                <el-option value="FIREFOX" label="Firefox" />
                <el-option value="WEBKIT" label="WebKit" />
                <el-option value="EDGE" label="Edge" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="驱动">
              <el-select v-model="caseInfo.driverType" style="width:100%">
                <el-option value="PLAYWRIGHT" label="Playwright" />
                <el-option value="SELENIUM" label="Selenium" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8"><el-form-item label="无头"><el-switch v-model="caseInfo.headless" :active-value="1" :inactive-value="0" /></el-form-item></el-col>
        </el-row>
      </el-form>
    </div>

    <div class="content-card steps-wrap">
      <div class="steps-header">
        <span class="section-label">步骤 ({{ steps.length }})</span>
        <el-button type="primary" size="small" :icon="Plus" @click="addStep">添加步骤</el-button>
      </div>

      <div v-if="!steps.length" class="empty-hint">暂无步骤，点击「添加步骤」</div>

      <div v-for="(step, idx) in steps" :key="idx" class="step-card">
        <div class="step-head">
          <span class="step-idx">#{{ idx + 1 }}</span>
          <el-tag size="small" effect="plain" type="info">{{ step.stepType }}</el-tag>
          <span class="step-desc-preview">{{ step.description || '无描述' }}</span>
          <span v-if="elementSummary(step)" class="step-elem">{{ elementSummary(step) }}</span>
          <div class="step-actions">
            <el-switch v-model="step.enabled" :active-value="1" :inactive-value="0" size="small" />
            <el-button text type="primary" size="small" :disabled="idx === 0" @click="moveStep(idx, -1)"><el-icon><Top /></el-icon></el-button>
            <el-button text type="primary" size="small" :disabled="idx === steps.length - 1" @click="moveStep(idx, 1)"><el-icon><Bottom /></el-icon></el-button>
            <el-button text type="danger" size="small" @click="steps.splice(idx, 1)"><el-icon><Delete /></el-icon></el-button>
          </div>
        </div>
        <div class="step-form">
          <el-form label-width="110px" size="small">
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="步骤类型">
                  <el-select v-model="step.stepType" style="width:100%" @change="onStepTypeChange(step)">
                    <el-option v-for="t in stepTypes" :key="t" :label="t" :value="t" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="16"><el-form-item label="描述"><el-input v-model="step.description" placeholder="步骤说明" /></el-form-item></el-col>
            </el-row>

            <template v-if="step.stepType === 'NAVIGATE'">
              <el-form-item label="目标 URL"><el-input v-model="step.targetUrl" placeholder="https://..." /></el-form-item>
            </template>

            <template v-else-if="needsElement(step.stepType)">
              <el-form-item label="定位方式">
                <el-radio-group v-model="step._useManual" size="small">
                  <el-radio :label="false">页面元素</el-radio>
                  <el-radio :label="true">手动定位</el-radio>
                </el-radio-group>
              </el-form-item>
              <template v-if="!step._useManual">
                <el-form-item label="页面">
                  <el-select v-model="step._pageId" style="width:100%" placeholder="选择页面对象" filterable @change="onStepPageChange(step)">
                    <el-option v-for="p in pages" :key="p.id" :label="p.name" :value="p.id" />
                  </el-select>
                </el-form-item>
                <el-form-item label="元素">
                  <el-select v-model="step.elementId" style="width:100%" placeholder="选择元素" filterable :loading="step._elLoading"
                    @focus="ensureElements(step)">
                    <el-option v-for="e in (step._elements || [])" :key="e.id" :label="e.name + ' (' + e.locatorType + ')'" :value="e.id" />
                  </el-select>
                </el-form-item>
              </template>
              <template v-else>
                <el-row :gutter="12">
                  <el-col :span="8">
                    <el-form-item label="定位类型">
                      <el-select v-model="step.locatorType" style="width:100%">
                        <el-option v-for="lt in locatorTypes" :key="lt" :label="lt" :value="lt" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="16"><el-form-item label="定位值"><el-input v-model="step.locatorValue" /></el-form-item></el-col>
                </el-row>
              </template>
              <el-form-item v-if="step.stepType === 'INPUT'" label="输入内容"><el-input v-model="step.inputValue" /></el-form-item>
            </template>

            <template v-else-if="step.stepType === 'WAIT'">
              <el-row :gutter="12">
                <el-col :span="10">
                  <el-form-item label="等待类型">
                    <el-select v-model="step.waitType" style="width:100%">
                      <el-option-group label="显式等待（推荐）">
                        <el-option value="ELEMENT_VISIBLE" label="等待元素可见" />
                        <el-option value="ELEMENT_HIDDEN" label="等待元素隐藏" />
                        <el-option value="ELEMENT_CLICKABLE" label="等待元素可点击" />
                        <el-option value="ELEMENT_PRESENT" label="等待元素存在（DOM）" />
                      </el-option-group>
                      <el-option-group label="隐式等待">
                        <el-option value="IMPLICIT" label="设置隐式等待超时" />
                      </el-option-group>
                      <el-option-group label="强制等待">
                        <el-option value="FIXED" label="固定等待（不推荐）" />
                      </el-option-group>
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="10"><el-form-item :label="step.waitType === 'IMPLICIT' ? '超时(ms)' : step.waitType === 'FIXED' ? '等待时间(ms)' : '超时(ms)'"><el-input-number v-model="step.waitTimeoutMs" :min="0" :step="500" controls-position="right" style="width:100%" /></el-form-item></el-col>
              </el-row>
              <el-alert v-if="step.waitType === 'IMPLICIT'" type="info" :closable="false" show-icon style="margin-bottom:12px">
                隐式等待会设置全局超时，后续查找元素时若未立即找到则等待指定时间
              </el-alert>
              <el-alert v-if="step.waitType === 'FIXED'" type="warning" :closable="false" show-icon style="margin-bottom:12px">
                强制等待会阻塞线程，建议优先使用显式等待
              </el-alert>
              <template v-if="step.waitType && step.waitType !== 'FIXED' && step.waitType !== 'IMPLICIT'">
                <el-form-item label="定位方式">
                  <el-radio-group v-model="step._useManual" size="small">
                    <el-radio :label="false">页面元素</el-radio>
                    <el-radio :label="true">手动定位</el-radio>
                  </el-radio-group>
                </el-form-item>
                <template v-if="!step._useManual">
                  <el-form-item label="页面">
                    <el-select v-model="step._pageId" style="width:100%" filterable @change="onStepPageChange(step)">
                      <el-option v-for="p in pages" :key="p.id" :label="p.name" :value="p.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="元素">
                    <el-select v-model="step.elementId" style="width:100%" filterable @focus="ensureElements(step)">
                      <el-option v-for="e in (step._elements || [])" :key="e.id" :label="e.name" :value="e.id" />
                    </el-select>
                  </el-form-item>
                </template>
                <template v-else>
                  <el-row :gutter="12">
                    <el-col :span="8">
                      <el-form-item label="定位类型">
                        <el-select v-model="step.locatorType" style="width:100%">
                          <el-option v-for="lt in locatorTypes" :key="lt" :label="lt" :value="lt" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="16"><el-form-item label="定位值"><el-input v-model="step.locatorValue" /></el-form-item></el-col>
                  </el-row>
                </template>
              </template>
            </template>

            <template v-else-if="step.stepType === 'ASSERT'">
              <el-form-item label="断言类型">
                <el-select v-model="step.assertType" style="width:100%" placeholder="选择断言类型" @change="onAssertTypeChange(step)">
                  <el-option-group label="页面级断言">
                    <el-option value="URL_EQUALS" label="URL 完全匹配" />
                    <el-option value="URL_CONTAINS" label="URL 包含" />
                    <el-option value="TITLE_EQUALS" label="页面标题匹配" />
                  </el-option-group>
                  <el-option-group label="元素断言">
                    <el-option value="ELEMENT_EXISTS" label="元素存在" />
                    <el-option value="ELEMENT_NOT_EXISTS" label="元素不存在" />
                    <el-option value="ELEMENT_VISIBLE" label="元素可见" />
                    <el-option value="ELEMENT_ENABLED" label="元素可用" />
                    <el-option value="TEXT_EQUALS" label="元素文本完全匹配" />
                    <el-option value="TEXT_CONTAINS" label="元素文本包含" />
                    <el-option value="ATTRIBUTE_EQUALS" label="元素属性值匹配" />
                  </el-option-group>
                </el-select>
              </el-form-item>

              <template v-if="assertNeedsElement(step.assertType)">
                <el-form-item label="定位方式">
                  <el-radio-group v-model="step._useManual" size="small">
                    <el-radio :label="false">页面元素</el-radio>
                    <el-radio :label="true">手动定位</el-radio>
                  </el-radio-group>
                </el-form-item>
                <template v-if="!step._useManual">
                  <el-form-item label="页面">
                    <el-select v-model="step._pageId" style="width:100%" filterable placeholder="选择页面" @change="onStepPageChange(step)">
                      <el-option v-for="p in pages" :key="p.id" :label="p.name" :value="p.id" />
                    </el-select>
                  </el-form-item>
                  <el-form-item label="元素">
                    <el-select v-model="step.elementId" style="width:100%" filterable placeholder="选择元素" @focus="ensureElements(step)">
                      <el-option v-for="e in (step._elements || [])" :key="e.id" :label="e.name + ' (' + e.locatorType + ')'" :value="e.id" />
                    </el-select>
                  </el-form-item>
                </template>
                <template v-else>
                  <el-row :gutter="12">
                    <el-col :span="8">
                      <el-form-item label="定位类型">
                        <el-select v-model="step.locatorType" style="width:100%">
                          <el-option v-for="lt in locatorTypes" :key="lt" :label="lt" :value="lt" />
                        </el-select>
                      </el-form-item>
                    </el-col>
                    <el-col :span="16"><el-form-item label="定位值"><el-input v-model="step.locatorValue" /></el-form-item></el-col>
                  </el-row>
                </template>
              </template>

              <el-form-item v-if="step.assertType === 'ATTRIBUTE_EQUALS'" label="属性名">
                <el-input v-model="step.assertExpression" placeholder="如 href, class, data-id" />
              </el-form-item>
              <el-form-item v-if="assertNeedsExpected(step.assertType)" label="期望值">
                <el-input v-model="step.assertExpected" :placeholder="assertExpectedHint(step.assertType)" />
              </el-form-item>
            </template>

            <template v-else-if="step.stepType === 'SCRIPT'">
              <el-form-item label="脚本">
                <el-input v-model="step.scriptContent" type="textarea" :rows="5" placeholder="脚本内容" class="code-area" />
              </el-form-item>
            </template>
          </el-form>
        </div>
      </div>
    </div>

    <el-dialog v-model="envDialogVisible" title="选择运行环境" width="440px" destroy-on-close>
      <el-alert v-if="!envList.length" type="warning" :closable="false" show-icon style="margin-bottom:12px">
        暂无环境配置，请先在「环境管理」中创建环境
      </el-alert>
      <el-form label-width="80px">
        <el-form-item label="环境" required>
          <el-select v-model="selectedEnvId" style="width:100%" placeholder="请选择运行环境">
            <el-option v-for="env in envList" :key="env.id" :label="env.name + ' — ' + env.baseUrl" :value="env.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="envDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="running" :disabled="!selectedEnvId" @click="runCase">确认运行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, Plus, Top, Bottom, Delete } from '@element-plus/icons-vue';
import { uiCaseApi, uiExecApi, uiPageApi, uiElementApi, uiEnvApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { UiPageItem, UiElementItem, UiTestStepItem, UiEnv } from '../../types';

type StepRow = UiTestStepItem & {
  _useManual?: boolean;
  _pageId?: string;
  _elements?: UiElementItem[];
  _elLoading?: boolean;
};

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const caseId = route.params.id as string;
const projectId = () => store.currentProject?.id || '';

const stepTypes = [
  'NAVIGATE', 'CLICK', 'DOUBLE_CLICK', 'RIGHT_CLICK', 'INPUT', 'CLEAR', 'SELECT', 'HOVER',
  'KEY_PRESS', 'WAIT', 'SCREENSHOT', 'ASSERT', 'SCRIPT', 'SCROLL',
] as const;

const locatorTypes = ['CSS_SELECTOR', 'XPATH', 'ID', 'NAME', 'LINK_TEXT', 'TAG_NAME'] as const;

const loading = ref(false);
const saving = ref(false);
const running = ref(false);
const pages = ref<UiPageItem[]>([]);
const envDialogVisible = ref(false);
const envList = ref<UiEnv[]>([]);
const selectedEnvId = ref<string>('');
const caseInfo = reactive<any>({
  name: '',
  browserType: 'CHROMIUM',
  driverType: 'PLAYWRIGHT',
  headless: 1,
});
const steps = ref<StepRow[]>([]);

function needsElement(t: string) {
  return ['CLICK', 'DOUBLE_CLICK', 'RIGHT_CLICK', 'INPUT', 'CLEAR', 'SELECT', 'HOVER', 'KEY_PRESS', 'SCROLL'].includes(t);
}

const ELEMENT_ASSERT_TYPES = ['ELEMENT_EXISTS', 'ELEMENT_NOT_EXISTS', 'ELEMENT_VISIBLE', 'ELEMENT_ENABLED', 'TEXT_EQUALS', 'TEXT_CONTAINS', 'ATTRIBUTE_EQUALS'];
function assertNeedsElement(t?: string) { return !!t && ELEMENT_ASSERT_TYPES.includes(t); }
function assertNeedsExpected(t?: string) { return !!t && ['URL_EQUALS', 'URL_CONTAINS', 'TITLE_EQUALS', 'TEXT_EQUALS', 'TEXT_CONTAINS', 'ATTRIBUTE_EQUALS'].includes(t); }
function assertExpectedHint(t?: string) {
  if (t === 'URL_EQUALS' || t === 'URL_CONTAINS') return '如 https://ariconline.top/archive';
  if (t === 'TITLE_EQUALS') return '页面标题';
  if (t === 'TEXT_EQUALS' || t === 'TEXT_CONTAINS') return '期望的文本内容';
  if (t === 'ATTRIBUTE_EQUALS') return '属性期望值';
  return '';
}
function onAssertTypeChange(step: StepRow) {
  step.assertExpression = undefined;
  step.assertExpected = undefined;
  if (assertNeedsElement(step.assertType)) {
    step._useManual = false;
  } else {
    step.elementId = undefined;
    step.locatorType = undefined;
    step.locatorValue = undefined;
    step._pageId = undefined;
    step._elements = [];
  }
}

function onStepTypeChange(step: StepRow) {
  step.targetUrl = undefined;
  step.elementId = undefined;
  step.locatorType = undefined;
  step.locatorValue = undefined;
  step.inputValue = undefined;
  step.waitType = undefined;
  step.waitTimeoutMs = undefined;
  step.assertType = undefined;
  step.assertExpression = undefined;
  step.assertExpected = undefined;
  step.scriptContent = undefined;
  step._pageId = undefined;
  step._elements = [];
  if (needsElement(step.stepType!)) {
    step._useManual = false;
  }
}

function defaultStep(): StepRow {
  return {
    stepType: 'CLICK',
    description: '',
    enabled: 1,
    _useManual: false,
    _elements: [],
  };
}

function addStep() {
  steps.value.push(defaultStep());
}

function moveStep(i: number, d: number) {
  const j = i + d;
  if (j < 0 || j >= steps.value.length) return;
  const t = steps.value[i];
  steps.value[i] = steps.value[j];
  steps.value[j] = t;
}

async function loadPages() {
  if (!projectId()) return;
  const res = await uiPageApi.list({ projectId: projectId(), page: 1, size: 500 });
  pages.value = res.data.records || [];
}

async function ensureElements(step: StepRow) {
  if (!step._pageId) return;
  step._elLoading = true;
  try {
    step._elements = (await uiElementApi.list(step._pageId)).data || [];
  } finally {
    step._elLoading = false;
  }
}

function onStepPageChange(step: StepRow) {
  step.elementId = undefined;
  step._elements = [];
  if (step._pageId) ensureElements(step);
}

function elementSummary(step: StepRow) {
  if (step.elementId && step.elementName) return step.elementName;
  if (step.locatorType && step.locatorValue) return `${step.locatorType}: ${step.locatorValue}`;
  return '';
}

async function hydrateStepsFromDetail() {
  for (const step of steps.value) {
    if (step.elementId && !step._useManual) {
      step._useManual = false;
      try {
        const el = (await uiElementApi.detail(step.elementId)).data;
        step._pageId = el.pageId;
        await ensureElements(step);
      } catch {
        step._useManual = true;
      }
    } else if (step.locatorValue) {
      step._useManual = true;
    } else {
      step._useManual = false;
    }
  }
}

function buildPayloadSteps(): any[] {
  return steps.value.map((s, i) => {
    const row: any = {
      sortOrder: i,
      stepType: s.stepType,
      description: s.description || undefined,
      enabled: s.enabled ?? 1,
    };
    if (s.stepType === 'NAVIGATE') row.targetUrl = s.targetUrl;
    else if (s.stepType === 'WAIT') {
      row.waitType = s.waitType;
      row.waitTimeoutMs = s.waitTimeoutMs;
      if (s.waitType && s.waitType !== 'FIXED') {
        if (!s._useManual && s.elementId) row.elementId = s.elementId;
        else {
          row.locatorType = s.locatorType;
          row.locatorValue = s.locatorValue;
        }
      }
    } else if (s.stepType === 'ASSERT') {
      row.assertType = s.assertType;
      row.assertExpression = s.assertExpression;
      row.assertExpected = s.assertExpected;
      if (assertNeedsElement(s.assertType)) {
        if (!s._useManual && s.elementId) row.elementId = s.elementId;
        else {
          row.locatorType = s.locatorType;
          row.locatorValue = s.locatorValue;
        }
      }
    } else if (s.stepType === 'SCRIPT') {
      row.scriptContent = s.scriptContent;
    } else if (needsElement(s.stepType!)) {
      if (!s._useManual && s.elementId) row.elementId = s.elementId;
      else {
        row.locatorType = s.locatorType;
        row.locatorValue = s.locatorValue;
      }
      if (s.stepType === 'INPUT') row.inputValue = s.inputValue;
    }
    return row;
  });
}

async function load() {
  loading.value = true;
  try {
    await loadPages();
    const res = await uiCaseApi.detail(caseId);
    const c = res.data;
    caseInfo.name = c.name || '';
    caseInfo.browserType = c.browserType || 'CHROMIUM';
    caseInfo.driverType = c.driverType || 'PLAYWRIGHT';
    caseInfo.headless = c.headless ?? 1;
    steps.value = (c.steps || []).map((st: UiTestStepItem) => ({
      ...st,
      _useManual: !!(st.locatorValue && !st.elementId),
      _pageId: undefined as string | undefined,
      _elements: [] as UiElementItem[],
    }));
    await hydrateStepsFromDetail();
  } finally {
    loading.value = false;
  }
}

async function doSave() {
  if (!caseInfo.name?.trim()) { ElMessage.warning('请输入用例名称'); return; }
  saving.value = true;
  try {
    await uiCaseApi.update(caseId, {
      name: caseInfo.name,
      browserType: caseInfo.browserType,
      driverType: caseInfo.driverType,
      headless: caseInfo.headless,
      steps: buildPayloadSteps(),
    });
    ElMessage.success('已保存');
    await load();
  } finally {
    saving.value = false;
  }
}

async function showEnvDialog() {
  const pid = projectId();
  if (!pid) return;
  try {
    envList.value = (await uiEnvApi.list(pid)).data || [];
  } catch { envList.value = []; }
  selectedEnvId.value = '';
  envDialogVisible.value = true;
}

async function runCase() {
  const pid = projectId();
  if (!pid) return;
  running.value = true;
  try {
    const res = await uiExecApi.runCase(caseId, pid, selectedEnvId.value || undefined);
    ElMessage.success('已开始执行');
    envDialogVisible.value = false;
    router.push('/ui-auto/execution/' + res.data.executionId);
  } finally {
    running.value = false;
  }
}

watch(() => store.currentProject, () => { loadPages(); load(); });

onMounted(() => { loadPages().then(() => load()); });
</script>

<style scoped>
.detail-page { padding: 16px 24px; max-width: 1200px; margin: 0 auto; height: 100%; overflow: auto; }
.detail-top { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.detail-title { font-size: 18px; font-weight: 600; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.section-label { font-size: 14px; font-weight: 600; color: #1f2329; }
.case-form { margin-top: 8px; }
.steps-wrap { margin-top: 16px; }
.steps-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.empty-hint { color: #909399; text-align: center; padding: 24px; }
.step-card { border: 1px solid #ebeef5; border-radius: 8px; margin-bottom: 12px; overflow: hidden; }
.step-head { display: flex; align-items: center; gap: 10px; padding: 10px 14px; background: #fafafa; border-bottom: 1px solid #ebeef5; flex-wrap: wrap; }
.step-idx { font-weight: 600; color: #909399; font-size: 13px; }
.step-desc-preview { flex: 1; font-size: 13px; color: #606266; min-width: 120px; }
.step-elem { font-size: 12px; color: #909399; max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.step-actions { display: flex; align-items: center; gap: 4px; margin-left: auto; }
.step-form { padding: 16px; }
.code-area { font-family: Consolas, monospace; }
</style>
