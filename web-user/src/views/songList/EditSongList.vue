<script setup lang="ts">
import type { SelectSongListTagModalExpose } from '@/components/SongsList/SelectSongListTagModal.vue';
import { updatePlayListInfo, updatePlaylistCover } from '@/service';
import service from '@/service/request';
import { compressToSquare, validateImageFile } from '@/utils/imageCompress';
import { getImgSize } from '@/utils';
import type { FormInst, FormRules } from 'naive-ui';
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

const route = useRoute();
const router = useRouter();
const query = route.query as {
  tags: string;
  songListName: string;
  desc: string;
  coverUrl: string;
};
const desc = ref(query.desc || '');
const selectSongListTagRef = ref<SelectSongListTagModalExpose>();
const formRef = ref<FormInst | null>();
const saveBtnLoading = ref(false);
const coverUploading = ref(false);
/** 特例：「我喜欢的音乐」是系统歌单，封面由收藏歌曲自动继承，不提供手动修改入口 */
const isFavoritePlaylist = computed(() => String(query.name || '') === '我喜欢的音乐');
const fileRef = ref<HTMLInputElement>();
const coverUrl = ref(query.coverUrl);
const modelRef = ref({ songListName: query.songListName });
const selectTags = ref(query.tags
  ? query.tags.split(',')
  : []);

const rules: FormRules = {
  songListName: {
    required: true,
    message: '表单名不能为空',
    trigger: ['input', 'blur']
  }
};
const handleCompleteClick = (tags: any[]) => {
  if (tags.length === 0) {
    return window.$message.warning('请选择标签');
  }
  selectTags.value = tags.map(item => item.name);
  return selectSongListTagRef.value?.close();
};
const handleSaveClick = () => {
  let params = {
    id: route.params.id as string,
    name: modelRef.value.songListName,
    tags: selectTags.value.join(';'),
    desc: desc.value
  };
  formRef.value?.validate((errors) => {
    if (!errors) {
      saveBtnLoading.value = true;
      updatePlayListInfo(params).then(res => {
        if (res.data.code === 200) {
          window.$message.success('保存成功!');
          saveBtnLoading.value = false;
        }
      });
    }
  });
};
const handleChangCoverClick = () => {
  fileRef.value?.click();
};
// 选择封面文件：浏览器端裁剪压缩为正方形后上传（与头像上传同一套工具链）
const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 及时清空，保证同一文件再次选择也能触发 change
  input.value = '';
  if (!file) return;

  const invalid = validateImageFile(file);
  if (invalid) {
    window.$message.error(invalid);
    return;
  }

  coverUploading.value = true;
  try {
    const compressed = await compressToSquare(file, 512, 0.9);
    const res = await updatePlaylistCover(route.params.id as string, compressed);
    const body = res.data;
    if (body?.code === 200) {
      const apiPath: string = body?.data?.coverUrl || '';
      // 接口返回的是相对 API 路径，需拼上服务地址才能被 <img> 直接加载；
      // 再加时间戳破缓存：封面接口地址按歌单 id 固定，换图后必须让浏览器重新拉取
      const base = service.defaults.baseURL || '';
      coverUrl.value = apiPath
        ? `${base}${apiPath}${apiPath.includes('?') ? '&' : '?'}v=${Date.now()}`
        : '';
      window.$message.success('封面已更新');
    } else {
      window.$message.error(body?.message || '封面更新失败');
    }
  } catch (e: any) {
    window.$message.error(e?.message || '封面更新失败');
  } finally {
    coverUploading.value = false;
  }
};
</script>

<template>
  <div class="p-8">
    <h2>编辑歌单信息</h2>
    <div class="flex">
      <n-form ref="formRef" :rules="rules" :model="modelRef" label-placement="left" label-width="auto"
        require-mark-placement="right-hanging" :style="{
          width: '640px'
        }">
        <n-form-item label="歌单名：" path="songListName">
          <n-input v-model:value="modelRef.songListName" placeholder="请输入歌单名" />
        </n-form-item>
        <n-form-item label="标签：">
          <n-space>
            <n-tag v-for="(item, index) in selectTags" :key="index" round size="small">
              {{ item }}
            </n-tag>
            <n-button text @click="selectSongListTagRef?.show()">
              添加标签
            </n-button>
          </n-space>
        </n-form-item>
        <n-form-item label="简介：">
          <n-input v-model:value="desc" maxlength="1000" type="textarea" :show-count="true" placeholder="请输入歌单简介" />
        </n-form-item>
        <n-form-item label="提交：" class="submitFormItem">
          <n-space>
            <n-button :loading="saveBtnLoading" round size="medium" type="primary" @click="handleSaveClick">
              保存
            </n-button>
            <n-button round size="medium" @click="router.back()">
              取消
            </n-button>
          </n-space>
        </n-form-item>
      </n-form>
      <div class="flex flex-col items-center ml-40">
        <!-- 歌单封面可能为空串：空 src 会请求当前页面导致破图，故空值时渲染占位块 -->
        <img v-if="coverUrl" width="200" class="rounded-md" crossorigin="anonymous" :src="coverUrl" />
        <div v-else
          class="flex items-center justify-center rounded-md text-sm opacity-60"
          style="width:200px;height:200px;background:rgba(128,128,128,.12);">
          暂无封面
        </div>
        <n-button v-if="!isFavoritePlaylist" class="mt-8" :loading="coverUploading" @click="handleChangCoverClick">
          {{ coverUploading ? '上传中...' : '修改封面' }}
        </n-button>
        <div v-else class="mt-8 text-sm text-gray-400">封面由收藏的歌曲自动决定</div>
      <input v-if="!isFavoritePlaylist" id="file" ref="fileRef" class="hidden-input" type="file" accept="image/*" @change="handleFileChange">
      </div>
    </div>
  </div>
  <!-- 标签选择弹窗 -->
  <select-song-list-tag-modal ref="selectSongListTagRef" :handle-complete-click="handleCompleteClick" />
</template>
<style scoped>
:deep(.submitFormItem>.n-form-item-label) {
  opacity: 0;
}

/* 文件选择框隐藏（原 opacity-0 仍占位，改为不参与布局） */
.hidden-input {
  display: none;
}
</style>