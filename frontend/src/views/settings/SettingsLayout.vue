<template>
  <div class="settings-layout">
    <aside class="settings-sidebar">
      <h3 class="sidebar-title">系统管理</h3>
      <el-menu :default-active="activeMenu" class="settings-nav" @select="onSelect">
        <el-menu-item v-if="canMembers" index="/settings/members">
          <el-icon><User /></el-icon><span>成员管理</span>
        </el-menu-item>
        <el-menu-item v-if="canAttributes" index="/settings/attributes">
          <el-icon><Collection /></el-icon><span>用例属性管理</span>
        </el-menu-item>
        <el-menu-item v-if="canProjects" index="/settings/projects">
          <el-icon><OfficeBuilding /></el-icon><span>项目空间管理</span>
        </el-menu-item>
        <el-menu-item v-if="canRbac" index="/settings/rbac">
          <el-icon><Lock /></el-icon><span>权限管理</span>
        </el-menu-item>
        <el-menu-item v-if="canJobs" index="/settings/jobs">
          <el-icon><Timer /></el-icon><span>定时任务</span>
        </el-menu-item>
      </el-menu>
    </aside>
    <main class="settings-content">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useAppStore } from '../../stores/app';

const router = useRouter();
const route = useRoute();
const store = useAppStore();

const activeMenu = computed(() => route.path);

const canMembers = computed(() => store.hasAnyPermission('settings:members:add', 'settings:members:edit', 'settings:members:toggle'));
const canAttributes = computed(() => store.hasAnyPermission('settings:attributes:create', 'settings:attributes:edit', 'settings:attributes:delete'));
const canProjects = computed(() => store.hasAnyPermission('settings:projects:create', 'settings:projects:edit', 'settings:projects:delete'));
const canRbac = computed(() => store.hasAnyPermission('settings:rbac:role', 'settings:rbac:menu', 'settings:rbac:user'));
const canJobs = computed(() => store.hasAnyPermission('settings:jobs:create', 'settings:jobs:edit', 'settings:jobs:delete', 'settings:jobs:run'));

function onSelect(index: string) {
  router.push(index);
}

function getFirstAccessible(): string {
  if (canMembers.value) return '/settings/members';
  if (canAttributes.value) return '/settings/attributes';
  if (canProjects.value) return '/settings/projects';
  if (canRbac.value) return '/settings/rbac';
  if (canJobs.value) return '/settings/jobs';
  return '/dashboard';
}

onMounted(() => {
  const subPaths = ['/settings/members', '/settings/attributes', '/settings/projects', '/settings/rbac', '/settings/jobs'];
  if (!subPaths.includes(route.path)) {
    router.replace(getFirstAccessible());
  }
});
</script>

<style scoped>
.settings-layout {
  display: flex;
  height: 100%;
  background: #f0f2f5;
}

.settings-sidebar {
  width: 220px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.sidebar-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2329;
  padding: 20px 20px 12px;
  margin: 0;
}

.settings-nav {
  border-right: none;
}

.settings-nav .el-menu-item {
  height: 44px;
  line-height: 44px;
  font-size: 14px;
  padding-left: 20px !important;
}

.settings-nav .el-menu-item.is-active {
  background: #f0f7ff;
  color: #1677ff;
  font-weight: 500;
}

.settings-content {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}
</style>
