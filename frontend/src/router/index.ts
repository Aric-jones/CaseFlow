import { createRouter, createWebHistory } from 'vue-router';
import { ElMessage } from 'element-plus';
import { authApi } from '../api';
import { useAppStore } from '../stores/app';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/Login.vue') },
    {
      path: '/',
      component: () => import('../views/Layout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { permissions: ['dashboard:view'] } },
        { path: 'cases', component: () => import('../views/CaseHome.vue'), meta: { permissions: ['cases:view'] } },
        { path: 'test-plans', component: () => import('../views/TestPlan.vue'), meta: { permissions: ['plans:view'] } },
        {
          path: 'api-auto',
          component: () => import('../views/apiAuto/ApiAutoLayout.vue'),
          meta: { permissions: ['api:def:view'] },
          redirect: '/api-auto/defs',
          children: [
            { path: 'defs', component: () => import('../views/apiAuto/ApiDefList.vue') },
            { path: 'scenarios', component: () => import('../views/apiAuto/ApiScenarioList.vue') },
            { path: 'plans', component: () => import('../views/apiAuto/ApiPlanList.vue') },
            { path: 'executions', component: () => import('../views/apiAuto/ApiExecutionList.vue') },
            { path: 'env', component: () => import('../views/apiAuto/ApiEnvManager.vue') },
          ],
        },
        { path: 'recycle-bin', component: () => import('../views/RecycleBin.vue'), meta: { permissions: ['recycle:view'] } },
        { path: 'test-plan-recycle-bin', component: () => import('../views/TestPlanRecycleBin.vue'), meta: { permissions: ['plans:view'] } },
        { path: 'profile', component: () => import('../views/Profile.vue') },
        {
          path: 'settings',
          component: () => import('../views/settings/SettingsLayout.vue'),
          meta: {
            permissions: [
              'settings:members:add', 'settings:members:edit', 'settings:members:toggle',
              'settings:attributes:create', 'settings:attributes:edit', 'settings:attributes:delete',
              'settings:projects:create', 'settings:projects:edit', 'settings:projects:delete',
              'settings:rbac:role', 'settings:rbac:menu', 'settings:rbac:user',
              'settings:jobs:create', 'settings:jobs:edit', 'settings:jobs:delete', 'settings:jobs:run',
            ],
          },
          children: [
            { path: 'members', component: () => import('../views/settings/MemberManagement.vue') },
            { path: 'attributes', component: () => import('../views/settings/AttributeManagement.vue') },
            { path: 'projects', component: () => import('../views/settings/ProjectManagement.vue') },
            { path: 'rbac', component: () => import('../views/settings/RbacManagement.vue') },
            { path: 'jobs', component: () => import('../views/settings/JobManagement.vue') },
          ],
        },
      ],
    },
    { path: '/mind-map/:caseSetId', component: () => import('../views/MindMapEditor.vue'), meta: { permissions: ['mindmap:view'] } },
    { path: '/review/:caseSetId', component: () => import('../views/ReviewPage.vue'), meta: { permissions: ['review:view'] } },
    { path: '/test-plan/create', component: () => import('../views/TestPlanForm.vue'), meta: { permissions: ['plans:create'] } },
    { path: '/test-plan/:planId/edit', component: () => import('../views/TestPlanForm.vue'), meta: { permissions: ['plans:edit'] } },
    { path: '/test-plan/:planId/execute', component: () => import('../views/TestPlanExecution.vue'), meta: { permissions: ['plans:execute'] } },
    { path: '/api-auto/def/:id', component: () => import('../views/apiAuto/ApiDefDetail.vue'), meta: { permissions: ['api:def:view'] } },
    { path: '/api-auto/scenario/:id', component: () => import('../views/apiAuto/ApiScenarioDetail.vue'), meta: { permissions: ['api:scenario:view'] } },
    { path: '/api-auto/plan/create', component: () => import('../views/apiAuto/ApiPlanForm.vue'), meta: { permissions: ['api:plan:create'] } },
    { path: '/api-auto/plan/:id/edit', component: () => import('../views/apiAuto/ApiPlanForm.vue'), meta: { permissions: ['api:plan:edit'] } },
    { path: '/api-auto/execution/:id', component: () => import('../views/apiAuto/ApiExecutionReport.vue'), meta: { permissions: ['api:execution:view'] } },
    { path: '/test', component: () => import('../views/settings/test.vue') },
  ],
});

async function ensurePermissions(): Promise<boolean> {
  const store = useAppStore();
  if (store.permissions.length > 0) return true;
  try {
    const res = await authApi.permissions();
    store.setPermissions(res.data.permissions || [], res.data.roles || []);
    return true;
  } catch {
    localStorage.removeItem('token');
    return false;
  }
}

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token');
  if (!token && to.path !== '/login') return { path: '/login' };
  if (token && to.path === '/login') return { path: '/' };

  const matched = to.matched.find(r => r.meta.permissions);
  const requiredPerms = matched?.meta.permissions as string[] | undefined;
  if (token && requiredPerms && requiredPerms.length > 0) {
    const loaded = await ensurePermissions();
    if (!loaded) return { path: '/login' };

    const store = useAppStore();
    if (!store.hasAnyPermission(...requiredPerms)) {
      ElMessage.warning('您没有权限，请联系管理员');
      if (to.path === '/dashboard') return { path: '/profile' };
      return { path: '/dashboard' };
    }
  }

  return true;
});

export default router;
