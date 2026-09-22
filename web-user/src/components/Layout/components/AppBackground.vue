<script setup lang="ts">
import { computed, onUnmounted, watchEffect } from 'vue';
import { useMainStore } from '@/stores/main';

/**
 * 自定义背景层
 * <p>
 * 固定定位铺满视口，位于页面内容之下；上层叠加可调强度的遮罩，
 * 保证歌单名、菜单等文字在图片上依然可读。
 */
const mainStore = useMainStore();

const hasBackground = computed(() => !!mainStore.appBgImage);

const bgStyle = computed(() => ({
  backgroundImage: mainStore.appBgImage ? `url(${mainStore.appBgImage})` : 'none',
}));

// 有背景时给 html 打标记：App.vue 中据此把 Naive UI 的面板设为透明/毛玻璃
watchEffect(() => {
  document.documentElement.classList.toggle('has-app-bg', hasBackground.value);
});

// 组件卸载时移除标记，避免全局样式残留
onUnmounted(() => {
  document.documentElement.classList.remove('has-app-bg');
});
</script>

<template>
  <div v-if="hasBackground" class="app-bg" :style="bgStyle">
    <div class="app-bg-mask" :style="{ opacity: mainStore.appBgMask / 100 }"></div>
  </div>
</template>

<style scoped>
.app-bg {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.app-bg-mask {
  position: absolute;
  inset: 0;
  background: #ffffff;
  transition: opacity 0.2s ease;
}

/* 深色模式遮罩色（#101014）写在 App.vue 全局样式中
   （scoped 内 :global(html.dark) 写法实测不生效） */
</style>
