<template>
  <div class="func-test-layout">
    <div class="func-test-tabs">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="用例管理" name="cases" />
        <el-tab-pane label="测试计划" name="plans" />
        <el-tab-pane label="回收站" name="recycle" />
      </el-tabs>
    </div>
    <div class="func-test-content">
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
  cases: '/func-test/cases',
  plans: '/func-test/plans',
  recycle: '/func-test/recycle',
};
const reverseMap: Record<string, string> = Object.fromEntries(
  Object.entries(tabMap).map(([k, v]) => [v, k])
);

const activeTab = ref(reverseMap[route.path] || 'cases');

watch(() => route.path, (p) => { activeTab.value = reverseMap[p] || 'cases'; });

function onTabChange(name: string) {
  const target = tabMap[name];
  if (target && route.path !== target) router.push(target);
}
</script>

<style scoped>
.func-test-layout { display: flex; flex-direction: column; height: 100%; background: #fff; border-radius: 8px; overflow: hidden; }
.func-test-tabs { padding: 0 20px; border-bottom: 1px solid #f0f0f0; background: #fafafa; }
.func-test-tabs :deep(.el-tabs__header) { margin: 0; }
.func-test-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }
.func-test-content { flex: 1; overflow: hidden; }
</style>
