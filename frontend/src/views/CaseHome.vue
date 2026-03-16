<template>
  <div class="page-wrap">
    <!-- ===== 侧边栏 ===== -->
    <aside class="page-sidebar" :class="{ collapsed: siderCollapsed }">
      <div class="sidebar-header">
        <span v-if="!siderCollapsed" class="sidebar-title">用例目录</span>
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
        <el-tree v-else ref="treeRef" :data="treeDataWithNew" node-key="id"
          :highlight-current="true" :current-node-key="selectedDir || undefined"
          default-expand-all :expand-on-click-node="false"
          @node-click="(d: any) => { if (!d._isNew) onSelectDir(d.id); }"
          @node-contextmenu="(e: MouseEvent, d: any) => { if (!d._isNew) showCtx(e, d.id); }">
          <template #default="{ data }">
            <div class="tree-node" v-if="!data._isNew">
              <el-icon size="13" color="#909399" style="flex-shrink:0"><Folder /></el-icon>
              <span v-if="editingDirId !== data.id" class="node-label">{{ data.label }}</span>
              <input v-else class="node-input" :value="editingDirName"
                @input="editingDirName = ($event.target as HTMLInputElement).value"
                @keyup.enter="finishEditDir" @blur="finishEditDir"
                @keyup.escape="cancelEditDir" @click.stop autofocus />
              <el-button v-if="editingDirId !== data.id" text size="small"
                class="add-child-btn" @click.stop="startAddChild(data.id)">
                <el-icon><Plus /></el-icon>
              </el-button>
            </div>
            <div class="tree-node" v-else>
              <el-icon size="13" color="#1677ff" style="flex-shrink:0"><FolderAdd /></el-icon>
              <input class="node-input" v-model="newDirName"
                @keyup.enter="confirmAdd" @blur="confirmAdd"
                @keyup.escape="cancelAdd" @click.stop placeholder="输入名称回车保存" autofocus />
            </div>
          </template>
        </el-tree>
      </div>
      <div v-if="!siderCollapsed" class="sidebar-footer" @click="$router.push('/recycle-bin')">
        <el-icon color="#909399"><Delete /></el-icon>
        <span>用例回收站</span>
      </div>
    </aside>

    <!-- ===== 主内容 ===== -->
    <main class="page-main">
      <div class="content-card">
        <!-- 工具栏 -->
        <div class="toolbar">
          <el-input v-model="keyword" placeholder="搜索用例集名称" style="width:240px"
            clearable :prefix-icon="Search" />
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width:130px">
            <el-option value="WRITING" label="编写中" />
            <el-option value="PENDING_REVIEW" label="待评审" />
            <el-option value="NO_REVIEW" label="无需评审" />
            <el-option value="APPROVED" label="审核通过" />
          </el-select>
          <el-button @click="() => loadCases(1)">搜索</el-button>
          <div style="flex:1" />
          <el-button :icon="Upload" @click="showImport = true">导入</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreateCase">新建用例集</el-button>
        </div>

        <!-- 表格 -->
        <el-table :data="caseData.records" v-loading="loading" border style="width:100%">
          <el-table-column label="用例集名称" min-width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <a class="tbl-link" @click="$router.push(`/mind-map/${row.id}`)">{{ row.name }}</a>
            </template>
          </el-table-column>
          <el-table-column prop="caseCount" label="用例数" min-width="80" align="center" />
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="关联需求" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">
              <a v-if="row.requirementLink" :href="row.requirementLink" target="_blank"
                class="tbl-link" @click.stop>关联需求</a>
              <span v-else style="color:#c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="创建人" prop="createdByName" min-width="90" show-overflow-tooltip />
          <el-table-column label="创建时间" min-width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="150">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="$router.push(`/review/${row.id}`)">评审</el-button>
              <el-button text type="primary" size="small" @click="openEditCase(row)">编辑</el-button>
              <el-dropdown trigger="click" @command="(k: string) => handleCaseAction(k, row)">
                <el-button text size="small" style="padding:0 4px">
                  <el-icon><MoreFilled /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="copy"><el-icon><DocumentCopy /></el-icon>复制</el-dropdown-item>
                    <el-dropdown-item command="move"><el-icon><Sort /></el-icon>移动</el-dropdown-item>
                    <el-dropdown-item command="delete" style="color:#f56c6c">
                      <el-icon><Delete /></el-icon>删除
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination-bar">
          <el-pagination layout="total, prev, pager, next" :total="caseData.total"
            :page-size="20" :current-page="caseData.current"
            @current-change="(p: number) => loadCases(p)" background />
        </div>
      </div>
    </main>

    <!-- 右键菜单 -->
    <div v-if="ctxMenu.visible" class="ctx-menu"
      :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
      <div class="ctx-item" @click="startAddChild(ctxMenu.nodeId!)"><el-icon><FolderAdd /></el-icon>新建子目录</div>
      <div class="ctx-item" @click="startAddSibling(ctxMenu.nodeId!)"><el-icon><Plus /></el-icon>新建同级目录</div>
      <div class="ctx-item" @click="startRenameDir(ctxMenu.nodeId!)"><el-icon><Edit /></el-icon>重命名</div>
      <div class="ctx-item danger" @click="deleteDir(ctxMenu.nodeId!)"><el-icon><Delete /></el-icon>删除</div>
    </div>

    <!-- 新建用例集 -->
    <el-dialog v-model="showCreateCase" title="新建用例集" width="480px" @closed="resetCreateForm">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="newCaseName" placeholder="输入用例集名称" />
        </el-form-item>
        <el-form-item label="所属目录" required>
          <el-tree-select v-model="newCaseDirId" :data="dirSelectData"
            :render-after-expand="false" default-expand-all check-strictly
            clearable placeholder="选择目录" style="width:100%" />
        </el-form-item>
        <el-form-item label="关联需求">
          <el-input v-model="newCaseLink" placeholder="需求链接（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateCase = false">取消</el-button>
        <el-button type="primary" @click="createCase">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入 -->
    <el-dialog v-model="showImport" title="导入用例" width="480px">
      <p style="color:#909399;margin-bottom:16px;font-size:13px">
        请上传 Excel (.xlsx)，表头须含：用例标题、前置条件、步骤、预期结果
      </p>
      <el-upload drag accept=".xlsx" :limit="1" :before-upload="handleImport" action="">
        <el-icon size="40" color="#1677ff"><Upload /></el-icon>
        <div style="margin-top:8px;font-size:14px">点击或拖拽上传</div>
      </el-upload>
    </el-dialog>

    <!-- 移动 -->
    <el-dialog v-model="showMove" title="移动到目录" width="400px">
      <el-tree :data="treeData" node-key="id" highlight-current default-expand-all
        :expand-on-click-node="false" @node-click="(d: any) => moveTarget = d.id" />
      <template #footer>
        <el-button @click="showMove = false">取消</el-button>
        <el-button type="primary" @click="confirmMove">确定</el-button>
      </template>
    </el-dialog>

    <!-- 复制 -->
    <el-dialog v-model="showCopy" title="复制到目录" width="400px">
      <el-tree :data="treeData" node-key="id" highlight-current default-expand-all
        :expand-on-click-node="false" @node-click="(d: any) => copyTarget = d.id" />
      <template #footer>
        <el-button @click="showCopy = false">取消</el-button>
        <el-button type="primary" @click="confirmCopy">确定</el-button>
      </template>
    </el-dialog>

    <!-- 编辑用例集 -->
    <el-dialog v-model="editCaseDlg.visible" title="编辑用例集" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="editCaseDlg.name" />
        </el-form-item>
        <el-form-item label="关联需求">
          <el-input v-model="editCaseDlg.requirementLink" placeholder="需求链接（可选）" />
        </el-form-item>
        <el-form-item label="所属目录">
          <el-tree-select v-model="editCaseDlg.directoryId" :data="dirSelectData"
            :render-after-expand="false" default-expand-all check-strictly
            clearable placeholder="选择目录" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editCaseDlg.visible = false">取消</el-button>
        <el-button type="primary" :loading="editCaseDlg.saving" @click="saveEditCase">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search, Upload, Plus } from '@element-plus/icons-vue';
import { directoryApi, caseSetApi } from '../api';
import { useAppStore } from '../stores/app';
import type { DirectoryNode, CaseSet, PageResult } from '../types';

const router = useRouter();
const store = useAppStore();
const treeRef = ref();

const dirs = ref<DirectoryNode[]>([]);
const selectedDir = ref<string | null>(null);
const caseData = ref<PageResult<CaseSet>>({ records: [], total: 0, size: 20, current: 1, pages: 0 });
const keyword = ref('');
const statusFilter = ref<string | undefined>();
const loading = ref(false);
const siderCollapsed = ref(false);
const editingDirId = ref<string | null>(null);
const editingDirName = ref('');
const newDirName = ref('');
const addingDir = ref(false);
const addParentId = ref<string | null>(null);
const ctxMenu = ref({ visible: false, x: 0, y: 0, nodeId: null as string | null });
const showCreateCase = ref(false);
const showImport = ref(false);
const showMove = ref(false);
const showCopy = ref(false);
const newCaseName = ref('');
const newCaseLink = ref('');
const newCaseDirId = ref<string | null>(null);
const movingId = ref<string | null>(null);
const moveTarget = ref<string | null>(null);
const copyingId = ref<string | null>(null);
const copyTarget = ref<string | null>(null);

function dirToTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ id: d.id, label: d.name, parentId: d.parentId, children: d.children?.length ? dirToTree(d.children) : [] }));
}
function dirToSelectTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ value: d.id, label: d.name, children: d.children?.length ? dirToSelectTree(d.children) : undefined }));
}
const treeData = computed(() => dirToTree(dirs.value));
const dirSelectData = computed(() => dirToSelectTree(dirs.value));

const treeDataWithNew = computed(() => {
  const base = JSON.parse(JSON.stringify(treeData.value));
  if (!addingDir.value) return base;
  const newNode = { id: '__new__', label: '', _isNew: true, children: [] };
  if (!addParentId.value) {
    base.push(newNode);
  } else {
    function inject(nodes: any[]): boolean {
      for (const n of nodes) {
        if (n.id === addParentId.value) { n.children.push(newNode); return true; }
        if (n.children?.length && inject(n.children)) return true;
      }
      return false;
    }
    inject(base);
  }
  return base;
});

function flatDirs(list: DirectoryNode[]): DirectoryNode[] {
  const r: DirectoryNode[] = [];
  for (const d of list) { r.push(d); if (d.children?.length) r.push(...flatDirs(d.children)); }
  return r;
}

async function loadDirs() {
  if (!store.currentProject) return;
  dirs.value = (await directoryApi.tree(store.currentProject.id, 'CASE')).data;
}
async function loadCases(page = 1) {
  if (!store.currentProject) return;
  loading.value = true;
  try {
    caseData.value = (await caseSetApi.list({
      directoryId: selectedDir.value ?? undefined, projectId: store.currentProject.id,
      keyword: keyword.value || undefined, status: statusFilter.value, page, size: 20,
    })).data;
  } finally { loading.value = false; }
}

watch(() => store.currentProject, () => { loadDirs(); loadCases(); });
watch(selectedDir, () => loadCases());
onMounted(() => { loadDirs(); loadCases(); });

function toggleSider() { siderCollapsed.value = !siderCollapsed.value; }
function onSelectDir(id: string) { selectedDir.value = id; }

function startAddRoot() { addingDir.value = true; addParentId.value = null; newDirName.value = ''; }
function startAddChild(pid: string) { addingDir.value = true; addParentId.value = pid; newDirName.value = ''; ctxMenu.value.visible = false; }
async function confirmAdd() {
  const name = newDirName.value.trim();
  if (!name || !store.currentProject || !addingDir.value) { addingDir.value = false; return; }
  addingDir.value = false; newDirName.value = '';
  await directoryApi.create(name, addParentId.value, store.currentProject.id, 'CASE');
  loadDirs();
}
function cancelAdd() { addingDir.value = false; newDirName.value = ''; }
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
  if (editingDirId.value && editingDirName.value.trim()) { await directoryApi.rename(editingDirId.value, editingDirName.value.trim()); loadDirs(); }
  editingDirId.value = null;
}
function cancelEditDir() { editingDirId.value = null; }
async function deleteDir(id: string) {
  ctxMenu.value.visible = false;
  try { await directoryApi.delete(id); ElMessage.success('删除成功'); if (selectedDir.value === id) selectedDir.value = null; loadDirs(); } catch {}
}
function showCtx(e: MouseEvent, id: string) { ctxMenu.value = { visible: true, x: e.clientX, y: e.clientY, nodeId: id }; }
onMounted(() => document.addEventListener('click', () => { ctxMenu.value.visible = false; }));
onUnmounted(() => document.removeEventListener('click', () => {}));

function openCreateCase() { newCaseDirId.value = selectedDir.value || null; showCreateCase.value = true; }
function resetCreateForm() { newCaseName.value = ''; newCaseLink.value = ''; newCaseDirId.value = null; }

async function createCase() {
  if (!newCaseName.value.trim() || !store.currentProject) return;
  const targetDir = newCaseDirId.value || dirs.value[0]?.id;
  if (!targetDir) { ElMessage.error('请先选择一个目录'); return; }
  const res = await caseSetApi.create({ name: newCaseName.value, directoryId: targetDir, projectId: store.currentProject.id, requirementLink: newCaseLink.value });
  showCreateCase.value = false;
  router.push(`/mind-map/${res.data.id}`);
}

const editCaseDlg = ref({ visible: false, saving: false, id: '', name: '', requirementLink: '', directoryId: '' });
function openEditCase(row: CaseSet) {
  editCaseDlg.value = { visible: true, saving: false, id: row.id, name: row.name, requirementLink: (row as any).requirementLink || '', directoryId: row.directoryId };
}
async function saveEditCase() {
  const d = editCaseDlg.value;
  if (!d.name.trim()) { ElMessage.error('名称不能为空'); return; }
  d.saving = true;
  try {
    await caseSetApi.rename(d.id, d.name.trim());
    await caseSetApi.updateRequirement(d.id, d.requirementLink);
    if (d.directoryId) await caseSetApi.move(d.id, d.directoryId);
    ElMessage.success('更新成功');
    d.visible = false;
    loadCases();
  } finally { d.saving = false; }
}

async function handleCaseAction(key: string, record: CaseSet) {
  if (key === 'copy') { copyingId.value = record.id; copyTarget.value = record.directoryId; showCopy.value = true; }
  if (key === 'move') { movingId.value = record.id; showMove.value = true; }
  if (key === 'delete') {
    await ElMessageBox.confirm('确认删除？将移入回收站', '提示', { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' });
    await caseSetApi.delete(record.id);
    ElMessage.success('已移入回收站'); loadCases();
  }
}
async function confirmCopy() {
  if (copyingId.value && copyTarget.value) { await caseSetApi.copy(copyingId.value, copyTarget.value); ElMessage.success('复制成功'); showCopy.value = false; loadCases(); }
}
async function confirmMove() {
  if (movingId.value && moveTarget.value) { await caseSetApi.move(movingId.value, moveTarget.value); ElMessage.success('移动成功'); showMove.value = false; loadCases(); }
}
async function handleImport(file: File) {
  if (!store.currentProject) return false;
  const targetDir = selectedDir.value || dirs.value[0]?.id;
  if (!targetDir) { ElMessage.error('请先创建目录'); return false; }
  await caseSetApi.importExcel(file, targetDir, store.currentProject.id);
  ElMessage.success('导入成功'); showImport.value = false; loadCases(); return false;
}

function statusLabel(s: string) { return ({ WRITING: '编写中', PENDING_REVIEW: '待评审', NO_REVIEW: '无需评审', APPROVED: '审核通过' } as any)[s] || s; }
function statusType(s: string): any { return ({ WRITING: 'primary', PENDING_REVIEW: 'warning', NO_REVIEW: 'info', APPROVED: 'success' } as any)[s] || 'info'; }
function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }

</script>

<style scoped>
.page-wrap { display: flex; height: 100%; overflow: hidden; }

/* 侧边栏 */
.page-sidebar {
  width: 240px; flex-shrink: 0;
  background: #fff;
  display: flex; flex-direction: column;
  transition: width 0.25s ease;
  overflow: hidden;
  /* 用右侧阴影代替边框，视觉上与主内容有层次感 */
  box-shadow: 2px 0 8px rgba(0,0,0,0.04);
  position: relative; z-index: 2;
}
.page-sidebar.collapsed { width: 48px; }

.sidebar-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 12px; height: 52px; flex-shrink: 0;
  border-bottom: 1px solid #f0f2f5;
}
.sidebar-title { font-size: 13px; font-weight: 600; color: #1f2329; }
.collapse-btn { color: #909399; }

.sidebar-body { flex: 1; display: flex; flex-direction: column; overflow: hidden; padding: 8px 0; }
.empty-tree { text-align: center; padding: 40px 16px; color: #909399; display: flex; flex-direction: column; align-items: center; gap: 8px; }

.sidebar-footer {
  padding: 12px 16px; border-top: 1px solid #f0f2f5;
  cursor: pointer; color: #909399; font-size: 13px;
  display: flex; align-items: center; gap: 8px;
  flex-shrink: 0; transition: all 0.15s;
}
.sidebar-footer:hover { color: #1677ff; background: #f0f5ff; }

/* 树节点 */
.tree-node { display: flex; align-items: center; gap: 6px; width: 100%; min-width: 0; }
.node-label { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; color: #1f2329; }
.add-child-btn { opacity: 0; transition: opacity 0.15s; flex-shrink: 0; }
.tree-node:hover .add-child-btn { opacity: 1; }
.node-input { border: 1px solid #409eff; border-radius: 4px; padding: 2px 6px; font-size: 13px; flex: 1; outline: none; width: 40px;}
.inline-add { padding: 4px 12px; }

/* 主内容 */
.page-main {
  flex: 1; overflow: auto; background: #f0f2f5; padding: 20px;
  min-width: 0;
}
.content-card {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  min-height: calc(100% - 0px);
}

/* 工具栏 */
.toolbar { display: flex; gap: 10px; margin-bottom: 16px; align-items: center; flex-wrap: wrap; }

/* 表格链接 */
.tbl-link { color: #1677ff; cursor: pointer; transition: color 0.2s; text-decoration: none;}
.tbl-link:hover { color: #4096ff; }

/* 分页 */
.pagination-bar { margin-top: 16px; display: flex; justify-content: flex-end; }

/* 右键菜单 */
.ctx-menu {
  position: fixed; background: #fff; border: 1px solid #ebedf0;
  border-radius: 8px; box-shadow: 0 6px 20px rgba(0,0,0,0.1);
  padding: 4px 0; z-index: 9999; min-width: 148px;
}
.ctx-item {
  padding: 9px 16px; cursor: pointer; font-size: 13px;
  display: flex; align-items: center; gap: 8px; color: #1f2329;
  transition: background 0.15s;
}
.ctx-item:hover { background: #f5f7fa; }
.ctx-item.danger { color: #f56c6c; }
</style>
