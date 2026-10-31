<template>
  <div class="ops-page">
    <div class="ops-head">
      <h2>最火歌曲</h2>
      <div class="ops-head-right">
        <span class="refresh-at">更新于 {{ updatedAt }}</span>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-alert type="info" :closable="false" style="margin-bottom:14px"
      title="排序规则：播放量相同比评论数 → 评论数也相同比播放量 → 完全相同按先创建的排前。仅统计上架歌曲。" />

    <el-card shadow="never" class="mt14">
      <div class="hot-toolbar">
        <el-input v-model="search" placeholder="搜索歌名定位歌曲" size="small" clearable style="width: 220px" />
        <span class="muted">点击歌名查看该歌曲的数据曲线</span>
      </div>
      <el-tabs type="border-card">
        <el-tab-pane label="🔥 播放量 TOP50">
          <el-table :data="pagedByPlays" size="small" max-height="520"
                    highlight-current-row style="cursor: pointer"
                    @row-click="row => openSongData(row)">
            <el-table-column type="index" label="#" width="52" :index="(i: number) => i + 1" />
            <el-table-column prop="name" label="歌名" min-width="160" show-overflow-tooltip />
            <el-table-column label="歌手" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.artistName || (row.ar || []).map((a: any) => a.name).join(' / ') }}</template>
            </el-table-column>
            <el-table-column prop="playCount" label="播放量" width="90" align="right" sortable />
            <el-table-column prop="commentCount" label="评论数" width="90" align="right" sortable />
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="💬 评论数 TOP50">
          <el-table :data="pagedByComments" size="small" max-height="520"
                    highlight-current-row style="cursor: pointer"
                    @row-click="row => openSongData(row)">
            <el-table-column type="index" label="#" width="52" :index="(i: number) => i + 1" />
            <el-table-column prop="name" label="歌名" min-width="160" show-overflow-tooltip />
            <el-table-column label="歌手" min-width="110" show-overflow-tooltip>
              <template #default="{ row }">{{ row.artistName || (row.ar || []).map((a: any) => a.name).join(' / ') }}</template>
            </el-table-column>
            <el-table-column prop="commentCount" label="评论数" width="90" align="right" sortable />
            <el-table-column prop="playCount" label="播放量" width="90" align="right" sortable />
          </el-table>
        </el-tab-pane>
      </el-tabs>
      <el-pagination class="hot-pager" layout="prev, pager, next, total" :page-size="pageSize"
                     :total="filteredCount" :current-page="page" @current-change="page = $event" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { hotSongs } from '@/api/admin'

const hot = ref<{ byPlays: any[]; byComments: any[] }>({ byPlays: [], byComments: [] })
const loading = ref(false)
const updatedAt = ref('')
const search = ref('')
const page = ref(1)
const pageSize = 20
const router = useRouter()
let timer: number | undefined

import { useRouter } from 'vue-router'

const filteredByPlays = computed(() => {
  const kw = search.value.trim().toLowerCase()
  const list = hot.value.byPlays.filter(x => !kw || (x.name || '').toLowerCase().includes(kw))
  return list
})
const filteredByComments = computed(() => {
  const kw = search.value.trim().toLowerCase()
  const list = hot.value.byComments.filter(x => !kw || (x.name || '').toLowerCase().includes(kw))
  return list
})
const filteredCount = computed(() => filteredByPlays.value.length)
const pagedByPlays = computed(() => filteredByPlays.value.slice((page.value - 1) * pageSize, page.value * pageSize))
const pagedByComments = computed(() => filteredByComments.value.slice((page.value - 1) * pageSize, page.value * pageSize))

function openSongData(row: any) {
  router.push(`/admin/song-data/${row.id}`)
}

async function load() {
  loading.value = true
  try {
    hot.value = await hotSongs(50)
    updatedAt.value = new Date().toLocaleTimeString()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  timer = window.setInterval(() => load(), 60000)
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
.hot-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 10px;
  .muted { color: #909399; font-size: 12px; } }
.song-link { cursor: pointer; color: #409eff; &:hover { text-decoration: underline; } }
.hot-pager { margin-top: 10px; justify-content: flex-end; }
</style>
