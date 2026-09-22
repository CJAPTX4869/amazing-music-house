<script setup lang="tsx">
import { registerRouteHook } from '@/router';
import { getLikeList, getUserPlaylist } from '@/service';
import { useMainStore } from '@/stores/main';
import { BackToTop, Music, User } from '@vicons/carbon';
import { QueueMusicFilled } from '@vicons/material';
import { Add, List, SparklesOutline, StarOutline, Heart } from '@vicons/ionicons5';
import { NIcon, useLoadingBar } from 'naive-ui';
import { computed, onMounted, ref, watch, type CSSProperties, type VNodeChild, KeepAlive } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import LoginModal, { type LoginModalExpose } from './LoginModal.vue';
import CreatePlaylistModal, { type CreatePlaylistModalExpose } from '@/components/common/CreatePlaylistModal.vue';
import obverser from '@/utils/obverser';

const mainStore = useMainStore();
type MySongsList = { myCreatePlayList: any[], collectPlayList: any[] };
interface childrenMenuOptionItem extends MenuOptionItem {
  id: number;
}
type MenuOptionItem = {
  label: (() => VNodeChild) | string,
  key: string,
  icon?: () => VNodeChild,
  /** 菜单项右侧的附加内容（如"我创建的歌单"的 + 按钮） */
  extra?: () => VNodeChild,
  children?: childrenMenuOptionItem[]
}
const noLoginOption = {
  label: () => <div class="flex items-center" onClick={handleOpenLoginModalClick}>
    <p >未登录</p>
  </div>,
  key: 'login',
  icon: () => <NIcon class="mr-2" size={20} component={User} />
};

const menuOptions = [
  {
    label: () => <RouterLink to='/discovery'>发现音乐</RouterLink>,
    key: '/discovery',
    icon: () => <NIcon component={SparklesOutline} />
  },
  {
    label: () => <RouterLink to='/latestMusic'>最新音乐</RouterLink>,
    key: '/latestMusic',
    icon: () => <NIcon component={Music} />
  },
  {
    label: () => <RouterLink to='/songList'>推荐歌单</RouterLink>,
    key: '/songList',
    icon: () => <NIcon component={List} />
  }
];

const route = useRoute();
const router = useRouter();
const loadingBar = useLoadingBar();

let collapsed = ref(false);
let scrollContainer: HTMLElement | null;
let activeKey = ref<string | null>('');
let hiddenLeftMenu = ref(false);
const myMenuOptions = ref<MenuOptionItem[]>(menuOptions);
const loginModalRef = ref<LoginModalExpose>();
const createPlaylistModalRef = ref<CreatePlaylistModalExpose>();
// 最近一次的歌单数据缓存：新建/收藏/取消收藏后据此重建菜单，保证子项与数量显示同步
let latestCreate: any[] = [];
let latestCollect: any[] = [];
const mainStyle = computed<CSSProperties>(() => {
  return {
    height: route.meta.hidden
      ? '100%'
      : 'calc(100vh - 69px - 56px)'
  };
});
const changeMenuOption = (myCreatePlayList: any[] = latestCreate, collectPlayList: any[] = latestCollect) => {
  // 浅拷贝解耦：myCreatePlayList 与 store.mySubscribeSongList 常为同一个数组引用，
  // 若直接持有引用，"创建弹窗 push + 本组件 splice"会双写同一数组导致歌单重复出现
  latestCreate = [...myCreatePlayList];
  latestCollect = [...collectPlayList];
  if (!mainStore.isLogin) {
    myMenuOptions.value = [noLoginOption, ...menuOptions];
  } else {
    // 接口返回的歌单列表第 0 项为"我喜欢的音乐"（见 classifySongsList），
    // 单独提为顶级菜单项（与"发现音乐/最新音乐/推荐歌单"平级），其余为自建歌单
    const [likeItem, ...myCreateOnly] = myCreatePlayList;
    myMenuOptions.value = [
      ...(likeItem
        ? [{
          label: () => <span onClick={() => handlePlayListItemClick(likeItem)}>我喜欢的音乐</span>,
          key: `/songList/${likeItem.id}`,
          icon: () => <NIcon size={20} component={Heart} />
        }]
        : []),
      {
        label: () => (
          <span class="menu-label-row">
            我创建的歌单
            {myCreateOnly.length > 0 && <span class="menu-count">{myCreateOnly.length}</span>}
            {/* 圆形 + 按钮：放到 label 内推到行右侧（naive-ui 的 extra 槽位布局会掉到第二行，弃用） */}
            <span
              class="create-playlist-btn"
              title="创建歌单"
              onClick={(e: MouseEvent) => {
                // 阻止冒泡：点 + 不应触发展开/收起分组
                e.stopPropagation();
                createPlaylistModalRef.value?.show();
              }}
            >
              <NIcon size={14} component={Add} />
            </span>
          </span>
        ),
        key: 'create',
        icon: () => <NIcon class="mr-2" size={20} component={User} />,
        children: myCreateOnly.map((item: any) => {
          return {
            label: () => <span onClick={() => handlePlayListItemClick(item)}>{item.name}</span>,
            key: item.name,
            icon: () => <NIcon size={20} component={QueueMusicFilled}></NIcon>,
            id: item.id
          };
        })
      },
      {
        label: () => (
          <span class="flex items-center">
            收藏的歌单
            {collectPlayList.length > 0 && <span class="menu-count">{collectPlayList.length}</span>}
          </span>
        ),
        key: 'collect',
        icon: () => <NIcon component={StarOutline} />,
        children: collectPlayList.map((item: any) => {
          return {
            label: () => <span onClick={() => handlePlayListItemClick(item)}>{item.name}</span>,
            key: item.name,
            icon: () => <NIcon size={20} component={QueueMusicFilled}></NIcon>,
            id: item.id
          };
        })
      },
      ...menuOptions
    ];

  }
};
const handleOpenLoginModalClick = () => {
  loginModalRef.value?.show();
};
const handlePlayListItemClick = (item: any) => {
  router.push(`/songList/${item.id}`);
};
watch(() => route.path, (newVal) => {
  activeKey.value = newVal;
  if (route.meta.hidden) {
    hiddenLeftMenu.value = true;
  } else {
    hiddenLeftMenu.value = false;
  }
});
watch(() => mainStore.userProfile, (val) => {
  let userId = mainStore.userProfile?.profile?.userId;
  if (val && userId) {
    // myMenuOptions.value.shift();
    fetchUserPlaylist(userId);
    fetchMyLikeMusicList(userId);
  } else {
    changeMenuOption();
  }
});
if (!mainStore.isLogin) {
  changeMenuOption();
}

const fetchUserPlaylist = (userId: number) => {
  window.$message.loading('加载用户歌单中...');
  if (myMenuOptions.value[0].key === 'login') {
    myMenuOptions.value.shift();
  }
  getUserPlaylist(userId).then((res) => {
    // 将歌单分类
    if (res.data.code === 200) {
      let { collectPlayList, myCreatePlayList } = classifySongsList(userId, res.data.playlist);
      mainStore.setMySubscribeSongList(myCreatePlayList);
      changeMenuOption(myCreatePlayList, collectPlayList);

    }
  })
    .finally(() => {
      window.$message.destroyAll();
    });
};
// 获取我喜欢的音乐
const fetchMyLikeMusicList = (userId: number) => {
  getLikeList(userId).then(res => {
    mainStore.setLikeList(res.data.ids);
  });
};
const classifySongsList = (userId: number, playList: any[]) => {
  return playList.reduce((
    prev, currentValue, index
  ) => {
    if (index === 0) currentValue.name = '我喜欢的音乐';
    if (currentValue.creator.userId === userId) {
      prev.myCreatePlayList.push(currentValue);
    } else {
      prev.collectPlayList.push(currentValue);
    }
    return prev;
  }, { myCreatePlayList: [], collectPlayList: [] }) as MySongsList;
};
registerRouteHook((to) => {
  scrollContainer?.scrollTo({
    behavior: 'smooth',
    top: 0
  });
  if (to.meta.auth && !mainStore.isLogin) {
    window.$message.error('请先登录');
    return false;
  } else {
    loadingBar.start();
    return true;
  }

}, () => {
  loadingBar.finish();
});
//监听歌单收藏状态
const watchUpdateCollectPlayList = () => {
  obverser.on('updateCollectPlayList', (data: any) => {
    let { subscribed } = data;
    // 收藏 添加歌单
    if (subscribed) {
      latestCollect.unshift(data.songListDetail);
    } else { //取消收藏. 删除歌单
      let id = data.id;
      let index = latestCollect.findIndex((item: any) => item.id === +id);
      // 修复原 bug：index === 0 时旧写法 if (index) 为假，导致第一个收藏歌单删不掉
      if (index > -1) {
        latestCollect.splice(index, 1);
      }
    }
    // 数据变更后重建菜单（子项 + 数量显示同步刷新）
    changeMenuOption();
  });
};
const watchUpdateMyCreatePlayList = () => {
  obverser.on('updateMyCreatePlayList', (data: any) => {
    // 插入到"我喜欢的音乐"（index 0，classifySongsList 保证）之后、
    // 其他自建歌单之前——不能 unshift：否则新歌单会顶替 index 0 的"我喜欢"位置
    latestCreate.splice(1, 0, data);
    changeMenuOption();
  });
};
onMounted(() => {
  scrollContainer = document.querySelector('.rightMain>.n-layout-scroll-container');
  watchUpdateCollectPlayList();
  watchUpdateMyCreatePlayList();
});
</script>
<template>
  <n-layout has-sider>
    <n-layout-sider v-show="!hiddenLeftMenu" bordered collapse-mode="width" :collapsed-width="64" :width="192"
      :style="mainStyle" :collapsed="collapsed" show-trigger @collapse="collapsed = true" @expand="collapsed = false">
      <n-menu v-model:value="activeKey" :collapsed="collapsed" :collapsed-width="64" :collapsed-icon-size="22"
        :options="myMenuOptions" :default-expand-all="true" />
    </n-layout-sider>
    <login-modal ref="loginModalRef" />
    <create-playlist-modal ref="createPlaylistModalRef" />
    <n-back-top :right="mainStore.backTopLeft" :bottom="220" :visibility-height="800">
      <n-icon :component="BackToTop" />
    </n-back-top>
    <n-layout :style="mainStyle">
      <!-- 修复：原实现把 keep-alive 套在 div 上（缓存无效）+ key 用 route.name
           （同路由换参数不重建，点另一个歌单像没反应），且进出场动画让每次跳转都"卡一下"。
           现在：key 用 fullPath（不同歌单各自缓存与重建）、keep-alive 套在组件上并限量、
           过渡交给 CSS（时长已在 transition.css 缩短）。 -->
      <router-view v-slot="{ Component, route }">
        <transition name="fade-transform" mode="out-in">
          <keep-alive :max="8">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </transition>
      </router-view>
    </n-layout>
  </n-layout>
</template>
<style lang="less" scoped>
.n-layout-sider {
  transition: color 0.3s var(--n-bezier), border-color 0.3s var(--n-bezier),
    min-width 0.3s var(--n-bezier), max-width 0.3s var(--n-bezier), width 0.3s var(--n-bezier),
    transform 0.3s var(--n-bezier), background-color 0.3s var(--n-bezier);
}

:deep(.n-submenu-children > .n-menu-item > .n-menu-item-content) {
  padding-left: 40px !important;
}

/* 分组名后的数量（如"我创建的歌单 2"） */
:deep(.menu-count) {
  margin-left: 4px;
  font-size: 12px;
  opacity: 0.5;
}

/* "我创建的歌单"右侧 + 按钮：默认隐藏，悬停分组行浮现 */
/* 菜单项是 CSS Grid 布局（grid-area: content），文字区窄，
   + 按钮放进文字流会溢出压住折叠箭头 —— 改为绝对定位钉在行右侧 */
:deep(.n-menu-item-content) {
  position: relative;
}

/* 行容器：文字 + 数量自然排列即可 */
:deep(.menu-label-row) {
  display: inline-flex;
  align-items: center;
  min-width: 0;
}

/* + 圆形按钮（官方风：浅灰圆底 + 居中加号）：默认隐藏，悬停分组行浮现 */
:deep(.create-playlist-btn) {
  position: absolute;
  top: 50%;
  /* 行右界约 42px 处，正好落在折叠箭头左侧并留 8px 间距 */
  right: 10px;   /* 贴到行右上角边缘（原来 42px 在折叠箭头左侧） */
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  z-index: 6;   /* 压住行悬停高亮与折叠箭头 */
  /* 底色加深 + 细描边：保证在 hover 高亮行上清晰可辨 */
  background: rgba(128, 128, 128, 0.45);
  box-shadow: 0 0 0 1px rgba(128, 128, 128, 0.18);
  /* 常驻显示：原来是 opacity:0 悬停才浮现，视觉上像被高亮行遮挡 */
  opacity: 1;
  transition: opacity 0.2s ease, background-color 0.2s ease;
  cursor: pointer;
}

:deep(.n-menu-item-content:hover .create-playlist-btn) {
  opacity: 1;
}

/* 悬停"我创建的歌单"行时隐藏折叠箭头：+ 按钮悬停出现在箭头左侧，
   不同窗口宽度/显示缩放下两者可能视觉重叠，用 :has 限定仅此行生效 */
:deep(.n-menu-item-content:has(.create-playlist-btn) .n-menu-item-content__arrow) {
  opacity: 0;
}

:deep(.n-menu-item-content .n-menu-item-content__arrow) {
  transition: opacity 0.2s ease;
}

:deep(.create-playlist-btn:hover) {
  background: rgba(128, 128, 128, 0.5);
}

/* 菜单收起成图标条时隐藏 + 按钮（避免压在图标上） */
:deep(.n-menu--collapsed .create-playlist-btn) {
  display: none;
}
</style>
