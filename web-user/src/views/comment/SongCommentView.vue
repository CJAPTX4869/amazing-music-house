<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { NAlert, NAvatar, NButton, NEmpty, NIcon, NInput, NSpin, NTime } from 'naive-ui';
import { ChatbubbleEllipsesOutline, Heart, HeartOutline, ImageOutline, TrashOutline } from '@vicons/ionicons5';
import { useMainStore } from '@/stores/main';
import service from '@/service/request';
import {
  deleteComment,
  getEmojis,
  getSongComments,
  getMusicUrl,
  likeComment,
  replyComment,
  sendComment,
  uploadCommentImage,
  unlikeComment
} from '@/service';
import { formatCount, parseContent, type ContentSegment } from '@/utils/commentContent';
import { compressKeepRatio, validateImageFile } from '@/utils/imageCompress';
import EmojiPicker from '@/components/comment/EmojiPicker.vue';
import UserAvatar from '@/components/common/UserAvatar.vue';

/**
 * 歌曲评论页（网易云风格）
 * <p>
 * 精彩评论（按点赞）/ 最新评论（按时间）双排序；评论正文以 [表情名] 占位符
 * 存储，渲染时由 parseContent 拆段替换为表情图片（无 v-html，天然防 XSS）；
 * 楼中楼回复平铺两层，任何人可继续追评。
 */

type CommentUser = { id: number; nickname: string; avatarUrl: string };
type CommentItem = {
  id: number;
  content: string;
  imageUrl: string | null;
  likeCount: number;
  liked: boolean;
  createTime: string;
  user: CommentUser;
  replyUserNickname?: string | null;
  replies?: CommentItem[] | null;
};

const route = useRoute();
const songId = route.params.id as string;
const mainStore = useMainStore();

// 图片地址统一拼服务前缀（后端返回相对 API 路径）
const base = service.defaults.baseURL || '';
const full = (path?: string | null) => (path ? (path.startsWith('http') ? path : base + path) : '');

const myUserId = computed(() => mainStore.userProfile?.profile?.userId ?? null);

/* ---------- 歌曲信息条 ---------- */
const songInfo = ref<{
  name: string;
  artist: string;
  picUrl: string;
  likeCount: number;
  commentCount: number;
} | null>(null);

/* ---------- 评论列表 ---------- */
type SortKey = 'hot' | 'new';
const comments = ref<CommentItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = 20;
const sort = ref<SortKey>('hot');
const listLoading = ref(false);

async function load(reset: boolean) {
  if (listLoading.value) return;
  listLoading.value = true;
  try {
    const res = await getSongComments(songId, page.value, pageSize, sort.value);
    if (res.data.code === 200) {
      total.value = res.data.data.total;
      if (reset) {
        comments.value = res.data.data.records;
      } else {
        comments.value.push(...res.data.data.records);
      }
    }
  } finally {
    listLoading.value = false;
  }
}

async function loadMore() {
  page.value += 1;
  await load(false);
}

function switchSort(value: string) {
  sort.value = value as SortKey;
  page.value = 1;
  load(true);
}

/* ---------- 表情 ---------- */
const emojiMap = ref<Map<string, string>>(new Map());

function segments(content: string): ContentSegment[] {
  return parseContent(content, emojiMap.value, full);
}

/* ---------- 输入与发布 ---------- */
const content = ref('');
const imagePath = ref('');
const imagePreview = ref('');
const submitting = ref(false);
const uploading = ref(false);
const pickImageRef = ref<HTMLInputElement>();

type ReplyTarget = {
  /** 被回复的评论 id（可能是根也可能是楼中楼某条，后端统一挂根） */
  commentId: number;
  /** 所属根评论 id（本地插入定位用） */
  rootId: number;
  replyUserId?: number;
  nickname: string;
};
const replyTarget = ref<ReplyTarget | null>(null);

const placeholder = computed(() =>
  replyTarget.value ? `回复 @${replyTarget.value.nickname}：` : '说点什么吧，写下你的乐评…'
);

function onPickEmoji(placeholder: string) {
  content.value += placeholder;
}

function startReply(root: CommentItem, reply?: CommentItem) {
  const target = reply ?? root;
  replyTarget.value = {
    commentId: target.id,
    rootId: root.id,
    replyUserId: target.user.id,
    nickname: target.user.nickname
  };
}

function cancelReply() {
  replyTarget.value = null;
}

async function submit() {
  const text = content.value.trim();
  if (!text) {
    window.$message.warning('说点什么吧');
    return;
  }
  submitting.value = true;
  try {
    const res = replyTarget.value
      ? await replyComment(replyTarget.value.commentId, {
          content: text,
          replyUserId: replyTarget.value.replyUserId
        })
      : await sendComment(songId, { content: text, imagePath: imagePath.value || undefined });
    if (res.data.code === 200) {
      window.$message.success(replyTarget.value ? '回复成功' : '评论成功');
      applyLocal(res.data.data);
      content.value = '';
      imagePath.value = '';
      imagePreview.value = '';
      replyTarget.value = null;
    } else {
      window.$message.error(res.data.message || '发布失败');
    }
  } catch (e: any) {
    window.$message.error(e?.message || '发布失败');
  } finally {
    submitting.value = false;
  }
}

/** 发布成功后本地插入，免重拉整页 */
function applyLocal(vo: CommentItem) {
  if (replyTarget.value) {
    const { rootId, nickname } = replyTarget.value;
    const root = comments.value.find(c => c.id === rootId);
    if (root) {
      root.replies = [...(root.replies || []), { ...vo, replyUserNickname: nickname }];
    }
  } else {
    comments.value.unshift(vo);
  }
  total.value += 1;
}

/* ---------- 点赞 / 删除 ---------- */
async function toggleLike(item: CommentItem) {
  if (!mainStore.isLogin) {
    window.$message.warning('请先登录后点赞');
    return;
  }
  try {
    if (item.liked) {
      await unlikeComment(item.id);
      item.liked = false;
      item.likeCount = Math.max(0, item.likeCount - 1);
    } else {
      await likeComment(item.id);
      item.liked = true;
      item.likeCount += 1;
    }
  } catch (e: any) {
    window.$message.error(e?.message || '操作失败');
  }
}

function canDelete(item: CommentItem) {
  return myUserId.value != null && item.user.id === myUserId.value;
}

async function removeComment(item: CommentItem, root?: CommentItem) {
  try {
    const res = await deleteComment(item.id);
    if (res.data.code === 200) {
      if (root) {
        root.replies = (root.replies || []).filter(r => r.id !== item.id);
      } else {
        comments.value = comments.value.filter(c => c.id !== item.id);
      }
      total.value = Math.max(0, total.value - 1);
      window.$message.success('已删除');
    } else {
      window.$message.error(res.data.message || '删除失败');
    }
  } catch (e: any) {
    window.$message.error(e?.message || '删除失败');
  }
}

/* ---------- 配图上传 ---------- */
function pickImage() {
  pickImageRef.value?.click();
}

async function onPickImage(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  const invalid = validateImageFile(file);
  if (invalid) {
    window.$message.error(invalid);
    return;
  }
  uploading.value = true;
  try {
    const compressed = await compressKeepRatio(file, 1080, 0.85);
    const res = await uploadCommentImage(compressed);
    if (res.data.code === 200) {
      imagePath.value = res.data.data;
      // 预览走按路径直出接口（发布前评论尚无 id，无法走 /comments/{id}/image）
      imagePreview.value = full(`/comments/image?path=${encodeURIComponent(res.data.data)}`);
    } else {
      window.$message.error(res.data.message || '图片上传失败');
    }
  } catch (e: any) {
    window.$message.error(e?.message || '图片上传失败');
  } finally {
    uploading.value = false;
  }
}

/* ---------- 初始化 ---------- */
onMounted(async () => {
  load(true);
  // 表情清单（匿名可访问）
  try {
    const em = await getEmojis();
    if (em.data.code === 200) {
      emojiMap.value = new Map(em.data.data.map((e: any) => [e.name, full(e.imageUrl)]));
    }
  } catch {
    // 表情加载失败不影响文本评论
  }
  // 歌曲信息条（复用 legacy 单曲接口，字段含 name/ar/al）
  try {
    const info = await getMusicUrl(songId);
    const s = info.data.data?.[0];
    if (s) {
      songInfo.value = {
        name: s.name,
        artist: s.ar?.[0]?.name || '',
        picUrl: s.al?.picUrl || '',
        likeCount: s.likeCount ?? 0,
        commentCount: s.commentCount ?? 0
      };
    }
  } catch {
    // 信息条失败不影响评论功能
  }
});
</script>

<template>
  <div class="p-6 pb-24 comment-page">
    <!-- 歌曲信息条 -->
    <div class="flex items-center gap-3 mb-6">
      <img
        v-if="songInfo?.picUrl"
        :src="songInfo.picUrl"
        class="w-12 h-12 rounded object-cover"
        crossorigin="anonymous"
      />
      <div class="min-w-0">
        <div class="font-bold text-base truncate">{{ songInfo?.name || '歌曲评论' }}</div>
        <div class="text-xs opacity-60 truncate">{{ songInfo?.artist }}</div>
      </div>
      <div class="ml-auto flex items-center gap-4 text-sm opacity-70">
        <span class="flex items-center gap-1" :title="`${songInfo?.likeCount ?? 0} 人喜欢`">
          <n-icon :size="15" :component="Heart" />
          {{ formatCount(songInfo?.likeCount) }}
        </span>
        <span class="flex items-center gap-1" title="当前评论数">
          <n-icon :size="15" :component="ChatbubbleEllipsesOutline" />
          {{ formatCount(songInfo?.commentCount) }}
        </span>
      </div>
    </div>

    <!-- 评论输入区 -->
    <div v-if="mainStore.isLogin" class="flex gap-3 mb-6">
      <n-avatar
        round
        :size="36"
        :src="mainStore.userProfile?.profile?.avatarUrl || undefined"
      />
      <div class="flex-1 min-w-0">
        <div v-if="replyTarget" class="replying-tip mb-1">
          回复 @{{ replyTarget.nickname }}
          <n-button text size="tiny" @click="cancelReply">取消</n-button>
        </div>
        <n-input
          v-model:value="content"
          type="textarea"
          :maxlength="1000"
          show-count
          :placeholder="placeholder"
          :autosize="{ minRows: 2, maxRows: 6 }"
        />
        <div v-if="imagePreview" class="image-preview mt-2">
          <img :src="imagePreview" crossorigin="anonymous" />
          <button class="remove-btn" title="移除配图" @click="imagePath = ''; imagePreview = ''">×</button>
        </div>
        <div class="flex items-center gap-2 mt-2">
          <EmojiPicker @pick="onPickEmoji" />
          <n-button
            quaternary
            circle
            size="small"
            title="插入图片"
            :loading="uploading"
            @click="pickImage"
          >
            <n-icon :component="ImageOutline" :size="18" />
          </n-button>
          <input
            ref="pickImageRef"
            type="file"
            accept="image/*"
            class="hidden-input"
            @change="onPickImage"
          />
          <div class="flex-1" />
          <n-button round type="primary" :loading="submitting" :disabled="!content.trim()" @click="submit">
            发布
          </n-button>
        </div>
      </div>
    </div>
    <n-alert v-else type="info" :bordered="false" class="mb-6">
      登录后即可发表评论与点赞
    </n-alert>

    <!-- 排序切换 -->
    <div class="sort-bar">
      <span class="sort-item" :class="{ active: sort === 'hot' }" @click="switchSort('hot')">精彩评论</span>
      <span class="sort-item" :class="{ active: sort === 'new' }" @click="switchSort('new')">
        最新评论 ({{ formatCount(total) }})
      </span>
    </div>

    <!-- 评论列表 -->
    <n-spin :show="listLoading">
      <n-empty v-if="!comments.length && !listLoading" class="py-12" description="还没有评论，来抢沙发～" />
      <div v-for="c in comments" :key="c.id" class="comment-item">
        <user-avatar
          :id="c.user.id"
          :nickname="c.user.nickname"
          :avatar-url="full(c.user.avatarUrl)"
          :size="36"
        />
        <div class="flex-1 min-w-0">
          <div class="nickname">{{ c.user.nickname }}</div>
          <div class="content">
            <template v-for="(seg, i) in segments(c.content)" :key="i">
              <span v-if="seg.type === 'text'" class="whitespace-pre-wrap">{{ seg.text }}</span>
              <img v-else :src="seg.url" :alt="seg.name" class="emoji-img" />
            </template>
          </div>
          <img
            v-if="c.imageUrl"
            :src="full(c.imageUrl)"
            class="comment-image"
            crossorigin="anonymous"
          />
          <div class="meta">
            <n-time :time="Date.parse(c.createTime)" type="relative" />
            <span class="meta-actions">
              <span class="action like-btn" :class="{ liked: c.liked }" @click="toggleLike(c)">
                <n-icon :component="c.liked ? Heart : HeartOutline" :size="14" />
                {{ formatCount(c.likeCount) }}
              </span>
              <span class="action" @click="startReply(c)">回复</span>
              <span v-if="canDelete(c)" class="action delete-btn" @click="removeComment(c)">
                <n-icon :component="TrashOutline" :size="13" />
              </span>
            </span>
          </div>

          <!-- 楼中楼（平铺两层，可继续追评） -->
          <div v-if="c.replies?.length" class="replies">
            <div v-for="r in c.replies" :key="r.id" class="reply-item">
              <user-avatar
                :id="r.user.id"
                :nickname="r.user.nickname"
                :avatar-url="full(r.user.avatarUrl)"
                :size="26"
              />
              <div class="flex-1 min-w-0">
                <div class="nickname text-xs">
                  {{ r.user.nickname }}
                  <span v-if="r.replyUserNickname" class="opacity-60">
                    回复 @{{ r.replyUserNickname }}
                  </span>
                </div>
                <div class="content text-sm">
                  <template v-for="(seg, i) in segments(r.content)" :key="i">
                    <span v-if="seg.type === 'text'" class="whitespace-pre-wrap">{{ seg.text }}</span>
                    <img v-else :src="seg.url" :alt="seg.name" class="emoji-img" />
                  </template>
                </div>
                <img
                  v-if="r.imageUrl"
                  :src="full(r.imageUrl)"
                  class="comment-image"
                  crossorigin="anonymous"
                />
                <div class="meta">
                  <n-time :time="Date.parse(r.createTime)" type="relative" />
                  <span class="meta-actions">
                    <span class="action like-btn" :class="{ liked: r.liked }" @click="toggleLike(r)">
                      <n-icon :component="r.liked ? Heart : HeartOutline" :size="13" />
                      {{ formatCount(r.likeCount) }}
                    </span>
                    <span class="action" @click="startReply(c, r)">回复</span>
                    <span v-if="canDelete(r)" class="action delete-btn" @click="removeComment(r, c)">
                      <n-icon :component="TrashOutline" :size="12" />
                    </span>
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </n-spin>

    <!-- 加载更多 -->
    <div v-if="comments.length < total" class="text-center mt-4">
      <n-button :loading="listLoading" @click="loadMore">加载更多评论</n-button>
    </div>
  </div>
</template>

<style scoped>
.comment-page {
  max-width: 860px;
  margin: 0 auto;
}

.sort-bar {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid rgba(128, 128, 128, 0.15);
  margin-bottom: 4px;
}

.sort-item {
  cursor: pointer;
  padding: 8px 2px;
  font-size: 14px;
  opacity: 0.65;
  user-select: none;
}

.sort-item.active {
  color: var(--primary);
  opacity: 1;
  font-weight: 600;
  border-bottom: 2px solid var(--primary);
}

.comment-item {
  display: flex;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid rgba(128, 128, 128, 0.15);
}

.nickname {
  font-size: 13px;
  opacity: 0.65;
}

.content {
  font-size: 14px;
  line-height: 1.7;
  margin: 2px 0;
  word-break: break-word;
}

.emoji-img {
  width: 22px;
  height: 22px;
  vertical-align: -6px;
  margin: 0 1px;
}

.comment-image {
  max-width: 220px;
  max-height: 160px;
  border-radius: 6px;
  margin-top: 6px;
  display: block;
}

.meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  opacity: 0.65;
  margin-top: 4px;
}

.meta-actions {
  display: inline-flex;
  align-items: center;
  gap: 14px;
}

.action {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  user-select: none;
}

.action:hover {
  opacity: 1;
}

.like-btn.liked {
  color: var(--primary);
  opacity: 1;
}

.delete-btn:hover {
  color: #e0443c;
}

.replies {
  background: rgba(128, 128, 128, 0.08);
  border-radius: 8px;
  padding: 4px 12px;
  margin-top: 8px;
}

.reply-item {
  display: flex;
  gap: 8px;
  padding: 8px 0;
}

.reply-item + .reply-item {
  border-top: 1px dashed rgba(128, 128, 128, 0.15);
}

.replying-tip {
  font-size: 12px;
  opacity: 0.75;
}

.hidden-input {
  display: none;
}

/* 已选配图预览：96px 方卡 + 右上角悬浮移除钮 */
.image-preview {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(128, 128, 128, 0.3);
}

.image-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.image-preview .remove-btn {
  position: absolute;
  top: 4px;
  right: 4px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.image-preview .remove-btn:hover {
  background: rgba(0, 0, 0, 0.75);
}
</style>
