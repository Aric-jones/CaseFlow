<template>
  <div class="def-detail" v-loading="loading" @click="onPageClick">
    <div class="detail-top">
      <el-button text @click="$router.push('/api-auto/defs')"><el-icon><ArrowLeft /></el-icon> 返回列表</el-button>
      <span class="def-name">{{ def?.name }}</span>
      <el-tag v-if="def?.method" :type="methodColor(def.method)" size="small" effect="dark" style="margin-left:8px">{{ def.method }}</el-tag>
    </div>

    <!-- 接口信息 + 默认请求参数 合并 -->
    <el-card shadow="never" class="section-card" v-if="def">
      <template #header><span class="card-title">接口信息 &amp; 默认参数</span></template>
      <el-form :model="def" label-width="95px">
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
        <el-form-item v-if="def.authType === 'BEARER_TOKEN'" label="Token">
          <el-input v-model="authToken" placeholder="{{token}} 支持变量" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="def.tags" multiple filterable allow-create default-first-option style="width:100%" />
        </el-form-item>
        <el-form-item label="描述"><el-input v-model="def.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>

      <el-divider content-position="left"><span class="hint-inline">默认请求参数（新建用例时自动继承）</span></el-divider>

      <el-tabs v-model="defParamTab" class="def-param-tabs">
        <el-tab-pane label="Headers" name="headers">
          <KvTable v-model="def.defaultHeaders" />
        </el-tab-pane>
        <el-tab-pane label="Query Params" name="params">
          <KvTable v-model="def.defaultParams" />
        </el-tab-pane>
        <el-tab-pane label="Body" name="body">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px">
            <el-radio-group v-model="def.defaultBodyType">
              <el-radio-button v-for="t in ['NONE','JSON','FORM','RAW','XML']" :key="t" :value="t">{{ t }}</el-radio-button>
            </el-radio-group>
            <el-button v-if="def.defaultBodyType === 'JSON'" size="small" @click="formatDefBody">格式化</el-button>
          </div>
          <CodeEditor v-if="def.defaultBodyType && def.defaultBodyType !== 'NONE'"
            v-model="def.defaultBody" :language="def.defaultBodyType === 'JSON' ? 'json' : 'javascript'"
            :min-height="140" placeholder="默认请求体" />
        </el-tab-pane>
      </el-tabs>

      <div style="text-align:right;margin-top:16px">
        <el-button type="primary" @click="saveDef">保存接口信息</el-button>
      </div>
    </el-card>

    <!-- 用例列表 -->
    <el-card shadow="never" class="section-card" @click.stop>
      <template #header>
        <div class="card-header-row">
          <span class="card-title">接口用例 ({{ cases.length }})</span>
          <div style="display:flex;gap:8px">
            <el-button type="success" size="small" @click="debugAllCases" :loading="debugging">调试全部</el-button>
            <el-button type="primary" size="small" @click="createCase">新建用例</el-button>
          </div>
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
    <el-card shadow="never" class="section-card" v-if="activeCase" @click.stop>
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
        <el-tab-pane label="Body" name="body">
          <div style="display:flex;align-items:center;gap:8px;margin-bottom:10px">
            <el-radio-group v-model="activeCase.bodyType">
              <el-radio-button v-for="t in ['NONE','JSON','FORM','RAW','XML']" :key="t" :value="t">{{ t }}</el-radio-button>
            </el-radio-group>
            <el-button v-if="activeCase.bodyType === 'JSON'" size="small" @click="formatBody">格式化</el-button>
          </div>
          <CodeEditor v-if="activeCase.bodyType && activeCase.bodyType !== 'NONE'"
            v-model="activeCase.bodyContent" :language="activeCase.bodyType === 'JSON' ? 'json' : 'javascript'"
            :min-height="180" placeholder="请求体内容" />
        </el-tab-pane>

        <el-tab-pane label="Headers" name="headers">
          <KvTable v-model="activeCase.headers" />
        </el-tab-pane>

        <el-tab-pane label="Params" name="params">
          <KvTable v-model="activeCase.queryParams" />
        </el-tab-pane>

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

        <!-- 前置脚本 -->
        <el-tab-pane label="前置脚本" name="preScript">
          <div class="script-toolbar">
            <el-radio-group v-model="preScriptMode" size="small">
              <el-radio-button value="extract">变量设置</el-radio-button>
              <el-radio-button value="groovy">Groovy 脚本</el-radio-button>
            </el-radio-group>
            <el-dropdown v-if="preScriptMode === 'groovy'" trigger="click" @command="insertPreTemplate">
              <el-button size="small" type="primary" text>插入模板 <el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="t in preTemplates" :key="t.label" :command="t.code">{{ t.label }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div v-if="preScriptMode === 'extract'">
            <div class="hint-text">设置请求头或变量（在发送请求前执行）</div>
            <div v-for="(a, idx) in preActions" :key="idx" class="extract-row">
              <el-select v-model="a.type" size="small" style="width:130px">
                <el-option value="SET_HEADER" label="设置请求头" />
                <el-option value="SET_VARIABLE" label="设置变量" />
              </el-select>
              <el-input v-model="a.key" size="small" placeholder="Key" style="width:140px" />
              <el-input v-model="a.value" size="small" placeholder="Value (支持 {{var}})" style="flex:1" />
              <el-button text type="danger" size="small" @click="preActions.splice(idx, 1)">删除</el-button>
            </div>
            <el-button text type="primary" @click="preActions.push({ type: 'SET_HEADER', key: '', value: '' })">+ 添加操作</el-button>
          </div>
          <div v-else>
            <div class="hint-text">Groovy 脚本，可用变量: vars(Map), headers(Map), log(Logger)</div>
            <CodeEditor v-model="preGroovyScript" language="groovy" :min-height="240"
              placeholder="// 在此编写 Groovy 前置脚本" />
          </div>
        </el-tab-pane>

        <!-- 后置脚本 -->
        <el-tab-pane label="后置脚本" name="postScript">
          <div class="script-toolbar">
            <el-radio-group v-model="postScriptMode" size="small">
              <el-radio-button value="extract">变量提取</el-radio-button>
              <el-radio-button value="groovy">Groovy 脚本</el-radio-button>
            </el-radio-group>
            <el-dropdown v-if="postScriptMode === 'groovy'" trigger="click" @command="insertPostTemplate">
              <el-button size="small" type="primary" text>插入模板 <el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="t in postTemplates" :key="t.label" :command="t.code">{{ t.label }}</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
          <div v-if="postScriptMode === 'extract'">
            <div class="hint-text">从响应中提取变量，供后续步骤使用</div>
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
          </div>
          <div v-else>
            <div class="hint-text">Groovy 脚本，可用变量: vars(Map), response(Map{body,statusCode,headers,durationMs}), log(Logger)</div>
            <CodeEditor v-model="postGroovyScript" language="groovy" :min-height="240"
              placeholder="// 在此编写 Groovy 后置脚本" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 调试响应区域 -->
    <el-card shadow="never" class="section-card" v-if="debugResult" @click.stop>
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
      <div v-if="debugResult.assertions?.length" style="margin-bottom:12px">
        <div v-for="(a, i) in debugResult.assertions" :key="i" class="assertion-result-row">
          <el-icon :color="a.pass ? '#52c41a' : '#f5222d'"><component :is="a.pass ? 'CircleCheck' : 'CircleClose'" /></el-icon>
          <span>{{ a.type }}<template v-if="a.expression"> {{ a.expression }}</template> {{ opLabel(a.operator) }} {{ a.expected ?? '' }}</span>
          <span v-if="!a.pass" class="actual-val">实际: {{ a.actual }}</span>
        </div>
      </div>
      <div v-if="debugResult.extractedVars && Object.keys(debugResult.extractedVars).length" style="margin-bottom:12px">
        <div class="resp-label">提取的变量</div>
        <div v-for="(val, key) in debugResult.extractedVars" :key="key" class="extract-result">
          <span class="var-key">{{ key }}</span> = <span class="var-val">{{ val }}</span>
        </div>
      </div>
      <div class="resp-label">Response Body</div>
      <pre class="resp-body">{{ formatJson(debugResult.responseBody) }}</pre>
    </el-card>

    <!-- 批量调试结果 -->
    <el-card shadow="never" class="section-card" v-if="batchDebugResults.length" @click.stop>
      <template #header>
        <div class="card-header-row">
          <span class="card-title">
            批量调试结果 ({{ batchDebugResults.filter(r => r.status === 'PASS').length }}/{{ batchDebugResults.length }} 通过)
          </span>
          <el-button text type="primary" size="small" @click="batchDebugResults = []">关闭</el-button>
        </div>
      </template>
      <div v-for="(r, i) in batchDebugResults" :key="i" class="batch-result-item">
        <div class="batch-result-header" @click="expandedBatchIdx = expandedBatchIdx === i ? null : i">
          <el-icon :color="r.status === 'PASS' ? '#52c41a' : '#f5222d'"><component :is="r.status === 'PASS' ? 'CircleCheck' : 'CircleClose'" /></el-icon>
          <span class="batch-case-name">{{ r.caseName }}</span>
          <el-tag :type="r.status === 'PASS' ? 'success' : 'danger'" size="small">{{ r.status }}</el-tag>
          <span v-if="r.durationMs" class="batch-dur">{{ r.durationMs }}ms</span>
        </div>
        <div v-if="expandedBatchIdx === i" class="batch-result-detail">
          <div v-if="r.assertions?.length">
            <div v-for="(a, ai) in r.assertions" :key="ai" class="assertion-result-row">
              <el-icon :color="a.pass ? '#52c41a' : '#f5222d'"><component :is="a.pass ? 'CircleCheck' : 'CircleClose'" /></el-icon>
              <span>{{ a.type }}{{ a.expression ? ' ' + a.expression : '' }} {{ opLabel(a.operator) }} {{ a.expected ?? '' }}</span>
              <span v-if="!a.pass" class="actual-val">实际: {{ a.actual }}</span>
            </div>
          </div>
          <pre v-if="r.responseBody" class="resp-body" style="margin-top:8px">{{ formatJson(r.responseBody) }}</pre>
        </div>
      </div>
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
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, ArrowDown, CircleCheck, CircleClose } from '@element-plus/icons-vue';
import { apiDefApi, apiCaseApi, apiEnvApi, apiExecApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { ApiDef, ApiCaseItem, ApiAssertionItem, ApiEnv } from '../../types';
import KvTable from '../../components/apiAuto/KvTable.vue';
import CodeEditor from '../../components/CodeEditor.vue';

const route = useRoute();
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
const defParamTab = ref('headers');
const caseAssertions = ref<ApiAssertionItem[]>([]);
const postExtracts = ref<any[]>([]);
const preActions = ref<any[]>([]);
const authToken = ref('');

// Groovy 脚本
const preScriptMode = ref<'extract' | 'groovy'>('extract');
const postScriptMode = ref<'extract' | 'groovy'>('extract');
const preGroovyScript = ref('');
const postGroovyScript = ref('');

const envDialog = ref(false);
const envList = ref<ApiEnv[]>([]);
const selectedEnvId = ref('');
const debugging = ref(false);
const debugResult = ref<any>(null);
const pendingDebugCaseId = ref('');
const expandedBatchIdx = ref<number | null>(null);

// Groovy 模板
const preTemplates = [
  { label: '设置变量', code: `vars.put('key', 'value')\n` },
  { label: '获取变量', code: `def val = vars.get('key')\nlog.info("val = " + val)\n` },
  { label: '生成时间戳', code: `vars.put('timestamp', String.valueOf(System.currentTimeMillis()))\n` },
  { label: '生成 UUID', code: `vars.put('uuid', UUID.randomUUID().toString())\n` },
  { label: '设置请求头', code: `headers.put('Authorization', 'Bearer ' + vars.get('token'))\n` },
  { label: 'Base64 编码', code: `vars.put('encoded', Base64.encoder.encodeToString('hello'.bytes))\n` },
  { label: 'MD5 签名', code: `import java.security.MessageDigest\ndef md5 = MessageDigest.getInstance('MD5')\ndef digest = md5.digest('text'.bytes)\nvars.put('md5', digest.collect { String.format('%02x', it) }.join())\n` },
  { label: 'HMAC-SHA256', code: `import javax.crypto.Mac\nimport javax.crypto.spec.SecretKeySpec\ndef mac = Mac.getInstance('HmacSHA256')\nmac.init(new SecretKeySpec('secret'.bytes, 'HmacSHA256'))\ndef sig = mac.doFinal('data'.bytes).collect { String.format('%02x', it) }.join()\nvars.put('signature', sig)\n` },
  { label: '生成随机数', code: `vars.put('random', String.valueOf(new Random().nextInt(10000)))\n` },
];

const postTemplates = [
  { label: '提取 JSONPath 值', code: `def json = new groovy.json.JsonSlurper().parseText(response.body)\nvars.put('token', json.data?.token?.toString() ?: '')\n` },
  { label: '提取响应头', code: `vars.put('cookie', response.headers.get('Set-Cookie') ?: '')\n` },
  { label: '断言状态码', code: `assert response.statusCode == 200 : "Expected 200, got \${response.statusCode}"\n` },
  { label: '断言包含内容', code: `assert response.body.contains('success') : 'Response does not contain success'\n` },
  { label: '打印响应', code: `log.info("Status: " + response.statusCode)\nlog.info("Body: " + response.body)\n` },
  { label: '解析 JSON 并提取', code: `def json = new groovy.json.JsonSlurper().parseText(response.body)\nvars.put('id', json.data?.id?.toString() ?: '')\nvars.put('name', json.data?.name?.toString() ?: '')\n` },
  { label: '断言响应时间', code: `assert response.durationMs < 3000 : "Response too slow: \${response.durationMs}ms"\n` },
  { label: '条件提取', code: `def json = new groovy.json.JsonSlurper().parseText(response.body)\nif (json.code == 200 && json.data?.list) {\n  vars.put('firstId', json.data.list[0]?.id?.toString() ?: '')\n}\n` },
];

function methodColor(m: string): any {
  return ({ GET: 'success', POST: 'warning', PUT: '', DELETE: 'danger', PATCH: 'info' } as any)[m] || '';
}
function priorityColor(p?: string): any {
  return ({ P0: 'danger', P1: 'warning', P2: '', P3: 'info' } as any)[p || 'P1'] || '';
}
function opLabel(op: string) { return operators.find(o => o.v === op)?.l || op; }
function formatJson(s: string) {
  if (!s) return '';
  try { return JSON.stringify(JSON.parse(s), null, 2); } catch { return s; }
}

function onPageClick() {
  activeCase.value = null;
  debugResult.value = null;
}

function insertPreTemplate(code: string) {
  preGroovyScript.value += code;
}
function insertPostTemplate(code: string) {
  postGroovyScript.value += code;
}

async function loadDef() {
  loading.value = true;
  try {
    const res = await apiDefApi.detail(defId);
    def.value = res.data;
    if (def.value?.authType === 'BEARER_TOKEN' && def.value.authConfig) {
      authToken.value = (def.value.authConfig as any).token || '';
    }
    if (!def.value.defaultHeaders) def.value.defaultHeaders = [];
    if (!def.value.defaultParams) def.value.defaultParams = [];
    if (!def.value.defaultBodyType) def.value.defaultBodyType = 'NONE';
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
  ElMessage.success('已创建（默认参数已从接口定义继承）');
  await loadCases();
  selectCase(cases.value.find(c => c.id === res.data.id) || cases.value[cases.value.length - 1]);
}

async function selectCase(c: ApiCaseItem) {
  if (activeCase.value?.id === c.id) {
    activeCase.value = null;
    debugResult.value = null;
    return;
  }
  const res = await apiCaseApi.detail(c.id);
  activeCase.value = res.data;
  caseAssertions.value = res.data.assertions || [];
  postExtracts.value = res.data.postScript?.extracts || [];
  preActions.value = res.data.preScript?.actions || [];

  preScriptMode.value = res.data.preScriptType === 'GROOVY' ? 'groovy' : 'extract';
  postScriptMode.value = res.data.postScriptType === 'GROOVY' ? 'groovy' : 'extract';
  preGroovyScript.value = res.data.preScriptContent || '';
  postGroovyScript.value = res.data.postScriptContent || '';

  caseTab.value = 'body';
  debugResult.value = null;
}

async function saveCase() {
  if (!activeCase.value) return;
  if (preScriptMode.value === 'groovy') {
    activeCase.value.preScriptType = 'GROOVY';
    activeCase.value.preScriptContent = preGroovyScript.value;
    activeCase.value.preScript = undefined;
  } else {
    activeCase.value.preScriptType = 'JSON';
    activeCase.value.preScriptContent = undefined;
    activeCase.value.preScript = preActions.value.length ? { actions: preActions.value } : undefined;
  }
  if (postScriptMode.value === 'groovy') {
    activeCase.value.postScriptType = 'GROOVY';
    activeCase.value.postScriptContent = postGroovyScript.value;
    activeCase.value.postScript = undefined;
  } else {
    activeCase.value.postScriptType = 'JSON';
    activeCase.value.postScriptContent = undefined;
    activeCase.value.postScript = postExtracts.value.length ? { extracts: postExtracts.value } : undefined;
  }
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

function formatDefBody() {
  if (!def.value?.defaultBody) return;
  try {
    def.value.defaultBody = JSON.stringify(JSON.parse(def.value.defaultBody), null, 2);
  } catch { ElMessage.warning('JSON 格式错误，无法格式化'); }
}

function formatBody() {
  if (!activeCase.value?.bodyContent) return;
  try {
    activeCase.value.bodyContent = JSON.stringify(JSON.parse(activeCase.value.bodyContent), null, 2);
  } catch { ElMessage.warning('JSON 格式错误，无法格式化'); }
}

const debugMode = ref<'single' | 'all'>('single');

function debugCase(c: ApiCaseItem) {
  debugMode.value = 'single';
  pendingDebugCaseId.value = c.id;
  envDialog.value = true;
}

function debugAllCases() {
  if (!cases.value.length) { ElMessage.warning('暂无用例'); return; }
  debugMode.value = 'all';
  envDialog.value = true;
}

const batchDebugResults = ref<any[]>([]);

async function doDebug() {
  if (!selectedEnvId.value) { ElMessage.warning('请选择环境'); return; }
  debugging.value = true;
  envDialog.value = false;
  debugResult.value = null;
  batchDebugResults.value = [];
  try {
    if (debugMode.value === 'single') {
      const res = await apiExecApi.debug(pendingDebugCaseId.value, selectedEnvId.value);
      debugResult.value = res.data;
    } else {
      const enabledCases = cases.value.filter(c => c.enabled !== 0);
      for (const c of enabledCases) {
        try {
          const res = await apiExecApi.debug(c.id, selectedEnvId.value);
          batchDebugResults.value.push({ caseName: c.name, caseId: c.id, ...res.data });
        } catch (e: any) {
          batchDebugResults.value.push({ caseName: c.name, caseId: c.id, status: 'ERROR', error: e.message });
        }
      }
    }
  } catch { ElMessage.error('调试请求失败'); }
  finally { debugging.value = false; }
}

onMounted(() => { loadDef(); loadCases(); loadEnvs(); });
</script>

<style scoped>
.def-detail { padding: 16px 24px; max-width: 1100px; margin: 0 auto; height: 100%; overflow: auto; }
.detail-top { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.def-name { font-size: 18px; font-weight: 600; }
.section-card { margin-bottom: 16px; }
.card-title { font-weight: 600; font-size: 14px; }
.hint-inline { font-weight: 400; font-size: 12px; color: #909399; }
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
.script-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }
.code-editor :deep(textarea) { font-family: 'Consolas', 'Monaco', 'Courier New', monospace !important; font-size: 13px !important; line-height: 1.6 !important; }
.extract-result { font-size: 13px; margin-bottom: 2px; }
.var-key { color: #409eff; font-weight: 500; font-family: monospace; }
.var-val { color: #67c23a; font-family: monospace; }
.def-param-tabs { margin-top: 4px; }
.batch-result-item { border: 1px solid #f0f0f0; border-radius: 6px; margin-bottom: 6px; overflow: hidden; }
.batch-result-header { display: flex; align-items: center; gap: 8px; padding: 10px 14px; cursor: pointer; font-size: 13px; transition: background .15s; }
.batch-result-header:hover { background: #f5f9ff; }
.batch-case-name { flex: 1; font-weight: 500; }
.batch-dur { color: #909399; font-size: 12px; }
.batch-result-detail { padding: 0 14px 14px; border-top: 1px solid #f5f5f5; }
</style>
