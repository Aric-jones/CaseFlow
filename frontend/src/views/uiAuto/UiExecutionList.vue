<template>
  <div class="page-wrap-single">
    <div class="content-card">
      <div class="toolbar">
        <div style="flex:1" />
      </div>
      <el-table :data="list" v-loading="loading" border style="width:100%" @row-click="onRowClick">
        <el-table-column label="来源" min-width="180" show-overflow-tooltip fixed="left">
          <template #default="{ row }">
            <span v-if="row.planName"><el-tag size="small" type="danger" effect="plain" style="margin-right:4px">计划</el-tag>{{ row.planName }}</span>
            <span v-else-if="row.scenarioName"><el-tag size="small" type="warning" effect="plain" style="margin-right:4px">场景</el-tag>{{ row.scenarioName }}</span>
            <span v-else-if="row.caseName"><el-tag size="small" effect="plain" style="margin-right:4px">用例</el-tag>{{ row.caseName }}</span>
            <span v-else style="color:#909399">{{ triggerLabel(row.triggerType) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" effect="dark" :style="statusStyle(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="browserType" label="浏览器" width="100" align="center" />
        <el-table-column label="总步骤" width="90" align="center">
          <template #default="{ row }">{{ row.totalSteps ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="通过" width="80" align="center">
          <template #default="{ row }"><span style="color:#52c41a">{{ row.passedSteps ?? 0 }}</span></template>
        </el-table-column>
        <el-table-column label="失败" width="80" align="center">
          <template #default="{ row }"><span style="color:#f5222d">{{ row.failedSteps ?? 0 }}</span></template>
        </el-table-column>
        <el-table-column label="耗时" min-width="70">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column prop="executedByName" label="执行人" min-width="90" show-overflow-tooltip />
        <el-table-column label="开始时间" min-width="120">
          <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click.stop="$router.push('/ui-auto/execution/' + row.id)">查看报告</el-button>
            <el-popconfirm title="确认删除？" @confirm="doDelete(row.id)">
              <template #reference><el-button text type="danger" size="small" @click.stop>删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination layout="total, prev, pager, next" :total="total" :page-size="20" :current-page="currentPage" @current-change="loadList" background />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { uiExecApi } from '../../api';
import { useAppStore } from '../../stores/app';

const router = useRouter();
const store = useAppStore();
const list = ref<any[]>([]);
const total = ref(0);
const currentPage = ref(1);
const loading = ref(false);

function fmtTime(t?: string) { return t ? t.replace('T', ' ').substring(0, 16) : ''; }

function formatDuration(ms?: number) {
  if (ms == null) return '—';
  if (ms < 1000) return ms + ' ms';
  return (ms / 1000).toFixed(1) + ' s';
}

function statusStyle(s: string) {
  const colors: Record<string, string> = {
    PASS: '#52c41a',
    FAIL: '#f5222d',
    ERROR: '#fa8c16',
    RUNNING: '#e6a23c',
  };
  const bg = colors[s] || '#909399';
  return { backgroundColor: bg, borderColor: bg, color: '#fff' };
}

function statusLabel(s: string) {
  const m: Record<string, string> = {
    PASS: '通过',
    FAIL: '失败',
    ERROR: '异常',
    RUNNING: '执行中',
    CANCELLED: '已取消',
  };
  return m[s] || s;
}

function triggerLabel(t: string) {
  const m: Record<string, string> = {
    MANUAL: '手动',
    SCHEDULE: '定时',
    CASE: '用例',
    SCENARIO: '场景',
    PLAN: '计划',
  };
  return m[t] || t;
}

async function loadList(page = 1) {
  if (!store.currentProject) return;
  loading.value = true;
  currentPage.value = page;
  try {
    const res = await uiExecApi.list(store.currentProject.id, page, 20);
    list.value = res.data.records;
    total.value = res.data.total;
  } finally { loading.value = false; }
}

function onRowClick(row: any) {
  router.push('/ui-auto/execution/' + row.id);
}

async function doDelete(id: string) {
  await uiExecApi.delete(id);
  ElMessage.success('已删除');
  loadList();
}

watch(() => store.currentProject, () => loadList());
onMounted(loadList);
</script>

<style scoped>
.page-wrap-single { padding: 20px; background: #f0f2f5; height: 100%; overflow: auto; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; }
.pagination-bar { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
