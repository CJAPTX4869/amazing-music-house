<script setup lang="ts">
import { NAvatar, NButton, NEmpty, NModal, NTime } from 'naive-ui';
import { ref } from 'vue';
import service from '@/service/request';
// 修复：原先漏了 getPendingRequests 的导入，导致打开弹窗时 ReferenceError（页面顶部报"页面出现异常"）
import { acceptFollow, getPendingRequests, removeFollow } from '@/service';

/**
 * 好友请求弹窗（页头入口）：列出待处理请求，支持同意/拒绝
 */
const show = ref(false);
const loading = ref(false);
const list = ref<{
  id: number;
  user: { id: number; nickname: string; avatarUrl: string };
  createTime: string;
}[]>([]);

// 头像为相对 API 路径，拼服务地址
const base = service.defaults.baseURL || '';
const full = (path?: string | null) => (path ? (path.startsWith('http') ? path : base + path) : '');

const emit = defineEmits<{ (e: 'changed'): void }>();

async function open() {
  show.value = true;
  loading.value = true;
  try {
    const res = await getPendingRequests();
    if (res.data.code === 200) {
      list.value = res.data.data;
    }
  } finally {
    loading.value = false;
  }
}

async function handle(row: { id: number; user: { id: number } }, accept: boolean) {
  const res = accept ? await acceptFollow(row.user.id) : await removeFollow(row.user.id);
  if (res.data.code === 200) {
    list.value = list.value.filter(r => r.id !== row.id);
    emit('changed');
    window.$message.success(accept ? '已同意，你们已成为好友' : '已拒绝该请求');
  } else {
    window.$message.error(res.data.message || '操作失败');
  }
}

defineExpose({ open });
</script>

<template>
  <n-modal v-model:show="show" preset="card" title="好友请求" style="width: 420px">
    <n-empty v-if="!list.length" description="暂无好友请求" class="py-6" />
    <div v-for="r in list" :key="r.id" class="flex items-center gap-3 py-3">
      <n-avatar round :size="40" :src="full(r.user.avatarUrl) || undefined" />
      <div class="flex-1 min-w-0">
        <div class="text-sm font-bold truncate">{{ r.user.nickname }}</div>
        <n-time :time="Date.parse(r.createTime)" type="relative" class="text-xs opacity-60" />
      </div>
      <n-button size="tiny" type="primary" round @click="handle(r, true)">同意</n-button>
      <n-button size="tiny" round @click="handle(r, false)">拒绝</n-button>
    </div>
  </n-modal>
</template>
