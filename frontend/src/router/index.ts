import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: () => (useAuthStore().isLoggedIn ? '/discover' : '/welcome')
    },
    {
      // 营销落地页：复刻参考稿的排版与配色，未登录也可访问
      path: '/welcome',
      name: 'welcome',
      component: () => import('@/views/LandingView.vue'),
      meta: { public: true, bare: true }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      // 公开只读分享页：未登录也能看，因此必须放在守卫白名单里
      path: '/share/:shareCode',
      name: 'share',
      component: () => import('@/views/ShareView.vue'),
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
      path: '/me',
      name: 'me',
      component: () => import('@/views/MeView.vue')
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
      path: '/guidebook/compare/:comparisonId',
      name: 'guidebook-compare',
      component: () => import('@/views/CompareView.vue')
    },
    {
      path: '/guidebook/:guidebookId',
      name: 'guidebook-detail',
      component: () => import('@/views/GuidebookView.vue')
    },
    {
      path: '/trip/:guidebookId',
      name: 'trip-companion',
      component: () => import('@/views/TripCompanionView.vue')
    },
    {
      // 守卫只校验登录；达人门禁由 ExpertInterceptor 负责，前端不复制白名单逻辑
      path: '/expert',
      name: 'expert',
      component: () => import('@/views/ExpertView.vue')
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
