<template>
  <div class="api-auto-layout">
    <div class="api-auto-tabs">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="接口管理" name="defs" />
        <el-tab-pane label="测试场景" name="scenarios" />
        <el-tab-pane label="测试计划" name="plans" />
        <el-tab-pane label="执行记录" name="executions" />
        <el-tab-pane label="环境管理" name="env" />
        <el-tab-pane label="回收站" name="recycle" />
      </el-tabs>
    </div>
    <div class="api-auto-content">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();

const tabMap: Record<string, string> = { defs: '/api-auto/defs', scenarios: '/api-auto/scenarios', plans: '/api-auto/plans', executions: '/api-auto/executions', env: '/api-auto/env', recycle: '/api-auto/recycle' };
const reverseMap: Record<string, string> = Object.fromEntries(Object.entries(tabMap).map(([k, v]) => [v, k]));

const activeTab = ref(reverseMap[route.path] || 'defs');

watch(() => route.path, (p) => { activeTab.value = reverseMap[p] || 'defs'; });

function onTabChange(name: string) {
  const target = tabMap[name];
  if (target && route.path !== target) router.push(target);
}
</script>

<style scoped>
.api-auto-layout { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 8px; overflow: hidden; }
.api-auto-tabs { padding: 0 20px; border-bottom: 1px solid #f0f0f0; background: #fafafa; }
.api-auto-tabs :deep(.el-tabs__header) { margin: 0; }
.api-auto-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.api-auto-content { flex: 1; overflow: hidden; }
</style>
