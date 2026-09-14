<template>
  <div class="portrait-root">
    <!-- ══ TOP BAR ══ -->
    <div class="top-bar">
      <div class="top-bar-left">
        <div class="avatar-badge">
          <span class="avatar-letter">{{ (portrait.empName || '?').charAt(0) }}</span>
        </div>
        <div class="person-meta">
          <div class="person-name">{{ portrait.empName || '--' }}</div>
          <div class="person-tags">
            <span class="ptag"><el-icon><Postcard /></el-icon>{{ portrait.empCode || '--' }}</span>
            <span class="ptag"><el-icon><OfficeBuilding /></el-icon>{{ portrait.deptName || '--' }}</span>
            <span class="ptag"><el-icon><Suitcase /></el-icon>{{ portrait.jobTypeName || '--' }}</span>
            <span v-if="portrait.gender" class="ptag dim">{{ portrait.gender === 1 ? '男' : '女' }}</span>
            <span v-if="portrait.bloodType" class="ptag dim">血型 {{ portrait.bloodType }}</span>
            <span v-if="portrait.height" class="ptag dim">{{ portrait.height }}cm</span>
            <span v-if="portrait.weight" class="ptag dim">{{ portrait.weight }}kg</span>
          </div>
        </div>
      </div>

      <div class="vitals-strip">
        <div class="vcard heart">
          <div class="vcard-icon">HR</div>
          <div class="vcard-info">
            <div class="vcard-val">{{ vitals.heartRate ?? '--' }}<span class="vcard-unit">bpm</span></div>
            <div class="vcard-label">心率</div>
          </div>
          <div class="vstatus" :class="vitalStatus(vitals.heartRate, 60, 100)">{{ vitalStatusText(vitals.heartRate, 60, 100) }}</div>
        </div>
        <div class="vcard oxygen">
          <div class="vcard-icon">SpO2</div>
          <div class="vcard-info">
            <div class="vcard-val">{{ vitals.bloodOxygen ?? '--' }}<span class="vcard-unit">%</span></div>
            <div class="vcard-label">血氧</div>
          </div>
          <div class="vstatus" :class="vitalStatus(vitals.bloodOxygen, 95, 100)">{{ vitalStatusText(vitals.bloodOxygen, 95, 100) }}</div>
        </div>
        <div class="vcard temp">
          <div class="vcard-icon">TEMP</div>
          <div class="vcard-info">
            <div class="vcard-val">{{ vitals.temperature ?? '--' }}<span class="vcard-unit">°C</span></div>
            <div class="vcard-label">体温</div>
          </div>
          <div class="vstatus" :class="vitalStatus(vitals.temperature, 36, 37.3)">{{ vitalStatusText(vitals.temperature, 36, 37.3) }}</div>
        </div>
        <div class="vcard bp">
          <div class="vcard-icon">BP</div>
          <div class="vcard-info">
            <div class="vcard-val">{{ vitals.systolic ?? '--' }}/{{ vitals.diastolic ?? '--' }}<span class="vcard-unit">mmHg</span></div>
            <div class="vcard-label">血压</div>
          </div>
          <div class="vstatus" :class="vitalStatus(vitals.systolic, 90, 140)">{{ vitalStatusText(vitals.systolic, 90, 140) }}</div>
        </div>
      </div>

      <div class="top-bar-right">
        <div class="clock-badge"><el-icon><Timer /></el-icon>{{ currentTime }}</div>
        <el-button size="small" type="primary" @click="goRealtime"><el-icon><Monitor /></el-icon>实时监控</el-button>
        <el-button size="small" type="warning" :loading="complianceExporting" @click="exportComplianceReport"><el-icon><Document /></el-icon>职业健康档案</el-button>
        <el-button size="small" @click="goBack"><el-icon><Back /></el-icon>返回</el-button>
      </div>
    </div>

    <div v-if="!empCode" class="empty-tip">
      <el-icon size="60" color="#2d3561"><UserFilled /></el-icon>
      <p>请从职工列表点击姓名进入健康画像</p>
      <el-button type="primary" @click="goEmployeeList">前往职工列表</el-button>
    </div>

    <!-- 轮询刷新不触发白色遮罩，只有首次加载才显示 loading -->
    <div v-else v-loading="firstLoading" class="main-grid">

      <!-- LEFT: 趋势图 + 预警表 -->
      <div class="col-left">
        <div class="panel trend-panel">
          <div class="panel-hd">
            <span class="title-bar"></span>{{ trendPanelTitle }}
            <span v-if="metricDrilldown" class="badge">仅展示异常点</span>
          </div>
          <div ref="trendChartRef" class="trend-chart"></div>
        </div>
        <div class="panel warn-panel" ref="warnPanelRef"
          @mouseenter="pauseWarnAutoScroll"
          @mouseleave="resumeWarnAutoScroll"
          @wheel="onWarnWheel">
          <div class="panel-hd">
            <span class="title-bar warn-bar"></span>{{ warningPanelTitle }}
            <span class="badge">共 {{ metricDrilldown ? abnormalRecordTotal : warnings.length }} 条</span>
          </div>
          <el-table v-if="metricDrilldown" :data="metricAbnormalRows" stripe style="width:100%" max-height="99999"
            :empty-text="`该周期暂无异常${metricDrilldownConfig?.label || '指标'}记录`"
            :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'12px', padding:'7px 0' }"
            :row-style="{ background:'#1a1f3a' }"
            :cell-style="{ padding:'5px 0', fontSize:'12px' }">
            <el-table-column prop="recordTime" label="异常时间" min-width="135" />
            <el-table-column :label="abnormalValueTitle" min-width="100" align="center">
              <template #default="{ row }">
                <strong class="abnormal-heart-rate">{{ row.primaryValue }}{{ abnormalValueUnit ? ` ${abnormalValueUnit}` : '' }}</strong>
                <small v-if="abnormalDualValue" class="abnormal-secondary">{{ row.secondaryValue }} {{ abnormalValueUnit }}</small>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="82" align="center">
              <template #default="{ row }">
                <el-tag :type="row.level === 'danger' ? 'danger' : 'warning'" size="small" effect="dark">
                  {{ row.direction === 'low' ? '偏低' : row.level === 'danger' ? '高危' : '偏高' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-table v-else :data="warnings" stripe style="width:100%" max-height="99999"
            :header-cell-style="{ background:'#141830', color:'#7eb8d4', fontWeight:'600', fontSize:'12px', padding:'7px 0' }"
            :row-style="{ background:'#1a1f3a' }"
            :cell-style="{ padding:'5px 0', fontSize:'12px' }">
            <el-table-column prop="createTime" label="时间" min-width="110" :formatter="fmtTime" />
            <el-table-column prop="warningType" label="类型" width="72" />
            <el-table-column prop="indicatorName" label="指标" width="72" align="center" />
            <el-table-column prop="warningValue" label="数值" width="100" align="center" />
            <el-table-column label="级别" width="72" align="center">
              <template #default="{ row }">
                <el-tag :type="levelTagType(row.warningLevel)" size="small" effect="dark">{{ levelLabel(row.warningLevel) }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination v-if="metricDrilldown && abnormalRecordTotal > abnormalRecordSize"
            class="abnormal-pagination"
            background
            layout="prev, pager, next"
            :current-page="abnormalRecordPage"
            :page-size="abnormalRecordSize"
            :total="abnormalRecordTotal"
            @current-change="changeAbnormalRecordPage" />
        </div>
      </div>

      <!-- CENTER: 评分面板（填满） -->
      <div class="col-center">
        <div class="panel center-panel">
          <PortraitHologram
            :person-name="portrait.empName"
            :vitals="vitals"
            :freshness-status="vitals.freshnessStatus"
            :last-collected="vitals.recordTime"
          />
          <div class="panel-hd"><span class="title-bar radar-bar"></span>健康评分</div>

          <!-- ① 综合等级卡 -->
          <div class="grade-card" :class="gradeInfo.cls">
            <div class="grade-letter">{{ gradeInfo.grade }}</div>
            <div class="grade-right">
              <div class="grade-score">{{ gradeInfo.score }}<span class="grade-unit">/ 100</span></div>
              <div class="grade-label">{{ gradeInfo.label }}</div>
            </div>
          </div>

          <!-- ② 雷达图 -->
          <div ref="radarChartRef" class="radar-chart"></div>

          <!-- ③ 评分进度条 -->
          <div class="score-pills">
            <div v-for="(item, key) in scorePills" :key="key" class="pill">
              <div class="pill-label">{{ item.label }}</div>
              <div class="pill-bar">
                <div v-if="item.value > 0" class="pill-fill" :style="{ width: item.value + '%', background: item.color }"></div>
                <div v-else class="pill-fill" style="width:0%;background:#2a3a5a"></div>
              </div>
              <div class="pill-val" :style="{ color: item.value > 0 ? item.color : '#4a6080' }">{{ item.value || '--' }}</div>
            </div>
          </div>

          <!-- ④ 体征状态徽章 -->
          <div class="status-row">
            <div class="status-badge" :class="vitalStatus(vitals.heartRate, 60, 100)"><span class="sb-dot"></span>心率</div>
            <div class="status-badge" :class="vitalStatus(vitals.bloodOxygen, 95, 100)"><span class="sb-dot"></span>血氧</div>
            <div class="status-badge" :class="vitalStatus(vitals.temperature, 36, 37.3)"><span class="sb-dot"></span>体温</div>
            <div class="status-badge" :class="vitalStatus(vitals.systolic, 90, 140)"><span class="sb-dot"></span>血压</div>
            <div class="status-badge" :class="pressureStatus(vitals.pressure)"><span class="sb-dot"></span>压力</div>
          </div>

          <!-- ⑤ 建议复查 -->
          <div class="recheck-tip">
            <span class="recheck-tip__label">建议复查</span>
            <span><strong>{{ nextCheckDate }}</strong></span>
          </div>

          <!-- ⑥ 本周关键统计 2×2 -->
          <div class="section-label">本周健康统计</div>
          <div class="stats-grid">
            <div class="stat-card" :class="weekStats.minOxygen < 90 ? 'warn' : 'ok'">
              <div class="stat-label">最低血氧</div>
              <div class="stat-val">{{ weekStats.minOxygen || '--' }}<span>%</span></div>
              <div class="stat-sub">{{ weekStats.minOxygen ? (weekStats.minOxygen < 95 ? '低于正常值' : '正常范围') : '暂无数据' }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">平均心率</div>
              <div class="stat-val" style="color:#ff7070">{{ weekStats.avgHr || '--' }}<span>bpm</span></div>
              <div class="stat-sub">{{ weekStats.avgHr ? (weekStats.avgHr >= 60 && weekStats.avgHr <= 100 ? '正常范围内' : '需关注') : '暂无数据' }}</div>
            </div>
            <div class="stat-card ok">
              <div class="stat-label">平均体温</div>
              <div class="stat-val">{{ weekStats.avgTemp || '--' }}<span>°C</span></div>
              <div class="stat-sub">{{ weekStats.avgTemp ? '维持正常区间' : '暂无数据' }}</div>
            </div>
            <div class="stat-card">
              <div class="stat-label">预警次数</div>
              <div class="stat-val" style="color:#ffd200">{{ warnings.length }}<span>次</span></div>
              <div class="stat-sub">近30天累计</div>
            </div>
          </div>

          <!-- ⑦ 风险等级分布 -->
          <div class="section-label">风险等级分布</div>
          <div class="risk-strip">
            <div v-for="item in riskLevels" :key="item.name" class="risk-row">
              <div class="risk-name">{{ item.name }}</div>
              <div class="risk-bar-wrap">
                <div class="risk-bar-fill" :style="{ width: item.pct + '%', background: item.color }"></div>
              </div>
              <div class="risk-lv" :style="{ color: item.color }">{{ item.label }}</div>
            </div>
          </div>

          <!-- ⑧ 心率时段热力图 -->
          <div class="section-label">今日心率时段分布</div>
          <div class="heatmap-wrap">
            <div class="heat-row">
              <div v-for="(cell, i) in heatmapCells.slice(0, 12)" :key="i"
                class="heat-cell" :style="{ background: cell.color }" :title="cell.label"></div>
            </div>
            <div class="heat-row">
              <div v-for="(cell, i) in heatmapCells.slice(12)" :key="i+12"
                class="heat-cell" :style="{ background: cell.color }" :title="cell.label"></div>
            </div>
            <div class="heat-axis">
              <span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>23:00</span>
            </div>
            <div class="heat-legend">
              <span class="hl-dot" style="background:#1a2a4d"></span>低
              <span class="hl-dot" style="background:#1565c0;margin-left:6px"></span>正常
              <span class="hl-dot" style="background:#ff5252;margin-left:6px"></span>偏高
            </div>
          </div>

          <!-- ⑨ 疲劳指数 -->
          <div class="section-label">疲劳指数评估</div>
          <div class="fatigue-card" :class="fatigueInfo.cls">
            <div class="fatigue-gauge">
              <div class="fatigue-ring" :style="{ '--pct': fatigueInfo.index + '%', '--color': fatigueInfo.color }">
                <span class="fatigue-val">{{ fatigueInfo.index }}</span>
                <span class="fatigue-unit">/100</span>
              </div>
            </div>
            <div class="fatigue-body">
              <div class="fatigue-level" :style="{ color: fatigueInfo.color }">{{ fatigueInfo.label }}</div>
              <div class="fatigue-factors">
                <div v-for="f in fatigueInfo.factors" :key="f.name" class="fatigue-factor">
                  <span class="ff-name">{{ f.name }}</span>
                  <div class="ff-bar"><div class="ff-fill" :style="{ width: f.pct + '%', background: f.color }"></div></div>
                  <span class="ff-val" :style="{ color: f.color }">{{ f.label }}</span>
                </div>
              </div>
              <div class="fatigue-tip">{{ fatigueInfo.tip }}</div>
            </div>
          </div>

          <!-- ⑩ 职业病风险评分 -->
          <div class="section-label">职业风险评估</div>
          <div class="occ-risk-list">
            <div v-for="risk in occRisks" :key="risk.name" class="occ-risk-row">
              <div class="occ-risk-name">{{ risk.name }}</div>
              <div class="occ-risk-bar-wrap">
                <div class="occ-risk-fill" :style="{ width: risk.score + '%', background: risk.color }"></div>
              </div>
              <div class="occ-risk-score" :style="{ color: risk.color }">{{ risk.level }}</div>
              <div class="occ-risk-tip" :title="risk.tip">{{ risk.tip }}</div>
            </div>
          </div>

          <!-- ⑪ 与全矿平均对比 -->
          <div class="section-label">与全矿平均对比</div>
          <div class="mine-avg-compare">
            <div v-for="item in mineAvgCompare" :key="item.name" class="mac-row">
              <div class="mac-name">{{ item.name }}</div>
              <div class="mac-bars">
                <div class="mac-bar-wrap">
                  <div class="mac-label-my">本人</div>
                  <div class="mac-track">
                    <div class="mac-fill mac-fill-my" :style="{ width: item.myPct + '%', background: item.myColor }"></div>
                  </div>
                  <div class="mac-val-my" :style="{ color: item.myColor }">{{ item.myVal }}</div>
                </div>
                <div class="mac-bar-wrap">
                  <div class="mac-label-avg">全矿均</div>
                  <div class="mac-track">
                    <div class="mac-fill mac-fill-avg" :style="{ width: item.avgPct + '%' }"></div>
                  </div>
                  <div class="mac-val-avg">{{ item.avgVal }}</div>
                </div>
              </div>
              <div class="mac-rank" :class="item.rankCls">{{ item.rankLabel }}</div>
            </div>
          </div>

          <div style="flex:1"></div>
        </div>
      </div>

      <!-- RIGHT: 心理健康 + AI 报告 + 诊断历史 -->
      <div class="col-right">

        <!-- 心理健康评估 -->
        <div class="panel mh-panel">
          <div class="panel-hd">
            <span class="title-bar mh-bar"></span>心理健康评估
            <span class="badge mh-badge" :class="mentalHealthInfo.badgeCls">{{ mentalHealthInfo.level }}</span>
          </div>
          <div class="mh-body">
            <div class="mh-score-row">
              <div class="mh-score-circle" :style="{ '--color': mentalHealthInfo.color, '--pct': mentalHealthInfo.score }">
                <span class="mh-score-val">{{ mentalHealthInfo.score }}</span>
              </div>
              <div class="mh-dims">
                <div v-for="d in mentalHealthInfo.dims" :key="d.name" class="mh-dim">
                  <span class="mh-dim-name">{{ d.name }}</span>
                  <div class="mh-dim-bar"><div class="mh-dim-fill" :style="{ width: d.score + '%', background: d.color }"></div></div>
                  <span class="mh-dim-val" :style="{ color: d.color }">{{ d.label }}</span>
                </div>
              </div>
            </div>
            <div class="mh-advice">{{ mentalHealthInfo.advice }}</div>
          </div>
        </div>

        <div class="panel ai-panel">
          <div class="panel-hd ai-hd">
            <div class="ai-hd-left">
              <span class="title-bar ai-bar"></span>
              <span>AI 健康分析报告</span>
              <span v-if="aiReport.generateTime" class="ai-ts">· 生成于 {{ aiReport.generateTime }}</span>
            </div>
            <div class="ai-hd-right">
              <el-button type="primary" size="small" :loading="aiLoading" @click="handleGenerateReport(false)">
                {{ aiReport.content ? '刷新报告' : '生成 AI 分析' }}
              </el-button>
              <el-button v-if="aiReport.content" size="small" plain :loading="aiLoading" @click="handleGenerateReport(true)">重新生成</el-button>
              <el-button v-if="aiReport.content" size="small" plain :loading="pdfExporting" @click="exportPdf">📄 导出PDF</el-button>
            </div>
          </div>
          <div v-if="aiLoading" class="ai-loading">
            <div class="ai-dot"></div>
            <span>DeepSeek AI 正在分析，请稍候（约15-30秒）…</span>
          </div>
          <div v-else-if="!aiReport.content" class="ai-empty">
            <div class="ai-bot">🤖</div>
            <p>点击「生成 AI 分析」，DeepSeek 将根据近30天健康数据生成专属分析报告</p>
          </div>
          <div v-else class="ai-content" v-html="renderedReport"></div>
        </div>

        <!-- 诊断历史 -->
        <div class="panel hist-panel" v-if="reportHistory.length">
          <div class="panel-hd hist-hd" @click="histExpanded = !histExpanded">
            <span class="title-bar hist-bar"></span>诊断历史
            <span class="badge">{{ reportHistory.length }} 条</span>
            <span class="hist-toggle">{{ histExpanded ? '▲' : '▼' }}</span>
          </div>
          <div v-if="histExpanded" class="hist-list">
            <div v-for="(h, i) in reportHistory" :key="i" class="hist-item" @click="restoreHistory(h)">
              <div class="hist-time">{{ h.generateTime }}</div>
              <div class="hist-preview">{{ (h.content || '').replace(/#+\s*/g,'').slice(0, 60) }}…</div>
            </div>
          </div>
        </div>

      </div>
    </div>
  </div>
</template>

<script setup>
import { Timer, UserFilled, Back, Postcard, OfficeBuilding, Suitcase, Monitor, Document } from '@element-plus/icons-vue'
import PortraitHologram from './components/PortraitHologram.vue'
import { useHealthPortraitPage } from './use-health-portrait-page'

const {
  aiLoading,
  aiReport,
  complianceExporting,
  currentTime,
  empCode,
  exportComplianceReport,
  exportPdf,
  fatigueInfo,
  firstLoading,
  fmtTime,
  goBack,
  goEmployeeList,
  goRealtime,
  gradeInfo,
  handleGenerateReport,
  heatmapCells,
  abnormalRecordPage,
  abnormalRecordSize,
  abnormalRecordTotal,
  abnormalDualValue,
  abnormalValueTitle,
  abnormalValueUnit,
  changeAbnormalRecordPage,
  metricAbnormalRows,
  metricDrilldown,
  metricDrilldownConfig,
  histExpanded,
  levelLabel,
  levelTagType,
  mentalHealthInfo,
  mineAvgCompare,
  occRisks,
  onWarnWheel,
  pauseWarnAutoScroll,
  pdfExporting,
  portrait,
  pressureStatus,
  radarChartRef,
  renderedReport,
  reportHistory,
  restoreHistory,
  resumeWarnAutoScroll,
  riskLevels,
  scorePills,
  trendChartRef,
  trendPanelTitle,
  vitals,
  vitalStatus,
  vitalStatusText,
  warnings,
  warningPanelTitle,
  warnPanelRef,
  weekStats,
  nextCheckDate
} = useHealthPortraitPage()
</script>

<style scoped lang="scss">
@import './health-portrait.scss';
</style>

