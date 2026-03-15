<template>
  <div class="app-layout">
    <header class="app-header">
      <div class="header-left">
        <div class="logo" @click="$router.push('/cases')">
          <el-icon size="20" color="#1677ff"><Document /></el-icon>
          <span class="logo-text">CaseFlow</span>
        </div>
        <el-menu mode="horizontal" :default-active="activeKey" class="top-menu"
          :ellipsis="false" @select="(key: string) => $router.push(key)">
          <el-menu-item index="/cases">
            <el-icon><Document /></el-icon><span>用例首页</span>
          </el-menu-item>
          <el-menu-item index="/test-plans">
            <el-icon><Calendar /></el-icon><span>测试计划</span>
          </el-menu-item>
          <el-sub-menu index="/settings" v-if="store.user?.role !== 'MEMBER'">
            <template #title>
              <el-icon><Setting /></el-icon><span>系统设置</span>
            </template>
            <el-menu-item index="/settings/members">成员管理</el-menu-item>
            <el-menu-item index="/settings/attributes">用例属性管理</el-menu-item>
            <el-menu-item index="/settings/projects">项目空间管理</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>
      <div class="header-right">
        <el-select :model-value="store.currentProject?.id" @change="handleProjectChange"
          style="width:160px" placeholder="选择项目" size="default">
          <el-option v-for="p in store.projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-dropdown placement="bottom-end" @command="handleUserMenu" class="user-dropdown">
          <div class="user-trigger">
            <el-avatar :size="32" style="background:#1677ff;font-size:13px;cursor:pointer">
              {{ (store.user?.displayName || '?').charAt(0).toUpperCase() }}
            </el-avatar>
            <span class="user-name">{{ store.user?.displayName }}</span>
            <el-icon size="12" color="#909399"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>个人信息
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided style="color:#f56c6c">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
    <main class="app-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { authApi, projectApi } from '../api';
import { useAppStore } from '../stores/app';

const router = useRouter();
const route = useRoute();
const store = useAppStore();

const activeKey = computed(() => {
  const p = route.path;
  if (p.startsWith('/cases') || p === '/recycle-bin') return '/cases';
  if (p.startsWith('/test-plans') || p.startsWith('/test-plan')) return '/test-plans';
  if (p.startsWith('/settings')) return '/settings';
  return '/cases';
});

function handleProjectChange(val: string) {
  const proj = store.projects.find(p => p.id === val);
  if (proj) store.setCurrentProject(proj);
}

function handleUserMenu(command: string) {
  if (command === 'profile') { router.push('/profile'); return; }
  if (command === 'logout') { store.logout(); router.push('/login'); }
}

onMounted(async () => {
  if (!store.user) {
    try {
      const [uRes, pRes] = await Promise.all([authApi.currentUser(), projectApi.list()]);
      store.setUser(uRes.data);
      store.setProjects(pRes.data);
      if (!store.currentProject && pRes.data.length > 0) store.setCurrentProject(pRes.data[0]);
    } catch { router.push('/login'); }
  }
});
</script>

<style scoped>
.app-layout { display: flex; flex-direction: column; height: 100vh; background: #f0f2f5; }

.app-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff;
  padding: 0 24px;
  height: 56px;
  flex-shrink: 0;
  position: sticky; top: 0; z-index: 100;
  /* 用阴影代替下边框，避免和菜单active指示器叠加 */
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.header-left { display: flex; align-items: center; flex: 1; min-width: 0; }
.header-right { display: flex; align-items: center; gap: 16px; flex-shrink: 0; }

.logo { display: flex; align-items: center; gap: 8px; cursor: pointer; margin-right: 8px; flex-shrink: 0; padding: 0 4px; }
.logo-text { font-size: 17px; font-weight: 700; color: #1677ff; letter-spacing: -0.3px; }

.user-dropdown { cursor: pointer; }
.user-trigger { display: flex; align-items: center; gap: 8px; padding: 4px 8px; border-radius: 8px; transition: background 0.2s; }
.user-name { font-size: 14px; color: #1f2329; font-weight: 500; }

.app-content { flex: 1; overflow: hidden; }

</style>
