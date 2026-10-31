import 'normalize.css';
import { createPinia } from 'pinia';
import VueVirtualScroller from 'vue-virtual-scroller';
// 虚拟滚动组件库的必需样式（PlayList 的 DynamicScroller 依赖，缺失会导致列表项定位错乱）
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css';
import { createApp } from 'vue';
import App from './App.vue';
import './assets/css/common.css';
import './assets/css/transition.css';
import './index.css';
import router from './router';
import { loadRuntimeConfig } from './config';

/**
 * 启动引导：先读运行时配置（config.json 里的后端地址），再挂载应用
 * <p>
 * 必须早于任何请求，否则首屏会用错后端地址（这正是"换电脑就全失败"的根因）。
 */
const bootstrap = async () => {
  const bootStart = performance.now();
  await loadRuntimeConfig();

  const app = createApp(App);

  /**
   * 全局错误兜底
   * <p>
   * 单个组件在 setup / 渲染阶段抛错时，Vue 会卸载该组件树；
   * 历史上（如 UserInfoEdit 访问 profile.province）曾因此导致整页空白且无任何提示，
   * 这里统一记录并提示，便于定位问题。
   */
  app.config.errorHandler = (err, _instance, info) => {
    console.error('[全局异常]', info, err);
    window.$message?.error('页面出现异常，请刷新重试');
  };

  app.use(createPinia());
  app.use(VueVirtualScroller);
  app.use(router);
  app.mount('#app');

  // 开屏淡出：至少展示 600ms，避免首屏很快时"闪一下就没"
  const splash = document.getElementById('app-splash');
  if (splash) {
    const wait = Math.max(0, 600 - (performance.now() - bootStart));
    window.setTimeout(() => {
      splash.classList.add('is-hidden');
      window.setTimeout(() => splash.remove(), 500);
    }, wait);
  }
};

bootstrap();