<template>
  <div style="padding: 24px">
    <h3>用例回收站</h3>
    <a-empty v-if="!items.length" description="回收站为空" />
    <a-table v-else :columns="columns" :data-source="items" row-key="id" :loading="loading" :pagination="false" size="middle">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="restore(record.id)">恢复</a-button>
            <a-popconfirm title="彻底删除？不可恢复" @confirm="permanentDel(record.id)">
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

async function load() {
  if (!store.currentProject) return;
  loading.value = true;
  try { items.value = (await recycleBinApi.list(store.currentProject.id)).data; } finally { loading.value = false; }
}
watch(() => store.currentProject, load);
onMounted(load);

async function restore(id: string) { await recycleBinApi.restore(id); message.success('已恢复'); load(); }
async function permanentDel(id: string) { await recycleBinApi.permanentDelete(id); message.success('已删除'); load(); }

const columns = [
  { title: '用例集ID', dataIndex: 'caseSetId' },
  { title: '删除时间', dataIndex: 'deletedAt', width: 180 },
  { title: '删除人', dataIndex: 'deletedBy', width: 100 },
  { title: '操作', key: 'action', width: 200 },
];
</script>
