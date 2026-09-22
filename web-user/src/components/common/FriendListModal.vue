<template>
  <n-modal v-model:show="show" preset="card" title="我的好友" style="width: 460px">
    <n-spin :show="loading">
      <n-empty v-if="!list.length" description="还没有好友，去用户主页点「+ 关注」发起申请吧" class="py-6" />
      <div v-else class="friend-wrap">
        <div v-for="item in list" :key="item.id" class="friend-row">
          <n-avatar round :size="40" :src="full(item.avatarUrl) || undefined" />
          <div class="friend-meta" @click="goHome(item.id)">
            <div class="friend-name">{{ item.nickname || item.username || '用户' }}</div>
            <div class="friend-sign">{{ item.signature || '这个人很懒，什么都没写' }}</div>
          </div>
          <n-button text size="small" @click="onRemove(item)">解除好友</n-button>
        </div>
      </div>
    </n-spin>
  </n-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { NAvatar, NButton, NEmpty, NModal, NSpin } from 'naive-ui';
import { getFriends, removeFollow } from '@/service';
import { getApiBase } from '@/config';

const show = ref(false);
const loading = ref(false);
const list = ref<any[]>([]);
const router = useRouter();

/** 后端返回根相对路径（如 /users/11/avatar），这里补成绝对地址 */
const full = (url?: string | null) => {
  if (!url) return '';
  if (/^https?:\/\//i.test(url)) return url;
  const base = getApiBase().replace(/\/api\/?$/i, '');
  return /^\/api\//i.test(url) ? base + url : base + '/api' + url;
};

async function load() {
  loading.value = true;
  try {
    const res: any = await getFriends();
    if ((res?.data?.code ?? res?.code) === 200) {
      list.value = res.data.data || [];
    } else {
      window.$message?.error(res?.data?.message || '加载好友列表失败');
    }
  } catch (e) {
    window.$message?.error('加载好友列表失败');
  } finally {
    loading.value = false;
  }
}

function open() {
  show.value = true;
  load();
}

const goHome = (id: number | string) => {
  show.value = false;
  router.push(`/user/${id}`);
};

const onRemove = async (item: any) => {
  try {
    const res: any = await removeFollow(item.id);
    if ((res?.data?.code ?? res?.code) === 200) {
      list.value = list.value.filter((f) => f.id !== item.id);
      window.$message?.success('已解除好友');
    } else {
      window.$message?.error(res?.data?.message || '操作失败');
    }
  } catch (e) {
    window.$message?.error('操作失败');
  }
};

defineExpose({ open });
</script>

<style scoped>
.friend-wrap { max-height: 380px; overflow-y: auto; }
.friend-row { display: flex; align-items: center; gap: 10px; padding: 8px 4px; border-radius: 8px; }
.friend-row:hover { background: rgba(128, 128, 128, 0.1); }
.friend-meta { flex: 1; min-width: 0; cursor: pointer; }
.friend-name { font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.friend-sign { font-size: 12px; opacity: 0.55; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>