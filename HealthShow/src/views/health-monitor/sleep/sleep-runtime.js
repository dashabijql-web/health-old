import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getSleepPageData, getSleepTrend, getSleepQualityDistribution } from '@/api/sleep'
import { emptyOption } from '@/utils/echarts-config'
import { gradV, initChart } from '@/utils/chart-helpers'
import { getDepartmentList } from '@/api/department'
import { exportToExcel } from '@/utils/export-excel'
import { createIntervalTask } from '@/utils/task-timer'

export const sleepPageRuntime = {
  mounted() {
    this.initClock()
    this.yesterdayDate = dayjs().subtract(1, 'day').format('MM月DD日')
    this.fetchData()
    this.$nextTick(() => {
      this.startAutoScroll()
      this.initBedtime()
    })
    this.__refreshFn = () => this.fetchData()
    this.__refreshInterval = 60000
    this._refreshTask = createIntervalTask(this.__refreshFn, 60000)
    this._refreshTask.start()
  },
  beforeUnmount() {
    if (this._refreshTask) this._refreshTask.stop()
  },
  methods: {
    async exportExcel() {
      const list = this.detailList
      if (!list.length) { ElMessage.warning('暂无数据可导出'); return }
      const data = list.map((r) => ({
        userName: r.userName || '--',
        deptName: r.deptName || '--',
        empCode: r.empCode || '--',
        sleepHours: r.sleepHours || '--',
        score: r.score ?? '--',
        levelText: r.levelText || '--',
        recordTime: r.recordTime ? dayjs(r.recordTime).format('YYYY-MM-DD HH:mm') : '--'
      }))
      const cols = [
        { label: '姓名', key: 'userName' },
        { label: '部门', key: 'deptName' },
        { label: '工号', key: 'empCode' },
        { label: '睡眠时长', key: 'sleepHours' },
        { label: '睡眠评分', key: 'score' },
        { label: '睡眠质量', key: 'levelText' },
        { label: '记录时间', key: 'recordTime' }
      ]
      await exportToExcel(data, cols, `睡眠分析_${dayjs().format('YYYYMMDD')}`)
      ElMessage.success(`已导出 ${list.length} 条记录`)
    },

    async fetchData() {
      const pageDataResult = await this.loadPageData()
      await Promise.allSettled([
        this.loadTrend(),
        this.loadQualityDist(),
        this.loadDept(pageDataResult)
      ])
    },

    async loadDept(deptUploadFromPageData) {
      let depts = []
      let uploadMap = {}
      try {
        const r = await getDepartmentList()
        if (r.code === 200 && r.data) {
          depts = r.data.list || r.data.rows || r.data.records || r.data || []
        }
      } catch {}
      const uploadList = Array.isArray(deptUploadFromPageData) && deptUploadFromPageData.length
        ? deptUploadFromPageData
        : []
      uploadList.forEach((d) => { uploadMap[d.deptName] = d.count })
      const list = depts.length > 0
        ? depts.map((d) => {
          const name = d.deptName || d.name || d.label || ''
          const realCount = uploadMap[name]
          return { deptName: name, count: realCount != null ? realCount : 0 }
        }).filter((d) => d.deptName)
        : null
      this.$nextTick(() => this.initDept(list))
    },

    async loadPageData() {
      try {
        const r = await getSleepPageData()
        if (r.code === 200 && r.data) {
          const d = r.data
          if (d.overview) this.overview = { ...this.overview, ...d.overview }
          if (d.durationLegend?.length) this.durationLegend = d.durationLegend
          if (d.categoryLegend?.length) this.stageLegend = d.categoryLegend
          if (d.detailList?.length) this.detailList = d.detailList
          if (d.deptUpload?.length) this.deptUploadList = d.deptUpload
          this.$nextTick(() => {
            this.initStage()
            this.initDuration()
          })
          return d.deptUpload || []
        }
      } catch {}
      this.$nextTick(() => {
        this.initStage()
        this.initDuration()
      })
      return []
    },

    async loadTrend() {
      let d = {}
      try { const r = await getSleepTrend(30); if (r.code === 200) d = r.data || {} } catch {}
      this.$nextTick(() => this.initTrend(d))
    },

    async loadQualityDist() {
      let d = []
      try {
        const r = await getSleepQualityDistribution()
        if (r.code === 200 && r.data) {
          const colorMap = { excellent: '#52c41a', good: '#4FC3F7', fair: '#FFB84D', poor: '#ff5252' }
          const labelMap = { excellent: '优秀(≥8h)', good: '良好(7-8h)', fair: '一般(6-7h)', poor: '较差(<6h)' }
          d = ['excellent', 'good', 'fair', 'poor']
            .filter((k) => r.data[k] > 0)
            .map((k) => ({ label: labelMap[k], count: r.data[k], color: colorMap[k] }))
        }
      } catch {}
      this.$nextTick(() => this.initScore(d))
    },

    initStage() {
      const c = initChart(this.charts, 'stage', this.$refs.stageRef); if (!c) return
      c.setOption({
        backgroundColor: 'transparent',
        series: [{
          type: 'pie', radius: ['50%', '78%'], center: ['50%', '50%'],
          label: { show: false }, labelLine: { show: false },
          cursor: 'pointer',
          data: this.stageLegend.map((x) => ({
            value: x.value, name: x.name,
            itemStyle: { color: x.color, borderRadius: 3, shadowColor: x.color + '55', shadowBlur: 8 }
          }))
        }]
      })
      c.on('click', (params) => { this.$message && this.$message.info(`${params.name}：${params.value}%`) })
    },

    initScore(data) {
      const c = initChart(this.charts, 'score', this.$refs.scoreRef); if (!c) return
      const d = Array.isArray(data) ? data : []
      if (!d.length) {
        c.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#8ba6c8', fontSize: 13, fontWeight: 'normal' } }, series: [] })
        return
      }
      c.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(8,13,35,0.9)', borderColor: 'rgba(167,139,250,0.3)',
          textStyle: { color: '#e0f0ff', fontSize: 11 },
          formatter: (p) => `${p[0].name}<br/>人数：<b style="color:#a78bfa">${p[0].value}</b> 人`
        },
        grid: { left: '2%', right: '4%', top: '8%', bottom: '16%', containLabel: true },
        xAxis: {
          type: 'category', data: d.map((x) => x.label),
          axisLine: { lineStyle: { color: 'rgba(167,139,250,0.15)' } }, axisTick: { show: false },
          axisLabel: { color: '#8ba6c8', fontSize: 9 }
        },
        yAxis: {
          type: 'value', axisLine: { show: false }, axisTick: { show: false },
          splitLine: { lineStyle: { color: 'rgba(167,139,250,0.07)', type: 'dashed' } },
          axisLabel: { color: '#8ba6c8', fontSize: 9 }
        },
        series: [{
          type: 'bar', barWidth: '55%',
          cursor: 'pointer',
          data: d.map((x) => ({
            value: x.count,
            itemStyle: { color: x.color, borderRadius: [4, 4, 0, 0], shadowColor: x.color + '44', shadowBlur: 6 }
          })),
          label: { show: true, position: 'top', color: '#a8c5e6', fontSize: 9 }
        }]
      })
      c.on('click', (params) => { this.$message && this.$message.info(`${params.name}：${params.value} 人`) })
    },

    initTrend(data) {
      const c = initChart(this.charts, 'trend', this.$refs.trendRef); if (!c) return
      const fbDates = Array.from({ length: 30 }, (_, i) => dayjs().subtract(29 - i, 'day').format('MM/DD'))
      const dates = data.dates || fbDates
      const hours = data.avgData || data.hours || new Array(dates.length).fill(0)
      const scores = data.scores || hours.map((h) => (h >= 8 ? 90 : h >= 7 ? 75 : h >= 6 ? 60 : h > 0 ? 40 : 0))
      c.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(8,13,35,0.92)', borderColor: 'rgba(167,139,250,0.3)',
          textStyle: { color: '#e0f0ff', fontSize: 11 },
          formatter: (p) => `${p[0].name}<br/>
            <span style="color:#a78bfa">睡眠时长：${p[0].value}h</span><br/>
            <span style="color:#52c41a">质量评分：${p[1]?.value ?? '--'}分</span>`
        },
        grid: { left: '5%', right: '5%', top: '10%', bottom: '12%', containLabel: true },
        xAxis: {
          type: 'category', data: dates, boundaryGap: true,
          axisLine: { lineStyle: { color: 'rgba(167,139,250,0.18)' } }, axisTick: { show: false },
          axisLabel: { color: '#8ba6c8', fontSize: 10, interval: 4 }
        },
        yAxis: [
          {
            type: 'value', name: '时长(h)', nameTextStyle: { color: '#a78bfa', fontSize: 10 },
            axisLine: { show: false }, axisTick: { show: false },
            splitLine: { lineStyle: { color: 'rgba(167,139,250,0.07)', type: 'dashed' } },
            axisLabel: { color: '#8ba6c8', fontSize: 9 },
            min: 0, max: 12
          },
          {
            type: 'value', name: '评分', nameTextStyle: { color: '#52c41a', fontSize: 10 },
            axisLine: { show: false }, axisTick: { show: false },
            splitLine: { show: false },
            axisLabel: { color: '#8ba6c8', fontSize: 9 },
            min: 0, max: 100
          }
        ],
        series: [
          {
            name: '睡眠时长', type: 'bar', yAxisIndex: 0, data: hours, barWidth: '55%',
            itemStyle: {
              color: gradV('rgba(167,139,250,0.9)', 'rgba(167,139,250,0.18)'),
              borderRadius: [3, 3, 0, 0]
            },
            markLine: {
              silent: true, symbol: 'none',
              data: [{ yAxis: 7, lineStyle: { color: '#FFB84D', type: 'dashed', width: 1 },
                label: { color: '#FFB84D', fontSize: 10, formatter: '建议7h' } }]
            }
          },
          {
            name: '质量评分', type: 'line', yAxisIndex: 1, data: scores, smooth: true, symbol: 'none',
            lineStyle: { color: '#52c41a', width: 2 },
            areaStyle: { color: gradV('rgba(82,196,26,0.18)', 'rgba(82,196,26,0.02)') },
            markPoint: {
              symbol: 'circle', symbolSize: 5,
              label: { fontSize: 9, fontFamily: 'Consolas', offset: [0, -12] },
              data: [
                { type: 'max', itemStyle: { color: '#52c41a' }, label: { color: '#52c41a', formatter: (p) => '▲' + p.value } },
                { type: 'min', itemStyle: { color: '#ff5252' }, label: { color: '#ff5252', formatter: (p) => '▼' + p.value } }
              ]
            }
          }
        ]
      })
      c.on('click', (params) => {
        if (params.seriesName === '睡眠时长') {
          this.$message && this.$message.info(`${params.name} 睡眠时长：${params.value}h`)
        } else if (params.seriesName === '质量评分') {
          this.$message && this.$message.info(`${params.name} 质量评分：${params.value}分`)
        }
      })
    },

    initDuration() {
      const c = initChart(this.charts, 'duration', this.$refs.durationRef); if (!c) return
      const total = this.overview.totalCount || 0
      c.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'item',
          backgroundColor: 'rgba(8,13,35,0.9)', borderColor: 'rgba(167,139,250,0.25)',
          textStyle: { color: '#e0f0ff', fontSize: 11 },
          formatter: (p) => `${p.name}<br/>占比：<b style="color:${p.color}">${p.value}%</b><br/>约 ${Math.round(p.value * total / 100)} 人`
        },
        title: {
          text: '检测人数', subtext: total + '人',
          left: 'center', top: '36%',
          textStyle: { color: '#6a88ab', fontSize: 10 },
          subtextStyle: { color: '#a78bfa', fontSize: 16, fontWeight: 'bold', fontFamily: 'Consolas' }
        },
        series: [{
          type: 'pie', radius: ['42%', '64%'], center: ['50%', '52%'],
          label: { show: false }, labelLine: { show: false },
          cursor: 'pointer',
          data: this.durationLegend.map((x) => ({
            value: x.value, name: x.name,
            itemStyle: { color: x.color, borderRadius: 3, shadowColor: x.color + '44', shadowBlur: 6 }
          }))
        }]
      })
      c.on('click', (params) => {
        const count = Math.round(params.value * (this.overview.totalCount || 0) / 100)
        this.$message && this.$message.info(`${params.name}：${params.value}%（约 ${count} 人）`)
      })
    },

    initBedtime() {
      const c = initChart(this.charts, 'bedtime', this.$refs.bedtimeRef); if (!c) return
      this.lateBedPct = 0
      c.setOption(emptyOption('暂无数据', 13))
    },

    initDept(data) {
      const c = initChart(this.charts, 'dept', this.$refs.deptRef); if (!c) return
      if (!data || !data.length) {
        c.setOption(emptyOption())
        return
      }
      const list = data
      this.deptUploadList = list
      const sorted = [...list].sort((a, b) => b.count - a.count)
      const getColor = (v) => v >= 90 ? '#52c41a' : v >= 75 ? '#4FC3F7' : v >= 60 ? '#FFB84D' : '#ff5252'
      const barMaxW = Math.max(10, Math.min(28, Math.floor(900 / sorted.length) - 4))
      c.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis', axisPointer: { type: 'none' },
          backgroundColor: 'rgba(8,13,35,0.9)', borderColor: 'rgba(167,139,250,0.25)',
          textStyle: { color: '#e0f0ff', fontSize: 11 },
          formatter: (p) => `${p[0].name}：<b style="color:${p[0].color}">${p[0].value}%</b>`
        },
        grid: { left: 32, right: 12, top: 24, bottom: 56 },
        xAxis: {
          type: 'category',
          data: sorted.map((x) => x.deptName),
          axisLine: { lineStyle: { color: 'rgba(167,139,250,0.15)' } },
          axisTick: { show: false },
          axisLabel: {
            color: '#8ba6c8',
            fontSize: sorted.length > 15 ? 8 : 9,
            rotate: sorted.length > 12 ? 35 : 0,
            interval: 0,
            overflow: 'truncate',
            width: 52
          }
        },
        yAxis: {
          type: 'value', min: 0, max: 100,
          axisLine: { show: false }, axisTick: { show: false },
          splitLine: { lineStyle: { color: 'rgba(167,139,250,0.07)', type: 'dashed' } },
          axisLabel: { color: '#8ba6c8', fontSize: 9, formatter: (v) => v + '%' }
        },
        series: [{
          type: 'bar',
          barMaxWidth: barMaxW,
          data: sorted.map((x) => ({
            value: x.count,
            itemStyle: {
              color: gradV(getColor(x.count), getColor(x.count) + '55'),
              borderRadius: [4, 4, 0, 0]
            }
          })),
          label: {
            show: true, position: 'top',
            color: '#a8c5e6', fontSize: sorted.length > 15 ? 8 : 9,
            formatter: (p) => p.value + '%'
          },
          showBackground: true,
          backgroundStyle: { color: 'rgba(167,139,250,0.05)', borderRadius: [4, 4, 0, 0] }
        }]
      })
    },

    scoreClass(s) {
      if (s >= 80) return 'sc-excellent'
      if (s >= 60) return 'sc-good'
      if (s >= 40) return 'sc-fair'
      return 'sc-poor'
    },

    openRecordDialog(item) {
      this.recordDialog.item = item
      this.recordDialog.visible = true
    }
  }
}
