<script setup lang="ts">
import { useThemeVars } from 'naive-ui';
import { computed, ref, toRaw } from 'vue';
export interface SelectSongListTagModalExpose {
  show: () => void;
  close: () => void;
  selectTagList: any[]
}
const props = withDefaults(defineProps<{
  btnLoading?: boolean;
  handleCompleteClick: (selectTagList: any[]) => void;
}>(), { btnLoading: false });

defineExpose({
  show() {
    showSelectTagModal.value = true;
  },
  close() {
    showSelectTagModal.value = false;
  }
});

const showSelectTagModal = ref(false);
const selectTagList = ref<any[]>([]);
const themeVars = useThemeVars();
const primaryColor = computed(() => themeVars.value.primaryColor);
// 固定标签列表（无外部数据源）
const tagList = ref<any[]>([
  { name: '流行', checked: false },
  { name: '摇滚', checked: false },
  { name: '民谣', checked: false },
  { name: '电子', checked: false },
  { name: '说唱', checked: false },
  { name: '轻音乐', checked: false },
  { name: '古典', checked: false },
  { name: '爵士', checked: false },
]);

const handleTagClick = (item: { checked: boolean, name: string }, index: number) => {
  if (selectTagList.value.length === 3 && !item.checked) {
    window.$message.warning('最多可选三个标签');
    return;
  }
  if (!item.checked) {
    selectTagList.value.push(item);
  } else {
    let removeIndex = selectTagList.value.findIndex(val => val.name === item.name);
    if (removeIndex !== -1) {
      selectTagList.value.splice(removeIndex, 1);
    }
  }
  item.checked = !item.checked;
};
const handleCompleteOnClick = () => {
  props.handleCompleteClick(toRaw(selectTagList.value));
};
</script>

<template>
  <!-- 标签选择弹窗 -->
  <n-modal v-model:show="showSelectTagModal" transform-origin="center">
    <n-card
      style="width: 600px"
      title="添加标签"
      :bordered="false"
      size="medium"
      role="dialog"
      aria-modal="true"
    >
      <span>
        <span class="opacity-60"> 选择合适的标签, 最多可选</span>
        <span :style="{color:primaryColor}" class="px-1 text-base">3</span>
        <span class="opacity-60">个</span>
      </span>
      <div class="flex flex-wrap mt-3">
        <n-space size="large">
          <n-tag
            v-for="(item, index) in tagList" :key="item.name" round
            checkable :checked="item.checked"
            @click="handleTagClick(item, index)"
          >
            {{ item.name }}
          </n-tag>
        </n-space>
        <div class="flex justify-center items-center mt-4 w-full">
          <n-button
            :loading="btnLoading" size="medium" type="primary"
            @click="handleCompleteOnClick"
          >
            完成
          </n-button>
        </div>
      </div>
    </n-card>
  </n-modal>
</template>
