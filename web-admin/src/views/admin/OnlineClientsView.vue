<template>
  <div class="online-page">
    <div class="ops-head">
      <h2>用户运维管理</h2>
      <div class="ops-head-right">
        <span class="refresh-at">更新于 {{ updatedAt }}</span>
        <el-switch v-model="auto" active-text="自动刷新" />
        <el-button type="primary" size="small" :loading="loading" @click="load">立即刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" style="margin-bottom:14px"
              title="统计规则：每个客户端登录后，只要 90 秒内有任意请求即视为在线；完全离线 90 秒后自动从列表消失。" />

    <div class="total-bar">
      <el-tag type="success" effect="dark" size="large">总在线 {{ online.total ?? 0 }}</el-tag>
      <span class="muted">网页端 {{ cnt('web') }} · 管理后台 {{ cnt('admin') }} · 桌面端 {{ cnt('desktop') }} · 移动端 {{ cnt('mobile') }}</span>
    </div>

    <el-row :gutter="14" class="mt14">
      <el-col :span="12" v-for="p in online.platforms || []" :key="p.key">
        <el-card shadow="never" :class="p.online ? 'plat online' : 'plat offline'">
          <template #header>
            <div class="plat-head">
              <span class="dot"></span>
              <b>{{ p.label }}</b>
              <span class="plat-state">{{ p.online ? '● 在线 ' + p.count + ' 人' : '○ 离线' }}</span>
            </div>
          </template>
          <div v-if="!p.users || !p.users.length" class="plat-empty">暂无设备接入</div>
          <div v-for="u in p.users || []" :key="p.key + u.userId" class="user-card">
            <!-- 头像：后端公开接口 /api/users/{id}/avatar，无头像时显示首字 -->
            <el-avatar :size="48" :src="avatarSrc(u)" @error="() => true">
              {{ (u.nickname || u.username || '?').slice(0, 1) }}
            </el-avatar>
            <div class="user-info">
              <div class="user-name">
                {{ u.nickname || u.username }}
                <el-tag size="small" effect="plain" style="margin-left:6px">已登录</el-tag>
                <el-tag size="small" effect="dark" style="margin-left:4px">{{ p.label }}</el-tag>
              </div>
              <div class="user-line">
                <span class="muted">账号</span> {{ u.username }}
                <el-divider direction="vertical" />
                <span class="muted">IP:端口</span>
                <b>{{ u.ip }}:{{ u.port }}</b>
              </div>
              <div class="user-line">
                <span class="muted">最后活跃</span> {{ ago(u.lastSeenMs) }}
                <el-divider direction="vertical" />
                <span class="muted">用户ID</span> {{ u.userId }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { opsOnline } from '@/api/admin'

const online = ref<Record<string, any>>({})
const loading = ref(false)
const auto = ref(true)
const updatedAt = ref('')
let timer: number | undefined

async function load() {
  loading.value = true
  try {
    online.value = await opsOnline()
    updatedAt.value = new Date().toLocaleTimeString()
  } finally {
    loading.value = false
  }
}

const platforms = computed(() => online.value.platforms || [])
function cnt(key: string) {
  const p = platforms.value.find((x: any) => x.key === key)
  return p ? p.count : 0
}
function avatarSrc(u: any) {
  return u.avatar ? `/api/users/${u.userId}/avatar` : ''
}
function ago(ms: number) {
  const sec = Math.max(0, Math.round((Date.now() - ms) / 1000))
  return sec < 60 ? sec + ' 秒前' : Math.floor(sec / 60) + ' 分钟前'
}

onMounted(() => {
  load()
  timer = window.setInterval(() => auto.value && load(), 5000)
})
onUnmounted(() => timer && clearInterval(timer))
</script>

<style scoped lang="scss">
.ops-page { padding: 4px; }
.ops-head {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
  h2 { margin: 0; }
  .ops-head-right { display: flex; align-items: center; gap: 12px; .refresh-at { color: #909399; font-size: 12px; } }
}
.total-bar {
  display: flex; align-items: center; gap: 14px; margin-bottom: 14px;
  .muted { color: #909399; font-size: 13px; }
}
.mt14 { margin-top: 14px; }
.plat {
  min-height: 120px;
  &.online { border-color: #b3e19d; background: #f0f9eb; }
  &.offline { background: #fafafa; }
  .plat-head { display: flex; align-items: center; gap: 8px;
    .dot { width: 9px; height: 9px; border-radius: 50%; background: #c0c4cc; }
    .plat-state { margin-left: auto; font-size: 12px; color: #909399; } }
  &.online .plat-head .dot { background: #67c23a; box-shadow: 0 0 8px #67c23a; }
  .plat-empty { color: #c0c4cc; font-size: 13px; text-align: center; padding: 22px 0; }
}
.user-card {
  display: flex; gap: 12px; padding: 10px 6px; border-bottom: 1px dashed #ebeef5;
  &:last-child { border-bottom: none; }
  .user-info { flex: 1; min-width: 0;
    .user-name { font-weight: 600; }
    .user-line { font-size: 13px; color: #606266; padding-top: 3px;
      .muted { color: #909399; margin-right: 4px; } b { font-family: Consolas, monospace; } } }
}
</style>
