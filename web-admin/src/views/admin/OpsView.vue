<template>
  <div class="ops-page">
    <div class="ops-head">
      <h2>运维状态</h2>
      <div class="ops-head-right">
        <span class="refresh-at">更新于 {{ updatedAt }}</span>
        <el-switch v-model="auto" active-text="自动刷新" />
        <el-button type="primary" size="small" :loading="loading" @click="loadAll">立即刷新</el-button>
      </div>
    </div>

    <!-- ============ CPU / 内存 / JVM堆 ============ -->
    <el-row :gutter="14">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            CPU（{{ sv.cpu?.cpuNum ?? '-' }} 核）
            <el-tag v-if="sv.cpu" :type="tagOf(sv.cpu.used)" size="small" style="margin-left:8px">用户 {{ sv.cpu.used }}%</el-tag>
          </template>
          <div v-if="sv.cpu" class="cpu-lines">
            <div class="cpu-row"><span>用户使用率</span>
              <el-progress :percentage="sv.cpu.used" :stroke-width="10" :color="loadColor" /></div>
            <div class="cpu-row"><span>系统使用率</span>
              <el-progress :percentage="sv.cpu.sys" :stroke-width="10" color="#409eff" /></div>
            <div class="cpu-row"><span>当前空闲率</span>
              <el-progress :percentage="sv.cpu.free" :stroke-width="10" color="#909399" /></div>
            <div class="kv"><div><span>CPU 总 ticks</span><b>{{ sv.cpu.total }}</b></div></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            物理内存
            <el-tag v-if="sv.mem" :type="tagOf(sv.mem.usage)" size="small" style="margin-left:8px">
              使用率 {{ sv.mem.usage }}%
            </el-tag>
          </template>
          <div v-if="sv.mem" class="kv">
            <div><span>总内存</span><b>{{ sv.mem.total }} GB</b></div>
            <div><span>已用</span><b>{{ sv.mem.used }} GB</b></div>
            <div><span>空闲</span><b>{{ sv.mem.free }} GB</b></div>
          </div>
          <el-progress :percentage="sv.mem?.usage ?? 0" :stroke-width="10" :color="loadColor" style="margin-top:8px" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>JVM 堆内存</template>
          <div class="bar-wrap">
            <el-progress type="dashboard" :percentage="heapPct" :color="loadColor" :width="120">
              <span class="bar-num">{{ st.app?.heapUsedMB }} / {{ st.app?.heapMaxMB }} MB</span>
            </el-progress>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ JVM 信息 / 服务器信息 ============ -->
    <el-row :gutter="14" class="mt14">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>JVM 信息</template>
          <el-descriptions :column="1" border size="small" v-if="sv.jvm">
            <el-descriptions-item label="Java 名称">{{ sv.jvm.name }}</el-descriptions-item>
            <el-descriptions-item label="Java 版本">{{ sv.jvm.version }}</el-descriptions-item>
            <el-descriptions-item label="启动时间">{{ sv.jvm.startTime }}</el-descriptions-item>
            <el-descriptions-item label="运行时长">{{ sv.jvm.runTime }}</el-descriptions-item>
            <el-descriptions-item label="安装路径">{{ sv.jvm.home }}</el-descriptions-item>
            <el-descriptions-item label="堆 已用/最大">
              {{ sv.jvm.used }} / {{ sv.jvm.max }} MB（{{ sv.jvm.usage }}%）
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>服务器信息</template>
          <el-descriptions :column="1" border size="small" v-if="sv.sys">
            <el-descriptions-item label="服务器名称">{{ sv.sys.computerName }}</el-descriptions-item>
            <el-descriptions-item label="服务器 IP">{{ sv.sys.computerIp }}</el-descriptions-item>
            <el-descriptions-item label="操作系统">{{ sv.sys.osName }}</el-descriptions-item>
            <el-descriptions-item label="系统架构">{{ sv.sys.osArch }}</el-descriptions-item>
            <el-descriptions-item label="项目路径">{{ sv.sys.userDir }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 磁盘状态（OSHI 全分区） ============ -->
    <el-card shadow="never" class="mt14">
      <template #header>磁盘状态</template>
      <el-table :data="sv.sysFiles || []" size="small">
        <el-table-column prop="dirName" label="盘符路径" min-width="110" />
        <el-table-column prop="sysTypeName" label="文件系统" width="100" />
        <el-table-column prop="typeName" label="盘符类型" min-width="120" show-overflow-tooltip />
        <el-table-column prop="total" label="总大小" width="100" align="right" />
        <el-table-column prop="free" label="空闲大小" width="100" align="right" />
        <el-table-column prop="used" label="已用大小" width="100" align="right" />
        <el-table-column label="已用百分比" min-width="160">
          <template #default="{ row }">
            <el-progress :percentage="row.usage" :stroke-width="8"
                         :color="row.usage > 90 ? '#f56c6c' : row.usage > 75 ? '#e6a23c' : '#67c23a'" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- ============ 应用 / 数据库 / 业务统计 / 存储 ============ -->
    <el-row :gutter="14" class="mt14">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>应用</template>
          <div class="kv">
            <div><span>PID</span><b>{{ st.app?.pid }}</b></div>
            <div><span>端口 / 配置</span><b>{{ st.app?.port }} / {{ st.app?.activeProfiles }}</b></div>
            <div><span>恢复码已配置</span><b>
              <el-tag :type="st.app?.recoveryCodeConfigured ? 'success' : 'danger'" size="small">
                {{ st.app?.recoveryCodeConfigured ? '是' : '否（找回已禁用）' }}
              </el-tag></b></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            数据库
            <el-tag :type="st.db?.ok ? 'success' : 'danger'" size="small" style="margin-left:8px">
              {{ st.db?.ok ? '连接正常' : '连接失败' }}
            </el-tag>
          </template>
          <div class="kv">
            <div><span>产品</span><b>{{ st.db?.product }}</b></div>
            <div><span>地址</span><b>{{ st.db?.url }}</b></div>
            <div v-if="st.db?.error"><span>错误</span><b class="err">{{ st.db.error }}</b></div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>业务统计</template>
          <div class="stat-grid">
            <div class="stat"><b>{{ st.stats?.users ?? '-' }}</b><span>用户</span></div>
            <div class="stat"><b>{{ st.stats?.songs ?? '-' }}</b><span>歌曲</span></div>
            <div class="stat"><b>{{ st.stats?.playlists ?? '-' }}</b><span>歌单</span></div>
            <div class="stat"><b>{{ st.stats?.comments ?? '-' }}</b><span>评论</span></div>
          </div>
          <div class="kv" style="margin-top:8px">
            <div><span>存储目录</span><b>{{ st.storage?.path }}</b></div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ 在线客户端 ============ -->
    <el-row :gutter="14" class="mt14">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            在线客户端（90 秒内有活动）
            <el-tag size="small" style="margin-left:8px" type="success">总在线 {{ online.total ?? 0 }}</el-tag>
          </template>
          <el-row :gutter="14">
            <el-col :span="8" v-for="p in online.platforms || []" :key="p.key">
              <div class="plat-card" :class="p.online ? 'is-online' : 'is-offline'">
                <div class="plat-head">
                  <span class="dot"></span>
                  <b>{{ p.label }}</b>
                  <span class="plat-state">{{ p.online ? '● 在线 ' + p.count + ' 人' : '○ 离线' }}</span>
                </div>
                <div v-if="p.users && p.users.length" class="plat-users">
                  <div v-for="u in p.users" :key="u.platform + u.userId" class="plat-user">
                    {{ u.nickname || u.username }} <span class="muted">@ {{ u.ip }}</span>
                  </div>
                </div>
                <div v-else class="plat-empty">暂无设备接入</div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- ============ Redis ============ -->
    <el-row :gutter="14" class="mt14">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header>
            Redis（验证码票据 / 登录限流 / 分页缓存）
            <el-tag :type="st.redis?.ok ? 'success' : 'danger'" size="small" style="margin-left:8px">
              {{ st.redis?.ok ? '连接正常' : '连接失败' }}
            </el-tag>
          </template>
          <div v-if="st.redis && !st.redis.ok" class="err">{{ st.redis.error }}</div>
          <el-descriptions v-if="st.redis" :column="4" border size="small">
            <el-descriptions-item label="版本">{{ st.redis.version }}</el-descriptions-item>
            <el-descriptions-item label="运行天数">{{ st.redis.uptimeDays }} 天</el-descriptions-item>
            <el-descriptions-item label="内存占用">{{ st.redis.usedMemoryHuman }}</el-descriptions-item>
            <el-descriptions-item label="OPS">{{ st.redis.opsPerSec }}/s</el-descriptions-item>
            <el-descriptions-item label="连接客户端">{{ st.redis.connectedClients }}</el-descriptions-item>
            <el-descriptions-item label="缓存命中率">{{ st.redis.hitRate }}</el-descriptions-item>
            <el-descriptions-item label="键总数">{{ st.redis.dbSize }}</el-descriptions-item>
            <el-descriptions-item label="业务键">
              验证码 {{ (st.redis.businessKeys || {})['验证码票据'] ?? 0 }} ·
              限流 {{ (st.redis.businessKeys || {})['登录限流'] ?? 0 }} ·
              分页缓存 {{ (st.redis.businessKeys || {})['歌曲分页缓存'] ?? 0 }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { opsServer, opsStatus, opsOnline } from '@/api/admin'

const sv = ref<Record<string, any>>({})   // OSHI 整机快照
const st = ref<Record<string, any>>({})   // 应用/数据库/业务快照
const loading = ref(false)
const auto = ref(true)
const online = ref<Record<string, any>>({})
const updatedAt = ref('')
let timer: number | undefined

async function loadAll() {
  loading.value = true
  try {
    const [a, b, c] = await Promise.all([opsServer(), opsStatus(), opsOnline()])
    sv.value = a
    st.value = b
    online.value = c
    updatedAt.value = new Date().toLocaleTimeString()
  } finally {
    loading.value = false
  }
}

const heapPct = computed(() =>
  st.value.app?.heapMaxMB ? Math.round((st.value.app.heapUsedMB / st.value.app.heapMaxMB) * 100) : 0)
const loadColor = [{ color: '#67c23a', percentage: 60 }, { color: '#e6a23c', percentage: 85 }, { color: '#f56c6c', percentage: 100 }]

function tagOf(v: number) {
  return v > 85 ? 'danger' : v > 60 ? 'warning' : 'success'
}

onMounted(() => {
  loadAll()
  timer = window.setInterval(() => auto.value && loadAll(), 5000)
})
onUnmounted(() => timer && clearInterval(timer))
</script>

<style scoped lang="scss">
.ops-page { padding: 4px; }
.ops-head {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px;
  h2 { margin: 0; }
  .ops-head-right { display: flex; align-items: center; gap: 12px; .refresh-at { color: #909399; font-size: 12px; } }
}
.mt14 { margin-top: 14px; }
.plat-card { border: 1px solid #e4e7ed; border-radius: 10px; padding: 12px 14px; min-height: 84px;
  &.is-online { border-color: #b3e19d; background: #f0f9eb; }
  &.is-offline { background: #fafafa; }
  .plat-head { display: flex; align-items: center; gap: 8px;
    .dot { width: 8px; height: 8px; border-radius: 50%; background: #c0c4cc; }
    .plat-state { margin-left: auto; font-size: 12px; color: #909399; } }
  &.is-online .dot { background: #67c23a; box-shadow: 0 0 6px #67c23a; }
  .plat-users { margin-top: 8px; font-size: 13px;
    .plat-user { padding: 2px 0; } .muted { color: #909399; font-size: 12px; } }
  .plat-empty { margin-top: 10px; color: #c0c4cc; font-size: 12px; text-align: center; } }
.cpu-lines {
  .cpu-row { display: flex; align-items: center; gap: 10px; padding: 5px 0;
             span { width: 78px; color: #909399; font-size: 13px; flex-shrink: 0; } }
  .kv { margin-top: 4px; }
}
.kv {
  div { display: flex; justify-content: space-between; padding: 5px 0; gap: 10px;
        span { color: #909399; flex-shrink: 0; } b { font-weight: 500; text-align: right; word-break: break-all; } }
  .err { color: #f56c6c; }
}
.bar-wrap { display: flex; justify-content: center; padding: 6px 0; .bar-num { font-size: 13px; color: #606266; } }
.stat-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px;
  .stat { text-align: center; padding: 10px 0; background: #f7f8fa; border-radius: 8px;
          b { display: block; font-size: 24px; color: #409eff; } span { color: #909399; font-size: 12px; } } }
</style>
