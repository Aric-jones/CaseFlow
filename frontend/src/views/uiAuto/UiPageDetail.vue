<template>
  <div class="detail-page" v-loading="loading">
    <div class="detail-top">
      <el-button text @click="$router.push('/ui-auto/pages')"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
      <span class="detail-title">{{ page.name || '页面详情' }}</span>
      <el-button type="primary" size="small" :loading="savingPage" style="margin-left:auto" @click="savePageInfo">保存页面信息</el-button>
    </div>

    <div class="content-card">
      <div class="section-label">页面信息</div>
      <el-form :model="page" label-width="72px" class="page-form">
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="名称"><el-input v-model="page.name" placeholder="页面名称" /></el-form-item></el-col>
          <el-col :span="16"><el-form-item label="URL"><el-input v-model="page.url" placeholder="https://..." /></el-form-item></el-col>
        </el-row>
        <el-form-item label="描述"><el-input v-model="page.description" type="textarea" :rows="2" /></el-form-item>
      </el-form>
    </div>

    <div class="content-card" style="margin-top:16px">
      <div class="toolbar-inner">
        <span class="section-label">页面元素</span>
        <div style="flex:1" />
        <el-button type="primary" size="small" :icon="Plus" :disabled="addingRow" @click="startAddElement">添加元素</el-button>
      </div>

      <el-table :data="tableRows" border style="width:100%">
        <el-table-column prop="name" label="名称" min-width="120">
          <template #default="{ row }">
            <el-input v-if="row._edit" v-model="row.name" size="small" placeholder="元素名称" />
            <span v-else>{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="定位方式" min-width="130">
          <template #default="{ row }">
            <el-select v-if="row._edit" v-model="row.locatorType" size="small" style="width:100%">
              <el-option v-for="t in locatorTypes" :key="t" :label="t" :value="t" />
            </el-select>
            <span v-else>{{ row.locatorType }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="locatorValue" label="定位值" min-width="180">
          <template #default="{ row }">
            <el-input v-if="row._edit" v-model="row.locatorValue" size="small" placeholder="选择器或表达式" />
            <code v-else class="mono">{{ row.locatorValue }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="140">
          <template #default="{ row }">
            <el-input v-if="row._edit" v-model="row.description" size="small" placeholder="可选" />
            <span v-else>{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <template v-if="row._isNew">
              <el-button text type="primary" size="small" @click="confirmCreate(row)">保存</el-button>
              <el-button text size="small" @click="cancelAdd">取消</el-button>
            </template>
            <template v-else>
              <el-button v-if="!row._edit" text type="primary" size="small" @click="beginEdit(row)">编辑</el-button>
              <el-button v-if="row._edit" text type="primary" size="small" @click="saveElement(row)">保存</el-button>
              <el-button v-if="row._edit" text size="small" @click="cancelEdit(row)">取消</el-button>
              <el-popconfirm v-if="!row._edit" title="确认删除该元素？" @confirm="removeElement(row)">
                <template #reference><el-button text type="danger" size="small">删除</el-button></template>
              </el-popconfirm>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowLeft, Plus } from '@element-plus/icons-vue';
import { uiPageApi, uiElementApi } from '../../api';
import type { UiElementItem, UiPageItem } from '../../types';

const route = useRoute();
const pageId = computed(() => route.params.id as string);

const locatorTypes = ['CSS_SELECTOR', 'XPATH', 'ID', 'NAME', 'LINK_TEXT', 'TAG_NAME'] as const;

const loading = ref(false);
const savingPage = ref(false);
const page = ref<Partial<UiPageItem>>({ name: '', url: '', description: '' });
const elements = ref<(UiElementItem & { _edit?: boolean; _isNew?: boolean; _snapshot?: string })[]>([]);
const addingRow = ref(false);

const tableRows = computed(() => elements.value);

async function load() {
  if (!pageId.value) return;
  loading.value = true;
  try {
    const p = (await uiPageApi.detail(pageId.value)).data;
    page.value = { ...p };
    const list = (await uiElementApi.list(pageId.value)).data || [];
    elements.value = list.map(e => ({ ...e, _edit: false }));
  } finally {
    loading.value = false;
  }
}

async function savePageInfo() {
  if (!page.value.name?.trim()) {
    ElMessage.warning('请输入页面名称');
    return;
  }
  savingPage.value = true;
  try {
    await uiPageApi.update(pageId.value, {
      name: page.value.name,
      url: page.value.url,
      description: page.value.description,
    });
    ElMessage.success('页面信息已保存');
  } finally {
    savingPage.value = false;
  }
}

function startAddElement() {
  if (addingRow.value) return;
  addingRow.value = true;
  elements.value.push({
    id: '',
    pageId: pageId.value,
    name: '',
    locatorType: 'CSS_SELECTOR',
    locatorValue: '',
    description: '',
    _edit: true,
    _isNew: true,
  } as any);
}

function cancelAdd() {
  addingRow.value = false;
  elements.value = elements.value.filter(e => !(e as any)._isNew);
}

async function confirmCreate(row: UiElementItem & { _isNew?: boolean }) {
  if (!row.name?.trim() || !row.locatorValue?.trim()) {
    ElMessage.warning('请填写名称与定位值');
    return;
  }
  await uiElementApi.create({
    pageId: pageId.value,
    name: row.name.trim(),
    locatorType: row.locatorType,
    locatorValue: row.locatorValue.trim(),
    description: row.description || undefined,
  });
  ElMessage.success('元素已创建');
  addingRow.value = false;
  await load();
}

function beginEdit(row: UiElementItem & { _edit?: boolean; _snapshot?: string }) {
  row._snapshot = JSON.stringify({
    name: row.name,
    locatorType: row.locatorType,
    locatorValue: row.locatorValue,
    description: row.description,
  });
  row._edit = true;
}

function cancelEdit(row: UiElementItem & { _edit?: boolean; _snapshot?: string }) {
  if (row._snapshot) {
    const o = JSON.parse(row._snapshot);
    Object.assign(row, o);
  }
  row._edit = false;
  delete row._snapshot;
}

async function saveElement(row: UiElementItem & { _edit?: boolean; _snapshot?: string }) {
  if (!row.id) return;
  if (!row.name?.trim() || !row.locatorValue?.trim()) {
    ElMessage.warning('请填写名称与定位值');
    return;
  }
  await uiElementApi.update(row.id, {
    name: row.name.trim(),
    locatorType: row.locatorType,
    locatorValue: row.locatorValue.trim(),
    description: row.description || undefined,
  });
  ElMessage.success('已保存');
  row._edit = false;
  delete row._snapshot;
  await load();
}

function removeElement(row: UiElementItem) {
  if (!row.id) return;
  uiElementApi.delete(row.id).then(() => {
    ElMessage.success('已删除');
    load();
  });
}

onMounted(load);
watch(pageId, load);
</script>

<style scoped>
.detail-page { padding: 16px 24px; max-width: 1200px; margin: 0 auto; height: 100%; overflow: auto; }
.detail-top { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.detail-title { font-size: 18px; font-weight: 600; }
.content-card { background: #fff; border-radius: 10px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.06); }
.section-label { font-size: 14px; font-weight: 600; color: #1f2329; }
.page-form { margin-top: 8px; }
.toolbar-inner { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }
.mono { font-size: 12px; font-family: Consolas, monospace; color: #606266; }
</style>
