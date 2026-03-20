<template>
  <div class="def-detail" v-loading="loading">
    <div class="detail-top">
      <el-button text @click="$router.push('/api-auto/defs')"><el-icon><ArrowLeft /></el-icon> 返回列表</el-button>
      <span class="def-name">{{ def?.name }}</span>
    </div>

    <!-- 接口基本信息 -->
    <el-card shadow="never" class="section-card">
      <template #header><span class="card-title">接口信息</span></template>
      <el-form :model="def" label-width="95px" v-if="def">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="接口名称"><el-input v-model="def.name" /></el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="请求方法">
              <el-select v-model="def.method" style="width:100%">
                <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="鉴权方式">
              <el-select v-model="def.authType" style="width:100%">
                <el-option value="NONE" label="无需鉴权" />
                <el-option value="BEARER_TOKEN" label="Bearer Token" />
                <el-option value="BASIC" label="Basic Auth" />
                <el-option value="API_KEY" label="API Key" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="请求路径"><el-input v-model="def.path" placeholder="/api/xxx" /></el-form-item>
        <!-- Bearer Token 配置 -->
        <el-form-item v-if="def.authType === 'BEARER_TOKEN'" label="Token">
          <el-input v-model="authToken" placeholder="{{token}} 支持变量" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="def.tags" multiple filterable allow-create default-first-option style="width:100%" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="def.description" type="textarea" :rows="2" /></el-form-item>
        <el-form-item><el-button type="primary" @click="saveDef">保存接口信息</el-button></el-form-item>
      </el-form>
    </el-card>

    <!-- 用例列表 -->
    <el-card shadow="never" class="section-card">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">接口用例 ({{ cases.length }})</span>
          <el-button type="primary" size="small" @click="createCase">新建用例</el-button>
        </div>
      </template>
      <div v-if="!cases.length" class="empty-hint">暂无用例，点击上方按钮创建</div>
      <div v-for="c in cases" :key="c.id" :class="['case-item', { active: activeCase?.id === c.id }]" @click="selectCase(c)">
        <div class="case-row">
          <el-tag :type="priorityColor(c.priority)" size="small" effect="dark" style="min-width:28px;text-align:center">{{ c.priority }}</el-tag>
          <span class="case-name">{{ c.name }}</span>
          <el-tag v-if="c.enabled === 0" type="info" size="small">已禁用</el-tag>
          <div class="case-actions">
            <el-button text type="primary" size="small" @click.stop="debugCase(c)">调试</el-button>
            <el-button text size="small" @click.stop="copyCase(c.id)">复制</el-button>
            <el-popconfirm title="确认删除？" @confirm.stop="deleteCase(c.id)">
              <template #reference><el-button text type="danger" size="small" @click.stop>删除</el-button></template>
            </el-popconfirm>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 用例编辑区 -->
    <el-card shadow="never" class="section-card" v-if="activeCase">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">编辑用例: {{ activeCase.name }}</span>
          <el-button type="primary" size="small" @click="saveCase">保存用例</el-button>
        </div>
      </template>
      <el-form :model="activeCase" label-width="90px">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="用例名称"><el-input v-model="activeCase.name" /></el-form-item></el-col>
          <el-col :span="6">
            <el-form-item label="优先级">
              <el-select v-model="activeCase.priority" style="width:100%">
                <el-option v-for="p in ['P0','P1','P2','P3']" :key="p" :label="p" :value="p" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="6"><el-form-item label="启用"><el-switch v-model="activeCase.enabled" :active-value="1" :inactive-value="0" /></el-form-item></el-col>
        </el-row>
      </el-form>

      <el-tabs v-model="caseTab" class="case-tabs">
        <!-- Body -->
        <el-tab-pane label="Body" name="body">
          <el-radio-group v-model="activeCase.bodyType" style="margin-bottom:10px">
            <el-radio-button v-for="t in ['NONE','JSON','FORM','RAW','XML']" :key="t" :value="t">{{ t }}</el-radio-button>
          </el-radio-group>
          <el-input v-if="activeCase.bodyType && activeCase.bodyType !== 'NONE'"
            v-model="activeCase.bodyContent" type="textarea" :rows="8" placeholder="请求体内容" style="font-family:monospace;font-size:13px" />
        </el-tab-pane>

        <!-- Headers -->
        <el-tab-pane label="Headers" name="headers">
          <KvTable v-model="activeCase.headers" />
        </el-tab-pane>

        <!-- Params -->
        <el-tab-pane label="Params" name="params">
          <KvTable v-model="activeCase.queryParams" />
        </el-tab-pane>

        <!-- 断言 -->
        <el-tab-pane label="断言" name="assertions">
          <div v-for="(a, idx) in caseAssertions" :key="idx" class="assertion-row">
            <el-select v-model="a.type" size="small" style="width:130px" placeholder="类型">
              <el-option value="STATUS_CODE" label="状态码" />
              <el-option value="JSON_PATH" label="JSONPath" />
              <el-option value="HEADER" label="响应头" />
              <el-option value="BODY_CONTAINS" label="Body包含" />
              <el-option value="RESPONSE_TIME" label="响应时间" />
            </el-select>
            <el-input v-model="a.expression" size="small" placeholder="表达式 (如 $.data.id)" style="width:180px"
              v-if="a.type !== 'STATUS_CODE' && a.type !== 'RESPONSE_TIME' && a.type !== 'BODY_CONTAINS'" />
            <el-select v-model="a.operator" size="small" style="width:110px" placeholder="操作符">
              <el-option v-for="o in operators" :key="o.v" :value="o.v" :label="o.l" />
            </el-select>
            <el-input v-model="a.expectedValue" size="small" placeholder="期望值" style="flex:1"
              v-if="a.operator !== 'EXISTS' && a.operator !== 'NOT_EXISTS' && a.operator !== 'IS_EMPTY' && a.operator !== 'IS_NOT_EMPTY'" />
            <el-button text type="danger" size="small" @click="caseAssertions.splice(idx, 1)">删除</el-button>
          </div>
          <el-button text type="primary" @click="caseAssertions.push({ type: 'STATUS_CODE', operator: 'EQUALS', expectedValue: '200' })">+ 添加断言</el-button>
        </el-tab-pane>

        <!-- 前后置脚本 -->
        <el-tab-pane label="后置提取" name="post">
          <div class="hint-text">从响应中提取变量，供后续步骤使用（格式：变量名 → JSONPath 表达式）</div>
          <div v-for="(e, idx) in postExtracts" :key="idx" class="extract-row">
            <el-input v-model="e.key" size="small" placeholder="变量名" style="width:140px" />
            <el-select v-model="e.source" size="small" style="width:120px">
              <el-option value="JSON_PATH" label="JSONPath" />
              <el-option value="HEADER" label="响应头" />
              <el-option value="STATUS_CODE" label="状态码" />
            </el-select>
            <el-input v-model="e.expression" size="small" placeholder="$.data.token" style="flex:1" />
            <el-button text type="danger" size="small" @click="postExtracts.splice(idx, 1)">删除</el-button>
          </div>
          <el-button text type="primary" @click="postExtracts.push({ key: '', source: 'JSON_PATH', expression: '' })">+ 添加提取</el-button>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 调试响应区域 -->
    <el-card shadow="never" class="section-card" v-if="debugResult">
      <template #header>
        <div class="card-header-row">
          <span class="card-title">
            调试结果
            <el-tag :type="debugResult.status === 'PASS' ? 'success' : (debugResult.status === 'FAIL' ? 'danger' : 'warning')"
              size="small" style="margin-left:8px">{{ debugResult.status }}</el-tag>
          </span>
          <span class="debug-meta">{{ debugResult.responseStatus }} | {{ debugResult.durationMs }}ms</span>
        </div>
      </template>
      <!-- 断言结果 -->
      <div v-if="debugResult.assertions?.length" style="margin-bottom:12px">
        <div v-for="(a, i) in debugResult.assertions" :key="i" class="assertion-result-row">
          <el-icon :color="a.pass ? '#52c41a' : '#f5222d'"><component :is="a.pass ? 'CircleCheck' : 'CircleClose'" /></el-icon>
          <span>{{ a.type }}<template v-if="a.expression"> {{ a.expression }}</template> {{ opLabel(a.operator) }} {{ a.expected ?? '' }}</span>
          <span v-if="!a.pass" class="actual-val">实际: {{ a.actual }}</span>
        </div>
      </div>
      <!-- 响应体 -->
      <div class="resp-label">Response Body</div>
      <pre class="resp-body">{{ formatJson(debugResult.responseBody) }}</pre>
    </el-card>

    <!-- 选择环境弹窗 -->
    <el-dialog v-model="envDialog" title="选择执行环境" width="400px">
      <el-select v-model="selectedEnvId" style="width:100%" placeholder="选择环境">
        <el-option v-for="e in envList" :key="e.id" :label="e.name + ' (' + e.baseUrl + ')'" :value="e.id" />
      </el-select>
      <template #footer>
        <el-button @click="envDialog = false">取消</el-button>
        <el-button type="primary" :loading="debugging" @click="doDebug">发送请求</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, CircleCheck, CircleClose } from '@element-plus/icons-vue';
import { apiDefApi, apiCaseApi, apiEnvApi, apiExecApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { ApiDef, ApiCaseItem, ApiAssertionItem, ApiEnv } from '../../types';
import KvTable from '../../components/apiAuto/KvTable.vue';

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const defId = String(route.params.id);
const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'];
const operators = [
  { v: 'EQUALS', l: '等于' }, { v: 'NOT_EQUALS', l: '不等于' }, { v: 'CONTAINS', l: '包含' },
  { v: 'NOT_CONTAINS', l: '不包含' }, { v: 'GT', l: '大于' }, { v: 'LT', l: '小于' },
  { v: 'EXISTS', l: '存在' }, { v: 'NOT_EXISTS', l: '不存在' }, { v: 'REGEX', l: '正则' },
  { v: 'IS_EMPTY', l: '为空' }, { v: 'IS_NOT_EMPTY', l: '非空' },
];

const loading = ref(true);
const def = ref<ApiDef | null>(null);
const cases = ref<ApiCaseItem[]>([]);
const activeCase = ref<ApiCaseItem | null>(null);
const caseTab = ref('body');
const caseAssertions = ref<ApiAssertionItem[]>([]);
const postExtracts = ref<any[]>([]);
const authToken = ref('');

const envDialog = ref(false);
const envList = ref<ApiEnv[]>([]);
const selectedEnvId = ref('');
const debugging = ref(false);
const debugResult = ref<any>(null);
const pendingDebugCaseId = ref('');

function priorityColor(p?: string): any {
  return ({ P0: 'danger', P1: 'warning', P2: '', P3: 'info' } as any)[p || 'P1'] || '';
}
function opLabel(op: string) { return operators.find(o => o.v === op)?.l || op; }
function formatJson(s: string) {
  if (!s) return '';
  try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; }
}

async function loadDef() {
  loading.value = true;
  try {
    const res = await apiDefApi.detail(defId);
    def.value = res.data;
    if (def.value?.authType === 'BEARER_TOKEN' && def.value.authConfig) {
      authToken.value = (def.value.authConfig as any).token || '';
    }
  } finally { loading.value = false; }
}

async function loadCases() {
  const res = await apiCaseApi.list(defId);
  cases.value = res.data;
}

async function loadEnvs() {
  if (!store.currentProject) return;
  const res = await apiEnvApi.list(store.currentProject.id);
  envList.value = res.data;
  if (envList.value.length && !selectedEnvId.value) selectedEnvId.value = envList.value[0].id;
}

async function saveDef() {
  if (!def.value) return;
  if (def.value.authType === 'BEARER_TOKEN') {
    def.value.authConfig = { token: authToken.value };
  }
  await apiDefApi.update(defId, def.value);
  ElMessage.success('接口信息已保存');
}

async function createCase() {
  const res = await apiCaseApi.create({ apiId: defId, name: '新用例', priority: 'P1', enabled: 1, bodyType: 'JSON' });
  ElMessage.success('已创建');
  await loadCases();
  selectCase(cases.value.find(c => c.id === res.data.id) || cases.value[cases.value.length - 1]);
}

async function selectCase(c: ApiCaseItem) {
  const res = await apiCaseApi.detail(c.id);
  activeCase.value = res.data;
  caseAssertions.value = res.data.assertions || [];
  postExtracts.value = res.data.postScript?.extracts || [];
  caseTab.value = 'body';
  debugResult.value = null;
}

async function saveCase() {
  if (!activeCase.value) return;
  activeCase.value.postScript = postExtracts.value.length ? { extracts: postExtracts.value } : null;
  await apiCaseApi.update(activeCase.value.id, activeCase.value);
  await apiCaseApi.saveAssertions(activeCase.value.id, caseAssertions.value);
  ElMessage.success('用例已保存');
  loadCases();
}

async function deleteCase(id: string) {
  try { await apiCaseApi.delete(id); ElMessage.success('已删除'); loadCases(); if (activeCase.value?.id === id) activeCase.value = null; } catch {}
}

async function copyCase(id: string) {
  await apiCaseApi.copy(id);
  ElMessage.success('已复制');
  loadCases();
}

function debugCase(c: ApiCaseItem) {
  pendingDebugCaseId.value = c.id;
  envDialog.value = true;
}

async function doDebug() {
  if (!selectedEnvId.value) { ElMessage.warning('请选择环境'); return; }
  debugging.value = true;
  envDialog.value = false;
  debugResult.value = null;
  try {
    const res = await apiExecApi.debug(pendingDebugCaseId.value, selectedEnvId.value);
    debugResult.value = res.data;
  } catch { ElMessage.error('调试请求失败'); }
  finally { debugging.value = false; }
}

onMounted(() => { loadDef(); loadCases(); loadEnvs(); });
</script>

<style scoped>
.def-detail { padding: 16px 24px; max-width: 1100px; margin: 0 auto; }
.detail-top { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.def-name { font-size: 18px; font-weight: 600; }
.section-card { margin-bottom: 16px; }
.card-title { font-weight: 600; font-size: 14px; }
.card-header-row { display: flex; align-items: center; justify-content: space-between; }
.empty-hint { color: #c0c4cc; text-align: center; padding: 32px 0; }
.case-item { padding: 10px 14px; border: 1px solid #f0f0f0; border-radius: 6px; margin-bottom: 6px; cursor: pointer; transition: all .15s; }
.case-item:hover { border-color: #c6e2ff; background: #f5f9ff; }
.case-item.active { border-color: #409eff; background: #ecf5ff; }
.case-row { display: flex; align-items: center; gap: 8px; }
.case-name { flex: 1; font-size: 13px; font-weight: 500; }
.case-actions { display: flex; gap: 2px; }
.case-tabs { margin-top: 8px; }
.assertion-row, .extract-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
.assertion-result-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; font-size: 13px; }
.actual-val { color: #f56c6c; font-size: 12px; }
.hint-text { font-size: 12px; color: #909399; margin-bottom: 10px; }
.debug-meta { font-size: 13px; color: #606266; }
.resp-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.resp-body { background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; font-size: 12px; max-height: 400px; overflow: auto; white-space: pre-wrap; word-break: break-all; font-family: 'Consolas', monospace; }
</style>
