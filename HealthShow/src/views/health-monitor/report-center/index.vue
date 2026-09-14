<template>
  <div class="rc-page">
    <header class="rc-hd">
      <div class="rc-hd-left">
        <span class="rc-live-dot"></span>
        <h1 class="rc-hd-title">报表中心</h1>
      </div>
      <div class="rc-hd-kpis">
        <div class="rc-kpi" v-for="card in reportOverviewCards" :key="card.label">
          <span class="rc-kpi-n" :class="'kpi-' + card.tone">{{ card.value }}</span>
          <span class="rc-kpi-l">{{ card.label }}</span>
        </div>
      </div>
      <div class="rc-hd-actions">
        <el-date-picker
          v-model="selectedMonth"
          type="month"
          placeholder="选择月份"
          size="small"
          format="YYYY-MM"
          value-format="YYYY-MM"
          style="width:130px"
          @change="onMonthChange"
        />
        <el-tooltip :content="reportExportState.excelDisabledReason" :disabled="reportExportState.canExportExcel" placement="bottom">
          <span class="rc-export-wrap">
            <button class="rc-action-btn rc-action-btn--primary" @click="exportExcel" :disabled="exporting || !reportExportState.canExportExcel">
              {{ exporting ? '导出中...' : '导出 Excel' }}
            </button>
          </span>
        </el-tooltip>
        <el-tooltip :content="reportExportState.pdfDisabledReason" :disabled="reportExportState.canExportPdf" placement="bottom">
          <span class="rc-export-wrap">
            <button class="rc-action-btn rc-action-btn--warning" @click="exportPdf" :disabled="exportingPdf || !reportExportState.canExportPdf">
              {{ exportingPdf ? '导出中...' : '导出 PDF' }}
            </button>
          </span>
        </el-tooltip>
      </div>
    </header>

    <!-- ══ Tab ══ -->
    <el-tabs v-model="activeTab" class="rc-tabs" @tab-change="onTabChange">
      <el-tab-pane label="月度报表" name="monthly" />
      <el-tab-pane label="部门对比" name="dept" />
      <el-tab-pane label="健康趋势" name="trend" />
    </el-tabs>

    <!-- ══ 内容区（可导出区域）══ -->
    <div class="rc-content" ref="reportArea" v-loading="loading">
      <div class="rc-ai-card">
        <div class="rc-ai-head">
          <span class="rc-ai-title">AI 报表摘要</span>
          <span class="rc-ai-tag">{{ activeTab === 'monthly' ? '月度结论' : activeTab === 'dept' ? '部门结论' : '趋势结论' }}</span>
        </div>
        <div class="rc-ai-lines">
          <div v-for="(line, idx) in insightLines" :key="idx" class="rc-ai-line">
            <span class="rc-ai-dot"></span>
            <span>{{ line }}</span>
          </div>
        </div>
      </div>

      <!-- ── Tab 1: 月度报表 ── -->
      <template v-if="activeTab === 'monthly'">
        <div class="rc-kpi-row">
          <div class="rc-kpi" v-for="k in monthlyKpis" :key="k.label">
            <div class="rc-kpi-val" :style="{ color: k.color }">{{ k.val }}</div>
            <div class="rc-kpi-label">{{ k.label }}</div>
          </div>
        </div>

        <div class="rc-charts-row">
          <div class="rc-chart-card rc-chart-card--half">
            <div class="rc-ch-title">每日记录量</div>
            <div id="rcDailyCountChart" style="height:220px"></div>
          </div>
          <div class="rc-chart-card rc-chart-card--half">
            <div class="rc-ch-title">预警类型分布</div>
            <div id="rcWarnTypeChart" style="height:220px"></div>
          </div>
        </div>

        <div class="rc-table-card">
          <div class="rc-ch-title">月度员工健康明细（共 {{ monthlySummary.length }} 人）</div>
          <el-table :data="monthlySummary" size="small" class="rc-table" stripe max-height="360">
            <el-table-column label="姓名"   min-width="80">
              <template #default="{ row }">{{ row.empName || row.emp_name || '--' }}</template>
            </el-table-column>
            <el-table-column label="部门"   min-width="100">
              <template #default="{ row }">{{ row.deptName || row.dept_name || '--' }}</template>
            </el-table-column>
            <el-table-column label="记录条数" min-width="80" align="center">
              <template #default="{ row }">{{ row.recordCount || row.record_count || 0 }}</template>
            </el-table-column>
            <el-table-column label="心率均值" min-width="80" align="center">
              <template #default="{ row }">{{ fmt1(row.avgHeartRate || row.avg_heart_rate) }}</template>
            </el-table-column>
            <el-table-column label="血氧均值%" min-width="90" align="center">
              <template #default="{ row }">{{ fmt1(row.avgBloodOxygen || row.avg_blood_oxygen) }}</template>
            </el-table-column>
            <el-table-column label="体温均值" min-width="80" align="center">
              <template #default="{ row }">{{ fmt1(row.avgTemperature || row.avg_temperature) }}</template>
            </el-table-column>
            <el-table-column label="健康评分" min-width="80" align="center">
              <template #default="{ row }">
                <span :style="{ color: (row.healthScore || row.health_score||0) >= 90 ? '#4CAF50' : (row.healthScore || row.health_score||0) >= 70 ? '#ffd200' : '#ff5252' }">
                  {{ row.healthScore ?? row.health_score ?? '--' }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>

      <!-- ── Tab 2: 部门对比 ── -->
      <template v-if="activeTab === 'dept'">
        <div class="rc-chart-card">
          <div class="rc-ch-title">部门健康汇总对比（当前月）</div>
          <div id="rcDeptBarChart" style="height:300px"></div>
        </div>
        <div class="rc-chart-card">
          <div class="rc-ch-title">部门预警率排行</div>
          <div id="rcDeptWarnChart" style="height:260px"></div>
        </div>
        <div class="rc-table-card">
          <div class="rc-ch-title">部门健康详情</div>
          <el-table :data="deptSummary" size="small" class="rc-table" stripe>
            <el-table-column label="部门"     min-width="120">
              <template #default="{ row }">{{ row.deptName || row.dept_name || '--' }}</template>
            </el-table-column>
            <el-table-column label="在册人数" align="center">
              <template #default="{ row }">{{ row.employeeCount || row.employee_count || 0 }}</template>
            </el-table-column>
            <el-table-column label="近30天预警次数" align="center">
              <template #default="{ row }">
                <span :style="{ color: (row.warningCount||row.warning_count||0) > 0 ? '#ffd200' : '#4CAF50' }">{{ row.warningCount || row.warning_count || 0 }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>

      <!-- ── Tab 3: 健康趋势 ── -->
      <template v-if="activeTab === 'trend'">
        <div class="rc-trend-controls">
          <span class="rc-trend-label">时间范围：</span>
          <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
            <el-radio-button :label="7">近7天</el-radio-button>
            <el-radio-button :label="30">近30天</el-radio-button>
            <el-radio-button :label="90">近90天</el-radio-button>
          </el-radio-group>
        </div>
        <div class="rc-chart-card">
          <div class="rc-ch-title">心率 / 血氧 / 体温异常率趋势</div>
          <div id="rcTrendChart" style="height:320px"></div>
        </div>
        <div class="rc-chart-card">
          <div class="rc-ch-title">每日检测人次趋势</div>
          <div id="rcPersonTrendChart" style="height:240px"></div>
        </div>
      </template>

    </div>
  </div>
</template>

<script setup lang="ts">
import { useReportCenterPage } from './use-report-center-page'

defineOptions({ name: 'ReportCenter' })

const {
  activeTab,
  deptSummary,
  exportExcel,
  exportPdf,
  exporting,
  exportingPdf,
  fmt1,
  insightLines,
  loadTrend,
  loading,
  monthlyKpis,
  monthlySummary,
  onMonthChange,
  onTabChange,
  reportArea,
  reportExportState,
  reportOverviewCards,
  selectedMonth,
  trendDays
} = useReportCenterPage()
</script>

<style lang="scss" scoped>
@import './report-center.scss';
</style>
