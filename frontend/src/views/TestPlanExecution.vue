<template>
  <a-layout style="height: 100vh">
    <div class="exec-header">
      <a-space><a-button type="text" @click="$router.push('/test-plans')"><ArrowLeftOutlined /></a-button><strong>{{ plan?.name }}</strong></a-space>
      <a-space>
        <a-tag>总: {{ stats.total }}</a-tag><a-tag color="success">通过: {{ stats.pass }}</a-tag>
        <a-tag color="error">不通过: {{ stats.fail }}</a-tag><a-tag color="warning">跳过: {{ stats.skip }}</a-tag>
        <a-tag>待执行: {{ stats.pending }}</a-tag>
      </a-space>
    </div>
    <a-layout style="flex: 1; overflow: hidden">
      <a-layout-sider width="320" style="background:#fff;border-right:1px solid #f0f0f0;padding:12px;overflow:auto" :trigger="null">
        <a-select v-model:value="filterResult" placeholder="筛选" allow-clear style="width: 100%; margin-bottom: 8px"
          :options="[{value:'PENDING',label:'待执行'},{value:'PASS',label:'通过'},{value:'FAIL',label:'不通过'},{value:'SKIP',label:'跳过'}]" />
        <a-list size="small" :data-source="filteredCases">
          <template #renderItem="{ item }">
            <a-list-item :style="{ cursor: 'pointer', background: selectedCase?.id === item.id ? '#e6f4ff' : '' }" @click="selectCase(item)">
              <a-tag :color="resultColor(item.result)" style="font-size:11px">{{ resultLabel(item.result) }}</a-tag>
              用例#{{ item.nodeId }}
            </a-list-item>
          </template>
        </a-list>
      </a-layout-sider>
      <a-layout-content style="padding: 24px; overflow: auto; background: #fafafa">
        <a-card v-if="selectedCase">
          <h4>用例详情</h4>
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="用例ID">#{{ selectedCase.nodeId }}</a-descriptions-item>
            <a-descriptions-item label="当前结果"><a-tag :color="resultColor(selectedCase.result)">{{ resultLabel(selectedCase.result) }}</a-tag></a-descriptions-item>
            <a-descriptions-item v-if="selectedCase.reason" label="原因">{{ selectedCase.reason }}</a-descriptions-item>
          </a-descriptions>
          <a-divider />
          <h4>执行操作</h4>
          <a-textarea v-model:value="reason" placeholder="不通过/跳过请填写原因" :auto-size="{ minRows: 3 }" style="margin-bottom: 12px" />
          <a-space>
            <a-button style="background:#52c41a;border-color:#52c41a;color:#fff" @click="execute('PASS')">通过</a-button>
            <a-button danger @click="execute('FAIL')">不通过</a-button>
            <a-button style="background:#faad14;border-color:#faad14;color:#fff" @click="execute('SKIP')">跳过</a-button>
            <a-button type="text" danger @click="removeCase">移除</a-button>
          </a-space>
        </a-card>
        <a-empty v-else description="请选择用例" />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import { ArrowLeftOutlined } from '@ant-design/icons-vue';
import { testPlanApi } from '../api';
import type { TestPlan, TestPlanCase } from '../types';

const route = useRoute();
const planId = String(route.params.planId);
const plan = ref<TestPlan | null>(null);
const cases = ref<TestPlanCase[]>([]);
const selectedCase = ref<TestPlanCase | null>(null);
const reason = ref('');
const filterResult = ref<string | undefined>();

const filteredCases = computed(() => filterResult.value ? cases.value.filter(c => c.result === filterResult.value) : cases.value);
const stats = computed(() => ({
  total: cases.value.length, pass: cases.value.filter(c => c.result === 'PASS').length,
  fail: cases.value.filter(c => c.result === 'FAIL').length, skip: cases.value.filter(c => c.result === 'SKIP').length,
  pending: cases.value.filter(c => c.result === 'PENDING').length,
}));

function resultLabel(r: string) { return r === 'PASS' ? '通过' : r === 'FAIL' ? '不通过' : r === 'SKIP' ? '跳过' : '待执行'; }
function resultColor(r: string) { return r === 'PASS' ? 'success' : r === 'FAIL' ? 'error' : r === 'SKIP' ? 'warning' : 'default'; }

function selectCase(c: TestPlanCase) { selectedCase.value = c; reason.value = ''; }

async function loadData() {
  const [pRes, cRes] = await Promise.all([testPlanApi.get(planId), testPlanApi.getCases(planId)]);
  plan.value = pRes.data; cases.value = cRes.data;
  if (!selectedCase.value && cases.value.length) selectedCase.value = cases.value[0];
}
onMounted(loadData);

async function execute(result: string) {
  if (!selectedCase.value) return;
  if ((result === 'FAIL' || result === 'SKIP') && !reason.value.trim()) { message.error('请填写原因'); return; }
  await testPlanApi.executeCase(selectedCase.value.id, result, reason.value || undefined);
  message.success('已记录'); reason.value = '';
  cases.value = (await testPlanApi.getCases(planId)).data;
  selectedCase.value = cases.value.find(c => c.id === selectedCase.value!.id) || null;
}
async function removeCase() {
  if (!selectedCase.value) return;
  await testPlanApi.removeCase(selectedCase.value.id);
  cases.value = (await testPlanApi.getCases(planId)).data;
  selectedCase.value = cases.value[0] || null;
}
</script>

<style scoped>
.exec-header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #f0f0f0; padding: 8px 16px; }
</style>
