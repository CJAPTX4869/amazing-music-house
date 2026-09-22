<template>
  <!--
    自定义排序（拖拽）视图
    - 用法：歌单详情页把「排序」切到「自定义」时替换表格显示本组件
    - 交互：按住行拖动排序（也提供上/下移按钮作为兜底），点「保存顺序」回调父组件持久化
    - 只做展示与排序，不负责请求；保存结果由父组件处理
  -->
  <div class="drag-sort">
    <div class="drag-sort__tip">
      按住任意一行上下拖动即可调整顺序，调好后点右侧「保存顺序」
    </div>

    <div
      v-for="(song, index) in list"
      :key="song.id"
      class="drag-sort__row"
      :class="{ 'is-dragging': dragIndex === index, 'is-over': overIndex === index }"
      draggable="true"
      @dragstart="handleDragStart(index)"
      @dragover.prevent="handleDragOver(index)"
      @dragend="handleDragEnd"
      @drop.prevent="handleDrop(index)"
    >
      <span class="drag-sort__handle" title="按住拖动">⠿</span>
      <span class="drag-sort__index">{{ String(index + 1).padStart(2, '0') }}</span>

      <img v-if="song.picUrl" class="drag-sort__cover" :src="song.picUrl" alt="" />
      <div v-else class="drag-sort__cover drag-sort__cover--empty" />

      <div class="drag-sort__meta">
        <div class="drag-sort__title">{{ song.name }}</div>
        <div class="drag-sort__artist">{{ formatAuthor(song.ar) }}</div>
      </div>

      <div class="drag-sort__actions">
        <button class="drag-sort__btn" :disabled="index === 0" @click="move(index, -1)">↑</button>
        <button class="drag-sort__btn" :disabled="index === list.length - 1" @click="move(index, 1)">↓</button>
      </div>
    </div>

    <div class="drag-sort__footer">
      <n-button size="small" @click="reset">还原为进入时顺序</n-button>
      <n-button size="small" type="primary" :loading="saving" @click="save">保存顺序</n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { NButton } from 'naive-ui';
import { formateSongsAuthor } from '@/utils';

const props = defineProps<{
  songs: any[];
  saving?: boolean;
}>();

const emit = defineEmits<{
  (e: 'save', songIds: number[]): void;
}>();

const list = ref<any[]>([]);
const origin = ref<any[]>([]);
const dragIndex = ref(-1);
const overIndex = ref(-1);

/** 父组件的歌曲列表变化时同步（进入自定义模式、切歌单等） */
watch(
  () => props.songs,
  (value) => {
    list.value = Array.isArray(value) ? [...value] : [];
    origin.value = list.value.map((item) => item.id);
  },
  { immediate: true, deep: false }
);

const formatAuthor = (ar: any[]) => {
  if (!Array.isArray(ar) || ar.length === 0) return '未知歌手';
  return formateSongsAuthor(ar);
};

function handleDragStart(index: number) {
  dragIndex.value = index;
}

function handleDragOver(index: number) {
  overIndex.value = index;
}

function handleDragEnd() {
  dragIndex.value = -1;
  overIndex.value = -1;
}

function handleDrop(index: number) {
  const from = dragIndex.value;
  if (from < 0 || from === index) {
    handleDragEnd();
    return;
  }
  const next = [...list.value];
  const [moved] = next.splice(from, 1);
  next.splice(index, 0, moved);
  list.value = next;
  handleDragEnd();
}

/** 上/下移（键盘或不便拖拽时的兜底） */
function move(index: number, delta: number) {
  const target = index + delta;
  if (target < 0 || target >= list.value.length) return;
  const next = [...list.value];
  const [moved] = next.splice(index, 1);
  next.splice(target, 0, moved);
  list.value = next;
}

function reset() {
  const map = new Map(list.value.map((item) => [item.id, item]));
  list.value = origin.value.map((id) => map.get(id)).filter(Boolean);
}

function save() {
  emit('save', list.value.map((item) => item.id));
}
</script>

<style scoped>
.drag-sort {
  padding: 8px 0 16px;
}
.drag-sort__tip {
  font-size: 12px;
  opacity: 0.55;
  padding: 4px 2px 10px;
}
.drag-sort__row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 8px;
  cursor: grab;
  transition: background 0.15s;
}
.drag-sort__row:hover {
  background: rgba(128, 128, 128, 0.12);
}
.drag-sort__row.is-dragging {
  opacity: 0.4;
}
.drag-sort__row.is-over {
  box-shadow: inset 0 2px 0 var(--primary-color, #18a058);
}
.drag-sort__handle {
  opacity: 0.45;
  font-size: 16px;
  line-height: 1;
  user-select: none;
}
.drag-sort__index {
  width: 26px;
  font-size: 12px;
  opacity: 0.5;
}
.drag-sort__cover {
  width: 36px;
  height: 36px;
  border-radius: 6px;
  object-fit: cover;
  flex-shrink: 0;
}
.drag-sort__cover--empty {
  background: rgba(128, 128, 128, 0.2);
}
.drag-sort__meta {
  flex: 1;
  min-width: 0;
}
.drag-sort__title {
  font-size: 14px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.drag-sort__artist {
  font-size: 12px;
  opacity: 0.6;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.drag-sort__actions {
  display: flex;
  gap: 4px;
}
.drag-sort__btn {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  border: 1px solid rgba(128, 128, 128, 0.3);
  background: transparent;
  cursor: pointer;
  line-height: 1;
}
.drag-sort__btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.drag-sort__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 2px 0;
}
</style>
