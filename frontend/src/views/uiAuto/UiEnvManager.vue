<template>
  <div class="env-page">
    <div class="env-header">
      <h3>环境管理</h3>
      <el-button type="primary" @click="openForm()">新建环境</el-button>
    </div>
    <el-table :data="envList" border stripe style="width:100%">
      <el-table-column prop="name" label="环境名称" min-width="120" />
      <el-table-column prop="baseUrl" label="Base URL" min-width="200" show-overflow-tooltip />
      <el-table-column label="环境变量数" width="120" align="center">
        <template #default="{ row }">{{ row.variables ? Object.keys(row.variables).length : 0 }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
      <el-table-column prop="createdByName" label="创建人" width="100" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openForm(row)">编辑</el-button>
          <el-popconfirm title="确认删除？" @confirm="doDelete(row.id)">
            <template #reference><el-button text type="danger" size="small">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="formData.id ? '编辑环境' : '新建环境'" width="640px" destroy-on-close>
      <el-form :model="formData" label-width="90px">
        <el-form-item label="环境名称" required>
          <el-input v-model="formData.name" placeholder="如：生产环境、测试环境" />
        </el-form-item>
        <el-form-item label="Base URL" required>
          <el-input v-model="formData.baseUrl" placeholder="https://example.com" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" placeholder="环境描述" />
        </el-form-item>
        <el-form-item label="环境变量">
          <div class="kv-editor">
            <div v-for="(_, key) in (formData.variables || {})" :key="key" class="kv-row">
              <el-input :model-value="key" disabled size="small" style="width:160px" />
              <el-input :model-value="formData.variables![key]" @update:model-value="v => formData.variables![key] = v" size="small" style="flex:1" />
              <el-button text type="danger" size="small" @click="delete formData.variables![key]">删除</el-button>
            </div>
            <div class="kv-add">
              <el-input v-model="newVarKey" size="small" placeholder="变量名" style="width:160px" />
              <el-input v-model="newVarVal" size="small" placeholder="变量值" style="flex:1" />
              <el-button text type="primary" size="small" @click="addVar">添加</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { uiEnvApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { UiEnv } from '../../types';

const store = useAppStore();
const envList = ref<UiEnv[]>([]);
const dialogVisible = ref(false);
const saving = ref(false);
const formData = ref<Partial<UiEnv>>({});
const newVarKey = ref('');
const newVarVal = ref('');

async function loadList() {
  if (!store.currentProject) return;
  const res = await uiEnvApi.list(store.currentProject.id);
  envList.value = res.data;
}

function openForm(row?: UiEnv) {
  if (row) {
    formData.value = { ...row, variables: { ...(row.variables || {}) } };
  } else {
    formData.value = { projectId: store.currentProject?.id, name: '', baseUrl: '', description: '', variables: {} };
  }
  newVarKey.value = '';
  newVarVal.value = '';
  dialogVisible.value = true;
}

function addVar() {
  if (!newVarKey.value.trim()) return;
  if (!formData.value.variables) formData.value.variables = {};
  formData.value.variables[newVarKey.value.trim()] = newVarVal.value;
  newVarKey.value = '';
  newVarVal.value = '';
}

async function doSave() {
  if (!formData.value.name?.trim()) { ElMessage.warning('请输入环境名称'); return; }
  if (!formData.value.baseUrl?.trim()) { ElMessage.warning('请输入 Base URL'); return; }
  saving.value = true;
  try {
    if (formData.value.id) {
      await uiEnvApi.update(formData.value.id, formData.value);
    } else {
      await uiEnvApi.create(formData.value);
    }
    ElMessage.success('保存成功');
    dialogVisible.value = false;
    loadList();
  } finally { saving.value = false; }
}

async function doDelete(id: string) {
  try {
    await uiEnvApi.delete(id);
    ElMessage.success('已删除');
    loadList();
  } catch { /* error handled by interceptor */ }
}

onMounted(loadList);
</script>

<style scoped>
.env-page { padding: 20px; }
.env-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.env-header h3 { margin: 0; font-size: 16px; }
.kv-editor { width: 100%; }
.kv-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
.kv-add { display: flex; gap: 8px; align-items: center; margin-top: 4px; }
</style>
