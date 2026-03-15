<template>
  <div class="profile-page">
    <div class="page-header">
      <h2>个人信息</h2>
    </div>
    <el-card class="profile-card">
      <el-descriptions :column="1" border label-class-name="profile-desc-label">
        <el-descriptions-item label="用户名">{{ store.user?.username }}</el-descriptions-item>
        <el-descriptions-item label="显示名">
          <span v-if="!editingName" style="display:flex;align-items:center;gap:8px">
            {{ store.user?.displayName }}
            <el-button text size="small" @click="startEditName">
              <el-icon><Edit /></el-icon>
            </el-button>
          </span>
          <span v-else style="display:flex;align-items:center;gap:8px">
            <el-input v-model="newDisplayName" size="small" style="width:200px"
              @keyup.enter="saveDisplayName" />
            <el-button type="primary" size="small" :loading="savingName" @click="saveDisplayName">保存</el-button>
            <el-button size="small" @click="editingName = false">取消</el-button>
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="角色">{{ roleText(store.user?.role) }}</el-descriptions-item>
        <el-descriptions-item label="身份">{{ identityText(store.user?.identity) }}</el-descriptions-item>
      </el-descriptions>

      <el-divider />
      <h4 style="margin-bottom:16px;font-size:15px;font-weight:600">修改密码</h4>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="100px" style="max-width:400px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="输入原密码" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saving" @click="changePassword">保存密码</el-button>
          <el-button style="margin-left:8px" @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import { authApi, userApi } from '../api';
import { useAppStore } from '../stores/app';

const store = useAppStore();
const saving = ref(false);
const pwdFormRef = ref();

const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, message: '新密码至少6位', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: (_: any, v: string, cb: any) => v === pwdForm.newPassword ? cb() : cb(new Error('两次密码不一致')), trigger: 'blur' },
  ],
};

const editingName = ref(false);
const newDisplayName = ref('');
const savingName = ref(false);

function startEditName() {
  newDisplayName.value = store.user?.displayName || '';
  editingName.value = true;
}

async function saveDisplayName() {
  const name = newDisplayName.value.trim();
  if (!name) { ElMessage.error('显示名不能为空'); return; }
  if (!store.user) return;
  savingName.value = true;
  try {
    await userApi.updateDisplayName(store.user.id, name);
    store.user.displayName = name;
    editingName.value = false;
    ElMessage.success('显示名已更新');
  } catch { ElMessage.error('更新失败'); }
  finally { savingName.value = false; }
}

function roleText(role?: string) {
  return ({ SUPER_ADMIN: '超管', ADMIN: '管理员', MEMBER: '成员' } as any)[role || ''] || '-';
}
function identityText(identity?: string) {
  return ({ TEST: '测试', DEV: '研发', PRODUCT: '产品' } as any)[identity || ''] || '-';
}
function resetForm() { Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirmPassword: '' }); }

async function changePassword() {
  await pwdFormRef.value?.validate();
  saving.value = true;
  try {
    await authApi.changePassword(pwdForm.oldPassword, pwdForm.newPassword);
    ElMessage.success('密码修改成功，请重新登录');
    store.logout();
    window.location.href = '/login';
  } finally { saving.value = false; }
}
</script>

<style scoped>
.profile-page { padding: 24px; height: 100%; overflow: auto; background: #f0f2f5; }
.page-header { margin-bottom: 16px; }
.page-header h2 { font-size: 18px; font-weight: 600; color: #1f2329; margin: 0; }
.profile-card { border-radius: 10px !important; }
:deep(.profile-desc-label) { width: 50px !important; }
:deep(.el-descriptions) { width: 500px !important; }
</style>
