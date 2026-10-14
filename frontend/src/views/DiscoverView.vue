<template>
  <div class="discover">
    <header class="discover-head">
      <h1>发现好去处</h1>
      <p>为你推荐 · 精选攻略</p>
    </header>

    <div v-if="loading" class="grid">
      <div v-for="n in 6" :key="n" class="card">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="image" class="sk-cover" />
            <div class="sk-body">
              <el-skeleton-item variant="h3" style="width: 82%" />
              <el-skeleton-item variant="text" style="width: 46%" />
            </div>
          </template>
        </el-skeleton>
      </div>
    </div>

    <div v-else-if="!items.length" class="empty">
      <div class="empty-ico">🧭</div>
      <h3>暂时没有可推荐的攻略</h3>
      <p>稍后再来看看，或者先去「对话」里说说你想去哪。</p>
    </div>

    <div v-else class="grid">
      <article
        v-for="post in items"
        :key="post.postId"
        class="card"
        @click="openDetail(post)"
      >
        <div class="cover">
          <img
            v-if="post.coverUrl && !coverFailed(post.postId)"
            :src="post.coverUrl"
            :alt="post.title"
            loading="lazy"
            @error="onCoverError(post.postId)"
          />
          <div v-else class="cover-fallback">
            <span>{{ post.destination.charAt(0) }}</span>
          </div>
          <span v-if="post.durationDays" class="cover-badge">{{ post.durationDays }} 天</span>
        </div>

        <div class="body">
          <h3 class="title">{{ post.title }}</h3>
          <div class="meta">
            <div class="author">
              <span class="avatar" :style="{ background: avatarBg(post.postId) }">
                {{ initial(post.authorName) }}
              </span>
              <span class="author-name">{{ post.authorName || '旅行者' }}</span>
            </div>
            <span class="likes">♡ {{ formatCount(post.likeCount) }}</span>
          </div>
        </div>
      </article>
    </div>

    <div class="more">
      <el-button v-if="hasMore" :loading="loadingMore" @click="loadMore">加载更多</el-button>
      <span v-else-if="items.length" class="no-more">没有更多了</span>
    </div>

    <el-dialog v-model="dialogVisible" width="min(620px, 92vw)" align-center>
      <div v-if="active" class="detail">
        <div class="detail-cover">
          <img
            v-if="active.coverUrl && !coverFailed(active.postId)"
            :src="active.coverUrl"
            :alt="active.title"
            @error="onCoverError(active.postId)"
          />
          <div v-else class="cover-fallback">
            <span>{{ active.destination.charAt(0) }}</span>
          </div>
        </div>

        <h2 class="detail-title">{{ active.title }}</h2>

        <div class="detail-meta">
          <span class="avatar" :style="{ background: avatarBg(active.postId) }">
            {{ initial(active.authorName) }}
          </span>
          <span>{{ active.authorName || '旅行者' }}</span>
          <span class="dot">·</span>
          <span>目的地 {{ active.destination }}</span>
          <span class="dot">·</span>
          <span>{{ formatCount(active.likeCount) }} 收藏</span>
        </div>

        <p class="detail-summary">{{ active.summary }}</p>

        <div class="detail-tags">
          <el-tag v-if="active.durationDays" effect="plain" round>{{ active.durationDays }} 天</el-tag>
          <el-tag v-if="active.budgetLevel" effect="plain" round>{{ budgetLabel(active.budgetLevel) }}</el-tag>
          <el-tag v-for="t in active.tags || []" :key="t" effect="plain" round>{{ t }}</el-tag>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="goChat">按这个生成我的行程</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { recommendGuides } from '@/api/guide'
import type { GuidePostItem } from '@/types/api'

const PAGE_SIZE = 12
const BUDGET_LABELS: Record<string, string> = {
  budget: '穷游',
  economy: '经济',
  balanced: '均衡',
  comfort: '舒适',
  luxury: '奢华'
}

const router = useRouter()
const items = ref<GuidePostItem[]>([])
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const coverFailedIds = ref(new Set<string>())
const active = ref<GuidePostItem | null>(null)
const dialogVisible = ref(false)

const hasMore = computed(() => items.value.length < total.value)

function coverFailed(postId: string) {
  return coverFailedIds.value.has(postId)
}

function onCoverError(postId: string) {
  // 外链图片挂掉时切成 CSS 渐变封面，避免出现破图
  coverFailedIds.value.add(postId)
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

async function load(targetPage: number, append: boolean) {
  if (append) loadingMore.value = true
  else loading.value = true
  try {
    const res = await recommendGuides(targetPage, PAGE_SIZE)
    items.value = append ? [...items.value, ...res.list] : res.list
    total.value = res.total
    page.value = res.page
  } catch {
    // 失败提示由 axios 拦截器统一处理，这里保留已加载内容
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function loadMore() {
  load(page.value + 1, true)
}

function openDetail(post: GuidePostItem) {
  active.value = post
  dialogVisible.value = true
}

function goChat() {
  dialogVisible.value = false
  router.push('/chat')
}

onMounted(() => load(1, false))
</script>

<style scoped>
.discover {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.discover-head h1 {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.2px;
  color: var(--text-1);
}
.discover-head p {
  margin: 0;
  font-size: 14px;
  color: var(--text-2);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: var(--space-4);
}

.card {
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-float);
}

.cover {
  position: relative;
  aspect-ratio: 16 / 10;
  background: var(--brand-050);
  overflow: hidden;
}
.cover img {
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
  font-size: 44px;
  font-weight: 700;
  opacity: 0.9;
}
.cover-badge {
  position: absolute;
  left: 10px;
  top: 10px;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 600;
}

.body {
  padding: 14px 16px 16px;
}
.title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--text-1);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 44px;
}
.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}
.author {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
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
.author-name {
  font-size: 13px;
  color: var(--text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.likes {
  font-size: 13px;
  color: var(--text-3);
  flex-shrink: 0;
}

.sk-cover {
  width: 100%;
  aspect-ratio: 16 / 10;
  height: auto;
}
.sk-body {
  padding: 14px 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  padding: 72px var(--space-6);
  text-align: center;
}
.empty-ico {
  font-size: 48px;
  line-height: 1;
}
.empty h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
}
.empty p {
  margin: 0;
  font-size: 14px;
  color: var(--text-2);
}

.more {
  display: flex;
  justify-content: center;
  padding: var(--space-2) 0 var(--space-4);
}
.no-more {
  font-size: 13px;
  color: var(--text-3);
}

.detail-cover {
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--brand-050);
}
.detail-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.detail-title {
  margin: var(--space-4) 0 var(--space-2);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--text-1);
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-2);
}
.detail-meta .dot {
  color: var(--text-3);
}
.detail-summary {
  margin: var(--space-4) 0;
  font-size: 14px;
  line-height: 1.75;
  color: var(--text-2);
}
.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}
</style>
