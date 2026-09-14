<template>
  <div class="wr-root">
    <header class="wr-hd">
      <div class="wr-title-block">
        <span :class="['wr-live-dot', autoRefresh ? 'is-on' : '']"></span>
        <div>
          <h1 class="wr-title">手表原始数据</h1>
          <p class="wr-subtitle">展示 TCP 收到与发出的手表原始报文，按时间倒序保留最近 {{ capacity }} 条</p>
        </div>
      </div>

      <div class="wr-kpis">
        <div class="wr-kpi">
          <span class="wr-kpi-value">{{ totalBuffered }}</span>
          <span class="wr-kpi-label">缓存包数</span>
        </div>
        <div class="wr-kpi">
          <span class="wr-kpi-value">{{ returnedCount }}</span>
          <span class="wr-kpi-label">当前返回</span>
        </div>
        <div class="wr-kpi">
          <span class="wr-kpi-value">{{ lastProtocol || '--' }}</span>
          <span class="wr-kpi-label">最新协议</span>
        </div>
      </div>

      <div class="wr-actions">
        <span class="wr-refresh-time">{{ lastRefreshTime || '未刷新' }}</span>
        <el-switch
          v-model="autoRefresh"
          size="small"
          active-text="自动"
          inactive-text="手动"
        />
        <el-button type="primary" :loading="loading" @click="fetchPackets">
          刷新
        </el-button>
      </div>
    </header>

    <section class="wr-filter">
      <el-input
        v-model.trim="filters.imei"
        class="wr-imei-input"
        clearable
        placeholder="IMEI，例如 861265063894429"
        @keyup.enter="fetchPackets"
      />
      <el-select
        v-model="filters.protocolCode"
        class="wr-protocol-select"
        clearable
        filterable
        allow-create
        default-first-option
        placeholder="协议号"
      >
        <el-option v-for="code in protocolOptions" :key="code" :label="code" :value="code" />
      </el-select>
      <el-select v-model="filters.direction" class="wr-direction-select" clearable placeholder="方向">
        <el-option value="RX" label="接收 RX" />
        <el-option value="TX" label="发送 TX" />
      </el-select>
      <el-select v-model="filters.limit" class="wr-limit-select" placeholder="条数">
        <el-option :value="50" label="最近 50 条" />
        <el-option :value="100" label="最近 100 条" />
        <el-option :value="200" label="最近 200 条" />
        <el-option :value="500" label="最近 500 条" />
        <el-option :value="1000" label="最近 1000 条" />
      </el-select>
      <el-button :disabled="loading" @click="resetFilters">重置</el-button>
    </section>

    <section class="wr-table-wrap" v-loading="loading" element-loading-text="正在读取原始数据...">
      <el-table
        :data="packetList"
        height="100%"
        border
        stripe
        empty-text="还没有手表原始报文"
      >
        <el-table-column prop="sequence" label="#" width="78" align="center" />
        <el-table-column prop="receiveTime" label="时间" width="170" />
        <el-table-column prop="direction" label="方向" width="86" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.direction === 'TX' ? 'success' : 'primary'"
              effect="dark"
              class="wr-direction-tag"
            >
              {{ row.direction === 'TX' ? '发送' : '接收' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="protocolCode" label="协议" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.protocolCode || '--' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="命令意义" min-width="210">
          <template #default="{ row }">
            <span class="wr-protocol-meaning">{{ protocolMeaning(row.protocolCode, row.direction) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="imei" label="IMEI" width="170" />
        <el-table-column prop="remoteAddress" label="设备地址" min-width="190" />
        <el-table-column prop="paramCount" label="参数数" width="86" align="center" />
        <el-table-column label="参数" min-width="220">
          <template #default="{ row }">
            <div v-if="row.params?.length" class="wr-param-list">
              <el-tag
                v-for="(param, index) in row.params"
                :key="`${row.sequence}-${index}`"
                size="small"
                effect="plain"
                class="wr-param-tag"
              >
                {{ Number(index) + 1 }}: {{ param }}
              </el-tag>
            </div>
            <span v-else class="wr-empty-text">无</span>
          </template>
        </el-table-column>
        <el-table-column label="原始报文" min-width="420">
          <template #default="{ row }">
            <code class="wr-raw">{{ row.rawMessage || '' }}</code>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getWatchRawPackets } from '@/api/watch-raw'
import { useIntervalTask } from '@/composables/useIntervalTask'
import './watch-raw.scss'

defineOptions({ name: 'WatchRawPackets' })

interface PacketFilters {
  imei: string
  protocolCode: string
  direction: string
  limit: number
}

interface RawPacket {
  protocolCode?: string
  direction?: string
  params?: unknown[]
  sequence?: string | number
  [key: string]: unknown
}

interface RawPacketPage {
  list?: RawPacket[]
  totalBuffered?: number
  returnedCount?: number
  capacity?: number
}

interface ApiResult<T> {
  data?: T
}

const DEFAULT_FILTERS: PacketFilters = {
  imei: '',
  protocolCode: '',
  direction: '',
  limit: 200
}

const PROTOCOL_MEANINGS: Record<string, string> = {
  AP00: '手表登录上报',
  BP00: '服务器登录应答',
  AP01: 'GPS 定位上报',
  BP01: 'GPS 定位确认',
  AP02: '基站/Wi-Fi 定位上报',
  BP02: '定位数据确认',
  AP03: '心跳包上报',
  BP03: '心跳包确认',
  AP10: 'SOS/报警上报',
  BP10: '报警处理确认',
  AP12: 'SOS 号码设置确认',
  BP12: '下发设置 SOS 号码',
  AP14: '联系人白名单设置确认',
  BP14: '下发联系人白名单',
  AP16: '立即定位命令确认',
  BP16: '下发立即定位',
  AP18: '重启终端确认',
  BP18: '下发重启终端',
  AP20: '时区设置确认',
  BP20: '下发设置时区',
  AP33: '工作模式设置确认',
  BP33: '下发工作模式',
  AP40: '文字发送确认',
  BP40: '下发文字消息',
  AP84: '白名单开关确认',
  BP84: '下发白名单开关',
  AP49: '心率数据上报',
  BP49: '心率数据确认',
  AP50: '体温/电量上报',
  BP50: '体温数据确认',
  AP86: '心率/血压周期设置确认',
  BP86: '下发心率/血压测量周期',
  AP87: '体温周期设置确认',
  BP87: '下发体温测量周期',
  AP96: '睡眠时间段设置确认',
  BP96: '下发睡眠时间段',
  AP97: '睡眠数据上报',
  BP97: '睡眠数据确认',
  APHT: '心率和血压上报',
  BPHT: '心率血压数据确认',
  APHP: '综合健康数据上报',
  BPHP: '综合健康数据确认',
  APXL: '心率测量命令确认',
  BPXL: '下发测量心率',
  APXY: '血压测量命令确认',
  BPXY: '下发测量血压',
  APXZ: '血氧测量命令确认',
  BPXZ: '下发测量血氧',
  APXT: '体温测量命令确认',
  BPXT: '下发测量体温',
  APJZ: '血压校准确认',
  BPJZ: '下发血压校准',
  APHD: '心电分析数据上报',
  BPHD: '心电分析数据确认',
  APRR: '压力/情绪/疲劳数据上报',
  BPRR: '压力分析数据确认',
  APTM: '基站校时/经纬度请求',
  BPTM: '下发校时/经纬度',
  APWT: '天气请求',
  BPWT: '下发天气信息'
}

const fetchRawPackets = getWatchRawPackets as unknown as (
  params: Partial<PacketFilters>
) => Promise<ApiResult<RawPacketPage>>

const loading = ref(false)
const autoRefresh = ref(true)
const filters = reactive<PacketFilters>({ ...DEFAULT_FILTERS })
const packetList = ref<RawPacket[]>([])
const totalBuffered = ref(0)
const returnedCount = ref(0)
const capacity = ref(1000)
const lastRefreshTime = ref('')

const protocolOptions = computed(() => {
  const codes = new Set([
    'AP00', 'AP02', 'AP03', 'APHP', 'APHT', 'AP10',
    'AP12', 'AP14', 'AP16', 'AP18', 'AP20', 'AP40', 'AP84', 'AP86', 'AP87', 'AP96',
    'BP00', 'BP12', 'BP14', 'BP16', 'BP18', 'BP20', 'BP40', 'BP49',
    'BP84', 'BP86', 'BP87', 'BP96', 'BPHP', 'BPXL', 'BPXY', 'BPXZ', 'BPXT'
  ])
  packetList.value.forEach((packet) => {
    if (packet.protocolCode) codes.add(packet.protocolCode)
  })
  return [...codes].sort()
})
const lastProtocol = computed(() => packetList.value[0]?.protocolCode || '')

async function fetchPackets() {
  if (loading.value) return
  loading.value = true
  try {
    const response = await fetchRawPackets({
      imei: filters.imei || undefined,
      protocolCode: filters.protocolCode || undefined,
      direction: filters.direction || undefined,
      limit: filters.limit
    })
    const data = response.data || {}
    packetList.value = data.list || []
    totalBuffered.value = data.totalBuffered || 0
    returnedCount.value = data.returnedCount || packetList.value.length
    capacity.value = data.capacity || 1000
    lastRefreshTime.value = formatTime(new Date())
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '读取手表原始数据失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  Object.assign(filters, DEFAULT_FILTERS)
  void fetchPackets()
}

function protocolMeaning(protocolCode: unknown, direction: unknown) {
  if (!protocolCode) return '--'
  const code = String(protocolCode).toUpperCase()
  if (PROTOCOL_MEANINGS[code]) return PROTOCOL_MEANINGS[code]
  if (code.startsWith('BP')) return direction === 'TX' ? '服务器下发/确认命令' : 'BP 下行命令'
  if (code.startsWith('AP')) return direction === 'RX' ? '手表上行数据/确认' : 'AP 上行协议'
  return '未知协议'
}

function formatTime(date: Date) {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

const { start: startRefresh, stop: stopRefresh } = useIntervalTask(fetchPackets, 3000)

watch(autoRefresh, (enabled) => {
  if (enabled) startRefresh()
  else stopRefresh()
}, { immediate: true })

watch(
  () => [filters.protocolCode, filters.direction, filters.limit],
  () => void fetchPackets()
)

onMounted(() => void fetchPackets())
</script>
