<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Compass, MapLocation, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()

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
    <header class="app-nav">
      <div class="nav-inner">
        <div class="brand" @click="router.push('/')">
          <span class="brand-mark">✈</span>
          <span class="brand-text">AI 旅行规划助理</span>
        </div>

        <el-menu
          v-if="auth.isLoggedIn"
          class="nav-menu"
          mode="horizontal"
          router
          :default-active="$route.path"
        >
          <el-menu-item index="/discover">
            <el-icon><Compass /></el-icon>
            <span>推荐</span>
          </el-menu-item>
          <el-menu-item index="/profile">
            <el-icon><User /></el-icon>
            <span>画像</span>
          </el-menu-item>
          <el-menu-item index="/chat">
            <el-icon><ChatDotRound /></el-icon>
            <span>对话</span>
          </el-menu-item>
          <el-menu-item index="/guidebook">
            <el-icon><MapLocation /></el-icon>
            <span>攻略</span>
          </el-menu-item>
        </el-menu>

        <div class="nav-user">
          <span v-if="auth.userInfo" class="nickname">{{ auth.userInfo.nickname }}</span>
          <el-button v-if="auth.isLoggedIn" size="small" text @click="onLogout">退出登录</el-button>
          <el-button v-else size="small" @click="router.push({ name: 'login' })">登录</el-button>
        </div>
      </div>
    </header>

    <main class="app-main">
      <router-view />
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
</style>
