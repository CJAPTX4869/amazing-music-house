<script setup lang="ts">
import { createPlaylist } from '@/service/playlist';
import { useMainStore } from '@/stores/main';
import obverser from '@/utils/obverser';
import { ref } from 'vue';

/**
 * 创建歌单弹窗（左菜单"我创建的歌单"分组旁的 + 入口）
 * <p>
 * 创建成功后走 obverser('updateMyCreatePlayList') 事件，
 * 由 LayoutLeftMenu 监听并刷新左侧菜单，本组件不直接操作菜单。
 */
const mainStore = useMainStore();
const show = ref(false);
const title = ref('');
const isPrivate = ref(false);
const btnLoading = ref(false);

const handleCreate = () => {
  if (!mainStore.isLogin) {
    window.$message.error('请先登录');
    return;
  }
  const name = title.value.trim();
  if (!name) return;
  const params: { name: string, privacy?: string } = { name };
  if (isPrivate.value) {
    params.privacy = '10';
  }
  btnLoading.value = true;
  createPlaylist(params).then((res) => {
    if (res.data?.code === 200) {
      window.$message.success('创建成功');
      show.value = false;
      title.value = '';
      isPrivate.value = false;
      mainStore.mySubscribeSongList.push(res.data.playlist);
      obverser.emit('updateMyCreatePlayList', res.data.playlist);
    } else {
      window.$message.error(res.data?.message || '创建失败');
    }
  }).catch((e: any) => {
    window.$message.error(e?.message || '创建失败');
  }).finally(() => {
    btnLoading.value = false;
  });
};

defineExpose({
  show() {
    // 打开时重置表单，避免上次未提交的输入残留
    title.value = '';
    isPrivate.value = false;
    show.value = true;
  }
});
</script>

<template>
  <n-modal v-model:show="show" preset="card" title="创建歌单" style="width: 420px">
    <div class="flex flex-col gap-4">
      <n-input v-model:value="title" placeholder="请输入歌单标题" :maxlength="40" show-count clearable
        @keyup.enter="handleCreate" />
      <n-checkbox v-model:checked="isPrivate" size="small">
        设为隐私歌单
      </n-checkbox>
      <div class="flex justify-end gap-3">
        <n-button @click="show = false">
          取消
        </n-button>
        <n-button type="primary" :loading="btnLoading" :disabled="!title.trim()" @click="handleCreate">
          创建
        </n-button>
      </div>
    </div>
  </n-modal>
</template>

<script lang="ts">
export type CreatePlaylistModalExpose = { show: () => void; };
</script>
