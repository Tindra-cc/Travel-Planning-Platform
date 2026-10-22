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
      <el-button type="primary" @click="router.push('/chat')">去对话聊聊</el-button>
    </div>

    <template v-else>
      <section v-if="featured.length" class="featured">
        <div class="featured-head">
          <h2>🔥 热门精选</h2>
          <span>大家都在看</span>
        </div>
        <div class="featured-grid">
          <article class="feature-main" @click="goDetail(featureMain)">
            <div class="feature-cover">
              <img
                v-if="featureMain.coverUrl && !coverFailed(featureMain.postId)"
                :src="featureMain.coverUrl"
                :alt="featureMain.title"
                @error="onCoverError(featureMain.postId)"
              />
              <div v-else class="cover-fallback">
                <span>{{ featureMain.destination.charAt(0) }}</span>
              </div>
              <span class="feature-badge">热门 · {{ formatCount(featureMain.viewCount) }} 浏览</span>
            </div>
            <div class="feature-body">
              <h3>{{ featureMain.title }}</h3>
              <p class="feature-meta">
                <span>{{ featureMain.destination }}</span>
                <span v-if="featureMain.durationDays"> · {{ featureMain.durationDays }} 天</span>
                <span> · ♡ {{ formatCount(featureMain.likeCount) }}</span>
              </p>
              <p v-if="featureMain.summary" class="feature-summary">{{ featureMain.summary }}</p>
            </div>
          </article>

          <div class="feature-side">
            <article
              v-for="post in featured.slice(1)"
              :key="post.postId"
              class="feature-small"
              @click="goDetail(post)"
            >
              <div class="feature-small-cover">
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
              </div>
              <div class="feature-small-body">
                <h4>{{ post.title }}</h4>
                <span class="feature-meta">
                  {{ post.destination }} · ♡ {{ formatCount(post.likeCount) }}
                </span>
              </div>
            </article>
          </div>
        </div>
      </section>

      <div class="section-head">
        <h2>{{ featured.length ? '更多攻略' : '全部攻略' }}</h2>
        <span>{{ items.length }} 篇</span>
      </div>

    <div class="grid">
      <article
        v-for="post in gridItems"
        :key="post.postId"
        class="card"
        @click="goDetail(post)"
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { recommendGuides } from '@/api/guide'
import type { GuidePostItem } from '@/types/api'

const PAGE_SIZE = 12

const router = useRouter()
const items = ref<GuidePostItem[]>([])
const featured = ref<GuidePostItem[]>([])
// 精选区仅在 featured 非空时渲染，抽出首条避免模板下标可能为空
const featureMain = computed(() => featured.value[0] as GuidePostItem)
const total = ref(0)
const page = ref(0)
const loading = ref(false)
const loadingMore = ref(false)
const coverFailedIds = ref(new Set<string>())

const hasMore = computed(() => items.value.length < total.value)
const featuredIds = computed(() => new Set(featured.value.map((post) => post.postId)))
const gridItems = computed(() =>
  items.value.filter((post) => !featuredIds.value.has(post.postId))
)

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

async function load(targetPage: number, append: boolean) {
  if (append) loadingMore.value = true
  else loading.value = true
  try {
    const res = await recommendGuides(targetPage, PAGE_SIZE)
    items.value = append ? [...items.value, ...res.list] : res.list
    // 热门位只在首屏确定一次，翻页时保持位置稳定
    if (!append && !featured.value.length) {
      featured.value = [...res.list]
        .sort((a, b) => (b.viewCount ?? 0) - (a.viewCount ?? 0)
          || (b.likeCount ?? 0) - (a.likeCount ?? 0))
        .slice(0, 3)
    }
    // 后端 Long 统一序列化为字符串，分页字段必须转回数字
    total.value = Number(res.total)
    page.value = Number(res.page)
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

function goDetail(post: GuidePostItem) {
  router.push({ name: 'guide-detail', params: { postId: post.postId } })
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

.featured {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.featured-head,
.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-3);
}
.featured-head h2,
.section-head h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  color: var(--text-1);
}
.featured-head span,
.section-head span {
  font-size: 13px;
  color: var(--text-3);
}

.featured-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(0, 1fr);
  gap: var(--space-4);
}

.feature-main,
.feature-small {
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.feature-main:hover,
.feature-small:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-float);
}

.feature-cover {
  position: relative;
  aspect-ratio: 16 / 8.2;
  background: var(--brand-050);
  overflow: hidden;
}
.feature-cover img,
.feature-small-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.feature-badge {
  position: absolute;
  left: 14px;
  top: 14px;
  padding: 4px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--brand-600);
  font-size: 12px;
  font-weight: 600;
}
.feature-body {
  padding: 16px 18px 18px;
}
.feature-body h3 {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.4;
  color: var(--text-1);
}
.feature-meta {
  margin: 0;
  font-size: 13px;
  color: var(--text-3);
}
.feature-summary {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.feature-side {
  display: grid;
  grid-template-rows: repeat(2, minmax(0, 1fr));
  gap: var(--space-4);
  min-height: 0;
}
.feature-small {
  display: grid;
  grid-template-columns: 40% minmax(0, 1fr);
  min-height: 0;
}
.feature-small-cover {
  background: var(--brand-050);
  overflow: hidden;
  min-height: 132px;
}
.feature-small-body {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  padding: 14px 16px;
  min-width: 0;
}
.feature-small-body h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  color: var(--text-1);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.section-head {
  margin-top: var(--space-2);
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

@media (max-width: 860px) {
  .featured-grid {
    grid-template-columns: 1fr;
  }
  .feature-side {
    grid-template-rows: none;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 560px) {
  .feature-side {
    grid-template-columns: 1fr;
  }
  .feature-small-cover {
    min-height: 108px;
  }
  .feature-body h3 {
    font-size: 18px;
  }
}
</style>
