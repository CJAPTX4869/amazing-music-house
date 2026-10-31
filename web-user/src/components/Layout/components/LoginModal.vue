<template>
  <n-modal v-model:show="showModal" :mask-closable="false" transform-origin="center">
    <n-card
      style="width: 470px; max-width: 94vw; max-height: 86vh; overflow-y: auto;"
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
      <!-- 第一步：账密表单（提交后进入第二步验证，验证码不再与表单同屏） -->
      <div v-if="step === 'form'" class="flex flex-col items-center">
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

      <!-- 第二步：点过登录/注册后弹出的抓娃娃验证（组件自带标题，与表单互斥只显示一个） -->
      <div v-else class="flex flex-col items-center pb-2">
        <div class="w-full px-2">
          <ClawCaptcha @verify="onCaptchaVerified" />
        </div>
        <n-button quaternary size="small" class="mt-1" @click="backToForm">
          返回修改账号信息
        </n-button>
      </div>
    </n-card>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { CloseOutline } from '@vicons/ionicons5';
import { login, register, captchaChallenge, captchaVerify } from '@/service';
import { useMainStore } from '@/stores/main';
import ClawCaptcha from '@/components/clawcaptcha/ClawCaptcha.vue';

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

// 两步式登录：step=form 填账密，step=captcha 弹出抓娃娃验证（账密对错此时还不知道，
// 服务端在验证通过后才判账密）。抓错=验证失败（组件不 emit verify，继续抓），抓对=自动提交。
const step = ref<'form' | 'captcha'>('form');
const captchaVerified = ref(false);
const captchaTicket = ref('');
async function fetchTicket() {
  try {
    const r = await captchaChallenge();
    captchaTicket.value = r.data?.data?.ticket || '';
  } catch {
    captchaTicket.value = '';
    window.$message?.error?.('获取验证票据失败，请检查服务端');
  }
}
async function onCaptchaVerified() {
  try {
    await captchaVerify(captchaTicket.value);
  } catch {
    window.$message?.error?.('验证票据确认失败，请重试');
    return;
  }
  // 票据已确认：抓对了就算验证通过，自动提交（账密错由后端返回明确提示）
  captchaVerified.value = true;
  await handleSubmit(true);
}
function backToForm() {
  step.value = 'form';
  captchaVerified.value = false;
}

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
    step.value = 'form';
    captchaVerified.value = false;
    fetchTicket();
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

const handleSubmit = async (fromCaptcha?: boolean) => {
  try {
    await formRef.value?.validate();
  } catch {
    return;
  }
  // 自动提交（验证通过回调）直接放行；手动点击则先进入验证步骤
  if (fromCaptcha !== true) {
    step.value = 'captcha';
    fetchTicket();
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
      const loginRes = await login(formData.value.username, formData.value.password, captchaTicket.value);
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
        step.value = 'form';
        captchaVerified.value = false;
      }
      return;
    }

    const res = await login(formData.value.username, formData.value.password, captchaTicket.value);
    if (res.data.code === 200) {
      saveLoginState(res.data);
      window.$message.success('登录成功');
      showModal.value = false;
      resetForm();
    } else {
      // 账密错误：验证已通过但登录失败，回表单并重置验证（票据已消费）
      window.$message.error(res.data.message || '用户名或密码错误');
      step.value = 'form';
      captchaVerified.value = false;
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
