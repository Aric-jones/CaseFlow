<template>
  <div class="kv-table">
    <div v-for="(item, idx) in list" :key="idx" class="kv-row">
      <el-input v-model="item.key" size="small" placeholder="Key" style="width:160px" @input="emit('update:modelValue', list)" />
      <el-input v-model="item.value" size="small" placeholder="Value" style="flex:1" @input="emit('update:modelValue', list)" />
      <el-input v-model="item.desc" size="small" placeholder="描述(选填)" style="width:140px" @input="emit('update:modelValue', list)" />
      <el-button text type="danger" size="small" @click="removeRow(idx)">删除</el-button>
    </div>
    <el-button text type="primary" size="small" @click="addRow">+ 添加</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';

const props = defineProps<{ modelValue?: { key: string; value: string; desc?: string }[] }>();
const emit = defineEmits<{ (e: 'update:modelValue', v: any[]): void }>();

const list = ref<{ key: string; value: string; desc?: string }[]>([]);

watch(() => props.modelValue, (v) => {
  list.value = v ? [...v] : [];
}, { immediate: true });

function addRow() {
  list.value.push({ key: '', value: '', desc: '' });
  emit('update:modelValue', list.value);
}
function removeRow(idx: number) {
  list.value.splice(idx, 1);
  emit('update:modelValue', list.value);
}
</script>

<style scoped>
.kv-row { display: flex; gap: 8px; align-items: center; margin-bottom: 6px; }
</style>
