<template>
  <div class="plan-form-page">
    <div class="form-header">
      <a-button type="text" @click="goBack"><ArrowLeftOutlined /></a-button>
      <strong>{{ isEdit ? '编辑测试计划' : '新建测试计划' }}</strong>
    </div>
    <div class="form-body">
      <a-form layout="vertical" style="max-width: 720px; margin: 0 auto">
        <a-form-item label="任务名称" required>
          <a-input v-model:value="form.name" placeholder="输入测试计划名称" />
        </a-form-item>
        <a-form-item label="所属目录">
          <a-tree-select v-model:value="form.directoryId" :tree-data="dirTree" placeholder="选择目录（可选）"
            allow-clear tree-default-expand-all :field-names="{ children: 'children', label: 'title', value: 'key' }" style="width: 100%" />
        </a-form-item>
        <a-form-item label="分配执行人">
          <a-select mode="multiple" v-model:value="form.executorIds" placeholder="选择执行人"
            :options="allUsers.map(u => ({ value: u.id, label: u.displayName }))" />
        </a-form-item>
        <a-form-item label="选择用例">
          <a-button @click="openCaseModal"><PlusOutlined /> 选择用例集</a-button>
          <div v-if="selectedSets.length" style="margin-top: 8px">
            <a-tag v-for="s in selectedSets" :key="s.id" closable @close="removeSet(s.id)" style="margin-bottom: 4px">
              {{ s.name }} ({{ s.caseCount }}条)
            </a-tag>
          </div>
          <span v-else style="margin-left: 8px; color: #999">未选择</span>
        </a-form-item>
        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="saving" @click="submitForm">{{ isEdit ? '保存' : '创建' }}</a-button>
            <a-button @click="goBack">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </div>

    <!-- 用例集选择弹窗 -->
    <a-modal v-model:open="caseModal.visible" title="选择用例集" width="860px" @ok="confirmCaseSelect" ok-text="确定">
      <div class="case-modal-body">
        <div class="case-modal-left">
          <div style="font-weight: 600; margin-bottom: 8px">用例目录</div>
          <a-tree v-if="caseDirTree.length" :tree-data="caseDirTree" :selected-keys="caseModal.dirId ? [caseModal.dirId] : []"
            @select="(keys: any) => { caseModal.dirId = keys[0] || null; loadCaseSets(); }" default-expand-all block-node />
          <div v-else style="color: #999; padding: 20px; text-align: center">暂无目录</div>
        </div>
        <div class="case-modal-right">
          <div style="display: flex; gap: 8px; margin-bottom: 12px">
            <a-input v-model:value="caseModal.keyword" placeholder="搜索用例集" allow-clear style="flex: 1" />
            <a-button type="primary" @click="loadCaseSets"><SearchOutlined /> 搜索</a-button>
          </div>
          <a-table :columns="csColumns" :data-source="caseModal.list" row-key="id" size="small"
            :row-selection="{ selectedRowKeys: caseModal.checkedKeys, onChange: (keys: any) => caseModal.checkedKeys = keys }"
            :pagination="{ current: caseModal.page, total: caseModal.total, pageSize: 10, onChange: (p: number) => { caseModal.page = p; loadCaseSets(); }, size: 'small' }">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="csStatusColor(record.status)">{{ csStatusLabel(record.status) }}</a-tag>
              </template>
            </template>
          </a-table>
        </div>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { ArrowLeftOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue';
import { testPlanApi, directoryApi, userApi, caseSetApi } from '../api';
import { useAppStore } from '../stores/app';
import type { DirectoryNode, User, CaseSet } from '../types';

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const isEdit = computed(() => !!route.params.planId);
const planId = computed(() => route.params.planId as string);

const form = ref({ name: '', directoryId: undefined as string | undefined, executorIds: [] as string[] });
const selectedSets = ref<CaseSet[]>([]);
const allUsers = ref<User[]>([]);
const dirTree = ref<any[]>([]);
const saving = ref(false);

/** 目录树转为 a-tree-select 格式 */
function dirToTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ key: d.id, title: d.name, children: d.children?.length ? dirToTree(d.children) : [] }));
}

onMounted(async () => {
  if (!store.currentProject) return;
  const [dirRes, userRes] = await Promise.all([
    directoryApi.tree(store.currentProject.id, 'TEST_PLAN'),
    userApi.listAll(),
  ]);
  dirTree.value = dirToTree(dirRes.data);
  allUsers.value = userRes.data;

  if (isEdit.value) {
    const [planRes, execRes, csIdsRes] = await Promise.all([
      testPlanApi.get(planId.value),
      testPlanApi.getExecutors(planId.value),
      testPlanApi.getCaseSetIds(planId.value),
    ]);
    form.value.name = planRes.data.name;
    form.value.directoryId = planRes.data.directoryId || undefined;
    form.value.executorIds = execRes.data.map((e: any) => e.userId);
    if (csIdsRes.data.length) {
      const csRes = await caseSetApi.list({ projectId: store.currentProject.id, size: 1000 });
      const idSet = new Set(csIdsRes.data);
      selectedSets.value = csRes.data.records.filter(cs => idSet.has(cs.id));
    }
  }
});

async function submitForm() {
  if (!form.value.name.trim()) { message.error('请输入计划名称'); return; }
  saving.value = true;
  try {
    const caseSetIds = selectedSets.value.map(s => s.id);
    if (isEdit.value) {
      await testPlanApi.update(planId.value, { ...form.value, caseSetIds });
      message.success('更新成功');
    } else {
      if (!store.currentProject) return;
      await testPlanApi.create({ ...form.value, projectId: store.currentProject.id, caseSetIds });
      message.success('创建成功');
    }
    router.push('/test-plans');
  } finally { saving.value = false; }
}

function goBack() { router.push('/test-plans'); }
function removeSet(id: string) { selectedSets.value = selectedSets.value.filter(s => s.id !== id); }

// ── 用例集选择弹窗 ─────────────────────────────────
const caseDirTree = ref<any[]>([]);
const caseModal = ref({
  visible: false,
  dirId: null as string | null,
  keyword: '',
  list: [] as CaseSet[],
  page: 1, total: 0,
  checkedKeys: [] as string[],
});

const csColumns = [
  { title: '用例集名称', dataIndex: 'name', ellipsis: true },
  { title: '状态', key: 'status', width: 100 },
  { title: '用例数量', dataIndex: 'caseCount', width: 90 },
];

function csStatusLabel(s: string) { return ({ WRITING: '编写中', PENDING_REVIEW: '待评审', NO_REVIEW: '无需评审', APPROVED: '审核通过' } as any)[s] || s; }
function csStatusColor(s: string) { return ({ WRITING: 'processing', PENDING_REVIEW: 'warning', NO_REVIEW: 'default', APPROVED: 'success' } as any)[s] || 'default'; }

async function openCaseModal() {
  if (!store.currentProject) return;
  const dirRes = await directoryApi.tree(store.currentProject.id, 'CASE');
  caseDirTree.value = dirToTree(dirRes.data);
  caseModal.value.visible = true;
  caseModal.value.dirId = null;
  caseModal.value.keyword = '';
  caseModal.value.page = 1;
  caseModal.value.checkedKeys = selectedSets.value.map(s => s.id);
  await loadCaseSets();
}

async function loadCaseSets() {
  if (!store.currentProject) return;
  const res = await caseSetApi.list({
    projectId: store.currentProject.id,
    directoryId: caseModal.value.dirId || undefined,
    keyword: caseModal.value.keyword || undefined,
    page: caseModal.value.page,
    size: 10,
  });
  caseModal.value.list = res.data.records;
  caseModal.value.total = res.data.total;
}

function confirmCaseSelect() {
  const idSet = new Set(caseModal.value.checkedKeys);
  const existing = new Map(selectedSets.value.map(s => [s.id, s]));
  for (const cs of caseModal.value.list) {
    if (idSet.has(cs.id) && !existing.has(cs.id)) existing.set(cs.id, cs);
  }
  for (const id of existing.keys()) {
    if (!idSet.has(id)) existing.delete(id);
  }
  selectedSets.value = Array.from(existing.values());
  caseModal.value.visible = false;
}
</script>

<style scoped>
.plan-form-page { display: flex; flex-direction: column; height: 100%; background: #f5f5f5; }
.form-header { display: flex; align-items: center; gap: 12px; background: #fff; border-bottom: 1px solid #e8e8e8; padding: 12px 20px; flex-shrink: 0; }
.form-header strong { font-size: 15px; }
.form-body { flex: 1; overflow: auto; padding: 32px 24px; }
.case-modal-body { display: flex; gap: 16px; min-height: 400px; }
.case-modal-left { width: 220px; flex-shrink: 0; border-right: 1px solid #f0f0f0; padding-right: 16px; overflow: auto; max-height: 500px; }
.case-modal-right { flex: 1; min-width: 0; }
</style>
