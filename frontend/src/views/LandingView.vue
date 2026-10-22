<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const root = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

onMounted(() => {
  if (!root.value) return
  const els = Array.from(root.value.querySelectorAll<HTMLElement>('[data-in]'))
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (reduced || !('IntersectionObserver' in window)) {
    els.forEach((el) => el.classList.add('in'))
    return
  }
  observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('in')
          observer?.unobserve(entry.target)
        }
      })
    },
    { rootMargin: '0px 0px -8% 0px' }
  )
  els.forEach((el) => observer?.observe(el))
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div ref="root" class="lp-page">
    <header class="nav-header">
      <nav class="nav-content" aria-label="主导航">
        <RouterLink class="logo" to="/welcome" aria-label="AI 旅行规划助理首页">
          <span class="lp-brand" aria-hidden="true">
            <span class="lp-brand-mark">✈</span>
            <span class="lp-brand-wordmark">AI 旅行规划</span>
          </span>
        </RouterLink>
        <div class="nav-left">
          <RouterLink class="nav-link" to="/discover">推荐</RouterLink>
          <RouterLink class="nav-link" to="/guidebook">攻略</RouterLink>
          <RouterLink class="nav-link" to="/expert">达人</RouterLink>
        </div>
        <div class="nav-right">
          <a class="nav-link nav-link-active" href="#plans" aria-current="page">定价</a>
          <RouterLink class="a2-btn nav-cta-button" :to="auth.isLoggedIn ? '/discover' : '/chat'">
            {{ auth.isLoggedIn ? '进入工作台' : '开始规划' }}
          </RouterLink>
        </div>
      </nav>
    </header>

    <section class="pl-hero">
      <div class="pl-hero-inner">
        <div class="pl-hero-copy">
          <div class="a2-proof" role="group" aria-label="4.9 分评价，12,000+ 旅行者">
            <div class="a2-proof-item">
              <span class="a2-proof-top">
                <b>4.9</b>
                <span class="a2-proof-stars" aria-hidden="true">
                  <svg v-for="n in 5" :key="n" viewBox="0 0 20 20">
                    <path d="M10 1.5l2.6 5.4 5.9.8-4.3 4.1 1 5.9L10 14.9l-5.2 2.8 1-5.9L1.5 7.7l5.9-.8z" />
                  </svg>
                </span>
              </span>
              <small>用户评分</small>
            </div>
            <div class="a2-proof-item">
              <span class="a2-proof-top">
                <span class="a2-proof-faces" aria-hidden="true">
                  <img src="https://picsum.photos/seed/traveler-a/48/48" alt="" width="26" height="26" loading="lazy" />
                  <img src="https://picsum.photos/seed/traveler-b/48/48" alt="" width="26" height="26" loading="lazy" />
                  <img src="https://picsum.photos/seed/traveler-c/48/48" alt="" width="26" height="26" loading="lazy" />
                </span>
                <b>12,000+</b>
              </span>
              <small>位旅行者</small>
            </div>
          </div>

          <h1 class="hm-display pl-hero-title" data-in>免费体验 7 天 AI 旅行规划</h1>
          <p class="pl-hero-lead" data-in>从一句目的地，到逐小时行程、预算清单和雨天备选，全部交给你的旅行助理。</p>
          <RouterLink class="a2-btn a2-btn--lg pl-hero-cta" :to="auth.isLoggedIn ? '/chat' : '/login?redirect=/chat'" data-in>
            开始我的免费周
          </RouterLink>
          <p class="pl-hero-note">免费 7 天，之后 ¥99/年。随时取消，取消前分文不收。</p>
        </div>

        <figure class="pl-hero-photo">
          <img
            src="https://picsum.photos/seed/travel-planning-hero/1200/800"
            alt="旅行者在目的地街头查看手机里的行程"
            width="1536"
            height="1024"
            loading="eager"
            fetchpriority="high"
          />
          <div class="pl-hero-chat" aria-hidden="true">
            <div class="a2-bubble pl-hero-you">5 月去成都 4 天，带爸妈，预算 4000</div>
            <div class="a2-bubble pl-hero-assistant">
              <div class="a2-bubble-from">
                <span class="lp-brand-mark lp-brand-mark--mini">✈</span>
                旅行助理
              </div>
              已记录。熊猫基地放在 Day 2 上午，避开周末人流，雨天备选也加好了。
            </div>
          </div>
          <span class="orb" aria-hidden="true"></span>
        </figure>
      </div>
    </section>

    <section id="plans" class="hm-section pl-plans" aria-labelledby="pl-plans-title">
      <div class="hm-wrap">
        <h2 id="pl-plans-title" class="hm-h2 pl-center" data-in>选择你的旅行方案</h2>
        <div class="pl-plans-grid">
          <article class="pl-card" data-in>
            <h3 class="pl-card-name">随行版</h3>
            <p class="pl-card-price">¥19.99<span> / 月</span></p>
            <p class="pl-card-secondary">按月订阅，随时取消。适合偶尔出发的你。</p>
            <RouterLink class="a2-btn pl-card-btn" :to="auth.isLoggedIn ? '/chat' : '/login?redirect=/chat'">按月开始</RouterLink>
          </article>
          <article class="pl-card pl-card--featured" data-in>
            <p class="pl-card-eyebrow">最受欢迎</p>
            <h3 class="pl-card-name">旅行家</h3>
            <p class="pl-card-price">¥99<span> / 年</span></p>
            <p class="pl-card-secondary">免费 7 天，之后约 ¥8.25/月，按年计费。一年内的每一次出发都算数。</p>
            <RouterLink
              class="a2-btn a2-btn--lg pl-card-btn pl-card-btn--kiwi"
              :to="auth.isLoggedIn ? '/chat' : '/login?redirect=/chat'"
            >
              开始我的免费周
            </RouterLink>
          </article>
        </div>
        <p class="pl-exit" data-in>还没决定？ <RouterLink to="/discover">先看看大家怎么玩</RouterLink></p>
      </div>
    </section>

    <section class="hm-section pl-week" aria-labelledby="pl-week-title">
      <div class="hm-wrap">
        <div class="pl-week-panel" data-in>
          <div class="pl-week-copy">
            <h2 id="pl-week-title" class="hm-h2">免费周怎么开始</h2>
            <p class="hm-lead">扣费之前，助理会先提醒你。</p>
            <RouterLink class="a2-btn a2-btn--lg" :to="auth.isLoggedIn ? '/chat' : '/login?redirect=/chat'">开始我的免费周</RouterLink>
          </div>
          <ul class="pl-week-steps">
            <li><b>今天</b><span>完整功能：生成、调整、导出和分享，一分钱不付。</span></li>
            <li><b>第 6 天</b><span>助理提醒你免费周即将结束。</span></li>
            <li><b>第 7 天</b><span>按 ¥99/年 生效。提前取消则不产生费用。</span></li>
          </ul>
        </div>
      </div>
    </section>

    <section class="hm-section pl-shoot" aria-labelledby="pl-shoot-title">
      <div class="hm-wrap">
        <h2 id="pl-shoot-title" class="hm-h2" data-in>顺着你本来的旅行方式</h2>
        <p class="hm-lead" data-in>说出来、贴行程、随时改。剩下的交给助理。</p>
        <div class="pl-shoot-grid">
          <img
            class="pl-shoot-wide"
            src="https://picsum.photos/seed/chengdu-family/1200/800"
            alt="一家人在成都的巷子里散步"
            width="1536"
            height="1024"
            loading="lazy"
            data-in
          />
          <img
            src="https://picsum.photos/seed/shanghai-walk/800/1200"
            alt="上海梧桐区的街角咖啡店"
            width="1024"
            height="1536"
            loading="lazy"
            data-in
            style="--d: 90ms"
          />
          <img
            src="https://picsum.photos/seed/xian-morning/800/1200"
            alt="西安清晨的城墙与街市"
            width="1024"
            height="1536"
            loading="lazy"
            data-in
            style="--d: 180ms"
          />
        </div>
      </div>
    </section>

    <section id="stories" class="hm-members" aria-label="旅行者评价">
      <div class="hm-wrap">
        <h2 class="hm-h2 hm-members-title" data-in>旅行者怎么说</h2>
        <div class="hm-feature" data-in>
          <div class="hm-feature-photo">
            <img src="https://picsum.photos/seed/traveler-story/1000/1000" alt="旅行者在目的地的合影" loading="lazy" />
          </div>
          <div class="hm-feature-copy">
            <div>
              <blockquote>“爸妈第一次说行程不赶。”</blockquote>
              <p class="hm-feature-sub">“四天三晚按体力排，午休和雨天备选都想到了。”</p>
            </div>
            <div>
              <div class="hm-feature-name">林女士 · 成都亲子行</div>
              <p class="hm-feature-note">个人体验，行程因同行人和天气而异。</p>
            </div>
          </div>
        </div>
      </div>

      <div class="hm-quotes" aria-label="更多旅行者评价">
        <figure class="hm-quote" style="background: var(--lp-balearic)">
          <blockquote>“早上还在纠结去哪，中午攻略已经连预算一起排好了。”</blockquote>
          <figcaption><span>杭州 · 周末两日<small>用户评价</small></span></figcaption>
        </figure>
        <figure class="hm-quote" style="background: var(--lp-mango)">
          <blockquote>“临下雨改室内备选，只改了两三个节点，时间还是对齐的。”</blockquote>
          <figcaption><span>上海 · 梧桐区漫步<small>用户评价</small></span></figcaption>
        </figure>
        <figure class="hm-quote" style="background: var(--lp-spring)">
          <blockquote>“带娃出门最怕排太满，它会主动把午休和交通留出来。”</blockquote>
          <figcaption><span>成都 · 亲子四日<small>用户评价</small></span></figcaption>
        </figure>
        <figure class="hm-quote" style="background: var(--lp-peach)">
          <blockquote>“导出的行程发给朋友，扫码就能看，不用再截图解释。”</blockquote>
          <figcaption><span>西安 · 毕业旅行<small>用户评价</small></span></figcaption>
        </figure>
        <figure class="hm-quote" style="background: var(--lp-pink)">
          <blockquote>“预算超了会提醒，不会等到了目的地才发现钱不够。”</blockquote>
          <figcaption><span>厦门 · 三日游<small>用户评价</small></span></figcaption>
        </figure>
        <figure class="hm-quote" style="background: var(--lp-kiwi)">
          <blockquote>“达人可以直接按我的偏好出攻略，改起来也快。”</blockquote>
          <figcaption><span>定制客户<small>达人服务评价</small></span></figcaption>
        </figure>
      </div>
    </section>

    <section class="hm-section pl-faq" aria-labelledby="pl-faq-title">
      <div class="hm-narrow">
        <h2 id="pl-faq-title" class="hm-h2" data-in>常见问题</h2>
        <div class="pl-faq-list">
          <details class="alma-acc-item">
            <summary>免费周结束后会怎样？</summary>
            <p>按 ¥99/年 生效。我们会在第 6 天提醒你，第 7 天前取消则不产生任何费用。</p>
          </details>
          <details class="alma-acc-item">
            <summary>需要下载 App 吗？</summary>
            <p>不需要。浏览器打开就能用，手机上也可以随时查看和调整行程。</p>
          </details>
          <details class="alma-acc-item">
            <summary>可以导出或分享行程吗？</summary>
            <p>可以。支持只读分享链接、二维码和打印友好的导出，同伴打开就能看。</p>
          </details>
          <details class="alma-acc-item">
            <summary>行程会考虑天气、预算和时间冲突吗？</summary>
            <p>会。生成和编辑时都会校验时间不重叠、预算不超上限，并提供雨天备选动线。</p>
          </details>
        </div>
      </div>
    </section>

    <footer class="hm-footer">
      <span class="orb" aria-hidden="true"></span>
      <div class="hm-footer-main">
        <nav class="site-footer-nav" aria-label="页脚">
          <div class="site-footer-nav-group">
            <h2>探索</h2>
            <ul>
              <li><RouterLink to="/discover">推荐攻略</RouterLink></li>
              <li><RouterLink to="/guidebook">我的攻略</RouterLink></li>
              <li><RouterLink to="/expert">达人工作台</RouterLink></li>
            </ul>
          </div>
          <div class="site-footer-nav-group">
            <h2>产品</h2>
            <ul>
              <li><RouterLink to="/welcome">方案与定价</RouterLink></li>
              <li><RouterLink to="/chat">免费规划</RouterLink></li>
              <li><RouterLink to="/guidebook">行程对比</RouterLink></li>
              <li><RouterLink to="/me">个人中心</RouterLink></li>
            </ul>
          </div>
          <div class="site-footer-nav-group">
            <h2>支持</h2>
            <ul>
              <li><RouterLink to="/login">登录</RouterLink></li>
              <li><a href="#pl-faq-title">帮助</a></li>
              <li><a href="mailto:support@example.com">联系我们</a></li>
            </ul>
          </div>
          <div class="site-footer-nav-group">
            <h2>条款</h2>
            <ul>
              <li><a href="#pl-faq-title">隐私说明</a></li>
              <li><a href="#pl-faq-title">服务条款</a></li>
              <li><a href="#pl-faq-title">内容规范</a></li>
            </ul>
          </div>
        </nav>

        <div class="hm-newsletter">
          <div>
            <h2>订阅旅行灵感</h2>
            <p>每周一条路线，一个避坑提醒。</p>
          </div>
          <form @submit.prevent>
            <input type="email" name="email" placeholder="you@example.com" aria-label="邮箱地址" required />
            <button class="a2-btn a2-btn--light" type="submit">订阅</button>
          </form>
        </div>
      </div>
      <p class="hm-copyright">© 2026 AI 旅行规划助理</p>
    </footer>
  </div>
</template>

<style scoped>
@font-face {
  font-family: 'HAL Four Grotesk';
  src: url('../assets/fonts/hal-four-grotesk-400.woff2') format('woff2');
  font-weight: 400;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'HAL Four Grotesk';
  src: url('../assets/fonts/hal-four-grotesk-500.woff2') format('woff2');
  font-weight: 500;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'HAL Four Grotesk';
  src: url('../assets/fonts/hal-four-grotesk-700.woff2') format('woff2');
  font-weight: 700;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'GT Alpina Condensed';
  src: url('../assets/fonts/gt-alpina-condensed-300.woff2') format('woff2');
  font-weight: 300;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'GT Alpina Condensed';
  src: url('../assets/fonts/gt-alpina-condensed-500.woff2') format('woff2');
  font-weight: 500;
  font-style: normal;
  font-display: swap;
}
@font-face {
  font-family: 'GT Alpina Condensed';
  src: url('../assets/fonts/gt-alpina-condensed-700.woff2') format('woff2');
  font-weight: 700;
  font-style: normal;
  font-display: swap;
}

.lp-page {
  --lp-teriyaki: #33140c;
  --lp-collard: #3c5627;
  --lp-kiwi: #aadf49;
  --lp-yogurt: #fffdf5;
  --lp-surface: #fffefc;
  --lp-aioli: #f7f5ed;
  --lp-mango: #ffc131;
  --lp-balearic: #93e2e2;
  --lp-spring: #aeef4b;
  --lp-kale: #0c331f;
  --lp-peach: #fdbf95;
  --lp-plum: #963b65;
  --lp-pink: #e7c3d5;
  --lp-muted-ink: #6f6a64;
  --lp-ease: cubic-bezier(0.34, 1.05, 0.64, 1);
  --lp-card-radius: 28px;
  --lp-section-radius: 40px;
  --lp-section-radius-mobile: 32px;
  --lp-shadow-soft: 0 18px 28px -20px #33140c80, inset 0 0 0 1px #33140c0f;
  --lp-shadow-lift: 0 28px 36px -22px #33140c8c, inset 0 0 0 1px #33140c0f;
  --lp-button-gradient: linear-gradient(to bottom, #45632d, #2b3d1c);
  --lp-button-gradient-hover: linear-gradient(to bottom, #4b6b31, #30451f);
  --lp-button-shadow: 0 15px 14px -10px #00000026, inset 0 0 0 1px #0003;
  --lp-button-shadow-hover: 0 10px 18px -10px #0000003d, inset 0 0 0 1px #0003;
  --lp-font-display: 'GT Alpina Condensed', 'Times New Roman', serif;
  --lp-font-body: 'HAL Four Grotesk', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  --lp-h1-size: clamp(46px, 6.6vw, 88px);
  --lp-h2-size: clamp(36px, 5vw, 56px);
  --lp-gap: 96px;

  position: relative;
  overflow-x: clip;
  min-height: 100svh;
  background: var(--lp-yogurt);
  color: var(--lp-teriyaki);
  font-family: var(--lp-font-body);
  -webkit-font-smoothing: antialiased;
}
.lp-page *,
.lp-page *::before,
.lp-page *::after {
  box-sizing: border-box;
}
.lp-page img {
  max-width: 100%;
}
.lp-page a {
  color: inherit;
  text-decoration: none;
}

.hm-wrap {
  width: min(1080px, 100% - 32px);
  margin-inline: auto;
}
.hm-narrow {
  width: min(720px, 100% - 32px);
  margin-inline: auto;
}
.hm-section {
  padding-block: calc(var(--lp-gap) / 2);
}
.hm-h2 {
  font-family: var(--lp-font-display);
  font-weight: 300;
  font-size: var(--lp-h2-size);
  text-wrap: balance;
  margin: 0;
  line-height: 1.05;
}
.hm-display {
  font-family: var(--lp-font-display);
  font-weight: 500;
  font-size: var(--lp-h1-size);
  text-wrap: balance;
  margin: 0;
  line-height: 0.95;
}
.hm-lead {
  color: var(--lp-muted-ink);
  margin: 16px 0 0;
  font-size: 18px;
  line-height: 27px;
}
.pl-center {
  text-align: center;
}

.lp-page [data-in] {
  opacity: 0;
  transform: translateY(28px);
  transition:
    opacity 0.7s var(--lp-ease),
    transform 0.7s var(--lp-ease);
  transition-delay: var(--d, 0s);
}
.lp-page [data-in].in {
  opacity: 1;
  transform: none;
}
@media (prefers-reduced-motion: reduce) {
  .lp-page [data-in] {
    opacity: 1 !important;
    transform: none !important;
    transition: none !important;
  }
}

.a2-btn {
  background: var(--lp-button-gradient);
  min-height: 48px;
  color: var(--lp-surface);
  font-family: var(--lp-font-body);
  text-align: center;
  white-space: nowrap;
  box-shadow: var(--lp-button-shadow);
  cursor: pointer;
  border: 0;
  border-radius: 20px;
  justify-content: center;
  align-items: center;
  gap: 8px;
  padding: 0 24px;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.2;
  text-decoration: none;
  transition:
    background 0.18s,
    box-shadow 0.18s,
    transform 0.18s,
    opacity 0.18s;
  display: inline-flex;
}
@media (hover: hover) and (pointer: fine) {
  .a2-btn:hover {
    background: var(--lp-button-gradient-hover);
    box-shadow: var(--lp-button-shadow-hover);
    transform: translateY(-1px);
  }
}
.a2-btn:active {
  transform: scale(0.97);
}
.a2-btn--light {
  background: var(--lp-surface);
  color: var(--lp-teriyaki);
  box-shadow: 0 15px 14px -10px #0000002e, inset 0 0 0 1px #33140c1a;
}
.a2-btn--lg {
  min-height: 56px;
  padding: 0 32px;
  font-size: 18px;
}

.nav-header {
  position: fixed;
  left: 0;
  right: 0;
  top: max(14px, env(safe-area-inset-top));
  z-index: 50;
  background: 0 0;
  border: 0;
  padding: 0;
  pointer-events: none;
}
.nav-content {
  width: fit-content;
  max-width: calc(100% - 32px);
  color: var(--lp-teriyaki);
  font-family: var(--lp-font-body);
  pointer-events: auto;
  background: rgba(255, 254, 252, 0.9);
  border-radius: 999px;
  justify-content: flex-start;
  align-items: center;
  gap: 6px;
  margin: 0 auto;
  padding: 8px 8px 8px 18px;
  font-size: 16px;
  font-weight: 500;
  display: flex;
  box-shadow: rgba(51, 20, 12, 0.35) 0 10px 30px -14px, rgba(51, 20, 12, 0.06) 0 0 0 1px inset;
}
.logo {
  display: flex;
  flex-direction: row;
  order: 0;
  padding: 0 10px 0 0;
  align-items: center;
  color: var(--lp-teriyaki);
}
.nav-left,
.nav-right {
  align-items: center;
  gap: 2px;
  margin: 0;
  display: flex;
}
.nav-left {
  order: 1;
}
.nav-right {
  order: 2;
}
.nav-link {
  white-space: nowrap;
  border-radius: 999px;
  padding: 10px 14px;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.2;
  transition: background 0.18s;
  color: var(--lp-teriyaki);
  text-decoration: none;
}
@media (hover: hover) and (pointer: fine) {
  .nav-link:hover {
    background: var(--lp-aioli);
  }
}
.nav-link-active {
  color: var(--lp-collard);
}
.nav-cta-button.a2-btn {
  background: var(--lp-button-gradient);
  min-height: 44px;
  box-shadow: var(--lp-button-shadow);
  border-radius: 999px;
  margin-left: 10px;
  padding: 0 20px;
  color: var(--lp-surface);
}
@media (hover: hover) and (pointer: fine) {
  .nav-cta-button.a2-btn:hover {
    background: var(--lp-button-gradient-hover);
    box-shadow: var(--lp-button-shadow-hover);
  }
}

.lp-brand {
  align-items: center;
  gap: 8px;
  color: inherit;
  line-height: 0;
  display: inline-flex;
}
.lp-brand-mark {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: 10px;
  background: var(--lp-kiwi);
  color: var(--lp-kale);
  font-size: 16px;
  line-height: 1;
}
.lp-brand-wordmark {
  font-family: var(--lp-font-display);
  font-weight: 500;
  font-size: 27px;
  line-height: 1;
  letter-spacing: -0.01em;
  color: var(--lp-teriyaki);
  white-space: nowrap;
}

.pl-hero {
  background: var(--lp-mango);
  padding: 128px 0 calc(var(--lp-gap) / 2 + 24px);
  position: relative;
  overflow: hidden;
}
.pl-hero-inner {
  grid-template-columns: minmax(0, 1.08fr) minmax(0, 1fr);
  align-items: center;
  gap: clamp(32px, 5vw, 72px);
  width: min(1180px, 100% - 32px);
  margin-inline: auto;
  display: grid;
}
.pl-hero-copy {
  color: var(--lp-kale);
  order: 1;
}
.pl-hero-title {
  color: var(--lp-kale);
  margin-top: 24px;
}
.pl-hero-lead {
  max-width: 30ch;
  color: var(--lp-kale);
  margin: 20px 0 0;
  font-size: 18px;
  line-height: 27px;
}
.pl-hero-cta {
  margin-top: 32px;
}
.pl-hero-note {
  color: rgba(12, 51, 31, 0.78);
  margin: 14px 0 0;
  font-size: 14px;
  line-height: 20px;
}
.pl-hero-photo {
  order: 2;
  margin: 0;
  position: relative;
}
.pl-hero-photo img {
  aspect-ratio: 3 / 2;
  object-fit: cover;
  width: 100%;
  height: auto;
  box-shadow: var(--lp-shadow-lift);
  border-radius: 36px;
  display: block;
}
.pl-hero-chat {
  pointer-events: none;
  position: absolute;
  inset: 0;
}
.a2-bubble {
  border-radius: 22px;
  padding: 14px 18px;
  font-size: 16px;
  line-height: 1.45;
}
.pl-hero-chat .a2-bubble {
  text-wrap: pretty;
  width: max-content;
  max-width: 300px;
  font-size: 16px;
  position: absolute;
  animation: bubble-in 0.5s var(--lp-ease) both;
}
.pl-hero-you {
  background: var(--lp-collard);
  color: var(--lp-surface);
  border-radius: 22px 22px 22px 8px;
  top: -20px;
  left: -28px;
}
.pl-hero-assistant {
  background: var(--lp-surface);
  color: var(--lp-teriyaki);
  border-radius: 22px 22px 8px;
  max-width: 260px;
  bottom: -28px;
  right: -24px;
  box-shadow: rgba(51, 20, 12, 0.3) 0 12px 28px -18px, rgba(51, 20, 12, 0.06) 0 0 0 1px inset;
  animation-delay: 0.7s;
}
@keyframes bubble-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}
.a2-bubble-from {
  letter-spacing: 0.04em;
  color: var(--lp-muted-ink);
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 500;
  display: flex;
}
.lp-brand-mark--mini {
  width: 20px;
  height: 20px;
  border-radius: 7px;
  font-size: 11px;
}

.orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  background: radial-gradient(
    circle at 32% 28%,
    #fff 0 6%,
    #ffe6a8 14%,
    #ffb0d0 32%,
    #b7f0e6 52%,
    #a9c8ff 68%,
    #e6c6ff 82%,
    #f6b7ff 92%,
    #ffffff 100%
  );
  filter: blur(0.3px) saturate(1.1);
  box-shadow: inset 0 0 18px rgba(255, 255, 255, 0.55), 0 10px 24px -8px rgba(180, 120, 60, 0.35);
  animation: orb-float 7s ease-in-out infinite;
}
@keyframes orb-float {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}
.pl-hero .orb {
  width: 104px;
  height: 104px;
  right: -14px;
  bottom: 44px;
}

.a2-proof {
  --lp-proof-ring: var(--lp-yogurt);
  color: var(--lp-kale);
  flex-wrap: wrap;
  align-items: center;
  gap: 20px 40px;
  display: flex;
}
.a2-proof-item {
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  display: flex;
}
.a2-proof-top {
  align-items: center;
  gap: 8px;
  line-height: 1;
  display: flex;
}
.a2-proof-top b {
  font-family: var(--lp-font-display);
  font-size: 32px;
  font-weight: 500;
}
.a2-proof small {
  opacity: 0.8;
  font-size: 14px;
  line-height: 1.3;
}
.a2-proof-stars {
  gap: 2px;
  display: inline-flex;
}
.a2-proof-stars svg {
  fill: currentcolor;
  width: 15px;
  height: 15px;
}
.a2-proof-faces {
  display: inline-flex;
}
.a2-proof-faces img {
  object-fit: cover;
  border: 2px solid var(--lp-proof-ring);
  border-radius: 50%;
  width: 28px;
  height: 28px;
  margin-left: -8px;
}
.a2-proof-faces img:first-child {
  margin-left: 0;
}

.pl-plans {
  padding-top: var(--lp-gap);
}
.pl-plans-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: stretch;
  gap: 20px;
  max-width: 760px;
  margin: 40px auto 0;
  display: grid;
}
.pl-card {
  border-radius: var(--lp-card-radius);
  background: var(--lp-surface);
  box-shadow: var(--lp-shadow-soft);
  flex-direction: column;
  padding: 32px 28px 28px;
  display: flex;
}
.pl-card--featured {
  background: var(--lp-kale);
  color: var(--lp-yogurt);
  box-shadow: var(--lp-shadow-lift);
}
.pl-card-eyebrow {
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--lp-kiwi);
  margin: 0 0 12px;
  font-size: 12px;
  font-weight: 500;
  line-height: 16px;
}
.pl-card-name {
  font-family: var(--lp-font-display);
  margin: 0;
  font-size: 36px;
  font-weight: 400;
  line-height: 1.1;
}
.pl-card-price {
  font-family: var(--lp-font-display);
  margin: 20px 0 0;
  font-size: 52px;
  font-weight: 400;
  line-height: 1;
}
.pl-card-price span {
  font-family: var(--lp-font-body);
  color: var(--lp-muted-ink);
  font-size: 16px;
}
.pl-card--featured .pl-card-price span {
  color: rgba(255, 253, 245, 0.72);
}
.pl-card-secondary {
  color: var(--lp-muted-ink);
  flex: 1 1 0%;
  margin: 12px 0 28px;
  font-size: 16px;
  line-height: 24px;
}
.pl-card--featured .pl-card-secondary {
  color: rgba(255, 253, 245, 0.84);
}
.pl-card-btn {
  width: 100%;
}
.pl-card-btn--kiwi {
  background: var(--lp-kiwi);
  color: var(--lp-kale);
  box-shadow: rgba(0, 0, 0, 0.35) 0 15px 14px -10px, rgba(255, 255, 255, 0.35) 0 1px inset;
}
.pl-exit {
  text-align: center;
  color: var(--lp-muted-ink);
  margin: 40px 0 0;
  font-size: 16px;
}
.pl-exit a {
  color: var(--lp-collard);
  text-underline-offset: 3px;
  font-weight: 500;
}

.pl-week-panel {
  border-radius: var(--lp-section-radius);
  background: var(--lp-balearic);
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  align-items: center;
  gap: 48px;
  padding: clamp(32px, 5vw, 64px);
  display: grid;
  position: relative;
  overflow: hidden;
}
.pl-week-copy .a2-btn {
  margin-top: 28px;
}
.pl-week-copy .hm-lead {
  color: rgba(51, 20, 12, 0.72);
}
.pl-week-steps {
  gap: 28px;
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  position: relative;
}
.pl-week-steps::before {
  content: '';
  background: rgba(12, 51, 31, 0.18);
  width: 2px;
  position: absolute;
  top: 12px;
  bottom: 12px;
  left: 11px;
}
.pl-week-steps li {
  padding-left: 44px;
  position: relative;
}
.pl-week-steps li::before {
  content: '';
  background: var(--lp-balearic);
  width: 16px;
  height: 16px;
  box-shadow: inset 0 0 0 3px var(--lp-collard);
  border-radius: 50%;
  position: absolute;
  top: 6px;
  left: 2px;
}
.pl-week-steps li:first-child::before {
  background: var(--lp-kiwi);
  box-shadow: inset 0 0 0 3px var(--lp-collard), 0 0 0 6px #aadf4973;
}
.pl-week-steps b {
  font-family: var(--lp-font-display);
  font-size: 32px;
  font-weight: 400;
  line-height: 1.1;
  display: block;
}
.pl-week-steps span {
  color: rgba(51, 20, 12, 0.8);
  margin-top: 6px;
  font-size: 16px;
  line-height: 24px;
  display: block;
}

.pl-shoot-grid {
  grid-template-columns: 2fr 1fr 1fr;
  gap: 16px;
  margin-top: 40px;
  display: grid;
}
.pl-shoot-grid img {
  object-fit: cover;
  border-radius: var(--lp-card-radius);
  width: 100%;
  height: clamp(300px, 34vw, 440px);
  box-shadow: var(--lp-shadow-soft);
  display: block;
}

.hm-members {
  padding-block: calc(var(--lp-gap) / 2);
  overflow-x: clip;
}
.hm-members-title {
  margin-bottom: 28px;
}
.hm-feature {
  border-radius: var(--lp-section-radius);
  background: var(--lp-kiwi);
  grid-template-columns: 1fr 1fr;
  display: grid;
  overflow: hidden;
  position: relative;
}
.hm-feature-photo {
  min-height: 540px;
  position: relative;
  overflow: hidden;
}
.hm-feature-photo img {
  object-fit: cover;
  object-position: 50% 20%;
  width: 100%;
  height: 100%;
  position: absolute;
  inset: 0;
}
.hm-feature-copy {
  color: var(--lp-kale);
  flex-direction: column;
  justify-content: space-between;
  gap: 32px;
  padding: clamp(28px, 5vw, 64px);
  display: flex;
}
.hm-feature-copy blockquote {
  font-family: var(--lp-font-display);
  margin: 0;
  font-size: clamp(44px, 5.6vw, 80px);
  font-weight: 500;
  line-height: 0.96;
}
.hm-feature-sub {
  font-family: var(--lp-font-display);
  margin: 20px 0 0;
  font-size: clamp(22px, 2.2vw, 28px);
  font-weight: 300;
  line-height: 1.2;
}
.hm-feature-name {
  font-size: 18px;
  font-weight: 500;
}
.hm-feature-note {
  color: rgba(12, 51, 31, 0.75);
  margin: 6px 0 0;
  font-size: 14px;
  line-height: 20px;
}
.hm-quotes {
  gap: 14px;
  padding: 16px max(16px, 50% - 540px) 24px;
  display: flex;
  overflow-x: auto;
  scrollbar-width: none;
  overscroll-behavior-x: contain;
}
.hm-quotes::-webkit-scrollbar {
  display: none;
}
.hm-quote {
  border-radius: var(--lp-card-radius);
  min-height: 280px;
  color: var(--lp-kale);
  box-shadow: var(--lp-shadow-soft);
  flex-direction: column;
  flex: 0 0 clamp(280px, 30vw, 350px);
  justify-content: space-between;
  gap: 28px;
  margin: 0;
  padding: 26px;
  display: flex;
}
.hm-quote blockquote {
  font-family: var(--lp-font-display);
  margin: 0;
  font-size: 26px;
  font-weight: 300;
  line-height: 1.15;
}
.hm-quote figcaption {
  align-items: center;
  gap: 12px;
  font-size: 15px;
  font-weight: 500;
  display: flex;
}
.hm-quote small {
  opacity: 0.75;
  font-size: 14px;
  font-weight: 400;
  display: block;
}

.pl-faq-list {
  margin-top: 32px;
}
.alma-acc-item {
  background: #f3f2ee;
  color: var(--lp-teriyaki);
  font-family: var(--lp-font-body);
  border-radius: 24px;
  padding: 0 24px;
  transition: background-color 0.18s;
  display: flow-root;
}
.alma-acc-item + .alma-acc-item {
  margin-top: 10px;
}
@media (hover: hover) and (pointer: fine) {
  .alma-acc-item:hover {
    background: #eeece6;
  }
}
.alma-acc-item > summary {
  box-sizing: border-box;
  width: 100%;
  min-height: 64px;
  color: var(--lp-teriyaki);
  font-family: var(--lp-font-body);
  text-align: left;
  cursor: pointer;
  background: 0 0;
  border: 0;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin: 0;
  padding: 18px 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.35;
  list-style: none;
  display: flex;
}
.alma-acc-item > summary::-webkit-details-marker {
  display: none;
}
.alma-acc-item > summary::after {
  content: '';
  width: 14px;
  height: 14px;
  color: var(--lp-collard);
  background:
    linear-gradient(currentcolor, currentcolor) 50% / 100% 2px no-repeat,
    linear-gradient(currentcolor, currentcolor) 50% / 2px 100% no-repeat;
  flex: 0 0 auto;
  transition: background-size 0.18s;
}
.alma-acc-item[open] > summary::after {
  background-size: 100% 2px, 2px 0;
}
.alma-acc-item > p {
  color: var(--lp-muted-ink);
  font-size: 18px;
  font-weight: 400;
  line-height: 1.55;
  overflow-wrap: break-word;
  margin: 0 0 20px;
  animation: acc-reveal 0.18s ease;
}
@keyframes acc-reveal {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.hm-footer {
  background: var(--lp-teriyaki);
  color: var(--lp-surface);
  border-radius: var(--lp-section-radius) var(--lp-section-radius) 0 0;
  position: relative;
  overflow: hidden;
}
.hm-footer .orb {
  width: 118px;
  height: 118px;
  right: 64px;
  top: -26px;
}
.hm-footer-main {
  box-sizing: border-box;
  justify-content: center;
  align-items: center;
  gap: 80px;
  width: min(1200px, 100%);
  margin: 0 auto;
  padding: 80px 80px 24px;
  display: flex;
}
.site-footer-nav {
  --footer-ink: var(--lp-yogurt);
  --footer-heading: var(--lp-kiwi);
  width: min(100%, 1000px);
  color: var(--footer-ink);
  text-align: left;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 32px 24px;
  display: grid;
}
.site-footer-nav-group h2 {
  color: var(--footer-heading);
  font: 500 14px/1.4 var(--lp-font-body);
  margin: 0 0 14px;
}
.site-footer-nav-group ul {
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
}
.site-footer-nav-group a {
  color: var(--footer-ink);
  font: 400 16px/1.4 var(--lp-font-body);
  overflow-wrap: anywhere;
  text-decoration: none;
}
@media (hover: hover) and (pointer: fine) {
  .site-footer-nav-group a:hover {
    text-underline-offset: 3px;
    text-decoration: underline;
  }
}
.hm-newsletter {
  flex-direction: column;
  gap: 24px;
  max-width: 480px;
  display: flex;
}
.hm-newsletter h2 {
  font-family: var(--lp-font-display);
  color: var(--lp-surface);
  margin: 0;
  font-size: 40px;
  font-weight: 300;
  line-height: 1.05;
}
.hm-newsletter p {
  color: rgba(255, 254, 252, 0.7);
  margin: 8px 0 0;
  font-size: 16px;
  line-height: 1.4;
}
.hm-newsletter form {
  flex-direction: column;
  gap: 16px;
  display: flex;
}
.hm-newsletter input {
  box-sizing: border-box;
  width: 100%;
  min-height: 48px;
  color: var(--lp-surface);
  background: rgba(255, 254, 252, 0.1);
  border: 0;
  border-radius: 20px;
  padding: 14px 20px;
  font-size: 16px;
  font-family: var(--lp-font-body);
}
.hm-newsletter input::placeholder {
  color: rgba(255, 254, 252, 0.45);
}
.hm-newsletter .a2-btn {
  align-self: flex-start;
}
.hm-copyright {
  text-align: center;
  color: rgba(255, 254, 252, 0.55);
  padding: 16px 24px 32px;
  font-size: 14px;
  margin: 0;
}

@media (width <= 960px) {
  .pl-hero-chat .a2-bubble {
    max-width: 240px;
    font-size: 15px;
  }
  .pl-hero-you {
    left: -8px;
  }
  .pl-hero-assistant {
    right: -8px;
  }
}
@media (width <= 860px) {
  .pl-hero {
    padding-top: 104px;
  }
  .pl-hero-inner {
    grid-template-columns: 1fr;
    gap: 40px;
  }
  .pl-hero-photo {
    order: 0;
    margin-inline: 8px;
  }
  .pl-hero-photo img {
    border-radius: var(--lp-section-radius-mobile);
    aspect-ratio: 4 / 3;
  }
  .pl-hero-copy {
    order: 1;
  }
  .pl-hero-title {
    margin-top: 20px;
  }
  .pl-hero-chat .a2-bubble {
    max-width: 220px;
    padding: 10px 14px;
    font-size: 14px;
  }
  .pl-hero-you {
    top: -14px;
    left: -12px;
  }
  .pl-hero-assistant {
    bottom: -20px;
    right: -12px;
  }
  .pl-plans-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }
  .pl-card--featured {
    order: -1;
  }
  .pl-week-panel {
    border-radius: var(--lp-section-radius-mobile);
    grid-template-columns: 1fr;
    gap: 32px;
    padding: 32px 20px;
  }
  .pl-week-copy .a2-btn {
    width: 100%;
  }
  .pl-shoot-grid {
    grid-template-columns: 1fr 1fr;
    gap: 10px;
    margin-top: 28px;
  }
  .pl-shoot-grid img {
    border-radius: 20px;
    height: 240px;
  }
  .pl-shoot-grid .pl-shoot-wide {
    grid-column: 1 / -1;
    height: 220px;
  }
  .hm-feature {
    border-radius: var(--lp-section-radius-mobile);
    grid-template-columns: 1fr;
  }
  .hm-feature-photo {
    aspect-ratio: 1 / 1;
    min-height: 0;
  }
  .hm-quotes {
    padding-inline: 16px;
  }
  .hm-quote {
    flex-basis: 78vw;
    min-height: 0;
  }
  .hm-quote blockquote {
    font-size: 24px;
  }
}
@media (width <= 768px) {
  .nav-content {
    background: var(--lp-surface);
    width: calc(100% - 32px);
    gap: 6px;
    padding: 8px 12px 8px 14px;
    justify-content: space-between;
  }
  .nav-left {
    display: none;
  }
  .hm-footer {
    border-radius: var(--lp-section-radius-mobile) var(--lp-section-radius-mobile) 0 0;
  }
  .hm-footer-main {
    flex-direction: column;
    align-items: stretch;
    gap: 40px;
    padding: 48px 16px 16px;
  }
  .hm-newsletter {
    gap: 14px;
    max-width: 100%;
  }
  .hm-newsletter h2 {
    font-size: 28px;
  }
  .site-footer-nav {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 28px 18px;
  }
}
@media (width <= 760px) {
  .lp-page {
    --lp-gap: 64px;
  }
  .hm-members {
    padding-bottom: calc(var(--lp-gap) / 2 - 24px);
  }
}
@media (width <= 600px) {
  .a2-btn {
    border-radius: 16px;
  }
  .a2-proof {
    gap: 16px 28px;
  }
}
@media (width <= 480px) {
  .pl-hero-cta {
    width: 100%;
  }
}
</style>
