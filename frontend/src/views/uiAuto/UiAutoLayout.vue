<template>
  <div class="ui-auto-layout">
    <div class="ui-auto-tabs">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="页面对象" name="pages" />
        <el-tab-pane label="测试用例" name="cases" />
        <el-tab-pane label="测试场景" name="scenarios" />
        <el-tab-pane label="测试计划" name="plans" />
        <el-tab-pane label="执行记录" name="executions" />
        <el-tab-pane label="环境管理" name="env" />
        <el-tab-pane label="回收站" name="recycle" />
      </el-tabs>
    </div>
    <div class="ui-auto-content">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';

const router = useRouter();
const route = useRoute();

const tabMap: Record<string, string> = {
  pages: '/ui-auto/pages', cases: '/ui-auto/cases', scenarios: '/ui-auto/scenarios',
  plans: '/ui-auto/plans', executions: '/ui-auto/executions', env: '/ui-auto/env', recycle: '/ui-auto/recycle',
};
const reverseMap: Record<string, string> = Object.fromEntries(Object.entries(tabMap).map(([k, v]) => [v, k]));

function pathToTab(p: string): string {
  if (p.startsWith('/ui-auto/pages') || p.startsWith('/ui-auto/page/')) return 'pages';
  if (p.startsWith('/ui-auto/cases') || p.startsWith('/ui-auto/case/')) return 'cases';
  if (p.startsWith('/ui-auto/scenarios') || p.startsWith('/ui-auto/scenario/')) return 'scenarios';
  if (p.startsWith('/ui-auto/plans') || p.startsWith('/ui-auto/plan/')) return 'plans';
  if (p.startsWith('/ui-auto/executions') || p.startsWith('/ui-auto/execution/')) return 'executions';
  if (p.startsWith('/ui-auto/env')) return 'env';
  if (p.startsWith('/ui-auto/recycle')) return 'recycle';
  return reverseMap[p] || 'pages';
}

const activeTab = ref(pathToTab(route.path));

watch(() => route.path, (p) => { activeTab.value = pathToTab(p); });

function onTabChange(name: string) {
  const target = tabMap[name];
  if (target && route.path !== target) router.push(target);
}
</script>

<style scoped>
.ui-auto-layout { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 8px; overflow: hidden; }
.ui-auto-tabs { padding: 0 20px; border-bottom: 1px solid #f0f0f0; background: #fafafa; }
.ui-auto-tabs :deep(.el-tabs__header) { margin: 0; }
.ui-auto-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.ui-auto-content { flex: 1; overflow: hidden; }
</style>
