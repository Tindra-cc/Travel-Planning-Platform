<template>
  <div class="guidebook-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>旅行攻略</span>
        </div>
      </template>

      <div class="empty-state">
        <div class="empty-ico">🗺️</div>
        <h3>还没有生成攻略</h3>
        <p>在「对话」里描述你的旅行需求，信息收集完成后即可一键生成行程与攻略。</p>
        <el-button type="primary" @click="router.push('/chat')">去对话</el-button>
        <p class="empty-note">攻略生成（FR-3）将在 Sprint 2 接入</p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: 72px var(--space-6);
  text-align: center;
}
.empty-ico {
  font-size: 52px;
  line-height: 1;
}
.empty-state h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
}
.empty-state p {
  margin: 0;
  max-width: 420px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-2);
}
.empty-state .el-button {
  margin-top: var(--space-2);
}
.empty-note {
  font-size: 12px;
  color: var(--text-3);
}
</style>
