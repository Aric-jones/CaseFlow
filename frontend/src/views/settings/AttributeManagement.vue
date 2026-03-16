<template>
  <div class="settings-page">
    <div class="page-header">
      <h2>用例属性管理</h2>
      <el-button type="primary" :icon="Plus"
        @click="editId = null; Object.assign(form, { name:'', options:'', multiSelect:false, required:false, nodeTypeLimit:'', displayType:'DROPDOWN' }); showModal = true">
        新增属性
      </el-button>
    </div>
    <div class="content-card">
      <el-table :data="attrs" v-loading="loading" border style="width:100%">
        <el-table-column label="属性名" prop="name" min-width="120" show-overflow-tooltip />
        <el-table-column label="可选值" min-width="220">
          <template #default="{ row }">
            <div style="display:flex;flex-wrap:wrap;gap:4px">
              <el-tag v-for="o in row.options.slice(0,5)" :key="o" size="small" type="info">{{ o }}</el-tag>
              <el-tag v-if="row.options.length > 5" size="small" type="info">+{{ row.options.length - 5 }}</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="多选" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.multiSelect ? 'primary' : 'info'" size="small">{{ row.multiSelect ? '是' : '否' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="必填" min-width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.required ? 'danger' : 'info'" size="small">{{ row.required ? '必填' : '选填' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="限制节点" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">{{ row.nodeTypeLimit || '不限' }}</template>
        </el-table-column>
        <el-table-column label="展示" min-width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.displayType === 'TILE' ? 'success' : ''">
              {{ row.displayType === 'TILE' ? '平铺' : '下拉' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="120">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="startEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除?" @confirm="customAttributeApi.delete(row.id).then(loadAttrs)">
              <template #reference>
                <el-button text type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showModal" :title="editId ? '编辑属性' : '新增属性'" width="500px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="属性值（逗号分隔）">
          <el-input v-model="form.options" placeholder="值1,值2,值3" />
        </el-form-item>
        <el-form-item label="是否多选"><el-switch v-model="form.multiSelect" /></el-form-item>
        <el-form-item label="是否必填"><el-switch v-model="form.required" /></el-form-item>
        <el-form-item label="限制节点类型">
          <el-select v-model="form.nodeTypeLimit" clearable placeholder="不限制" style="width:100%">
            <el-option value="" label="不限制" />
            <el-option value="TITLE" label="用例标题" />
            <el-option value="PRECONDITION" label="前置条件" />
            <el-option value="STEP" label="步骤" />
            <el-option value="EXPECTED" label="预期结果" />
          </el-select>
        </el-form-item>
        <el-form-item label="展示形式">
          <el-radio-group v-model="form.displayType">
            <el-radio-button value="DROPDOWN">下拉框</el-radio-button>
            <el-radio-button value="TILE">平铺选择</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showModal = false">取消</el-button>
        <el-button type="primary" :loading="locks.save" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus } from '@element-plus/icons-vue';
import { customAttributeApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { CustomAttribute } from '../../types';
import { useGuard } from '../../composables/useGuard';

const store = useAppStore();
const attrs = ref<CustomAttribute[]>([]);
const loading = ref(false);
const showModal = ref(false);
const editId = ref<string | null>(null);
const form = reactive({ name: '', options: '', multiSelect: false, required: false, nodeTypeLimit: '', displayType: 'DROPDOWN' });
const { locks, run } = useGuard();

async function loadAttrs() {
  if (!store.currentProject) return;
  loading.value = true;
  try { attrs.value = (await customAttributeApi.list(store.currentProject.id)).data; } finally { loading.value = false; }
}
watch(() => store.currentProject, loadAttrs);
onMounted(loadAttrs);

function startEdit(attr: CustomAttribute) {
  editId.value = attr.id;
  form.name = attr.name; form.options = attr.options.join(',');
  form.multiSelect = attr.multiSelect === 1; form.required = attr.required === 1;
  form.nodeTypeLimit = attr.nodeTypeLimit || ''; form.displayType = attr.displayType;
  showModal.value = true;
}
async function save() {
  await run('save', async () => {
    const data = {
      projectId: store.currentProject?.id, name: form.name,
      options: form.options.split(',').map(s => s.trim()).filter(Boolean),
      multiSelect: form.multiSelect ? 1 : 0, required: form.required ? 1 : 0,
      nodeTypeLimit: form.nodeTypeLimit || null, displayType: form.displayType,
    };
    if (editId.value) await customAttributeApi.update(editId.value, data);
    else await customAttributeApi.create(data);
    ElMessage.success('保存成功'); showModal.value = false; loadAttrs();
  });
}

</script>

<style scoped>
.settings-page { padding: 24px; height: 100%; overflow: auto; background: #f0f2f5; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #1f2329; margin: 0; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
</style>
