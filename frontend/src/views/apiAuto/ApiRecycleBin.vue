<template>
  <div class="page-wrap-single">
    <div class="content-card">
      <div class="toolbar">
        <el-radio-group v-model="itemType" @change="load">
          <el-radio-button value="API_DEF">接口定义</el-radio-button>
          <el-radio-button value="API_SCENARIO">测试场景</el-radio-button>
          <el-radio-button value="API_PLAN">测试计划</el-radio-button>
          <el-radio-button value="API_EXECUTION">执行记录</el-radio-button>
        </el-radio-group>
        <div style="flex:1" />
        <template v-if="selected.length">
          <el-button type="primary" size="small" :loading="restoring" @click="batchRestore">
            批量恢复 ({{ selected.length }})
          </el-button>
          <el-popconfirm title="彻底删除所选项？此操作不可恢复" @confirm="batchPermanentDel">
            <template #reference>
              <el-button type="danger" size="small" :loading="deleting">
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
        <el-table-column label="名称" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.itemName || '-' }}</template>
        </el-table-column>
        <el-table-column label="删除人" prop="deletedByName" min-width="100" show-overflow-tooltip />
        <el-table-column label="删除时间" min-width="170">
          <template #default="{ row }">{{ fmtTime(row.deletedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="restoreOne(row.id)">恢复</el-button>
            <el-popconfirm title="彻底删除？不可恢复" @confirm="permanentDelOne(row.id)">
              <template #reference>
                <el-button text type="danger" size="small">彻底删除</el-button>
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

const store = useAppStore();
const itemType = ref('API_DEF');
const items = ref<any[]>([]);
const selected = ref<any[]>([]);
const loading = ref(false);
const restoring = ref(false);
const deleting = ref(false);

async function load() {
  if (!store.currentProject) return;
  loading.value = true;
  selected.value = [];
  try {
    items.value = (await recycleBinApi.list(store.currentProject.id, itemType.value)).data;
  } catch { ElMessage.error('加载失败'); }
  finally { loading.value = false; }
}

function onSelect(rows: any[]) { selected.value = rows; }
function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }

async function restoreOne(id: string) {
  try { await recycleBinApi.restore(id); ElMessage.success('已恢复'); load(); }
  catch (e: any) { ElMessage.error(e.response?.data?.message || '恢复失败'); }
}

async function permanentDelOne(id: string) {
  try { await recycleBinApi.permanentDelete(id); ElMessage.success('已彻底删除'); load(); }
  catch (e: any) { ElMessage.error(e.response?.data?.message || '删除失败'); }
}

async function batchRestore() {
  restoring.value = true;
  try {
    await recycleBinApi.batchRestore(selected.value.map((r: any) => r.id));
    ElMessage.success(`已恢复 ${selected.value.length} 条`);
    load();
  } catch (e: any) { ElMessage.error(e.response?.data?.message || '恢复失败'); }
  finally { restoring.value = false; }
}

async function batchPermanentDel() {
  deleting.value = true;
  try {
    await recycleBinApi.batchDelete(selected.value.map((r: any) => r.id));
    ElMessage.success(`已彻底删除 ${selected.value.length} 条`);
    load();
  } catch (e: any) { ElMessage.error(e.response?.data?.message || '删除失败'); }
  finally { deleting.value = false; }
}

watch(() => store.currentProject, load);
onMounted(load);
</script>

<style scoped>
.page-wrap-single { padding: 20px; background: #f0f2f5; height: 100%; overflow: auto; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; flex-wrap: wrap; }
</style>
