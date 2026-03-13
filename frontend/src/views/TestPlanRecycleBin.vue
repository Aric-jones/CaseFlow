<template>
  <div style="padding: 24px">
    <h3>测试计划回收站</h3>
    <a-empty v-if="!items.length && !loading" description="回收站为空" />
    <a-table v-else :columns="columns" :data-source="items" row-key="id" :loading="loading"
      :pagination="false" size="middle" :scroll="{ x: 900 }" @resizeColumn="handleResizeColumn">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">{{ record.itemName }}</template>
        <template v-if="column.key === 'deletedAt'">{{ fmtTime(record.deletedAt) }}</template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="restore(record.id)">恢复</a-button>
            <a-popconfirm title="彻底删除？将同时删除执行人和关联用例，不可恢复" @confirm="permanentDel(record.id)">
              <a-button type="link" size="small" danger>彻底删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { recycleBinApi } from '../api';
import { useAppStore } from '../stores/app';
import type { RecycleBinItem } from '../types';

const store = useAppStore();
const items = ref<RecycleBinItem[]>([]);
const loading = ref(false);

/** 加载测试计划回收站数据 */
async function load() {
  if (!store.currentProject) return;
  loading.value = true;
  try {
    const res = await recycleBinApi.list(store.currentProject.id, 'TEST_PLAN');
    items.value = (res.data as any) || [];
  } catch { message.error('加载失败'); }
  finally { loading.value = false; }
}
watch(() => store.currentProject, load);
onMounted(load);

/** 恢复测试计划 */
async function restore(id: string) {
  await recycleBinApi.restore(id); message.success('已恢复'); load();
}

/** 彻底删除（级联删除执行人、用例） */
async function permanentDel(id: string) {
  await recycleBinApi.permanentDelete(id); message.success('已彻底删除'); load();
}

/** 格式化时间 */
function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }

const columns = ref([
  { title: '计划名称', key: 'name', ellipsis: true, resizable: true, width: 360 },
  { title: '删除人', dataIndex: 'deletedByName', key: 'deletedByName', resizable: true, width: 140 },
  { title: '删除时间', key: 'deletedAt', dataIndex: 'deletedAt', resizable: true, width: 220 },
  { title: '操作', key: 'action', resizable: true, width: 200 },
]);

/** 调整列宽 */
function handleResizeColumn(w: number, col: any) { col.width = w; }
</script>
