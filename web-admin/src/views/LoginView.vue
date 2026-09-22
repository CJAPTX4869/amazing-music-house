<template>
  <div class="login-page">
    <div class="login-card">
      <!-- 品牌区 -->
      <div class="brand">
        <div class="logo">🎵</div>
        <h1>音乐文件管理系统</h1>
        <p class="text-muted">管理员上传歌曲 · 全员在线收听与下载</p>
      </div>

      <!-- 表单区 -->
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
        </div>

        <el-divider v-if="mode === 'login'">
          <span class="text-muted" style="font-size: 12px">演示账号</span>
        </el-divider>
        <div v-if="mode === 'login'" class="demo-tip">
          <code>admin</code> / <code>admin123</code>（管理员）
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Postcard } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const mode = ref<'login' | 'register'>('login')

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: ''
})

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

  loading.value = true
  try {
    if (mode.value === 'login') {
      await userStore.login({ username: form.username, password: form.password })
      ElMessage.success(`欢迎，${userStore.userInfo?.nickname || userStore.userInfo?.username}`)
    } else {
      await userStore.register({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined
      })
      ElMessage.success('注册成功，请登录')
      toggleMode()
      loading.value = false
      return
    }
    // 跳转到原目标页或首页
    const redirect = (route.query.redirect as string) || '/songs'
    router.push(redirect)
  } catch {
    // request 拦截器已统一提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 420px;
  max-width: 92vw;
  padding: 36px 36px 28px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);

  .brand {
    text-align: center;
    margin-bottom: 28px;
    .logo {
      font-size: 40px;
    }
    h1 {
      font-size: 20px;
      margin: 8px 0 4px;
      color: #1f2937;
    }
    p {
      margin: 0;
      font-size: 13px;
    }
  }

  .submit {
    width: 100%;
    margin-top: 8px;
  }

  .switch {
    margin-top: 14px;
    text-align: center;
  }

  .demo-tip {
    text-align: center;
    font-size: 13px;
    color: #6b7280;
    code {
      background: #f3f4f6;
      padding: 2px 6px;
      border-radius: 4px;
      font-family: ui-monospace, Consolas, monospace;
    }
  }
}
</style>
