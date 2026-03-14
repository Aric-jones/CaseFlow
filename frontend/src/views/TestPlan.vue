<template>
  <a-layout style="height: 100%; background: #fff">
    <a-layout-sider
      width="260"
      :collapsed-width="0"
      :collapsed="siderCollapsed"
      collapsible
      style="background: #fff; border-right: 1px solid #f0f0f0; overflow: auto; padding: 12px 8px"
      :trigger="null"
    >
      <div style="display: flex; justify-content: space-between; padding: 0 8px 12px">
        <a-space :size="6">
          <a-button size="small" type="text" @click="toggleSider">☰</a-button>
          <strong>测试计划目录</strong>
        </a-space>
      </div>
      <a-tree v-if="treeData.length" :tree-data="treeData" :selected-keys="selectedDir ? [selectedDir] : []"
              @select="(keys: any) => selectedDir = keys[0] || null" default-expand-all block-node>
        <template #title="{ key: nodeKey, title }">
          <div class="dir-tree-item" @contextmenu.prevent="(e: MouseEvent) => showCtx(e, String(nodeKey))">
            <span v-if="editingDirId !== String(nodeKey)">{{ title }}</span>
            <input v-else class="inline-input" :value="editingDirName"
              @input="editingDirName = ($event.target as HTMLInputElement).value"
              @keyup.enter="finishEditDir" @blur="finishEditDir" @keyup.escape="cancelEditDir" autofocus />
            <span class="actions" v-if="editingDirId !== String(nodeKey)">
              <a-button size="small" type="text" @click.stop="startAdd(String(nodeKey))"><PlusOutlined /></a-button>
            </span>
          </div>
        </template>
      </a-tree>
      <div v-else style="text-align: center; padding: 40px 0; color: #999">
        <p>暂无目录</p><a-button type="link" @click="startAddRoot">创建根目录</a-button>
      </div>
      <div v-if="addingDir" style="padding: 4px 16px">
        <input class="inline-input" v-model="newDirName" @keyup.enter="confirmAdd" @blur="confirmAdd" @keyup.escape="addingDir = false" placeholder="回车保存" autofocus />
      </div>
      <!-- 回收站入口 -->
      <div style="padding: 12px 16px; border-top: 1px solid #f0f0f0; margin-top: 8px; cursor: pointer; color: #8c8c8c"
        @click="$router.push('/test-plan-recycle-bin')">
        <DeleteOutlined /> 测试计划回收站
      </div>
    </a-layout-sider>

    <a-layout-content style="padding: 24px">
      <div v-if="siderCollapsed" style="margin-bottom: 12px">
        <a-button size="small" @click="toggleSider">展开目录</a-button>
      </div>
      <div style="display: flex; gap: 12px; margin-bottom: 16px; align-items: center">
        <a-input-search v-model:value="keyword" placeholder="搜索" style="width: 280px" @search="() => loadPlans(1)" />
        <a-checkbox v-model:checked="onlyMine">只看我的</a-checkbox>
        <div style="flex: 1" />
        <a-button type="primary" @click="$router.push('/test-plan/create')"><PlusOutlined /> 新建测试计划</a-button>
      </div>
      <a-table :columns="columns" :data-source="plans.records" row-key="id" :loading="loading"
        :pagination="{ current: plans.current, total: plans.total, pageSize: 20, onChange: loadPlans }" size="middle"
        @resizeColumn="handleResizeColumn">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'progress'">
            <div style="display:flex; align-items:center; gap:8px; min-width:120px">
              <a-progress :percent="record.caseTotal ? Math.round(record.caseExecuted / record.caseTotal * 100) : 0"
                :stroke-color="'#52c41a'" size="small" style="flex:1;min-width:60px" :show-info="false" />
              <span style="font-size:12px; color:#666; white-space:nowrap">{{ record.caseExecuted || 0 }}/{{ record.caseTotal || 0 }}</span>
            </div>
          </template>
          <template v-if="column.key === 'executors'">
            <a-tooltip v-if="record.executorNames?.length" :title="record.executorNames.join('、')">
              <span class="executor-cell">{{ record.executorNames.join('、') }}</span>
            </a-tooltip>
            <span v-else style="color:#ccc">未分配</span>
          </template>
          <template v-if="column.key === 'createdAt'">{{ fmtTime(record.createdAt) }}</template>
          <template v-if="column.key === 'action'">
            <a-space :size="0">
              <a-button type="link" size="small" @click="$router.push(`/test-plan/${record.id}/execute`)">执行</a-button>
              <a-button type="link" size="small" @click="$router.push(`/test-plan/${record.id}/edit`)">编辑</a-button>
              <a-popconfirm title="确认删除？将移入回收站" @confirm="deletePlan(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-layout-content>


    <div v-if="ctxMenu.visible" class="context-menu" :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }">
      <div class="ctx-item" @click="startAdd(ctxMenu.nodeId!)"><PlusOutlined /> 新建子目录</div>
      <div class="ctx-item" @click="startAddSibling(ctxMenu.nodeId!)"><PlusOutlined /> 新建同级目录</div>
      <div class="ctx-item" @click="startRenameDir(ctxMenu.nodeId!)"><EditOutlined /> 重命名</div>
      <div class="ctx-item danger" @click="delDir(ctxMenu.nodeId!)"><DeleteOutlined /> 删除</div>
    </div>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons-vue';
import { directoryApi, testPlanApi } from '../api';
import { useAppStore } from '../stores/app';
import { useResizableColumns } from '../composables/useResizableColumns';
import type { DirectoryNode, TestPlan, PageResult } from '../types';

const store = useAppStore();

// ── 状态 ──────────────────────────────────────────────────────
const dirs = ref<DirectoryNode[]>([]);
const selectedDir = ref<string | null>(null);
const plans = ref<PageResult<TestPlan>>({ records: [], total: 0, size: 20, current: 1, pages: 0 });
const keyword = ref('');
const onlyMine = ref(false);
const loading = ref(false);
const addingDir = ref(false);
const newDirName = ref('');
const addParentId = ref<string | null>(null);
const editingDirId = ref<string | null>(null);
const editingDirName = ref('');
const siderCollapsed = ref(false);
const ctxMenu = ref({ visible: false, x: 0, y: 0, nodeId: null as string | null });

// ── 目录树 ────────────────────────────────────────────────────
/** 将目录列表转为 a-tree 数据格式 */
function dirToTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ key: d.id, title: d.name, children: d.children?.length ? dirToTree(d.children) : [] }));
}
const treeData = ref<any[]>([]);

/** 递归展平目录树 */
function flatDirs(list: DirectoryNode[]): DirectoryNode[] {
  const r: DirectoryNode[] = [];
  for (const d of list) { r.push(d); if (d.children?.length) r.push(...flatDirs(d.children)); }
  return r;
}

// ── 数据加载 ──────────────────────────────────────────────────
/** 加载目录树 */
async function loadDirs() {
  if (!store.currentProject) return;
  dirs.value = (await directoryApi.tree(store.currentProject.id, 'TEST_PLAN')).data;
  treeData.value = dirToTree(dirs.value);
}

/** 加载测试计划列表（分页） */
async function loadPlans(page = 1) {
  if (!store.currentProject) return;
  loading.value = true;
  try {
    plans.value = (await testPlanApi.list({ projectId: store.currentProject.id, directoryId: selectedDir.value ?? undefined, keyword: keyword.value || undefined, onlyMine: onlyMine.value, page, size: 20 })).data;
  } finally { loading.value = false; }
}

watch(() => store.currentProject, () => { loadDirs(); loadPlans(); });
watch(selectedDir, () => loadPlans());
watch(onlyMine, () => loadPlans());
onMounted(() => { loadDirs(); loadPlans(); });

function toggleSider() { siderCollapsed.value = !siderCollapsed.value; }

// ── 目录操作 ──────────────────────────────────────────────────
function startAddRoot() { addingDir.value = true; addParentId.value = null; newDirName.value = ''; }
function startAdd(pid: string) { addingDir.value = true; addParentId.value = pid; newDirName.value = ''; ctxMenu.value.visible = false; }

/** 确认创建目录 */
async function confirmAdd() {
  const name = newDirName.value.trim();
  if (!name || !store.currentProject || !addingDir.value) { addingDir.value = false; return; }
  addingDir.value = false; newDirName.value = '';
  await directoryApi.create(name, addParentId.value, store.currentProject.id, 'TEST_PLAN');
  loadDirs();
}

function startAddSibling(nodeId: string) {
  ctxMenu.value.visible = false;
  const dir = flatDirs(dirs.value).find(d => d.id === nodeId);
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
async function delDir(id: string) { ctxMenu.value.visible = false; await directoryApi.delete(id); message.success('删除成功'); loadDirs(); }

function showCtx(e: MouseEvent, id: string) { ctxMenu.value = { visible: true, x: e.clientX, y: e.clientY, nodeId: id }; }
function hideCtx() { ctxMenu.value.visible = false; }
onMounted(() => document.addEventListener('click', hideCtx));
onUnmounted(() => document.removeEventListener('click', hideCtx));

// ── 计划 CRUD ─────────────────────────────────────────────────
/** 逻辑删除（移入回收站） */
async function deletePlan(id: string) {
  await testPlanApi.delete(id); message.success('已移入回收站'); loadPlans();
}

// ── 工具 ──────────────────────────────────────────────────────
/** 格式化时间为 yyyy-MM-dd HH:mm:ss */
function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }

const { columns, handleResizeColumn } = useResizableColumns('test-plan', [
  { title: '计划名称', dataIndex: 'name', key: 'name', resizable: true, width: 200 },
  { title: '执行进度', key: 'progress', resizable: true, width: 200 },
  { title: '执行人', key: 'executors', resizable: true, width: 150, ellipsis: true },
  { title: '创建人', dataIndex: 'createdByName', key: 'createdByName', resizable: true, width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', resizable: true, width: 160 },
  { title: '操作', key: 'action', resizable: true, width: 180, fixed: 'right' as const },
]);
</script>

<style scoped>
.executor-cell { max-width: 130px; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; vertical-align: middle; }
.context-menu { position: fixed; background: #fff; border: 1px solid #e8e8e8; border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.12); padding: 4px 0; z-index: 1000; min-width: 140px; }
.ctx-item { padding: 6px 16px; cursor: pointer; font-size: 13px; display: flex; align-items: center; gap: 8px; }
.ctx-item:hover { background: #f5f5f5; }
.ctx-item.danger { color: #ff4d4f; }
.dir-tree-item { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.dir-tree-item .actions { opacity: 0; transition: opacity 0.15s; }
.dir-tree-item:hover .actions { opacity: 1; }
.inline-input { border: 1px solid #d9d9d9; border-radius: 4px; padding: 2px 6px; font-size: 13px; width: 100%; outline: none; }
.inline-input:focus { border-color: #1677ff; box-shadow: 0 0 0 2px rgba(22,119,255,0.06); }
</style>
