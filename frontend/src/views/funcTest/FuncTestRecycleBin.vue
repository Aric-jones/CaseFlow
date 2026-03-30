<template>
  <div class="page-wrap-single">
    <div class="content-card">
      <div class="toolbar">
        <el-radio-group v-model="itemType" @change="load">
          <el-radio-button value="CASE_SET">用例集</el-radio-button>
          <el-radio-button value="TEST_PLAN">测试计划</el-radio-button>
        </el-radio-group>
        <div style="flex:1" />
        <template v-if="selected.length">
          <el-button type="primary" size="small" :loading="locks.batchRestore" @click="batchRestore">
            批量恢复 ({{ selected.length }})
          </el-button>
          <el-popconfirm :title="itemType === 'TEST_PLAN' ? '批量彻底删除所选项？将同时删除关联用例，不可恢复' : '批量彻底删除所选项？不可恢复'" @confirm="batchDel">
            <template #reference>
              <el-button type="danger" size="small" :loading="locks.batchDel">
                批量删除 ({{ selected.length }})
              </el-button>
            </template>
          </el-popconfirm>
        </template>
      </div>

      <el-empty v-if="!items.length && !loading" description="回收站为空" :image-size="80" />
      <el-table v-else :data="items" v-loading="loading" border style="width:100%"
                @selection-change="onSelect">
        <el-table-column type="selection" width="50" />
        <el-table-column :label="itemType === 'TEST_PLAN' ? '计划名称' : '用例集名称'" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemName || row.caseSetName || '-' }}</template>
        </el-table-column>
        <el-table-column label="创建人" prop="createdByName" min-width="100" show-overflow-tooltip />
        <el-table-column label="删除时间" min-width="170">
          <template #default="{ row }">{{ fmtTime(row.deletedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" :loading="locks['r_' + row.id]" @click="restore(row.id)">恢复</el-button>
            <el-popconfirm :title="itemType === 'TEST_PLAN' ? '彻底删除？将同时删除关联用例，不可恢复' : '彻底删除？不可恢复'" @confirm="permanentDel(row.id)">
              <template #reference>
                <el-button text type="danger" size="small" :loading="locks['d_' + row.id]">彻底删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { recycleBinApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { RecycleBinItem } from '../../types';
import { useGuard } from '../../composables/useGuard';

const store = useAppStore();
const itemType = ref('CASE_SET');
const items = ref<RecycleBinItem[]>([]);
const selected = ref<RecycleBinItem[]>([]);
const loading = ref(false);
const { locks, run } = useGuard();

async function load() {
  if (!store.currentProject) return;
  loading.value = true;
  selected.value = [];
  try {
    if (itemType.value === 'CASE_SET') {
      items.value = (await recycleBinApi.list(store.currentProject.id)).data;
    } else {
      items.value = (await recycleBinApi.list(store.currentProject.id, 'TEST_PLAN')).data as any || [];
    }
  } catch { ElMessage.error('加载失败'); }
  finally { loading.value = false; }
}

function onSelect(rows: RecycleBinItem[]) { selected.value = rows; }
function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }

async function restore(id: string) {
  await run('r_' + id, async () => { await recycleBinApi.restore(id); ElMessage.success('已恢复'); load(); });
}
async function permanentDel(id: string) {
  await run('d_' + id, async () => { await recycleBinApi.permanentDelete(id); ElMessage.success('已彻底删除'); load(); });
}

async function batchRestore() {
  await run('batchRestore', async () => {
    const ids = selected.value.map(r => r.id);
    await recycleBinApi.batchRestore(ids);
    ElMessage.success(`已恢复 ${ids.length} 条`);
    load();
  });
}
async function batchDel() {
  await run('batchDel', async () => {
    const ids = selected.value.map(r => r.id);
    await recycleBinApi.batchDelete(ids);
    ElMessage.success(`已彻底删除 ${ids.length} 条`);
    load();
  });
}

watch(() => store.currentProject, load);
onMounted(load);
</script>

<style scoped>
.page-wrap-single { padding: 20px; background: #f0f2f5; height: 100%; overflow: auto; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; flex-wrap: wrap; }
</style>
