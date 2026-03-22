<template>
  <div class="page-wrap-single">
    <div class="content-card">
      <div class="toolbar">
        <el-input v-model="keyword" placeholder="搜索来源名称" style="width:220px"
          clearable :prefix-icon="Search" @keyup.enter="() => loadList(1)" />
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:130px" @change="() => loadList(1)">
          <el-option value="PASS" label="通过" />
          <el-option value="FAIL" label="失败" />
          <el-option value="RUNNING" label="执行中" />
          <el-option value="ERROR" label="异常" />
        </el-select>
        <el-button @click="() => loadList(1)">搜索</el-button>
        <div style="flex:1" />
        <el-popconfirm v-if="selectedRows.length" title="确认批量删除所选执行记录？" @confirm="batchDeleteExecs">
          <template #reference>
            <el-button type="danger">批量删除 ({{ selectedRows.length }})</el-button>
          </template>
        </el-popconfirm>
      </div>

      <el-table :data="list" v-loading="loading" border style="width:100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" />
        <el-table-column label="状态" width="90" align="center" fixed="left">
          <template #default="{ row }">
            <el-tag :type="statusColor(row.status)" size="small" effect="dark">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.planName">计划: {{ row.planName }}</span>
            <span v-else-if="row.scenarioName">场景: {{ row.scenarioName }}</span>
            <span v-else>单用例调试</span>
          </template>
        </el-table-column>
        <el-table-column label="通过率" min-width="200">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-progress
                :percentage="row.totalCases ? Math.round(row.passedCases / row.totalCases * 100) : 0"
                :color="row.failedCases ? '#f56c6c' : '#52c41a'" :show-text="false" style="flex:1;min-width:60px" />
              <span style="font-size:12px;color:#909399;white-space:nowrap;min-width:36px;text-align:right">
                {{ row.passedCases }}/{{ row.totalCases }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="耗时" min-width="80">
          <template #default="{ row }">{{ (row.durationMs / 1000).toFixed(1) }}s</template>
        </el-table-column>
        <el-table-column prop="executedByName" label="执行人" min-width="90" show-overflow-tooltip />
        <el-table-column label="执行时间" min-width="160">
          <template #default="{ row }">{{ fmtTime(row.startedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="$router.push('/api-auto/execution/' + row.id)">报告</el-button>
            <el-popconfirm title="确认删除？" @confirm="doDelete(row.id)">
              <template #reference><el-button text type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination layout="total, prev, pager, next" :total="total"
          :page-size="20" :current-page="currentPage" @current-change="loadList" background />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import { apiExecApi } from '../../api';
import { useAppStore } from '../../stores/app';

const store = useAppStore();
const list = ref<any[]>([]);
const total = ref(0);
const currentPage = ref(1);
const keyword = ref('');
const statusFilter = ref('');
const loading = ref(false);
const selectedRows = ref<any[]>([]);

function handleSelectionChange(rows: any[]) {
  selectedRows.value = rows;
}

async function batchDeleteExecs() {
  if (!selectedRows.value.length) { ElMessage.warning('请先选择要删除的记录'); return; }
  const ids = selectedRows.value.map((r: any) => r.id);
  try {
    await apiExecApi.batchDelete(ids);
    ElMessage.success(`成功删除 ${ids.length} 条记录`);
    selectedRows.value = [];
    loadList();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '删除失败');
  }
}

function fmtTime(t?: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }
function statusColor(s: string): any { return ({ PASS: 'success', FAIL: 'danger', RUNNING: 'warning', ERROR: 'danger' } as any)[s] || 'info'; }
function statusLabel(s: string) { return ({ PASS: '通过', FAIL: '失败', RUNNING: '执行中', ERROR: '异常', CANCELLED: '已取消' } as any)[s] || s; }

async function loadList(page = 1) {
  if (!store.currentProject) return;
  loading.value = true;
  currentPage.value = page;
  try {
    const res = await apiExecApi.list(store.currentProject.id, page, 20);
    list.value = res.data.records;
    total.value = res.data.total;
  } finally { loading.value = false; }
}

async function doDelete(id: string) {
  await apiExecApi.delete(id);
  ElMessage.success('已删除');
  loadList();
}

watch(() => store.currentProject, () => loadList());
onMounted(loadList);
</script>

<style scoped>
.page-wrap-single { padding: 20px; background: #f0f2f5; height: 100%; overflow: auto; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; flex-wrap: wrap; }
.pagination-bar { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
