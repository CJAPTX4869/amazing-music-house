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

    <!-- 全局播放器 -->
    <AudioPlayer />
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Headset, Setting, Upload, Files, User, Expand, Fold } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import AudioPlayer from '@/components/AudioPlayer.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string) || '')

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
