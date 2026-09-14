export const riskWarningPageViewModel = {
  computed: {
    activePeriodLabel() {
      return { day: '今日', week: '近7日', month: '近30日' }[this.activePeriod] || '当前时段'
    },
    heroDescription() {
      const total = this.warningStats.reduce((sum, item) => sum + item.value, 0)
      return `${this.activePeriodLabel}累计 ${total} 条风险波动，优先核查待处理记录并联动预警中心完成处置。`
    },
    // FIX ②: 5个KPI含压力
    headerKpis() {
      const total = this.warningStats.reduce((s, x) => s + x.value, 0)
      return [
        { key: 'total', label: '总预警', val: total, tone: 'danger', note: `${this.activePeriodLabel}总量` },
        { key: 'heart-rate', label: '心率', val: this.warningStats[0].value, tone: 'danger', note: '优先级最高' },
        { key: 'blood-oxygen', label: '血氧', val: this.warningStats[1].value, tone: 'warning', note: '异常波动' },
        { key: 'temperature', label: '体温', val: this.warningStats[2].value, tone: 'success', note: '趋势跟踪' },
        { key: 'pressure', label: '压力', val: this.warningStats[3].value, tone: 'primary', note: '班前复核' }
      ]
    },
    warningMetricStripItems() {
      return this.headerKpis.map((item) => ({
        key: item.key,
        label: item.label,
        value: String(item.val ?? '--'),
        note: item.note || '',
        tone: item.tone || 'primary'
      }))
    },
    statTitle()  { return { day:'今日预警统计', week:'近7日预警统计', month:'近30日预警统计' }[this.activePeriod] },
    trendTitle() { return { day:'今日预警分布', week:'近7天预警趋势', month:'近30天预警趋势' }[this.activePeriod] },
    statMax() { return Math.max(1, ...this.warningStats.map(x => x.value)) },
    // FIX ⑤: 筛选
    filteredList() {
      return this.warningList
    },
    pagedList()   { return this.warningList },
    totalPages()  { return Math.max(1, Math.ceil(this.totalWarnings / this.pageSize)) },
    warningTypes(){ return this.warningTypeOptions },
    pendingInFiltered() { return this.filteredList.filter(x => !x.handled) },
    allPendingSelected() { return this.pendingInFiltered.length > 0 && this.pendingInFiltered.every(x => this.selectedKeys.includes(this.warningLocatorKey(x))) },
    somePendingSelected() { return this.pendingInFiltered.some(x => this.selectedKeys.includes(this.warningLocatorKey(x))) },

    // 右侧面板：体征均值卡
    vitalAvg() {
      const total = this.warningList.length || 1
      const parse = v => parseFloat(v) || null
      const hrVals   = this.warningList.filter(x=>(x.warningType||'').includes('心率')).map(x=>parse(x.warningValue)).filter(v=>v!=null)
      const spo2Vals = this.warningList.filter(x=>(x.warningType||'').includes('血氧')).map(x=>parse(x.warningValue)).filter(v=>v!=null)
      const tempVals = this.warningList.filter(x=>(x.warningType||'').includes('体温')).map(x=>parse(x.warningValue)).filter(v=>v!=null)
      const avg = arr => arr.length ? (arr.reduce((s,v)=>s+v,0)/arr.length).toFixed(0) : '--'
      return [
        { label:'平均心率', val:hrVals.length   ? avg(hrVals)+'bpm'  : '--', color:'#ef4444', abnormal:hrVals.length,   rate:Math.round(hrVals.length/total*100)   },
        { label:'平均血氧', val:spo2Vals.length  ? avg(spo2Vals)+'%'  : '--', color:'#f97316', abnormal:spo2Vals.length, rate:Math.round(spo2Vals.length/total*100) },
        { label:'平均体温', val:tempVals.length  ? avg(tempVals)+'°C' : '--', color:'#22c55e', abnormal:tempVals.length, rate:Math.round(tempVals.length/total*100) }
      ]
    },

    // 右侧面板：处理进度
    handleProgress() {
      return ['心率','血氧','体温','压力'].map(key => {
        const matched = this.warningList.filter(x=>(x.warningType||'').includes(key))
        const handled = matched.filter(x=>x.handled||x.isHandled===1||x.isHandled===true)
        return { label:key+'预警', total:matched.length, handled:handled.length, rate:matched.length?Math.round(handled.length/matched.length*100):0 }
      })
    },
    handleProgressTotal() {
      const all = this.warningList.length; if(!all) return 0
      return Math.round(this.warningList.filter(x=>x.handled||x.isHandled===1||x.isHandled===true).length/all*100)
    },

  },
  watch: {},
}

