<template>
  <a-layout style="height: 100%; background: #fff">
    <a-layout-sider width="260" style="background: #fff; border-right: 1px solid #f0f0f0; overflow: auto; padding: 12px 8px" :trigger="null">
      <div style="display: flex; justify-content: space-between; padding: 0 8px 12px">
        <strong>测试计划目录</strong>
      </div>
      <a-tree v-if="treeData.length" :tree-data="treeData" :selected-keys="selectedDir ? [selectedDir] : []"
              @select="(keys: any) => selectedDir = keys[0] || null" default-expand-all block-node>
        <template #title="{ key: nodeKey, title }">
          <div class="dir-tree-item" @contextmenu.prevent="(e: MouseEvent) => showCtx(e, String(nodeKey))">
            {{ title }}
            <span class="actions"><a-button size="small" type="text" @click.stop="startAdd(String(nodeKey))"><PlusOutlined /></a-button></span>
          </div>
        </template>
      </a-tree>
      <div v-else style="text-align: center; padding: 40px 0; color: #999">
        <p>暂无目录</p><a-button type="link" @click="startAddRoot">创建根目录</a-button>
      </div>
      <div v-if="addingDir" style="padding: 4px 16px">
        <input class="inline-input" v-model="newDirName" @keyup.enter="confirmAdd" @blur="confirmAdd" @keyup.escape="addingDir = false" placeholder="回车保存" autofocus />
      </div>
    </a-layout-sider>
    <a-layout-content style="padding: 24px">
      <div style="display: flex; gap: 12px; margin-bottom: 16px; align-items: center">
        <a-input-search v-model:value="keyword" placeholder="搜索" style="width: 280px" @search="loadPlans" />
        <a-checkbox v-model:checked="onlyMine">只看我的</a-checkbox>
        <div style="flex: 1" />
        <a-button type="primary" @click="showCreate = true"><PlusOutlined /> 新建测试计划</a-button>
      </div>
      <a-table :columns="columns" :data-source="plans.records" row-key="id" :loading="loading"
        :pagination="{ current: plans.current, total: plans.total, pageSize: 20, onChange: loadPlans }" size="middle">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="record.status === 'COMPLETED' ? 'success' : record.status === 'IN_PROGRESS' ? 'processing' : 'default'">
              {{ record.status === 'NOT_STARTED' ? '未开始' : record.status === 'IN_PROGRESS' ? '进行中' : '已完成' }}
            </a-tag>
          </template>
          <template v-if="column.key === 'action'">
            <a-button type="link" @click="$router.push(`/test-plan/${record.id}/execute`)">执行</a-button>
          </template>
        </template>
      </a-table>
    </a-layout-content>

    <a-modal v-model:open="showCreate" title="新建测试计划" @ok="createPlan" width="680px">
      <a-form layout="vertical">
        <a-form-item label="计划名称"><a-input v-model:value="planName" placeholder="输入名称" /></a-form-item>
        <a-form-item label="执行人">
          <a-select mode="multiple" v-model:value="executorIds" placeholder="选择执行人"
            :options="allUsers.map(u => ({ value: u.id, label: u.displayName }))" />
        </a-form-item>
        <a-form-item label="选择用例集">
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
      <div class="ctx-item danger" @click="delDir(ctxMenu.nodeId!)"><DeleteOutlined /> 删除</div>
    </div>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue';
import { directoryApi, testPlanApi, caseSetApi, mindNodeApi, userApi } from '../api';
import { useAppStore } from '../stores/app';
import type { DirectoryNode, TestPlan, CaseSet, User, MindNodeData, PageResult } from '../types';

const router = useRouter();
const store = useAppStore();

const dirs = ref<DirectoryNode[]>([]);
const selectedDir = ref<string | null>(null);
const plans = ref<PageResult<TestPlan>>({ records: [], total: 0, size: 20, current: 1, pages: 0 });
const keyword = ref('');
const onlyMine = ref(false);
const loading = ref(false);
const allUsers = ref<User[]>([]);
const caseSets = ref<CaseSet[]>([]);
const selectedCases = ref<{ nodeId: string; caseSetId: string }[]>([]);

const showCreate = ref(false);
const showCaseSelect = ref(false);
const planName = ref('');
const executorIds = ref<string[]>([]);

const addingDir = ref(false);
const newDirName = ref('');
const addParentId = ref<string | null>(null);

const ctxMenu = ref({ visible: false, x: 0, y: 0, nodeId: null as string | null });

function dirToTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ key: d.id, title: d.name, children: d.children?.length ? dirToTree(d.children) : [] }));
}
const treeData = ref<any[]>([]);

async function loadDirs() {
  if (!store.currentProject) return;
  dirs.value = (await directoryApi.tree(store.currentProject.id, 'TEST_PLAN')).data;
  treeData.value = dirToTree(dirs.value);
}
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

function startAddRoot() { addingDir.value = true; addParentId.value = null; newDirName.value = ''; }
function startAdd(pid: string) { addingDir.value = true; addParentId.value = pid; newDirName.value = ''; ctxMenu.value.visible = false; }
async function confirmAdd() {
  if (!newDirName.value.trim() || !store.currentProject) { addingDir.value = false; return; }
  await directoryApi.create(newDirName.value.trim(), addParentId.value, store.currentProject.id, 'TEST_PLAN');
  addingDir.value = false; loadDirs();
}
async function delDir(id: string) { ctxMenu.value.visible = false; await directoryApi.delete(id); loadDirs(); }
function showCtx(e: MouseEvent, id: string) { ctxMenu.value = { visible: true, x: e.clientX, y: e.clientY, nodeId: id }; }
function hideCtx() { ctxMenu.value.visible = false; }
onMounted(() => document.addEventListener('click', hideCtx));
onUnmounted(() => document.removeEventListener('click', hideCtx));

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
  message.success(`已添加用例`);
}

async function createPlan() {
  if (!planName.value.trim() || !store.currentProject) return;
  await testPlanApi.create({ name: planName.value, directoryId: selectedDir.value ?? undefined, projectId: store.currentProject.id, executorIds: executorIds.value, cases: selectedCases.value });
  message.success('创建成功'); showCreate.value = false; planName.value = ''; selectedCases.value = []; loadPlans();
}

onMounted(async () => {
  if (store.currentProject) caseSets.value = (await caseSetApi.list({ projectId: store.currentProject.id, size: 1000 })).data.records;
});

const columns = [
  { title: '计划名称', dataIndex: 'name' },
  { title: '状态', key: 'status', width: 100 },
  { title: '创建人', dataIndex: 'createdBy', width: 90 },
  { title: '创建时间', dataIndex: 'createdAt', width: 170 },
  { title: '操作', key: 'action', width: 100 },
];
</script>

<style scoped>
.context-menu { position: fixed; background: #fff; border: 1px solid #e8e8e8; border-radius: 6px; box-shadow: 0 4px 12px rgba(0,0,0,0.12); padding: 4px 0; z-index: 1000; min-width: 140px; }
.ctx-item { padding: 6px 16px; cursor: pointer; font-size: 13px; display: flex; align-items: center; gap: 8px; }
.ctx-item:hover { background: #f5f5f5; }
.ctx-item.danger { color: #ff4d4f; }
</style>
