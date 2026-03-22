<template>
  <div class="report-page" v-loading="loading">
    <div class="report-top">
      <el-button text @click="$router.back()"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
      <span class="report-title">执行报告</span>
      <el-tag v-if="isRunning" type="warning" size="small" effect="dark"
        style="margin-left:8px;display:inline-flex;align-items:center;gap:4px;">
        <span>执行中，自动刷新...</span>
      </el-tag>
    </div>

    <template v-if="report">
      <!-- 汇总 -->
      <el-card shadow="never" class="summary-card">
        <div class="summary-grid">
          <div class="summary-item">
            <el-progress type="circle" :percentage="passPct" :color="report.execution.failedCases ? '#f56c6c' : '#52c41a'" :width="80">
              <template #default><span class="pct-text">{{ passPct }}%</span></template>
            </el-progress>
          </div>
          <div class="summary-nums">
            <div class="num-row"><span class="num-val">{{ report.execution.totalCases }}</span><span class="num-lbl">总用例</span></div>
            <div class="num-row"><span class="num-val pass">{{ report.execution.passedCases }}</span><span class="num-lbl">通过</span></div>
            <div class="num-row"><span class="num-val fail">{{ report.execution.failedCases }}</span><span class="num-lbl">失败</span></div>
            <div class="num-row"><span class="num-val error">{{ report.execution.errorCases }}</span><span class="num-lbl">异常</span></div>
          </div>
          <div class="summary-meta">
            <div>状态: <el-tag :type="statusColor(report.execution.status)" size="small" effect="dark">{{ statusLabel(report.execution.status) }}</el-tag></div>
            <div>耗时: <b>{{ (report.execution.durationMs / 1000).toFixed(2) }}s</b></div>
            <div>执行人: {{ report.execution.executedByName }}</div>
            <div>时间: {{ fmtTime(report.execution.startedAt) }}</div>
          </div>
        </div>
      </el-card>

      <!-- 步骤时间线 -->
      <el-card shadow="never" class="steps-card">
        <template #header><span style="font-weight:600">执行步骤</span></template>
        <div v-for="(step, idx) in report.steps" :key="step.id" class="step-item" @click="toggleStep(idx)">
          <div class="step-header">
            <el-icon :color="step.status === 'PASS' ? '#52c41a' : '#f5222d'" :size="16">
              <component :is="step.status === 'PASS' ? 'CircleCheck' : 'CircleClose'" />
            </el-icon>
            <span class="step-idx">{{ idx + 1 }}.</span>
            <el-tag :type="methodColor(step.method)" size="small" effect="dark" style="min-width:44px;text-align:center">{{ step.method }}</el-tag>
            <span class="step-api">{{ step.apiName }}</span>
            <span class="step-case">/ {{ step.caseName }}</span>
            <span class="step-url">{{ step.url }}</span>
            <span class="step-dur">{{ step.durationMs }}ms</span>
          </div>
          <div v-if="expandedStep === idx" class="step-detail">
            <div v-if="step.assertions?.length" class="detail-section">
              <div class="detail-label">断言结果</div>
              <div v-for="(a, ai) in step.assertions" :key="ai" class="assert-line">
                <el-icon :color="a.pass ? '#52c41a' : '#f5222d'" :size="14"><component :is="a.pass ? 'CircleCheck' : 'CircleClose'" /></el-icon>
                {{ a.type }}<template v-if="a.expression"> {{ a.expression }}</template> {{ a.operator }} {{ a.expected ?? '' }}
                <span v-if="!a.pass" class="actual-val">→ 实际: {{ a.actual }}</span>
              </div>
            </div>
            <div v-if="step.error" class="detail-section">
              <div class="detail-label">错误</div>
              <pre class="error-text">{{ step.error }}</pre>
            </div>
            <div class="detail-section">
              <div class="detail-label">响应 ({{ step.responseStatus }})</div>
              <pre class="resp-body">{{ formatJson(step.responseBody) }}</pre>
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
import { ArrowLeft, CircleCheck, CircleClose, Loading } from '@element-plus/icons-vue';
import { apiExecApi } from '../../api';

const route = useRoute();
const execId = String(route.params.id);
const loading = ref(true);
const report = ref<any>(null);
const expandedStep = ref<number | null>(null);
let pollTimer: ReturnType<typeof setInterval> | null = null;

const passPct = computed(() => {
  if (!report.value?.execution?.totalCases) return 0;
  return Math.round(report.value.execution.passedCases / report.value.execution.totalCases * 100);
});

const isRunning = computed(() => report.value?.execution?.status === 'RUNNING');

function fmtTime(t?: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }
function statusColor(s: string): any { return ({ PASS: 'success', FAIL: 'danger', RUNNING: 'warning', ERROR: 'danger' } as any)[s] || 'info'; }
function statusLabel(s: string) { return ({ PASS: '全部通过', FAIL: '有失败', RUNNING: '执行中', ERROR: '异常' } as any)[s] || s; }
function methodColor(m: string): any { return ({ GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger' } as any)[m] || 'info'; }
function formatJson(s: string) { if (!s) return ''; try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; } }
function toggleStep(idx: number) { expandedStep.value = expandedStep.value === idx ? null : idx; }

async function loadReport() {
  try {
    const res = await apiExecApi.report(execId);
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
.step-item { border: 1px solid #f0f0f0; border-radius: 6px; margin-bottom: 6px; cursor: pointer; transition: all .15s; }
.step-item:hover { border-color: #d9ecff; }
.step-header { display: flex; align-items: center; gap: 8px; padding: 10px 14px; font-size: 13px; }
.step-idx { font-weight: 600; color: #909399; }
.step-api { font-weight: 600; }
.step-case { color: #909399; }
.step-url { flex: 1; color: #c0c4cc; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.step-dur { color: #606266; font-size: 12px; white-space: nowrap; }
.step-detail { padding: 0 14px 14px; border-top: 1px solid #f5f5f5; }
.detail-section { margin-top: 10px; }
.detail-label { font-size: 12px; color: #909399; font-weight: 600; margin-bottom: 4px; }
.assert-line { display: flex; align-items: center; gap: 6px; font-size: 13px; margin-bottom: 2px; }
.actual-val { color: #f56c6c; font-size: 12px; }
.error-text { background: #fff2f0; color: #f5222d; padding: 8px; border-radius: 4px; font-size: 12px; white-space: pre-wrap; }
.resp-body { background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; font-size: 12px; max-height: 300px; overflow: auto; white-space: pre-wrap; word-break: break-all; font-family: 'Consolas', monospace; }
</style>
