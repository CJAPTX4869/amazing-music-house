import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    redirect: '/songs'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/components/Layout/MainLayout.vue'),
    children: [
      {
        path: 'songs',
        name: 'Songs',
        component: () => import('@/views/SongLibraryView.vue'),
        meta: { title: '歌曲库' }
      },
      {
        path: 'upload',
        name: 'Upload',
        component: () => import('@/views/SongUploadView.vue'),
        meta: { title: '上传歌曲', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'admin/songs',
        name: 'AdminSongs',
        component: () => import('@/views/admin/SongManageView.vue'),
        meta: { title: '歌曲管理', requiresAuth: true, requiresAdmin: true }
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/UserManageView.vue'),
        meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFoundView.vue')
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/**
 * 路由守卫
 * 说明：真正的权限边界在后端（@RequireAdmin），此处仅作体验拦截。
 */
router.beforeEach(async to => {
  const userStore = useUserStore()

  // 已登录但用户信息缺失（刷新页面）时，先恢复
  if (userStore.isLogin && !userStore.userInfo) {
    await userStore.fetchProfile()
  }

  if (to.meta.requiresAuth && !userStore.isLogin) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !userStore.isAdmin) {
    return { name: 'Songs' }
  }

  // 已登录访问登录页，跳转到首页
  if (to.name === 'Login' && userStore.isLogin) {
    return { name: 'Songs' }
  }
})

router.afterEach(to => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} - 音乐文件管理系统` : '音乐文件管理系统'
})

export default router
