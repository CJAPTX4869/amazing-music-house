<template>
  <div class="song-library">
    <el-card shadow="never">
      <!-- 工具栏 -->
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索歌名 / 歌手 / 专辑"
          clearable
          style="width: 260px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-select v-model="query.sort" style="width: 150px" @change="loadList">
          <el-option label="最新上传" value="create_time,desc" />
          <el-option label="下载最多" value="download_count,desc" />
          <el-option label="播放最多" value="play_count,desc" />
          <el-option label="歌名 A→Z" value="title,asc" />
        </el-select>

        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <el-button @click="handleReset">重置</el-button>

        <div class="spacer" />

        <el-button
          v-if="songList.length"
          type="success"
          :icon="VideoPlay"
          @click="playAll"
        >
          播放全部
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table v-loading="loading" :data="songList" style="width: 100%" empty-text="暂无歌曲">
        <el-table-column type="index" label="#" width="50" :index="(i) => (query.page - 1) * query.size + i + 1" />

        <el-table-column label="封面" width="70">
          <template #default="{ row }">
            <el-image
              v-if="row.coverUrl"
              :src="coverUrl((row as SongVO).id, (row as SongVO).updateTime)"
              fit="cover"
              class="cover-cell"
              :preview-src-list="[coverUrl((row as SongVO).id, (row as SongVO).updateTime)]"
              preview-teleported
            />
            <div v-else class="cover-cell cover-empty">
              <el-icon><Picture /></el-icon>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="歌名" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="song-title">{{ row.title }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="artist" label="歌手" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.artist || '-' }}
          </template>
        </el-table-column>

        <el-table-column prop="album" label="专辑" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.album || '-' }}
          </template>
        </el-table-column>

        <el-table-column label="时长" width="80">
          <template #default="{ row }">{{ row.durationText }}</template>
        </el-table-column>

        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ row.fileSizeText }}</template>
        </el-table-column>

        <el-table-column label="下载" width="80" align="center">
          <template #default="{ row }">{{ row.downloadCount }}</template>
        </el-table-column>

        <el-table-column label="播放" width="80" align="center">
          <template #default="{ row }">{{ row.playCount }}</template>
        </el-table-column>

        <el-table-column label="上传者" width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.uploaderName || '-' }}</template>
        </el-table-column>

        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handlePlay(row as SongVO)">播放</el-button>
            <el-button link type="primary" @click="handleDownload(row as SongVO)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadList"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, VideoPlay, Picture } from '@element-plus/icons-vue'
import { getSongList, downloadSong, coverUrl } from '@/api/song'
import type { SongVO } from '@/types'
import { usePlayerStore } from '@/stores/player'

const playerStore = usePlayerStore()

const loading = ref(false)
const songList = ref<SongVO[]>([])
const total = ref(0)

const query = reactive({
  keyword: '',
  sort: 'create_time,desc',
  page: 1,
  size: 10
})

// 请求序号：快速搜索/切页时丢弃过期响应，防止旧结果覆盖新结果
let reqSeq = 0
// 下载后延迟刷新的定时器句柄，卸载时清理
let refreshTimer: number | undefined

async function loadList() {
  const seq = ++reqSeq
  loading.value = true
  try {
    const res = await getSongList(query)
    if (seq !== reqSeq) return
    songList.value = res.records
    total.value = res.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.page = 1
  loadList()
}

/** 每页条数变化：回到第一页再加载，避免停留在超出范围的页码 */
function handleSizeChange() {
  query.page = 1
  loadList()
}

function handleReset() {
  query.keyword = ''
  query.sort = 'create_time,desc'
  query.page = 1
  loadList()
}

function handlePlay(song: SongVO) {
  playerStore.play(song)
}

/** 播放全部：把当前列表灌入播放列表并从第一首开始 */
function playAll() {
  if (!songList.value.length) return
  playerStore.playList(songList.value, 0)
  ElMessage.success(`已加入 ${songList.value.length} 首到播放列表`)
}

async function handleDownload(song: SongVO) {
  try {
    await downloadSong(song.id, song.originalName || song.title)
    ElMessage.success('已开始下载')
    refreshTimer = window.setTimeout(loadList, 800)
  } catch {
    // 拦截器已提示
  }
}

onMounted(loadList)

onBeforeUnmount(() => {
  if (refreshTimer !== undefined) window.clearTimeout(refreshTimer)
})
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;

  .spacer {
    flex: 1;
  }
}

.song-title {
  font-weight: 500;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 封面缩略图（正方形） */
.cover-cell {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  overflow: hidden;
  display: block;
}

.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
  color: #9ca3af;
}
</style>
