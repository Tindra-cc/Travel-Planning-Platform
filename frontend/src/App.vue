<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Compass, MapLocation, Suitcase, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

// 菜单高亮按路径首段取：攻略详情页 /discover/12 也要让「推荐」保持选中
const activeMenu = computed(() => `/${route.path.split('/')[1] ?? ''}`)

// 营销页自带独立导航，隐藏应用外壳避免双层导航
const isBare = computed(() => route.meta.bare === true)

// 递增后 router-view 以新 key 重新挂载当前视图，实现“再次跳转即刷新”
const viewKey = ref(0)

function refreshView() {
  viewKey.value++
}

// 点击当前所在导航项时，路由不会变化，改为重新挂载刷新数据
function onMenuSelect(index: string) {
  if (index === route.path) refreshView()
}

function goHome() {
  if (route.path === '/' || route.path === '/discover') refreshView()
  else router.push('/')
}

async function onLogout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}

onMounted(async () => {
  if (auth.isLoggedIn && !auth.userInfo) {
    try {
      await auth.fetchMe()
    } catch {
      // token 失效由拦截器处理
    }
  }
})
</script>

<template>
  <div class="app">
    <header v-if="!isBare" class="app-nav">
      <div class="nav-inner">
        <div class="brand" @click="goHome">
          <span class="brand-mark">✈</span>
          <span class="brand-text">AI 旅行规划助理</span>
        </div>

        <el-menu
          v-if="auth.isLoggedIn"
          class="nav-menu"
          mode="horizontal"
          router
          :ellipsis="false"
          :default-active="activeMenu"
          @select="onMenuSelect"
        >
          <el-menu-item index="/discover">
            <el-icon><Compass /></el-icon>
            <span>推荐</span>
          </el-menu-item>
          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <span>对话</span>
          </el-menu-item>
          <el-menu-item index="/guidebook">
            <el-icon><MapLocation /></el-icon>
            <span>攻略</span>
          </el-menu-item>
          <el-menu-item v-if="auth.userInfo?.expertEligible" index="/expert">
            <el-icon><Suitcase /></el-icon>
            <span>达人</span>
          </el-menu-item>
          <el-menu-item index="/me">
            <el-icon><User /></el-icon>
            <span>个人中心</span>
          </el-menu-item>
        </el-menu>

        <div class="nav-user">
          <template v-if="auth.isLoggedIn">
            <el-button
              v-if="auth.userInfo"
              class="nickname"
              size="small"
              text
              @click="router.push('/me')"
            >
              {{ auth.userInfo.nickname }}
            </el-button>
            <el-button size="small" text @click="onLogout">退出登录</el-button>
          </template>
          <el-button v-else size="small" @click="router.push({ name: 'login' })">登录</el-button>
        </div>
      </div>
    </header>

    <main class="app-main" :class="{ 'app-main--bare': isBare }">
      <router-view :key="`${route.fullPath}#${viewKey}`" />
    </main>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  background: var(--bg-page);
}

.app-nav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border);
}

.nav-inner {
  display: flex;
  align-items: center;
  gap: var(--space-6);
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 var(--space-6);
  height: 64px;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  background: var(--grad-brand);
  color: #fff;
  font-size: 16px;
}
.brand-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-1);
  letter-spacing: 0.2px;
}

.nav-menu {
  flex: 1;
  border-bottom: none;
  background: transparent;
}
.nav-menu :deep(.el-menu-item) {
  height: 64px;
  border-bottom: none;
  color: var(--text-2);
  font-weight: 500;
  gap: 4px;
}
.nav-menu :deep(.el-menu-item:hover) {
  background: transparent;
  color: var(--brand-600);
}
.nav-menu :deep(.el-menu-item.is-active) {
  color: var(--brand-600);
  border-bottom: 2px solid var(--brand-500);
}

.nav-user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}
.nickname {
  color: var(--text-2);
  font-size: 14px;
}

.app-main {
  max-width: 1200px;
  margin: 0 auto;
  padding: var(--space-6);
}
.app-main--bare {
  max-width: none;
  padding: 0;
}

@media (max-width: 720px) {
  .nav-inner {
    gap: var(--space-2);
    padding: 0 var(--space-3);
  }
  .brand-text {
    display: none;
  }
  .nav-menu :deep(.el-menu-item) {
    padding: 0 10px;
    gap: 0;
  }
  .nav-menu :deep(.el-menu-item span) {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
    white-space: nowrap;
  }
  .nickname {
    display: none;
  }
  .app-main {
    padding: var(--space-3);
  }
}
</style>
