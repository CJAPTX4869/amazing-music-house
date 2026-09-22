<script setup lang="ts">
import { useMainStore } from '@/stores/main';
import { useElementHover } from '@vueuse/core';
import { isString } from 'lodash';
import { useThemeVars } from 'naive-ui';
import { computed, nextTick } from 'vue';
import { onMounted, onUnmounted, ref } from 'vue';
const props = withDefaults(defineProps<{
  modelValue: number;
  loadValue?: number;
  width?: number | string;
  height?: number;

}>(), { width: 500, height: 4, loadValue: 0 });
let isTargetClick = ref(false);
let isWidthString = isString(props.width);
const mainStore = useMainStore();
let width = isWidthString ? 0 : props.width as number;
let startWidth = width * (props.modelValue / 100);

const mousePosition = { y: 0, x: 0 };// 鼠标坐标 
const moveDiff = { x: 0, y: 0 };// 移动距离
const themeVars = useThemeVars();
const target = ref();
const isHover = useElementHover(target);
const emit = defineEmits(['update:modelValue', 'change', 'onDone']);

/* ---------- 悬停/拖动时间气泡 ---------- */
// 悬停位置百分比（0-100）
const hoverPercent = ref(0);
// 总时长（秒）
const durationSec = computed(() => Math.round((mainStore.currentPlaySong?.dt || 0) / 1000));
const formatTime = (totalSeconds: number) => {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
};
// 拖动中显示拖动位置，否则显示悬停位置
const bubblePercent = computed(() => (isTargetClick.value ? props.modelValue : hoverPercent.value));
const bubbleTimeText = computed(() => {
  if (!durationSec.value) return '';
  const t = Math.round(durationSec.value * bubblePercent.value / 100);
  return `${formatTime(t)} / ${formatTime(durationSec.value)}`;
});
const handleBarMouseMove = (e: MouseEvent) => {
  const el = target.value as HTMLElement;
  if (!el) return;
  const rect = el.getBoundingClientRect();
  const w = rect.width || width;
  hoverPercent.value = Math.min(100, Math.max(0, Math.round(((e.clientX - rect.left) / w) * 100)));
};


const calcPercentage = (value: number) => {
  return Math.round((value / width) * 100);
};
const handleSliderMouseDown = async (e: MouseEvent) => {
  let eventTarget = e.target as HTMLElement;
  if (eventTarget.classList.contains('dot') && isTargetClick) return;
  if (isTargetClick.value) return;
  let { offsetX } = e;
  if (width === 0) {
    await nextTick();
    let targetEle = target.value as HTMLElement;
    if (isWidthString) {
      width = targetEle.clientWidth;
    }
  }
  let percentage = calcPercentage(offsetX);
  emit('update:modelValue', percentage);
  startWidth = getProgressWidth(percentage);
  emit('change');
  emit('onDone');
};
// 在小圆点下按下鼠标
const handleDotMouseDown = (e: MouseEvent) => {
  isTargetClick.value = true;
  mousePosition.x = e.clientX;
  moveDiff.x = 0;
};
// 移动鼠标
const handleMouseMove = (e: MouseEvent) => {
  if (!isTargetClick.value) return;
  moveDiff.x += e.clientX - mousePosition.x;
  if (moveDiff.x > 0) {
    moveDiff.x = Math.min(width, moveDiff.x);
  } else {
    moveDiff.x = Math.max(-width, moveDiff.x);
  }
  let value;
  if (e.clientX > mousePosition.x) {
    value = calcPercentage(startWidth + moveDiff.x);
  } else {
    value = calcPercentage(startWidth + moveDiff.x);
  }
  if (value >= 0 && value <= 100 && value !== props.modelValue) {
    emit('update:modelValue', value);
    emit('change', value);
  }
  mousePosition.x = e.clientX;
};

// 鼠标抬起
const handleMouseUp = (e: MouseEvent) => {
  if (!isTargetClick.value) return;
  isTargetClick.value = false;
  startWidth = getProgressWidth(props.modelValue);
  emit('change');
  emit('onDone');
};
const handleResize = () => {
  let targetEle = target.value as HTMLElement;
  width = targetEle.clientWidth;
  startWidth = width * (props.modelValue / 100);
}
const getProgressWidth = (percentage: number) => width * (percentage / 100);
onMounted(() => {
  window.addEventListener('mousemove', handleMouseMove);
  window.addEventListener('mouseup', handleMouseUp);
  let targetEle = target.value as HTMLElement;
  if (isWidthString) {
    width = targetEle.clientWidth;
    startWidth = width * (props.modelValue / 100);
  }
  window.addEventListener('resize', handleResize)
});
onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove);
  window.removeEventListener('mouseup', handleMouseUp);
  window.removeEventListener('resize', handleResize)
});

</script>

<template>
  <div ref="target" class="group py-1 no-select" @mousedown="handleSliderMouseDown" @mousemove="handleBarMouseMove">
    <div
      :style="{ width: isString(props.width) ? props.width : width + 'px', height: isString(height) ? height : height + 'px', transform: `scaleY(${isTargetClick || isHover ? '1.5' : '1'})` }"
      class="relative bg-gray-200 dark:bg-gray-200/50 rounded-md transition-transform">
      <!-- 加载进度条 -->
      <div class="absolute top-0 h-full bg-gray-300 dark:bg-gray-300/50 rounded-md transition-all"
        :style="{ width: loadValue + '%' }" />
      <!-- 播放进度条 -->
      <div class="absolute top-0 h-full rounded-md  transition-all"
        :style="{ background: mainStore.showMusicDetail ? `linear-gradient(to right,${themeVars.bodyColor}, ${themeVars.primaryColor})` : `${themeVars.primaryColor}`, width: modelValue + '%' }" />
      <!-- 小圆点 -->
      <div v-show="isHover || isTargetClick" class="dot"
        :style="{ 'background-color': themeVars.primaryColor, left: modelValue + '%' }"
        @mousedown="handleDotMouseDown" />
      <!-- 悬停 / 拖动时间气泡（当前位置时间 / 总时长） -->
      <div v-show="(isHover || isTargetClick) && bubbleTimeText" class="time-bubble"
        :style="{ left: bubblePercent + '%' }">
        {{ bubbleTimeText }}
      </div>
      <div />
    </div>
  </div>
</template>

<style scoped>
.dot {
  position: absolute;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  top: -2px;
  transform: scaleX(1.5);
  cursor: pointer;
}

/* 时间气泡（深色圆角，悬浮在进度条上方，跟随鼠标/拖动位置） */
.time-bubble {
  position: absolute;
  bottom: 14px;
  transform: translateX(-50%);
  background: #000;
  color: #fff;
  font-size: 12px;
  line-height: 1;
  padding: 5px 8px;
  border-radius: 6px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0.88;
  z-index: 10;
}
</style>
