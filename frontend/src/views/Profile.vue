<template>
  <div style="padding: 24px; max-width: 720px">
    <h3 style="margin-bottom: 16px">个人信息</h3>
    <a-card :bordered="true">
      <a-descriptions :column="1" size="small" style="margin-bottom: 16px">
        <a-descriptions-item label="用户名">{{ store.user?.username }}</a-descriptions-item>
        <a-descriptions-item label="显示名">{{ store.user?.displayName }}</a-descriptions-item>
        <a-descriptions-item label="角色">{{ roleText(store.user?.role) }}</a-descriptions-item>
        <a-descriptions-item label="身份">{{ identityText(store.user?.identity) }}</a-descriptions-item>
      </a-descriptions>

      <a-divider />
      <h4 style="margin-bottom: 12px">修改密码</h4>
      <a-form layout="vertical">
        <a-form-item label="原密码" required>
          <a-input-password v-model:value="oldPassword" placeholder="输入原密码" />
        </a-form-item>
        <a-form-item label="新密码" required>
          <a-input-password v-model:value="newPassword" placeholder="至少6位" />
        </a-form-item>
        <a-form-item label="确认新密码" required>
          <a-input-password v-model:value="confirmPassword" placeholder="再次输入新密码" />
        </a-form-item>
      </a-form>
      <a-space>
        <a-button type="primary" :loading="saving" @click="changePassword">保存密码</a-button>
        <a-button @click="resetForm">重置</a-button>
      </a-space>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { authApi } from '../api';
import { useAppStore } from '../stores/app';

const store = useAppStore();
const oldPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const saving = ref(false);

function roleText(role?: string) {
  const map: Record<string, string> = { SUPER_ADMIN: '超管', ADMIN: '管理员', MEMBER: '成员' };
  return role ? (map[role] || role) : '-';
}

function identityText(identity?: string) {
  const map: Record<string, string> = { TEST: '测试', DEV: '研发', PRODUCT: '产品' };
  return identity ? (map[identity] || identity) : '-';
}

function resetForm() {
  oldPassword.value = '';
  newPassword.value = '';
  confirmPassword.value = '';
}

async function changePassword() {
  if (!oldPassword.value.trim()) { message.error('请输入原密码'); return; }
  if (!newPassword.value.trim()) { message.error('请输入新密码'); return; }
  if (newPassword.value.length < 6) { message.error('新密码长度至少6位'); return; }
  if (newPassword.value !== confirmPassword.value) { message.error('两次输入的新密码不一致'); return; }
  saving.value = true;
  try {
    await authApi.changePassword(oldPassword.value, newPassword.value);
    message.success('密码修改成功，请重新登录');
    store.logout();
    window.location.href = '/login';
  } finally {
    saving.value = false;
  }
}
</script>
