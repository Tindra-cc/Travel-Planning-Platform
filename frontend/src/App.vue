<script setup lang="ts"></script>

<template>
  <div class="app">
    <el-menu mode="horizontal" router>
      <el-menu-item index="/profile">个人信息</el-menu-item>
      <el-menu-item index="/chat">AI规划对话</el-menu-item>
      <el-menu-item index="/guidebook">旅行攻略</el-menu-item>
    </el-menu>

    <router-view style="padding: 20px;max-width: 1000px;margin:0 auto"></router-view>
  </div>
</template>
<style scoped>
  .app{
    min-height: 100vh;
  }
</style>
