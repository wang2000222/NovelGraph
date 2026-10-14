import { createRouter, createWebHistory } from 'vue-router';
export default createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/account',
      component: () => import('./saas/AccountView.vue'),
      meta: { title: '账号与安全' },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('./saas/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/',
      name: 'projects',
      component: () => import('./saas/ProjectsView.vue'),
      meta: { title: '我的世界' },
    },
    {
      path: '/projects/:id/editor',
      name: 'editor',
      component: () => import('./saas/EditorView.vue'),
      meta: { title: '世界编辑器' },
    },
    {
      path: '/team',
      component: () => import('./saas/WorkspaceView.vue'),
      meta: { title: '空间与成员' },
    },
    {
      path: '/plans',
      component: () => import('./saas/PlansView.vue'),
      meta: { title: '套餐与账单' },
    },
    {
      path: '/admin',
      component: () => import('./saas/AdminView.vue'),
      meta: { title: '平台运营' },
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});
