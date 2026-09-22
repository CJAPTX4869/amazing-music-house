<script setup lang="ts">
import { ref } from 'vue';
import { useMainStore } from '@/stores/main';
const showModal = ref(false);
const commentBtnLoading = ref(false);
const commentContent = ref('');
const props = withDefaults(defineProps<{
  commentPlaceholder?: string;
  title: string;
  updateCommentList?: (value: any) => void;
  t?: number;
  type?: number;
  resourceId?: number;
}>(), { t: 1, type: 0 });

const mainStore = useMainStore();

defineExpose({
  show() {
    if (!mainStore.isLogin) {
      window.$message.error('请先登录');
      return;
    }
    showModal.value = true;
  },
  close() {
    showModal.value = false;
  }
});
</script>
<template>
  <n-modal v-model:show="showModal" :mask-closable="false" transform-origin="center">
    <n-card style="width: 400px" :bordered="false" size="small" preset="dialog" aria-modal="true">
      <template #header>
        <p class="text-center">{{ title }}</p>
      </template>
      <n-input v-model:value="commentContent" type="textarea" :placeholder="commentPlaceholder || '发表评论'" :maxlength="140" :show-count="true" />
      <div class="flex justify-end mt-5">
        <n-button round :loading="commentBtnLoading" @click="window.$message.info('评论功能暂未开放')">
          评论
        </n-button>
      </div>
    </n-card>
  </n-modal>
</template>
