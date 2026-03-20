<template>
  <div class="page-wrap">
    <aside class="page-sidebar" :class="{ collapsed: siderCollapsed }">
      <div class="sidebar-header">
        <span v-if="!siderCollapsed" class="sidebar-title">场景目录</span>
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
    </aside>

    <main class="page-main">
      <div class="content-card">
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="搜索场景名称" style="width:180px"
            clearable :prefix-icon="Search" @keyup.enter="() => loadList(1)" />
          <el-button @click="() => loadList(1)">搜索</el-button>
          <div style="flex:1" />
          <el-button type="primary" :icon="Plus" @click="goCreate">新建场景</el-button>
        </div>

        <el-table :data="list" v-loading="loading" border style="width:100%;cursor:pointer" @row-click="goEdit">
          <el-table-column prop="name" label="场景名称" min-width="200" show-overflow-tooltip fixed="left" />
          <el-table-column label="步骤数" min-width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" round>{{ row.stepCount || 0 }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="失败策略" min-width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="row.failStrategy === 'STOP' ? 'danger' : 'success'" size="small">
                {{ row.failStrategy === 'STOP' ? '遇错停止' : '继续执行' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="标签" min-width="140">
            <template #default="{ row }">
              <el-tag v-for="t in (row.tags || [])" :key="t" size="small" type="info" round style="margin:0 4px 2px 0">{{ t }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdByName" label="创建人" min-width="90" show-overflow-tooltip />
          <el-table-column label="更新时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button text type="success" size="small" @click.stop="openRunDialog(row)">执行</el-button>
              <el-button text type="primary" size="small" @click.stop="goEdit(row)">编辑</el-button>
              <el-popconfirm title="删除将同时删除所有步骤，确认？" @confirm="doDelete(row.id)">
                <template #reference><el-button text type="danger" size="small" @click.stop>删除</el-button></template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination layout="total, prev, pager, next" :total="total"
            :page-size="20" :current-page="currentPage" @current-change="loadList" background />
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


    <!-- 执行场景弹窗 -->
    <el-dialog v-model="runDialog" title="执行场景" width="400px">
      <el-form label-width="80px">
        <el-form-item label="选择环境">
          <el-select v-model="runEnvId" style="width:100%" placeholder="请选择环境">
            <el-option v-for="e in envList" :key="e.id" :label="e.name + ' (' + e.baseUrl + ')'" :value="e.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="runDialog = false">取消</el-button>
        <el-button type="primary" :loading="running" @click="doRun">开始执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Plus, Search } from '@element-plus/icons-vue';
import { directoryApi, apiScenarioApi, apiEnvApi, apiExecApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { DirectoryNode, ApiEnv, ApiScenarioItem } from '../../types';

const router = useRouter();
const store = useAppStore();
const projectId = () => store.currentProject?.id || '';

// Directory
const dirs = ref<DirectoryNode[]>([]);
const selectedDir = ref<string | null>(null);
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
  if (!projectId()) return;
  dirs.value = (await directoryApi.tree(projectId(), 'API_SCENARIO')).data;
}
function toggleSider() { siderCollapsed.value = !siderCollapsed.value; }
function startAddRoot() { addingDir.value = true; addParentId.value = null; newDirName.value = ''; }
function startAdd(pid: string) { addingDir.value = true; addParentId.value = pid; newDirName.value = ''; ctxMenu.value.visible = false; }
async function confirmAdd() {
  const name = newDirName.value.trim();
  if (!name || !projectId() || !addingDir.value) { addingDir.value = false; return; }
  addingDir.value = false; newDirName.value = '';
  await directoryApi.create(name, addParentId.value, projectId(), 'API_SCENARIO');
  loadDirs();
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
    await directoryApi.rename(editingDirId.value, editingDirName.value.trim());
    loadDirs();
  }
  editingDirId.value = null;
}
function cancelEditDir() { editingDirId.value = null; }
async function delDir(id: string) {
  ctxMenu.value.visible = false;
  try { await directoryApi.delete(id); ElMessage.success('删除成功'); if (selectedDir.value === id) selectedDir.value = null; loadDirs(); } catch {}
}
function showCtx(e: MouseEvent, id: string) { ctxMenu.value = { visible: true, x: e.clientX, y: e.clientY, nodeId: id }; }
onMounted(() => document.addEventListener('click', () => { ctxMenu.value.visible = false; }));
onUnmounted(() => document.removeEventListener('click', () => {}));

// Scenario list
const list = ref<ApiScenarioItem[]>([]);
const total = ref(0);
const currentPage = ref(1);
const keyword = ref('');
const loading = ref(false);


function fmtTime(t?: string) { return t ? t.replace('T', ' ').substring(0, 16) : ''; }

async function loadList(page = 1) {
  if (!projectId()) return;
  loading.value = true;
  currentPage.value = page;
  try {
    const res = await apiScenarioApi.list({ projectId: projectId(), directoryId: selectedDir.value ?? undefined, keyword: keyword.value || undefined, page, size: 20 });
    list.value = res.data.records;
    total.value = res.data.total;
  } finally { loading.value = false; }
}

function goCreate() {
  router.push({ path: '/api-auto/scenario/create', query: { directoryId: selectedDir.value || undefined } });
}

function goEdit(row: ApiScenarioItem) {
  router.push('/api-auto/scenario/' + row.id);
}

async function doDelete(id: string) {
  try { await apiScenarioApi.delete(id); ElMessage.success('已删除'); loadList(); } catch {}
}

// Run
const runDialog = ref(false);
const running = ref(false);
const runEnvId = ref('');
const runScenarioId = ref('');
const envList = ref<ApiEnv[]>([]);

async function loadEnvs() {
  if (!projectId()) return;
  const res = await apiEnvApi.list(projectId());
  envList.value = res.data;
  if (envList.value.length && !runEnvId.value) runEnvId.value = envList.value[0].id;
}

function openRunDialog(row: ApiScenarioItem) {
  runScenarioId.value = row.id;
  loadEnvs();
  runDialog.value = true;
}

async function doRun() {
  if (!runEnvId.value) { ElMessage.warning('请选择环境'); return; }
  running.value = true;
  try {
    const res = await apiExecApi.runScenario(runScenarioId.value, runEnvId.value, projectId());
    ElMessage.success('已开始执行');
    runDialog.value = false;
    router.push('/api-auto/execution/' + res.data.executionId);
  } finally { running.value = false; }
}

watch(() => store.currentProject, () => { loadDirs(); loadList(); });
watch(selectedDir, () => loadList());
onMounted(() => { loadDirs(); loadList(); });
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
.tree-node { display: flex; align-items: center; gap: 6px; width: 100%; min-width: 0; }
.node-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: #1f2329; }
.add-child-btn { opacity: 0; transition: opacity 0.15s; flex-shrink: 0; }
.tree-node:hover .add-child-btn { opacity: 1; }
.node-input { border: 1px solid #409eff; border-radius: 4px; padding: 2px 6px; font-size: 13px; flex: 1; outline: none; }
.inline-add { padding: 4px 12px; }
.page-main { flex: 1; overflow: auto; background: #f0f2f5; padding: 20px; min-width: 0; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); min-height: 100%; }
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; flex-wrap: wrap; }
.pagination-bar { margin-top: 16px; display: flex; justify-content: flex-end; }
.ctx-menu { position: fixed; background: #fff; border: 1px solid #ebedf0; border-radius: 8px; box-shadow: 0 6px 20px rgba(0,0,0,0.1); padding: 4px 0; z-index: 9999; min-width: 148px; }
.ctx-item { padding: 9px 16px; cursor: pointer; font-size: 13px; display: flex; align-items: center; gap: 8px; color: #1f2329; transition: background 0.15s; }
.ctx-item:hover { background: #f5f7fa; }
.ctx-item.danger { color: #f56c6c; }
</style>
