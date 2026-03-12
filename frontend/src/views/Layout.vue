<template>
  <a-layout style="height: 100vh">
    <a-layout-header class="top-header">
      <div class="header-left">
        <div class="logo" @click="$router.push('/cases')">
          <FileTextOutlined style="font-size: 22px; color: #1677ff" />
          <span style="font-size: 17px; font-weight: 600; color: #1677ff; margin-left: 8px">CaseFlow</span>
        </div>
        <a-menu mode="horizontal" :selectedKeys="[activeKey]" style="border: none; flex: 1; margin-left: 24px">
          <a-menu-item key="/cases" @click="$router.push('/cases')">
            <FileTextOutlined /> 用例首页
          </a-menu-item>
          <a-menu-item key="/test-plans" @click="$router.push('/test-plans')">
            <ScheduleOutlined /> 测试计划
          </a-menu-item>
          <a-sub-menu v-if="store.user?.role !== 'MEMBER'" key="/settings">
            <template #title><SettingOutlined /> 系统设置</template>
            <a-menu-item key="/settings/members" @click="$router.push('/settings/members')">成员管理</a-menu-item>
            <a-menu-item key="/settings/attributes" @click="$router.push('/settings/attributes')">用例属性管理</a-menu-item>
            <a-menu-item key="/settings/projects" @click="$router.push('/settings/projects')">项目空间管理</a-menu-item>
          </a-sub-menu>
        </a-menu>
      </div>
      <div class="header-right">
        <a-select
          :value="store.currentProject?.id"
          @change="handleProjectChange"
          style="width: 160px"
          :options="store.projects.map(p => ({ label: p.name, value: p.id }))"
          placeholder="选择项目"
        />
        <a-dropdown placement="bottomRight">
          <a-space style="cursor: pointer; margin-left: 16px">
            <a-avatar size="small" style="background: #1677ff"><UserOutlined /></a-avatar>
            <span>{{ store.user?.displayName }}</span>
          </a-space>
          <template #overlay>
            <a-menu @click="handleUserMenu">
              <a-menu-item key="profile"><IdcardOutlined /> 个人信息</a-menu-item>
              <a-menu-item key="logout" danger><LogoutOutlined /> 退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </a-layout-header>
    <a-layout-content style="overflow: auto">
      <router-view />
    </a-layout-content>
  </a-layout>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import {
  FileTextOutlined, ScheduleOutlined, SettingOutlined,
  UserOutlined, LogoutOutlined, IdcardOutlined,
} from '@ant-design/icons-vue';
import { authApi, projectApi } from '../api';
import { useAppStore } from '../stores/app';

const router = useRouter();
const route = useRoute();
const store = useAppStore();

const activeKey = computed(() => {
  const p = route.path;
  if (p.startsWith('/cases')) return '/cases';
  if (p.startsWith('/test-plans')) return '/test-plans';
  if (p.startsWith('/settings')) return '/settings';
  return '/cases';
});

function handleProjectChange(val: string) {
  const proj = store.projects.find(p => p.id === val);
  if (proj) store.setCurrentProject(proj);
}

function handleUserMenu({ key }: { key: string }) {
  if (key === 'profile') { router.push('/profile'); return; }
  if (key === 'logout') { store.logout(); router.push('/login'); }
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
.top-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid #f0f0f0; padding: 0 24px;
  position: sticky; top: 0; z-index: 100; height: 56px; line-height: 56px;
}
.header-left { display: flex; align-items: center; flex: 1; }
.header-right { display: flex; align-items: center; }
.logo { display: flex; align-items: center; cursor: pointer; }
</style>
