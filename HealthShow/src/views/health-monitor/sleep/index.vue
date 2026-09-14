<template>
  <div class="sl-root">

    <!-- ══ Header ══ -->
    <header class="sl-hd">
      <div class="sl-hd-left">
        <span class="sl-live-dot"></span>
        <h1 class="sl-hd-title">睡眠分析</h1>
        <span class="sl-hd-date">{{ yesterdayDate }} 昨夜汇总</span>
      </div>
      <div class="sl-hd-kpis">
        <div class="sl-kpi" v-for="k in headerKpis" :key="k.label">
          <span class="sl-kpi-n" :class="k.cls">{{ k.val }}</span>
          <span class="sl-kpi-l">{{ k.label }}</span>
        </div>
      </div>
      <div class="sl-hd-time">{{ currentTime }}</div>
      <button class="hm-export-btn" @click="exportExcel" title="导出当前数据">导出</button>
    </header>

    <!-- ══ Body ══ -->
    <section class="sl-bd">

      <!-- ─ 左侧：4个概况卡 + 睡眠阶段环图 + 评分分布柱图 ─ -->
      <aside class="sl-aside">

        <!-- 2×2 概况指标卡 -->
        <div class="sl-stat-grid">
          <div class="sl-stat-card" v-for="c in statCards" :key="c.label" :style="{'--c': c.color}">
            <div class="sl-stat-icon" :style="{background: c.bg}">
              <span :style="{color: c.color}">{{ c.icon }}</span>
            </div>
            <div class="sl-stat-body">
              <div class="sl-stat-val" :style="{color: c.color}">{{ c.val }}</div>
              <div class="sl-stat-label">{{ c.label }}</div>
            </div>
          </div>
        </div>

        <!-- 睡眠阶段占比 -->
        <div class="sl-panel sl-stage-panel">
          <div class="sl-ph">
            <span class="sl-ph-bar"></span>
            <span class="sl-ph-title">昨夜睡眠阶段分布</span>
          </div>
          <div class="sl-stage-body">
            <div ref="stageRef" class="sl-stage-chart"></div>
            <div class="sl-stage-legend">
              <div class="sl-stage-row" v-for="s in stageLegend" :key="s.name">
                <div class="sl-stage-dot" :style="{background: s.color}"></div>
                <span class="sl-stage-name">{{ s.name }}</span>
                <div class="sl-stage-bar-wrap">
                  <div class="sl-stage-bar" :style="{width: s.value+'%', background: s.color}"></div>
                </div>
                <span class="sl-stage-pct" :style="{color: s.color}">{{ s.value }}%</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 质量评分分布 -->
        <div class="sl-panel sl-score-panel">
          <div class="sl-ph">
            <span class="sl-ph-bar"></span>
            <span class="sl-ph-title">睡眠质量评分分布</span>
          </div>
          <div class="sl-pc">
            <div ref="scoreRef" style="width:100%;height:100%"></div>
          </div>
        </div>

        <!-- 昨日睡眠异常预警 -->
        <div class="sl-panel sl-alert-panel">
          <div class="sl-ph">
            <span class="sl-ph-bar" style="background:linear-gradient(180deg,#ff5252,rgba(255,82,82,0.3))"></span>
            <span class="sl-ph-title">昨日睡眠异常预警</span>
            <span class="sl-alert-count">{{ alertList.length }} 人</span>
          </div>
          <div class="sl-alert-body">
            <div v-if="!alertList.length" class="sl-alert-empty">昨夜无睡眠异常预警</div>
            <div class="sl-alert-row" v-for="(a, i) in alertList" :key="i" :class="a.level">
              <span class="sl-alert-name">{{ a.name }}</span>
              <span class="sl-alert-tag" :class="a.level">{{ a.tag }}</span>
              <span class="sl-alert-val">{{ a.val }}</span>
              <span class="sl-alert-desc">{{ a.desc }}</span>
            </div>
          </div>
        </div>

      </aside>

      <!-- ─ 中间：趋势(大) + 三列小图 ─ -->
      <main class="sl-main">

        <!-- 近30天趋势：睡眠时长柱 + 评分折线 双轴 -->
        <div class="sl-panel sl-trend-panel">
          <div class="sl-ph">
            <span class="sl-ph-bar"></span>
            <span class="sl-ph-title">近30天睡眠趋势</span>
            <div class="sl-trend-tags">
              <span class="sl-tag" style="color:#a78bfa;border-color:rgba(167,139,250,0.3)">▌ 睡眠时长(h)</span>
              <span class="sl-tag" style="color:#52c41a;border-color:rgba(82,196,26,0.3)">── 质量评分</span>
              <span class="sl-tag" style="color:#FFB84D;border-color:rgba(255,184,77,0.3)">- - 建议时长(7h)</span>
            </div>
          </div>
          <div class="sl-pc">
            <div ref="trendRef" style="width:100%;height:100%"></div>
          </div>
        </div>

        <!-- 上下两层布局 -->
        <div class="sl-mid-row">

          <!-- 上层：睡眠时长 + 入睡时间（高度约半） -->
          <div class="sl-mid-top">

            <!-- 睡眠时长分布 -->
            <div class="sl-panel sl-duration-panel">
              <div class="sl-ph">
                <span class="sl-ph-bar"></span>
                <span class="sl-ph-title">睡眠时长分布</span>
                <span class="sl-ph-sub">{{ overview.totalCount || 0 }} 人</span>
              </div>
              <div class="sl-dur-segbar">
                <div class="sl-dur-seg" v-for="(d,i) in durationLegend" :key="d.name"
                  :style="{flex:d.value, background:d.color,
                    borderRadius: i===0?'5px 0 0 5px': i===durationLegend.length-1?'0 5px 5px 0':'0'}">
                </div>
              </div>
              <div class="sl-dur-seglabels">
                <span class="sl-dur-seglabel" v-for="d in durationLegend" :key="d.name" :style="{flex:d.value}">
                  <em :style="{background:d.color}"></em>{{ d.name }} {{ d.value }}%
                </span>
              </div>
              <div class="sl-dur-body">
                <div ref="durationRef" class="sl-dur-chart"></div>
                <div class="sl-dur-tips">
                  <div class="sl-dur-tip-title">时长健康参考</div>
                  <div class="sl-dur-tip-row" v-for="t in durationTips" :key="t.label">
                    <span class="sl-dur-tip-dot" :style="{background: t.color}"></span>
                    <span class="sl-dur-tip-name" :style="{color: t.color}">{{ t.label }}</span>
                    <span class="sl-dur-tip-desc">{{ t.desc }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- 入睡时间分布 -->
            <div class="sl-panel sl-bedtime-panel">
              <div class="sl-ph">
                <span class="sl-ph-bar"></span>
                <span class="sl-ph-title">入睡时间分布</span>
                <span class="sl-ph-sub" style="color:#4a6080">暂无入睡时刻数据</span>
              </div>
              <div class="sl-bed-body">
                <div ref="bedtimeRef" class="sl-bed-chart"></div>
                <div class="sl-bed-tips">
                  <div class="sl-bed-tip-title">入睡时间与健康关联</div>
                  <div class="sl-bed-tip-row" v-for="b in bedtimeTips" :key="b.time">
                    <span class="sl-bed-tip-icon" :style="{color: b.color}">{{ b.icon }}</span>
                    <span class="sl-bed-tip-time" :style="{color: b.color}">{{ b.time }}</span>
                    <span class="sl-bed-tip-desc">{{ b.desc }}</span>
                  </div>
                  <div class="sl-bed-warn" style="color:#4a6080">
                    <span class="sl-bed-warn-dot" style="background:#4a6080"></span>
                    <span>设备暂未采集入睡时刻，分布图不可用</span>
                  </div>
                </div>
              </div>
            </div>

          </div>

          <!-- 下层：各部门数据上传率（全宽横向，动态从API获取所有部门）-->
          <div class="sl-panel sl-dept-panel">
            <div class="sl-ph">
              <span class="sl-ph-bar"></span>
              <span class="sl-ph-title">各部门数据上传率</span>
              <span class="sl-ph-sub">共 {{ deptUploadList.length }} 个部门</span>
            </div>
            <div class="sl-pc">
              <div ref="deptRef" style="width:100%;height:100%"></div>
            </div>
          </div>

        </div>

      </main>

      <!-- ─ 右侧：数据明细 ─ -->
      <div class="sl-rtlist">
        <div class="sl-panel hm-panel-flex">
          <div class="sl-ph">
            <span class="sl-ph-bar"></span>
            <span class="sl-ph-title">数据明细</span>
            <span class="sl-rt-total">{{ detailList.length }} 条</span>
          </div>
          <div class="sl-rt-hd">
            <span>姓名</span><span>时长</span><span>评分</span><span>评级</span><span>时间</span>
          </div>
          <div class="sl-rt-body" ref="listRef">
            <div
              class="sl-rt-row"
              v-for="(item, i) in pagedList"
              :key="i"
              :class="item.level"
              style="cursor:pointer"
              @click="openRecordDialog(item)"
            >
              <span class="sl-rt-name">{{ item.userName }}</span>
              <span class="sl-rt-dur">{{ item.sleepHours }}</span>
              <span class="sl-rt-score" :class="scoreClass(item.score)">{{ item.score }}</span>
              <span class="sl-rt-badge" :class="item.level">{{ item.levelText }}</span>
              <span class="sl-rt-time">{{ fmtTime(item.recordTime) }}</span>
            </div>
          </div>
          <div class="sl-rt-pg">
            <button class="sl-pg-btn" :disabled="currentPage===1" @click="currentPage=1">首页</button>
            <button class="sl-pg-btn" :disabled="currentPage===1" @click="currentPage--">‹</button>
            <span class="sl-pg-info">{{ currentPage }} / {{ totalPages }}</span>
            <button class="sl-pg-btn" :disabled="currentPage>=totalPages" @click="currentPage++">›</button>
            <button class="sl-pg-btn" :disabled="currentPage>=totalPages" @click="currentPage=totalPages">末页</button>
          </div>
        </div>
      </div>

    </section>

  <!-- ══ 睡眠记录详情弹窗 ══ -->
  <el-dialog
    v-model="recordDialog.visible"
    title="睡眠记录详情"
    width="420px"
    :append-to-body="true"
    class="sl-record-dialog"
  >
    <div v-if="recordDialog.item" class="sl-rd-body">
      <div class="sl-rd-row"><span class="sl-rd-key">姓名</span><span class="sl-rd-val">{{ recordDialog.item.userName }}</span></div>
      <div class="sl-rd-row"><span class="sl-rd-key">睡眠时长</span><span class="sl-rd-val" style="color:#a78bfa">{{ recordDialog.item.sleepHours }}</span></div>
      <div class="sl-rd-row"><span class="sl-rd-key">质量评分</span>
        <span class="sl-rd-val" :class="scoreClass(recordDialog.item.score)" style="font-weight:700">{{ recordDialog.item.score }} 分</span>
      </div>
      <div class="sl-rd-row"><span class="sl-rd-key">评级</span>
        <span class="sl-rt-badge" :class="recordDialog.item.level" style="font-size:12px;padding:2px 8px">{{ recordDialog.item.levelText }}</span>
      </div>
      <div class="sl-rd-row"><span class="sl-rd-key">记录时间</span><span class="sl-rd-val">{{ fmtTime(recordDialog.item.recordTime) }}</span></div>
      <div class="sl-rd-row" v-if="recordDialog.item.deepSleep != null">
        <span class="sl-rd-key">深睡占比</span><span class="sl-rd-val" style="color:#4FC3F7">{{ recordDialog.item.deepSleep }}%</span>
      </div>
      <div class="sl-rd-row" v-if="recordDialog.item.remSleep != null">
        <span class="sl-rd-key">REM 占比</span><span class="sl-rd-val" style="color:#a78bfa">{{ recordDialog.item.remSleep }}%</span>
      </div>
      <div class="sl-rd-row" v-if="recordDialog.item.lightSleep != null">
        <span class="sl-rd-key">浅睡占比</span><span class="sl-rd-val" style="color:#52c41a">{{ recordDialog.item.lightSleep }}%</span>
      </div>
    </div>
    <template #footer>
      <el-button @click="recordDialog.visible = false">关闭</el-button>
    </template>
  </el-dialog>
</div><!-- /sl-root -->
</template>

<script setup lang="ts">
import { useSleepPage } from './use-sleep-page'

const {
  alertList,
  bedtimeRef,
  bedtimeTips,
  currentPage,
  currentTime,
  deptRef,
  deptUploadList,
  detailList,
  durationLegend,
  durationRef,
  durationTips,
  exportExcel,
  fmtTime,
  headerKpis,
  listRef,
  openRecordDialog,
  overview,
  pagedList,
  recordDialog,
  scoreClass,
  scoreRef,
  stageLegend,
  stageRef,
  statCards,
  totalPages,
  trendRef,
  yesterdayDate
} = useSleepPage()
</script>

<style lang="scss" scoped>
@import './sleep.scss';
</style>

<style>
.sl-record-dialog .el-dialog {
  background: rgba(8,13,35,0.97);
  border: 1px solid rgba(167,139,250,0.22);
  border-radius: 12px;
  color: #a8c5e6;
}
.sl-record-dialog .el-dialog__title { color: #e8f4ff; font-weight: 700; letter-spacing: 1px; }
.sl-record-dialog .el-dialog__header { border-bottom: 1px solid rgba(167,139,250,0.12); }
.sl-rd-body { display: flex; flex-direction: column; gap: 12px; padding: 4px 0; }
.sl-rd-row {
  display: flex; align-items: center; gap: 12px;
  padding: 8px 12px; border-radius: 6px;
  background: rgba(167,139,250,0.04);
  border: 1px solid rgba(167,139,250,0.08);
}
.sl-rd-key { font-size: 13px; color: #6a88ab; width: 72px; flex-shrink: 0; }
.sl-rd-val { font-size: 14px; color: #e8f4ff; font-family: 'Consolas', monospace; font-weight: 600; }
</style>

