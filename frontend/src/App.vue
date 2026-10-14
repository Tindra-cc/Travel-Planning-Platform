<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
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
    <el-menu mode="horizontal" router :default-active="$route.path">
      <template v-if="auth.isLoggedIn">
        <el-menu-item index="/profile">个人信息</el-menu-item>
        <el-menu-item index="/chat">AI规划对话</el-menu-item>
        <el-menu-item index="/guidebook">旅行攻略</el-menu-item>
      </template>
      <div class="spacer" />
      <div class="user-area">
        <span v-if="auth.userInfo" class="nickname">{{ auth.userInfo.nickname }}</span>
        <el-button v-if="auth.isLoggedIn" size="small" text @click="onLogout">退出登录</el-button>
        <el-button v-else size="small" text @click="router.push({ name: 'login' })">登录</el-button>
      </div>
    </el-menu>

    <router-view style="padding: 20px; max-width: 1200px; margin: 0 auto"></router-view>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
}
.spacer {
  flex: 1;
}
.user-area {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-right: 20px;
}
.nickname {
  color: #606266;
  font-size: 14px;
}
:deep(.el-menu) {
  display: flex;
  align-items: center;
}
</style>
