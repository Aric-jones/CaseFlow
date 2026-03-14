<template>
  <div class="plan-form-page">
    <div class="form-header">
      <a-button type="text" @click="goBack"><ArrowLeftOutlined /></a-button>
      <strong>{{ isEdit ? '编辑测试计划' : '新建测试计划' }}</strong>
    </div>
    <div class="form-body">
      <a-form layout="vertical" style="max-width: 780px; margin: 0 auto">
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
          <a-space>
            <a-button @click="openCaseModal"><PlusOutlined /> 选择用例集</a-button>
            <a-button v-if="selectedSets.length" @click="openPreview"><EyeOutlined /> 预览用例</a-button>
          </a-space>

          <!-- 已选用例集表格 -->
          <a-table v-if="selectedSets.length" :columns="selectedCols" :data-source="selectedSets"
            row-key="id" size="small" :pagination="false" style="margin-top: 12px">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'filteredCount'">
                <span>{{ getFilteredCount(record.id) }}</span>
              </template>
              <template v-if="column.key === 'action'">
                <a-space>
                  <a-button type="link" size="small" @click="openFilter(record)"><FilterOutlined /> 筛选用例</a-button>
                  <a-button type="link" size="small" danger @click="removeSet(record.id)"><DeleteOutlined /> 删除</a-button>
                </a-space>
              </template>
            </template>
          </a-table>
          <div v-else style="margin-top: 8px; color: #999">未选择用例集</div>
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" :loading="saving" @click="submitForm">{{ isEdit ? '保存' : '创建' }}</a-button>
            <a-button @click="goBack">取消</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </div>

    <!-- ===== 用例集选择弹窗 ===== -->
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

    <!-- ===== 筛选用例弹窗 ===== -->
    <a-modal v-model:open="filterModal.visible" :title="'筛选用例 - ' + filterModal.csName" width="600px"
      @ok="confirmFilter" ok-text="确定筛选">
      <div v-if="filterModal.loading" style="text-align:center; padding:40px"><a-spin /></div>
      <div v-else-if="Object.keys(filterModal.attrValues).length === 0" style="text-align:center; color:#999; padding:40px">
        该用例集没有可筛选的属性
      </div>
      <div v-else>
        <div v-for="(values, attrName) in filterModal.attrValues" :key="attrName" class="filter-attr-row">
          <div class="filter-attr-label">{{ propLabel(attrName) }}</div>
          <a-select mode="multiple" :value="filterModal.selected[attrName] || []"
            @change="(v: any) => { filterModal.selected[attrName] = v; }"
            placeholder="全部（不筛选）" allow-clear style="width: 100%"
            :options="values.map((v: string) => ({ value: v, label: attrName === 'mark' ? markLabel(v) : v }))" />
        </div>
        <div style="margin-top:12px; color:#666; font-size:12px">
          提示：不选择的属性表示不做限制（包含所有值）
        </div>
      </div>
    </a-modal>

    <!-- ===== 预览用例弹窗 ===== -->
    <a-modal v-model:open="previewModal.visible" title="用例预览" width="900px" :footer="null">
      <div v-if="previewModal.loading" style="text-align:center; padding:40px"><a-spin /></div>
      <div v-else>
        <div style="margin-bottom:8px; color:#666; font-size:13px">
          共 <b>{{ previewModal.totalCount }}</b> 条有效用例
        </div>
        <a-table :columns="previewCols" :data-source="previewModal.tree" row-key="_key" size="small"
          :pagination="false" :scroll="{ y: 500 }" default-expand-all-rows
          :expandable="{ childrenColumnName: 'children' }">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'title'">
              <a-tag v-if="record._nodeType" :color="ntColor(record._nodeType)" style="font-size:11px">{{ ntLabel(record._nodeType) }}</a-tag>
              {{ record._text }}
            </template>
          </template>
        </a-table>
      </div>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { ArrowLeftOutlined, PlusOutlined, SearchOutlined, FilterOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons-vue';
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

/** 每个用例集的属性筛选条件 {caseSetId -> {attrName -> [values]}} */
const caseSetFilters = reactive<Record<string, Record<string, string[]>>>({});
/** 每个用例集筛选后的用例数 {caseSetId -> count} */
const filteredCounts = reactive<Record<string, number>>({});

const selectedCols = [
  { title: '用例集名称', dataIndex: 'name', ellipsis: true },
  { title: '总用例数', dataIndex: 'caseCount', width: 90 },
  { title: '已筛选', key: 'filteredCount', width: 80 },
  { title: '操作', key: 'action', width: 180 },
];

function getFilteredCount(csId: string) {
  if (filteredCounts[csId] !== undefined) return filteredCounts[csId];
  return '-';
}

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
      // 加载已选用例集的筛选计数
      for (const cs of selectedSets.value) loadFilteredCount(cs.id);
    }
  }
});

async function loadFilteredCount(csId: string) {
  try {
    const filters = caseSetFilters[csId] || undefined;
    const hasFilter = filters && Object.values(filters).some(v => v && v.length > 0);
    const res = await testPlanApi.previewCases(csId, hasFilter ? filters : undefined);
    filteredCounts[csId] = res.data.length;
  } catch { filteredCounts[csId] = 0; }
}

async function submitForm() {
  if (!form.value.name.trim()) { message.error('请输入计划名称'); return; }
  saving.value = true;
  try {
    const caseSetIds = selectedSets.value.map(s => s.id);
    // 构建有效的筛选条件
    const filters: Record<string, Record<string, string[]>> = {};
    for (const csId of caseSetIds) {
      const f = caseSetFilters[csId];
      if (f && Object.values(f).some(v => v && v.length > 0)) {
        const cleaned: Record<string, string[]> = {};
        for (const [k, v] of Object.entries(f)) { if (v && v.length > 0) cleaned[k] = v; }
        filters[csId] = cleaned;
      }
    }
    const hasFilters = Object.keys(filters).length > 0;
    if (isEdit.value) {
      await testPlanApi.update(planId.value, { ...form.value, caseSetIds, ...(hasFilters ? { filters } : {}) });
      message.success('更新成功');
    } else {
      if (!store.currentProject) return;
      await testPlanApi.create({ ...form.value, projectId: store.currentProject.id, caseSetIds, ...(hasFilters ? { filters } : {}) });
      message.success('创建成功');
    }
    router.push('/test-plans');
  } finally { saving.value = false; }
}

function goBack() { router.push('/test-plans'); }
function removeSet(id: string) {
  selectedSets.value = selectedSets.value.filter(s => s.id !== id);
  delete caseSetFilters[id];
  delete filteredCounts[id];
}

// ── 工具函数 ──────────────────────────────────────
function propLabel(k: string) { return ({ mark: '标记', priority: '优先级', category: '分类', coverage: '覆盖集', device: '设备集' } as any)[k] || k; }
function markLabel(v: any) { return ({ PENDING: '待完成', TO_CONFIRM: '待确认', TO_MODIFY: '待修改', '待完成': '待完成', '待确认': '待确认', '待修改': '待修改' } as any)[v] || v; }
function ntLabel(t: string) { return ({ TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' } as any)[t] || t || ''; }
function ntColor(t: string) { return ({ TITLE: 'purple', PRECONDITION: 'blue', STEP: 'green', EXPECTED: 'orange' } as any)[t] || 'default'; }

// ── 用例集选择弹窗 ────────────────────────────────
const caseDirTree = ref<any[]>([]);
const caseModal = ref({
  visible: false, dirId: null as string | null, keyword: '',
  list: [] as CaseSet[], page: 1, total: 0, checkedKeys: [] as string[],
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
  caseModal.value = { visible: true, dirId: null, keyword: '', list: [], page: 1, total: 0, checkedKeys: selectedSets.value.map(s => s.id) };
  await loadCaseSets();
}

async function loadCaseSets() {
  if (!store.currentProject) return;
  const res = await caseSetApi.list({
    projectId: store.currentProject.id, directoryId: caseModal.value.dirId || undefined,
    keyword: caseModal.value.keyword || undefined, page: caseModal.value.page, size: 10,
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
  // 为新加的用例集加载计数
  for (const cs of selectedSets.value) {
    if (filteredCounts[cs.id] === undefined) loadFilteredCount(cs.id);
  }
  caseModal.value.visible = false;
}

// ── 筛选用例弹窗 ─────────────────────────────────
const filterModal = reactive({
  visible: false, csId: '', csName: '', loading: false,
  attrValues: {} as Record<string, string[]>,
  selected: {} as Record<string, string[]>,
});

async function openFilter(cs: CaseSet) {
  filterModal.visible = true;
  filterModal.csId = cs.id;
  filterModal.csName = cs.name;
  filterModal.loading = true;
  filterModal.selected = { ...(caseSetFilters[cs.id] || {}) };
  try {
    const res = await testPlanApi.getAttributeValues(cs.id);
    filterModal.attrValues = res.data || {};
  } catch { filterModal.attrValues = {}; }
  filterModal.loading = false;
}

async function confirmFilter() {
  const cleaned: Record<string, string[]> = {};
  for (const [k, v] of Object.entries(filterModal.selected)) {
    if (v && v.length > 0) cleaned[k] = v;
  }
  if (Object.keys(cleaned).length > 0) {
    caseSetFilters[filterModal.csId] = cleaned;
  } else {
    delete caseSetFilters[filterModal.csId];
  }
  filterModal.visible = false;
  await loadFilteredCount(filterModal.csId);
}

// ── 预览用例弹窗 ─────────────────────────────────
const previewModal = reactive({
  visible: false, loading: false, tree: [] as any[], totalCount: 0,
});
const previewCols = [{ title: '用例结构', key: 'title', ellipsis: true }];

async function openPreview() {
  previewModal.visible = true;
  previewModal.loading = true;
  previewModal.tree = [];
  previewModal.totalCount = 0;
  try {
    const allPaths: { paths: any[][]; csName: string; csId: string }[] = [];
    for (const cs of selectedSets.value) {
      const f = caseSetFilters[cs.id];
      const hasFilter = f && Object.values(f).some(v => v && v.length > 0);
      const res = await testPlanApi.previewCases(cs.id, hasFilter ? f : undefined);
      allPaths.push({ paths: res.data, csName: cs.name, csId: cs.id });
    }

    const tree: any[] = [];
    let total = 0;
    for (const { paths, csName, csId } of allPaths) {
      total += paths.length;
      const merged = mergePreviewPaths(paths, csId);
      tree.push({ _key: `cs-${csId}`, _text: `${csName} (${paths.length}条)`, _nodeType: null, children: merged.length ? merged : undefined });
    }
    previewModal.tree = tree;
    previewModal.totalCount = total;
  } catch { message.error('预览加载失败'); }
  previewModal.loading = false;
}

function mergePreviewPaths(paths: any[][], csId: string): any[] {
  interface TN { _key: string; _text: string; _nodeType: string | null; children?: TN[]; _childMap?: Map<string, TN> }
  const vr: TN = { _key: 'vr', _text: '', _nodeType: null, _childMap: new Map() };
  for (const path of paths) {
    let cur = vr;
    for (let i = 0; i < path.length; i++) {
      const n = path[i];
      const nid = n.id || `a-${i}`;
      if (!cur._childMap) cur._childMap = new Map();
      let child = cur._childMap.get(nid);
      if (!child) {
        child = { _key: `pv-${csId}-${nid}`, _text: n.text || '', _nodeType: n.nodeType || null };
        cur._childMap.set(nid, child);
      }
      cur = child;
    }
  }
  function toArr(node: TN): TN[] {
    if (!node._childMap || node._childMap.size === 0) return [];
    const arr: TN[] = [];
    for (const c of node._childMap.values()) {
      const kids = toArr(c);
      if (kids.length) c.children = kids;
      delete c._childMap;
      arr.push(c);
    }
    return arr;
  }
  const roots = toArr(vr);
  if (roots.length === 1 && roots[0].children) return roots[0].children;
  return roots;
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
.filter-attr-row { margin-bottom: 16px; }
.filter-attr-label { font-weight: 600; font-size: 13px; margin-bottom: 6px; color: #333; }
</style>
