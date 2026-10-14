<template>
  <div class="login-page">
    <el-card class="login-card" :header="mode === 'login' ? '登录' : '注册'">
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
          <el-button type="primary" :loading="loading" @click="onSubmit">
            {{ mode === 'login' ? '登录' : '注册并登录' }}
          </el-button>
          <el-button text @click="toggleMode">
            {{ mode === 'login' ? '没有账号？注册' : '已有账号？登录' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { sendVerifyCode } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import type { VerifyCodeResponse } from '@/types/api'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

const mode = ref<'login' | 'register'>('login')
const loading = ref(false)
const countdown = ref(0)
const formRef = ref<FormInstance>()

const form = reactive({
  account: '',
  accountType: 'mobile' as 'mobile' | 'email',
  verifyCode: '',
  password: ''
})

const rules: FormRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, min: 8, message: '密码至少 8 位', trigger: 'blur' }]
}

function toggleMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
}

async function onSendCode() {
  if (!form.account) {
    ElMessage.warning('请先填写账号')
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
    ElMessage.success('验证码已发送')
  }
  countdown.value = 60
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) clearInterval(timer)
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
  } catch {
    // 错误信息已由 axios 拦截器统一提示
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
.code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
