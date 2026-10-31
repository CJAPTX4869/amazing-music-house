<template>
  <div class="ops-page">
    <div class="ops-head">
      <h2>歌曲状态总览</h2>
      <div class="ops-head-right">
        <span class="refresh-at">更新于 {{ updatedAt }}</span>
        <el-switch v-model="auto" active-text="自动刷新" />
        <el-button size="small" @click="$router.push('/admin/play-daily')">每日播放统计</el-button>
        <el-button size="small" @click="$router.push('/admin/hot-songs')">最火歌曲</el-button>
        <el-button type="primary" size="small" :loading="loading" @click="load">立即刷新</el-button>
      </div>
    </div>

    <!-- 大数字卡 -->
    <el-row :gutter="14">
      <el-col :span="4"><el-card shadow="never"><div class="big"><b>{{ st.total ?? '-' }}</b><span>歌曲总数</span></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="big"><b class="ok">{{ st.online ?? '-' }}</b><span>上架中</span></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="big"><b class="warn">{{ st.offline ?? '-' }}</b><span>已下架</span></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="big"><b class="ok">{{ st.withLyric ?? '-' }}</b><span>有歌词</span></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="big"><b class="warn">{{ st.withoutLyric ?? '-' }}</b><span>无歌词</span></div></el-card></el-col>
      <el-col :span="4"><el-card shadow="never"><div class="big"><b class="warn">{{ st.withoutCover ?? '-' }}</b><span>无专辑图</span></div></el-card></el-col>
    </el-row>

    <!-- 类型 / 标签分布 -->
    <el-row :gutter="14" class="mt14">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>类型分布（第一个标签 = 类型）</template>
          <div v-for="(v, k) in st.typeDist || {}" :key="k" class="dist-row">
            <span class="dist-name">{{ k }}</span>
            <el-progress class="dist-bar" :percentage="pct(v, st.total)" :stroke-width="10" :color="barColor" />
            <span class="dist-num">{{ v }}</span>
          </div>
          <el-empty v-if="!Object.keys(st.typeDist || {}).length" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>标签分布（全部标签，最多 3 个/首）</template>
          <div v-for="(v, k) in st.tagDist || {}" :key="k" class="dist-row">
            <span class="dist-name">{{ k }}</span>
            <el-progress class="dist-bar" :percentage="pct(v, st.total)" :stroke-width="10" color="#409eff" />
            <span class="dist-num">{{ v }}</span>
          </div>
          <!-- 没打标签的歌在标签分布里也显式占一行「未知」，不留暗数 -->
          <div v-if="st.noTag" class="dist-row">
            <span class="dist-name warn-name">未知</span>
            <el-progress class="dist-bar" :percentage="pct(st.noTag, st.total)" :stroke-width="10" color="#e6a23c" />
            <span class="dist-num warn-num">{{ st.noTag }}</span>
          </div>
          <div v-if="st.noTag" class="notag">⚠️ 「未知」= 还没有任何标签（上传时可留空，之后在歌曲管理里编辑补上）</div>
          <el-empty v-if="!Object.keys(st.tagDist || {}).length && !st.noTag" description="暂无数据" :image-size="60" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 明细：无歌词 / 无专辑图 / 下架 -->
    <el-row :gutter="14" class="mt14">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>无歌词歌曲（{{ listNoLyric.length }}）</template>
          <div class="song-scroll">
            <div v-for="s in listNoLyric" :key="s.id" class="song-row">
              <span class="song-title link" @click="router.push(`/admin/songs?edit=${s.id}`)">{{ s.title }}</span><span class="song-artist">{{ s.artist }}</span>
            </div>
            <el-empty v-if="!listNoLyric.length" description="全部歌曲都有歌词 🎉" :image-size="60" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>无专辑图歌曲（{{ listNoCover.length }}）</template>
          <div class="song-scroll">
            <div v-for="s in listNoCover" :key="s.id" class="song-row">
              <span class="song-title link" @click="router.push(`/admin/songs?edit=${s.id}`)">{{ s.title }}</span><span class="song-artist">{{ s.artist }}</span>
            </div>
            <el-empty v-if="!listNoCover.length" description="全部歌曲都有封面 🎉" :image-size="60" />
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>下架歌曲（{{ listOffline.length }}）</template>
          <div class="song-scroll">
            <div v-for="s in listOffline" :key="s.id" class="song-row offline">
              <span class="song-title link" @click="router.push(`/admin/songs?edit=${s.id}`)">{{ s.title }}</span><span class="song-artist">{{ s.artist }}</span>
            </div>
            <el-empty v-if="!listOffline.length" description="没有下架歌曲 🎉" :image-size="60" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { songStats } from '@/api/admin'

const st = ref<Record<string, any>>({})
const loading = ref(false)
const auto = ref(true)
const updatedAt = ref('')
const router = useRouter()
let timer: number | undefined

async function load() {
  loading.value = true
  try {
    st.value = await songStats()
    updatedAt.value = new Date().toLocaleTimeString()
  } finally {
    loading.value = false
  }
}

const listNoLyric = computed(() => (st.value.details || {}).noLyric || [])
const listNoCover = computed(() => (st.value.details || {}).noCover || [])
const listOffline = computed(() => (st.value.details || {}).offline || [])

const pct = (v: number, total: number) => (total ? Math.round((v / total) * 100) : 0)
const barColor = [
  { color: '#409eff', percentage: 50 },
  { color: '#e6a23c', percentage: 80 },
  { color: '#f56c6c', percentage: 100 }
]

onMounted(() => {
  load()
  timer = window.setInterval(() => {
    if (auto.value) load()
  }, 60000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped lang="scss">
.ops-page { padding: 4px; }
.ops-head {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
  h2 { margin: 0; }
  .ops-head-right { display: flex; align-items: center; gap: 12px; .refresh-at { color: #909399; font-size: 12px; } }
}
.mt14 { margin-top: 14px; }
.big { text-align: center; padding: 4px 0;
  b { display: block; font-size: 28px; &.ok { color: #67c23a; } &.warn { color: #e6a23c; } }
  span { color: #909399; font-size: 12px; } }
.dist-row { display: flex; align-items: center; gap: 10px; padding: 5px 0;
  .dist-name { width: 70px; text-align: right; color: #606266; font-size: 13px; }
  .dist-bar { flex: 1; } .dist-num { width: 44px; text-align: right; color: #409eff; font-weight: 600; }
  .warn-name { color: #e6a23c; } .warn-num { color: #e6a23c; } }
.notag { margin-top: 10px; color: #e6a23c; font-size: 13px; }
// 明细全量展示：固定高度内滚动，不再"还有 N 首"截断
.song-scroll { max-height: 420px; overflow-y: auto; padding-right: 4px; }
.song-row { display: flex; justify-content: space-between; gap: 10px; padding: 5px 2px; border-bottom: 1px dashed #ebeef5; font-size: 13px;
  .song-title { color: #303133; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; &:hover { color: #409eff; } }
  .song-artist { color: #909399; flex-shrink: 0; }
  &.offline .song-title { color: #f56c6c; } }
</style>
