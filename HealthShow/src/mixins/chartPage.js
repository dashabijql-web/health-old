/**
 * 健康监测页面通用 mixin
 * 提供：initClock、handleResize、startAutoScroll、fmtTime、periodRange、switchPeriod
 * 自动注册/注销 resize 事件，管理 clock/scroll/resize/refresh 任务
 * 自动 dispose 所有 ECharts 实例
 *
 * 使用方式：
 *   import chartPageMixin from '@/mixins/chartPage'
 *   export default { mixins: [chartPageMixin], ... }
 *
 * 要求宿主组件：
 *   - data 中有 charts 对象（key→echarts实例）
 *   - data 中有 currentTime（字符串，用于时钟显示）
 *   - data 中有 activePeriod（字符串，用于 periodRange 计算）
 *   - methods 中有 fetchData()（switchPeriod 调用）
 *   - template 中 ref="listRef" 绑定到需要自动滚动的容器
 */
import dayjs from 'dayjs'
import { createEventBinding, createIntervalTask, createTimeoutTask } from '@/utils/task-timer'
import { createScrollLoop } from '@/composables/useScrollLoop'

export default {
  data() {
    return {
      clockTask: null,
      scrollLoop: null,
      resizeTask: null,
      refreshTask: null,
      filterDept: '',
      pageLoading: false
    }
  },
  computed: {
    metricPeriodLabel() {
      return { day: '当日', week: '近7日', month: '近30日' }[this.activePeriod] || '当前周期'
    },
    latestRealtimeText() {
      const latest = (this.realtimeList || [])
        .map(item => item.recordTime)
        .filter(Boolean)
        .sort()
        .at(-1)
      return latest ? dayjs(latest).format('HH:mm') : '--'
    },
    periodRange() {
      const today = dayjs().format('YYYY-MM-DD')
      if (this.activePeriod === 'day') return { startDate: today, endDate: today }
      if (this.activePeriod === 'week') return { startDate: dayjs().subtract(6, 'day').format('YYYY-MM-DD'), endDate: today }
      return { startDate: dayjs().subtract(29, 'day').format('YYYY-MM-DD'), endDate: today }
    },
    /** 经过部门筛选的实时列表（filterDept 为空时返回全量） */
    filteredRealtimeList() {
      const list = this.realtimeList || []
      return this.filterDept
        ? list.filter(x => (x.deptName || x.dept_name) === this.filterDept)
        : list
    },
    pagedList() {
      const s = (this.currentPage - 1) * this.pageSize
      return this.filteredRealtimeList.slice(s, s + this.pageSize)
    },
    totalPages() {
      return Math.max(1, Math.ceil(this.filteredRealtimeList.length / this.pageSize))
    }
  },
  mounted() {
    this._resizeBinding = createEventBinding(() => window, 'resize', this.handleResize)
    this._visibilityBinding = createEventBinding(() => document, 'visibilitychange', this._handlePageVisibility)
    this._resizeBinding.start()
    this._visibilityBinding.start()
  },
  beforeUnmount() {
      this.clockTask?.stop?.()
      this.scrollLoop?.stop?.()
      this.refreshTask?.stop?.()
      this.resizeTask?.stop?.()
      this._pageSizeObserver?.disconnect?.()
      this._resizeBinding?.stop?.()
      this._visibilityBinding?.stop?.()
      this._listScrollBinding?.stop?.()
    if (this.charts) Object.values(this.charts).forEach(c => c && c.dispose())
  },
  methods: {
    initClock() {
      const tick = () => { this.currentTime = dayjs().format('YYYY年MM月DD日 HH:mm:ss') }
      tick()
      this.clockTask = this.clockTask || createIntervalTask(tick, 1000)
      this.clockTask.start()
    },
    handleResize() {
      this.resizeTask = this.resizeTask || createTimeoutTask(() => {
        this.$nextTick(() => Object.values(this.charts).forEach(c => c?.resize?.()))
      }, 200)
      this.resizeTask.start()
    },
    startAutoScroll() {
      const el = this.$refs.listRef
      if (!el) return
      let lastAutoScrollTop = 0
      let pausedUntil = 0

      this.scrollLoop?.stop?.()
      this._listScrollBinding?.stop?.()

      this.scrollLoop = createScrollLoop({
        getElement: () => this.$refs.listRef,
        intervalMs: 80,
        step: 1,
        endPauseMs: 1500,
        shouldScroll: () => Date.now() >= pausedUntil,
        onTick: current => {
          lastAutoScrollTop = current.scrollTop
        },
        onReachEnd: () => {
          const current = this.$refs.listRef
          if (!current) return
          lastAutoScrollTop = 0
          current.scrollTop = 0
        }
      })

      this._listScrollBinding = createEventBinding(
        () => this.$refs.listRef,
        'scroll',
        () => {
          const current = this.$refs.listRef
          if (!current) return
          if (Math.abs(current.scrollTop - lastAutoScrollTop) > 2) {
            lastAutoScrollTop = current.scrollTop
            pausedUntil = Date.now() + 2000
          }
        },
        { passive: true }
      )

      this._listScrollBinding.start()
      this.scrollLoop.start()
    },
    /** 通用页面初始化：时钟 + 首次加载 + 自动滚动 + 定时刷新 */
    initPage(refreshFn, interval = 30000) {
      this.__refreshFn = refreshFn || (() => this.fetchData())
      this.__refreshInterval = interval
      this.initClock()
      this._doFetchWithLoading()
      this.$nextTick(() => this.startAutoScroll())
      this.refreshTask = this.refreshTask || createIntervalTask(this.__refreshFn, interval)
      this.refreshTask.start(interval)
    },
    /** 首次加载时显示 loading 遮罩，刷新时静默更新 */
    async _doFetchWithLoading() {
      this.pageLoading = true
      try { await this.fetchData() } finally { this.pageLoading = false }
    },
    /** 标签页隐藏时暂停轮询，显示时立即刷新并重启定时器 */
    _handlePageVisibility() {
      if (document.hidden) {
        this.refreshTask?.stop?.()
      } else if (this.__refreshFn) {
        this.__refreshFn()
        this.refreshTask = this.refreshTask || createIntervalTask(this.__refreshFn, this.__refreshInterval)
        this.refreshTask.start(this.__refreshInterval)
      }
    },
    switchPeriod(val) {
      if (this.activePeriod === val) return
      this.activePeriod = val
      this.fetchData()
    },
    fmtTime(ts) {
      return ts ? dayjs(ts).format('MM-DD HH:mm') : ''
    },
    goToPortrait(item) {
      const code = item.userCode || item.empCode
      this.$router.push({
        path: '/health-monitor/health-portrait',
        query: code ? { empCode: code } : { name: item.userName }
      })
    },
    /** 根据容器高度自动计算分页大小 */
    setPageSize(rowH = 27) {
      const el = this.$refs.listRef; if (!el) return
      const n = Math.max(10, Math.floor(el.clientHeight / rowH))
      if (n !== this.pageSize) {
        this.pageSize = n
        this.currentPage = 1
      }
    },
    initAutoPageSize(rowH = 27) {
      this.$nextTick(() => {
        this._pageSizeObserver?.disconnect?.()
        const el = this.$refs.listRef
        if (!el) return
        this._pageSizeObserver = new ResizeObserver(() => this.setPageSize(rowH))
        this._pageSizeObserver.observe(el)
        this.setPageSize(rowH)
      })
    },
    showDetail(item) {
      this.detailItem = item
      this.detailVisible = true
    }
  }
}
