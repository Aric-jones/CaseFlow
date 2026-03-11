<template>
  <div class="login-bg">
    <a-card class="login-card">
      <div style="text-align: center; margin-bottom: 32px">
        <h1 style="color: #1677ff; margin-bottom: 8px">CaseFlow</h1>
        <a-typography-text type="secondary">测试用例管理平台</a-typography-text>
      </div>
      <a-form :model="form" @finish="handleLogin" size="large">
        <a-form-item name="username" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input v-model:value="form.username" placeholder="用户名">
            <template #prefix><UserOutlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="form.password" placeholder="密码">
            <template #prefix><LockOutlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading" block style="height: 44px">登 录</a-button>
        </a-form-item>
      </a-form>
      <a-typography-text type="secondary" style="font-size: 12px">默认账号: admin / wps123456</a-typography-text>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue';
import { authApi, projectApi } from '../api';
import { useAppStore } from '../stores/app';

const router = useRouter();
const store = useAppStore();
const loading = ref(false);
const form = reactive({ username: '', password: '' });

async function handleLogin() {
  loading.value = true;
  try {
    const res = await authApi.login(form.username, form.password);
    localStorage.setItem('token', res.data.token);
    const [userRes, projRes] = await Promise.all([authApi.currentUser(), projectApi.list()]);
    store.setUser(userRes.data);
    store.setProjects(projRes.data);
    if (!store.currentProject && projRes.data.length > 0) store.setCurrentProject(projRes.data[0]);
    message.success('登录成功');
    router.push('/');
  } catch { /* handled */ } finally { loading.value = false; }
}
</script>

<style scoped>
.login-bg {
  height: 100vh; display: flex; justify-content: center; align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card { width: 420px; border-radius: 12px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); }
</style>
