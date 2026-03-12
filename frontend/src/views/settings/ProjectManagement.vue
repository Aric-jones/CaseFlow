<template>
  <div style="padding: 24px">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>项目空间管理</h3>
      <a-button type="primary" @click="editId = null; form.name = ''; form.description = ''; showModal = true"><PlusOutlined /> 创建项目</a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="projects"
      row-key="id"
      :loading="loading"
      :pagination="false"
      size="middle"
      :scroll="{ x: 960 }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="editId = record.id; form.name = record.name; form.description = record.description; showModal = true">编辑</a-button>
            <a-popconfirm title="确认删除?" @confirm="projectApi.delete(record.id).then(load)"><a-button type="link" size="small" danger>删除</a-button></a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
    <a-modal v-model:open="showModal" :title="editId ? '编辑项目' : '创建项目'" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="项目名称"><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="描述"><a-textarea v-model:value="form.description" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import { projectApi } from '../../api';
import type { Project } from '../../types';

const projects = ref<Project[]>([]);
const loading = ref(false);
const showModal = ref(false);
const editId = ref<string | null>(null);
const form = reactive({ name: '', description: '' });

async function load() { loading.value = true; try { projects.value = (await projectApi.listAll()).data; } finally { loading.value = false; } }
onMounted(load);

async function save() {
  if (editId.value) { await projectApi.update(editId.value, form.name, form.description); }
  else { await projectApi.create(form.name, form.description); }
  message.success('保存成功'); showModal.value = false; load();
}

const columns = ref([
  { title: '项目名称', dataIndex: 'name', key: 'name', resizable: true, width: 220 },
  { title: '描述', dataIndex: 'description', key: 'description', resizable: true, width: 420 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', resizable: true, width: 220 },
  { title: '操作', key: 'action', resizable: true, width: 180 },
]);

function handleResizeColumn(w: number, col: any) { col.width = w; }
</script>
