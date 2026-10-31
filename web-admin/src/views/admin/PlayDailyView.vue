<template>
  <div class="ops-page">
    <div class="ops-head">
      <h2>每日播放统计</h2>
      <div class="ops-head-right">
        <span class="refresh-at">更新于 {{ updatedAt }}</span>
        <el-radio-group v-model="days" size="small" @change="load">
          <el-radio-button :value="7">近 7 天</el-radio-button>
          <el-radio-button :value="14">近 14 天</el-radio-button>
          <el-radio-button :value="30">近 30 天</el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- 汇总卡 -->
    <el-row :gutter="14">
      <el-col :span="6"><el-card shadow="never"><div class="big"><b class="ok">{{ withData }}</b><span>有播放记录的歌</span></div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="big"><b>{{ totalPlays }}</b><span>区间总播放量</span></div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="big"><b class="warn">{{ zeroSongs }}</b><span>区间零播放的歌</span></div></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><div class="big"><b class="blue">{{ totalSongs }}</b><span>曲库总数</span></div></el-card></el-col>
    </el-row>

    <!-- 多线曲线：每一首有播放记录的歌一条线；下拉框选歌（替代横向图例），点曲线跳单曲数据页 -->
    <el-card shadow="never" class="mt14">
      <template #header>
        <div class="card-head">
          <b>每日播放量曲线</b>
          <span class="muted">每首歌一条线 · 下拉框选歌 · 点曲线进单曲数据页</span>
          <el-select v-model="picked" multiple filterable collapse-tags collapse-tags-tooltip
                     placeholder="选择要显示的歌曲" size="small" class="song-select">
            <template #header>
              <div class="sel-actions">
                <el-button size="small" link type="primary" @click="picked = activeSongs.map(r => r.title)">全选</el-button>
                <el-button size="small" link type="danger" @click="picked = []">清空</el-button>
              </div>
            </template>
            <el-option v-for="(r, i) in activeSongs" :key="r.songId" :label="r.title" :value="r.title">
              <span class="dot" :style="{ background: PALETTE[i % PALETTE.length] }" />
              <span>{{ r.title }}</span>
              <span class="opt-cnt">{{ r.total }}</span>
            </el-option>
          </el-select>
        </div>
      </template>
      <div ref="chartEl" class="chart" v-show="chartSongs.length > 0" />
      <el-empty v-if="activeSongs.length === 0" description="这个区间还没有任何播放记录（有人播放一次就会出现曲线）" :image-size="70" />
      <el-empty v-else-if="chartSongs.length === 0" description="下拉框里没有选中任何歌（点「全选」恢复全部曲线）" :image-size="70" />
    </el-card>

    <!-- 播放排行：区间 TOP50（点击同样进单曲数据页） -->
    <el-card shadow="never" class="mt14">
      <template #header><div class="card-head"><b>区间播放排行 TOP{{ topRank.length }}</b><span class="muted">点条目进单曲数据页</span></div></template>
      <div ref="rankEl" class="rank" :style="{ height: rankHeight + 'px' }" />
    </el-card>

    <!-- 全量表格：每一首歌 × 每天（分页，不再整表渲染防卡死） -->
    <el-card shadow="never" class="mt14">
      <template #header>
        <div class="card-head">
          <span><b>全部歌曲（{{ filteredTable.length }} 首，最新在右）</b><span class="muted">　灰点 = 当天零播放 · 蓝 = 有播放 · 点行进单曲数据页</span></span>
          <el-input v-model="search" placeholder="搜索歌名" size="small" clearable style="width: 200px" />
        </div>
      </template>
      <el-table :data="pagedTable" size="small" max-height="460" border style="cursor: pointer"
                :cell-style="cellStyle" @row-click="goSong">
        <el-table-column type="index" label="#" width="56"
                         :index="(i: number) => (page - 1) * pageSize + i + 1" />
        <el-table-column prop="title" label="歌名" min-width="150" show-overflow-tooltip sortable />
        <el-table-column v-for="(d, i) in dates" :key="d" :label="d.slice(5)" width="62" align="center" :prop="'d' + i">
          <template #default="{ row }">{{ row.counts[i] > 0 ? row.counts[i] : '·' }}</template>
        </el-table-column>
        <el-table-column prop="total" label="合计" width="72" align="right" sortable />
      </el-table>
      <el-pagination class="pager" layout="prev, pager, next, total, jumper" :page-size="pageSize"
                     :total="filteredTable.length" :current-page="page" @current-change="page = $event" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import request from '@/api/request'

interface SongRow { songId: number; title: string; total: number; counts: number[] }

const router = useRouter()
const days = ref(30)
const dates = ref<string[]>([])
const tableData = ref<SongRow[]>([])
const loading = ref(false)
const search = ref('')
const page = ref(1)
const pageSize = 30
const updatedAt = ref('')
const chartEl = ref<HTMLElement>()
const rankEl = ref<HTMLElement>()
let chart: echarts.ECharts | undefined
let rank: echarts.ECharts | undefined
let timer: number | undefined
// 容器尺寸稳定/变化后自适应重布局（下拉框撑开 header、v-show 恢复显示都会改变宽度）
let ro: ResizeObserver | undefined
const onResize = () => { chart?.resize(); rank?.resize() }

const filteredTable = computed(() => {
  const kw = search.value.trim().toLowerCase()
  if (!kw) return tableData.value
  return tableData.value.filter(r => r.title.toLowerCase().includes(kw))
})
const pagedTable = computed(() =>
  filteredTable.value.slice((page.value - 1) * pageSize, page.value * pageSize))

const activeSongs = computed(() =>
  tableData.value.filter(r => r.total > 0).sort((a, b) => b.total - a.total))
const withData = computed(() => activeSongs.value.length)
const totalPlays = computed(() => tableData.value.reduce((s, r) => s + r.total, 0))
const zeroSongs = computed(() => tableData.value.length - withData.value)
const totalSongs = computed(() => tableData.value.length)
const topRank = computed(() => activeSongs.value.slice(0, 50))
const rankHeight = computed(() => Math.max(320, topRank.value.length * 24 + 56))

// 下拉框选歌（替代横向图例）：默认全部；自动刷新只追加新歌、保留用户已做的选择
const picked = ref<string[]>([])
const chartSongs = computed(() =>
  picked.value.length === 0 ? [] : activeSongs.value.filter(r => picked.value.includes(r.title)))

function goSong(row: SongRow) {
  router.push(`/admin/song-data/${row.songId}`)
}

function cellStyle({ columnIndex }: { columnIndex: number }) {
  // 日期列从第 2 列开始（0=#，1=歌名），最后是合计
  if (columnIndex >= 2 && columnIndex < 2 + dates.value.length) {
    return { padding: '4px 0' }
  }
  return {}
}

async function load() {
  loading.value = true
  try {
    const d = await request.get<never, any>('/admin/ops/play-daily', { params: { days: days.value } })
    dates.value = d.dates || []
    tableData.value = d.songs || []
    // 合并选择：保留用户已选的（仍存在的），追加新出现的歌；首次加载即全选
    const titles = activeSongs.value.map(r => r.title)
    picked.value = titles.filter(t => picked.value.includes(t))
      .concat(titles.filter(t => !picked.value.includes(t)))
    updatedAt.value = new Date().toLocaleTimeString()
    await nextTick()
    renderChart()
    renderRank()
  } finally {
    loading.value = false
  }
}

const PALETTE = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#9a66e4', '#00c4cc',
  '#ff9f43', '#2ecc71', '#e84393', '#54a0ff', '#fd79a8', '#0984e3', '#badc58',
  '#a29bfe', '#00b894', '#d63031', '#fda7df', '#12cbc4']

function renderChart() {
  if (!chartEl.value || chartSongs.value.length === 0) return
  if (!chart) chart = echarts.init(chartEl.value)
  const all = activeSongs.value
  // 曲线颜色按 activeSongs 里的位次固定，选显子集时颜色不漂移，与下拉框色点一致
  const series = chartSongs.value.map(r => ({
    name: r.title,
    type: 'line',
    smooth: true,
    symbol: 'circle',
    symbolSize: 5,
    lineStyle: { width: 2 },
    emphasis: { focus: 'series', lineStyle: { width: 3.5 } },
    itemStyle: { color: PALETTE[all.indexOf(r) % PALETTE.length] },
    areaStyle: { opacity: 0.06 },
    data: r.counts
  }))
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 16, top: 24, bottom: 60 },
    xAxis: {
      type: 'category', data: dates.value, boundaryGap: false,
      axisLabel: { interval: 0, rotate: 40, fontSize: 11 }
    },
    yAxis: { type: 'value', minInterval: 1 },
    series
  }, true)
  chart.off('click')
  chart.on('click', params => {
    const hit = chartSongs.value.find(r => r.title === params.seriesName)
    if (hit) router.push(`/admin/song-data/${hit.songId}`)
  })
}

function renderRank() {
  if (!rankEl.value) return
  if (!rank) rank = echarts.init(rankEl.value)
  const list = topRank.value
  if (list.length === 0) {
    rank.clear()
    return
  }
  const names = list.map(r => r.title).reverse()
  const vals = list.map(r => r.total).reverse()
  rank.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 40, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: { type: 'category', data: names, axisLabel: { fontSize: 11, width: 150, overflow: 'truncate' } },
    series: [{
      type: 'bar',
      data: vals,
      barWidth: 14,
      itemStyle: {
        borderRadius: [0, 7, 7, 0],
        color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
          { offset: 0, color: '#409eff' }, { offset: 1, color: '#9a66e4' }
        ])
      },
      label: { show: true, position: 'right', fontSize: 11, color: '#606266' }
    }]
  }, true)
  rank.resize()
  rank.off('click')
  rank.on('click', params => {
    const hit = list.find(r => r.title === params.name)
    if (hit) router.push(`/admin/song-data/${hit.songId}`)
  })
}

watch(picked, () => renderChart())
watch(search, () => { page.value = 1 })
onMounted(() => {
  load()
  timer = window.setInterval(() => { if (days.value) load() }, 60000)
  window.addEventListener('resize', onResize)
  ro = new ResizeObserver(onResize)
  if (chartEl.value) ro.observe(chartEl.value)
  if (rankEl.value) ro.observe(rankEl.value)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('resize', onResize)
  ro?.disconnect()
  chart?.dispose()
  rank?.dispose()
})
</script>

<style scoped lang="scss">
.ops-page { padding: 4px; }
.ops-head {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
  h2 { margin: 0; }
  .ops-head-right { display: flex; align-items: center; gap: 12px; .refresh-at { color: #909399; font-size: 12px; } }
}
.mt14 { margin-top: 14px; }
.chart { width: 100%; height: 400px; }
.rank { width: 100%; height: 320px; }
.big { text-align: center; padding: 4px 0;
  b { display: block; font-size: 28px; &.ok { color: #67c23a; } &.warn { color: #e6a23c; } &.blue { color: #409eff; } }
  span { color: #909399; font-size: 12px; } }
.card-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.song-select { margin-left: auto; max-width: 420px; }
.sel-actions { display: flex; justify-content: space-around; padding: 0 8px 4px; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 8px; vertical-align: middle; }
.opt-cnt { float: right; color: #909399; font-size: 12px; padding-left: 16px; }
.pager { margin-top: 10px; justify-content: flex-end; }
.muted { color: #909399; font-size: 12px; font-weight: normal; }
</style>
