export const sleepPageComputed = {
  computed: {
    headerKpis() {
      const o = this.overview
      return [
        { label: '数据上传率', val: (o.uploadRate || 0) + '%', cls: 'kpi-cyan' },
        { label: '绿线达标率', val: (o.greenLineRate || 0) + '%', cls: 'kpi-green' },
        { label: '平均睡眠时长', val: o.avgSleepTime || '--', cls: 'kpi-purple' },
        { label: '平均质量评分', val: (o.avgScore || 0) + '分', cls: 'kpi-amber' }
      ]
    },
    statCards() {
      const o = this.overview
      return [
        { label: '数据上传率', val: (o.uploadRate || 0) + '%', icon: '↑', color: '#00d4ff', bg: 'rgba(0,212,255,0.12)' },
        { label: '绿线达标率', val: (o.greenLineRate || 0) + '%', icon: 'OK', color: '#52c41a', bg: 'rgba(82,196,26,0.12)' },
        { label: '平均睡眠时长', val: o.avgSleepTime || '--', icon: '⏱', color: '#a78bfa', bg: 'rgba(167,139,250,0.12)' },
        { label: '平均质量评分', val: (o.avgScore || 0) + '分', icon: '★', color: '#FFB84D', bg: 'rgba(255,184,77,0.12)' }
      ]
    },
    pagedList() {
      const s = (this.currentPage - 1) * this.pageSize
      return this.detailList.slice(s, s + this.pageSize)
    },
    totalPages() {
      return Math.max(1, Math.ceil(this.detailList.length / this.pageSize))
    }
  }
}
