<template>
  <div class="auth">
    <!-- 左：品牌氛围栏（纯视觉，窄屏隐藏） -->
    <section class="brand-pane">
      <div class="blob blob-a" />
      <div class="blob blob-b" />
      <div class="brand-inner">
        <div class="chip">✈ AI 旅行规划助理</div>
        <h1 class="brand-title">一起去<br />看山海</h1>
        <p class="brand-sub">告诉它你想去哪，剩下的交给 AI —— 行程、预算、攻略一并生成。</p>
        <div class="float-cards">
          <div class="fcard">
            <span class="fcard-ico">🗺️</span>
            <div>
              <div class="fcard-t">行程编排</div>
              <div class="fcard-d">按节奏与预算自动排</div>
            </div>
          </div>
          <div class="fcard">
            <span class="fcard-ico">💰</span>
            <div>
              <div class="fcard-t">预算把控</div>
              <div class="fcard-d">档位内选最优解</div>
            </div>
          </div>
          <div class="fcard">
            <span class="fcard-ico">📖</span>
            <div>
              <div class="fcard-t">成册攻略</div>
              <div class="fcard-d">一键生成旅行手册</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 右：表单 -->
    <section class="form-pane">
      <div class="form-card">
        <el-alert
          v-if="lockRemaining > 0"
          class="lock-alert"
          type="warning"
          :closable="false"
          show-icon
          :title="lockText"
        />

        <header class="form-head">
          <h2>{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h2>
          <p>{{ mode === 'login' ? '登录并继续你的行程' : '注册后即可开始规划' }}</p>
        </header>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="账号" prop="account">
            <el-input v-model="form.account" placeholder="邮箱或手机号" />
          </el-form-item>

          <el-form-item label="账号类型">
            <el-radio-group v-model="form.accountType">
              <el-radio-button value="email">邮箱</el-radio-button>
              <el-radio-button value="mobile">手机号</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="mode === 'register'" label="验证码" prop="verifyCode">
            <div class="code-row">
              <el-input v-model="form.verifyCode" placeholder="6 位验证码" />
              <el-button :disabled="countdown > 0" @click="onSendCode">
                {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
              </el-button>
            </div>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="至少 8 位"
            />
          </el-form-item>

          <el-button
            class="submit-btn"
            type="primary"
            :loading="loading"
            :disabled="lockRemaining > 0"
            @click="onSubmit"
          >
            {{ mode === 'login' ? '登录' : '注册并登录' }}
          </el-button>
        </el-form>

        <div class="form-foot">
          <span>{{ mode === 'login' ? '没有账号？' : '已有账号？' }}</span>
          <el-button text type="primary" @click="toggleMode">
            {{ mode === 'login' ? '注册' : '登录' }}
          </el-button>
        </div>

        <div v-if="mode === 'register' && form.accountType === 'mobile'" class="channel-hint">
          短信通道未接入，开发环境将回显验证码
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { sendVerifyCode } from '@/api/auth'
import { ApiError } from '@/api/request'
import { useAuthStore } from '@/stores/auth'
import type { VerifyCodeResponse } from '@/types/api'

const LOCK_SECONDS = 30 * 60
const EMAIL_RE = /^[\w.+-]+@[\w-]+(\.[\w-]+)+$/
const MOBILE_RE = /^1[3-9]\d{9}$/

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const countdown = ref(0)
const lockRemaining = ref(0)
const formRef = ref<FormInstance>()
let codeTimer: number | undefined
let lockTimer: number | undefined

const form = reactive({
  account: '',
  accountType: 'email' as 'mobile' | 'email',
  verifyCode: '',
  password: ''
})

function validateAccount(_rule: unknown, value: string, callback: (error?: Error) => void) {
  if (!value) {
    callback()
    return
  }
  const valid = form.accountType === 'email' ? EMAIL_RE.test(value) : MOBILE_RE.test(value)
  callback(valid ? undefined : new Error(form.accountType === 'email' ? '邮箱格式不正确' : '手机号格式不正确'))
}

const rules: FormRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }, { validator: validateAccount, trigger: 'blur' }],
  password: [{ required: true, min: 8, message: '密码至少 8 位', trigger: 'blur' }]
}

const lockText = computed(() => {
  const total = lockRemaining.value
  const mm = String(Math.floor(total / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return `账号已锁定，请 ${mm}:${ss} 后重试`
})

watch(
  () => form.accountType,
  () => formRef.value?.validateField('account')
)

function toggleMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
}

function startLockCountdown() {
  lockRemaining.value = LOCK_SECONDS
  clearInterval(lockTimer)
  lockTimer = window.setInterval(() => {
    lockRemaining.value--
    if (lockRemaining.value <= 0) clearInterval(lockTimer)
  }, 1000)
}

async function onSendCode() {
  try {
    await formRef.value?.validateField('account')
  } catch {
    return
  }
  let res: VerifyCodeResponse
  try {
    res = await sendVerifyCode({
      account: form.account,
      accountType: form.accountType,
      scene: 'register'
    })
  } catch {
    return
  }
  if (res.code) {
    ElMessage.success(`验证码已发送（开发环境回显：${res.code}）`)
  } else {
    ElMessage.success(`验证码已发送至 ${form.account}`)
  }
  countdown.value = 60
  clearInterval(codeTimer)
  codeTimer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(codeTimer)
  }, 1000)
}

// 只接受能匹配到路由的站内路径，避免跳到不存在的路径导致白屏
function safeRedirect(): string {
  const raw = route.query.redirect
  const value = typeof raw === 'string' ? raw : ''
  if (!value.startsWith('/') || value.startsWith('//')) return '/discover'
  return router.resolve(value).matched.length ? value : '/discover'
}

onBeforeUnmount(() => {
  clearInterval(codeTimer)
  clearInterval(lockTimer)
})

async function onSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return // 校验未通过，表单已就地提示
  }
  loading.value = true
  try {
    if (mode.value === 'login') {
      await auth.login({ account: form.account, password: form.password })
    } else {
      await auth.register({
        account: form.account,
        accountType: form.accountType,
        verifyCode: form.verifyCode,
        password: form.password
      })
    }
    ElMessage.success('登录成功')
    router.push(safeRedirect())
  } catch (err) {
    if (err instanceof ApiError && err.code === 42300) {
      startLockCountdown()
    }
    // 其余错误信息已由 axios 拦截器统一提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  min-height: calc(100vh - 64px);
}

/* 左：品牌氛围栏 */
.brand-pane {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding: var(--space-12) 56px;
  background: var(--grad-brand);
  color: #fff;
}
.blob {
  position: absolute;
  border-radius: 50%;
  filter: blur(46px);
  opacity: 0.55;
  pointer-events: none;
}
.blob-a {
  width: 340px;
  height: 340px;
  background: #a5f3e8;
  top: -90px;
  right: -70px;
}
.blob-b {
  width: 280px;
  height: 280px;
  background: #7cc6ff;
  bottom: -80px;
  left: -60px;
  opacity: 0.5;
}
.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 440px;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: 6px 14px;
  margin-bottom: var(--space-6);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.35);
  font-size: 13px;
}
.brand-title {
  margin: 0 0 var(--space-4);
  font-size: 54px;
  line-height: 1.12;
  font-weight: 700;
  letter-spacing: -0.5px;
}
.brand-sub {
  margin: 0 0 var(--space-8);
  font-size: 15px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.9);
}
.float-cards {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-width: 340px;
}
.fcard {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12px 28px rgba(6, 60, 60, 0.18);
  color: var(--text-1);
}
.fcard:nth-child(2) {
  transform: translateX(28px);
}
.fcard-ico {
  font-size: 20px;
}
.fcard-t {
  font-size: 14px;
  font-weight: 600;
}
.fcard-d {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 2px;
}

/* 右：表单 */
.form-pane {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-12) var(--space-6);
}
.form-card {
  width: 100%;
  max-width: 420px;
  padding: 40px 36px;
  background: var(--surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}
.form-head h2 {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.2px;
}
.form-head p {
  margin: 0 0 var(--space-6);
  font-size: 14px;
  color: var(--text-2);
}
.lock-alert {
  margin-bottom: var(--space-4);
}
.code-row {
  display: flex;
  gap: var(--space-2);
  width: 100%;
}
.submit-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  margin-top: var(--space-2);
}
.form-foot {
  margin-top: var(--space-4);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  font-size: 13px;
  color: var(--text-2);
}
.channel-hint {
  margin-top: var(--space-3);
  font-size: 12px;
  color: var(--text-3);
  text-align: center;
}

@media (max-width: 900px) {
  .auth {
    grid-template-columns: 1fr;
  }
  .brand-pane {
    display: none;
  }
}
</style>
