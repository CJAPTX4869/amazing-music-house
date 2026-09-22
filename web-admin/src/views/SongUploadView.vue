<template>
  <div class="upload-view">
    <el-card shadow="never">
      <template #header>
        <div class="card-title">⬆️ 上传歌曲（管理员）</div>
      </template>

      <!-- 拖拽区 -->
      <div
        class="dropzone"
        :class="{ over: isOver }"
        @click="triggerSelect"
        @dragover.prevent="isOver = true"
        @dragleave="isOver = false"
        @drop.prevent="handleDrop"
      >
        <el-icon :size="44" color="#6366f1"><UploadFilled /></el-icon>
        <div class="main">点击选择 或 拖拽音频文件到此处</div>
        <div class="sub text-muted">
          支持 mp3 / wav / flac / aac / m4a / ogg / wma，单个文件 ≤ 100MB
        </div>
        <div class="sub text-muted">元数据可留空，后端会自动从文件标签读取</div>
      </div>

      <input
        ref="fileInputRef"
        type="file"
        accept=".mp3,.wav,.flac,.aac,.m4a,.ogg,.wma"
        multiple
        hidden
        @change="handleSelect"
      />

      <!-- 待上传队列 -->
      <div v-if="queue.length" class="queue">
        <div v-for="item in queue" :key="item.uid" class="queue-item">
          <div class="item-head">
            <span class="name" :title="item.file.name">{{ item.file.name }}</span>
            <span class="size text-muted">{{ formatFileSize(item.file.size) }}</span>
            <el-tag v-if="item.status === 'success'" type="success" size="small">完成</el-tag>
            <el-tag v-else-if="item.status === 'error'" type="danger" size="small">失败</el-tag>
            <el-tag v-else-if="item.status === 'uploading'" type="primary" size="small">
              {{ item.percent }}%
            </el-tag>
            <el-tag v-else size="small" type="info">等待中</el-tag>
          </div>

          <el-progress
            v-if="item.status === 'uploading' || item.status === 'success'"
            :percentage="item.percent"
            :status="item.status === 'success' ? 'success' : undefined"
            :stroke-width="6"
          />
          <div v-if="item.error" class="error text-muted">{{ item.error }}</div>
        </div>

        <div class="queue-actions">
          <el-button @click="clearFinished">清除已完成</el-button>
        </div>
      </div>
    </el-card>

    <!-- 最近上传 -->
    <el-card v-if="uploaded.length" shadow="never" style="margin-top: 16px">
      <template #header><div class="card-title">✅ 本次上传成功</div></template>
      <el-table :data="uploaded" size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="封面" width="70">
          <template #default="{ row }">
            <img
              v-if="coverBust[(row as SongVO).id]"
              :src="coverUrl((row as SongVO).id, coverBust[(row as SongVO).id])"
              class="cover-cell"
              alt="封面"
            />
            <div v-else class="cover-cell cover-empty">
              <el-icon><Picture /></el-icon>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="歌名" min-width="140" show-overflow-tooltip />
        <el-table-column prop="artist" label="歌手" width="120" />
        <el-table-column label="时长" width="80">
          <template #default="{ row }">{{ row.durationText }}</template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ row.fileSizeText }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :loading="coverUploadingId === (row as SongVO).id"
              @click="chooseCover(row as SongVO)"
            >
              {{ coverBust[(row as SongVO).id] ? '换封面' : '导入图片' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <p class="cover-tip">
        提示：点击「导入图片」选择封面，会自动压缩并居中裁剪为正方形
      </p>
    </el-card>

    <!-- 封面选择（共用同一个隐藏 input） -->
    <input
      ref="coverInputRef"
      type="file"
      accept="image/*"
      hidden
      @change="handleCoverSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Picture } from '@element-plus/icons-vue'
import { uploadSong, uploadCover, coverUrl } from '@/api/song'
import { compressToSquare, validateImageFile } from '@/utils/image'
import { formatFileSize } from '@/utils/format'
import type { SongVO } from '@/types'

interface QueueItem {
  uid: string
  file: File
  percent: number
  status: 'pending' | 'uploading' | 'success' | 'error'
  error?: string
}

const ALLOWED_EXT = ['mp3', 'wav', 'flac', 'aac', 'm4a', 'ogg', 'wma']
const MAX_SIZE = 100 * 1024 * 1024

const fileInputRef = ref<HTMLInputElement>()
const isOver = ref(false)
const queue = ref<QueueItem[]>([])
const uploaded = ref<SongVO[]>([])

function triggerSelect() {
  fileInputRef.value?.click()
}

function handleSelect(e: Event) {
  const input = e.target as HTMLInputElement
  if (input.files?.length) addFiles(Array.from(input.files))
  input.value = ''
}

function handleDrop(e: DragEvent) {
  isOver.value = false
  if (e.dataTransfer?.files?.length) addFiles(Array.from(e.dataTransfer.files))
}

/** 加入队列并做前端预校验 */
function addFiles(files: File[]) {
  for (const file of files) {
    const ext = (file.name.split('.').pop() || '').toLowerCase()
    if (!ALLOWED_EXT.includes(ext)) {
      ElMessage.error(`「${file.name}」格式不支持，已跳过`)
      continue
    }
    if (file.size > MAX_SIZE) {
      ElMessage.error(`「${file.name}」超过 100MB，已跳过`)
      continue
    }
    queue.value.push({
      uid: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
      file,
      percent: 0,
      status: 'pending'
    })
  }
  processQueue()
}

/**
 * 串行上传
 * 原因：后端 max-request-size 限制下单次提交多文件会被拒；
 *      串行还能逐个精确反馈进度与结果。
 */
let uploading = false
async function processQueue() {
  if (uploading) return
  uploading = true
  try {
    for (const item of queue.value) {
      if (item.status !== 'pending') continue
      item.status = 'uploading'
      try {
        const song = await uploadSong(
          item.file,
          {},
          percent => {
            item.percent = percent
          }
        )
        item.status = 'success'
        item.percent = 100
        uploaded.value.unshift(song)
        ElMessage.success(`上传成功：${song.title}${song.duration ? `（${song.durationText}）` : ''}`)
      } catch (e) {
        item.status = 'error'
        item.error = (e as Error).message || '上传失败'
      }
    }
  } finally {
    uploading = false
  }
}

function clearFinished() {
  queue.value = queue.value.filter(i => i.status !== 'success')
}

/* ---------------- 封面上传 ---------------- */
const coverInputRef = ref<HTMLInputElement>()
/** 待设置封面的歌曲 */
const pendingCoverSong = ref<SongVO | null>(null)
/** 正在上传封面的歌曲 ID */
const coverUploadingId = ref<number | null>(null)
/** 已设置封面的歌曲 ID → 版本戳（用于刷新浏览器缓存） */
const coverBust = ref<Record<number, number>>({})

function chooseCover(song: SongVO) {
  pendingCoverSong.value = song
  coverInputRef.value?.click()
}

/** 选择图片 → 压缩裁剪为正方形 → 上传 */
async function handleCoverSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  const song = pendingCoverSong.value
  if (!file || !song) return

  const invalid = validateImageFile(file)
  if (invalid) {
    ElMessage.error(invalid)
    return
  }

  coverUploadingId.value = song.id
  try {
    const { file: compressed, originWidth, originHeight, outputSize } =
      await compressToSquare(file)
    await uploadCover(song.id, compressed)
    coverBust.value = { ...coverBust.value, [song.id]: Date.now() }
    ElMessage.success(
      `「${song.title}」封面已更新（${originWidth}×${originHeight} → ${outputSize}×${outputSize}，` +
        `${(compressed.size / 1024).toFixed(0)}KB）`
    )
  } catch (err) {
    ElMessage.error((err as Error).message || '封面上传失败')
  } finally {
    coverUploadingId.value = null
  }
}
</script>

<style scoped lang="scss">
.card-title {
  font-weight: 600;
}

.dropzone {
  border: 2px dashed #d1d5db;
  border-radius: 12px;
  padding: 36px 20px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafafa;

  &:hover,
  &.over {
    border-color: #6366f1;
    background: #eef2ff;
  }

  .main {
    margin-top: 10px;
    font-size: 15px;
    font-weight: 500;
  }
  .sub {
    margin-top: 4px;
    font-size: 12px;
  }
}

.queue {
  margin-top: 18px;

  .queue-item {
    padding: 10px 0;
    border-bottom: 1px solid #f3f4f6;

    .item-head {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;

      .name {
        flex: 1;
        font-size: 13px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .size {
        font-size: 12px;
      }
    }

    .error {
      font-size: 12px;
      color: #ef4444;
      margin-top: 4px;
    }
  }

  .queue-actions {
    margin-top: 12px;
    text-align: right;
  }
}

/* 封面缩略图（正方形） */
.cover-cell {
  width: 44px;
  height: 44px;
  border-radius: 6px;
  overflow: hidden;
  display: block;
  object-fit: cover;
}

.cover-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
  color: #9ca3af;
}

.cover-tip {
  margin: 10px 0 0;
  font-size: 12px;
  color: #9ca3af;
}
</style>
