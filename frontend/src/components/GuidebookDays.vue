<template>
  <div class="guidebook-days">
    <section v-for="day in days" :key="String(day.day)" class="day-block">
      <h3>Day {{ day.day }}</h3>
      <el-timeline v-if="day.nodes?.length">
        <el-timeline-item
          v-for="(node, index) in day.nodes"
          :key="node.nodeId ?? index"
          :timestamp="timeText(node)"
          placement="top"
        >
          <div class="node-card">
            <strong>{{ node.place?.name || typeLabel(node.type) }}</strong>
            <el-tag size="small" type="info">{{ typeLabel(node.type) }}</el-tag>
            <p v-if="node.note" class="note">{{ node.note }}</p>
            <p v-if="node.openingHours" class="hours">营业时间：{{ node.openingHours }}</p>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="本日暂无安排" />
    </section>
  </div>
</template>

<script setup lang="ts">
import type { GuidebookDay, GuidebookNode } from '@/types/api'

defineProps<{ days: GuidebookDay[] | null }>()

const TYPE_LABELS: Record<string, string> = {
  activity: '活动',
  meal: '餐饮',
  transport: '交通',
  stay: '住宿',
  free: '自由活动'
}

function typeLabel(type: string | null | undefined) {
  return TYPE_LABELS[type ?? ''] ?? '活动'
}

function timeText(node: GuidebookNode) {
  const start = node.timeSlot?.start
  const end = node.timeSlot?.end
  if (start && end) return `${start} - ${end}`
  return start ?? ''
}
</script>

<style scoped>
.day-block {
  margin-bottom: 24px;
}
.day-block h3 {
  margin: 0 0 var(--space-3);
  font-size: 15px;
  color: var(--text-1);
}
.node-card {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.note,
.hours {
  width: 100%;
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--text-3);
}
</style>
