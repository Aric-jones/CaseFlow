<template>
  <div class="app-layout">
    <header class="app-header">
      <div class="header-left">
        <div class="logo" @click="$router.push('/')">
          <el-icon size="20" color="#1677ff"><Document /></el-icon>
          <span class="logo-text">CaseFlow</span>
        </div>
        <el-menu mode="horizontal" :default-active="activeKey" class="top-menu"
          :ellipsis="false" @select="(key: string) => $router.push(key)">
          <el-menu-item index="/dashboard">
            <el-icon><Monitor /></el-icon><span>工作台</span>
          </el-menu-item>
          <el-menu-item index="/cases">
            <el-icon><Document /></el-icon><span>用例管理</span>
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
            <el-menu-item v-if="store.user?.role === 'SUPER_ADMIN'" index="/settings/rbac">权限管理</el-menu-item>
          </el-sub-menu>
        </el-menu>
      </div>
      <div class="header-right">
        <el-select :model-value="store.currentProject?.id" @change="handleProjectChange"
          style="width:160px" placeholder="选择项目" size="default">
          <el-option v-for="p in store.projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>

        <!-- 通知铃铛 -->
        <el-popover placement="bottom-end" :width="380" trigger="click" @show="loadNotifications">
          <template #reference>
            <el-badge :value="unreadCount > 0 ? unreadCount : undefined" :hidden="unreadCount === 0"
                       :max="99" class="notification-badge">
              <el-icon :size="20" class="notification-bell"><Bell /></el-icon>
            </el-badge>
          </template>
          <div class="notif-panel">
            <div class="notif-header">
              <span class="notif-title">消息通知</span>
              <el-button v-if="unreadCount > 0" text size="small" type="primary" @click="markAll">全部已读</el-button>
            </div>
            <div class="notif-list">
              <el-empty v-if="!notifications.length" description="暂无通知" :image-size="50" />
              <div v-for="n in notifications" :key="n.id"
                   :class="['notif-item', { unread: !n.isRead }]"
                   @click="handleNotifClick(n)">
                <div class="notif-item-title">{{ n.title }}</div>
                <div class="notif-item-content">{{ n.content }}</div>
                <div class="notif-item-time">{{ fmtTime(n.createdAt) }}</div>
              </div>
            </div>
          </div>
        </el-popover>

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
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Bell, Monitor } from '@element-plus/icons-vue';
import { authApi, projectApi, notificationApi } from '../api';
import { useAppStore } from '../stores/app';
import { useWebSocket, setUnreadCount } from '../composables/useWebSocket';
import type { Notification } from '../types';

const router = useRouter();
const route = useRoute();
const store = useAppStore();

const { unreadCount, latestNotification, connect, disconnect } = useWebSocket();
const notifications = ref<Notification[]>([]);

const activeKey = computed(() => {
  const p = route.path;
  if (p === '/dashboard' || p === '/') return '/dashboard';
  if (p.startsWith('/cases') || p === '/recycle-bin') return '/cases';
  if (p.startsWith('/test-plans') || p.startsWith('/test-plan')) return '/test-plans';
  if (p.startsWith('/settings')) return '/settings';
  return '/dashboard';
});

function handleProjectChange(val: string) {
  const proj = store.projects.find(p => p.id === val);
  if (proj) store.setCurrentProject(proj);
}

function handleUserMenu(command: string) {
  if (command === 'profile') { router.push('/profile'); return; }
  if (command === 'logout') { disconnect(); store.logout(); router.push('/login'); }
}

async function loadNotifications() {
  try {
    notifications.value = (await notificationApi.list()).data;
  } catch { /* ignore */ }
}

async function fetchUnreadCount() {
  try {
    const res = await notificationApi.unreadCount();
    setUnreadCount(res.data);
  } catch { /* ignore */ }
}

async function markAll() {
  await notificationApi.markAllRead();
  setUnreadCount(0);
  notifications.value.forEach(n => n.isRead = 1);
}

async function handleNotifClick(n: Notification) {
  if (!n.isRead) {
    await notificationApi.markRead(n.id);
    n.isRead = 1;
    setUnreadCount(Math.max(0, unreadCount.value - 1));
  }
  if (n.link) router.push(n.link);
}

function fmtTime(t: string) { return t ? t.replace('T', ' ').substring(0, 16) : ''; }

// 监听 WebSocket 推送的新通知，在列表中插入
watch(latestNotification, (n) => {
  if (n) {
    notifications.value.unshift(n);
    ElMessage.info({ message: n.title + '：' + (n.content || '').substring(0, 40), duration: 4000 });
  }
});

onMounted(async () => {
  if (!store.user) {
    try {
      const [uRes, pRes] = await Promise.all([authApi.currentUser(), projectApi.list()]);
      store.setUser(uRes.data);
      store.setProjects(pRes.data);
      if (!store.currentProject && pRes.data.length > 0) store.setCurrentProject(pRes.data[0]);
    } catch { router.push('/login'); return; }
  }
  connect();
  fetchUnreadCount();
});

onUnmounted(() => { disconnect(); });
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
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}
.header-left { display: flex; align-items: center; flex: 1; min-width: 0; }
.header-right { display: flex; align-items: center; gap: 16px; flex-shrink: 0; }

.logo { display: flex; align-items: center; gap: 8px; cursor: pointer; margin-right: 8px; flex-shrink: 0; padding: 0 4px; }
.logo-text { font-size: 17px; font-weight: 700; color: #1677ff; letter-spacing: -0.3px; }

.notification-badge { cursor: pointer; }
.notification-bell { color: #606266; cursor: pointer; transition: color 0.2s; }
.notification-bell:hover { color: #1677ff; }

.notif-panel { max-height: 420px; display: flex; flex-direction: column; }
.notif-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0; }
.notif-title { font-size: 15px; font-weight: 600; color: #1f2329; }
.notif-list { overflow-y: auto; max-height: 360px; margin-top: 8px; }
.notif-item { padding: 10px 8px; border-radius: 6px; cursor: pointer; transition: background 0.2s; border-bottom: 1px solid #fafafa; }
.notif-item:hover { background: #f5f7fa; }
.notif-item.unread { background: #f0f7ff; }
.notif-item-title { font-size: 13px; font-weight: 600; color: #1f2329; margin-bottom: 3px; }
.notif-item-content { font-size: 12px; color: #606266; margin-bottom: 3px; line-height: 1.4; }
.notif-item-time { font-size: 11px; color: #c0c4cc; }

.user-dropdown { cursor: pointer; }
.user-trigger { display: flex; align-items: center; gap: 8px; padding: 4px 8px; border-radius: 8px; transition: background 0.2s; }
.user-name { font-size: 14px; color: #1f2329; font-weight: 500; }

.app-content { flex: 1; overflow: hidden; }
</style>
