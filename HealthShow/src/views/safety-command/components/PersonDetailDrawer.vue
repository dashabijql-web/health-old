<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="drawerTitle"
    direction="rtl"
    :size="isContactMode ? 'min(480px, 100vw)' : 'min(620px, 100vw)'"
    class="person-drawer"
    style="--el-bg-color:#071426;--el-drawer-bg-color:#071426;--el-text-color-primary:#e8f4ff;--el-text-color-regular:#9ab5ce;--el-border-color:#183754"
    :close-on-click-modal="true"
  >
    <div class="pd-body" v-loading="loading">
      <!-- 人员基本信息 -->
      <div class="pd-header">
        <div class="pd-avatar">{{ (resolvedName || '?')[0] }}</div>
        <div class="pd-info">
          <div class="pd-name">{{ resolvedName }}</div>
          <div class="pd-meta">
            <span>{{ resolvedDept || '未分配部门' }}</span>
            <code>{{ userCode || '--' }}</code>
            <span :class="['pd-status', resolvedOnline ? 'online' : 'offline']">
              <i class="status-dot"></i>{{ resolvedOnline ? '设备在线' : resolvedImei ? '设备离线' : '未绑定设备' }}
            </span>
          </div>
          <div class="pd-device-line">IMEI：{{ resolvedImei || '暂无绑定设备' }}</div>
        </div>
      </div>

      <!-- 实时体征卡片 -->
      <div v-if="!isContactMode" class="vital-grid">
        <div class="vital-card v-hr">
          <div class="vc-label">心率</div>
          <div class="vc-value">
            <span :class="['vc-num', getHrClass(realtimeData.heartRate)]">{{ realtimeData.heartRate || '--' }}</span>
            <span class="vc-unit">bpm</span>
          </div>
          <div class="vc-range">正常 60-100</div>
        </div>
        <div class="vital-card v-spo2">
          <div class="vc-label">血氧</div>
          <div class="vc-value">
            <span :class="['vc-num', getSpo2Class(realtimeData.bloodOxygen)]">{{ realtimeData.bloodOxygen || '--' }}</span>
            <span class="vc-unit">%</span>
          </div>
          <div class="vc-range">正常 ≥95</div>
        </div>
        <div class="vital-card v-temp">
          <div class="vc-label">体温</div>
          <div class="vc-value">
            <span :class="['vc-num', getTempClass(realtimeData.temperature)]">{{ formatTemp(realtimeData.temperature) }}</span>
            <span class="vc-unit">°C</span>
          </div>
          <div class="vc-range">正常 36.0-37.3</div>
        </div>
        <div class="vital-card v-steps">
          <div class="vc-label">今日步数</div>
          <div class="vc-value">
            <span class="vc-num blue">{{ realtimeData.steps || '--' }}</span>
            <span class="vc-unit">步</span>
          </div>
          <div class="vc-range">
            <el-progress
              :percentage="Math.min(100, ((realtimeData.steps || 0) / 10000) * 100)"
              :stroke-width="4"
              :show-text="false"
              color="#1890ff"
            />
          </div>
        </div>
      </div>

      <!-- ECG 波形 -->
      <div v-if="!isContactMode" class="pd-section">
        <div class="sec-title">实时心电图</div>
        <HeartRateWave
          v-if="realtimeData.heartRate"
          :width="396"
          :height="120"
          :heartRate="realtimeData.heartRate"
          waveColor="#00ff00"
          :speed="2"
          :showWarning="false"
        />
        <div v-else class="pd-no-ecg">暂无心电数据</div>
      </div>

      <!-- 7天趋势图 -->
      <div v-if="!isContactMode" class="pd-section">
        <div class="sec-title">7天趋势</div>
        <div ref="trendChartRef" class="trend-chart"></div>
      </div>

      <div v-if="!isContactMode" class="pd-section">
        <div class="sec-title">近期体征记录</div>
        <div v-if="healthRecords.length" class="pd-record-table">
          <div class="pd-record-row pd-record-row--head">
            <span>采集时间</span><span>心率</span><span>血氧</span><span>体温</span><span>血压</span>
          </div>
          <div v-for="record in healthRecords" :key="record.id || `${record.recordTime}-${record.heartRate}`" class="pd-record-row">
            <span>{{ formatRecordTime(record.recordTime || record.record_time) }}</span>
            <span>{{ record.heartRate || '--' }}</span>
            <span>{{ record.bloodOxygen ? `${record.bloodOxygen}%` : '--' }}</span>
            <span>{{ formatTemp(record.temperature) }}</span>
            <span>{{ formatBloodPressure(record) }}</span>
          </div>
        </div>
        <div v-else class="pd-record-empty">暂无近期体征记录</div>
      </div>

      <!-- 近期预警 -->
      <div class="pd-section" v-if="recentWarnings.length > 0">
        <div class="sec-title">近期预警 ({{ recentWarnings.length }})</div>
        <div class="warn-list">
          <div v-for="w in recentWarnings" :key="w.id" class="warn-item">
            <span :class="['wi-dot', w.handled ? 'done' : 'pending']"></span>
            <span class="wi-type">{{ w.warningType || w.type }}</span>
            <span class="wi-time">{{ formatTime(w.createTime || w.time) }}</span>
            <span :class="['wi-status', w.handled ? 'done' : 'pending']">{{ w.handled ? '已处理' : '未处理' }}</span>
          </div>
        </div>
      </div>
      <div v-else-if="isContactMode" class="pd-empty-warning">当前没有可关联的近期预警</div>

      <!-- 操作按钮 -->
      <div class="pd-operation-note">单人文字和语音可直接下发；应急动作必须关联该人员的具体预警事件。</div>
      <div class="pd-actions">
        <el-button type="primary" :disabled="!resolvedImei" @click="openMessageDialog">发送消息</el-button>
        <el-button type="warning" :disabled="!resolvedImei" @click="openVoiceDialog">语音播报</el-button>
        <el-button type="danger" plain @click="openEmergency">应急处置</el-button>
        <el-button v-if="!isContactMode" @click="openProfile">完整画像</el-button>
      </div>
    </div>
  </el-drawer>

  <el-dialog v-model="messageVisible" title="发送消息到手表" width="420px" append-to-body class="person-command-dialog" :close-on-click-modal="false">
    <div class="pd-dialog-target">{{ resolvedName }} · {{ resolvedImei }}</div>
    <el-input
      v-model="messageText"
      type="textarea"
      :rows="4"
      maxlength="50"
      show-word-limit
      resize="none"
      placeholder="输入需要发送到手表的文字消息"
    />
    <template #footer>
      <el-button @click="messageVisible = false">取消</el-button>
      <el-button type="primary" :loading="messageSending" :disabled="!messageText.trim()" @click="sendMessage">发送</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="voiceVisible" title="语音播报到手表" width="420px" append-to-body class="person-command-dialog" :close-on-click-modal="false">
    <div class="pd-dialog-target">{{ resolvedName }} · {{ resolvedImei }}</div>
    <el-select v-model="voiceTemplateId" placeholder="选择语音模板" style="width: 100%">
      <el-option v-for="template in voiceTemplates" :key="template.id" :label="template.name" :value="template.id" />
    </el-select>
    <template #footer>
      <el-button @click="voiceVisible = false">取消</el-button>
      <el-button type="warning" :loading="voiceSending" :disabled="!voiceTemplateId" @click="sendVoice">立即播报</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch, nextTick, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from '@/utils/echarts-setup'
import HeartRateWave from '@/components/HeartRateWave.vue'
import { sendWatchMessage, sendVoiceMessage, getVoiceTemplates } from '@/api/device'
import { searchEmployeesForCommand } from '@/api/employee'
import { getUserRealtimeData } from '@/api/realtime'
import { getHealthRecords } from '@/api/health'
import { getRiskWarningList } from '@/api/risk-warning'

const props = defineProps({
  visible: { type: Boolean, default: false },
  userCode: { type: String, default: '' },
  userName: { type: String, default: '' },
  deptName: { type: String, default: '' },
  imei: { type: String, default: '' },
  online: { type: Boolean, default: false },
  mode: { type: String, default: 'comprehensive' }
})

const emit = defineEmits(['update:visible', 'call', 'notify', 'viewRecord', 'emergency'])
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const realtimeData = ref({})
const recentWarnings = ref([])
const healthRecords = ref([])
const personIdentity = ref({})
const trendChartRef = ref(null)
let trendChart = null

const messageVisible = ref(false)
const messageText = ref('')
const messageSending = ref(false)
const voiceVisible = ref(false)
const voiceTemplates = ref([])
const voiceTemplateId = ref('')
const voiceSending = ref(false)

const resolvedName = computed(() => personIdentity.value.empName || props.userName || realtimeData.value.userName || '人员详情')
const resolvedDept = computed(() => personIdentity.value.deptName || props.deptName || realtimeData.value.deptName || '')
const resolvedImei = computed(() => personIdentity.value.imei || props.imei || '')
const resolvedOnline = computed(() => personIdentity.value.online ?? (realtimeData.value.online || props.online))
const isContactMode = computed(() => props.mode === 'contact')
const drawerTitle = computed(() => `${resolvedName.value} · ${isContactMode.value ? '联系与处置' : '人员综合管控'}`)

const formatTemp = (t) => {
  if (!t) return '--'
  // Backend stores temp as int * 10 (367 = 36.7)
  return t > 100 ? (t / 10).toFixed(1) : t.toFixed ? t.toFixed(1) : t
}

const formatTime = (t) => {
  if (!t) return ''
  if (typeof t === 'string' && t.length > 16) return t.substring(5, 16)
  return t
}

const formatRecordTime = (value) => {
  if (!value) return '--'
  return String(value).replace('T', ' ').slice(5, 16)
}

const formatBloodPressure = (record) => {
  const high = record.bloodPressureHigh ?? record.blood_pressure_high
  const low = record.bloodPressureLow ?? record.blood_pressure_low
  return high || low ? `${high || '--'}/${low || '--'}` : '--'
}

const getHrClass = (v) => !v ? '' : v < 60 || v > 100 ? 'danger' : v < 65 || v > 90 ? 'warn' : 'normal'
const getSpo2Class = (v) => !v ? '' : v < 90 ? 'danger' : v < 95 ? 'warn' : 'normal'
const getTempClass = (v) => {
  if (!v) return ''
  const t = v > 100 ? v / 10 : v
  return t > 37.3 || t < 36.0 ? 'danger' : t > 37.0 ? 'warn' : 'normal'
}

const fetchData = async () => {
  if (!props.userCode) return
  loading.value = true
  try {
    const [identity, rtRes, warnings] = await Promise.allSettled([
      searchEmployeesForCommand(props.userCode, 5),
      getUserRealtimeData(props.userCode),
      getRiskWarningList({ userCode: props.userCode, page: 1, size: 5 })
    ])

    if (identity.status === 'fulfilled' && identity.value?.code === 200) {
      const candidates = identity.value.data || []
      personIdentity.value = candidates.find((item) => item.empCode === props.userCode) || candidates[0] || {}
    }

    if (rtRes.status === 'fulfilled' && rtRes.value?.data) {
      const d = rtRes.value.data
      realtimeData.value = {
        userName: d.userName,
        heartRate: d.heartRate,
        bloodOxygen: d.bloodOxygen,
        temperature: d.temperature,
        bloodPressureHigh: d.bloodPressureHigh,
        bloodPressureLow: d.bloodPressureLow,
        pressure: d.pressure,
        steps: d.steps,
        deptName: d.deptName,
        lastUpdate: d.lastUpdate,
        online: d.status !== 'offline'
      }
    }

    if (warnings.status === 'fulfilled' && warnings.value?.data) {
      const wData = warnings.value.data
      recentWarnings.value = Array.isArray(wData) ? wData : (wData.records || wData.list || [])
    }

    if (!isContactMode.value) {
      await nextTick()
      buildTrendChart()
    }
  } catch { /* silent */
  } finally {
    loading.value = false
  }
}

function openMessageDialog() {
  if (!resolvedImei.value) {
    ElMessage.warning('该人员未绑定手表，无法发送消息')
    return
  }
  messageText.value = ''
  messageVisible.value = true
}

async function sendMessage() {
  const text = messageText.value.trim()
  if (!resolvedImei.value || !text) return
  messageSending.value = true
  try {
    const response = await sendWatchMessage(resolvedImei.value, text)
    if (response.code !== 200) throw new Error(response.message)
    ElMessage.success('文字消息已发送')
    messageVisible.value = false
  } catch (error) {
    ElMessage.error(error?.message || '消息发送失败')
  } finally {
    messageSending.value = false
  }
}

async function openVoiceDialog() {
  if (!resolvedImei.value) {
    ElMessage.warning('该人员未绑定手表，无法语音播报')
    return
  }
  voiceTemplateId.value = ''
  if (voiceTemplates.value.length === 0) {
    try {
      const response = await getVoiceTemplates()
      voiceTemplates.value = response.code === 200 ? (response.data || []) : []
    } catch {
      voiceTemplates.value = []
    }
  }
  if (voiceTemplates.value.length === 0) {
    ElMessage.warning('当前没有可用的语音模板')
    return
  }
  voiceVisible.value = true
}

async function sendVoice() {
  if (!resolvedImei.value || !voiceTemplateId.value) return
  voiceSending.value = true
  try {
    const response = await sendVoiceMessage(resolvedImei.value, voiceTemplateId.value)
    if (response.code !== 200) throw new Error(response.message)
    ElMessage.success('语音播报已发送')
    voiceVisible.value = false
  } catch (error) {
    ElMessage.error(error?.message || '语音播报失败')
  } finally {
    voiceSending.value = false
  }
}

function openEmergency() {
  const warning = recentWarnings.value.find((item) => {
    const handled = item.handled ?? item.isHandled
    return !handled && item.id && (item.occurredAt || item.createTime || item.time)
  })
  if (!warning) {
    ElMessage.warning('该人员暂无可关联的未处理预警；SOS由手表端主动发起，不能在管理端伪造。')
    return
  }
  emit('emergency', {
    ...warning,
    id: warning.id,
    occurredAt: warning.occurredAt || warning.createTime || warning.time,
    userCode: props.userCode,
    userName: resolvedName.value,
    deptName: resolvedDept.value
  })
  emit('update:visible', false)
}

function openProfile() {
  router.push({
    path: '/health-monitor/employee-profile',
    query: {
      empCode: props.userCode,
      empName: resolvedName.value,
      deptName: resolvedDept.value,
      imei: resolvedImei.value,
      from: route.fullPath
    }
  })
  emit('update:visible', false)
}

const buildTrendChart = async () => {
  if (!trendChartRef.value) return
  if (trendChart) trendChart.dispose()
  trendChart = echarts.init(trendChartRef.value)

  // Build 7-day date labels and lookup map
  const labels = []
  const dateKeys = []
  const dateMap = {}
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    labels.push(`${d.getMonth() + 1}/${d.getDate()}`)
    dateKeys.push(key)
    dateMap[key] = { hr: [], spo2: [], temp: [] }
  }

  // Fetch this person's health records for the last 7 days
  if (props.userCode) {
    try {
      const res = await getHealthRecords({
        userCode: props.userCode,
        size: 500,
        startTime: dateKeys[0],
        endTime: dateKeys[6]
      })
      if (res?.data) {
        const data = res.data
        const records = Array.isArray(data) ? data : (data.records || data.list || [])
        healthRecords.value = records.slice(0, 8)
        records.forEach(r => {
          const dt = (r.recordTime || r.record_time || '').substring(0, 10)
          if (!dateMap[dt]) return
          if (r.heartRate) dateMap[dt].hr.push(+r.heartRate)
          if (r.bloodOxygen) dateMap[dt].spo2.push(+r.bloodOxygen)
          if (r.temperature) {
            const t = r.temperature > 100 ? r.temperature / 10 : +r.temperature
            dateMap[dt].temp.push(t)
          }
        })
      }
    } catch (e) { /* silent */ }
  }

  const avg = (arr) => arr.length ? +(arr.reduce((a, b) => a + b, 0) / arr.length).toFixed(1) : null
  const hrValues = dateKeys.map(k => avg(dateMap[k].hr))
  const spo2Values = dateKeys.map(k => avg(dateMap[k].spo2))
  const tempValues = dateKeys.map(k => avg(dateMap[k].temp))

  const hasHr = hrValues.some(v => v !== null)
  const hasSpo2 = spo2Values.some(v => v !== null)
  const hasTemp = tempValues.some(v => v !== null)

  if (!hasHr && !hasSpo2 && !hasTemp) {
    trendChart.setOption({
      graphic: [{
        type: 'text', left: 'center', top: 'middle',
        style: { text: '暂无趋势数据', fill: 'rgba(255,255,255,0.3)', fontSize: 13 }
      }]
    })
    return
  }

  const series = []
  if (hasHr) series.push({
    name: '心率', type: 'line', yAxisIndex: 0, data: hrValues,
    smooth: true, connectNulls: false,
    lineStyle: { color: '#ff5252', width: 2 }, itemStyle: { color: '#ff5252' },
    areaStyle: { color: 'rgba(255,82,82,0.1)' }, symbol: 'circle', symbolSize: 4
  })
  if (hasSpo2) series.push({
    name: '血氧', type: 'line', yAxisIndex: 1, data: spo2Values,
    smooth: true, connectNulls: false,
    lineStyle: { color: '#1890ff', width: 2 }, itemStyle: { color: '#1890ff' },
    areaStyle: { color: 'rgba(24,144,255,0.1)' }, symbol: 'circle', symbolSize: 4
  })
  if (hasTemp) series.push({
    name: '体温', type: 'line', yAxisIndex: 2, data: tempValues,
    smooth: true, connectNulls: false,
    lineStyle: { color: '#faad14', width: 1.5, type: 'dashed' },
    itemStyle: { color: '#faad14' }, symbol: 'circle', symbolSize: 3
  })

  trendChart.setOption({
    grid: { left: 40, right: hasTemp ? 70 : 20, top: 10, bottom: 25 },
    xAxis: {
      type: 'category', data: labels,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.2)' } },
      axisLabel: { fontSize: 10, color: 'rgba(255,255,255,0.5)' }
    },
    yAxis: [
      { type: 'value', name: 'HR', min: 50, max: 120,
        axisLine: { lineStyle: { color: '#ff5252' } },
        axisLabel: { fontSize: 9, color: '#ff5252' },
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.05)' } } },
      { type: 'value', name: 'SpO2', min: 85, max: 100,
        axisLine: { lineStyle: { color: '#1890ff' } },
        axisLabel: { fontSize: 9, color: '#1890ff' }, splitLine: { show: false } },
      { type: 'value', name: '℃', min: 35, max: 40,
        axisLine: { lineStyle: { color: '#faad14' } },
        axisLabel: { fontSize: 9, color: '#faad14' }, splitLine: { show: false } }
    ],
    series,
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(10,22,40,0.9)',
      borderColor: 'rgba(24,144,255,0.3)',
      textStyle: { color: '#fff', fontSize: 11 }
    }
  })
}

watch(() => props.visible, (val) => {
  if (val && props.userCode) {
    realtimeData.value = {}
    recentWarnings.value = []
    healthRecords.value = []
    personIdentity.value = {}
    fetchData()
  }
})

onUnmounted(() => {
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
})
</script>

<style scoped lang="scss">
$blue: #1890ff;
$red: #ff5252;
$orange: #ff9800;
$yellow: #faad14;
$green: #52c41a;
$bg: #0d1f3c;
$text: rgba(255,255,255,.9);
$text2: rgba(255,255,255,.6);

.pd-body {
  padding: 0 4px;
  color: $text;
}

.pd-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba($blue, .08);
  border: 1px solid rgba($blue, .25);
  border-radius: 8px;
  margin-bottom: 14px;
}

.pd-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, $blue, #40a9ff);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: bold;
  color: #fff;
  flex-shrink: 0;
}

.pd-info { flex: 1; }
.pd-name { font-size: 16px; font-weight: bold; margin-bottom: 4px; }
.pd-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: $text2;

  code { color: rgba($blue, .88); font-size: 11px; }
}
.pd-device-line { margin-top: 5px; color: rgba(255,255,255,.42); font-size: 10px; }
.pd-status {
  display: flex;
  align-items: center;
  gap: 4px;
  &.online { color: $green; }
  &.offline { color: $text2; }
}
.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
  .online & { background: $green; box-shadow: 0 0 6px $green; }
  .offline & { background: #666; }
}

// Vital signs grid
.vital-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 14px;
}

.vital-card {
  padding: 10px;
  border-radius: 6px;
  border: 1px solid rgba($blue, .25);
  background: rgba(0,0,0,.2);
}

.vc-label {
  font-size: 11px;
  color: $text2;
  margin-bottom: 4px;
}

.vc-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.vc-num {
  font-size: 26px;
  font-weight: bold;
  font-family: 'Courier New', monospace;
  &.normal { color: $green; }
  &.warn { color: $orange; }
  &.danger { color: $red; }
  &.blue { color: $blue; }
}

.vc-unit {
  font-size: 11px;
  color: $text2;
}

.vc-range {
  font-size: 9px;
  color: rgba(255,255,255,.35);
  margin-top: 4px;
}

.v-hr { border-left: 3px solid $red; }
.v-spo2 { border-left: 3px solid $blue; }
.v-temp { border-left: 3px solid $orange; }
.v-steps { border-left: 3px solid $green; }

// Sections
.pd-section {
  margin-bottom: 14px;
}
.pd-no-ecg {
  height: 120px; display: flex; align-items: center; justify-content: center;
  color: rgba(126,184,247,0.4); font-size: 12px; background: rgba(255,255,255,0.02);
  border: 1px dashed rgba(126,184,247,0.15); border-radius: 4px;
}

.sec-title {
  font-size: 12px;
  font-weight: bold;
  color: rgba($blue, .9);
  margin-bottom: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid rgba($blue, .2);
}

.trend-chart {
  width: 100%;
  height: 160px;
  background: rgba(0,0,0,.15);
  border-radius: 6px;
}

.pd-record-table {
  overflow-x: auto;
  border: 1px solid rgba($blue, .15);
  border-radius: 6px;
}

.pd-record-row {
  min-width: 520px;
  display: grid;
  grid-template-columns: minmax(120px, 1.4fr) repeat(4, minmax(64px, 1fr));
  align-items: center;
  min-height: 34px;
  padding: 0 10px;
  border-bottom: 1px solid rgba($blue, .08);
  color: rgba(255,255,255,.68);
  font-size: 11px;

  &:last-child { border-bottom: none; }
  span:not(:first-child) { text-align: center; }
}

.pd-record-row--head {
  background: rgba($blue, .08);
  color: rgba($blue, .9);
  font-weight: 700;
}

.pd-record-empty {
  padding: 24px 12px;
  border: 1px dashed rgba($blue, .15);
  border-radius: 6px;
  color: rgba(255,255,255,.35);
  font-size: 11px;
  text-align: center;
}

.pd-empty-warning {
  margin-bottom: 14px;
  padding: 18px 12px;
  border: 1px dashed rgba($blue, .2);
  border-radius: 6px;
  color: rgba(255,255,255,.42);
  font-size: 11px;
  text-align: center;
}

// Warning list
.warn-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.warn-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  background: rgba(0,0,0,.15);
  border-radius: 4px;
  font-size: 11px;
}

.wi-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
  &.pending { background: $red; }
  &.done { background: $green; }
}

.wi-type { font-weight: bold; flex: 1; }
.wi-time { color: $text2; font-size: 10px; }
.wi-status {
  font-size: 9px;
  padding: 1px 5px;
  border-radius: 2px;
  &.pending { background: rgba($red, .2); color: $red; }
  &.done { background: rgba($green, .2); color: $green; }
}

// Actions
.pd-operation-note {
  padding: 8px 10px;
  border: 1px solid rgba($yellow, .18);
  border-radius: 6px;
  background: rgba($yellow, .05);
  color: rgba(255,255,255,.58);
  font-size: 10px;
  line-height: 1.5;
}

.pd-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-start;
  padding-top: 12px;
  border-top: 1px solid rgba($blue, .15);
}

.pd-dialog-target {
  margin-bottom: 12px;
  color: #547895;
  font-size: 12px;
}

@media (max-width: 600px) {
  .vital-grid { grid-template-columns: 1fr 1fr; }
  .pd-meta { flex-wrap: wrap; }
  .pd-actions :deep(.el-button) { margin-left: 0; }
}

:global(.person-drawer.el-drawer) {
  --el-bg-color: #071426;
  --el-fill-color-blank: #071426;
  --el-text-color-primary: #e8f4ff;
  background: #071426;
  border-left: 1px solid rgba(24, 144, 255, .28);
  box-shadow: -18px 0 48px rgba(0, 0, 0, .5);
}

:global(.person-drawer .el-drawer__header) {
  min-height: 62px;
  margin-bottom: 0;
  padding: 16px 22px;
  border-bottom: 1px solid rgba(24, 144, 255, .2);
  background: #081a30;
  color: #e8f4ff;
}

:global(.person-drawer .el-drawer__title) {
  color: #e8f4ff;
  font-size: 16px;
  font-weight: 700;
}

:global(.person-drawer .el-drawer__close-btn) {
  color: #82a8c8;
}

:global(.person-drawer .el-drawer__close-btn:hover) {
  color: #40a9ff;
}

:global(.person-drawer .el-drawer__body) {
  padding: 18px;
  background: #071426;
  color: #dceeff;
}

:global(.person-drawer .el-loading-mask) {
  background: rgba(7, 20, 38, .78);
}

:global(.person-command-dialog.el-dialog) {
  --el-bg-color: #081a30;
  --el-fill-color-blank: #081a30;
  --el-text-color-primary: #e8f4ff;
  --el-text-color-regular: #9ab5ce;
  --el-border-color: #21445f;
  max-width: calc(100vw - 24px);
  border: 1px solid rgba(24, 144, 255, .28);
  background: #081a30;
  box-shadow: 0 22px 70px rgba(0, 0, 0, .55);
}

:global(.person-command-dialog .el-dialog__header) {
  margin: 0;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(24, 144, 255, .18);
}

:global(.person-command-dialog .el-dialog__title) { color: #e8f4ff; }
:global(.person-command-dialog .el-dialog__headerbtn .el-dialog__close) { color: #82a8c8; }
:global(.person-command-dialog .el-dialog__body) { padding: 18px 20px; color: #b8cee2; }
:global(.person-command-dialog .el-dialog__footer) {
  padding: 14px 20px;
  border-top: 1px solid rgba(24, 144, 255, .18);
}

:global(.person-command-dialog .el-textarea__inner),
:global(.person-command-dialog .el-select__wrapper) {
  background: #061221;
  box-shadow: 0 0 0 1px #21445f inset;
  color: #e8f4ff;
}
</style>
