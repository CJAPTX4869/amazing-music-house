<template>
  <div class="user-manage">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="card-title">👥 用户管理</span>
          <div class="spacer" />
          <el-tag type="info" size="small">共 {{ total }} 个用户</el-tag>
        </div>
      </template>

      <div class="toolbar">
        <el-input
          v-model="query.keyword"
          placeholder="搜索用户名 / 昵称"
          clearable
          style="width: 240px"
          @keyup.enter="loadList"
          @clear="loadList"
        />
        <el-select v-model="query.role" placeholder="角色" clearable style="width: 130px" @change="loadList">
          <el-option label="管理员" :value="1" />
          <el-option label="普通用户" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadList">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="头像" width="80" align="center">
          <template #default="{ row }">
            <el-avatar :size="36" :src="avatarSrc(row as UserVO)">
              {{ (row.nickname || row.username || '?').slice(0, 1).toUpperCase() }}
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column label="昵称" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" width="100">
          <template #default="{ row }">
            <el-tag :type="row.role === 1 ? 'warning' : 'info'" size="small">
              {{ row.role === 1 ? '管理员' : '普通用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }">{{ row.createTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :loading="avatarUploadingId === row.id" @click="triggerAvatarUpload(row as UserVO)">
              设置头像
            </el-button>
            <el-button
              link
              :type="row.status === 1 ? 'danger' : 'success'"
              :disabled="isSelf(row as UserVO)"
              @click="toggleStatus(row as UserVO)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="query.page"
          :total="total"
          :page-size="query.size"
          layout="total, prev, pager, next"
          @current-change="loadList"
        />
      </div>

      <!-- 头像文件选择（隐藏） -->
      <input
        ref="avatarInputRef"
        class="avatar-input"
        type="file"
        accept="image/*"
        @change="handleAvatarSelect"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getUserList, updateUserStatus, uploadUserAvatar } from '@/api/admin'
import { compressToSquare, validateImageFile } from '@/utils/image'
import type { UserVO } from '@/types'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const loading = ref(false)
const list = ref<UserVO[]>([])
const total = ref(0)

/** 头像版本号：列表加载 / 换图后刷新，用于绕过浏览器图片缓存 */
const avatarVersion = ref(Date.now())
/** 待上传头像的用户 ID */
const avatarTargetId = ref<number | null>(null)
/** 正在上传头像的用户 ID */
const avatarUploadingId = ref<number | null>(null)
const avatarInputRef = ref<HTMLInputElement>()

const query = reactive({
  keyword: '',
  role: undefined as 0 | 1 | undefined,
  page: 1,
  size: 10
})

const isSelf = (row: UserVO) => row.id === userStore.userInfo?.id

/** 头像地址（无头像返回空串，由 el-avatar 显示昵称首字母） */
function avatarSrc(row: UserVO) {
  if (!row.avatar) return ''
  // 走 Vite 代理 → 后端 /api/users/{id}/avatar
  return `/api/users/${row.id}/avatar?v=${avatarVersion.value}`
}

async function loadList() {
  loading.value = true
  try {
    const res = await getUserList({
      keyword: query.keyword || undefined,
      role: query.role,
      page: query.page,
      size: query.size
    })
    list.value = res.records
    total.value = res.total
    avatarVersion.value = Date.now()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function triggerAvatarUpload(row: UserVO) {
  avatarTargetId.value = row.id
  avatarInputRef.value?.click()
}

async function handleAvatarSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  // 及时清空，保证同一文件再次选择也能触发
  input.value = ''
  const id = avatarTargetId.value
  if (!file || !id) return

  const invalid = validateImageFile(file)
  if (invalid) {
    ElMessage.error(invalid)
    return
  }

  avatarUploadingId.value = id
  try {
    // 浏览器端裁剪压缩为正方形后再上传
    const { file: compressed } = await compressToSquare(file, 512, 0.9)
    await uploadUserAvatar(id, compressed)
    avatarVersion.value = Date.now()
    ElMessage.success('头像已更新')
    await loadList()
  } catch {
    // 拦截器已提示
  } finally {
    avatarUploadingId.value = null
    avatarTargetId.value = null
  }
}

async function toggleStatus(row: UserVO) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确定${next === 0 ? '禁用' : '启用'}用户「${row.username}」吗？${next === 0 ? '禁用后该用户将无法登录。' : ''}`,
      '提示',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await updateUserStatus(row.id, { status: next as 0 | 1 })
    ElMessage.success(next === 0 ? '已禁用' : '已启用')
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

.avatar-input {
  display: none;
}
</style>
