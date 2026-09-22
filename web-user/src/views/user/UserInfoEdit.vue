<template>
  <div class=" px-10 pt-2">
    <h2>编辑个人信息</h2>

    <n-alert v-if="!isProfileLoaded" type="warning" class="my-3" style="max-width:640px">
      未能读取到账号资料（登录可能已过期），请重新登录后再修改。
    </n-alert>
    <p class="mb-2 text-xs opacity-60">提示：目前可保存「昵称」与「介绍」，性别 / 生日 / 地区暂未开放。</p>

    <div class="edit-layout">
      <n-form ref="formRef" :model="model" :rules="rules" label-placement="left" label-width="auto"
        require-mark-placement="right-hanging" class="edit-form">
        <n-form-item label="昵称：" required path="nickname">
          <!-- 与后端 @Size(max=50) 及数据库 varchar(50) 保持一致 -->
          <n-input v-model:value="model.nickname" placeholder="请输入昵称" :maxlength="50" show-count clearable />
        </n-form-item>
        <n-form-item label="介绍：" path="textareaValue">
          <n-input v-model:value="model.signature" type="textarea" :autosize="{
            minRows: 3,
            maxRows: 5
          }" />
        </n-form-item>

        <n-form-item label="性别：" path="radioGroupValue">
          <n-radio-group v-model:value="model.gender" name="radiogroup1">
            <n-space>
              <n-radio :value="0">
                保密
              </n-radio>
              <n-radio :value="1">
                男
              </n-radio>
              <n-radio :value="2">
                女
              </n-radio>
            </n-space>
          </n-radio-group>
        </n-form-item>
        <n-form-item label="生日：" path="selectValue">
          <div class="triple-grid">
            <n-select v-model:value="birthday.year" :options="yearOption" />
            <n-select v-model:value="birthday.month" :options="monthOption" />
            <n-select v-model:value="birthday.day" :options="dayOption" />
          </div>
        </n-form-item>
        <n-form-item label="地区：" path="selectValue">
          <div class="double-grid">
            <n-select v-model:value="model.province" :options="provinceOption" />
            <n-select v-model:value="model.city" :options="cityOption" />
          </div>
        </n-form-item>
        <n-form-item label="提交：" class="submitFormItem">
          <div style="display: flex; justify-content: flex-end">
            <n-button size="large" round type="primary" :loading="btnSaveLoading" :disabled="btnSaveDisabled"
              @click="handleValidateButtonClick">
              保存
            </n-button>
            <n-button size="large" class="ml-4" round @click="router.back()">
              取消
            </n-button>
          </div>
        </n-form-item>
      </n-form>

      <!-- 头像列：hover 出现"替换图片"遮罩，点击直接选文件 -->
      <div class="avatar-col">
        <div class="avatar-wrap" title="点击替换头像" @click="handleAvatarButtonClick">
          <img v-if="avatarUrl" crossorigin="anonymous" class="avatar-img" :src="avatarUrl" />
          <div v-else class="avatar-placeholder">
            暂无头像
          </div>
          <div class="avatar-mask" :class="{ uploading: updateAvatarLoading }">
            <n-icon :size="26" :component="CameraOutline" />
            <span>{{ updateAvatarLoading ? '上传中...' : '替换图片' }}</span>
          </div>
        </div>
        <input ref="fileRef" type="file" accept="image/*" class="hidden-input" @change="handleFileChange">
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, toRaw, watch } from 'vue';
import { CameraOutline } from '@vicons/ionicons5';
import { compareObject, generalTimeOptions, getDayOptions, getImgSize } from '@/utils';
import type { FormInst } from 'naive-ui';
import { useRouter } from 'vue-router';
import regionList from '@/assets/js/region.json';
import { useMainStore } from '@/stores/main';
import { updateUserAvatar, updateUserInfo } from '@/service';
import { compressToSquare, validateImageFile } from '@/utils/imageCompress';

const rules = {
  nickname: {
    required: true,
    trigger: ['blur', 'input'],
    message: '请输入昵称'

  }
};
const indexMap = new Map();
const initProvinceIndex = () => {
  regionList.forEach((val, index) => {
    if (val.provinceList) {
      val.provinceList.forEach((item) => {
        indexMap.set(Object.values(item)[0], index);
      });
    }
    indexMap.set(val.value, index);
  });
};

initProvinceIndex();

let nowYear = new Date().getFullYear();
const yearOption = generalTimeOptions(
  1920, nowYear, '年'
);
const monthOption = generalTimeOptions(
  1, 12, '月'
);
let isInit = false;
const formRef = ref<FormInst | null>(null);
const mainStore = useMainStore();
const btnSaveLoading = ref(false);
const btnSaveDisabled = ref(true);
const updateAvatarLoading = ref(false);
const fileRef = ref<HTMLInputElement>();
const profile = mainStore.userProfile?.profile;
const avatarUrl = ref(profile?.avatarUrl);
// 资料是否已加载：未登录 / 登录态刚过期时 profile 为 undefined，页面需降级显示而非崩溃
const isProfileLoaded = computed(() => !!profile);

const model = ref<{
  nickname: string,
  signature: string,
  gender: number;
  birthday: number | string,
  province: number | string,
  city: number | string,
}>({
  nickname: profile?.nickname || '',
  signature: profile?.signature || '',
  gender: profile?.gender ?? 0,
  birthday: '',
  province: '',
  city: ''
});
let rawModel: { nickname: string; signature: string; gender: number; birthday: string | number; province: string | number; city: string | number; };

const birthday = ref<{
  year: string | number,
  month: string | number,
  day: string | number
}>({
  year: '',
  month: '',
  day: ''
});
const dayOption = ref(getDayOptions(2));
const provinceOption = ref(regionList);
const cityOption = ref(regionList[0].cityList);
const router = useRouter();

watch(() => model.value.province, (val) => {
  let index = indexMap.get(val);
  let target = regionList[index];
  cityOption.value = target.cityList;
  model.value.city = target.cityList[0].value;
});
watch(() => birthday.value.year, (val) => {
  if (isInit) return;
  birthday.value.month = 1;
  dayOption.value = getDayOptions(1, +val);
  birthday.value.day = 1;
  changeModelBirthday();
});
watch(() => birthday.value.month, (val) => {
  if (isInit) return;
  dayOption.value = getDayOptions(+val);
  birthday.value.day = 1;
  changeModelBirthday();
});
watch(
  model, (val) => {
    // 对比是否发生了变化
    if (rawModel) {
      btnSaveDisabled.value = compareObject(toRaw(val), rawModel);
    }
  }, { deep: true }
);
const initData = () => {
  // profile 缺失（未登录 / 资料未加载）时直接返回并留空表单：
  // 原实现在此处访问 profile.province 等字段，一旦缺少数据就抛 TypeError，
  // 会让整个 Vue 应用崩溃（表现为"点进来空白、返回后也全空"）。
  if (!profile) {
    rawModel = JSON.parse(JSON.stringify(toRaw(model.value)));
    return;
  }
  isInit = true;
  // 无生日数据时用今天占位，避免 Invalid Date 产出 NaN
  const date = profile.birthday ? new Date(profile.birthday) : new Date();
  birthday.value = {
    year: date.getFullYear(),
    month: date.getMonth() + 1,
    day: date.getDate()
  };
  model.value.birthday = profile.birthday || '';
  // 地区回显：优先旧字段 province/city，否则从组合 region（"省 市"）拆分；未匹配到时保持默认选项
  const regionParts = (profile.region || '').split(' ').filter(Boolean);
  const regionProvince = profile.province || regionParts[0] || '';
  const province = regionList[indexMap.get(regionProvince)];
  if (province) {
    model.value.province = province.value;
    cityOption.value = province.cityList;
  }
  const regionCity = profile.city || regionParts[1] || '';
  if (regionCity) {
    model.value.city = regionCity;
  }
  rawModel = JSON.parse(JSON.stringify(toRaw(model.value)));
  isInit = false;
};
const changeModelBirthday = () => {
  // 将日期转为时间戳
  let date = new Date(
    +birthday.value.year,
    +birthday.value.month - 1,
    +birthday.value.day
  );
  let timestamp = date.getTime();
  model.value.birthday = timestamp;
};
initData();

const handleValidateButtonClick = (e: MouseEvent) => {
  e.preventDefault();
  formRef.value?.validate((errors: any) => {
    if (errors) {
      window.$message.error('验证失败');
      return;
    }
    // 昵称 / 签名 / 地区可提交（ProfileUpdateDTO 支持 region）；性别 / 生日后端暂无字段
    const params = {
      nickname: model.value.nickname,
      signature: model.value.signature,
      region: `${model.value.province} ${model.value.city}`.trim()
    };
    btnSaveLoading.value = true;
    updateUserInfo(params).then((res) => {
      const body = res.data;
      if (body?.code === 200) {
        window.$message.success('修改个人资料成功!');
        // 保存成功更新原始数据（用于"未修改时禁用保存"的判断）
        rawModel = JSON.parse(JSON.stringify(toRaw(model.value)));
        // 同步全局登录态：页头昵称即时刷新
        if (mainStore.userProfile?.profile) {
          mainStore.userProfile.profile.nickname = params.nickname;
          mainStore.userProfile.profile.signature = params.signature;
          localStorage.userProfile = JSON.stringify(toRaw(mainStore.userProfile));
        }
        btnSaveDisabled.value = true;
      } else {
        window.$message.error(body?.message || '保存失败');
      }
      btnSaveLoading.value = false;
    }).catch((err: any) => {
      window.$message.error(err?.message || '保存失败');
      btnSaveLoading.value = false;
    });
  });
};
// 点击修改头像
const handleAvatarButtonClick = () => {
  fileRef.value?.click();
};
// 选择文件回调：先在浏览器端裁剪压缩为正方形，再上传
const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  // 及时清空，保证同一文件再次选择也能触发
  input.value = '';
  if (!file) return;

  const invalid = validateImageFile(file);
  if (invalid) {
    window.$message.error(invalid);
    return;
  }

  updateAvatarLoading.value = true;
  try {
    const compressed = await compressToSquare(file, 512, 0.9);
    const res = await updateUserAvatar(compressed);
    const body = res.data;
    if (body?.code === 200) {
      const rawUrl: string = body?.data || '';
      // 加时间戳破缓存：头像接口地址固定，换图后必须让浏览器重新拉取
      // （后端已改为 no-cache 协商缓存，这里再加一层，保证本地即时可见）
      const newUrl = rawUrl
        ? `${rawUrl}${rawUrl.includes('?') ? '&' : '?'}v=${Date.now()}`
        : '';
      // 同步本地预览与全局登录态中的头像，页头头像即时刷新
      avatarUrl.value = newUrl;
      if (mainStore.userProfile?.profile) {
        mainStore.userProfile.profile.avatarUrl = newUrl;
        localStorage.userProfile = JSON.stringify(toRaw(mainStore.userProfile));
      }
      window.$message.success('头像已更新');
    } else {
      window.$message.error(body?.message || '头像上传失败');
    }
  } catch (e: any) {
    window.$message.error(e?.message || '头像上传失败');
  } finally {
    updateAvatarLoading.value = false;
  }
};
</script>
<style scoped>
:deep(.submitFormItem>.n-form-item-label) {
  opacity: 0;
}

/* ===== 方案B 双列布局：左表单 + 右头像；窄屏自动上下切换 ===== */
.edit-layout {
  display: flex;
  align-items: flex-start;
  gap: 48px;
}

/* 表单列：占满剩余空间但有最小宽度保护，任何窗口宽度下都不塌 */
.edit-form {
  flex: 1;
  min-width: 0;
  max-width: 640px;
}

/* 生日三列 / 地区两列：grid 均分，子项不会被压成细条 */
.triple-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  width: 100%;
}

.double-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  width: 66%;
}

/* 头像列：固定宽度不参与挤压 */
.avatar-col {
  flex-shrink: 0;
  padding-top: 8px;
}

.avatar-wrap {
  position: relative;
  width: 180px;
  height: 180px;
  /* border-box：加装饰环后总尺寸不变，不影响布局 */
  box-sizing: border-box;
  border-radius: 50%;
  overflow: hidden;
  cursor: pointer;
  background: rgba(128, 128, 128, 0.12);
  /* 官方风格：头像外圈深色装饰环 + 轻投影（告别光秃秃的圆） */
  border: 9px solid #1f1f22;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.16);
}

/* 深色模式下黑环换浅灰环：规则写在 App.vue 全局样式
   （scoped 内 :global(html.dark) 写法实测不生效） */

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  opacity: 0.6;
}

/* hover 遮罩："替换图片"入口（上传中常显） */
.avatar-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  font-size: 13px;
  opacity: 0;
  transition: opacity 0.2s ease;
}

.avatar-mask.uploading {
  opacity: 1;
}

.avatar-wrap:hover .avatar-mask {
  opacity: 1;
}

.hidden-input {
  display: none;
}

/* 窄窗口（半屏/手机竖屏）：头像移到顶部居中，栅格降为单列 */
@media (max-width: 900px) {
  .edit-layout {
    flex-direction: column-reverse;
    gap: 24px;
  }

  .avatar-col {
    align-self: center;
  }

  .edit-form {
    max-width: none;
    width: 100%;
  }

  .triple-grid {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .double-grid {
    width: 100%;
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
</style>