<template>
  <div class="report-page" v-loading="loading">
    <div class="report-top">
      <el-button text @click="$router.back()"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
      <span class="report-title">UI 执行报告</span>
      <span v-if="sourceName" class="report-source">— {{ sourceName }}</span>
      <el-tag v-if="isRunning" type="warning" size="small" effect="dark" style="margin-left:8px">
        执行中，自动刷新...
      </el-tag>
    </div>

    <template v-if="report">
      <el-card shadow="never" class="summary-card">
        <div class="summary-grid">
          <div class="summary-item">
            <el-progress type="circle" :percentage="progressPct" :color="progressColor" :width="80">
              <template #default><span class="pct-text">{{ progressPct }}%</span></template>
            </el-progress>
          </div>
          <div class="summary-nums">
            <div class="num-row">
              <span class="num-val">
                <template v-if="isRunning">{{ completedSteps }} / {{ report.execution.totalSteps }}</template>
                <template v-else>{{ report.execution.totalSteps }}</template>
              </span>
              <span class="num-lbl">{{ isRunning ? '进度' : '总步骤' }}</span>
            </div>
            <div class="num-row"><span class="num-val pass">{{ report.execution.passedSteps || 0 }}</span><span class="num-lbl">通过</span></div>
            <div class="num-row"><span class="num-val fail">{{ report.execution.failedSteps || 0 }}</span><span class="num-lbl">失败</span></div>
            <div class="num-row"><span class="num-val error">{{ report.execution.errorSteps || 0 }}</span><span class="num-lbl">异常</span></div>
          </div>
          <div class="summary-meta">
            <div v-if="sourceName">来源: <b>{{ sourceTypeLabel }}</b> — {{ sourceName }}</div>
            <div>状态: <el-tag :type="statusColor(report.execution.status)" size="small" effect="dark">{{ statusLabel(report.execution.status) }}</el-tag></div>
            <div>驱动: <b>{{ report.execution.driverType || 'PLAYWRIGHT' }}</b> / {{ report.execution.browserType || 'CHROMIUM' }}</div>
            <div>耗时: <b>{{ ((report.execution.durationMs || 0) / 1000).toFixed(2) }}s</b></div>
            <div>执行人: {{ report.execution.executedByName }}</div>
            <div>时间: {{ fmtTime(report.execution.startedAt) }}</div>
          </div>
        </div>
      </el-card>

      <el-card shadow="never" class="steps-card">
        <template #header><span style="font-weight:600">执行步骤</span></template>
        <div v-if="!groupedSteps.length" class="expand-empty">暂无步骤</div>
        <div v-for="(scenario, sIdx) in groupedSteps" :key="scenario.scenarioKey" class="group-block">
          <div class="group-title">场景 {{ sIdx + 1 }}：{{ scenario.scenarioName }}</div>
          <div v-for="(tc, cIdx) in scenario.cases" :key="tc.caseKey" class="case-block">
            <div class="case-title">用例 {{ cIdx + 1 }}：{{ tc.caseName }}</div>
            <div v-for="step in tc.steps" :key="step.stepKey" class="step-item" @click="toggleStep(step.stepKey)">
              <div class="step-header">
                <el-icon :color="step.status === 'PASS' ? '#52c41a' : '#f5222d'" :size="16">
                  <component :is="step.status === 'PASS' ? 'CircleCheck' : 'CircleClose'" />
                </el-icon>
                <span class="step-idx">{{ step.globalIndex }}.</span>
                <el-tag :type="stepTypeColor(step.stepType)" size="small" effect="dark" style="min-width:60px;text-align:center">{{ step.stepType }}</el-tag>
                <span class="step-desc">{{ step.actionDesc }}</span>
                <span class="step-dur">{{ step.durationMs }}ms</span>
              </div>
              <div v-if="expandedStepKey === step.stepKey" class="step-detail" @click.stop>
                <div v-if="step.screenshotPath" class="detail-section">
                  <div class="detail-label">截图</div>
                  <el-image
                    :src="'/api/ui-executions/screenshot/' + step.screenshotPath"
                    fit="contain"
                    style="max-width:100%;max-height:400px;border-radius:6px;border:1px solid #eee"
                    :preview-src-list="['/api/ui-executions/screenshot/' + step.screenshotPath]"
                    :hide-on-click-modal="false"
                    @click.stop
                  />
                </div>
                <div v-if="step.errorMessage" class="detail-section">
                  <div class="detail-label">错误</div>
                  <pre class="error-text">{{ step.errorMessage }}</pre>
                </div>
                <div v-if="step.pageUrl" class="detail-section">
                  <div class="detail-label">页面URL</div>
                  <span style="font-size:12px;color:#606266">{{ step.pageUrl }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { useRoute } from 'vue-router';
import { ArrowLeft, CircleCheck, CircleClose } from '@element-plus/icons-vue';
import { uiExecApi } from '../../api';

const route = useRoute();
const execId = String(route.params.id);
const loading = ref(true);
const report = ref<any>(null);
const expandedStepKey = ref<string | null>(null);
let pollTimer: ReturnType<typeof setInterval> | null = null;

const isRunning = computed(() => report.value?.execution?.status === 'RUNNING');

const completedSteps = computed(() => {
  if (!report.value) return 0;
  return (report.value.execution.passedSteps || 0)
       + (report.value.execution.failedSteps || 0)
       + (report.value.execution.errorSteps || 0);
});

const progressPct = computed(() => {
  const total = report.value?.execution?.totalSteps || 0;
  if (!total) return 0;
  if (isRunning.value) {
    return Math.round(completedSteps.value / total * 100);
  }
  return Math.round((report.value.execution.passedSteps || 0) / total * 100);
});

const progressColor = computed(() => {
  if (isRunning.value) return '#E6A23C';
  return (report.value?.execution?.failedSteps || report.value?.execution?.errorSteps) ? '#f56c6c' : '#52c41a';
});

const sourceName = computed(() => {
  const e = report.value?.execution;
  if (!e) return '';
  return e.planName || e.scenarioName || e.caseName || '';
});

const sourceTypeLabel = computed(() => {
  const e = report.value?.execution;
  if (!e) return '';
  if (e.planName) return '测试计划';
  if (e.scenarioName) return '测试场景';
  if (e.caseName) return '测试用例';
  return '';
});

const groupedSteps = computed(() => {
  const steps = report.value?.steps || [];
  const scenarioMap = new Map<string, any>();
  let globalIndex = 1;

  for (const st of steps) {
    const scenarioName = st.scenarioName || report.value?.execution?.scenarioName || '未分组场景';
    const caseName = st.caseName || report.value?.execution?.caseName || '未命名用例';
    const scenarioKey = st.scenarioId || `scenario_${scenarioName}`;
    const caseKey = st.caseId || `${scenarioKey}_${caseName}`;
    const stepKey = String(st.id || `${caseKey}_${globalIndex}`);

    let scenarioGroup = scenarioMap.get(scenarioKey);
    if (!scenarioGroup) {
      scenarioGroup = { scenarioKey, scenarioName, cases: [] as any[] };
      scenarioMap.set(scenarioKey, scenarioGroup);
    }

    let caseGroup = scenarioGroup.cases.find((c: any) => c.caseKey === caseKey);
    if (!caseGroup) {
      caseGroup = { caseKey, caseName, steps: [] as any[] };
      scenarioGroup.cases.push(caseGroup);
    }

    caseGroup.steps.push({ ...st, stepKey, globalIndex });
    globalIndex++;
  }

  return Array.from(scenarioMap.values());
});

function fmtTime(t?: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }
function statusColor(s: string): any { return ({ PASS: 'success', FAIL: 'danger', RUNNING: 'warning', ERROR: 'danger' } as any)[s] || 'info'; }
function statusLabel(s: string) { return ({ PASS: '全部通过', FAIL: '有失败', RUNNING: '执行中', ERROR: '异常' } as any)[s] || s; }
function stepTypeColor(t: string): any {
  return ({ NAVIGATE: '', CLICK: 'primary', INPUT: 'success', ASSERT: 'warning', SCREENSHOT: 'info', WAIT: 'info', SCRIPT: 'danger' } as any)[t] || '';
}
function toggleStep(stepKey: string) { expandedStepKey.value = expandedStepKey.value === stepKey ? null : stepKey; }

async function loadReport() {
  try {
    const res = await uiExecApi.report(execId);
    report.value = res.data;
  } catch { /* ignore */ }
}

function startPolling() {
  stopPolling();
  pollTimer = setInterval(async () => {
    await loadReport();
    if (!isRunning.value) stopPolling();
  }, 3000);
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

onMounted(async () => {
  try {
    await loadReport();
    if (isRunning.value) startPolling();
  } finally { loading.value = false; }
});

onUnmounted(stopPolling);
</script>

<style scoped>
.report-page { padding: 16px 24px; max-width: 1000px; margin: 0 auto; height: 100%; overflow: auto; }
.report-top { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.report-title { font-size: 18px; font-weight: 600; }
.report-source { font-size: 15px; color: #606266; font-weight: 500; }
.summary-card { margin-bottom: 16px; }
.summary-grid { display: flex; gap: 32px; align-items: center; }
.summary-nums { display: flex; gap: 24px; }
.num-row { display: flex; flex-direction: column; align-items: center; }
.num-val { font-size: 24px; font-weight: 700; }
.num-val.pass { color: #52c41a; } .num-val.fail { color: #f5222d; } .num-val.error { color: #fa8c16; }
.num-lbl { font-size: 12px; color: #909399; }
.pct-text { font-size: 16px; font-weight: 700; }
.summary-meta { font-size: 13px; color: #606266; line-height: 1.8; }
.steps-card { margin-bottom: 16px; }
.expand-empty { color: #909399; font-size: 13px; }
.group-block { margin-bottom: 14px; }
.group-title { font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 8px; }
.case-block { margin-bottom: 8px; }
.case-title { font-size: 13px; font-weight: 600; color: #606266; margin: 4px 0 6px; }
.step-item { border: 1px solid #f0f0f0; border-radius: 6px; margin-bottom: 6px; cursor: pointer; transition: all .15s; }
.step-item:hover { border-color: #d9ecff; }
.step-header { display: flex; align-items: center; gap: 8px; padding: 10px 14px; font-size: 13px; }
.step-idx { font-weight: 600; color: #909399; }
.step-desc { flex: 1; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.step-dur { color: #606266; font-size: 12px; white-space: nowrap; }
.step-detail { padding: 0 14px 14px; border-top: 1px solid #f5f5f5; }
.detail-section { margin-top: 10px; }
.detail-label { font-size: 12px; color: #909399; font-weight: 600; margin-bottom: 4px; }
.error-text { background: #fff2f0; color: #f5222d; padding: 8px; border-radius: 4px; font-size: 12px; white-space: pre-wrap; }
</style>
