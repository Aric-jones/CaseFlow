<template>
  <div class="page-wrap">
    <!-- 侧边栏 -->
    <aside class="page-sidebar" :class="{ collapsed: siderCollapsed }">
      <div class="sidebar-header">
        <span v-if="!siderCollapsed" class="sidebar-title">计划目录</span>
        <el-button text circle @click="toggleSider" class="collapse-btn">
          <el-icon><Fold v-if="!siderCollapsed" /><Expand v-else /></el-icon>
        </el-button>
      </div>
      <div v-if="!siderCollapsed" class="sidebar-body">
        <div v-if="!treeData.length" class="empty-tree">
          <el-icon size="32" color="#c0c4cc"><FolderOpened /></el-icon>
          <p>暂无目录</p>
          <el-button type="primary" link @click="startAddRoot">创建根目录</el-button>
        </div>
        <el-tree v-else :data="treeData" node-key="id"
          :highlight-current="true" :current-node-key="selectedDir || undefined"
          default-expand-all :expand-on-click-node="false"
          @node-click="(d: any) => { selectedDir = d.id; }"
          @node-contextmenu="(e: MouseEvent, d: any) => showCtx(e, d.id)">
          <template #default="{ data }">
            <div class="tree-node">
              <el-icon size="13" color="#909399" style="flex-shrink:0"><Folder /></el-icon>
              <span v-if="editingDirId !== data.id" class="node-label">{{ data.label }}</span>
              <input v-else class="node-input" :value="editingDirName"
                @input="editingDirName = ($event.target as HTMLInputElement).value"
                @keyup.enter="finishEditDir" @blur="finishEditDir"
                @keyup.escape="cancelEditDir" @click.stop autofocus />
              <el-button v-if="editingDirId !== data.id" text size="small"
                class="add-child-btn" @click.stop="startAdd(data.id)">
                <el-icon><Plus /></el-icon>
              </el-button>
            </div>
          </template>
        </el-tree>
        <div v-if="addingDir" class="inline-add">
          <input class="node-input" v-model="newDirName"
                 @keyup.enter="confirmAdd" @blur="confirmAdd"
                 @keyup.escape="addingDir = false" placeholder="输入名称回车保存" autofocus />
        </div>
      </div>
      <div v-if="!siderCollapsed" class="sidebar-footer" @click="$router.push('/test-plan-recycle-bin')">
        <el-icon color="#909399"><Delete /></el-icon>
        <span>测试计划回收站</span>
      </div>
    </aside>

    <!-- 主内容 -->
    <main class="page-main">
      <div class="content-card">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="搜索计划名称" style="width:240px"
            clearable :prefix-icon="Search" @keyup.enter="() => loadPlans(1)" />
          <el-checkbox v-model="onlyMine">只看我的</el-checkbox>
          <div style="flex:1" />
          <el-button type="primary" :icon="Plus" @click="$router.push('/test-plan/create')">新建测试计划</el-button>
        </div>

        <!-- 批量操作 -->
        <div v-if="selectedPlans.length" style="margin-bottom:8px;display:flex;align-items:center;gap:8px">
          <span style="color:#606266;font-size:13px">已选 {{ selectedPlans.length }} 项</span>
          <el-popconfirm title="确认批量删除？将移入回收站" @confirm="batchDeletePlans">
            <template #reference>
              <el-button type="danger" size="small" :loading="locks.batchDeletePlans">批量删除</el-button>
            </template>
          </el-popconfirm>
        </div>

        <el-table :data="plans.records" v-loading="loading" border style="width:100%"
          @selection-change="(rows: TestPlan[]) => selectedPlans = rows">
          <el-table-column type="selection" width="60" />
          <el-table-column label="计划名称" prop="name" min-width="200" show-overflow-tooltip />
          <el-table-column label="执行进度" min-width="180">
            <template #default="{ row }">
              <div style="display:flex;align-items:center;gap:8px">
                <el-progress
                  :percentage="row.caseTotal ? Math.round(row.caseExecuted/row.caseTotal*100) : 0"
                  color="#52c41a" :show-text="false" style="flex:1;min-width:60px" />
                <span style="font-size:12px;color:#909399;white-space:nowrap;min-width:36px;text-align:right">
                  {{ row.caseExecuted||0 }}/{{ row.caseTotal||0 }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="执行人" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <span v-if="row.executorName">{{ row.executorName }}</span>
              <span v-else style="color:#c0c4cc;font-size:12px">未分配</span>
            </template>
          </el-table-column>
          <el-table-column label="创建人" prop="createdByName" min-width="90" show-overflow-tooltip />
          <el-table-column label="创建时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="140">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="$router.push(`/test-plan/${row.id}/execute`)">执行</el-button>
              <el-button text type="primary" size="small" @click="$router.push(`/test-plan/${row.id}/edit`)">编辑</el-button>
              <el-popconfirm title="确认删除？将移入回收站" @confirm="deletePlan(row.id)">
                <template #reference>
                  <el-button text type="danger" size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination layout="total, prev, pager, next" :total="plans.total"
            :page-size="20" :current-page="plans.current" @current-change="loadPlans" background />
        </div>
      </div>
    </main>

    <!-- 右键菜单 -->
    <div v-if="ctxMenu.visible" class="ctx-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
      <div class="ctx-item" @click="startAdd(ctxMenu.nodeId!)"><el-icon><FolderAdd /></el-icon>新建子目录</div>
      <div class="ctx-item" @click="startAddSibling(ctxMenu.nodeId!)"><el-icon><Plus /></el-icon>新建同级目录</div>
      <div class="ctx-item" @click="startRenameDir(ctxMenu.nodeId!)"><el-icon><Edit /></el-icon>重命名</div>
      <div class="ctx-item danger" @click="delDir(ctxMenu.nodeId!)"><el-icon><Delete /></el-icon>删除</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus, Search } from '@element-plus/icons-vue';
import { useGuard } from '../composables/useGuard';
import { directoryApi, testPlanApi } from '../api';
import { useAppStore } from '../stores/app';
import type { DirectoryNode, TestPlan, PageResult } from '../types';

const store = useAppStore();
const dirs = ref<DirectoryNode[]>([]);
const selectedDir = ref<string | null>(null);
const plans = ref<PageResult<TestPlan>>({ records: [], total: 0, size: 20, current: 1, pages: 0 });
const keyword = ref('');
const onlyMine = ref(false);
const loading = ref(false);
const selectedPlans = ref<TestPlan[]>([]);
const { locks, run } = useGuard();
const siderCollapsed = ref(false);
const addingDir = ref(false);
const newDirName = ref('');
const addParentId = ref<string | null>(null);
const editingDirId = ref<string | null>(null);
const editingDirName = ref('');
const ctxMenu = ref({ visible: false, x: 0, y: 0, nodeId: null as string | null });

function dirToTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ id: d.id, label: d.name, parentId: d.parentId, children: d.children?.length ? dirToTree(d.children) : [] }));
}
const treeData = computed(() => dirToTree(dirs.value));
function flatDirs(list: DirectoryNode[]): DirectoryNode[] {
  const r: DirectoryNode[] = [];
  for (const d of list) { r.push(d); if (d.children?.length) r.push(...flatDirs(d.children)); }
  return r;
}

async function loadDirs() {
  if (!store.currentProject) return;
  dirs.value = (await directoryApi.tree(store.currentProject.id, 'TEST_PLAN')).data;
}
async function loadPlans(page = 1) {
  if (!store.currentProject) return;
  loading.value = true;
  try {
    plans.value = (await testPlanApi.list({
      projectId: store.currentProject.id, directoryId: selectedDir.value ?? undefined,
      keyword: keyword.value || undefined, onlyMine: onlyMine.value, page, size: 20,
    })).data;
  } finally { loading.value = false; }
}

watch(() => store.currentProject, () => { loadDirs(); loadPlans(); });
watch(selectedDir, () => loadPlans());
watch(onlyMine, () => loadPlans());
onMounted(() => { loadDirs(); loadPlans(); });

function toggleSider() { siderCollapsed.value = !siderCollapsed.value; }
function startAddRoot() { addingDir.value = true; addParentId.value = null; newDirName.value = ''; }
function startAdd(pid: string) { addingDir.value = true; addParentId.value = pid; newDirName.value = ''; ctxMenu.value.visible = false; }
async function confirmAdd() {
  const name = newDirName.value.trim();
  if (!name || !store.currentProject || !addingDir.value) { addingDir.value = false; return; }
  addingDir.value = false; newDirName.value = '';
  await run('confirmAdd', async () => {
    await directoryApi.create(name, addParentId.value, store.currentProject!.id, 'TEST_PLAN');
    loadDirs();
  });
}
function startAddSibling(id: string) {
  ctxMenu.value.visible = false;
  const dir = flatDirs(dirs.value).find(d => d.id === id);
  addingDir.value = true; addParentId.value = dir?.parentId || null; newDirName.value = '';
}
function startRenameDir(id: string) {
  ctxMenu.value.visible = false;
  const dir = flatDirs(dirs.value).find(d => d.id === id);
  editingDirId.value = id; editingDirName.value = dir?.name || '';
}
async function finishEditDir() {
  if (editingDirId.value && editingDirName.value.trim()) {
    await run('finishEditDir', async () => {
      await directoryApi.rename(editingDirId.value!, editingDirName.value.trim());
      loadDirs();
    });
  }
  editingDirId.value = null;
}
function cancelEditDir() { editingDirId.value = null; }
async function delDir(id: string) {
  ctxMenu.value.visible = false;
  await run('delDir', async () => {
    await directoryApi.delete(id); ElMessage.success('删除成功'); loadDirs();
  });
}
function showCtx(e: MouseEvent, id: string) { ctxMenu.value = { visible: true, x: e.clientX, y: e.clientY, nodeId: id }; }
onMounted(() => document.addEventListener('click', () => { ctxMenu.value.visible = false; }));
onUnmounted(() => document.removeEventListener('click', () => {}));

async function deletePlan(id: string) {
  await run('deletePlan', async () => {
    await testPlanApi.delete(id); ElMessage.success('已移入回收站'); loadPlans();
  });
}
async function batchDeletePlans() {
  const ids = selectedPlans.value.map(p => p.id);
  if (!ids.length) return;
  await run('batchDeletePlans', async () => {
    await testPlanApi.batchDelete(ids);
    ElMessage.success('批量删除成功'); selectedPlans.value = []; loadPlans();
  });
}
function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }

</script>

<style scoped>
.page-wrap { display: flex; height: 100%; overflow: hidden; }
.page-sidebar { width: 240px; flex-shrink: 0; background: #fff; display: flex; flex-direction: column; transition: width 0.25s ease; overflow: hidden; box-shadow: 2px 0 8px rgba(0,0,0,0.04); position: relative; z-index: 2; }
.page-sidebar.collapsed { width: 48px; }
.sidebar-header { display: flex; align-items: center; justify-content: space-between; padding: 0 12px; height: 52px; flex-shrink: 0; border-bottom: 1px solid #f0f2f5; }
.sidebar-title { font-size: 13px; font-weight: 600; color: #1f2329; }
.collapse-btn { color: #909399; }
.sidebar-body { flex: 1; display: flex; flex-direction: column; overflow: hidden; padding: 8px 0; }
.empty-tree { text-align: center; padding: 40px 16px; color: #909399; display: flex; flex-direction: column; align-items: center; gap: 8px; }
.sidebar-footer { padding: 12px 16px; border-top: 1px solid #f0f2f5; cursor: pointer; color: #909399; font-size: 13px; display: flex; align-items: center; gap: 8px; flex-shrink: 0; transition: all 0.15s; }
.sidebar-footer:hover { color: #1677ff; background: #f0f5ff; }
.tree-node { display: flex; align-items: center; gap: 6px; width: 100%; min-width: 0; }
.node-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: #1f2329; }
.add-child-btn { opacity: 0; transition: opacity 0.15s; flex-shrink: 0; }
.tree-node:hover .add-child-btn { opacity: 1; }
.node-input { border: 1px solid #409eff; border-radius: 4px; padding: 2px 6px; font-size: 13px; flex: 1; outline: none; }
.inline-add { padding: 4px 12px; }
.page-main { flex: 1; overflow: auto; background: #f0f2f5; padding: 20px; min-width: 0; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); min-height: 100%; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; }
.pagination-bar { margin-top: 16px; display: flex; justify-content: flex-end; }
.executor-text { display: inline-block; max-width: 130px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; }
.ctx-menu { position: fixed; background: #fff; border: 1px solid #ebedf0; border-radius: 8px; box-shadow: 0 6px 20px rgba(0,0,0,0.1); padding: 4px 0; z-index: 9999; min-width: 148px; }
.ctx-item { padding: 9px 16px; cursor: pointer; font-size: 13px; display: flex; align-items: center; gap: 8px; color: #1f2329; transition: background 0.15s; }
.ctx-item:hover { background: #f5f7fa; }
.ctx-item.danger { color: #f56c6c; }
</style>
