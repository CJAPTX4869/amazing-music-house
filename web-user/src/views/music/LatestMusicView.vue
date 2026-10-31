<script setup lang="ts">
import { computed, ref, type CSSProperties } from 'vue';
import { useRouter } from 'vue-router';
import { getTopSong } from '../../service';
import LoadImg from '@/components/Base/LoadImg.vue';
import PlayIcon from '@/components/Base/PlayIcon.vue';
import { ChatbubbleEllipsesOutline, Heart } from '@vicons/ionicons5';
import { useMemorizeRequest } from '@/hook/useMemorizeRequest';
import useThemeStyle from '@/hook/useThemeStyle';
import { useDbClickPlay } from '@/hook/useDbClickPlay';
import { mapSongs } from '@/utils/arr-map';
import { formatCount } from '@/utils/commentContent';
import { useMainStore } from '@/stores/main';
import { useNanoid } from '@/hook/useNanoid';
// V3.2 类型标签分类：全部=最新100首，其余按歌曲标签精确筛选
const typeList = [
  { value: '全部', name: '全部' },
  { value: '华语', name: '华语' },
  { value: '欧美', name: '欧美' },
  { value: '日本', name: '日本' },
  { value: '韩国', name: '韩国' },
  { value: '纯音乐', name: '纯音乐' },
  { value: '其他', name: '其他' }
];
const mainStore = useMainStore();
// 评论角标 → 歌曲评论页（V3.1）
const router = useRouter();
const goComment = (item: any) => router.push(`/comment/song/${item.id}`);
const newSongList = ref<any[]>([]);
const activeType = ref('全部');
const { stripedClass, themeVars } = useThemeStyle();
const { currentId, set } = useNanoid();
const tagColor = computed(() => {
  return {
    textColor: themeVars.value.primaryColor,
    borderColor: themeVars.value.primaryColor
  };
});
const activeStyle = (value: string): CSSProperties => {
  return {
    color: value === activeType.value
      ? themeVars.value.primaryColor
      : themeVars.value.textColorBase,
    opacity: value === activeType.value
      ? 1
      : '0.5',
    fontSize: value === activeType.value
      ? '0.9rem'
      : '0.8rem'
  };
};
const { wrapRequest, requestLoading: isLoading, loadSuccess } = useMemorizeRequest(getTopSong, 'getTopSong');
const fetchData = () => {
  set(activeType.value);
  wrapRequest(activeType.value as any)
    .then(async (res: { data: { data: never[]; }; }) => {
      newSongList.value = mainStore.mapSongListAddLike(mapSongs(res.data.data));
      loadSuccess();
    });
};

const handleTypeClick = (value: string) => {
  activeType.value = value;
  fetchData();
};
const handleDBClick = useDbClickPlay();
const handleMouseEnter = (e: MouseEvent, value: string) => {
  if (value === activeType.value) return;
  (e.target as HTMLElement).style.opacity = '1';
};
const handleMouseLeave = (e: MouseEvent, value: string) => {
  if (value === activeType.value) return;
  (e.target as HTMLElement).style.opacity = '0.8';
};
fetchData();

</script>

<template>
  <!-- 必须单根：外层 <transition mode="out-in"> 拿不到 fragment 的离场元素，
       会导致跳走时新页面永远不插入（生产构建无告警，表现为白屏） -->
  <div class="latest-music-view">
  <div class="sticky top-0  z-10  p-4 bg-slate-50 dark:bg-black">
    <span v-for="item in typeList" :key="item.value"
      class="px-2 rounded-md opacity-50 transition duration-150 ease-in-out cursor-pointer"
      :style="activeStyle(item.value)" @mouseenter="handleMouseEnter($event, item.value)"
      @mouseleave="handleMouseLeave($event, item.value)" @click="handleTypeClick(item.value)">{{ item.name }}</span>
  </div>
  <!-- 新歌速递列表 -->
  <div class="mt-4">
    <div v-show="isLoading">
      <div v-for="item in 9" :key="item" class="flex justify-between items-center p-2">
        <div class="flex items-center">
          <n-skeleton width="15px" class="mt-2" type="text" />
          <n-skeleton class="mt-2 ml-2" height="64px" width="64px" :sharp="false" />
        </div>
        <n-skeleton width="30%" height="30px" class="m-4" type="text" :repeat="3" />
        <n-skeleton width="5%" height="30px" class="m-2" type="text" />
      </div>
    </div>
    <transition name="fade" appear mode="out-in">
      <ul v-show="!isLoading" class="songList">
        <li v-for="(item, index) in newSongList" :key="item.id"
          :class="'flex items-center py-2 px-4 transition-colors ' + stripedClass(index)"
          @dblclick="handleDBClick(newSongList, currentId, item, index)">
          <div class="flex items-center" style="width:120px">
            <p class="w-5 text-sm opacity-80">
              {{ index < 9 ? '0' + (index + 1) : (index + 1) }} </p>
                <div style="transform: translateZ(0);" class="relative ml-4 w-16 h-16 rounded-md">
                  <load-img loading-height="64px" class-name="w-16 h-16 rounded-md" :src="item.al?.picUrl"
                    :show-message="false" :double-click-preview="false" />
                  <play-icon :size="15" class="cursor-pointer position-center"
                    :style="{ opacity: '1', width: '25px', height: '25px' }" />
                </div>
          </div>
          <p class="ml-6 w-xs text-sm truncate flex-30">
            {{ item.name }}
            <n-tag v-if="item.mv" size="small" :color="tagColor">
              MV
            </n-tag>
            <span class="count-badge" :title="`${item.likeCount ?? 0} 人喜欢`">
              <n-icon :size="12" :component="Heart" />
              {{ formatCount(item.likeCount) }}
            </span>
            <span class="count-badge clickable" title="查看评论" @click.stop="goComment(item)">
              <n-icon :size="12" :component="ChatbubbleEllipsesOutline" />
              {{ formatCount(item.commentCount) }}
            </span>
          </p>
          <p class="ml-2 w-xs text-sm opacity-80 flex-30">
            <n-ellipsis>{{ item.formatAuthor }}</n-ellipsis>
          </p>
          <p class="flex-1 ml-2 w-xs text-sm truncate opacity-80 flex-30">
            {{ item.al?.name }}
          </p>
          <n-time class="pl-4 mx-2 text-sm text-left opacity-80" :time="item.dt" format="mm:ss" />
        </li>
      </ul>
    </transition>
  </div>
  </div>
</template>

<style scoped>
:deep(.n-tabs .n-tabs-rail) {
  border-radius: 30px;
}

:deep(.n-tabs .n-tabs-rail .n-tabs-tab-wrapper > .n-tabs-tab) {
  border-radius: 30px;
}

:deep(.n-data-table-thead) {
  display: none;
}

.text-left-opacity-50 {
  @apply pl-4 text-left opacity-50;
}

.flex-30 {
  flex: 0.5;
}

/* 喜欢/评论角标（V3.1，网易云样式） */
.count-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  margin-left: 10px;
  font-size: 12px;
  opacity: 0.55;
  white-space: nowrap;
  vertical-align: middle;
}

.count-badge.clickable {
  cursor: pointer;
}

.count-badge.clickable:hover {
  opacity: 1;
  color: var(--primary);
}
</style>
