import { onMounted, onUnmounted, ref } from 'vue';

/**
 * 图片懒加载：观察元素进入视口后把 visible 置为 true。
 * <p>
 * 旧实现只在回调里 new Image() 预载、并未控制真实 <img> 的 src，
 * 图片仍然立即全部加载，等于没有懒加载（已修复）：
 * 现在返回 visible，由 LoadImg 决定何时赋真实 src（进入视口后才真正发请求）。
 */
export default function useLazyLoad() {
  const imageRef = ref<HTMLElement | null>(null);
  const visible = ref(false);

  const observer: null | IntersectionObserver = new IntersectionObserver(callback);

  function callback(entries: IntersectionObserverEntry[]) {
    if (entries[0].isIntersecting) {
      visible.value = true;
      observer?.disconnect();
    }
  }
  onMounted(() => {
    imageRef.value && observer?.observe(imageRef.value);
  });

  onUnmounted(() => {
    imageRef.value && observer?.unobserve(imageRef.value);
    observer?.disconnect();
  });
  return { imageRef, visible };
}
