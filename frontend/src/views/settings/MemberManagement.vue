<template>
  <div style="padding: 24px">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>成员管理</h3>
      <a-button type="primary" @click="showAdd = true"><PlusOutlined /> 添加成员</a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="users"
      row-key="id"
      :loading="loading"
      :pagination="{ current: page, total, pageSize: 20, onChange: (p: number) => loadUsers(p) }"
      size="middle"
      :scroll="{ x: 980 }"
      @resizeColumn="handleResizeColumn"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'role'">
          <a-select :value="record.role" @change="(v: any) => userApi.update(record.id, { role: v }).then(() => loadUsers(page))" size="small" style="width: 100px"
            :options="[{value:'SUPER_ADMIN',label:'超管'},{value:'ADMIN',label:'管理员'},{value:'MEMBER',label:'成员'}]" />
        </template>
        <template v-if="column.key === 'identity'">
          <a-select :value="record.identity" @change="(v: any) => userApi.update(record.id, { identity: v }).then(() => loadUsers(page))" size="small" style="width: 80px"
            :options="[{value:'TEST',label:'测试'},{value:'DEV',label:'研发'},{value:'PRODUCT',label:'产品'}]" />
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'success' : 'error'">{{ record.status === 1 ? '启用' : '禁用' }}</a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-button type="link" size="small" @click="userApi.toggleStatus(record.id).then(() => loadUsers(page))">
            {{ record.status === 1 ? '禁用' : '启用' }}
          </a-button>
        </template>
      </template>
    </a-table>
    <a-modal v-model:open="showAdd" title="添加成员" @ok="addUser">
      <a-form layout="vertical">
        <a-form-item label="用户名"><a-input v-model:value="form.username" /></a-form-item>
        <a-form-item label="显示名"><a-input v-model:value="form.displayName" /></a-form-item>
        <a-form-item label="身份">
          <a-select v-model:value="form.identity" :options="[{value:'TEST',label:'测试'},{value:'DEV',label:'研发'},{value:'PRODUCT',label:'产品'}]" />
        </a-form-item>
        <a-form-item label="项目权限">
          <a-select mode="multiple" v-model:value="form.projectIds" :options="projects.map((p: any) => ({ value: p.id, label: p.name }))" />
        </a-form-item>
      </a-form>
      <a-typography-text type="secondary">默认密码: wps123456</a-typography-text>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import { userApi, projectApi } from '../../api';
import type { User, Project } from '../../types';

const users = ref<User[]>([]);
const projects = ref<Project[]>([]);
const loading = ref(false);
const page = ref(1);
const total = ref(0);
const showAdd = ref(false);
const form = reactive({ username: '', displayName: '', identity: 'TEST', projectIds: [] as string[] });

async function loadUsers(p = 1) {
  loading.value = true;
  try { const res = await userApi.list(p, 20); users.value = res.data.records; total.value = res.data.total; page.value = p; }
  finally { loading.value = false; }
}
onMounted(() => { loadUsers(); projectApi.listAll().then(r => projects.value = r.data); });

async function addUser() {
  await userApi.create({ ...form, password: 'wps123456' });
  message.success('创建成功'); showAdd.value = false; Object.assign(form, { username: '', displayName: '', identity: 'TEST', projectIds: [] }); loadUsers();
}

const columns = ref([
  { title: '用户名', dataIndex: 'username', key: 'username', resizable: true, width: 180 },
  { title: '显示名', dataIndex: 'displayName', key: 'displayName', resizable: true, width: 180 },
  { title: '角色', key: 'role', resizable: true, width: 130 },
  { title: '身份', key: 'identity', resizable: true, width: 110 },
  { title: '状态', key: 'status', resizable: true, width: 100 },
  { title: '操作', key: 'action', resizable: true, width: 120 },
]);

function handleResizeColumn(w: number, col: any) { col.width = w; }
</script>
