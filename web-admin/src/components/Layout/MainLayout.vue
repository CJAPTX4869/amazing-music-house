<template>
  <el-container class="layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '200px'" class="layout-aside">
      <div class="logo">
        <span class="logo-icon">🎵</span>
        <span v-show="!collapsed" class="logo-text">音乐库</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        background-color="#1f2937"
        text-color="#d1d5db"
        active-text-color="#fff"
        router
      >
        <el-menu-item index="/songs">
          <el-icon><Headset /></el-icon>
          <template #title>歌曲库</template>
        </el-menu-item>

        <el-sub-menu v-if="userStore.isAdmin" index="admin">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>管理后台</span>
          </template>
          <el-menu-item index="/upload">
            <el-icon><Upload /></el-icon>
            <template #title>上传歌曲</template>
          </el-menu-item>
          <el-menu-item index="/admin/songs">
            <el-icon><Files /></el-icon>
            <template #title>歌曲管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <template #title>用户管理</template>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="userStore.isAdmin" index="ops">
          <template #title>
            <el-icon><Monitor /></el-icon>
            <span>运维后台</span>
          </template>
          <el-menu-item index="/admin/ops">
            <el-icon><Monitor /></el-icon>
            <span>运维状态</span>
          </el-menu-item>
          <el-menu-item index="/admin/online">
            <el-icon><User /></el-icon>
            <span>用户运维管理</span>
          </el-menu-item>
          <el-menu-item index="/admin/song-ops">
            <el-icon><Files /></el-icon>
            <span>歌曲状态总览</span>
          </el-menu-item>
          <el-menu-item index="/admin/play-daily">
            <el-icon><TrendCharts /></el-icon>
            <span>每日播放统计</span>
          </el-menu-item>
          <el-menu-item index="/admin/hot-songs">
            <el-icon><Trophy /></el-icon>
            <span>最火歌曲</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶栏 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-button text @click="collapsed = !collapsed">
            <el-icon :size="18">
              <Expand v-if="collapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: '/songs' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ pageTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <template v-if="userStore.isLogin">
            <el-tag v-if="userStore.isAdmin" type="warning" size="small" effect="dark">管理员</el-tag>
            <el-tag v-else size="small">普通用户</el-tag>
            <span class="username">{{ userStore.userInfo?.nickname || userStore.userInfo?.username }}</span>
            <el-button text @click="pwdVisible = true">改密</el-button>
            <el-button text @click="handleLogout">退出</el-button>
          </template>
          <el-button v-else type="primary" size="small" @click="router.push('/login')">
            登录
          </el-button>
        </div>
      </el-header>

      <!-- 主内容 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>

    <!-- 修改密码 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="360px" :close-on-click-modal="false">
      <el-form label-width="72px">
        <el-form-item label="旧密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="handleChangePwd">保存</el-button>
      </template>
    </el-dialog>

    <!-- 全局播放器 -->
    <AudioPlayer />
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Headset, Setting, Upload, Files, User, Expand, Fold, Monitor, TrendCharts, Trophy } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import AudioPlayer from '@/components/AudioPlayer.vue'
import { changePassword } from '@/api/auth'
import { reactive } from 'vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string) || '')

// 修改密码
const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '' })

async function handleChangePwd() {
  if (!pwdForm.oldPassword || pwdForm.newPassword.length < 6) {
    ElMessage.warning('请填旧密码，新密码至少 6 位')
    return
  }
  pwdLoading.value = true
  try {
    await changePassword({ ...pwdForm })
    ElMessage.success('密码修改成功，下次登录用新密码')
    pwdVisible.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
  } finally {
    pwdLoading.value = false
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch {
    // 用户取消
  }
}
</script>

<style scoped lang="scss">
.layout {
  height: 100vh;
}

.layout-aside {
  background: #1f2937;
  transition: width 0.2s;
  overflow-x: hidden;

  .logo {
    height: var(--music-header-height);
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    color: #fff;
    font-size: 16px;
    font-weight: 600;
    background: #111827;

    .logo-icon {
      font-size: 20px;
    }
  }

  .el-menu {
    border-right: none;
  }
}

.layout-header {
  height: var(--music-header-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 16px;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 10px;

    .username {
      font-size: 13px;
      color: #4b5563;
    }
  }
}

.layout-main {
  padding: 16px;
  background: var(--music-bg);
  overflow-y: auto;
  /* 给底部播放器留出空间 */
  padding-bottom: calc(var(--music-player-height) + 16px);
}
</style>
