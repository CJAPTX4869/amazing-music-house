<template>
  <div class="login-page">
    <Transition name="step" mode="out-in">
      <!-- 第一步：账号密码 -->
      <div v-if="step === 'form'" key="form" class="login-card">
        <div class="brand">
          <div class="logo-tile" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="30" height="30" fill="none">
              <circle cx="6.9" cy="18.1" r="2.9" fill="#fff" />
              <circle cx="18.1" cy="16.1" r="2.9" fill="#fff" />
              <path d="M9.8 18.1V7.4L21 5v11.1" stroke="#fff" stroke-width="1.9" stroke-linejoin="round" />
              <path d="M9.8 10.6 21 8.2" stroke="#fff" stroke-width="2.6" stroke-linecap="round" />
            </svg>
          </div>
          <h1>音乐文件管理系统</h1>
          <p class="brand-sub">上传歌曲 · 在线收听 · 一键分享</p>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="4-20 位字母、数字或下划线" :prefix-icon="User" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="6-20 位"
              :prefix-icon="Lock"
              @keyup.enter="handleSubmit"
            />
          </el-form-item>

          <template v-if="mode === 'register'">
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="form.confirmPassword" type="password" show-password placeholder="再次输入密码" :prefix-icon="Lock" />
            </el-form-item>
            <el-form-item label="昵称（可选）" prop="nickname">
              <el-input v-model="form.nickname" placeholder="默认与用户名相同" :prefix-icon="Postcard" />
            </el-form-item>
          </template>

          <el-button type="primary" class="submit" :loading="loading" @click="handleSubmit">
            {{ mode === 'login' ? '登 录' : '注 册' }}
          </el-button>

          <div class="switch">
            <el-link type="primary" :underline="false" @click="toggleMode">
              {{ mode === 'login' ? '还没有账号？去注册' : '已有账号？去登录' }}
            </el-link>
            <el-link v-if="mode === 'login'" type="info" :underline="false" @click="forgotVisible = true">
              忘记密码？
            </el-link>
          </div>
        </el-form>

        <div class="card-foot">
          <span class="secure-hint">
            <svg viewBox="0 0 16 16" width="13" height="13" fill="none" aria-hidden="true">
              <path d="M8 1.5 13 3.4V7c0 3.2-2.1 5.9-5 7.5C5.1 12.9 3 10.2 3 7V3.4L8 1.5Z" stroke="currentColor" stroke-width="1.3" stroke-linejoin="round" />
              <path d="M5.8 7.6 7.3 9.1l3-3.2" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            受抓娃娃人机验证保护
          </span>
        </div>
      </div>

      <!-- 第二步：抓娃娃人机验证，通过后自动登录 -->
      <div v-else key="captcha" class="captcha-step">
        <div class="captcha-topbar">
          <button type="button" class="back-btn" :disabled="submitting" @click="backToForm">
            <svg viewBox="0 0 16 16" width="14" height="14" fill="none" aria-hidden="true">
              <path d="M9.8 3.4 5.2 8l4.6 4.6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
            返回
          </button>
          <span class="topbar-title" :class="{ ok: captchaVerified }">
            <template v-if="captchaVerified">
              <svg viewBox="0 0 16 16" width="13" height="13" fill="none" aria-hidden="true">
                <circle cx="8" cy="8" r="6.4" fill="currentColor" />
                <path d="M5.5 8.2 7.2 10l3.4-3.8" stroke="#fff" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
              验证通过，正在登录…
            </template>
            <template v-else>{{ mode === 'login' ? '完成验证，自动登录' : '完成验证，自动注册' }}</template>
          </span>
        </div>
        <ClawCaptcha @verify="onCaptchaVerified" />
      </div>
    </Transition>
  </div>
  <!-- 忘记密码：用户名 + 恢复码直接重置 -->
  <el-dialog v-model="forgotVisible" title="找回密码" width="360px" :close-on-click-modal="false">
    <el-form label-width="72px">
      <el-form-item label="用户名">
        <el-input v-model="forgotForm.username" placeholder="你的账号" />
      </el-form-item>
      <el-form-item label="恢复码">
        <el-input v-model="forgotForm.recoveryCode" placeholder="部署时设定的恢复码" show-password />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="forgotForm.newPassword" type="password" show-password placeholder="至少 6 位" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="forgotVisible = false">取消</el-button>
      <el-button type="primary" :loading="forgotLoading" @click="handleForgot">重置密码</el-button>
    </template>
  </el-dialog>
</template>


<script setup lang="ts">
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Postcard } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import { captchaChallenge, captchaVerify, forgotPassword } from '@/api/auth'
import ClawCaptcha from '@/components/ClawCaptcha.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const mode = ref<'login' | 'register'>('login')
const step = ref<'form' | 'captcha'>('form')
const captchaVerified = ref(false)
const captchaTicket = ref('')

// 忘记密码
const forgotVisible = ref(false)
const forgotLoading = ref(false)
const forgotForm = reactive({ username: '', recoveryCode: '', newPassword: '' })

async function handleForgot() {
  if (!forgotForm.username || !forgotForm.recoveryCode || forgotForm.newPassword.length < 6) {
    ElMessage.warning('请填完整：用户名 / 恢复码 / 新密码至少 6 位')
    return
  }
  forgotLoading.value = true
  try {
    await forgotPassword({ ...forgotForm })
    ElMessage.success('密码已重置，请用新密码登录')
    forgotVisible.value = false
    forgotForm.recoveryCode = ''
    forgotForm.newPassword = ''
  } finally {
    forgotLoading.value = false
  }
}
// 验证通过后延迟自动提交的定时器句柄，卸载时清理
let submitTimer: number | undefined

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: ''
})

async function onCaptchaVerified() {
  try {
    await captchaVerify(captchaTicket.value)
  } catch {
    ElMessage.error('验证票据确认失败，请重试')
    return
  }
  captchaVerified.value = true
  // 留 0.9s 展示庆祝动画，随后自动提交
  submitTimer = window.setTimeout(() => doSubmit(), 900)
}

function backToForm() {
  step.value = 'form'
}

const rules = reactive<FormRules>({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_]{4,20}$/, message: '4-20 位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度 6-20 位', trigger: 'blur' }
  ],
  confirmPassword: [
    {
      validator: (_rule, value, callback) => {
        if (mode.value === 'register' && value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
})

function toggleMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  form.confirmPassword = ''
  formRef.value?.clearValidate()
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  // 人机验证前置：未通过时先进入抓娃娃步骤；已通过的直接提交，
  // 这样凭据永远在验证码之后发出，防暴力破解不失效
  if (!captchaVerified.value) {
    step.value = 'captcha'
    // 领取一次性票据：登录时服务端校验并消费，纯前端"已验证"不再有任何效力
    captchaChallenge()
      .then(d => (captchaTicket.value = d.ticket))
      .catch(() => ElMessage.error('获取验证票据失败，请检查服务端'))
    return
  }
  await doSubmit()
}

async function doSubmit() {
  submitting.value = true
  loading.value = true
  try {
    if (mode.value === 'login') {
      await userStore.login({ username: form.username, password: form.password, ticket: captchaTicket.value })
      ElMessage.success(`欢迎，${userStore.userInfo?.nickname || userStore.userInfo?.username}`)
    } else {
      await userStore.register({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined
      })
      ElMessage.success('注册成功，请登录')
      toggleMode()
      captchaVerified.value = false
      step.value = 'form'
      return
    }
    const redirect = (route.query.redirect as string) || '/songs'
    router.push(redirect)
  } catch {
    // 票据已被服务端一次性消费：无论成败，下次登录都要重过抓娃娃
    captchaVerified.value = false
    step.value = 'form'
  } finally {
    submitting.value = false
    loading.value = false
  }
}

onBeforeUnmount(() => {
  if (submitTimer !== undefined) window.clearTimeout(submitTimer)
})
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  padding: 32px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(900px 520px at 12% -8%, rgba(99, 102, 241, 0.38), transparent 62%),
    radial-gradient(820px 500px at 108% 12%, rgba(168, 85, 247, 0.30), transparent 60%),
    radial-gradient(720px 520px at 50% 112%, rgba(56, 189, 248, 0.16), transparent 62%),
    linear-gradient(160deg, #0f172a 0%, #1e1b4b 55%, #312e81 100%);
}

/* 两步切换动画 */
.step-enter-active,
.step-leave-active {
  transition: opacity 0.26s ease, transform 0.26s ease;
}
.step-enter-from {
  opacity: 0;
  transform: translateY(14px) scale(0.985);
}
.step-leave-to {
  opacity: 0;
  transform: translateY(-10px) scale(0.985);
}

.login-card {
  width: 420px;
  max-width: 94vw;
  padding: 40px 40px 26px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(255, 255, 255, 0.65);
  border-radius: 22px;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.06),
    0 24px 70px -18px rgba(15, 23, 42, 0.45);
}

.brand {
  text-align: center;
  margin-bottom: 26px;

  .logo-tile {
    width: 60px;
    height: 60px;
    margin: 0 auto 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 18px;
    background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 58%, #a78bfa 100%);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.35),
      0 10px 26px -6px rgba(99, 102, 241, 0.55);
  }

  h1 {
    font-size: 21px;
    font-weight: 700;
    letter-spacing: 0.5px;
    margin: 0 0 6px;
    color: #111827;
  }

  .brand-sub {
    margin: 0;
    font-size: 13px;
    letter-spacing: 1.5px;
    color: #8a8f99;
  }
}

.submit {
  width: 100%;
  margin-top: 6px;
  height: 44px;
  font-size: 15px;
  letter-spacing: 4px;
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
  box-shadow: 0 8px 20px -6px rgba(99, 102, 241, 0.55);

  &:hover,
  &:focus {
    background: linear-gradient(135deg, #5457e8 0%, #7c4ef0 100%);
    box-shadow: 0 10px 24px -6px rgba(99, 102, 241, 0.65);
  }
}

.switch {
  display: flex;
  align-items: center;
  justify-content: space-between;   /* 注册在左 · 忘记密码在右 */
  gap: 12px;
  margin-top: 14px;
}

.card-foot {
  margin-top: 20px;
  padding-top: 14px;
  border-top: 1px solid #f0f1f4;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  .secure-hint {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;
    color: #9ca3af;
  }

  .demo-tip {
    font-size: 12px;
    color: #b0b5bf;

    code {
      background: #f4f5f7;
      padding: 1px 5px;
      border-radius: 4px;
      font-family: ui-monospace, Consolas, monospace;
      color: #6b7280;
    }
  }
}

/* 第二步：娃娃机本体即卡片 */
.captcha-step {
  width: 440px;
  max-width: 94vw;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.captcha-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
  color: rgba(255, 255, 255, 0.85);

  .back-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    padding: 5px 12px 5px 8px;
    font-size: 13px;
    color: rgba(255, 255, 255, 0.85);
    background: rgba(255, 255, 255, 0.12);
    border: 1px solid rgba(255, 255, 255, 0.22);
    border-radius: 999px;
    cursor: pointer;
    transition: background 0.2s ease;

    &:hover:not(:disabled) {
      background: rgba(255, 255, 255, 0.2);
    }

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
  }

  .topbar-title {
    font-size: 13px;
    letter-spacing: 0.5px;

    &.ok {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      color: #4ade80;
    }
  }
}
</style>
