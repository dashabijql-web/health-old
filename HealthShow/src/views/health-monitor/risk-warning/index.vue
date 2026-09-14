<template>
  <div class="rw-root">

    <!-- ══ Header ══ -->
    <header class="rw-hd">
      <div class="rw-hd-left">
        <span class="rw-live-dot"></span>
        <h1 class="rw-hd-title">健康风险预警</h1>
      </div>
      <div class="rw-hd-kpis">
        <div class="rw-kpi" v-for="k in headerKpis" :key="k.key">
          <span class="rw-kpi-n" :class="'kpi-' + k.tone">{{ k.val }}</span>
          <span class="rw-kpi-l">{{ k.label }}</span>
        </div>
      </div>
      <div class="rw-hd-time">{{ currentTime }}</div>
      <div class="rw-period-tabs">
        <span v-for="p in periodOptions" :key="p.value"
              :class="['rw-period-tab', activePeriod === p.value ? 'is-active' : '']"
              @click="switchPeriod(p.value)">{{ p.label }}</span>
      </div>
    </header>

    <div class="rw-center-nav">
      <WarningCenterNav />
    </div>

    <!-- ══ Body ══ -->
    <section class="rw-bd">

      <!-- 左侧 -->
      <aside class="rw-aside">
        <div class="rw-panel rw-aside-top">
          <div class="rw-ph">
            <span class="rw-ph-bar"></span>
            <span class="rw-ph-title">{{ statTitle }}</span>
          </div>
          <div class="rw-stat-list">
            <div class="rw-stat-row" v-for="(item, i) in warningStats" :key="i">
              <span class="rw-stat-dot" :style="{background: item.color}"></span>
              <span class="rw-stat-name">{{ item.label }}</span>
              <div class="rw-stat-bar-wrap">
                <div class="rw-stat-bar" :style="{width: statBarWidth(item.value) + '%', background: item.color}"></div>
              </div>
              <span class="rw-stat-val" :style="{color: item.color}">{{ item.value }}</span>
            </div>
          </div>
        </div>

        <div class="rw-panel rw-aside-bot">
          <div class="rw-ph">
            <span class="rw-ph-bar"></span>
            <span class="rw-ph-title">部门风险预警分布</span>
          </div>
          <div class="rw-pc">
            <div ref="deptRef" style="width:100%;height:100%"></div>
          </div>
        </div>
      </aside>

      <!-- 主区域 -->
      <main class="rw-main">
        <div class="rw-panel rw-panel-trend">
          <div class="rw-ph">
            <span class="rw-ph-bar"></span>
            <span class="rw-ph-title">{{ trendTitle }}</span>
            <div class="rw-trend-tags">
              <span class="rw-tag" style="color:#ef4444;border-color:rgba(239,68,68,0.3)">── 心率</span>
              <span class="rw-tag" style="color:#f97316;border-color:rgba(249,115,22,0.3)">── 血氧</span>
              <span class="rw-tag" style="color:#22c55e;border-color:rgba(34,197,94,0.3)">── 体温</span>
              <span class="rw-tag" style="color:#00d4ff;border-color:rgba(0,212,255,0.3)">── 压力</span>
            </div>
          </div>
          <div class="rw-pc">
            <div ref="trendRef" style="width:100%;height:100%"></div>
          </div>
        </div>

        <!-- 预警列表 + 右侧面板 -->
        <div class="rw-panel rw-panel-list">

          <!-- 列表区域 -->
          <div class="rw-list-wrap">
            <div class="rw-ph">
              <span class="rw-ph-bar"></span>
              <span class="rw-ph-title">当天预警数据</span>
              <span class="rw-rt-total">共 {{ totalWarnings }} 条</span>
              <div class="rw-filter-bar">
                <input v-model="filterName" class="rw-filter-input" placeholder="姓名/工号/部门" @keyup.enter="applyListFilters" />
                <el-select v-model="filterLevel" class="rw-filter-select" popper-class="rw-filter-select-popper" size="small" @change="applyListFilters">
                  <el-option label="全部级别" value="" />
                  <el-option label="高危" value="高危" />
                  <el-option label="中危" value="中危" />
                  <el-option label="低危" value="低危" />
                </el-select>
                <el-select v-model="filterType" class="rw-filter-select" popper-class="rw-filter-select-popper" size="small" @change="applyListFilters">
                  <el-option label="全部类型" value="" />
                  <el-option v-for="t in warningTypes" :key="t" :label="t" :value="t" />
                </el-select>
                <button class="rw-scroll-btn" @click="applyListFilters">查询</button>
                <button class="rw-scroll-btn" @click="toggleAutoScroll">
                  {{ autoScrollPaused ? '继续滚动' : '暂停滚动' }}
                </button>
                <button class="rw-export-btn" @click="exportWarnings" title="导出本页Excel">导出本页</button>
                <button class="rw-batch-btn" v-if="selectedKeys.length > 0" @click="batchHandle" :disabled="batchHandling">
                  {{ batchHandling ? '处理中...' : `批量处理 (${selectedKeys.length})` }}
                </button>
              </div>
            </div>

            <div class="rw-mobile-cards">
              <div
                v-for="(item, i) in filteredList"
                :key="warningLocatorKey(item) || `${item.userName || 'warning'}_${i}`"
                :class="['rw-mobile-card', warnClass(item.warningLevel), selectedKeys.includes(warningLocatorKey(item)) ? 'is-selected' : '']"
                role="button"
                tabindex="0"
                @click="openDetail(item)"
                @keydown.enter.prevent="openDetail(item)"
                @keydown.space.prevent="openDetail(item)"
              >
                <div class="rw-mobile-card-head">
                  <label class="rw-mobile-check" @click.stop>
                    <input type="checkbox" v-if="!item.handled" :checked="selectedKeys.includes(warningLocatorKey(item))" @change="toggleSelect(item)" />
                  </label>
                  <div class="rw-mobile-person">
                    <span class="rw-mobile-name">{{ item.userName || '--' }}</span>
                    <span class="rw-mobile-sub">{{ item.deptName || '--' }} · {{ item.empCode || item.userCode || '--' }}</span>
                  </div>
                  <span class="rw-list-badge" :class="warnClass(item.warningLevel)">{{ item.warningLevel || '--' }}</span>
                </div>
                <div class="rw-mobile-grid">
                  <span><b>类型</b>{{ item.warningType || '--' }}</span>
                  <span><b>预警值</b>{{ item.warningValue || '--' }}</span>
                  <span><b>状态</b><em :class="item.handled ? 'handled' : 'pending'">{{ item.handled ? '已处理' : '待处理' }}</em></span>
                  <span><b>时间</b>{{ fmtTime(item.createTime) }}</span>
                </div>
                <div class="rw-mobile-note" v-if="item.handleNote">{{ item.handleNote }}</div>
              </div>
              <div v-if="filteredList.length===0" class="rw-list-empty">暂无匹配数据</div>
            </div>

            <div class="rw-list-hd">
              <span class="rw-list-chk-cell"><input type="checkbox" :checked="allPendingSelected" :indeterminate.prop="somePendingSelected && !allPendingSelected" @change="toggleSelectAll" /></span>
              <span>序号</span><span>姓名</span><span>工号</span><span>部门</span><span>预警类型</span><span>级别</span><span>预警值</span><span>状态</span><span>备注</span><span>时间</span>
            </div>

            <div class="rw-list-body" ref="listRef"
              @mouseenter="pauseAutoScroll"
              @mouseleave="resumeAutoScroll">
              <div class="rw-list-row" v-for="(item, i) in filteredList" :key="warningLocatorKey(item)"
                :class="[warnClass(item.warningLevel), selectedKeys.includes(warningLocatorKey(item)) ? 'is-selected' : '']"
                @click="openDetail(item)">
                <span class="rw-list-chk-cell" @click.stop>
                  <input type="checkbox" v-if="!item.handled" :checked="selectedKeys.includes(warningLocatorKey(item))" @change="toggleSelect(item)" />
                </span>
                <span class="rw-list-idx">{{ listRowIndex(i) }}</span>
                <span class="rw-list-name">{{ item.userName || '--' }}</span>
                <span class="rw-list-code">{{ item.empCode || item.userCode || '--' }}</span>
                <span class="rw-list-dept">{{ item.deptName || '--' }}</span>
                <span class="rw-list-type">{{ item.warningType || '--' }}</span>
                <span class="rw-list-badge" :class="warnClass(item.warningLevel)">{{ item.warningLevel || '--' }}</span>
                <span class="rw-list-val">{{ item.warningValue || '--' }}</span>
                <span class="rw-list-handled" :class="item.handled ? 'handled' : 'pending'">{{ item.handled ? '已处理' : '待处理' }}</span>
                <span class="rw-list-note">{{ item.handleNote || '--' }}</span>
                <span class="rw-list-time">{{ fmtTime(item.createTime) }}</span>
              </div>
              <div v-if="filteredList.length===0" class="rw-list-empty">暂无匹配数据</div>
            </div>

            <div class="rw-list-pg" v-if="totalWarnings > 0">
              <button class="rw-pg-btn" :disabled="currentPage <= 1" @click="jumpPage(currentPage - 1)">上一页</button>
              <span class="rw-pg-info">{{ currentPage }} / {{ totalPages }}</span>
              <span class="rw-pg-total">共 {{ totalWarnings }} 条</span>
              <button class="rw-pg-btn" :disabled="currentPage >= totalPages" @click="jumpPage(currentPage + 1)">下一页</button>
            </div>

          </div>

        </div>
      </main>
    </section>

    <!-- ══ 预警详情 + 体征曲线 Drawer ══ -->
    <el-drawer
      v-model="detailVisible"
      :title="detailRow ? detailRow.userName + ' · 预警详情' : '预警详情'"
      width="580px"
      direction="rtl"
      class="rw-detail-drawer"
      @close="onDrawerClose"
    >
      <div v-if="detailRow" class="rw-dw-wrap">

        <!-- 顶部信息卡 -->
        <div class="rw-dw-card">
          <div class="rw-dw-avatar">{{ (detailRow.userName||'?').charAt(0) }}</div>
          <div class="rw-dw-main">
            <div class="rw-dw-name">{{ detailRow.userName||'--' }}</div>
            <div class="rw-dw-sub">{{ detailRow.deptName||'--' }} · {{ fmtTimeFull(detailRow.createTime) }}</div>
          </div>
          <div class="rw-dw-tags">
            <span :class="'badge-'+warnClass(detailRow.warningLevel)">{{ detailRow.warningLevel||'--' }}</span>
            <span :class="detailRow.handled?'badge-handled':'badge-pending'">{{ detailRow.handled?'已处理':'待处理' }}</span>
          </div>
        </div>

        <!-- KV 行 -->
        <div class="rw-dw-kvrow">
          <div class="rw-dw-kv"><span class="rw-dw-k">预警类型</span><span class="rw-dw-v">{{ detailRow.warningType||'--' }}</span></div>
          <div class="rw-dw-kv"><span class="rw-dw-k">预警值</span><span class="rw-dw-v rw-dw-num">{{ detailRow.warningValue||'--' }}</span></div>
          <div class="rw-dw-kv" v-if="detailRow.handleNote"><span class="rw-dw-k">备注</span><span class="rw-dw-v">{{ detailRow.handleNote }}</span></div>
        </div>

        <!-- 一键处理区 -->
        <div class="rw-dw-handle-section" v-if="!detailRow.handled">
          <div class="rw-dw-handle-title">处置记录</div>
          <textarea v-model="handleNote" class="rw-dw-handle-input" placeholder="输入处置备注（可选）..." rows="2"></textarea>
          <button class="rw-dw-handle-btn" :disabled="handling" @click="doHandle">
            {{ handling ? '处理中...' : '标记为已处理' }}
          </button>
        </div>
        <div class="rw-dw-handled-tip" v-else>
          <span class="rw-dw-handled-badge">已处理</span>
          <span v-if="detailRow.handleNote">：{{ detailRow.handleNote }}</span>
        </div>

        <!-- 体征曲线 -->
        <div class="rw-dw-chart-section">
          <div class="rw-dw-chart-hd">
            <span class="rw-dw-chart-title">预警时刻前后 1 小时体征曲线</span>
            <span class="rw-dw-chart-range">{{ vitalChartRange }}</span>
          </div>

          <div v-if="vitalLoading" class="rw-dw-loading">
            <div class="rw-dw-spinner"></div><span>加载体征数据…</span>
          </div>

          <div v-else-if="vitalEmpty" class="rw-dw-empty">
            <div class="rw-dw-empty-mark">LOG</div><p>该时段暂无体征历史记录</p>
          </div>

          <template v-else>
            <div ref="vitalChartRef" class="rw-dw-chart"></div>
            <!-- 峰值摘要 -->
            <div class="rw-dw-summary">
              <div v-for="s in vitalSummary" :key="s.label" class="rw-dw-si">
                <div class="rw-dw-si-label">{{ s.label }}</div>
                <div class="rw-dw-si-val" :style="{color:s.color}">{{ s.val }}</div>
                <div class="rw-dw-si-sub">{{ s.sub }}</div>
              </div>
            </div>
          </template>
        </div>

      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import WarningCenterNav from '@/components/WarningCenterNav.vue'
import { useRiskWarningPage } from './use-risk-warning-page'

const {
  activePeriod,
  allPendingSelected,
  applyListFilters,
  autoScrollPaused,
  batchHandle,
  batchHandling,
  currentPage,
  currentTime,
  deptRef,
  detailRow,
  detailVisible,
  doHandle,
  exportWarnings,
  filterLevel,
  filterName,
  filterType,
  filteredList,
  fmtTime,
  fmtTimeFull,
  handleNote,
  handling,
  headerKpis,
  jumpPage,
  listRef,
  listRowIndex,
  onDrawerClose,
  openDetail,
  pageSize,
  pauseAutoScroll,
  periodOptions,
  resumeAutoScroll,
  selectedKeys,
  somePendingSelected,
  statBarWidth,
  statTitle,
  toggleAutoScroll,
  toggleSelect,
  toggleSelectAll,
  totalPages,
  totalWarnings,
  trendRef,
  trendTitle,
  vitalChartRange,
  vitalChartRef,
  vitalEmpty,
  vitalLoading,
  vitalSummary,
  warnClass,
  warningLocatorKey,
  warningStats,
  warningTypes,
  switchPeriod
} = useRiskWarningPage()
</script>

<style lang="scss" scoped>
@import './risk-warning.scss';
</style>

<style lang="scss">
.rw-detail-drawer {
  background:#0d1228!important; border-left:1px solid rgba(0,212,255,.2)!important;
  .el-drawer__header { background:rgba(0,6,24,.8)!important; border-bottom:1px solid rgba(0,212,255,.15)!important; margin-bottom:0!important; padding:16px 20px!important; .el-drawer__title{color:#e8f4ff!important;font-size:15px!important;font-weight:600!important;} }
  .el-drawer__close-btn { color:#6a88ab!important; &:hover{color:#00d4ff!important;} }
  .el-drawer__body { padding:0!important; background:#0d1228!important; }
}

.rw-filter-select-popper {
  background:#0d1228 !important;
  border:1px solid rgba(0,212,255,.24) !important;
  box-shadow:0 8px 24px rgba(0,0,0,.4) !important;
  .el-select-dropdown__list { padding:4px; }
  .el-select-dropdown__item {
    height:30px;
    padding:0 10px;
    border-radius:3px;
    color:#a8c5e6;
    font-size:12px;
    line-height:30px;
    &.hover,
    &:hover { background:rgba(0,212,255,.1); color:#e8f4ff; }
    &.selected { background:rgba(0,212,255,.16); color:#d9f7ff; font-weight:700; }
  }
  .el-popper__arrow::before { background:#0d1228 !important; border-color:rgba(0,212,255,.24) !important; }
}
</style>
