import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/Chat.vue')
  },
  {
    path: '/workplace',
    redirect: '/analysis'
  },
  {
    path: '/datasets',
    name: 'Datasets',
    component: () => import('@/views/Datasets.vue')
  },
  {
    path: '/analysis',
    name: 'Analysis',
    component: () => import('@/views/Analysis.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫 - 检查登录状态
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('token')
  
  if (to.meta.public) {
    // 公开页面，直接访问
    next()
  } else if (!token) {
    // 未登录，跳转到登录页
    next('/login')
  } else {
    // 已登录，正常访问
    next()
  }
})

export default router
