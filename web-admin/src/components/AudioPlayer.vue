<template>
  <transition name="slide-up">
    <div v-if="playerStore.currentSong" class="player">
      <!-- 歌曲信息 -->
      <div class="song-info">
        <div class="cover">🎵</div>
        <div class="meta">
          <div class="title" :title="playerStore.currentSong.title">
            {{ playerStore.currentSong.title }}
          </div>
          <div class="artist text-muted">
            {{ playerStore.currentSong.artist || '未知歌手' }}
          </div>
        </div>
      </div>

      <!-- 控制区 -->
      <div class="controls">
        <div class="buttons">
          <el-button text circle title="上一首" @click="playerStore.prev()">
            <el-icon><CaretLeft /></el-icon>
          </el-button>

          <el-button type="primary" circle @click="togglePlay">
            <el-icon :size="18">
              <VideoPlay v-if="!playerStore.isPlaying" />
              <VideoPause v-else />
            </el-icon>
          </el-button>

          <el-button text circle title="下一首" @click="playerStore.next()">
            <el-icon><CaretRight /></el-icon>
          </el-button>
        </div>

        <div class="progress">
          <span class="time">{{ formatCurrentTime(currentTime) }}</span>
          <el-slider
            v-model="currentTime"
            :max="duration || 100"
            :show-tooltip="false"
            size="small"
            @change="seek"
          />
          <span class="time">{{ formatDuration(duration) }}</span>
        </div>
      </div>

      <!-- 右侧操作 -->
      <div class="actions">
        <el-button text circle :title="modeText" @click="playerStore.toggleMode()">
          <el-icon><component :is="modeIcon" /></el-icon>
        </el-button>

        <div class="volume">
          <el-icon><Microphone /></el-icon>
          <el-slider
            v-model="volume"
            :max="1"
            :step="0.05"
            style="width: 80px"
            size="small"
          />
        </div>

        <el-button text circle title="下载当前歌曲" @click="handleDownload">
          <el-icon><Download /></el-icon>
        </el-button>

        <el-button text circle title="播放列表" @click="showList = !showList">
          <el-icon><Operation /></el-icon>
        </el-button>
      </div>

      <!-- 播放列表抽屉 -->
      <el-drawer v-model="showList" title="播放列表" size="320px" :with-header="true" direction="rtl">
        <el-empty v-if="!playerStore.playlist.length" description="暂无播放歌曲" />
        <div v-else class="playlist">
          <div
            v-for="song in playerStore.playlist"
            :key="song.id"
            class="playlist-item"
            :class="{ active: song.id === playerStore.currentSong?.id }"
          >
            <div class="item-info" @click="playerStore.play(song)">
              <span class="item-title">{{ song.title }}</span>
              <span class="item-artist text-muted">{{ song.artist || '未知' }}</span>
            </div>
            <el-button text size="small" @click="playerStore.remove(song.id)">移除</el-button>
          </div>
        </div>
      </el-drawer>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  VideoPlay,
  VideoPause,
  CaretLeft,
  CaretRight,
  Download,
  Operation,
  Microphone,
  Refresh,
  Sort
} from '@element-plus/icons-vue'
import { usePlayerStore } from '@/stores/player'
import { formatCurrentTime, formatDuration } from '@/utils/format'
import { downloadSong } from '@/api/song'
import type { SongVO } from '@/types'

const playerStore = usePlayerStore()

const currentTime = ref(0)
const duration = ref(0)
const showList = ref(false)
const volume = computed({
  get: () => playerStore.volume,
  set: v => playerStore.setVolume(v)
})

// 音频元素：使用原生 audio 以便直接控制播放与进度
const audio = new Audio()
audio.preload = 'metadata'

watch(
  () => playerStore.currentUrl,
  url => {
    if (url) {
      audio.src = url
      audio.load()
      audio.play().catch(() => {
        // 浏览器可能阻止自动播放，此时由用户手动点击
      })
    }
  }
)

watch(
  () => playerStore.isPlaying,
  playing => {
    playing ? audio.play().catch(() => {}) : audio.pause()
  }
)

watch(volume, v => {
  audio.volume = v
})

audio.addEventListener('timeupdate', () => {
  currentTime.value = audio.currentTime
})
audio.addEventListener('loadedmetadata', () => {
  duration.value = audio.duration || (playerStore.currentSong?.duration ?? 0)
})
audio.addEventListener('ended', () => {
  if (playerStore.mode === 'single') {
    audio.currentTime = 0
    audio.play().catch(() => {})
  } else {
    playerStore.next()
  }
})
audio.addEventListener('error', () => {
  ElMessage.error('音频加载失败（可能格式不受浏览器支持，可尝试下载后播放）')
})

function togglePlay() {
  playerStore.isPlaying = !playerStore.isPlaying
}

function seek(value: number | number[]) {
  // el-slider 的 change 回调类型为 Arrayable<number>，需兼容数组形式
  const target = Array.isArray(value) ? (value[0] ?? 0) : value
  audio.currentTime = target
}

async function handleDownload() {
  const song = playerStore.currentSong as SongVO | null
  if (!song) return
  try {
    await downloadSong(song.id, song.originalName || song.title)
    ElMessage.success('开始下载')
  } catch {
    // request 拦截器已统一提示
  }
}

const modeText = computed(
  () => ({ order: '顺序播放', random: '随机播放', single: '单曲循环' })[playerStore.mode]
)
const modeIcon = computed(() =>
  playerStore.mode === 'order' ? Sort : playerStore.mode === 'random' ? Operation : Refresh
)
</script>

<style scoped lang="scss">
.player {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: var(--music-player-height);
  background: #fff;
  border-top: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 20px;
  z-index: 100;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.05);

  .song-info {
    width: 220px;
    display: flex;
    align-items: center;
    gap: 10px;
    flex-shrink: 0;

    .cover {
      width: 44px;
      height: 44px;
      border-radius: 8px;
      background: linear-gradient(135deg, #6366f1, #8b5cf6);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
    }

    .meta {
      overflow: hidden;
      .title {
        font-size: 13px;
        font-weight: 600;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .artist {
        font-size: 12px;
      }
    }
  }

  .controls {
    flex: 1;
    min-width: 0;
    .buttons {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
    }
    .progress {
      display: flex;
      align-items: center;
      gap: 10px;
      .time {
        font-size: 12px;
        color: #9ca3af;
        min-width: 40px;
        text-align: center;
      }
      :deep(.el-slider) {
        flex: 1;
      }
    }
  }

  .actions {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
    .volume {
      display: flex;
      align-items: center;
      gap: 4px;
      color: #9ca3af;
    }
  }
}

.playlist {
  .playlist-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px;
    border-radius: 6px;
    cursor: pointer;

    &:hover {
      background: #f3f4f6;
    }
    &.active .item-title {
      color: #6366f1;
      font-weight: 600;
    }

    .item-info {
      display: flex;
      flex-direction: column;
      overflow: hidden;
      .item-title {
        font-size: 13px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .item-artist {
        font-size: 12px;
      }
    }
  }
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: transform 0.25s;
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(100%);
}
</style>
