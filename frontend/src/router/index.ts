import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/discover'
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/discover',
      name: 'discover',
      component: () => import('@/views/DiscoverView.vue')
    },
    {
      path: '/discover/:postId',
      name: 'guide-detail',
      component: () => import('@/views/GuideDetailView.vue')
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue')
    },
    {
      path: '/chat',
      name: 'chat',
      component: () => import('@/views/ChatView.vue')
    },
    {
      path: '/guidebook',
      name: 'guidebook',
      component: () => import('@/views/GuidebookView.vue')
    },
    {
      path: '/guidebook/:guidebookId',
      name: 'guidebook-detail',
      component: () => import('@/views/GuidebookView.vue')
    },
    {
      // 兜底：任何未匹配路径都不能让 router-view 空渲染（否则白屏）
      path: '/:pathMatch(.*)*',
      redirect: '/discover'
    }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { path: '/discover' }
  }
  return true
})

export default router
