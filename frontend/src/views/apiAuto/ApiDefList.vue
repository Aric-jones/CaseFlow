<template>
  <div class="page-wrap">
    <aside class="page-sidebar" :class="{ collapsed: siderCollapsed }">
      <div class="sidebar-header">
        <span v-if="!siderCollapsed" class="sidebar-title">接口目录</span>
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
          :highlight-current="true" :current-node-key="selectedDirId || undefined"
          default-expand-all :expand-on-click-node="false"
          @node-click="(d: any) => { selectedDirId = d.id; }"
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
          <el-input v-model="keyword" placeholder="搜索接口名称/路径" style="width:220px"
            clearable :prefix-icon="Search" @keyup.enter="() => loadDefs(1)" />
          <el-select v-model="filterMethod" placeholder="Method" clearable style="width:110px" @change="() => loadDefs(1)">
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
          <el-select v-model="filterTag" placeholder="标签筛选" clearable style="width:140px" @change="() => loadDefs(1)">
            <el-option v-for="t in allTags" :key="t" :label="t" :value="t" />
          </el-select>
          <el-button @click="() => loadDefs(1)">搜索</el-button>
          <div style="flex:1" />
          <el-button v-if="selectedRows.length && canDelete" type="danger" @click="batchDeleteDefs">
            批量删除 ({{ selectedRows.length }})
          </el-button>
          <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openCreateDef">新建接口</el-button>
        </div>

        <el-table :data="defList" v-loading="loading" border style="width:100%" @row-click="onRowClick" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" />
          <el-table-column label="Method" width="120" align="center" fixed="left">
            <template #default="{ row }">
              <el-tag :type="methodColor(row.method)" size="small" effect="dark" style="font-weight:700;min-width:52px;text-align:center">{{ row.method }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="name" label="接口名称" min-width="200" show-overflow-tooltip />
          <el-table-column prop="path" label="路径" min-width="240" show-overflow-tooltip>
            <template #default="{ row }"><code style="font-size:12px;color:#606266">{{ row.path }}</code></template>
          </el-table-column>
          <el-table-column label="标签" min-width="160">
            <template #default="{ row }">
              <el-tag v-for="tag in (row.tags || [])" :key="tag" size="small" type="info" round style="margin:0 4px 2px 0">{{ tag }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="caseCount" label="用例数" min-width="80" align="center" />
          <el-table-column prop="createdByName" label="创建人" min-width="90" show-overflow-tooltip />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button text type="success" size="small" @click.stop="openDebugDialog(row)">调试</el-button>
              <el-button v-if="canDelete" text type="danger" size="small" @click.stop="confirmDeleteDef(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-bar">
          <el-pagination layout="total, prev, pager, next" :total="total"
            :page-size="20" :current-page="currentPage" @current-change="loadDefs" background />
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

    <!-- 新建接口弹窗 -->
    <el-dialog v-model="createDefDialog" title="新建接口" width="560px" destroy-on-close>
      <el-form :model="newDef" label-width="90px">
        <el-form-item label="接口名称" required>
          <el-input v-model="newDef.name" placeholder="如：用户登录" />
        </el-form-item>
        <el-form-item label="请求方法" required>
          <el-select v-model="newDef.method" style="width:100%">
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="路径" required>
          <el-input v-model="newDef.path" placeholder="/api/auth/login" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="newDef.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="newDef.tags" multiple filterable allow-create default-first-option
            placeholder="输入后回车创建" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDefDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doCreateDef">创建</el-button>
      </template>
    </el-dialog>

    <!-- 调试全部用例弹窗 -->
    <el-dialog v-model="debugDialog" title="调试全部用例" width="400px">
      <el-form label-width="80px">
        <el-form-item label="接口">{{ debugDefName }}</el-form-item>
        <el-form-item label="选择环境">
          <el-select v-model="debugEnvId" style="width:100%" placeholder="请选择环境">
            <el-option v-for="e in debugEnvList" :key="e.id" :label="e.name + ' (' + e.baseUrl + ')'" :value="e.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="debugDialog = false">取消</el-button>
        <el-button type="primary" :loading="debugRunning" @click="doDebugAll">执行调试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Search } from '@element-plus/icons-vue';
import { directoryApi, apiDefApi, apiEnvApi, apiCaseApi, apiExecApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { ApiDef, DirectoryNode } from '../../types';

const router = useRouter();
const store = useAppStore();
const canDelete = computed(() => store.hasPermission('api:def:delete'));
const canCreate = computed(() => store.hasPermission('api:def:create'));
const projectId = () => store.currentProject?.id || '';

// Directory
const dirs = ref<DirectoryNode[]>([]);
const selectedDirId = ref<string | null>(null);
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

async function loadDirTree() {
  if (!projectId()) return;
  dirs.value = (await directoryApi.tree(projectId(), 'API')).data;
}
function toggleSider() { siderCollapsed.value = !siderCollapsed.value; }
function startAddRoot() { addingDir.value = true; addParentId.value = null; newDirName.value = ''; }
function startAdd(pid: string) { addingDir.value = true; addParentId.value = pid; newDirName.value = ''; ctxMenu.value.visible = false; }
async function confirmAdd() {
  const name = newDirName.value.trim();
  if (!name || !projectId() || !addingDir.value) { addingDir.value = false; return; }
  addingDir.value = false; newDirName.value = '';
  await directoryApi.create(name, addParentId.value, projectId(), 'API');
  loadDirTree();
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
    loadDirTree();
  }
  editingDirId.value = null;
}
function cancelEditDir() { editingDirId.value = null; }
async function delDir(id: string) {
  ctxMenu.value.visible = false;
  try { await directoryApi.delete(id); ElMessage.success('删除成功'); if (selectedDirId.value === id) selectedDirId.value = null; loadDirTree(); loadDefs(); } catch {}
}
function showCtx(e: MouseEvent, id: string) { ctxMenu.value = { visible: true, x: e.clientX, y: e.clientY, nodeId: id }; }
onMounted(() => document.addEventListener('click', () => { ctxMenu.value.visible = false; }));
onUnmounted(() => document.removeEventListener('click', () => {}));

// Def list
const defList = ref<ApiDef[]>([]);
const total = ref(0);
const currentPage = ref(1);
const keyword = ref('');
const filterMethod = ref('');
const filterTag = ref('');
const allTags = ref<string[]>([]);
const loading = ref(false);
const methods = ['GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS'];

const createDefDialog = ref(false);
const saving = ref(false);
const selectedRows = ref<ApiDef[]>([]);
const debugDialog = ref(false);
const debugDefId = ref('');
const debugDefName = ref('');
const debugEnvId = ref('');
const debugEnvList = ref<any[]>([]);
const debugRunning = ref(false);
const newDef = ref<Partial<ApiDef>>({});

function handleSelectionChange(rows: ApiDef[]) {
  selectedRows.value = rows;
}

async function batchDeleteDefs() {
  if (!selectedRows.value.length) { ElMessage.warning('请先选择要删除的接口'); return; }
  const withCases = selectedRows.value.filter(r => (r.caseCount || 0) > 0);
  if (withCases.length) {
    ElMessage.warning(`选中的接口中有 ${withCases.length} 个含有用例（${withCases.map(r => r.name).join('、')}），请先删除用例`);
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除 ${selectedRows.value.length} 个接口？删除后可在回收站恢复`, '批量删除确认', { type: 'warning' });
    const ids = selectedRows.value.map(r => r.id);
    await apiDefApi.batchDelete(ids);
    ElMessage.success(`成功删除 ${ids.length} 个接口`);
    selectedRows.value = [];
    loadDefs();
  } catch (e: any) {
    if (e !== 'cancel' && e?.message !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '删除失败');
    }
  }
}

async function openDebugDialog(row: ApiDef) {
  debugDefId.value = row.id;
  debugDefName.value = row.name;
  if (!debugEnvList.value.length && projectId()) {
    const res = await apiEnvApi.list(projectId());
    debugEnvList.value = res.data;
    if (debugEnvList.value.length && !debugEnvId.value) debugEnvId.value = debugEnvList.value[0].id;
  }
  debugDialog.value = true;
}

async function doDebugAll() {
  if (!debugEnvId.value) { ElMessage.warning('请选择环境'); return; }
  debugRunning.value = true;
  try {
    const casesRes = await apiCaseApi.list(debugDefId.value);
    const enabledCases = casesRes.data.filter((c: any) => c.enabled !== 0);
    if (!enabledCases.length) { ElMessage.warning('该接口下没有可用的用例'); return; }
    let pass = 0, fail = 0;
    for (const c of enabledCases) {
      try {
        const r = await apiExecApi.debug(c.id, debugEnvId.value);
        if (r.data.status === 'PASS') pass++; else fail++;
      } catch { fail++; }
    }
    ElMessage.success(`调试完成：${pass} 通过，${fail} 失败`);
    debugDialog.value = false;
  } catch { ElMessage.error('调试失败'); }
  finally { debugRunning.value = false; }
}

function methodColor(m: string): any {
  return ({ GET: 'success', POST: 'primary', PUT: 'warning', DELETE: 'danger', PATCH: '' } as any)[m] || 'info';
}

async function loadDefs(page = 1) {
  if (!projectId()) return;
  loading.value = true;
  currentPage.value = page;
  try {
    const res = await apiDefApi.list({
      projectId: projectId(), directoryId: selectedDirId.value ?? undefined,
      keyword: keyword.value || undefined, method: filterMethod.value || undefined,
      tag: filterTag.value || undefined, page, size: 20,
    });
    defList.value = res.data.records;
    total.value = res.data.total;
  } finally { loading.value = false; }
}

async function loadTags() {
  if (!projectId()) return;
  try { allTags.value = (await apiDefApi.tags(projectId())).data; } catch {}
}

function openCreateDef() {
  newDef.value = { projectId: projectId(), directoryId: selectedDirId.value ?? undefined, method: 'GET', name: '', path: '', tags: [] };
  createDefDialog.value = true;
}

async function doCreateDef() {
  if (!newDef.value.name?.trim()) { ElMessage.warning('请输入接口名称'); return; }
  if (!newDef.value.path?.trim()) { ElMessage.warning('请输入路径'); return; }
  saving.value = true;
  try {
    await apiDefApi.create(newDef.value);
    ElMessage.success('创建成功');
    createDefDialog.value = false;
    loadDefs(); loadTags();
  } finally { saving.value = false; }
}

async function confirmDeleteDef(row: ApiDef) {
  const count = row.caseCount || 0;
  if (count > 0) {
    ElMessage.warning(`该接口下有 ${count} 个用例，请先删除用例后再删除接口`);
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该接口？删除后可在回收站恢复', '删除确认', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' });
    await apiDefApi.delete(row.id);
    ElMessage.success('已移入回收站');
    loadDefs();
  } catch {}
}

function onRowClick(row: ApiDef, column: any) {
  if (column?.type === 'selection') return;
  router.push(`/api-auto/def/${row.id}`);
}

watch(() => store.currentProject, () => { loadDirTree(); loadDefs(); loadTags(); });
watch(selectedDirId, () => loadDefs());
onMounted(() => { loadDirTree(); loadDefs(); loadTags(); });
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
