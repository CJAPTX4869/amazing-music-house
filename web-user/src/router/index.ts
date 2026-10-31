import { createRouter, createWebHashHistory, type NavigationGuardWithThis } from 'vue-router';
const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/discovery',
      meta: { auth: false }
    },
    {
      path: '/songList',
      name: 'songs',
      component: () => import('@/views/songList/RecommendSongListView.vue'),
      meta: { auth: false }
    },
    {
      path: '/songList/:id',
      name: 'songListDetail',
      component: () => import('@/views/songList/SongListDetail.vue'),
      meta: { auth: false }
    },
    {
      path: '/songList/edit/:id',
      name: 'songListEdit',
      component: () => import('@/views/songList/EditSongList.vue'),
      meta: { auth: true }
    },
    {
      path: '/discovery',
      name: 'discovery',
      component: () => import('@/views/home/DiscoveryView.vue'),
      meta: { auth: false }
    },
    {
      path: '/latestMusic',
      name: 'latestMusic',
      component: () => import('@/views/music/LatestMusicView.vue'),
      meta: { auth: false }
    },
    {
      path: '/comment/song/:id',
      name: 'songComment',
      component: () => import('@/views/comment/SongCommentView.vue'),
      meta: { auth: false }
    },
    {
      path: '/user/:id',
      name: 'userHome',
      component: () => import('@/views/user/UserHome.vue'),
      meta: { auth: false }
    },
    {
      path: '/userInfoEdit',
      name: 'userInfoEdit',
      component: () => import('@/views/user/UserInfoEdit.vue'),
      meta: { auth: true }
    },
    {
      path: '/searchResult',
      name: 'searchResult',
      component: () => import('@/views/search/SearchResult.vue')
    }
  ]
});
export const registerRouteHook = (beforeEachFn: NavigationGuardWithThis<undefined>, beforeResolveFn: NavigationGuardWithThis<undefined>) => {
  router.beforeEach(beforeEachFn);
  router.beforeResolve(beforeResolveFn);
};
// 懒加载 chunk 拉取失败时（断网、发版后旧 index.html 引用了已被清理的 hash），
// beforeResolve 不会触发，进度条会永远停在 70%，看起来像页面卡死。这里收尾并提示重试。
router.onError(() => {
  window.$message?.error('页面资源加载失败，请检查网络后重试');
  (document.querySelector('.n-loading-bar') as HTMLElement | null)?.style.setProperty('display', 'none');
});
export default router;