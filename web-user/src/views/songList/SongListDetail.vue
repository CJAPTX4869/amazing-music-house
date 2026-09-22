<script setup lang="ts">
import { checkPlaylistCollected, collectPlaylist, getPlaylistAllDetail, getPlaylistDetail, uncollectPlaylist } from '@/service';
import type { AnyObject } from 'env';
import { formateNumber } from '@/utils';
import { computed, reactive, ref, toRaw, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import LoadImg from '@/components/Base/LoadImg.vue';
import { ShareSocialOutline, Search, Star, StarOutline } from '@vicons/ionicons5';
import { Edit } from '@vicons/carbon';
import obverser from '@/utils/obverser';
import { useMainStore } from '@/stores/main';
import { useThemeVars } from 'naive-ui';
import { useMemorizeRequest } from '@/hook/useMemorizeRequest';
import { cloneDeep } from 'lodash';
import { markSearchKeyword } from '@/utils/markSearhKeyword';
import DragSortList from '@/components/SongsList/DragSortList.vue';
import { reorderPlaylistSongs, removePlaylistSong, unfavoriteSong } from '@/service';


let songListIndexMap = new Map();
const router = useRouter();
const route = useRoute();
const mainStore = useMainStore();
const songListDetail = ref<AnyObject>();
const songList = ref<any[]>([]);
/** 排序方式：default 默认（加入顺序）/ name 歌名 / artist 歌手 / custom 自定义（拖拽） */
const sortMode = ref<'default' | 'name' | 'artist' | 'custom'>('default');
const orderSaving = ref(false);
const artistsOf = (song: any) => (Array.isArray(song?.ar) ? song.ar.map((a: any) => a?.name || '').join('/') : '');
/** 展示用列表：歌名/歌手排序在本地完成 */
const displaySongs = computed(() => {
  const source = [...(songList.value || [])];
  if (sortMode.value === 'name') return source.sort((a: any, b: any) => String(a.name || '').localeCompare(String(b.name || ''), 'zh-Hans-CN'));
  if (sortMode.value === 'artist') return source.sort((a: any, b: any) => String(artistsOf(a)).localeCompare(String(artistsOf(b)), 'zh-Hans-CN'));
  return source;
});
/** 自定义排序保存（拖拽视图回调） */
/** 从歌单【真移除】歌曲（删除关联行；「我喜欢的音乐」= 取消收藏，两者都会真删） */
const handleRemoveSong = async (songId: number) => {
  const isFavoriteList = String(songListDetail.value?.name || '') === '我喜欢的音乐';
  const tip = isFavoriteList ? '确定取消收藏这首歌吗？' : '确定从歌单中移除这首歌吗？';
  const doRemove = async () => {
    try {
      const res: any = isFavoriteList
        ? await unfavoriteSong(songId)
        : await removePlaylistSong(songListId.value, songId);
      const code = res?.data?.code ?? res?.code;
      if (code === 200) {
        songList.value = (songList.value || []).filter((s: any) => s.id !== songId);
        rawSongList.value = (rawSongList.value || []).filter((s: any) => s.id !== songId);
        if (songListDetail.value) {
          songListDetail.value.songCount = Math.max(0, (songListDetail.value.songCount || 1) - 1);
        }
        window.$message?.success(isFavoriteList ? '已取消收藏' : '已从歌单移除');
      } else {
        window.$message?.error(res?.data?.message || '操作失败');
      }
    } catch (e) {
      window.$message?.error('操作失败');
    }
  };
  if (typeof (window as any).$dialog?.warning === 'function') {
    (window as any).$dialog.warning({
      title: '确认', content: tip, positiveText: '确定', negativeText: '取消', onPositiveClick: doRemove
    });
  } else if (window.confirm(tip)) {
    await doRemove();
  }
};
const handleSaveOrder = async (songIds: number[]) => {
  orderSaving.value = true;
  try {
    const res: any = await reorderPlaylistSongs(songListId.value, songIds);
    const code = res?.data?.code ?? res?.code;
    if (code === 200) {
      const map = new Map((songList.value || []).map((item: any) => [item.id, item]));
      songList.value = songIds.map((id) => map.get(id)).filter(Boolean) as any[];
      rawSongList.value = [...songList.value];
      window.$message?.success('排序已保存');
    } else {
      window.$message?.error(res?.data?.message || '排序保存失败');
    }
  } catch (e) {
    window.$message?.error('排序保存失败');
  } finally {
    orderSaving.value = false;
  }
};
const rawSongList = ref<any[]>([]);
const tabsValue = ref('musicList');
const hasLoadAllSongsFished = ref(false);
const searchKeyword = ref('');
const themeVars = useThemeVars();
const songListId = ref(route.params.id as string);
const isMySongList = computed(() => {
  return songListDetail.value
    && mainStore.userProfile
    && songListDetail.value?.userId === mainStore.userProfile?.profile?.userId;
});
/* ---------- 收藏歌单（V3.1：收藏他人的公开歌单，联动左菜单分组） ---------- */
const collected = ref(false);
const collectLoading = ref(false);
const loadCollectState = () => {
  if (!mainStore.isLogin) return;
  checkPlaylistCollected(songListId.value).then((res: { data: { code: number; data: boolean } }) => {
    if (res.data.code === 200) collected.value = res.data.data;
  });
};
const toggleCollect = async () => {
  collectLoading.value = true;
  try {
    const res = collected.value
      ? await uncollectPlaylist(songListId.value)
      : await collectPlaylist(songListId.value);
    if (res.data.code === 200) {
      collected.value = !collected.value;
      // 同步左菜单"收藏的歌单"分组（既有事件管道）
      obverser.emit('updateCollectPlayList', collected.value
        ? { subscribed: true, songListDetail: { ...songListDetail.value } }
        : { subscribed: false, id: songListId.value });
      window.$message.success(collected.value ? '收藏成功' : '已取消收藏');
    } else {
      window.$message.error(res.data.message || '操作失败');
    }
  } catch (e: any) {
    window.$message.error(e?.message || '操作失败');
  } finally {
    collectLoading.value = false;
  }
};
const { wrapRequest: wrapFetchPlayList, requestLoading: isLoading, loadSuccess: loadPlayListSuccess, removeCache } = useMemorizeRequest(getPlaylistDetail, 'getPlaylistDetail');

// 获取歌单详情
const fetchSongListDetail = (id: string = route.params.id as string) => {
  wrapFetchPlayList(id).then((res: { data: { playlist: AnyObject }; }) => {
    let creator = res.data.playlist.creator;
    if (res.data.playlist.name === (creator?.nickname + '喜欢的音乐') && creator.userId === mainStore.userProfile?.profile.userId) {
      res.data.playlist.isMyLike = true;
      res.data.playlist.name = '我喜欢的音乐';
    } else {
      res.data.playlist.isMyLike = false;
    }
    songListDetail.value = res.data.playlist;
    loadCollectState();
    fetchMusicList();
  }).finally(() => loadPlayListSuccess())
};
const { wrapRequest, requestLoading, loadSuccess } = useMemorizeRequest(getPlaylistAllDetail, 'getPlaylistAllDetail');

const getMoreMusicList = async () => {
  let trackCount = songListDetail.value!.trackCount;
  let requestCount = Math.round((trackCount - 300) / 300);
  let requestList = [];
  for (let i = 1; i <= requestCount; i++) {
    let offset = 300 * i;
    requestList.push(getPlaylistAllDetail({ id: songListDetail.value!.id, offset: offset }));
  }
  Promise.allSettled(requestList).then((results: any[]) => {
    let allData = results.reduce((total: any[], item: { status: string; value: { data: { songs: any; }; }; }) => {
      if (item.status === 'fulfilled') {
        total = [...total, ...item.value.data.songs]
      }
      return total;
    }, [])
    let data = mainStore.mapSongListAddLike(allData);
    songList.value = songList.value.concat(data).map((item, index
    ) => ({ ...item, rawIndex: index }));
    rawSongList.value = cloneDeep(toRaw(songList).value);
    rawSongList.value.forEach((item: any, index: number) => {
      songListIndexMap.set(item.id, index);
    });
    hasLoadAllSongsFished.value = true;
  });
}
const fetchMusicList = (id: string = route.params.id as string) => {
  wrapRequest({ id }).then((res: { data: { code: number; songs: any[]; }; }) => {
    if (res?.data?.code === 200) {
      let data = mainStore.mapSongListAddLike(res.data.songs);
      songList.value = data.map((item, index
      ) => ({ ...item, rawIndex: index }));
      rawSongList.value = cloneDeep(toRaw(songList).value);
      rawSongList.value.forEach((item: any, index: number) => {
        songListIndexMap.set(item.id, index);
      });
      if (songListDetail.value!.trackCount > 100) {
        getMoreMusicList();
      } else {
        hasLoadAllSongsFished.value = true;
      }
    }
  }).finally(() => loadSuccess());
};
const searchSongList = (keyword: string) => {
  if (!keyword) {
    songList.value = toRaw(rawSongList.value);
  } else {
    let result = rawSongList.value.filter((item: any) => {
      return item.name.includes(keyword)
        || item.ar.some((ar: any) => ar.name.includes(keyword))
        || item.al.name.includes(keyword);
    }).map(item => {
      return { ...item, isSearch: true, index: songListIndexMap.get(item.id) };
    });
    songList.value = markSearchKeyword(
      result, ['name', 'formatAuthor', ['al', 'name']], keyword, themeVars.value.primaryColor
    );
  }

};
watch(() => route.params, (val) => {
  let id = val.id as string;
  if (!route.path.includes('edit') && id && route.path.includes('songList')) {
    songListId.value = id;
    fetchSongListDetail(id);
  }
});
watch(() => mainStore.likeSongs, (val, oldVal) => {
  if (val.length !== oldVal.length) {
    removeCache();
  }
});
watch(searchKeyword, (val) => {
  searchSongList(val);
});
fetchSongListDetail();
const toSongListEdit = () => {
  let id = route.params.id;
  if (songListDetail.value) {
    router.push({
      path: '/songList/edit/' + id,
      query: {
        songListName: songListDetail.value.name,
        desc: songListDetail.value.description,
        coverUrl: songListDetail.value.coverImgUrl
      }
    });
  }
};
const handleShareClick = () => {
  navigator.clipboard.writeText(window.location.href).then(() => {
    window.$message.success('链接复制成功');
  });
};
const handleUpdateMusicListLike = (like: boolean, index: number) => {
  let target = songList.value[index];
  // 更新元数据
  if (target.isSearch) {
    rawSongList.value[target.index].like = like;
  }
  songList.value[index].like = like;
};
</script>
<template>
  <div class="p-8 pb-2">
    <n-spin :show="isLoading">
      <div v-if="songListDetail" class="flex justify-between">
        <load-img ref="imageRef" :has-hover-scale="false" class-name="w-52 h-52" :src="songListDetail?.coverImgUrl" />
        <div class="flex-1 ml-8">
          <div class="flex items-center">
            <n-tag type="primary">
              歌单
            </n-tag>
            <p class="ml-4 text-2xl font-bold ">
              {{ songListDetail.name }}
            </p>
            <div class="ml-2" style="line-height:0" @click="toSongListEdit">
              <n-icon v-if="isMySongList" :size="20" :component="Edit" />
            </div>
          </div>
          <div class="mt-3 text-sm flex-items-center">
            <n-avatar :img-props="{ crossorigin: 'anonymous' }" round :size="30" style="cursor: pointer"
              :src="songListDetail.creator.avatarUrl" :title="`查看 ${songListDetail.creator.nickname} 的主页`"
              @click="router.push(`/user/${songListDetail.creator.userId}`)" />
            <!-- 创作者昵称：限宽截断 + hover 查看完整（最长 50 字，防止挤掉右侧创建时间）；点击跳创作者主页 -->
            <span class="pl-4 text-primary truncate cursor-pointer" style="max-width: 220px"
              :title="`查看 ${songListDetail.creator.nickname} 的主页`"
              @click="router.push(`/user/${songListDetail.creator.userId}`)">{{ songListDetail.creator.nickname }}</span>
            <div class="ml-3 text-gray-600">
              <n-time :time="songListDetail.createTime" type="date" />
              <span>创建</span>
            </div>
          </div>
          <div class="mt-3">
            <n-space>
              <play-all-button :song-list="rawSongList" :song-list-id="songListId" />
          <!-- 排序：默认 / 歌名 / 歌手 / 自定义（拖拽） -->
          <n-select
            v-model:value="sortMode"
            class="ml-3"
            style="width: 136px"
            size="small"
            :options="[
              { label: '默认排序', value: 'default' },
              { label: '按歌名', value: 'name' },
              { label: '按歌手', value: 'artist' },
              { label: '自定义排序', value: 'custom' }
            ]"
          />
              <n-button v-if="!isMySongList && !songListDetail.isMyLike && mainStore.isLogin" size="medium"
                round :loading="collectLoading" @click="toggleCollect">
                <template #icon>
                  <n-icon :component="collected ? Star : StarOutline" />
                </template>
                {{ collected ? '已收藏' : '收藏' }}
              </n-button>
              <n-button size="medium" round @click="handleShareClick">
                <template #icon>
                  <n-icon :component="ShareSocialOutline" />
                </template>
                分享
              </n-button>
            </n-space>
          </div>
          <div class="mt-3">
            <div class="flex">
              <div>
                <span>歌曲</span>
                <span class="px-1">:</span>
                {{ songListDetail.trackCount }}
              </div>
              <div class="ml-4">
                <span>播放</span>
                <span class="px-1">:</span>
                {{ formateNumber(songListDetail.playCount) }}
              </div>
            </div>
            <div v-if="songListDetail.description" class="flex">
              <n-ellipsis expand-trigger="click" line-clamp="1" :tooltip="false">
                <span>简介</span>
                <span class="px-1">:</span>
                {{ songListDetail.description }}
              </n-ellipsis>
            </div>
          </div>
        </div>
      </div>
      <div v-else style="height:200px" />
      <div :value="tabsValue" class="mt-5">
        <div class="flex justify-between sticky top-0 z-[999] pt-2">
          <n-tabs type="line" :value="tabsValue">
            <n-tab name="musicList" @click="tabsValue = 'musicList'">
              歌曲列表
            </n-tab>
          </n-tabs>
          <div class="w-60" v-if="hasLoadAllSongsFished">
            <n-input v-model:value="searchKeyword" clearable size="small" placeholder="搜索歌单歌曲" round>
              <template #prefix>
                <n-icon class="cursor-pointer" :component="Search" />
              </template>
            </n-input>
          </div>
        </div>

        <div v-show="tabsValue === 'musicList'" class="mt-5">
          <drag-sort-list v-if="sortMode === 'custom'" class="py-2" :songs="songList" :saving="orderSaving" @save="handleSaveOrder" />
        <music-list v-else :removable="isMySongList" @remove="handleRemoveSong" :song-list="displaySongs" :raw-song-list="displaySongs" :loading="requestLoading"
            :play-list-id="songListId" @update-music-list-like="handleUpdateMusicListLike" />
        </div>
      </div>
    </n-spin>
  </div>
</template>
<style scoped>
::deep(.n-card-header__main) {
  text-align: center;
}

::deep(.n-tabs .n-tabs-nav.n-tabs-nav--line-type .n-tabs-nav-scroll-content) {
  border: none;
}
</style>
