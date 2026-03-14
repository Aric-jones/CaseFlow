<template>
  <div class="login-bg">
    <el-card class="login-card" shadow="always">
      <div class="login-title">
        <el-icon size="32" color="#1677ff"><Document /></el-icon>
        <h1>CaseFlow</h1>
        <p class="subtitle">测试用例管理平台</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码"
            :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width:100%;height:44px;font-size:16px"
            @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
      <p class="hint">默认账号: admin / wps123456</p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { User, Lock } from '@element-plus/icons-vue';
import { authApi, projectApi } from '../api';
import { useAppStore } from '../stores/app';

const router = useRouter();
const store = useAppStore();
const loading = ref(false);
const formRef = ref();
const form = reactive({ username: '', password: '' });
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
};

async function handleLogin() {
  await formRef.value?.validate();
  loading.value = true;
  try {
    const res = await authApi.login(form.username, form.password);
    localStorage.setItem('token', res.data.token);
    const [userRes, projRes] = await Promise.all([authApi.currentUser(), projectApi.list()]);
    store.setUser(userRes.data);
    store.setProjects(projRes.data);
    if (!store.currentProject && projRes.data.length > 0) store.setCurrentProject(projRes.data[0]);
    ElMessage.success('登录成功');
    router.push('/');
  } catch { /* handled by interceptor */ } finally { loading.value = false; }
}
</script>

<style scoped>
.login-bg {
  height: 100vh; display: flex; justify-content: center; align-items: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card { width: 420px; border-radius: 12px; }
.login-title { text-align: center; margin-bottom: 32px; }
.login-title h1 { font-size: 28px; font-weight: 700; color: #1677ff; margin: 8px 0 4px; }
.subtitle { color: #909399; font-size: 14px; margin: 0; }
.hint { color: #909399; font-size: 12px; text-align: center; margin: 0; }
</style>
