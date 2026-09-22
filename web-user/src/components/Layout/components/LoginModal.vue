<template>
  <n-modal v-model:show="showModal" :mask-closable="false" transform-origin="center">
    <n-card
      style="width: 350px;"
      :bordered="false"
      size="small"
      preset="dialog"
      aria-modal="true"
    >
      <template #header>
        <div class="flex justify-end ">
          <n-icon
            class="cursor-pointer" size="25" :component="CloseOutline"
            @click="showModal = false"
          />
        </div>
      </template>
      <div class="flex flex-col items-center">
        <p class="mt-5 text-3xl">
          {{ isRegister ? '注册账号' : '账号登录' }}
        </p>
        <n-form ref="formRef" :model="formData" :rules="rules" class="mt-6 w-full px-4">
          <n-form-item path="username" label="用户名">
            <n-input
              v-model:value="formData.username"
              placeholder="4-20 位字母、数字或下划线"
              @keyup.enter="handleSubmit"
            />
          </n-form-item>
          <n-form-item path="password" label="密码">
            <n-input
              v-model:value="formData.password"
              type="password"
              show-password-on="click"
              placeholder="6-20 位"
              @keyup.enter="handleSubmit"
            />
          </n-form-item>
          <template v-if="isRegister">
            <n-form-item path="confirmPassword" label="确认密码">
              <n-input
                v-model:value="formData.confirmPassword"
                type="password"
                show-password-on="click"
                placeholder="再次输入密码"
                @keyup.enter="handleSubmit"
              />
            </n-form-item>
            <n-form-item path="nickname" label="昵称（可选）">
              <!-- 与后端 @Size(max=50) 保持一致 -->
              <n-input
                v-model:value="formData.nickname"
                placeholder="默认与用户名相同（最多 50 字）"
                :maxlength="50"
                show-count
                @keyup.enter="handleSubmit"
              />
            </n-form-item>
          </template>
          <n-button
            type="primary"
            block
            strong
            :loading="isLoading"
            @click="handleSubmit"
          >
            {{ isRegister ? '注册并登录' : '登录' }}
          </n-button>
        </n-form>
        <div class="mt-4 mb-2 text-sm">
          <span class="opacity-60">{{ isRegister ? '已有账号？' : '还没有账号？' }}</span>
          <span class="ml-1 cursor-pointer text-sky-500" @click="toggleMode">
            {{ isRegister ? '去登录' : '去注册' }}
          </span>
        </div>
      </div>
    </n-card>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { CloseOutline } from '@vicons/ionicons5';
import { login, register } from '@/service';
import { useMainStore } from '@/stores/main';

const TOKEN_KEY = 'music_token';
const USERNAME_PATTERN = /^[a-zA-Z0-9_]{4,20}$/;

export interface LoginModalExpose {
  show: () => void;
  close: () => void;
}

const showModal = ref(false);
const isLoading = ref(false);
const isRegister = ref(false);
const mainStore = useMainStore();

const formRef = ref();
const formData = ref({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
});

// 规则与后端 RegisterDTO 保持一致：用户名 4-20 位字母数字下划线，密码 6-20 位
const rules = computed(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: ['blur', 'input'] },
    ...(isRegister.value
      ? [{ pattern: USERNAME_PATTERN, message: '用户名需为 4-20 位字母、数字或下划线', trigger: ['blur', 'input'] }]
      : []),
  ],
  password: [
    { required: true, message: '请输入密码', trigger: ['blur', 'input'] },
    ...(isRegister.value
      ? [{ min: 6, max: 20, message: '密码长度需为 6-20 位', trigger: ['blur', 'input'] }]
      : []),
  ],
  confirmPassword: isRegister.value
    ? [
        {
          required: true,
          validator: (_rule: any, value: string) => {
            if (!value) {
              return new Error('请再次输入密码');
            }
            if (value !== formData.value.password) {
              return new Error('两次输入的密码不一致');
            }
            return true;
          },
          trigger: ['blur', 'input'],
        },
      ]
    : [],
}));

defineExpose({
  show() {
    showModal.value = true;
  },
  close() {
    showModal.value = false;
  }
});

const toggleMode = () => {
  isRegister.value = !isRegister.value;
  formData.value.confirmPassword = '';
  formData.value.nickname = '';
  formRef.value?.restoreValidation();
};

// 保存登录态（登录与注册后自动登录共用）
const saveLoginState = (data: any) => {
  localStorage.setItem(TOKEN_KEY, data.token);
  localStorage.isLogin = 'true';
  mainStore.isLogin = true;
  // 与其他组件保持一致：userProfile 结构为 { profile: {...} }
  if (data.profile) {
    const userProfile = { code: 200, profile: data.profile };
    mainStore.userProfile = userProfile;
    localStorage.userProfile = JSON.stringify(userProfile);
  }
};

const resetForm = () => {
  formData.value.username = '';
  formData.value.password = '';
  formData.value.confirmPassword = '';
  formData.value.nickname = '';
  // 关闭弹窗后回到登录模式，避免下次打开仍停留在注册态
  isRegister.value = false;
  formRef.value?.restoreValidation();
};

const handleSubmit = async () => {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  isLoading.value = true;
  try {
    if (isRegister.value) {
      // 1) 注册
      const regRes = await register(
        formData.value.username,
        formData.value.password,
        formData.value.nickname || undefined
      );
      if (regRes.data.code !== 200) {
        window.$message.error(regRes.data.message || '注册失败');
        return;
      }
      // 2) 注册成功 → 用同一账号立即登录，免去二次输入
      const loginRes = await login(formData.value.username, formData.value.password);
      if (loginRes.data.code === 200) {
        saveLoginState(loginRes.data);
        window.$message.success('注册成功，已自动登录');
        showModal.value = false;
        resetForm();
      } else {
        // 注册成功但自动登录失败：切回登录模式预填用户名
        window.$message.warning('注册成功，请手动登录');
        isRegister.value = false;
        formData.value.password = '';
        formData.value.confirmPassword = '';
        formData.value.nickname = '';
      }
      return;
    }

    const res = await login(formData.value.username, formData.value.password);
    if (res.data.code === 200) {
      saveLoginState(res.data);
      window.$message.success('登录成功');
      showModal.value = false;
      resetForm();
    } else {
      window.$message.error(res.data.message || '登录失败');
    }
  } catch (error: any) {
    // 校验类错误（HTTP 4xx）由后端返回 message
    window.$message.error(error?.response?.data?.message || '操作失败，请稍后重试');
  } finally {
    isLoading.value = false;
  }
};
</script>
<style scoped>
</style>
