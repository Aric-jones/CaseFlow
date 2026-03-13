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
        <a-button type="primary" @click="openCreate"><PlusOutlined /> 新建测试计划</a-button>
      </div>
      <a-table :columns="columns" :data-source="plans.records" row-key="id" :loading="loading"
        :pagination="{ current: plans.current, total: plans.total, pageSize: 20, onChange: loadPlans }" size="middle"
        @resizeColumn="handleResizeColumn">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'COMPLETED' ? 'success' : record.status === 'IN_PROGRESS' ? 'processing' : 'default'">
              {{ record.status === 'NOT_STARTED' ? '未开始' : record.status === 'IN_PROGRESS' ? '进行中' : '已完成' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'createdAt'">{{ fmtTime(record.createdAt) }}</template>
          <template v-if="column.key === 'action'">
            <a-space :size="0">
              <a-button type="link" size="small" @click="$router.push(`/test-plan/${record.id}/execute`)">执行</a-button>
              <a-button type="link" size="small" @click="openEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除？将移入回收站" @confirm="deletePlan(record.id)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-layout-content>

    <!-- 新建/编辑弹窗 -->
    <a-modal v-model:open="showForm" :title="editingPlan ? '编辑测试计划' : '新建测试计划'" @ok="submitForm" width="680px">
      <a-form layout="vertical">
        <a-form-item label="计划名称"><a-input v-model:value="planName" placeholder="输入名称" /></a-form-item>
        <a-form-item label="所属目录">
          <a-tree-select v-model:value="formDirId" :tree-data="treeData" placeholder="选择目录（可选）"
            allow-clear tree-default-expand-all :field-names="{ children: 'children', label: 'title', value: 'key' }" style="width: 100%" />
        </a-form-item>
        <a-form-item label="执行人">
          <a-select mode="multiple" v-model:value="executorIds" placeholder="选择执行人"
            :options="allUsers.map(u => ({ value: u.id, label: u.displayName }))" />
        </a-form-item>
        <a-form-item v-if="!editingPlan" label="选择用例集">
          <a-button @click="showCaseSelect = true">选择</a-button>
          <span style="margin-left: 8px; color: #999">已选 {{ selectedCases.length }} 条用例</span>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="showCaseSelect" title="选择用例集" :footer="null" width="560px">
      <a-list :data-source="caseSets">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta :title="item.name" :description="`${item.caseCount}条用例`" />
            <template #actions><a-button type="link" @click="selectCaseSet(item.id)">选择</a-button></template>
          </a-list-item>
        </template>
      </a-list>
    </a-modal>

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
import { directoryApi, testPlanApi, caseSetApi, mindNodeApi, userApi } from '../api';
import { useAppStore } from '../stores/app';
import type { DirectoryNode, TestPlan, CaseSet, User, MindNodeData, PageResult } from '../types';

const store = useAppStore();

// ── 状态 ──────────────────────────────────────────────────────
const dirs = ref<DirectoryNode[]>([]);
const selectedDir = ref<string | null>(null);
const plans = ref<PageResult<TestPlan>>({ records: [], total: 0, size: 20, current: 1, pages: 0 });
const keyword = ref('');
const onlyMine = ref(false);
const loading = ref(false);
const allUsers = ref<User[]>([]);
const caseSets = ref<CaseSet[]>([]);
const selectedCases = ref<{ nodeId: string; caseSetId: string }[]>([]);
const showForm = ref(false);
const showCaseSelect = ref(false);
const planName = ref('');
const executorIds = ref<string[]>([]);
const editingPlan = ref<TestPlan | null>(null);
const formDirId = ref<string | undefined>();
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
onMounted(() => { loadDirs(); loadPlans(); userApi.listAll().then(r => allUsers.value = r.data); });

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
/** 打开新建弹窗 */
function openCreate() {
  editingPlan.value = null;
  planName.value = ''; executorIds.value = []; selectedCases.value = [];
  formDirId.value = selectedDir.value || undefined;
  showForm.value = true;
}

/** 打开编辑弹窗，并加载该计划的执行人 */
async function openEdit(plan: TestPlan) {
  editingPlan.value = plan;
  planName.value = plan.name;
  formDirId.value = plan.directoryId || undefined;
  try {
    const res = await testPlanApi.getExecutors(plan.id);
    executorIds.value = res.data.map((e: any) => e.userId);
  } catch { executorIds.value = []; }
  showForm.value = true;
}

/** 提交创建/编辑表单 */
async function submitForm() {
  if (!planName.value.trim()) { message.error('请输入计划名称'); return; }
  if (editingPlan.value) {
    await testPlanApi.update(editingPlan.value.id, { name: planName.value, directoryId: formDirId.value, executorIds: executorIds.value });
    message.success('更新成功');
  } else {
    if (!store.currentProject) return;
    await testPlanApi.create({ name: planName.value, directoryId: formDirId.value ?? undefined, projectId: store.currentProject.id, executorIds: executorIds.value, cases: selectedCases.value });
    message.success('创建成功');
  }
  showForm.value = false; loadPlans();
}

/** 逻辑删除（移入回收站） */
async function deletePlan(id: string) {
  await testPlanApi.delete(id); message.success('已移入回收站'); loadPlans();
}

/** 从用例集中提取符合条件的用例节点 */
async function selectCaseSet(csId: string) {
  const res = await mindNodeApi.tree(csId);
  if (!res.data.length) return;
  function collect(n: MindNodeData, path: MindNodeData[]): void {
    const p = [...path, n];
    if (!n.children?.length) {
      if (p.length >= 5 && p[p.length-1].nodeType === 'EXPECTED' && n.id) {
        selectedCases.value.push({ nodeId: n.id, caseSetId: csId });
      }
    } else { for (const c of n.children) collect(c, p); }
  }
  collect(res.data[0], []);
  message.success('已添加用例');
}

onMounted(async () => {
  if (store.currentProject) caseSets.value = (await caseSetApi.list({ projectId: store.currentProject.id, size: 1000 })).data.records;
});

// ── 工具 ──────────────────────────────────────────────────────
/** 格式化时间为 yyyy-MM-dd HH:mm:ss */
function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 19) : ''; }

/** 调整列宽 */
function handleResizeColumn(w: number, col: any) { col.width = w; }

const columns = ref([
  { title: '计划名称', dataIndex: 'name', resizable: true, width: 200 },
  { title: '状态', key: 'status', resizable: true, width: 100 },
  { title: '创建人', dataIndex: 'createdByName', resizable: true, width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', resizable: true, width: 160 },
  { title: '操作', key: 'action', resizable: true, width: 180, fixed: 'right' as const },
]);
</script>

<style scoped>
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
