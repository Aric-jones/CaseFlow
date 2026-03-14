<template>
  <div class="settings-page">
    <div class="page-header">
      <h2>项目空间管理</h2>
      <el-button type="primary" :icon="Plus"
        @click="editId = null; form.name = ''; form.description = ''; showModal = true">
        创建项目
      </el-button>
    </div>
    <div class="content-card">
      <el-table :data="projects" v-loading="loading" border style="width:100%">
        <el-table-column label="项目名称" prop="name" min-width="200" show-overflow-tooltip />
        <el-table-column label="描述" prop="description" min-width="350" show-overflow-tooltip />
        <el-table-column label="创建时间" min-width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="120">
          <template #default="{ row }">
            <el-button text type="primary" size="small"
              @click="editId = row.id; form.name = row.name; form.description = row.description; showModal = true">
              编辑
            </el-button>
            <el-popconfirm title="确认删除?" @confirm="projectApi.delete(row.id).then(load)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showModal" :title="editId ? '编辑项目' : '创建项目'" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="项目名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showModal = false">取消</el-button>
        <el-button type="primary" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
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
  if (editId.value) await projectApi.update(editId.value, form.name, form.description);
  else await projectApi.create(form.name, form.description);
  ElMessage.success('保存成功'); showModal.value = false; load();
}
function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }

</script>

<style scoped>
.settings-page { padding: 24px; height: 100%; overflow: auto; background: #f0f2f5; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #1f2329; margin: 0; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
</style>
