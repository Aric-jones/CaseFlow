<template>
  <a-layout style="height: 100%; background: #fff">
    <a-layout-sider width="260" style="background: #fff; border-right: 1px solid #f0f0f0; overflow: auto" :trigger="null">
      <div style="padding: 12px 16px; display: flex; justify-content: space-between; align-items: center">
        <strong>用例目录</strong>
        <a-space :size="4">
          <a-button size="small" type="primary" @click="showCreateCase = true"><PlusOutlined /> 新建用例</a-button>
          <a-button size="small" @click="showImport = true"><ImportOutlined /></a-button>
        </a-space>
      </div>

      <a-tree v-if="treeData.length" :tree-data="treeData" :selected-keys="selectedDir ? [selectedDir] : []"
        @select="onSelectDir" default-expand-all block-node>
        <template #title="{ key: nodeKey, title }">
          <div class="dir-tree-item" @contextmenu.prevent="(e: MouseEvent) => onContextMenu(e, String(nodeKey))">
            <span v-if="editingDirId !== String(nodeKey)">{{ title }}</span>
            <input v-else class="inline-input" :value="editingDirName"
              @input="editingDirName = ($event.target as HTMLInputElement).value"
              @keyup.enter="finishEditDir" @blur="finishEditDir" @keyup.escape="cancelEditDir" autofocus />
            <span class="actions" v-if="editingDirId !== String(nodeKey)">
              <a-button size="small" type="text" @click.stop="startAddChild(String(nodeKey))"><PlusOutlined /></a-button>
            </span>
          </div>
        </template>
      </a-tree>

      <div v-if="addingChild" style="padding: 4px 16px; margin-left: 24px">
        <input class="inline-input" v-model="newDirName" @keyup.enter="confirmAddChild" @blur="confirmAddChild"
          @keyup.escape="addingChild = false" placeholder="输入目录名，回车保存" autofocus />
      </div>
      <div v-if="!treeData.length" style="text-align: center; padding: 40px 0; color: #999">
        <p>暂无目录</p><a-button type="link" @click="startAddRoot">创建根目录</a-button>
      </div>
      <div v-if="addingRoot" style="padding: 4px 16px">
        <input class="inline-input" v-model="newDirName" @keyup.enter="confirmAddRoot" @blur="confirmAddRoot"
          @keyup.escape="addingRoot = false" placeholder="输入目录名，回车保存" autofocus />
      </div>
      <div style="padding: 12px 16px; border-top: 1px solid #f0f0f0; cursor: pointer; color: #8c8c8c" @click="$router.push('/recycle-bin')">
        <DeleteOutlined /> 用例回收站
      </div>
    </a-layout-sider>

    <a-layout-content style="padding: 24px">
      <div style="display: flex; gap: 12px; margin-bottom: 16px">
        <a-input-search v-model:value="keyword" placeholder="搜索用例集名称" style="width: 280px" @search="loadCases" />
        <a-select v-model:value="statusFilter" placeholder="状态筛选" allow-clear style="width: 130px"
          :options="[{ value: 'WRITING', label: '编写中' }, { value: 'PENDING_REVIEW', label: '待评审' }, { value: 'NO_REVIEW', label: '无需评审' }]" />
        <a-button type="primary" @click="loadCases"><SearchOutlined /> 搜索</a-button>
      </div>
      <a-table :columns="columns" :data-source="caseData.records" row-key="id" :loading="loading"
        :pagination="{ current: caseData.current, total: caseData.total, pageSize: 20, onChange: loadCases }" size="middle" :scroll="{ x: 1200 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'name'"><a @click="$router.push(`/mind-map/${record.id}`)">{{ record.name }}</a></template>
          <template v-if="column.key === 'status'"><a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag></template>
          <template v-if="column.key === 'requirementLink'"><a v-if="record.requirementLink" :href="record.requirementLink" target="_blank">查看需求</a><span v-else>-</span></template>
          <template v-if="column.key === 'action'">
            <a-space :size="0">
              <a-button v-if="record.status === 'PENDING_REVIEW'" type="link" size="small" @click="$router.push(`/review/${record.id}`)">评审</a-button>
              <a-button type="link" size="small" @click="$router.push(`/mind-map/${record.id}`)">编辑</a-button>
              <a-dropdown>
                <a-button type="link" size="small"><MoreOutlined /></a-button>
                <template #overlay>
                  <a-menu @click="(info: any) => handleCaseAction(info.key, record)">
                    <a-menu-item key="copy"><CopyOutlined /> 复制</a-menu-item>
                    <a-menu-item key="move"><SwapOutlined /> 移动</a-menu-item>
                    <a-menu-item key="delete" danger><DeleteOutlined /> 删除</a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-layout-content>

    <div v-if="contextMenu.visible" class="context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }">
      <div class="ctx-item" @click="startAddChild(contextMenu.nodeId!)"><PlusOutlined /> 新建子目录</div>
      <div class="ctx-item" @click="startRenameDir(contextMenu.nodeId!)"><EditOutlined /> 重命名</div>
      <div class="ctx-item danger" @click="deleteDir(contextMenu.nodeId!)"><DeleteOutlined /> 删除</div>
    </div>

    <a-modal v-model:open="showCreateCase" title="新建用例" @ok="createCase">
      <a-form layout="vertical">
        <a-form-item label="用例集名称"><a-input v-model:value="newCaseName" placeholder="输入用例集名称" /></a-form-item>
        <a-form-item label="关联需求（可选）"><a-input v-model:value="newCaseLink" placeholder="输入需求链接" /></a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="showImport" title="导入用例" :footer="null">
      <p style="color: #999; margin-bottom: 12px">请上传Excel(.xlsx)。表头必须包含：用例标题、前置条件、步骤、预期结果。</p>
      <a-upload-dragger accept=".xlsx" :max-count="1" :before-upload="handleImport">
        <p><InboxOutlined style="font-size: 40px; color: #1677ff" /></p><p>点击或拖拽上传</p>
      </a-upload-dragger>
    </a-modal>
    <a-modal v-model:open="showMove" title="移动用例集" @ok="confirmMove">
      <a-tree :tree-data="treeData" :selected-keys="moveTarget ? [moveTarget] : []"
        @select="(keys: any) => moveTarget = keys[0] || null" default-expand-all block-node />
    </a-modal>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { message, Modal } from 'ant-design-vue';
import { PlusOutlined, ImportOutlined, DeleteOutlined, SearchOutlined, EditOutlined, CopyOutlined, SwapOutlined, MoreOutlined, InboxOutlined } from '@ant-design/icons-vue';
import { directoryApi, caseSetApi } from '../api';
import { useAppStore } from '../stores/app';
import type { DirectoryNode, CaseSet, PageResult } from '../types';

const router = useRouter();
const store = useAppStore();
const dirs = ref<DirectoryNode[]>([]);
const selectedDir = ref<string | null>(null);
const caseData = ref<PageResult<CaseSet>>({ records: [], total: 0, size: 20, current: 1, pages: 0 });
const keyword = ref('');
const statusFilter = ref<string | undefined>();
const loading = ref(false);
const editingDirId = ref<string | null>(null);
const editingDirName = ref('');
const newDirName = ref('');
const addingChild = ref(false);
const addingRoot = ref(false);
const addingParentId = ref<string | null>(null);
const showCreateCase = ref(false);
const showImport = ref(false);
const showMove = ref(false);
const newCaseName = ref('');
const newCaseLink = ref('');
const movingId = ref<string | null>(null);
const moveTarget = ref<string | null>(null);
const contextMenu = ref({ visible: false, x: 0, y: 0, nodeId: null as string | null });

function dirToTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ key: d.id, title: d.name, children: d.children?.length ? dirToTree(d.children) : [] }));
}
const treeData = computed(() => dirToTree(dirs.value));

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

function onSelectDir(keys: any) { selectedDir.value = keys[0] || null; }
watch(() => store.currentProject, () => { loadDirs(); loadCases(); });
watch(selectedDir, () => loadCases());
onMounted(() => { loadDirs(); loadCases(); });

function startAddRoot() { addingRoot.value = true; newDirName.value = ''; }
function startAddChild(parentId: string) { addingChild.value = true; addingParentId.value = parentId; newDirName.value = ''; contextMenu.value.visible = false; }
async function confirmAddRoot() {
  if (!newDirName.value.trim() || !store.currentProject) { addingRoot.value = false; return; }
  await directoryApi.create(newDirName.value.trim(), null, store.currentProject.id, 'CASE');
  addingRoot.value = false; newDirName.value = ''; loadDirs();
}
async function confirmAddChild() {
  if (!newDirName.value.trim() || !store.currentProject) { addingChild.value = false; return; }
  await directoryApi.create(newDirName.value.trim(), addingParentId.value, store.currentProject.id, 'CASE');
  addingChild.value = false; newDirName.value = ''; loadDirs();
}
function startRenameDir(id: string) {
  contextMenu.value.visible = false;
  const flat = flatDirs(dirs.value);
  const dir = flat.find(d => d.id === id);
  editingDirId.value = id; editingDirName.value = dir?.name || '';
}
async function finishEditDir() {
  if (editingDirId.value && editingDirName.value.trim()) { await directoryApi.rename(editingDirId.value, editingDirName.value.trim()); loadDirs(); }
  editingDirId.value = null;
}
function cancelEditDir() { editingDirId.value = null; }
async function deleteDir(id: string) {
  contextMenu.value.visible = false;
  try { await directoryApi.delete(id); message.success('删除成功'); if (selectedDir.value === id) selectedDir.value = null; loadDirs(); } catch {}
}
function onContextMenu(e: MouseEvent, nodeId: string) { contextMenu.value = { visible: true, x: e.clientX, y: e.clientY, nodeId }; }
function hideContextMenu() { contextMenu.value.visible = false; }
onMounted(() => document.addEventListener('click', hideContextMenu));
onUnmounted(() => document.removeEventListener('click', hideContextMenu));
function flatDirs(list: DirectoryNode[]): DirectoryNode[] {
  const r: DirectoryNode[] = [];
  for (const d of list) { r.push(d); if (d.children?.length) r.push(...flatDirs(d.children)); }
  return r;
}

async function createCase() {
  if (!newCaseName.value.trim() || !store.currentProject) return;
  const targetDir = selectedDir.value || dirs.value[0]?.id;
  if (!targetDir) { message.error('请先创建目录'); return; }
  const res = await caseSetApi.create({ name: newCaseName.value, directoryId: targetDir, projectId: store.currentProject.id, requirementLink: newCaseLink.value });
  showCreateCase.value = false; newCaseName.value = ''; newCaseLink.value = '';
  router.push(`/mind-map/${res.data.id}`);
}
function handleCaseAction(key: string, record: CaseSet) {
  if (key === 'copy') caseSetApi.copy(record.id, record.directoryId).then(() => { message.success('复制成功'); loadCases(); });
  if (key === 'move') { movingId.value = record.id; showMove.value = true; }
  if (key === 'delete') Modal.confirm({ title: '确认删除?', content: '将移入回收站', onOk: () => caseSetApi.delete(record.id).then(() => { message.success('已移入回收站'); loadCases(); }) });
}
async function confirmMove() {
  if (movingId.value && moveTarget.value) { await caseSetApi.move(movingId.value, moveTarget.value); message.success('移动成功'); showMove.value = false; loadCases(); }
}
async function handleImport(file: File) {
  if (!store.currentProject) return false;
  const targetDir = selectedDir.value || dirs.value[0]?.id;
  if (!targetDir) { message.error('请先创建目录'); return false; }
  await caseSetApi.importExcel(file, targetDir, store.currentProject.id);
  message.success('导入成功'); showImport.value = false; loadCases(); return false;
}
function statusLabel(s: string) { return s === 'WRITING' ? '编写中' : s === 'PENDING_REVIEW' ? '待评审' : '无需评审'; }
function statusColor(s: string) { return s === 'WRITING' ? 'processing' : s === 'PENDING_REVIEW' ? 'warning' : 'default'; }

const columns = [
  { title: '用例集名称', dataIndex: 'name', key: 'name' },
  { title: '用例数量', dataIndex: 'caseCount', key: 'caseCount', width: 90 },
  { title: '状态', key: 'status', width: 110 },
  { title: '创建人', dataIndex: 'createdBy', width: 90 },
  { title: '更新时间', dataIndex: 'updatedAt', width: 170 },
  { title: '创建时间', dataIndex: 'createdAt', width: 170 },
  { title: '关联需求', key: 'requirementLink', width: 100 },
  { title: '操作', key: 'action', width: 180, fixed: 'right' as const },
];
</script>

<style scoped>
.context-menu { position: fixed; background: #fff; border: 1px solid #e8e8e8; border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.12); padding: 4px 0; z-index: 1000; min-width: 140px; }
.ctx-item { padding: 6px 16px; cursor: pointer; font-size: 13px; display: flex; align-items: center; gap: 8px; }
.ctx-item:hover { background: #f5f5f5; }
.ctx-item.danger { color: #ff4d4f; }
</style>
