<template>
  <div class="song-manage">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">🎼 歌曲管理（含下架）</span>
          <div class="spacer" />
          <el-button
            type="danger"
            :disabled="!selectedIds.length"
            @click="handleBatchDelete"
          >
            批量删除（{{ selectedIds.length }}）
          </el-button>
        </div>
      </template>

      <!-- 筛选 -->
      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索歌名 / 歌手"
          clearable
          style="width: 220px"
          @keyup.enter="loadList"
          @clear="loadList"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="loadList">
          <el-option label="已上架" :value="1" />
          <el-option label="已下架" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadList">查询</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        style="width: 100%"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" />
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
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="歌名" min-width="150" show-overflow-tooltip />
        <el-table-column prop="artist" label="歌手" width="110" show-overflow-tooltip />
        <el-table-column prop="album" label="专辑" width="120" show-overflow-tooltip />
        <el-table-column label="时长" width="76">
          <template #default="{ row }">{{ row.durationText }}</template>
        </el-table-column>
        <el-table-column label="大小" width="96">
          <template #default="{ row }">{{ row.fileSizeText }}</template>
        </el-table-column>
        <el-table-column label="下载/播放" width="96" align="center">
          <template #default="{ row }">{{ row.downloadCount }} / {{ row.playCount }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row as SongVO)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(row as SongVO)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button link type="danger" @click="handleDelete(row as SongVO)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑歌曲信息" width="520px">
      <el-form :model="editForm" label-width="70px">
        <!-- 封面：选择即压缩上传，无需额外保存 -->
        <el-form-item label="封面">
          <div class="cover-editor">
            <img v-if="coverPreview" :src="coverPreview" class="cover-preview" alt="封面预览" />
            <div v-else class="cover-preview cover-empty">
              <el-icon :size="26"><Picture /></el-icon>
              <span class="tip">暂无封面</span>
            </div>

            <div class="cover-actions">
              <el-button size="small" :loading="coverUploading" @click="triggerCoverSelect">
                {{ coverPreview ? '更换图片' : '导入图片' }}
              </el-button>
              <el-button
                v-if="coverPreview"
                size="small"
                link
                type="danger"
                @click="resetCoverPreview"
              >
                撤销
              </el-button>
              <p class="tip">
                支持 jpg / png / webp / gif，选择后自动压缩并居中裁剪为正方形
              </p>
            </div>

            <input
              ref="coverInputRef"
              type="file"
              accept="image/*"
              hidden
              @change="handleCoverSelect"
            />
          </div>
        </el-form-item>

        <el-form-item label="歌名">
          <el-input v-model="editForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="歌手">
          <el-input v-model="editForm.artist" maxlength="100" />
        </el-form-item>
        <el-form-item label="专辑">
          <el-input v-model="editForm.album" maxlength="100" />
        </el-form-item>
        <el-form-item label="流派">
          <el-input v-model="editForm.genre" maxlength="50" />
        </el-form-item>

        <!-- 歌词：选择 .lrc / .txt 后立即上传（与封面"选择即上传"一致） -->
        <el-form-item label="歌词">
          <div class="lyric-editor">
            <div class="lyric-row">
              <el-tag v-if="lyricUploading" type="info" size="small">上传中…</el-tag>
              <el-tag v-else-if="editingHasLyric" type="success" size="small">已有歌词</el-tag>
              <el-tag v-else type="warning" size="small">暂无歌词</el-tag>
              <el-button size="small" :loading="lyricUploading" @click="triggerLyricSelect">
                上传歌词文件
              </el-button>
            </div>
            <p class="tip">
              支持 .lrc / .txt（≤1MB，UTF-8）；网易云下载的逐字歌词会自动转成标准 LRC，选择后立即生效
            </p>
          </div>
          <input
            ref="lyricInputRef"
            type="file"
            accept=".lrc,.txt"
            hidden
            @change="handleLyricSelect"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { updateSong, deleteSong, batchDeleteSongs, uploadCover, uploadLyric, coverUrl } from '@/api/song'
import { getAdminSongList, updateSongStatus } from '@/api/admin'
import { compressToSquare, validateImageFile } from '@/utils/image'
import type { SongUpdateDTO, SongVO } from '@/types'

const loading = ref(false)
const saving = ref(false)
const list = ref<SongVO[]>([])
const total = ref(0)
const selectedIds = ref<number[]>([])

const query = reactive({
  keyword: '',
  status: undefined as 0 | 1 | undefined,
  page: 1,
  size: 10
})

const editVisible = ref(false)
const editId = ref<number | null>(null)
const editForm = reactive<SongUpdateDTO>({ title: '', artist: '', album: '', genre: '' })

/* ---------------- 封面 ---------------- */
const coverInputRef = ref<HTMLInputElement>()
const coverUploading = ref(false)
/** 已展示中的封面地址（来自服务端或刚上传后的本地预览） */
const coverPreview = ref('')
/** 记录打开弹窗时是否有封面，用于「撤销」判断与按钮文案 */
const coverLoadedAtOpen = ref('')

/** 打开文件选择框 */
function triggerCoverSelect() {
  coverInputRef.value?.click()
}

/** 选择图片 → 压缩裁剪 → 上传 → 刷新预览 */
async function handleCoverSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !editId.value) return

  const invalid = validateImageFile(file)
  if (invalid) {
    ElMessage.error(invalid)
    return
  }

  coverUploading.value = true
  try {
    // 1. 浏览器端压缩 + 居中裁剪为正方形
    const { file: compressed, originWidth, originHeight, outputSize } =
      await compressToSquare(file)

    // 2. 上传（后端仅做格式/体积校验，不重复处理图片）
    await uploadCover(editId.value, compressed)

    // 3. 用本地对象地址即时预览，避免受浏览器缓存影响
    if (coverPreview.value.startsWith('blob:')) {
      URL.revokeObjectURL(coverPreview.value)
    }
    coverPreview.value = URL.createObjectURL(compressed)

    ElMessage.success(
      `封面已更新（${originWidth}×${originHeight} → ${outputSize}×${outputSize}，` +
        `${(compressed.size / 1024).toFixed(0)}KB）`
    )
    loadList()
  } catch (err) {
    ElMessage.error((err as Error).message || '封面上传失败')
  } finally {
    coverUploading.value = false
  }
}

/** 撤销本地预览，回到打开弹窗时的状态 */
function resetCoverPreview() {
  if (coverPreview.value.startsWith('blob:')) {
    URL.revokeObjectURL(coverPreview.value)
  }
  coverPreview.value = coverLoadedAtOpen.value
}

/* ---------------- 歌词 ---------------- */
const lyricInputRef = ref<HTMLInputElement>()
const lyricUploading = ref(false)
/** 当前编辑歌曲是否已有歌词（上传成功后即时更新） */
const editingHasLyric = ref(false)

function triggerLyricSelect() {
  lyricInputRef.value?.click()
}

/** 选择歌词文件 → 校验 → 立即上传（与封面"选择即上传"一致） */
async function handleLyricSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !editId.value) return

  if (!/\.(lrc|txt)$/i.test(file.name)) {
    ElMessage.error('歌词文件仅支持 .lrc / .txt 格式')
    return
  }
  if (file.size > 1024 * 1024) {
    ElMessage.error('歌词文件不能超过 1MB')
    return
  }

  lyricUploading.value = true
  try {
    await uploadLyric(editId.value, file)
    editingHasLyric.value = true
    ElMessage.success('歌词已上传并生效')
    loadList()
  } catch {
    // 拦截器已提示
  } finally {
    lyricUploading.value = false
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await getAdminSongList({
      keyword: query.keyword || undefined,
      status: query.status,
      page: query.page,
      size: query.size
    })
    list.value = res.records
    total.value = res.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function handleSelectionChange(rows: SongVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

function openEdit(row: SongVO) {
  editId.value = row.id
  editForm.title = row.title
  editForm.artist = row.artist || ''
  editForm.album = row.album || ''
  editForm.genre = row.genre || ''
  editingHasLyric.value = !!row.hasLyric
  // 带 updateTime 作版本号，替换封面后不会被浏览器缓存挡住
  const url = row.coverUrl ? coverUrl(row.id, row.updateTime) : ''
  coverLoadedAtOpen.value = url
  coverPreview.value = url
  editVisible.value = true
}

// 关闭弹窗时释放本地预览地址，避免内存泄漏
watch(editVisible, visible => {
  if (!visible && coverPreview.value.startsWith('blob:')) {
    URL.revokeObjectURL(coverPreview.value)
    coverPreview.value = ''
  }
})

async function submitEdit() {
  if (!editId.value) return
  saving.value = true
  try {
    await updateSong(editId.value, { ...editForm })
    ElMessage.success('已保存')
    editVisible.value = false
    loadList()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: SongVO) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确定${next === 1 ? '上架' : '下架'}「${row.title}」吗？${next === 0 ? '下架后游客将无法看到与下载。' : ''}`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateSongStatus(row.id, { status: next as 0 | 1 })
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    loadList()
  } catch {
    // 拦截器已提示
  }
}

async function handleDelete(row: SongVO) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.title}」吗？数据库记录与磁盘文件都会被清理，且不可恢复。`,
      '危险操作',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteSong(row.id)
    ElMessage.success('已删除')
    loadList()
  } catch {
    // 拦截器已提示
  }
}

async function handleBatchDelete() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedIds.value.length} 首歌曲吗？此操作不可恢复。`,
      '危险操作',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await batchDeleteSongs(selectedIds.value)
    ElMessage.success('已批量删除')
    selectedIds.value = []
    loadList()
  } catch {
    // 拦截器已提示
  }
}

onMounted(loadList)
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  .spacer {
    flex: 1;
  }
  .card-title {
    font-weight: 600;
  }
}

.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* 列表内封面缩略图（正方形） */
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

/* 编辑弹窗内的封面编辑区 */
.cover-editor {
  display: flex;
  align-items: center;
  gap: 14px;

  .cover-preview {
    width: 96px;
    height: 96px;
    border-radius: 8px;
    object-fit: cover;
    background: #f3f4f6;
    flex: none;
  }

  .cover-empty {
    flex-direction: column;
    gap: 4px;
    border: 1px dashed #d1d5db;
  }

  .cover-actions {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;

    .tip {
      margin: 0;
      font-size: 12px;
      color: #9ca3af;
      line-height: 1.5;
    }
  }
}

/* 编辑弹窗内的歌词编辑区 */
.lyric-editor {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  width: 100%;

  .lyric-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .tip {
    margin: 0;
    font-size: 12px;
    color: #9ca3af;
    line-height: 1.5;
  }
}
</style>
