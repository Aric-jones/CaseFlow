<template>
  <div class="settings-page">
    <div class="page-header">
      <h2>成员管理</h2>
      <el-button type="primary" :icon="Plus" @click="showAdd = true">添加成员</el-button>
    </div>

    <div class="content-card">
      <el-table :data="users" v-loading="loading" border style="width:100%">
        <el-table-column label="用户名" prop="username" min-width="120" show-overflow-tooltip />
        <el-table-column label="显示名" prop="displayName" min-width="120" show-overflow-tooltip />
        <el-table-column label="角色" min-width="120">
          <template #default="{ row }">
            <el-select :model-value="row.role" size="small" style="width:100%"
              @change="(v: any) => userApi.update(row.id, { role: v }).then(() => loadUsers(page))">
              <el-option value="SUPER_ADMIN" label="超管" />
              <el-option value="ADMIN" label="管理员" />
              <el-option value="MEMBER" label="成员" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="身份" min-width="100">
          <template #default="{ row }">
            <el-select :model-value="row.identity" size="small" style="width:100%"
              @change="(v: any) => userApi.update(row.id, { identity: v }).then(() => loadUsers(page))">
              <el-option value="TEST" label="测试" />
              <el-option value="DEV" label="研发" />
              <el-option value="PRODUCT" label="产品" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="项目权限" min-width="200">
          <template #default="{ row }">
            <el-select :model-value="userProjectMap[row.id] || []" multiple
              collapse-tags collapse-tags-tooltip size="small" style="width:100%"
              @change="(v: any) => onProjectsChange(row.id, v)">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small" effect="light">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="70" align="center">
          <template #default="{ row }">
            <el-button :type="row.status === 1 ? 'danger' : 'primary'" link size="small"
              :loading="locks['toggle_' + row.id]" @click="toggleStatus(row.id)">
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination layout="total, prev, pager, next" :total="total"
          :page-size="20" :current-page="page" @current-change="(p: number) => loadUsers(p)" background />
      </div>
    </div>

    <el-dialog v-model="showAdd" title="添加成员" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名"><el-input v-model="form.username" /></el-form-item>
        <el-form-item label="显示名"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item label="身份">
          <el-radio-group v-model="form.identity">
            <el-radio-button value="TEST">测试</el-radio-button>
            <el-radio-button value="DEV">研发</el-radio-button>
            <el-radio-button value="PRODUCT">产品</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="项目权限">
          <el-select v-model="form.projectIds" multiple style="width:100%" placeholder="选择项目（可多选）">
            <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert type="info" :closable="false" style="margin-top:12px">
        <template #title>默认密码：wps123456</template>
      </el-alert>
      <template #footer>
        <el-button @click="showAdd = false">取消</el-button>
        <el-button type="primary" :loading="locks.addUser" @click="addUser">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { userApi, projectApi } from '../../api';
import type { User, Project } from '../../types';
import { useGuard } from '../../composables/useGuard';

const users = ref<User[]>([]);
const projects = ref<Project[]>([]);
const loading = ref(false);
const page = ref(1);
const total = ref(0);
const showAdd = ref(false);
const form = reactive({ username: '', displayName: '', identity: 'TEST', projectIds: [] as string[] });
const userProjectMap = ref<Record<string, string[]>>({});
const { locks, run } = useGuard();

async function loadUsers(p = 1) {
  loading.value = true;
  try {
    const res = await userApi.list(p, 20);
    users.value = res.data.records;
    total.value = res.data.total;
    page.value = p;
    await loadUserProjects();
  } finally { loading.value = false; }
}
async function loadUserProjects() {
  const map: Record<string, string[]> = {};
  await Promise.all(users.value.map(async u => {
    try { map[u.id] = (await userApi.getUserProjects(u.id)).data; } catch { map[u.id] = []; }
  }));
  userProjectMap.value = map;
}
onMounted(() => { projectApi.listAll().then(r => projects.value = r.data); loadUsers(); });

async function addUser() {
  await run('addUser', async () => {
    await userApi.create({ ...form, password: 'wps123456' });
    ElMessage.success('创建成功'); showAdd.value = false;
    Object.assign(form, { username: '', displayName: '', identity: 'TEST', projectIds: [] });
    loadUsers();
  });
}
async function toggleStatus(userId: string) {
  await run('toggle_' + userId, async () => {
    await userApi.toggleStatus(userId);
    await loadUsers(page.value);
  });
}
async function onProjectsChange(userId: string, projectIds: string[]) {
  userProjectMap.value[userId] = projectIds;
  try { await userApi.updateUserProjects(userId, projectIds); ElMessage.success('项目权限已更新'); }
  catch { ElMessage.error('更新失败'); }
}

</script>

<style scoped>
.settings-page { padding: 24px; height: 100%; overflow: auto; background: #f0f2f5; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #1f2329; margin: 0; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.pagination-bar { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
