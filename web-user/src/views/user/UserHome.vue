<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { NAvatar, NButton, NEmpty, NIcon, NSpin, NTabPane, NTabs } from 'naive-ui';
import { CreateOutline } from '@vicons/ionicons5';
import { useMainStore } from '@/stores/main';
import service from '@/service/request';
import { getFollowStatus, getUserPlaylists, getUserPublicProfile, removeFollow, requestFollow } from '@/service';
import SongList from '@/components/SongsList/SongList.vue';

/**
 * 用户个人主页（/user/:id，公开访问）
 * <p>
 * 官方风格：资料卡（头像/昵称/关注·粉丝/签名/地区）+「创建的歌单/收藏的歌单」网格；
 * 本人访问时显示"编辑资料"；他人的"关注"按钮在阶段 D（好友系统）激活。
 */

type PlaylistCard = { id: number | string; name: string; picUrl: string; playCount: number };

const route = useRoute();
const router = useRouter();
const userId = route.params.id as string;
const mainStore = useMainStore();

// 图片地址统一拼服务前缀
const base = service.defaults.baseURL || '';
const full = (path?: string | null) => (path ? (path.startsWith('http') ? path : base + path) : '');

const profile = ref<{
  id: number;
  nickname: string;
  signature: string;
  region: string;
  followCount: number;
  fanCount: number;
} | null>(null);

const createdCards = ref<PlaylistCard[]>([]);
const collectedCards = ref<PlaylistCard[]>([]);
const loading = ref(false);
const activeTab = ref('created');

/* ---------- 好友关注（V3.1 阶段 D） ---------- */
const followStatus = ref<'none' | 'pending_by_me' | 'pending_to_me' | 'friends'>('none');
const followLoading = ref(false);

async function loadFollowStatus() {
  if (!mainStore.isLogin || isSelf.value) return;
  try {
    const res = await getFollowStatus(userId);
    if (res.data.code === 200) followStatus.value = res.data.data;
  } catch {
    // 忽略：匿名或网络异常按 none 处理
  }
}

const followBtnText = computed(() => ({
  friends: '已是好友',
  pending_by_me: '等待对方同意',
  pending_to_me: '同意请求',
  none: '+ 关注'
}[followStatus.value]));

async function onFollow() {
  if (!mainStore.isLogin) {
    window.$message.warning('请先登录');
    return;
  }
  followLoading.value = true;
  try {
    if (followStatus.value === 'friends') {
      await removeFollow(userId);
      followStatus.value = 'none';
      window.$message.success('已解除好友');
    } else {
      const res = await requestFollow(userId);
      if (res.data.code === 200) {
        followStatus.value = res.data.data;
        window.$message.success(
          followStatus.value === 'friends' ? '你们已成为好友' : '好友请求已发送，等待对方同意'
        );
      } else {
        window.$message.error(res.data.message || '操作失败');
      }
    }
  } catch (e: any) {
    window.$message.error(e?.message || '操作失败');
  } finally {
    followLoading.value = false;
  }
}

const avatarUrl = computed(() => (profile.value ? `${base}/users/${profile.value.id}/avatar` : undefined));

const myUserId = computed(() => {
  const uid = mainStore.userProfile?.profile?.userId;
  return uid == null ? null : Number(uid);
});
const isSelf = computed(() => myUserId.value != null && myUserId.value === Number(userId));

/** 歌单 VO → 网格卡片（角标显示收藏数；封面相对路径拼服务地址） */
function toCard(p: any): PlaylistCard {
  return {
    id: p.id,
    name: p.name,
    picUrl: full(p.coverUrl),
    playCount: p.collectCount ?? 0
  };
}

async function load() {
  loading.value = true;
  try {
    const [profileRes, playlistsRes] = await Promise.all([
      getUserPublicProfile(userId),
      getUserPlaylists(userId)
    ]);
    if (profileRes.data.code === 200) {
      profile.value = profileRes.data.data;
    }
    if (playlistsRes.data.code === 200) {
      const data = playlistsRes.data.data;
      const created = (data.created || []).map(toCard);
      // 「我喜欢的音乐」置顶展示（与左菜单约定一致）
      createdCards.value = data.favorite ? [toCard(data.favorite), ...created] : created;
      collectedCards.value = (data.collected || []).map(toCard);
    }
  } finally {
    loading.value = false;
  }
  loadFollowStatus();
}

onMounted(load);
</script>

<template>
  <div class="p-6 pb-24 user-home">
    <n-spin :show="loading">
      <!-- 资料卡 -->
      <div class="flex items-center gap-6 mb-8">
        <n-avatar :size="96" round :src="avatarUrl || undefined" />
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-4">
            <span class="text-2xl font-bold truncate">{{ profile?.nickname || '用户' }}</span>
            <n-button v-if="isSelf" round size="small" @click="router.push('/userInfoEdit')">
              <n-icon :component="CreateOutline" :size="14" class="mr-1" />
              编辑资料
            </n-button>
            <n-button
              v-else
              round
              size="small"
              :type="followStatus === 'none' ? 'primary' : 'default'"
              :loading="followLoading"
              :disabled="followStatus === 'pending_by_me'"
              @click="onFollow"
            >
              {{ followBtnText }}
            </n-button>
          </div>
          <div class="flex gap-6 mt-3 text-sm">
            <span><b>{{ profile?.followCount ?? 0 }}</b>&nbsp;关注</span>
            <span><b>{{ profile?.fanCount ?? 0 }}</b>&nbsp;粉丝</span>
          </div>
          <div v-if="profile?.signature" class="text-sm opacity-75 mt-2">{{ profile.signature }}</div>
          <div v-if="profile?.region" class="text-xs opacity-50 mt-1">地区：{{ profile.region }}</div>
        </div>
      </div>

      <!-- 歌单集合 -->
      <n-tabs v-model:value="activeTab" type="line">
        <n-tab-pane name="created" :tab="`创建的歌单 (${createdCards.length})`">
          <SongList :songs="createdCards" />
        </n-tab-pane>
        <n-tab-pane name="collected" :tab="`收藏的歌单 (${collectedCards.length})`">
          <SongList :songs="collectedCards" />
        </n-tab-pane>
      </n-tabs>
    </n-spin>
  </div>
</template>

<style scoped>
.user-home {
  max-width: 960px;
  margin: 0 auto;
}
</style>
