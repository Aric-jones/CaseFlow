<template>
  <div class="form-page">
    <div class="form-header">
      <el-button text @click="goBack"><el-icon><ArrowLeft /></el-icon></el-button>
      <strong>{{ isEdit ? '编辑测试计划' : '新建测试计划' }}</strong>
    </div>
    <div class="form-body">
      <div class="form-card">
        <el-form label-width="90px" style="max-width:680px">
          <el-form-item label="任务名称" required>
            <el-input v-model="form.name" placeholder="输入测试计划名称" />
          </el-form-item>
          <el-form-item label="所属目录">
            <el-tree-select v-model="form.directoryId" :data="dirTree"
              :render-after-expand="false" default-expand-all check-strictly
              clearable placeholder="选择目录（可选）" style="width:100%" />
          </el-form-item>
          <el-form-item label="分配执行人">
            <el-select v-model="form.executorId" placeholder="选择执行人" clearable style="width:100%">
              <el-option v-for="u in allUsers" :key="u.id" :label="u.displayName" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="选择用例">
            <div style="display:flex;gap:8px;margin-bottom:12px">
              <el-button :icon="Plus" @click="openCaseModal">选择用例集</el-button>
              <el-button v-if="selectedSets.length" :icon="View" @click="openPreview">预览用例</el-button>
            </div>
            <!-- 已选用例集表格 -->
            <el-table v-if="selectedSets.length" :data="selectedSets" size="small" border style="width:100%">
              <el-table-column label="用例集名称" prop="name" show-overflow-tooltip min-width="160" />
              <el-table-column label="总用例数" prop="caseCount" width="90" />
              <el-table-column label="已筛选" width="80">
                <template #default="{ row }">
                  <span>{{ getFilteredCount(row.id) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" :resizable="false">
                <template #default="{ row }">
                  <el-button text type="primary" size="small" :icon="Filter" @click="openFilter(row)">筛选用例</el-button>
                  <el-button text type="danger" size="small" :icon="Delete" @click="removeSet(row.id)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <p v-else style="color:#909399;margin:0">未选择用例集</p>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="submitForm">{{ isEdit ? '保存' : '创建' }}</el-button>
            <el-button style="margin-left:8px" @click="goBack">取消</el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>

    <!-- 用例集选择弹窗 -->
    <el-dialog v-model="caseModal.visible" title="选择用例集" width="860px">
      <div class="case-modal-body">
        <div class="case-modal-left">
          <div style="font-weight:600;margin-bottom:8px">用例目录</div>
          <el-tree :data="caseDirTree" node-key="id"
            :highlight-current="true"
            :current-node-key="caseModal.dirId || undefined"
            default-expand-all :expand-on-click-node="false"
            @node-click="(d: any) => { caseModal.dirId = d.id; loadCaseSets(); }">
            <template #default="{ data }"><span>{{ data.label }}</span></template>
          </el-tree>
        </div>
        <div class="case-modal-right">
          <div style="display:flex;gap:8px;margin-bottom:12px">
            <el-input v-model="caseModal.keyword" placeholder="搜索用例集" clearable style="flex:1" />
            <el-button type="primary" :icon="Search" @click="loadCaseSets">搜索</el-button>
          </div>
          <el-table :data="caseModal.list" size="small" border
            @selection-change="(sel: any[]) => caseModal.checkedKeys = sel.map(s => s.id)">
            <el-table-column type="selection" width="48" />
            <el-table-column label="用例集名称" prop="name" show-overflow-tooltip min-width="160" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="csStatusType(row.status)" size="small">{{ csStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="用例数量" prop="caseCount" width="90" />
          </el-table>
          <div style="margin-top:10px;display:flex;justify-content:flex-end">
            <el-pagination layout="total, prev, pager, next" :total="caseModal.total"
              :page-size="10" :current-page="caseModal.page"
              @current-change="(p: number) => { caseModal.page = p; loadCaseSets(); }" />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="caseModal.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmCaseSelect">确定</el-button>
      </template>
    </el-dialog>

    <!-- 筛选用例弹窗 -->
    <el-dialog v-model="filterModal.visible" :title="'筛选用例 - ' + filterModal.csName" width="560px">
      <div v-if="filterModal.loading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" size="30"><Loading /></el-icon>
      </div>
      <div v-else-if="!Object.keys(filterModal.attrValues).length" style="text-align:center;color:#909399;padding:40px">
        该用例集没有可筛选的属性
      </div>
      <div v-else>
        <div v-for="(values, attrName) in filterModal.attrValues" :key="attrName" class="filter-row">
          <div class="filter-label">{{ propLabel(String(attrName)) }}</div>
          <el-select :model-value="filterModal.selected[String(attrName)] || []"
            @change="(v: any) => { filterModal.selected[String(attrName)] = v; }"
            multiple placeholder="全部（不筛选）" clearable style="width:100%">
            <el-option v-for="v in (values as string[])" :key="v" :value="v"
              :label="String(attrName) === 'mark' ? markLabel(v) : v" />
          </el-select>
        </div>
        <p style="margin-top:12px;color:#909399;font-size:12px">不选择的属性表示不做限制</p>
      </div>
      <template #footer>
        <el-button @click="filterModal.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmFilter">确定筛选</el-button>
      </template>
    </el-dialog>

    <!-- 预览用例弹窗 -->
    <el-dialog v-model="previewModal.visible" title="用例预览" width="900px">
      <div v-if="previewModal.loading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" size="30"><Loading /></el-icon>
      </div>
      <div v-else>
        <p style="color:#606266;font-size:13px;margin-bottom:10px">
          共 <b>{{ previewModal.totalCount }}</b> 条有效用例
        </p>
        <el-table :data="previewModal.tree" row-key="_key" border
          :tree-props="{ children: 'children' }" default-expand-all
          max-height="500" style="width:100%">
          <el-table-column label="用例结构" show-overflow-tooltip min-width="300">
            <template #default="{ row }">
              <el-tag v-if="row._nodeType" :type="ntType(row._nodeType)"
                size="small" style="margin-right:4px;font-size:11px">{{ ntLabel(row._nodeType) }}</el-tag>
              {{ row._text }}
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="previewModal.visible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Plus, View, Filter, Delete, Search } from '@element-plus/icons-vue';
import { testPlanApi, directoryApi, userApi, caseSetApi } from '../api';
import { useAppStore } from '../stores/app';
import type { DirectoryNode, User, CaseSet } from '../types';

const route = useRoute();
const router = useRouter();
const store = useAppStore();
const isEdit = computed(() => !!route.params.planId);
const planId = computed(() => route.params.planId as string);

const form = ref({ name: '', directoryId: undefined as string | undefined, executorId: undefined as string | undefined });
const selectedSets = ref<CaseSet[]>([]);
const allUsers = ref<User[]>([]);
const dirTree = ref<any[]>([]);
const saving = ref(false);
const caseSetFilters = reactive<Record<string, Record<string, string[]>>>({});
const filteredCounts = reactive<Record<string, number>>({});

function dirToSelectTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ value: d.id, label: d.name, children: d.children?.length ? dirToSelectTree(d.children) : undefined }));
}
function dirToElTree(list: DirectoryNode[]): any[] {
  return list.map(d => ({ id: d.id, label: d.name, children: d.children?.length ? dirToElTree(d.children) : [] }));
}

onMounted(async () => {
  if (!store.currentProject) return;
  const [dirRes, userRes] = await Promise.all([directoryApi.tree(store.currentProject.id, 'TEST_PLAN'), userApi.listAll()]);
  dirTree.value = dirToSelectTree(dirRes.data);
  allUsers.value = userRes.data;
  if (isEdit.value) {
    const [planRes, execRes, csIdsRes] = await Promise.all([
      testPlanApi.get(planId.value), testPlanApi.getExecutors(planId.value), testPlanApi.getCaseSetIds(planId.value),
    ]);
    form.value.name = planRes.data.name;
    form.value.directoryId = planRes.data.directoryId || undefined;
    form.value.executorId = execRes.data.length ? execRes.data[0].userId : undefined;
    if (csIdsRes.data.length) {
      const csRes = await caseSetApi.list({ projectId: store.currentProject.id, size: 1000 });
      const idSet = new Set(csIdsRes.data);
      selectedSets.value = csRes.data.records.filter((cs: CaseSet) => idSet.has(cs.id));
      for (const cs of selectedSets.value) loadFilteredCount(cs.id);
    }
  }
});

function getFilteredCount(csId: string) { return filteredCounts[csId] !== undefined ? filteredCounts[csId] : '-'; }

async function loadFilteredCount(csId: string) {
  try {
    const filters = caseSetFilters[csId] || undefined;
    const hasFilter = filters && Object.values(filters).some(v => v && v.length > 0);
    const res = await testPlanApi.previewCases(csId, hasFilter ? filters : undefined);
    filteredCounts[csId] = res.data.length;
  } catch { filteredCounts[csId] = 0; }
}

async function submitForm() {
  if (!form.value.name.trim()) { ElMessage.error('请输入计划名称'); return; }
  saving.value = true;
  try {
    const caseSetIds = selectedSets.value.map(s => s.id);
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
      ElMessage.success('更新成功');
    } else {
      if (!store.currentProject) return;
      await testPlanApi.create({ ...form.value, projectId: store.currentProject.id, caseSetIds, ...(hasFilters ? { filters } : {}) });
      ElMessage.success('创建成功');
    }
    router.push('/test-plans');
  } finally { saving.value = false; }
}

function goBack() { router.push('/test-plans'); }
function removeSet(id: string) {
  selectedSets.value = selectedSets.value.filter(s => s.id !== id);
  delete caseSetFilters[id]; delete filteredCounts[id];
}

// 工具
function propLabel(k: string) { return ({ mark: '标记', priority: '优先级' } as any)[k] || k; }
function markLabel(v: any) { return ({ '待完成': '待完成', '待确认': '待确认', '待修改': '待修改', PENDING: '待完成', TO_CONFIRM: '待确认', TO_MODIFY: '待修改' } as any)[v] || v; }
function ntLabel(t: string) { return ({ TITLE: '用例标题', PRECONDITION: '前置条件', STEP: '步骤', EXPECTED: '预期结果' } as any)[t] || t || ''; }
function ntType(t: string): any { return ({ TITLE: 'primary', PRECONDITION: 'info', STEP: 'success', EXPECTED: 'warning' } as any)[t] || ''; }
function csStatusLabel(s: string) { return ({ WRITING: '编写中', PENDING_REVIEW: '待评审', NO_REVIEW: '无需评审', APPROVED: '审核通过' } as any)[s] || s; }
function csStatusType(s: string): any { return ({ WRITING: 'primary', PENDING_REVIEW: 'warning', NO_REVIEW: 'info', APPROVED: 'success' } as any)[s] || 'info'; }

// 用例集选择
const caseDirTree = ref<any[]>([]);
const caseModal = ref({ visible: false, dirId: null as string | null, keyword: '', list: [] as CaseSet[], page: 1, total: 0, checkedKeys: [] as string[] });

async function openCaseModal() {
  if (!store.currentProject) return;
  const dirRes = await directoryApi.tree(store.currentProject.id, 'CASE');
  caseDirTree.value = dirToElTree(dirRes.data);
  caseModal.value = { visible: true, dirId: null, keyword: '', list: [], page: 1, total: 0, checkedKeys: selectedSets.value.map(s => s.id) };
  await loadCaseSets();
}
async function loadCaseSets() {
  if (!store.currentProject) return;
  const res = await caseSetApi.list({ projectId: store.currentProject.id, directoryId: caseModal.value.dirId || undefined, keyword: caseModal.value.keyword || undefined, page: caseModal.value.page, size: 10 });
  caseModal.value.list = res.data.records;
  caseModal.value.total = res.data.total;
}
function confirmCaseSelect() {
  const idSet = new Set(caseModal.value.checkedKeys);
  const existing = new Map(selectedSets.value.map(s => [s.id, s]));
  for (const cs of caseModal.value.list) { if (idSet.has(cs.id) && !existing.has(cs.id)) existing.set(cs.id, cs); }
  for (const id of existing.keys()) { if (!idSet.has(id)) existing.delete(id); }
  selectedSets.value = Array.from(existing.values());
  for (const cs of selectedSets.value) { if (filteredCounts[cs.id] === undefined) loadFilteredCount(cs.id); }
  caseModal.value.visible = false;
}

// 筛选
const filterModal = reactive({ visible: false, csId: '', csName: '', loading: false, attrValues: {} as Record<string, string[]>, selected: {} as Record<string, string[]> });

async function openFilter(cs: CaseSet) {
  filterModal.visible = true; filterModal.csId = cs.id; filterModal.csName = cs.name;
  filterModal.loading = true; filterModal.selected = { ...(caseSetFilters[cs.id] || {}) };
  try { const res = await testPlanApi.getAttributeValues(cs.id); filterModal.attrValues = res.data || {}; }
  catch { filterModal.attrValues = {}; }
  filterModal.loading = false;
}
async function confirmFilter() {
  const cleaned: Record<string, string[]> = {};
  for (const [k, v] of Object.entries(filterModal.selected)) { if (v && v.length > 0) cleaned[k] = v; }
  if (Object.keys(cleaned).length > 0) caseSetFilters[filterModal.csId] = cleaned;
  else delete caseSetFilters[filterModal.csId];
  filterModal.visible = false;
  await loadFilteredCount(filterModal.csId);
}

// 预览
const previewModal = reactive({ visible: false, loading: false, tree: [] as any[], totalCount: 0 });

async function openPreview() {
  previewModal.visible = true; previewModal.loading = true; previewModal.tree = []; previewModal.totalCount = 0;
  try {
    const allPaths: { paths: any[][]; csName: string; csId: string }[] = [];
    for (const cs of selectedSets.value) {
      const f = caseSetFilters[cs.id];
      const hasFilter = f && Object.values(f).some(v => v && v.length > 0);
      const res = await testPlanApi.previewCases(cs.id, hasFilter ? f : undefined);
      allPaths.push({ paths: res.data, csName: cs.name, csId: cs.id });
    }
    const tree: any[] = []; let total = 0;
    for (const { paths, csName, csId } of allPaths) {
      total += paths.length;
      const merged = mergePreviewPaths(paths, csId);
      tree.push({ _key: `cs-${csId}`, _text: `${csName} (${paths.length}条)`, _nodeType: null, children: merged.length ? merged : undefined });
    }
    previewModal.tree = tree; previewModal.totalCount = total;
  } catch { ElMessage.error('预览加载失败'); }
  previewModal.loading = false;
}

function mergePreviewPaths(paths: any[][], csId: string): any[] {
  interface TN { _key: string; _text: string; _nodeType: string | null; children?: TN[]; _childMap?: Map<string, TN> }
  const vr: TN = { _key: 'vr', _text: '', _nodeType: null, _childMap: new Map() };
  for (const path of paths) {
    let cur = vr;
    for (let i = 0; i < path.length; i++) {
      const n = path[i]; const nid = n.id || `a-${i}`;
      if (!cur._childMap) cur._childMap = new Map();
      let child = cur._childMap.get(nid);
      if (!child) { child = { _key: `pv-${csId}-${nid}`, _text: n.text || '', _nodeType: n.nodeType || null }; cur._childMap.set(nid, child); }
      cur = child;
    }
  }
  function toArr(node: TN): TN[] {
    if (!node._childMap || node._childMap.size === 0) return [];
    const arr: TN[] = [];
    for (const c of node._childMap.values()) { const kids = toArr(c); if (kids.length) c.children = kids; delete c._childMap; arr.push(c); }
    return arr;
  }
  const roots = toArr(vr);
  if (roots.length === 1 && roots[0].children) return roots[0].children;
  return roots;
}
</script>

<style scoped>
.form-page { display:flex; flex-direction:column; height:100%; background:#f0f2f5; }
.form-header { display:flex; align-items:center; gap:12px; background:#fff; border-bottom:1px solid #e4e7ed; padding:12px 20px; flex-shrink:0; box-shadow: 0 1px 4px rgba(0,0,0,0.04); }
.form-header strong { font-size:15px; }
.form-body { flex:1; overflow:auto; padding:20px 24px; }
.form-card { background:#fff; border-radius:10px; padding:32px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.case-modal-body { display:flex; gap:16px; min-height:400px; }
.case-modal-left { width:220px; flex-shrink:0; border-right:1px solid #e4e7ed; padding-right:16px; overflow:auto; max-height:500px; }
.case-modal-right { flex:1; min-width:0; }
.filter-row { margin-bottom:16px; }
.filter-label { font-weight:600; font-size:13px; margin-bottom:6px; color:#303133; }
</style>
