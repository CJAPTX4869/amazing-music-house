<script setup lang="ts">
import { useMainStore } from '@/stores/main';
import { Moon, PersonAdd, SunnySharp } from '@vicons/ionicons5';
import { ArrowForwardIosRound } from '@vicons/material';
import { UserProfile } from '@vicons/carbon';
import { ExitToAppRound } from '@vicons/material';
import { computed, onMounted, ref, watch } from 'vue';
import { onClickOutside } from '@vueuse/core';
import { getLoginStatus, getPendingRequests, getUserInfo, logout } from '@/service';
import type { AnyObject } from 'env';
import { useRouter } from 'vue-router';
import obverser from '@/utils/obverser';
import AppBackgroundSetting from '@/components/Layout/components/AppBackgroundSetting.vue';
import FollowRequestModal from '@/components/common/FollowRequestModal.vue';
import FriendListModal from '@/components/common/FriendListModal.vue';

const TOKEN_KEY = 'music_token';
let mainStore = useMainStore();
const router = useRouter();
const popoverContainerRef = ref();
const userDetail = ref<AnyObject>();
const showUserPopover = ref(false);
// 站名艺术字图片（放在 public/image.png）
// 注意：必须用 :src 动态绑定，写死 src="/image.png" 会被 Vite 当作模块解析并报错
const artImgUrl = '/image.png';
// 图片加载失败时自动回退为文字艺术字
const showArtImg = ref(true);

onMounted(() => {
  // 令牌失效：同步重置内存中的登录态（localStorage 已由 request 拦截器清理）
  obverser.on('authExpired', () => {
    mainStore.isLogin = false;
    mainStore.userProfile = {};
    mainStore.removeAllLikeList();
  });
  // 修复：刷新页面时 isLogin 初值即为 true，下方 watch 不会触发 → userDetail 始终为空，
  // 导致页头用户菜单点击无反应（模板里 @click 依赖 userDetail 才展开）
  if (mainStore.isLogin) {
    getUserProfile();
  }
});

// 监听登录状态 获取用户信息
watch(() => mainStore.isLogin, (val) => {
  if (val) {
    getUserProfile();
    loadPendingCount();
  } else {
    pendingFollowCount.value = 0;
  }
});

/* ---------- 好友请求（V3.1） ---------- */
const pendingFollowCount = ref(0);
// 修复：FollowRequestModal 通过 defineExpose 暴露的是 open()，此前调用方写的是 show()，
// 会以 "undefined is not a function" 抛错触发全局异常提示
const followModalRef = ref<{ open: () => void }>();
// 我的好友列表弹窗（与好友请求同一套写法）
const friendListRef = ref<{ open: () => void }>();
const openFriendList = () => friendListRef.value?.open();
const openFollowRequests = () => followModalRef.value?.open();
const loadPendingCount = async () => {
  try {
    const res = await getPendingRequests();
    if (res.data.code === 200) {
      pendingFollowCount.value = res.data.data.length;
    }
  } catch {
    // 忽略：未登录或网络异常时徽标保持 0
  }
};
// 未登录时不请求：/users/follow/requests 需要登录态，匿名请求会被拦截器
// 判为 401 并弹「未认证或登录已过期」，导致访客一打开页面就看到报错
onMounted(() => {
  if (mainStore.isLogin) {
    loadPendingCount();
  }
});
onClickOutside(popoverContainerRef, (event: MouseEvent) => {
  let target = event.target as HTMLElement;
  if (!target.classList.contains('trigger')) {
    showUserPopover.value = false;
  }
});
/* ---------- 管理端入口已移除：管理功能统一在管理端（5174 music-web） ---------- */

// 获取用户账号数据
const getUserProfile = () => {
  getUserInfo().then(res => {
    if (res?.data?.code === 200) {
      mainStore.userProfile = res.data;
      localStorage.userProfile = JSON.stringify(res.data);
      userDetail.value = res.data;
    }
  });
};
const checkLoginStatus = () => {
  getLoginStatus().then(res => {
    if (res.data?.data?.profile) {
      // logged in
    } else {
      window.$message.warning('登录状态过期,请重新登录');
      mainStore.userProfile = {};
      mainStore.isLogin = false;
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem('isLogin');
      localStorage.removeItem('userProfile');
    }
  });
};
const handlePositiveClick = () => {
  window.$message.loading('退出登录中...', { duration: 0 });
  logout().then(res => {
    if (res.data.code === 200) {
      mainStore.isLogin = false;
      mainStore.userProfile = {};
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem('isLogin');
      localStorage.removeItem('userProfile');
      localStorage.removeItem('searchHistory');
      mainStore.removeAllLikeList();
      window.$message.destroyAll();
      window.$message.success('退出登录成功!');
    }
  });
};
const handleInfoEditClick = () => {
  showUserPopover.value = false;
  router.push('/userInfoEdit');
};
const handleThemeSwitchUpdateChange = () => {
  mainStore.toggleTheme();
};
const BackToDiscovery = () => {
  router.push('/discovery');
};
if (mainStore.isLogin) {
  getUserProfile();
  checkLoginStatus();
}
</script>
<template>
  <n-layout-header bordered class="flex justify-between items-center px-4 h-14 sm:px-3">
    <div class="flex items-center">
      <!-- 艺术字图片优先（public/image.png），加载失败回退到文字艺术字 -->
      <img
        v-if="showArtImg"
        class="brand-img cursor-pointer select-none"
        :src="artImgUrl"
        alt="奇妙音乐屋"
        @click="BackToDiscovery"
        @error="showArtImg = false"
      />
      <span v-else class="brand-art cursor-pointer select-none" @click="BackToDiscovery">奇妙音乐屋</span>
      <layout-header-search />
    </div>
    <div class="flex items-center">
      <!-- 用户信息入口 -->
      <div v-if="mainStore.isLogin">
        <div v-if="mainStore.userProfile" class="flex items-center mr-2">
          <n-avatar :img-props="{ crossorigin: 'anonymous' }" round :size="30"
            :src="mainStore.userProfile?.profile?.avatarUrl" />
          <n-popover :show="showUserPopover" trigger="click" style="padding:0" display-directive="show">
            <template #trigger>
              <p class="pl-2 text-xs opacity-80 hover:opacity-100 cursor-pointer trigger" @click="() => (userDetail && (showUserPopover = !showUserPopover))">
                <!-- 长昵称（最长 50 字）截断显示，hover 气泡查看完整昵称 -->
                <n-ellipsis style="max-width: 150px">{{ mainStore.userProfile?.profile?.nickname }}</n-ellipsis>
              </p>
            </template>
            <div ref="popoverContainerRef" style="width:300px">
              <div
                class="hover:bg-neutral-200/20 border-0 border-b border-gray-200 dark:border-gray-200/20 border-solid">
                <!-- 好友请求（V3.1） -->
                <div class="flex justify-between items-center py-2 px-4 cursor-pointer" @click="openFollowRequests">
                  <div class="flex items-center text-base">
                    <n-icon :size="20" :component="PersonAdd" />
                    <span class="ml-2">好友请求</span>
                    <n-badge v-if="pendingFollowCount" :value="pendingFollowCount" class="ml-2" />
                  </div>
                  <n-icon :component="ArrowForwardIosRound" />
                </div>
              </div>
              <div
                class="hover:bg-neutral-200/20 border-0 border-b border-gray-200 dark:border-gray-200/20 border-solid">
                <!-- 我的好友列表（V3.1 阶段 D 补全） -->
                <div class="flex justify-between items-center py-2 px-4 cursor-pointer" @click="openFriendList">
                  <div class="flex items-center text-base">
                    <n-icon :size="20" :component="TeamOutlined" />
                    <span class="ml-2">我的好友</span>
                  </div>
                  <n-icon :component="ArrowForwardIosRound" />
                </div>
              </div>
              <div
                class="hover:bg-neutral-200/20 border-0 border-b border-gray-200 dark:border-gray-200/20 border-solid">
                <!-- 个人信息设置 -->
                <div class="flex justify-between items-center py-2 px-4 cursor-pointer" @click="handleInfoEditClick">
                  <div class="flex items-center text-base">
                    <n-icon :size="20" :component="UserProfile" />
                    <span class="ml-2">个人信息设置</span>
                  </div>
                  <n-icon :component="ArrowForwardIosRound" />
                </div>
              </div>
              <div
                class="hover:bg-neutral-200/20 border-0 border-b border-gray-200 dark:border-gray-200/20 border-solid">
                <n-popconfirm @positive-click="handlePositiveClick">
                  <template #trigger>
                    <div class="flex justify-between items-center py-2 px-4 cursor-pointer">
                      <div class="flex items-center text-base">
                        <n-icon :size="20" :component="ExitToAppRound" />
                        <span class="ml-2">退出登录</span>
                      </div>
                      <n-icon :component="ArrowForwardIosRound" />
                    </div>
                  </template>
                  确认退出登录吗?
                </n-popconfirm>
              </div>
            </div>
          </n-popover>
        </div>
        <!-- 用户详情加载骨架屏 -->
        <div v-else class="flex items-center mr-2">
          <n-skeleton width="30px" height="30px" round />
          <n-skeleton text style="width:100px;margin-left: 8px;" />
        </div>
      </div>
      <!-- 好友请求弹窗 -->
      <follow-request-modal ref="followModalRef" @changed="loadPendingCount" />
      <friend-list-modal ref="friendListRef" />
      <!-- 自定义背景设置 -->
      <app-background-setting class="mr-1" />
      <div class="flex items-center ml-2">
        <n-switch :value="mainStore.isActiveDarkTheme" size="large" :on-update:value="handleThemeSwitchUpdateChange">
          <template #checked-icon>
            <n-icon :component="Moon" />
          </template>
          <template #unchecked-icon>
            <n-icon :component="SunnySharp" />
          </template>
        </n-switch>
      </div>
    </div>
  </n-layout-header>
</template>
<style scoped>
/* 注意：Vue 3 的深度选择器是 :deep()；旧代码写的 ::deep() 是 Vue 2 语法，
   在 Vue 3 中不会被识别，导致这里的 padding 覆盖一直失效（菜单项外多出一圈留白）。 */
:deep(.n-popover:not(.n-popover--raw):not(.n-popover--show-header)) {
  padding: 0 !important;
}

/* 站名艺术字图片（public/image.png）：白底用 multiply 混合去掉 */
.brand-img {
  height: 34px;
  width: auto;
  max-width: 240px;
  object-fit: contain;
  mix-blend-mode: multiply;
}

/* 深色模式：图片反色后黑底用 screen 混合去掉，保留白色字迹
   注意：`html.dark` 版本写在 App.vue 全局样式中（scoped 内 :global(html.dark) 写法实测不生效） */

/* 站名艺术字（图片缺失时的文字兜底）：渐变填充 + 轻微投影
   渐变色阶基于 --primary（App.vue 注入）用 color-mix 派生，跟随主题换色 */
.brand-art {
  font-family: 'Georgia', 'STZhongsong', 'Songti SC', 'SimSun', serif;
  font-size: 19px;
  font-weight: 900;
  letter-spacing: 1.5px;
  white-space: nowrap;
  /* 兜底：老浏览器不支持 color-mix 时整条渐变会失效，而 color 是 transparent，
     会导致站名"隐身"。先声明纯色，支持 color-mix 的浏览器再用渐变覆盖它 */
  background: var(--primary);
  background: linear-gradient(95deg,
    color-mix(in srgb, var(--primary) 72%, #00351b) 0%,
    var(--primary) 28%,
    color-mix(in srgb, var(--primary) 62%, white) 50%,
    var(--primary) 72%,
    color-mix(in srgb, var(--primary) 72%, #00351b) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 1px 2px color-mix(in srgb, var(--primary) 35%, transparent));
  transition: filter 0.2s ease;
}

.brand-art:hover {
  filter: drop-shadow(0 1px 3px color-mix(in srgb, var(--primary) 60%, transparent)) brightness(1.08);
}

/* 深色模式的调亮渐变同样写在 App.vue 全局样式中 */
</style>
