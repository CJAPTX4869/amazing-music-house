<script setup lang="ts">
import { ThumbsUp, ThumbsUpFilled } from '@vicons/carbon';
import { computed, ref } from 'vue';
export interface CommentListProps {
  list: any[],
  title: string;
  resourceId: number;
  commentTotalNum?: string | number;
  type?: number;
}
const props = withDefaults(defineProps<CommentListProps>(), { type: 1, commentTotalNum: 0 });
const currentClickedComment = ref<any>();
const emit = defineEmits(['updateCommentList', 'updateCommentLiked']);

const commentPlaceholder = computed(() => {
  return currentClickedComment.value && '回复: ' + currentClickedComment.value.user.nickname;
});
</script>
<template>
  <div v-if="list.length">
    <p class="text-xl pb-5">{{ title }}</p>
    <div v-for="(item, index) in list" :key="index" class="flex py-5">
      <n-avatar round :size="40" :src="item.user.avatarUrl" />
      <div class="ml-4 flex-1">
        <p class="text-sm text-left opacity-50">{{ item.user.nickname }}</p>
        <p class="mt-2 text-left">{{ item.content }}</p>
        <div class="flex items-center mt-2 text-sm opacity-50">
          <span>{{ item.timeStr }}</span>
          <span class="ml-4 cursor-pointer">
            <n-icon :component="item.liked ? ThumbsUpFilled : ThumbsUp" class="mr-1" />
            {{ item.likedCount }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>
