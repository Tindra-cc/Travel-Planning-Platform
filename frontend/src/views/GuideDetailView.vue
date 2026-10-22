<template>
  <div class="detail-page">
    <el-button text :icon="ArrowLeft" class="back" @click="goBack">返回推荐</el-button>

    <div v-if="loading" class="skeleton">
      <el-skeleton animated :rows="7" />
    </div>

    <div v-else-if="error" class="failed">
      <div class="failed-ico">🗺️</div>
      <h3>{{ error }}</h3>
      <el-button type="primary" @click="goBack">返回推荐</el-button>
    </div>

    <template v-else-if="detail">
      <section class="hero">
        <div class="gallery">
          <div class="main-img">
            <img
              v-if="currentImage && !mainFailed"
              :src="currentImage"
              :alt="detail.title"
              @error="mainFailed = true"
            />
            <div v-else class="cover-fallback">
              <span>{{ detail.destination.charAt(0) }}</span>
            </div>
          </div>
          <div v-if="images.length > 1" class="thumbs">
            <button
              v-for="(img, i) in images"
              :key="img"
              type="button"
              :class="['thumb', { active: i === activeIndex }]"
              @click="selectImage(i)"
            >
              <img v-if="!failedThumbs.has(img)" :src="img" alt="" loading="lazy" @error="failedThumbs.add(img)" />
              <span v-else class="thumb-fallback">图</span>
            </button>
          </div>
        </div>

        <div class="info">
          <h1 class="title">{{ detail.title }}</h1>

          <div class="meta">
            <span class="avatar" :style="{ background: avatarBg(detail.postId) }">
              {{ initial(detail.authorName) }}
            </span>
            <span>{{ detail.authorName || '旅行者' }}</span>
            <span class="dot">·</span>
            <span>目的地 {{ detail.destination }}</span>
            <span class="dot">·</span>
            <span>♡ {{ formatCount(detail.likeCount) }}</span>
            <span class="dot">·</span>
            <span>{{ formatCount(detail.viewCount) }} 浏览</span>
          </div>

          <div class="tags">
            <el-tag v-if="detail.durationDays" effect="plain" round>{{ detail.durationDays }} 天</el-tag>
            <el-tag v-if="detail.budgetLevel" effect="plain" round>{{ budgetLabel(detail.budgetLevel) }}</el-tag>
            <el-tag v-for="t in detail.tags || []" :key="t" effect="plain" round>{{ t }}</el-tag>
          </div>

          <p v-if="detail.summary" class="summary">{{ detail.summary }}</p>

          <div class="actions">
            <el-button type="primary" @click="goChat">按这个生成我的行程</el-button>
          </div>
        </div>
      </section>

      <section v-if="dayGroups.length || sharedSections.length" class="plan">
        <div class="plan-head">
          <h2>规划详情</h2>
          <span v-if="dayGroups.length" class="plan-sub">{{ dayGroups.length }} 天路线</span>
        </div>

        <div v-if="dayGroups.length" class="day-list">
          <article v-for="day in dayGroups" :key="day.day" class="day-card">
            <header class="day-head">
              <span class="day-num">Day {{ day.day }}</span>
              <span class="day-sub">第 {{ day.day }} 天</span>
            </header>
            <ul class="day-items">
              <li v-for="(it, i) in day.items" :key="i" class="day-item">
                <span class="day-ico">{{ kindIcon(it.kind) }}</span>
                <div class="day-item-body">
                  <div class="item-main">
                    <span class="item-name">{{ it.name }}</span>
                    <span v-if="it.price" class="item-price">{{ it.price }}</span>
                  </div>
                  <p v-if="it.note" class="item-note">{{ it.note }}</p>
                </div>
              </li>
            </ul>
          </article>
        </div>

        <div v-if="sharedSections.length" class="shared">
          <h3>全程参考</h3>
          <div class="sections">
            <div v-for="sec in sharedSections" :key="sec.type + sec.title" class="section">
              <div class="section-head">
                <span class="section-ico">
                  <el-icon><component :is="iconOf(sec.type)" /></el-icon>
                </span>
                <span class="section-title">{{ sec.title }}</span>
              </div>
              <ul class="items">
                <li v-for="(it, i) in sec.items" :key="i" class="item">
                  <div class="item-main">
                    <span class="item-name">{{ it.name }}</span>
                    <span v-if="it.price" class="item-price">{{ it.price }}</span>
                  </div>
                  <p v-if="it.note" class="item-note">{{ it.note }}</p>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Dish, House, KnifeFork, Location, Van } from '@element-plus/icons-vue'
import { getGuideDetail } from '@/api/guide'
import type { GuidePostDetail } from '@/types/api'

const BUDGET_LABELS: Record<string, string> = {
  budget: '穷游',
  economy: '经济',
  balanced: '均衡',
  comfort: '舒适',
  luxury: '奢华'
}

// type -> 图标；未知 type 落到 DEFAULT_ICON，避免后端加新模块时前端白屏
const SECTION_ICONS: Record<string, unknown> = {
  stay: House,
  food: KnifeFork,
  spot: Location,
  transport: Van
}
const DEFAULT_ICON = Dish

const route = useRoute()
const router = useRouter()

const detail = ref<GuidePostDetail | null>(null)
const loading = ref(false)
const error = ref('')
const activeIndex = ref(0)
const mainFailed = ref(false)
const failedThumbs = ref(new Set<string>())

const images = computed(() => (detail.value?.images ?? []).filter(Boolean))
const sections = computed(() => (detail.value?.planSections ?? []).filter((s) => s.items?.length))
const currentImage = computed(() => images.value[activeIndex.value] ?? null)

interface DayItem {
  name: string | null
  note: string | null
  price: string | null
  kind: string
}
interface DayGroup {
  day: number
  items: DayItem[]
}

const DAY_TITLE = /^(?:day|第)?\s*(\d+)\s*(?:天)?$/i
const KIND_ICONS: Record<string, string> = {
  spot: '📍',
  food: '🍜',
  transport: '🚇',
  stay: '🏨',
  free: '🌿'
}

function kindIcon(kind: string) {
  return KIND_ICONS[kind] ?? '📍'
}

// 达人发布的攻略已按「Day N」分节；历史演示数据是住宿/吃饭/景点分类，这里按天数自动排成逐日路线
const dayGroups = computed<DayGroup[]>(() => {
  const keyed = sections.value
    .map((section) => ({ section, match: section.title.trim().match(DAY_TITLE) }))
    .filter((entry) => entry.match)
    .map((entry) => ({
      day: Number(entry.match![1]),
      items: entry.section.items.map((item) => ({ ...item, kind: entry.section.type }))
    }))
    .sort((a, b) => a.day - b.day)
  if (keyed.length) return keyed

  const spots = sections.value.find((s) => s.type === 'spot')?.items ?? []
  const foods = sections.value.find((s) => s.type === 'food')?.items ?? []
  if (!spots.length && !foods.length) return []

  const requested = detail.value?.durationDays ?? 0
  const count = Math.min(Math.max(requested || spots.length || foods.length, 1), 10)
  const groups: DayGroup[] = Array.from({ length: count }, (_, i) => ({ day: i + 1, items: [] }))
  spots.forEach((item, i) => groups[i % count]?.items.push({ ...item, kind: 'spot' }))
  foods.forEach((item, i) => groups[i % count]?.items.push({ ...item, kind: 'food' }))
  for (const group of groups) {
    if (!group.items.length) {
      group.items.push({
        name: '自由活动',
        note: `留白半天，在${detail.value?.destination ?? '当地'}慢慢逛，按体力随时调整。`,
        price: null,
        kind: 'free'
      })
    }
  }
  return groups
})

const sharedSections = computed(() =>
  sections.value.filter((section) => !DAY_TITLE.test(section.title.trim()))
)

function iconOf(type: string) {
  return SECTION_ICONS[type] ?? DEFAULT_ICON
}

function selectImage(i: number) {
  activeIndex.value = i
  mainFailed.value = false
}

function initial(name: string | null) {
  return name?.charAt(0) || '旅'
}

function avatarBg(postId: string) {
  const hue = (Number(postId) * 47) % 360
  return `linear-gradient(135deg, hsl(${hue} 62% 52%), hsl(${(hue + 42) % 360} 68% 60%))`
}

function formatCount(n: number) {
  if (n >= 10000) return `${(n / 10000).toFixed(1)}万`
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

function budgetLabel(level: string) {
  return BUDGET_LABELS[level] ?? level
}

function goBack() {
  router.push('/discover')
}

function goChat() {
  // 与推荐页一致：把目的地/标题/天数带过去，让对话页预填首句
  const d = detail.value
  router.push({
    path: '/chat',
    query: d
      ? {
          postId: d.postId,
          destination: d.destination,
          title: d.title,
          durationDays: d.durationDays ? String(d.durationDays) : undefined
        }
      : {}
  })
}

async function load() {
  const id = String(route.params.postId ?? '')
  if (!id) {
    error.value = '攻略不存在'
    return
  }
  loading.value = true
  error.value = ''
  try {
    detail.value = await getGuideDetail(id)
    activeIndex.value = 0
    mainFailed.value = false
    failedThumbs.value = new Set()
  } catch {
    // 具体错误提示由 axios 拦截器统一弹出，这里给页面级兜底
    detail.value = null
    error.value = '攻略不存在或加载失败'
  } finally {
    loading.value = false
  }
}

watch(() => route.params.postId, load, { immediate: true })
</script>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.back {
  align-self: flex-start;
  color: var(--text-2);
}

.skeleton {
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: var(--space-6);
}

.failed {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
  padding: 72px var(--space-6);
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  text-align: center;
}
.failed-ico {
  font-size: 52px;
  line-height: 1;
}
.failed h3 {
  margin: 0;
  font-size: 17px;
  font-weight: 600;
  color: var(--text-1);
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: var(--space-5);
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: var(--space-5);
}

.gallery {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
}
.main-img {
  aspect-ratio: 16 / 10;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--brand-050);
}
.main-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover-fallback {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  background: var(--grad-brand);
  color: #fff;
}
.cover-fallback span {
  font-size: 60px;
  font-weight: 700;
  opacity: 0.9;
}

.thumbs {
  display: flex;
  gap: var(--space-2);
  overflow-x: auto;
  padding-bottom: 2px;
}
.thumb {
  flex: 0 0 auto;
  width: 76px;
  height: 56px;
  padding: 0;
  border: 2px solid transparent;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--brand-050);
  cursor: pointer;
  transition: border-color 0.15s;
}
.thumb.active {
  border-color: var(--brand-500);
}
.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.thumb-fallback {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  background: var(--grad-brand);
  color: #fff;
  font-size: 12px;
}

.info {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  min-width: 0;
}
.title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.2px;
  color: var(--text-1);
}
.meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13px;
  color: var(--text-2);
}
.meta .dot {
  color: var(--text-3);
}
.avatar {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
.summary {
  margin: 0;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-2);
}
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-2);
}

.plan {
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  padding: var(--space-5);
}
.plan-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}
.plan-head h2 {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--text-1);
}
.plan-sub {
  font-size: 13px;
  color: var(--text-3);
}

.day-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.day-card {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
  background: linear-gradient(180deg, var(--brand-050) 0, transparent 88px);
}
.day-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.day-num {
  font-size: 16px;
  font-weight: 700;
  color: var(--brand-600);
}
.day-sub {
  font-size: 12px;
  color: var(--text-3);
}
.day-items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.day-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding-top: var(--space-3);
  border-top: 1px dashed var(--border);
}
.day-item:first-child {
  border-top: none;
  padding-top: 0;
}
.day-ico {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
  font-size: 15px;
  flex-shrink: 0;
}
.day-item-body {
  flex: 1;
  min-width: 0;
}

.shared {
  margin-top: var(--space-5);
}
.shared h3 {
  margin: 0 0 var(--space-3);
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}

.sections {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
}
.section {
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: var(--space-4);
}
.section-head {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}
.section-ico {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--radius-sm);
  background: var(--brand-050);
  color: var(--brand-600);
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
}
.items {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.item {
  border-top: 1px dashed var(--border);
  padding-top: var(--space-3);
}
.item:first-child {
  border-top: none;
  padding-top: 0;
}
.item-main {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-2);
}
.item-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}
.item-price {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--brand-600);
}
.item-note {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-2);
}

@media (max-width: 820px) {
  .hero {
    grid-template-columns: 1fr;
  }
}
</style>
