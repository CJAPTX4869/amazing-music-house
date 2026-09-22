<template>
  <div class="not-found">
    <div class="code">404</div>
    <p class="text-muted">页面不存在或已被移除</p>
    <el-button type="primary" @click="router.push('/songs')">返回歌曲库</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
const router = useRouter()
</script>

<style scoped lang="scss">
.not-found {
  height: 100%;
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;

  .code {
    font-size: 72px;
    font-weight: 700;
    background: linear-gradient(135deg, #6366f1, #8b5cf6);
    -webkit-background-clip: text;
    -webkit-text-fill-color: transparent;
  }
}
</style>
