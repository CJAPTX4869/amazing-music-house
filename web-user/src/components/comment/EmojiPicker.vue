<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { NButton, NIcon, NPopover, NSpin } from 'naive-ui';
import { HappyOutline } from '@vicons/ionicons5';
import service from '@/service/request';

/**
 * 评论表情选择器（服务端统一管理的图片表情）
 * 点选后向父组件 emit 占位符文本（如 [爱心]），由父组件拼进输入框
 */
const emit = defineEmits<{ (e: 'pick', placeholder: string): void }>();

const show = ref(false);
const loading = ref(false);
const list = ref<{ id: number; name: string; imageUrl: string }[]>([]);
// 图片地址为相对 API 路径，需拼服务地址（与评论页图片同规则）
// 修复：baseURL 已改为运行时配置（见 src/config.ts），相对路径由 request.ts 统一补全\nconst base = '';

onMounted(async () => {
  if (list.value.length) return;
  loading.value = true;
  try {
    const res = await service.get('/emojis');
    if (res.data.code === 200) {
      list.value = res.data.data;
    }
  } finally {
    loading.value = false;
  }
});

function pick(item: { name: string }) {
  emit('pick', `[${item.name}]`);
  show.value = false;
}
</script>

<template>
  <n-popover trigger="click" v-model:show="show" placement="bottom-start" :width="312">
    <template #trigger>
      <n-button quaternary circle size="small" title="表情">
        <n-icon :component="HappyOutline" :size="18" />
      </n-button>
    </template>
    <n-spin :show="loading">
      <div class="emoji-grid">
        <div
          v-for="item in list"
          :key="item.id"
          class="emoji-cell"
          :title="item.name"
          @click="pick(item)"
        >
          <img :src="item.imageUrl" :alt="item.name" loading="lazy" />
        </div>
      </div>
    </n-spin>
  </n-popover>
</template>

<style scoped>
.emoji-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  max-height: 264px;
  overflow: auto;
}

.emoji-cell {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.15s ease;
}

.emoji-cell:hover {
  background: rgba(128, 128, 128, 0.18);
}

.emoji-cell img {
  width: 28px;
  height: 28px;
  display: block;
}
</style>
