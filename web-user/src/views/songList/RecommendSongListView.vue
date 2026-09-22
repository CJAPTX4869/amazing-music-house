<script setup lang="ts">
import { useMemoryScrollTop } from '@/hook/useMemoryScrollTop';
import { getPersonalized } from '@/service';
import { useLoadingBar, useThemeVars } from 'naive-ui';
import { computed, onBeforeMount, ref } from 'vue';

const songList = ref<any[]>([]);
const isLoading = ref(true);
const loadingBar = useLoadingBar();
const themeVars = useThemeVars();
const bodyColor = computed(() => themeVars.value.bodyColor);
useMemoryScrollTop('.rightMain>.n-layout-scroll-container');

const fetchSongList = async () => {
  loadingBar.start();
  getPersonalized(50).then(res => {
    songList.value = res.data.result || [];
    isLoading.value = false;
    loadingBar.finish();
  }).catch(() => {
    isLoading.value = false;
    loadingBar.finish();
  });
};

onBeforeMount(() => {
  fetchSongList();
});
</script>

<template>
  <div class="px-6">
    <div v-if="songList.length === 0 && isLoading" class="relative">
      <div class="flex p-4">
        <n-skeleton class="w-36 h-36 rounded-xl" />
        <div class="flex-1 ml-4">
          <n-skeleton width="70px" height="28px" :sharp="false" />
          <n-skeleton class="my-2" :sharp="false" type="text" />
        </div>
      </div>
    </div>
    <div v-else-if="songList.length > 0" class="overflow-hidden relative mt-4 h-44 rounded-xl cursor-pointer">
      <div
        class="flex absolute w-full h-44 blur-lg"
        :style="{ backgroundImage: `url(${songList[0]?.coverImgUrl})` }"
      />
      <div class="box-border flex absolute z-50 p-4 w-full h-44 bg-black/30">
        <load-img
          loading-height="144px"
          class-name="w-36 h-36 rounded-md"
          :src="songList[0]?.coverImgUrl"
        />
        <div class="flex-1 ml-4">
          <n-tag type="success">
            推荐歌单
          </n-tag>
          <p class="py-2 text-white">
            {{ songList[0]?.name }}
          </p>
          <n-ellipsis :line-clamp="5" :tooltip="false" class="text-xs text-white opacity-80">
            {{ songList[0]?.description }}
          </n-ellipsis>
        </div>
      </div>
    </div>
    <div class="mt-4">
      <SongListSkeleton v-if="isLoading" />
      <div v-else>
        <SongList :songs="songList" />
      </div>
    </div>
  </div>
</template>

<style scoped>
::deep(.n-tabs-bar) {
  display: none;
}
::deep(.n-tabs .n-tabs .n-tabs-nav.n-tabs-nav--line-type .n-tabs-nav-scroll-content) {
  border: none;
}
::deep(.n-tag__content) {
  display: flex;
}
</style>
