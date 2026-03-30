<template>
  <div class="page-wrap">
    <aside class="page-sidebar" :class="{ collapsed: siderCollapsed }">
      <div class="sidebar-header">
        <span v-if="!siderCollapsed" class="sidebar-title">页面目录</span>
        <el-button text circle @click="siderCollapsed = !siderCollapsed" class="collapse-btn">
          <el-icon><Fold v-if="!siderCollapsed" /><Expand v-else /></el-icon>
        </el-button>
      </div>
      <div v-if="!siderCollapsed" class="sidebar-body">
        <div v-if="!treeData.length" class="empty-tree">
          <el-button type="primary" link @click="startAddRoot">创建根目录</el-button>
        </div>
        <el-tree v-else :data="treeData" node-key="id" :highlight-current="true"
          :current-node-key="selectedDir || undefined" default-expand-all :expand-on-click-node="false"
          @node-click="(d: any) => { selectedDir = d.id; }">
          <template #default="{ data }">
            <div class="tree-node">
              <el-icon size="13" color="#909399"><Folder /></el-icon>
              <span class="node-label">{{ data.label }}</span>
              <el-button text size="small" class="add-child-btn" @click.stop="startAdd(data.id)"><el-icon><Plus /></el-icon></el-button>
            </div>
          </template>
        </el-tree>
        <div v-if="addingDir" class="inline-add">
          <input class="node-input" v-model="newDirName" @keyup.enter="confirmAdd" @blur="confirmAdd" placeholder="输入名称回车保存" autofocus />
        </div>
      </div>
    </aside>
    <main class="page-main">
      <div class="content-card">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="搜索页面名称" style="width:220px" clearable :prefix-icon="Search" @keyup.enter="() => loadList(1)" />
          <el-button @click="() => loadList(1)">搜索</el-button>
          <div style="flex:1" />
          <el-button type="primary" :icon="Plus" @click="showCreate = true">新建页面</el-button>
        </div>
        <el-table :data="list" v-loading="loading" border style="width:100%" @row-click="onRowClick">
          <el-table-column prop="name" label="页面名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="url" label="URL" min-width="250" show-overflow-tooltip />
          <el-table-column label="元素数" min-width="80" align="center">
            <template #default="{ row }"><el-tag size="small" round>{{ row.elementCount || 0 }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="createdByName" label="创建人" min-width="90" />
          <el-table-column label="创建时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click.stop="goDetail(row)">管理元素</el-button>
              <el-popconfirm title="确认删除？" @confirm="doDelete(row.id)">
                <template #reference><el-button text type="danger" size="small" @click.stop>删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-bar">
          <el-pagination layout="total, prev, pager, next" :total="total" :page-size="20" :current-page="currentPage" @current-change="loadList" background />
        </div>
      </div>
    </main>
    <el-dialog v-model="showCreate" title="新建页面" width="500px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="URL"><el-input v-model="form.url" placeholder="https://example.com" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" @click="doCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Plus, Search } from '@element-plus/icons-vue';
import { directoryApi, uiPageApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { DirectoryNode } from '../../types';

const router = useRouter();
const store = useAppStore();
const projectId = () => store.currentProject?.id || '';

const dirs = ref<DirectoryNode[]>([]);
const selectedDir = ref<string | null>(null);
const siderCollapsed = ref(false);
const addingDir = ref(false);
const newDirName = ref('');
const addParentId = ref<string | null>(null);

function dirToTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ id: d.id, label: d.name, children: d.children?.length ? dirToTree(d.children) : [] }));
}
const treeData = computed(() => dirToTree(dirs.value));

async function loadDirs() {
  if (!projectId()) return;
  dirs.value = (await directoryApi.tree(projectId(), 'UI_PAGE')).data;
}
function startAddRoot() { addingDir.value = true; addParentId.value = null; newDirName.value = ''; }
function startAdd(pid: string) { addingDir.value = true; addParentId.value = pid; newDirName.value = ''; }
async function confirmAdd() {
  const name = newDirName.value.trim();
  if (!name || !projectId() || !addingDir.value) { addingDir.value = false; return; }
  addingDir.value = false;
  await directoryApi.create(name, addParentId.value, projectId(), 'UI_PAGE');
  loadDirs();
}

const list = ref<any[]>([]);
const total = ref(0);
const currentPage = ref(1);
const keyword = ref('');
const loading = ref(false);
const showCreate = ref(false);
const form = ref({ name: '', url: '', description: '' });

function fmtTime(t?: string) { return t ? t.replace('T', ' ').substring(0, 16) : ''; }

async function loadList(page = 1) {
  if (!projectId()) return;
  loading.value = true;
  currentPage.value = page;
  try {
    const res = await uiPageApi.list({ projectId: projectId(), directoryId: selectedDir.value ?? undefined, keyword: keyword.value || undefined, page, size: 20 });
    list.value = res.data.records;
    total.value = res.data.total;
  } finally { loading.value = false; }
}

async function doCreate() {
  if (!form.value.name.trim()) { ElMessage.warning('请输入名称'); return; }
  await uiPageApi.create({ ...form.value, projectId: projectId(), directoryId: selectedDir.value ?? undefined });
  ElMessage.success('创建成功');
  showCreate.value = false;
  form.value = { name: '', url: '', description: '' };
  loadList();
}

function goDetail(row: any) { router.push('/ui-auto/page/' + row.id); }
function onRowClick(row: any) { goDetail(row); }
async function doDelete(id: string) { await uiPageApi.delete(id); ElMessage.success('已删除'); loadList(); }

watch(() => store.currentProject, () => { loadDirs(); loadList(); });
watch(selectedDir, () => loadList());
onMounted(() => { loadDirs(); loadList(); });
</script>

<style scoped>
.page-wrap { display: flex; height: 100%; overflow: hidden; }
.page-sidebar { width: 240px; flex-shrink: 0; background: #fff; display: flex; flex-direction: column; transition: width 0.25s ease; overflow: hidden; box-shadow: 2px 0 8px rgba(0,0,0,0.04); }
.page-sidebar.collapsed { width: 48px; }
.sidebar-header { display: flex; align-items: center; justify-content: space-between; padding: 0 12px; height: 52px; border-bottom: 1px solid #f0f2f5; }
.sidebar-title { font-size: 13px; font-weight: 600; color: #1f2329; }
.sidebar-body { flex: 1; overflow: auto; padding: 8px 0; }
.empty-tree { text-align: center; padding: 40px 16px; }
.tree-node { display: flex; align-items: center; gap: 6px; width: 100%; }
.node-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.add-child-btn { opacity: 0; transition: opacity 0.15s; }
.tree-node:hover .add-child-btn { opacity: 1; }
.node-input { border: 1px solid #409eff; border-radius: 4px; padding: 2px 6px; font-size: 13px; flex: 1; outline: none; }
.inline-add { padding: 4px 12px; }
.page-main { flex: 1; overflow: auto; background: #f0f2f5; padding: 20px; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; }
.pagination-bar { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
