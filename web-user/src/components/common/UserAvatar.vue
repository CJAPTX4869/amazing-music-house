<script setup lang="ts">
import { NAvatar } from 'naive-ui';
import { useRouter } from 'vue-router';

/**
 * 可点击头像：点击跳转该用户的个人主页（/user/:id）
 * 头像为空时 n-avatar 自动降级为默认占位图
 */
const props = withDefaults(defineProps<{
  id: number;
  nickname?: string;
  avatarUrl?: string | null;
  size?: number;
}>(), {
  size: 36,
  nickname: '',
  avatarUrl: null
});

const router = useRouter();

function go() {
  if (props.id) {
    router.push(`/user/${props.id}`);
  }
}
</script>

<template>
  <n-avatar
    round
    :size="size"
    :src="avatarUrl || undefined"
    style="cursor: pointer"
    :title="nickname || '查看主页'"
    @click="go"
  />
</template>
