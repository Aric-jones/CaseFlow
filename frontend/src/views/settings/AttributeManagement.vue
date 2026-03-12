<template>
  <div style="padding: 24px">
    <div style="display: flex; justify-content: space-between; margin-bottom: 16px">
      <h3>用例属性管理</h3>
      <a-button type="primary" @click="editId = null; form.name = ''; form.options = ''; form.multiSelect = false; form.required = false; form.nodeTypeLimit = ''; form.displayType = 'DROPDOWN'; showModal = true">
        <PlusOutlined /> 新增属性
      </a-button>
    </div>
    <a-table :columns="columns" :data-source="attrs" row-key="id" :loading="loading" :pagination="false" size="middle">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'options'">
          <a-tag v-for="o in record.options" :key="o">{{ o }}</a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button type="link" size="small" @click="startEdit(record)">编辑</a-button>
            <a-button type="link" size="small" danger @click="customAttributeApi.delete(record.id).then(loadAttrs)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>
    <a-modal v-model:open="showModal" :title="editId ? '编辑属性' : '新增属性'" @ok="save">
      <a-form layout="vertical">
        <a-form-item label="名称"><a-input v-model:value="form.name" /></a-form-item>
        <a-form-item label="属性值（逗号分隔）"><a-input v-model:value="form.options" placeholder="值1,值2,值3" /></a-form-item>
        <a-form-item label="是否多选"><a-switch v-model:checked="form.multiSelect" /></a-form-item>
        <a-form-item label="是否必填"><a-switch v-model:checked="form.required" /></a-form-item>
        <a-form-item label="限制节点类型">
          <a-select v-model:value="form.nodeTypeLimit" allow-clear placeholder="不限制"
            :options="[{value:'',label:'不限制'},{value:'TITLE',label:'用例标题'},{value:'PRECONDITION',label:'前置条件'},{value:'STEP',label:'步骤'},{value:'EXPECTED',label:'预期结果'}]" />
        </a-form-item>
        <a-form-item label="展示形式">
          <a-select v-model:value="form.displayType" :options="[{value:'DROPDOWN',label:'下拉框'},{value:'TILE',label:'平铺'}]" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { PlusOutlined } from '@ant-design/icons-vue';
import { customAttributeApi } from '../../api';
import { useAppStore } from '../../stores/app';
import type { CustomAttribute } from '../../types';

const store = useAppStore();
const attrs = ref<CustomAttribute[]>([]);
const loading = ref(false);
const showModal = ref(false);
const editId = ref<string | null>(null);
const form = reactive({ name: '', options: '', multiSelect: false, required: false, nodeTypeLimit: '', displayType: 'DROPDOWN' });

async function loadAttrs() {
  if (!store.currentProject) return;
  loading.value = true;
  try { attrs.value = (await customAttributeApi.list(store.currentProject.id)).data; } finally { loading.value = false; }
}
watch(() => store.currentProject, loadAttrs);
onMounted(loadAttrs);

function startEdit(attr: CustomAttribute) {
  editId.value = attr.id;
  form.name = attr.name; form.options = attr.options.join(',');
  form.multiSelect = attr.multiSelect === 1; form.required = attr.required === 1; form.nodeTypeLimit = attr.nodeTypeLimit || '';
  form.displayType = attr.displayType; showModal.value = true;
}

async function save() {
  const data = { projectId: store.currentProject?.id, name: form.name,
    options: form.options.split(',').map(s => s.trim()).filter(Boolean),
    multiSelect: form.multiSelect ? 1 : 0, required: form.required ? 1 : 0, nodeTypeLimit: form.nodeTypeLimit || null, displayType: form.displayType };
  if (editId.value) { await customAttributeApi.update(editId.value, data); }
  else { await customAttributeApi.create(data); }
  message.success('保存成功'); showModal.value = false; loadAttrs();
}

const columns = [
  { title: '属性名', dataIndex: 'name' }, { title: '属性值', key: 'options' },
  { title: '多选', dataIndex: 'multiSelect', width: 70, customRender: ({ text }: any) => text ? '是' : '否' },
  { title: '必填', dataIndex: 'required', width: 70, customRender: ({ text }: any) => text ? '是' : '否' },
  { title: '限制节点', dataIndex: 'nodeTypeLimit', width: 110, customRender: ({ text }: any) => text || '不限制' },
  { title: '展示', dataIndex: 'displayType', width: 80 },
  { title: '操作', key: 'action', width: 120 },
];
</script>
