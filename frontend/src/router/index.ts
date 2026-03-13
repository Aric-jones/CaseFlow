import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('../views/Login.vue') },
    {
      path: '/',
      component: () => import('../views/Layout.vue'),
      redirect: '/cases',
      children: [
        { path: 'cases', component: () => import('../views/CaseHome.vue') },
        { path: 'test-plans', component: () => import('../views/TestPlan.vue') },
        { path: 'recycle-bin', component: () => import('../views/RecycleBin.vue') },
        { path: 'test-plan-recycle-bin', component: () => import('../views/TestPlanRecycleBin.vue') },
        { path: 'profile', component: () => import('../views/Profile.vue') },
        { path: 'settings/members', component: () => import('../views/settings/MemberManagement.vue') },
        { path: 'settings/attributes', component: () => import('../views/settings/AttributeManagement.vue') },
        { path: 'settings/projects', component: () => import('../views/settings/ProjectManagement.vue') },
      ],
    },
    { path: '/mind-map/:caseSetId', component: () => import('../views/MindMapEditor.vue') },
    { path: '/review/:caseSetId', component: () => import('../views/ReviewPage.vue') },
    { path: '/test-plan/:planId/execute', component: () => import('../views/TestPlanExecution.vue') },
  ],
});

router.beforeEach((to) => {
  const token = localStorage.getItem('token');
  if (!token && to.path !== '/login') return { path: '/login' };
  if (token && to.path === '/login') return { path: '/' };
  return true;
});

export default router;
