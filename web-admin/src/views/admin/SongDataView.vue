<template>
  <div class="ops-page" v-if="data.exists">
    <div class="ops-head">
      <h2>歌曲数据 · {{ data.info.title }}</h2>
      <el-button size="small" @click="router.back()">返回</el-button>
    </div>

    <!-- 状态卡 -->
    <el-row :gutter="14">
      <el-col :span="6"><el-card shadow="never"><div class="big"><b class="ok">{{ data.info.playCount }}</b><span>总播放量</span></div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="big"><b class="warn">{{ data.info.commentCount }}</b><span>评论条数</span></div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="big"><b>{{ data.info.likeCount }}</b><span>喜欢数</span></div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="big"><b>{{ data.info.status === 1 ? '上架' : '下架' }}</b><span>状态</span></div></el-card></el-col>
    </el-row>

    <!-- 基本信息 -->
    <el-card shadow="never" class="mt14">
      <template #header>基本信息</template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="歌名">{{ data.info.title }}</el-descriptions-item>
        <el-descriptions-item label="歌手">{{ data.info.artist || '-' }}</el-descriptions-item>
        <el-descriptions-item label="专辑">{{ data.info.album || '-' }}</el-descriptions-item>
        <el-descriptions-item label="类型/标签">
          <el-tag v-for="t in (data.info.tags || '').split(',').filter((x: string) => x)"
                  :key="t" size="small" style="margin-right:4px">{{ t }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ data.info.createTime }}</el-descriptions-item>
        <el-descriptions-item label="歌曲ID">{{ data.info.id }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 每日播放量 + 评论数双曲线（30 天全量，补零） -->
    <el-card shadow="never" class="mt14">
      <template #header>每日数据曲线（最近 30 天 · 播放量 + 评论数）</template>
      <div ref="chartEl" class="chart" v-show="!dailyEmpty" />
      <el-empty v-if="dailyEmpty" description="最近 30 天没有播放和评论记录（播放一次或评论一条就会出现数据）" :image-size="60" />
    </el-card>
  </div>
  <div class="ops-page" v-else-if="loaded">
    <el-empty description="歌曲不存在（可能已被删除）" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { songData } from '@/api/admin'

const route = useRoute()
const router = useRouter()
const data = ref<Record<string, any>>({})
const loaded = ref(false)
const chartEl = ref<HTMLElement>()
const dailyEmpty = ref(false)
let ro: ResizeObserver | undefined

async function load() {
  const songId = Number(route.params.id)
  if (!songId) return
  data.value = await songData(songId)
  loaded.value = true
  if (!data.value.exists) {
    dailyEmpty.value = true
    return
  }
  // 后端已按 30 天全量补零：dates 横轴 + plays 播放量 + comments 评论数
  const dates: string[] = data.value.dates || []
  const plays: number[] = data.value.plays || []
  const comments: number[] = data.value.comments || []
  if (plays.every(c => c === 0) && comments.every(c => c === 0)) {
    dailyEmpty.value = true
    return
  }
  await nextTick()
  if (!chartEl.value) return
  const chart = echarts.init(chartEl.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, left: 8, itemWidth: 14, itemHeight: 8, icon: 'roundRect', textStyle: { fontSize: 12 } },
    grid: { left: 44, right: 20, top: 34, bottom: 32 },
    xAxis: { type: 'category', data: dates, boundaryGap: false,
             axisLabel: { interval: 0, rotate: 40, fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '播放量', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5,
        data: plays, areaStyle: { opacity: 0.15 }, itemStyle: { color: '#409eff' },
        lineStyle: { width: 2 }, emphasis: { focus: 'series' }
      },
      {
        name: '评论数', type: 'line', smooth: true, symbol: 'circle', symbolSize: 5,
        data: comments, areaStyle: { opacity: 0.12 }, itemStyle: { color: '#e6a23c' },
        lineStyle: { width: 2 }, emphasis: { focus: 'series' }
      }
    ]
  })
  chart.resize()
}

onMounted(() => {
  load()
  ro = new ResizeObserver(() => {
    const c = echarts.getInstanceByDom(chartEl.value!)
    c?.resize()
  })
  if (chartEl.value) ro.observe(chartEl.value)
})
onUnmounted(() => {
  ro?.disconnect()
  if (chartEl.value) echarts.getInstanceByDom(chartEl.value)?.dispose()
})
</script>

<style scoped lang="scss">
.ops-page { padding: 4px; }
.ops-head {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
  h2 { margin: 0; }
}
.mt14 { margin-top: 14px; }
.chart { width: 100%; height: 300px; }
.big { text-align: center; padding: 4px 0;
  b { display: block; font-size: 28px; &.ok { color: #67c23a; } &.warn { color: #e6a23c; } }
  span { color: #909399; font-size: 12px; } }
</style>
