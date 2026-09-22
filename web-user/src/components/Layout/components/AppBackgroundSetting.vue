<script setup lang="ts">
import { ref } from 'vue';
import { Checkmark, ImageOutline } from '@vicons/ionicons5';
import { useMainStore } from '@/stores/main';
import { compressForBackground, validateImageFile, validateImageUrl } from '@/utils/imageCompress';

/**
 * 外观设置入口（页头图标 + 设置弹窗）
 * <p>
 * Tab 1「主题颜色」：预设色板 + 自定义取色器，实时切换全局主题主色；
 * Tab 2「自定义背景」：本地图片 / 网络地址 / 遮罩强度。
 */
const mainStore = useMainStore();
const show = defineModel<boolean>('show', { default: false });

/** 预设主题色板（第一个为默认经典绿） */
const PRESET_COLORS = [
  { name: '经典绿', value: '#18a058' },
  { name: '墨黑', value: '#1f1f1f' },
  { name: '樱粉', value: '#f25d8e' },
  { name: '浅粉', value: '#ff8fb3' },
  { name: '葡萄紫', value: '#8a7cf0' },
  { name: '晴空蓝', value: '#4a7cf0' },
  { name: '浅海蓝', value: '#41b3e0' },
  { name: '黄绿', value: '#7ec832' },
  { name: '柠黄', value: '#f0c02e' },
  { name: '活力橙', value: '#f58a3c' },
  { name: '珊瑚红', value: '#f0655a' },
  { name: '正红', value: '#e03e3e' }
];

const isPresetActive = (value: string) =>
  mainStore.primaryColor.toLowerCase() === value.toLowerCase();

const handlePickPreset = (value: string) => {
  mainStore.setPrimaryColor(value);
};

const handlePickCustom = (event: Event) => {
  const value = (event.target as HTMLInputElement).value;
  if (value) {
    mainStore.setPrimaryColor(value);
  }
};

const handleResetColor = () => {
  mainStore.resetPrimaryColor();
  window.$message?.success('已恢复默认主题色');
};

/* ---------- 自定义背景（原有逻辑） ---------- */
const fileRef = ref<HTMLInputElement>();
const urlInput = ref('');
const fileLoading = ref(false);
const urlLoading = ref(false);

const handleOpenFileDialog = () => {
  fileRef.value?.click();
};

const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 及时清空，保证选择同一文件也能再次触发 change
  input.value = '';
  if (!file) return;

  const invalid = validateImageFile(file);
  if (invalid) {
    window.$message?.error(invalid);
    return;
  }

  fileLoading.value = true;
  try {
    const dataUrl = await compressForBackground(file);
    mainStore.setAppBackground(dataUrl);
    window.$message?.success('背景已应用');
  } catch (e: any) {
    window.$message?.error(e?.message || '背景处理失败');
  } finally {
    fileLoading.value = false;
  }
};

const handleApplyUrl = async () => {
  const url = urlInput.value.trim();
  if (!url) {
    window.$message?.warning('请输入图片地址');
    return;
  }
  urlLoading.value = true;
  try {
    await validateImageUrl(url);
    mainStore.setAppBackground(url);
    window.$message?.success('背景已应用');
  } catch (e: any) {
    window.$message?.error(e?.message || '图片地址无效');
  } finally {
    urlLoading.value = false;
  }
};

const handleClear = () => {
  mainStore.clearAppBackground();
  window.$message?.success('已恢复默认背景');
};

const handleMaskChange = (value: number) => {
  mainStore.setAppBgMask(value);
};
</script>

<template>
  <div class="flex items-center">
    <n-button quaternary circle title="外观设置" @click="show = true">
      <template #icon>
        <n-icon :component="ImageOutline" />
      </template>
    </n-button>

    <n-modal v-model:show="show" preset="card" title="外观设置" style="width: 520px">
      <n-tabs type="segment" animated default-value="color">
        <!-- ========== Tab 1：主题颜色（调色盘） ========== -->
        <n-tab-pane name="color" tab="主题颜色">
          <div class="flex flex-col gap-4 pt-2">
            <div class="color-grid">
              <button
                v-for="preset in PRESET_COLORS"
                :key="preset.value"
                class="color-swatch"
                :class="{ active: isPresetActive(preset.value) }"
                :style="{ backgroundColor: preset.value }"
                :title="preset.name"
                @click="handlePickPreset(preset.value)"
              >
                <n-icon v-if="isPresetActive(preset.value)" class="check-icon" :component="Checkmark" />
              </button>
            </div>

            <div class="flex items-center gap-3">
              <span class="shrink-0 text-sm">自定义颜色</span>
              <label class="custom-color-chip" :style="{ backgroundColor: mainStore.primaryColor }">
                <input type="color" :value="mainStore.primaryColor" @input="handlePickCustom" />
              </label>
              <span class="text-sm opacity-60">{{ mainStore.primaryColor }}</span>
              <n-button size="small" quaternary class="ml-auto" @click="handleResetColor">
                恢复默认绿
              </n-button>
            </div>

            <p class="text-xs opacity-60">换色即时生效：按钮、开关、菜单高亮、艺术字等全局跟随。</p>
          </div>
        </n-tab-pane>

        <!-- ========== Tab 2：自定义背景 ========== -->
        <n-tab-pane name="background" tab="自定义背景">
          <div class="flex flex-col gap-4 pt-2">
            <!-- 预览 -->
            <div class="preview">
              <div
                v-if="mainStore.appBgImage"
                class="preview-img"
                :style="{ backgroundImage: `url(${mainStore.appBgImage})` }"
              ></div>
              <span v-else class="preview-empty">尚未设置背景</span>
            </div>

            <!-- 遮罩强度 -->
            <div class="flex items-center gap-3">
              <span class="shrink-0 text-sm">遮罩强度</span>
              <n-slider
                class="flex-1"
                :value="mainStore.appBgMask"
                :min="0"
                :max="100"
                :step="1"
                :on-update:value="handleMaskChange"
              />
              <span class="w-10 text-right text-sm">{{ mainStore.appBgMask }}%</span>
            </div>
            <p class="text-xs opacity-60">强度越高背景越淡、文字越清晰，推荐 60% 以上</p>

            <!-- 操作 -->
            <div class="flex gap-3">
              <n-button type="primary" :loading="fileLoading" @click="handleOpenFileDialog">
                导入本地图片
              </n-button>
              <n-button :disabled="!mainStore.appBgImage" @click="handleClear">恢复默认</n-button>
              <input ref="fileRef" type="file" accept="image/*" class="hidden" @change="handleFileChange" />
            </div>

            <!-- 网络图片 -->
            <div>
              <p class="mb-2 text-sm">或使用网络图片地址：</p>
              <div class="flex gap-3">
                <n-input v-model:value="urlInput" placeholder="https://example.com/background.jpg" />
                <n-button :loading="urlLoading" @click="handleApplyUrl">应用</n-button>
              </div>
            </div>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-modal>
  </div>
</template>

<style scoped>
/* 预设色板网格：6 列自适应 */
.color-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 12px;
}

.color-swatch {
  position: relative;
  aspect-ratio: 1 / 1;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.color-swatch:hover {
  transform: scale(1.08);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.color-swatch.active {
  box-shadow: 0 0 0 3px rgba(128, 128, 128, 0.35);
}

.check-icon {
  position: absolute;
  inset: 0;
  margin: auto;
  width: 20px;
  height: 20px;
  color: #fff;
  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.5));
}

/* 自定义取色器：把原生 color input 装进当前色圆片 */
.custom-color-chip {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  border: 1px dashed rgba(128, 128, 128, 0.6);
  display: inline-block;
}

.custom-color-chip input[type='color'] {
  position: absolute;
  inset: -8px;
  width: calc(100% + 16px);
  height: calc(100% + 16px);
  opacity: 0;
  cursor: pointer;
}

.preview {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 150px;
  overflow: hidden;
  border: 1px solid rgba(128, 128, 128, 0.25);
  border-radius: 8px;
}

.preview-img {
  width: 100%;
  height: 100%;
  background-position: center;
  background-size: cover;
}

.preview-empty {
  font-size: 13px;
  opacity: 0.5;
}
</style>
