<template>
  <div class="ep-page hm-page-shell">
    <PageHeroHeader
      class="ep-hero"
      variant="cockpit"
      eyebrow="Employee Health"
      title="职工健康画像"
      :description="`${empInfo.empName || '--'} · ${empInfo.deptName || '部门未录入'} · ${empInfo.jobTypeName || '岗位未录入'}`"
    >
      <template #meta>
        <div class="ep-hero-meta">
          <span class="hm-status-chip">工号 {{ empInfo.empCode || '--' }}</span>
          <span :class="['hm-status-chip', freshnessTone]">{{ freshnessLabel }}</span>
          <span class="hm-status-chip">采集 {{ lastUpdate }}</span>
        </div>
      </template>
      <template #actions>
        <button type="button" class="hm-action-btn" @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </button>
        <button type="button" class="hm-action-btn hm-action-btn--success" :disabled="aiReportLoading" @click="openAiReport">
          <el-icon><Document /></el-icon>{{ aiReportLoading ? '生成中...' : 'AI 诊断报告' }}
        </button>
        <button type="button" class="hm-action-btn hm-action-btn--primary" @click="openPersonCommand">
          <el-icon><ChatDotRound /></el-icon>联系与处置
        </button>
      </template>
    </PageHeroHeader>

    <MetricStrip class="ep-summary-strip" :items="summaryMetricItems" @select="openWarningDetails" />

    <el-dialog v-model="warningDetailVisible" title="近30日预警明细" width="760px" class="ep-warning-dialog">
      <div class="ep-warning-dialog-summary">
        <div v-for="item in recentWarningSummary" :key="item.key" :class="[`tone-${item.tone}`]">
          <span>{{ item.label }}</span><strong>{{ item.value }}</strong>
        </div>
      </div>
      <p class="ep-warning-dialog-note">{{ recentWarningFootnote }}</p>
      <div v-if="recentWarnings.length === 0" class="ep-empty-warn">该员工近30日暂无预警记录</div>
      <div v-else class="ep-warning-dialog-list">
        <article v-for="(warning, index) in recentWarnings" :key="(warning.id || warning.createTime) + '_' + index" class="ep-warning-dialog-item">
          <div>
            <strong>{{ warning.warningType || warning.type || '--' }}</strong>
            <span>{{ fmtTime(warning.occurredAt || warning.createTime || warning.time) }}</span>
          </div>
          <em :class="warning.handled ? 'done' : 'pend'">{{ warning.handled ? '已处理' : '待处理' }}</em>
          <el-button
            v-if="!warning.handled && warning.id && (warning.occurredAt || warning.createTime || warning.time)"
            type="danger"
            link
            @click="openWarningIncident({ ...warning, occurredAt: warning.occurredAt || warning.createTime || warning.time })"
          >处置</el-button>
        </article>
      </div>
      <template #footer>
        <el-button @click="warningDetailVisible = false">关闭</el-button>
        <el-button type="primary" @click="goWarningCenter">查看全部预警</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="aiReportVisible"
      :title="'AI 健康诊断报告 - ' + empInfo.empName"
      width="820px"
      class="ep-ai-report-dialog"
      :close-on-click-modal="false"
    >
      <div v-if="aiReportLoading" class="ep-report-loading">
        <div class="ep-report-dots"><span></span><span></span><span></span></div>
        <p>正在生成健康诊断报告，请稍候...</p>
      </div>
      <div v-else-if="aiReportContent" v-html="aiReportHtml" class="ep-report-content"></div>
      <div v-else class="ep-report-empty">生成失败，请重试</div>
      <template #footer>
        <el-button @click="aiReportVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!aiReportContent || aiReportLoading" @click="printAiReport">打印 / 导出 PDF</el-button>
      </template>
    </el-dialog>

    <EmployeeProfileCommandLayer
      v-model:person-visible="personCommandVisible"
      v-model:incident-visible="incidentDrawerVisible"
      :person="empInfo"
      :online="isOnline"
      :incident="currentIncidentEvent"
      @emergency="openEmergency"
      @updated="handleIncidentUpdated"
    />

    <main class="ep-workspace" v-loading="loading">
      <section class="ep-main-column">
        <div class="ep-panel ep-vitals-panel">
          <div class="ep-ph">
            <span class="ep-ph-bar"></span>当前体征
            <span class="ep-ph-context">每项取最近一次非空读数</span>
            <span class="ep-refresh-countdown">距下次刷新 {{ nextRefreshSeconds }} 秒</span>
            <button type="button" class="ep-text-action" @click="refresh">刷新</button>
          </div>
          <div class="ep-vital-grid">
            <div v-for="item in vitalCards" :key="item.key" :class="['ep-vital-card', `tone-${item.tone}`]">
              <span class="ep-vital-label">{{ item.label }}</span>
              <div class="ep-vital-reading">
                <strong>{{ item.value }}</strong><span>{{ item.unit }}</span>
              </div>
              <span class="ep-vital-state">{{ item.state }}</span>
              <span class="ep-vital-time">{{ item.time ? `采集 ${item.time}` : '无采集时间' }}</span>
            </div>
          </div>
          <div :class="['ep-freshness-note', `tone-${freshnessStatus}`]">
            <span>{{ freshnessDetail }}</span>
            <span>采集时间：{{ lastUpdate }}</span>
          </div>
        </div>

      </section>

      <section class="ep-support-grid">
        <div class="ep-panel ep-basic">
          <div class="ep-ph"><span class="ep-ph-bar"></span>职工信息</div>
          <dl class="ep-person-grid">
            <div><dt>姓名</dt><dd>{{ empInfo.empName || '--' }}</dd></div>
            <div><dt>工号</dt><dd>{{ empInfo.empCode || '--' }}</dd></div>
            <div><dt>性别</dt><dd>{{ empInfo.gender === 2 ? '女' : '男' }}</dd></div>
            <div><dt>年龄</dt><dd>{{ calcAge(empInfo.birthDate) }}</dd></div>
            <div><dt>部门</dt><dd>{{ empInfo.deptName || '--' }}</dd></div>
            <div><dt>岗位</dt><dd>{{ empInfo.jobTypeName || '--' }}</dd></div>
          </dl>
        </div>

        <div class="ep-panel ep-guidance">
          <div class="ep-ph">
            <span class="ep-ph-bar"></span>规则提示
            <span class="ep-rule-refresh">随画像每30秒刷新 · {{ nextRefreshSeconds }}秒后更新</span>
          </div>
          <ul class="ep-guidance-list">
            <li v-for="(item, index) in profileInsightLines" :key="index">{{ item }}</li>
          </ul>
          <div class="ep-guidance-actions">
            <button type="button" class="hm-action-btn hm-action-btn--primary" @click="openWarningDetails">查看预警</button>
            <button type="button" class="hm-action-btn" @click="openPersonCommand">联系处置</button>
          </div>
        </div>

        <div class="ep-panel ep-activity">
          <div class="ep-ph"><span class="ep-ph-bar"></span>今日活动</div>
          <div class="ep-activity-grid">
            <div><span>步数</span><strong>{{ exercise.todaySteps > 0 ? exercise.todaySteps.toLocaleString() : '--' }}</strong><em>步</em></div>
            <div><span>消耗</span><strong>{{ exercise.todayCalories > 0 ? exercise.todayCalories : '--' }}</strong><em>kcal</em></div>
          </div>
        </div>
      </section>
    </main>

    <EmployeeHealthHistory
      :employee-code="empInfo.empCode"
      :employee-name="empInfo.empName"
      :refresh-token="historyRefreshToken"
    />

  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowLeft, ChatDotRound, Document } from '@element-plus/icons-vue'
import MetricStrip from '@/components/health-shell/MetricStrip.vue'
import PageHeroHeader from '@/components/health-shell/PageHeroHeader.vue'
import EmployeeProfileCommandLayer from './components/EmployeeProfileCommandLayer.vue'
import EmployeeHealthHistory from './components/EmployeeHealthHistory.vue'
import { useEmployeeProfilePage } from './use-employee-profile-page'

const page = useEmployeeProfilePage()
const {
  aiReportContent, aiReportHtml, aiReportLoading, aiReportVisible, calcAge,
  currentIncidentEvent, empInfo, exercise, fmtTime, freshnessStatus,
  handleIncidentUpdated, incidentDrawerVisible, isOnline, lastUpdate, loading,
  openAiReport, openEmergency, openPersonCommand, personCommandVisible,
  openWarningDetails, openWarningIncident, nextRefreshSeconds, printAiReport, profileInsightLines, profileSummaryCards, recentWarnings,
  recentWarningFootnote, recentWarningSummary, refresh,
  vitals, warningDetailVisible, goWarningCenter, historyRefreshToken
} = page

const summaryMetricItems = computed(() => profileSummaryCards.value.map((card) => ({
  key: card.label,
  label: card.label,
  value: card.value,
  note: card.sub,
  tone: card.tone === 'danger' ? 'danger' : card.tone === 'warn' ? 'warning' : card.tone === 'safe' ? 'success' : 'primary',
  clickable: card.clickable
})))

const freshnessLabel = computed(() => ({ fresh: '数据新鲜', stale: '数据陈旧', offline: '当前离线', no_data: '暂无数据' }[freshnessStatus.value] || '暂无数据'))
const freshnessTone = computed(() => freshnessStatus.value === 'fresh' ? 'hm-status-chip--success' : 'hm-status-chip--warning')
const freshnessDetail = computed(() => ({
  fresh: '数据在5分钟新鲜度窗口内',
  stale: '人员仍在15分钟在线窗口内，但体征已超过5分钟',
  offline: '最近体征已超过15分钟，请核查设备和回传状态',
  no_data: '当前没有可用体征记录'
}[freshnessStatus.value] || '当前没有可用体征记录'))

function readingState(value) {
  return value === null || value === undefined || value === ''
    ? { state: '暂无数据', tone: 'muted' }
    : { state: '已采集', tone: 'accent' }
}

const vitalCards = computed(() => {
  const value = vitals.value
  const temperature = value.temperature ? Number(value.temperature > 100 ? value.temperature / 10 : value.temperature) : null
  const pressureValue = value.systolic && value.diastolic ? { s: value.systolic, d: value.diastolic } : null
  return [
    { key: 'heartRate', label: '心率', value: value.heartRate || '--', unit: 'bpm', time: value.heartRateTime, ...readingState(value.heartRate) },
    { key: 'bloodOxygen', label: '血氧', value: value.bloodOxygen || '--', unit: '%', time: value.bloodOxygenTime, ...readingState(value.bloodOxygen) },
    { key: 'temperature', label: '体温', value: temperature ? temperature.toFixed(1) : '--', unit: '°C', time: value.temperatureTime, ...readingState(temperature) },
    { key: 'bloodPressure', label: '血压', value: pressureValue ? `${pressureValue.s}/${pressureValue.d}` : '--', unit: 'mmHg', time: value.bloodPressureTime, ...readingState(pressureValue) },
    { key: 'pressure', label: '压力指数', value: value.pressure ?? '--', unit: '', time: value.pressureTime, ...readingState(value.pressure) }
  ]
})
</script>

<style scoped>
@import './employee-profile.scss';
</style>
