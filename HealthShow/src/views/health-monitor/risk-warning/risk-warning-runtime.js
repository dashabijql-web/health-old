import * as echarts from '@/utils/echarts-setup'
import dayjs from 'dayjs'
import { getRiskWarningOverview, getRiskWarningList, getRiskWarningTrend, getRiskWarningTypeDistribution, getDeptWarningStats, handleRiskWarning, handleBatchRiskWarning } from '@/api/risk-warning'
import { exportToExcel } from '@/utils/export-excel'
import { emptyOption, chartTooltip, categoryAxis, valueAxis, deptGrid, trendGrid, barLabel } from '@/utils/echarts-config'
import { initChart, gradV } from '@/utils/chart-helpers'
import { getHealthRecords } from '@/api/health'
import { createScrollLoop } from '@/composables/useScrollLoop'

export const riskWarningPageRuntime = {
  mounted() { this.initPage() },
  methods: {
    async fetchData() {
      await Promise.allSettled([this.loadStats(), this.loadTrend(), this.loadDept(), this.loadTypes(), this.loadList()])
      this.$nextTick(() => this.initDonutChart())
    },
    async loadStats() {
      const { startDate, endDate } = this.periodRange
      try {
        const r = await getRiskWarningOverview(startDate, endDate)
        if (r.code===200 && r.data) {
          const d=r.data
          this.warningStats[0].value=d.heartRateCount||0; this.warningStats[1].value=d.bloodOxygenCount||0
          this.warningStats[2].value=d.temperatureCount||0; this.warningStats[3].value=d.pressureCount||0
        }
      } catch {}
    },
    async loadTrend() {
      if (this.activePeriod==='day') { this.$nextTick(()=>this.initTrendDay()); return }
      const days = this.activePeriod==='week'?7:30
      try {
        const r = await getRiskWarningTrend(days)
        if (r.code===200 && r.data) {
          if (r.data.dates?.length) {
            this.trendData=r.data
          } else if (Array.isArray(r.data) && r.data.length) {
            const dates=[],heartRate=[],bloodOxygen=[],temperature=[],pressure=[]
            r.data.forEach(x=>{dates.push(x.date);heartRate.push(x.heartRate||0);bloodOxygen.push(x.bloodOxygen||0);temperature.push(x.temperature||0);pressure.push(x.pressure||0)})
            this.trendData={dates,series:{heartRate,bloodOxygen,temperature,pressure}}
          }
        }
      } catch {}
      this.$nextTick(()=>this.initTrendChart())
    },
    async loadDept() {
      const { startDate, endDate } = this.periodRange
      try {
        const r = await getDeptWarningStats(startDate, endDate)
        if (r.code===200 && r.data?.length) this.deptData=r.data
      } catch {}
      this.$nextTick(()=>this.initDeptChart())
    },
    async loadList() {
      try {
        const { startDate, endDate } = this.periodRange
        const r = await getRiskWarningList({
          page: this.currentPage,
          size: this.pageSize,
          keyword: this.filterName.trim() || undefined,
          level: this.filterLevel || undefined,
          warningType: this.filterType || undefined,
          handled: null,
          startDate,
          endDate
        })
        if (r.code===200 && r.data) {
          this.warningList = r.data.list || []
          this.totalWarnings = Number(r.data.total || 0)
          this.selectedKeys = []
        }
      } catch {}
    },
    async loadTypes() {
      try {
        const r = await getRiskWarningTypeDistribution()
        if (r.code === 200 && Array.isArray(r.data)) {
          const types = r.data.map(item => item.type).filter(Boolean)
          // 新库可以没有业务记录，保留默认类型选项，避免筛选菜单变成空白。
          if (types.length) this.warningTypeOptions = types
        }
      } catch {}
    },
    applyListFilters() {
      this.currentPage = 1
      this.loadList()
    },
    switchPeriod(period) {
      this.activePeriod = period
      this.currentPage = 1
      this.fetchData()
    },
    initTrendChart() {
      const c=initChart(this.charts,'trend',this.$refs.trendRef); if(!c) return
      const {dates,series}=this.trendData
      if(!dates.length){ c.setOption(emptyOption('暂无趋势数据')); return }
      c.setOption({
        backgroundColor:'transparent',
        tooltip:chartTooltip(),
        legend:{data:['心率','血氧','体温','压力'],right:10,top:4,textStyle:{color:'#8ba6c8',fontSize:11},itemWidth:16,itemHeight:8},
        grid:{left:'4%',right:'4%',top:'12%',bottom:'10%',containLabel:true},
        xAxis:{type:'category',data:dates,boundaryGap:false,axisLine:{lineStyle:{color:'rgba(0,212,255,0.18)'}},axisTick:{show:false},axisLabel:{color:'#8ba6c8',fontSize:10,interval:4}},
        yAxis:{type:'value',axisLine:{show:false},axisTick:{show:false},splitLine:{lineStyle:{color:'rgba(0,212,255,0.07)',type:'dashed'}},axisLabel:{color:'#8ba6c8',fontSize:10}},
        series:[
          {name:'心率',type:'line',data:series.heartRate,smooth:true,symbol:'none',lineStyle:{color:'#ef4444',width:1.5},itemStyle:{color:'#ef4444'}},
          {name:'血氧',type:'line',data:series.bloodOxygen,smooth:true,symbol:'none',lineStyle:{color:'#f97316',width:1.5},itemStyle:{color:'#f97316'}},
          {name:'体温',type:'line',data:series.temperature,smooth:true,symbol:'none',lineStyle:{color:'#22c55e',width:1.5},itemStyle:{color:'#22c55e'}},
          {name:'压力',type:'line',data:series.pressure,smooth:true,symbol:'none',lineStyle:{color:'#00d4ff',width:1.5},itemStyle:{color:'#00d4ff'}}
        ]
      })
    },

    initDeptChart() {
      const c=initChart(this.charts,'dept',this.$refs.deptRef); if(!c) return
      if(!this.deptData.length){ c.setOption(emptyOption()); return }
      const names=this.deptData.map(d=>d.deptName)
      // FIX ④: 动态左侧留白防截断
      const maxLen=Math.max(...names.map(n=>n.length))
      const leftPct=Math.min(42,Math.max(24,maxLen*2.8))+'%'
      c.setOption({
        backgroundColor:'transparent',
        tooltip:{trigger:'axis',axisPointer:{type:'shadow'},backgroundColor:'rgba(8,13,35,0.92)',borderColor:'rgba(0,212,255,0.25)',textStyle:{color:'#e0f0ff',fontSize:12}},
        legend:{data:['心率','血氧','体温','压力'],right:6,top:4,textStyle:{color:'#8ba6c8',fontSize:12},itemWidth:10,itemHeight:8,icon:'rect'},
        grid:{left:leftPct,right:'8%',top:'14%',bottom:'6%'},
        xAxis:{type:'value',axisLine:{show:false},axisTick:{show:false},splitLine:{lineStyle:{color:'rgba(0,212,255,0.07)',type:'dashed'}},axisLabel:{color:'#8ba6c8',fontSize:12}},
        yAxis:{type:'category',data:names,inverse:true,axisLine:{show:false},axisTick:{show:false},axisLabel:{color:'#a8c5e6',fontSize:12,overflow:'truncate',width:92}},
        series:[
          {name:'心率',type:'bar',stack:'total',barWidth:'50%',data:this.deptData.map(d=>d.heartRate||0),itemStyle:{color:'#ef4444'},label:{show:true,position:'inside',color:'#fff',fontSize:12,formatter:p=>p.value>0?p.value:''}},
          {name:'血氧',type:'bar',stack:'total',data:this.deptData.map(d=>d.bloodOxygen||0),itemStyle:{color:'#f97316'},label:{show:true,position:'inside',color:'#fff',fontSize:12,formatter:p=>p.value>0?p.value:''}},
          {name:'体温',type:'bar',stack:'total',data:this.deptData.map(d=>d.temperature||0),itemStyle:{color:'#22c55e'},label:{show:true,position:'inside',color:'#fff',fontSize:12,formatter:p=>p.value>0?p.value:''}},
          {name:'压力',type:'bar',stack:'total',data:this.deptData.map(d=>d.pressure||0),itemStyle:{color:'#00d4ff',borderRadius:[0,4,4,0]},label:{show:true,position:'inside',color:'#fff',fontSize:12,formatter:p=>p.value>0?p.value:''}}
        ]
      })
    },

    initTrendDay() {
      const c=initChart(this.charts,'trend',this.$refs.trendRef); if(!c) return
      const total=this.warningStats.reduce((s,x)=>s+x.value,0)
      if(!total){ c.setOption(emptyOption('今日暂无预警数据')); return }
      c.setOption({
        backgroundColor:'transparent',
        tooltip:{trigger:'axis',axisPointer:{type:'shadow'},backgroundColor:'rgba(8,13,35,0.92)',borderColor:'rgba(0,212,255,0.25)',textStyle:{color:'#e0f0ff',fontSize:12},formatter:p=>`${p[0].name}：<b style="color:${this.warningStats[p[0].dataIndex]?.color||'#00d4ff'}">${p[0].value}</b> 次`},
        grid:{left:'5%',right:'5%',top:'12%',bottom:'12%',containLabel:true},
        xAxis:{type:'category',data:this.warningStats.map(x=>x.label),axisLine:{lineStyle:{color:'rgba(0,212,255,0.18)'}},axisTick:{show:false},axisLabel:{color:'#a8c5e6',fontSize:12}},
        yAxis:{type:'value',axisLine:{show:false},axisTick:{show:false},splitLine:{lineStyle:{color:'rgba(0,212,255,0.07)',type:'dashed'}},axisLabel:{color:'#8ba6c8',fontSize:10}},
        series:[{type:'bar',barWidth:'40%',data:this.warningStats.map(x=>({value:x.value,itemStyle:{color:gradV(x.color,x.color+'55'),borderRadius:[6,6,0,0]}})),label:{show:true,position:'top',color:'#e0f0ff',fontSize:13,fontWeight:'bold',fontFamily:'Consolas'}}]
      })
    },

    // 右侧面板：环形图
    initDonutChart() {
      const c = initChart(this.charts,'donut',this.$refs.donutRef); if(!c) return
      const total = this.warningStats.reduce((s,x)=>s+x.value,0)
      if(!total) {
        c.setOption(emptyOption('暂无数据', 12))
        return
      }
      c.setOption({
        backgroundColor: 'transparent',
        tooltip: { trigger:'item', backgroundColor:'rgba(8,13,35,.95)', borderColor:'rgba(0,212,255,.3)', textStyle:{color:'#e0f0ff',fontSize:11}, formatter:p=>`${p.name}：${p.value} (${p.percent}%)` },
        series: [{ type:'pie', radius:['50%','75%'], center:['50%','50%'], label:{show:false}, emphasis:{scale:false},
          data: this.warningStats.map(x=>({ value:x.value, name:x.label, itemStyle:{color:x.color} })) }]
      })
    },
    donutPct(val) {
      const total = this.warningStats.reduce((s,x)=>s+x.value,0)
      return total ? Math.round(val/total*100)+'%' : '0%'
    },

    // 右侧面板：TOP5 点击打开详情
    openDetailByUser(u) {
      const first = this.warningList.find(x=>(x.empCode||x.userCode)===u.empCode)
      if(first) this.openDetail(first)
    },
    levelRank(lv) { return {高:3,危险:3,中:2,警告:2,低:1,提醒:1}[lv]||0 },
    levelLabel(lv) { return lv==='高'||lv==='危险'?'高危':lv==='中'||lv==='警告'?'中危':'注意' },
    levelClass(lv) { return lv==='高'||lv==='危险'?'danger':lv==='中'||lv==='警告'?'warn':'info' },

    async openDetail(item) {
      this.detailRow = item
      this.detailVisible = true
      this.vitalLoading = true
      this.vitalEmpty = false
      this.vitalSummary = []
      await this.$nextTick()
      await this.loadVitalChart(item)
    },

    async loadVitalChart(item) {
      try {
        const warnTime = dayjs(item.createTime)
        const empCode  = item.empCode || item.userCode || item.empId || ''
        let list = []
        let windowHours = 0  // 记录实际用的时间窗口
        let isDaily = false   // 是否降级为按天

        // ── 第一层：自动扩窗，±1h → ±6h，直到数据点 ≥ 3 ──
        for (const hours of [1, 3, 6]) {
          const start = warnTime.subtract(hours, 'hour')
          const end   = warnTime.add(hours, 'hour')
          try {
            const r = await getHealthRecords({
              pageNum: 1, pageSize: 200,
              empCode, userCode: empCode,
              startTime: start.format('YYYY-MM-DD HH:mm:ss'),
              endTime:   end.format('YYYY-MM-DD HH:mm:ss')
            })
            const rows = r?.data?.list || r?.data?.records || (Array.isArray(r?.data) ? r.data : [])
            if (rows.length >= 3) {
              list = rows
              windowHours = hours
              this.vitalChartRange = `${start.format('HH:mm')} – ${end.format('HH:mm')}（±${hours}h）`
              break
            } else if (rows.length > 0 && list.length === 0) {
              // 暂存最好结果，继续尝试更大窗口
              list = rows
              windowHours = hours
              this.vitalChartRange = `${start.format('HH:mm')} – ${end.format('HH:mm')}（±${hours}h）`
            }
          } catch(e) {
            /* retry with larger window */
          }
        }

        // ── 第二层兜底：数据点仍 < 3，改用 health-portrait 近7天日均趋势 ──
        if (list.length < 3 && empCode) {
          try {
            const { getHealthPortrait } = await import('@/api/health-portrait')
            const pr = await getHealthPortrait(empCode)
            const td = pr?.data?.trendData || pr?.data?.trend || pr?.data
            const dates = td?.dates || []
            const hArr  = td?.heartRates    || td?.heartRate    || []
            const sArr  = td?.bloodOxygens  || td?.bloodOxygen  || []
            const tArr  = td?.temperatures  || td?.temperature  || []
            if (dates.length >= 2) {
              list = dates.map((d, i) => ({
                recordTime: d + ' 00:00:00',
                heartRate:   hArr[i] || null,
                bloodOxygen: sArr[i] || null,
                temperature: tArr[i] || null
              }))
              isDaily = true
              this.vitalChartRange = '近7天日均趋势（采样间隔稀疏）'
            }
          } catch(e2) {
            /* fallback failed, continue */
          }
        }

        if (!list.length) { this.vitalEmpty = true; this.vitalLoading = false; return }

        list.sort((a, b) => new Date(a.time||a.recordTime||a.createTime) - new Date(b.time||b.recordTime||b.createTime))

        const times = list.map(d => {
          const t = d.time || d.recordTime || d.createTime
          return isDaily ? dayjs(t).format('MM/DD') : dayjs(t).format('HH:mm')
        })
        const hrs   = list.map(d => d.heartRate   ? +d.heartRate   : null)
        const spo2  = list.map(d => d.bloodOxygen ? +d.bloodOxygen : null)
        const temps = list.map(d => {
          if (!d.temperature) return null
          const t = +d.temperature
          return t > 100 ? +(t / 10).toFixed(1) : +t.toFixed(1)
        })

        const validHr   = hrs.filter(v => v != null)
        const validSpo2 = spo2.filter(v => v != null)
        const validTemp = temps.filter(v => v != null)
        this.vitalSummary = [
          { label:'最高心率', val: validHr.length   ? Math.max(...validHr)+'bpm'  : '--', sub:'正常 60-100', color:'#ef4444' },
          { label:'最低血氧', val: validSpo2.length  ? Math.min(...validSpo2)+'%'  : '--', sub:'正常 ≥95%',   color:'#f97316' },
          { label:'峰值体温', val: validTemp.length  ? Math.max(...validTemp)+'°C' : '--', sub:'正常 36-37.5',color:'#22c55e' },
          { label:'数据点数', val: list.length+'条',                                       sub:'采样点',      color:'#00d4ff' }
        ]

        this.vitalLoading = false
        await this.$nextTick()
        this.renderVitalChart(times, hrs, spo2, temps, isDaily ? '' : warnTime.format('HH:mm'))
      } catch(e) {
        this.vitalEmpty = true
        this.vitalLoading = false
      }
    },

    renderVitalChart(times, hrs, spo2, temps, warnTimeStr) {
      const el = this.$refs.vitalChartRef
      if (!el) return
      if (this.vitalChartInst) this.vitalChartInst.dispose()
      const c = echarts.init(el)
      this.vitalChartInst = c

      // 找预警时刻在 x 轴的索引
      const warnIdx = times.findIndex(t => t >= warnTimeStr)

      c.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(8,13,35,.95)',
          borderColor: 'rgba(0,212,255,.3)',
          textStyle: { color: '#e0f0ff', fontSize: 12 }
        },
        legend: {
          data: ['心率','血氧','体温'],
          top: 4, right: 8,
          textStyle: { color: '#8ba6c8', fontSize: 11 },
          itemWidth: 14, itemHeight: 8
        },
        grid: { left: 14, right: 14, top: 36, bottom: 32, containLabel: true },
        xAxis: {
          type: 'category', data: times, boundaryGap: false,
          axisLine: { lineStyle: { color: 'rgba(0,212,255,.2)' } },
          axisTick: { show: false },
          axisLabel: { color: '#8ba6c8', fontSize: 10, interval: Math.floor(times.length/6) }
        },
        yAxis: [
          // 左轴：心率 40-160，血氧映射到同轴但用右轴刻度显示
          { type:'value', name:'bpm / %',
            nameTextStyle:{color:'#8ba6c8',fontSize:9},
            min: 40, max: 160, interval: 20,
            axisLine:{show:false}, axisTick:{show:false},
            splitLine:{lineStyle:{color:'rgba(0,212,255,.07)',type:'dashed'}},
            axisLabel:{color:'#8ba6c8',fontSize:10} },
          // 右轴：体温 35.0-39.0°C
          { type:'value', name:'°C',
            nameTextStyle:{color:'#22c55e',fontSize:9},
            min: 35, max: 39, interval: 1,
            axisLine:{show:false}, axisTick:{show:false}, splitLine:{show:false},
            axisLabel:{color:'#22c55e',fontSize:10, formatter:v => v.toFixed(1)} },
        ],
        series: [
          {
            name:'心率', type:'line', color:'#ef4444', data: hrs, smooth: true, symbol:'none',
            lineStyle:{color:'#ef4444',width:2},
            areaStyle:{color:gradV('rgba(239,68,68,.25)','rgba(239,68,68,.02)')},
            markLine: warnIdx >= 0 ? {
              silent: true,
              symbol: ['none','none'],
              lineStyle: { color: '#ff3b3b', width: 2.5, type: 'solid', shadowColor: 'rgba(255,59,59,.6)', shadowBlur: 8 },
              data: [{ xAxis: warnTimeStr, label: {
                show: true, position: 'insideStartTop',
                formatter: '预警时刻',
                color: '#fff', fontSize: 11, fontWeight: 700,
                backgroundColor: '#ef4444',
                padding: [3,7,3,7], borderRadius: 4,
                shadowColor: 'rgba(239,68,68,.5)', shadowBlur: 6
              }}]
            } : {},
            markPoint: warnIdx >= 0 && hrs[warnIdx] != null ? {
              data: [{ coord:[warnTimeStr, hrs[warnIdx]], symbol:'circle', symbolSize:14,
                itemStyle:{color:'transparent',borderColor:'#ef4444',borderWidth:3}, label:{show:false} }]
            } : {}
          },
          { name:'血氧', type:'line', color:'#f97316', data: spo2, smooth:true, symbol:'none',
            yAxisIndex: 0,
            lineStyle:{color:'#f97316',width:2},
            areaStyle:{color:gradV('rgba(249,115,22,.2)','rgba(249,115,22,.01)')},
            markPoint: warnIdx >= 0 && spo2[warnIdx] != null ? {
              data: [{ coord:[warnTimeStr, spo2[warnIdx]], symbol:'circle', symbolSize:14,
                itemStyle:{color:'transparent',borderColor:'#f97316',borderWidth:3}, label:{show:false} }]
            } : {}
          },
          { name:'体温', type:'line', color:'#22c55e', data: temps, smooth:true, symbol:'none',
            yAxisIndex:1, lineStyle:{color:'#22c55e',width:2},
            markPoint: warnIdx >= 0 && temps[warnIdx] != null ? {
              data: [{ coord:[warnTimeStr, temps[warnIdx]], symbol:'circle', symbolSize:14,
                itemStyle:{color:'transparent',borderColor:'#22c55e',borderWidth:3}, label:{show:false} }]
            } : {}
          }
        ]
      })
    },

    onDrawerClose() {
      if (this.vitalChartInst) { this.vitalChartInst.dispose(); this.vitalChartInst = null }
      this.handleNote = ''
      this.handling = false
    },

    async doHandle() {
      if (!this.detailRow || this.handling) return
      this.handling = true
      try {
        const res = await handleRiskWarning(this.detailRow.id, {
          handleRemark: this.handleNote || '已确认处理',
          handleBy: 'admin',
          createTime: this.detailRow.createTime
        })
        if (res.code === 200) {
          const detailKey = this.warningLocatorKey(this.detailRow)
          const row = this.warningList.find(r => this.warningLocatorKey(r) === detailKey)
          if (row) { row.handled = true; row.handleNote = this.handleNote || '已确认处理' }
          this.detailRow = { ...this.detailRow, handled: true, handleNote: this.handleNote || '已确认处理' }
          this.$message?.success('处理成功') || alert('处理成功')
        } else {
          this.$message?.error(res.message || '处理失败') || alert(res.message || '处理失败')
        }
      } catch (e) {
        this.$message?.error('操作失败，请重试') || alert('操作失败')
      } finally {
        this.handling = false
      }
    },

    // FIX ①③: 翻页同步重置滚动，不打架
    async jumpPage(page) {
      const nextPage = Math.max(1, Math.min(page, this.totalPages))
      if (nextPage === this.currentPage) return
      this.currentPage = nextPage
      await this.loadList()
      this.$nextTick(()=>{ const el=this.$refs.listRef; if(el){el.scrollTop=0;this.scrollTop=0} })
    },

    startAutoScroll() {
      this.scrollLoop?.stop?.()
      this.scrollLoop = createScrollLoop({
        getElement: () => this.$refs.listRef,
        intervalMs: 40,
        step: 1,
        endPauseMs: 2000,
        shouldScroll: () => !this.autoScrollPaused,
        onTick: (el) => {
          this.scrollTop = el.scrollTop
        },
        onReachEnd: () => {
          this.$nextTick(() => {
            const el = this.$refs.listRef
            if (el) el.scrollTop = 0
            this.scrollTop = 0
            this.autoScrollPaused = Boolean(this._manualPause)
          })
        }
      })
      this.scrollLoop.start()
    },
    pauseAutoScroll()  { this.autoScrollPaused=true },
    resumeAutoScroll() { if(!this._manualPause) this.autoScrollPaused=false },
    toggleAutoScroll() {
      this._manualPause=!this._manualPause
      this.autoScrollPaused=this._manualPause
      if(!this._manualPause){ const el=this.$refs.listRef; if(el) this.scrollTop=el.scrollTop }
    },

    warnClass(level) {
      if(!level) return 'normal'
      // 高危=红色critical, 中度=橙色high, 轻度=黄色medium, 低危/正常=绿色low
      const lvl = String(level).trim()
      if(['高危','严重','危险','高'].includes(lvl)) return 'critical'
      if(['中度','中','警告'].includes(lvl)) return 'high'
      if(['轻度','低','提醒'].includes(lvl)) return 'medium'
      if(['低危','正常'].includes(lvl)) return 'low'
      return 'normal'
    },
    statBarWidth(val) { return val/this.statMax*100 },
    fmtTime(ts)     { return ts?dayjs(ts).format('MM-DD HH:mm'):'--' },
    fmtTimeFull(ts) { return ts?dayjs(ts).format('YYYY-MM-DD HH:mm:ss'):'--' },

    exportWarnings() {
      const cols = [
        { label: '序号', key: '_idx' },
        { label: '姓名', key: 'userName' },
        { label: '工号', key: 'empCode' },
        { label: '部门', key: 'deptName' },
        { label: '预警类型', key: 'warningType' },
        { label: '预警级别', key: 'warningLevel' },
        { label: '预警值', key: 'warningValue' },
        { label: '状态', key: '_status' },
        { label: '处理备注', key: 'handleNote' },
        { label: '时间', key: '_time' },
      ]
      const data = this.filteredList.map((row, i) => ({
        ...row,
        _idx: (this.currentPage - 1) * this.pageSize + i + 1,
        _status: row.handled ? '已处理' : '待处理',
        _time: row.createTime ? new Date(row.createTime).toLocaleString('zh-CN') : '--',
        empCode: row.empCode || row.userCode || '--'
      }))
      exportToExcel(data, cols, '风险预警记录')
    },

    warningLocatorKey(item) {
      return `${item?.id ?? ''}@@${item?.createTime ?? ''}`
    },

    toggleSelectAll(e) {
      if (e.target.checked) {
        this.selectedKeys = [...new Set([...this.selectedKeys, ...this.pendingInFiltered.map(item => this.warningLocatorKey(item))])]
      } else {
        const pendingKeys = new Set(this.pendingInFiltered.map(item => this.warningLocatorKey(item)))
        this.selectedKeys = this.selectedKeys.filter(key => !pendingKeys.has(key))
      }
    },
    toggleSelect(item) {
      const key = this.warningLocatorKey(item)
      if (this.selectedKeys.includes(key)) {
        this.selectedKeys = this.selectedKeys.filter(x => x !== key)
      } else {
        this.selectedKeys = [...this.selectedKeys, key]
      }
    },
    async batchHandle() {
      if (!this.selectedKeys.length || this.batchHandling) return
      this.batchHandling = true
      try {
        const selectedSet = new Set(this.selectedKeys)
        const selectedRows = this.warningList.filter(row => selectedSet.has(this.warningLocatorKey(row)))
        const locators = selectedRows.map(row => ({ warningId: row.id, occurredAt: row.createTime }))
        const res = await handleBatchRiskWarning(locators)
        if (res.code === 200) {
          this.$message?.success(`已处理 ${locators.length} 条预警`) || alert(`已处理 ${locators.length} 条预警`)
          this.selectedKeys = []
          await this.loadList()
        } else {
          this.$message?.error(res.message || '批量处理失败') || alert('批量处理失败')
        }
      } catch (e) {
        this.$message?.error('批量处理失败，请重试') || alert('批量处理失败')
      } finally {
        this.batchHandling = false
      }
    }
  }
}

