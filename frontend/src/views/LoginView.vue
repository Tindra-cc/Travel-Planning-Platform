<template>
  <div class="login-page">
    <el-card class="login-card" :header="mode === 'login' ? '登录' : '注册'">
      <el-alert
        v-if="lockRemaining > 0"
        class="lock-alert"
        type="error"
        :closable="false"
        show-icon
        :title="lockText"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="手机号或邮箱" />
        </el-form-item>
        <el-form-item label="账号类型" prop="accountType">
          <el-radio-group v-model="form.accountType">
            <el-radio value="mobile">手机号</el-radio>
            <el-radio value="email">邮箱</el-radio>
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
          <el-input v-model="form.password" type="password" show-password placeholder="至少 8 位" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" :disabled="lockRemaining > 0" @click="onSubmit">
            {{ mode === 'login' ? '登录' : '注册并登录' }}
          </el-button>
          <el-button text @click="toggleMode">
            {{ mode === 'login' ? '没有账号？注册' : '已有账号？登录' }}
          </el-button>
        </el-form-item>
      </el-form>
      <div v-if="mode === 'register' && form.accountType === 'mobile'" class="channel-hint">
        短信通道未接入，开发环境将回显验证码
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
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

async function onSubmit() {
  await formRef.value?.validate()
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
    const redirect = (route.query.redirect as string) || '/profile'
    router.push(redirect)
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
.login-page {
  display: flex;
  justify-content: center;
  padding-top: 60px;
}
.login-card {
  width: 440px;
}
.lock-alert {
  margin-bottom: 12px;
}
.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.channel-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}
</style>
