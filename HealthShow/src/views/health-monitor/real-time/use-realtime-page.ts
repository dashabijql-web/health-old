import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { getOnlineUsers } from '@/api/realtime'
import { getVoiceTemplates, sendVoiceMessage, sendWatchMessage } from '@/api/device'
import { useIntervalTask } from '@/composables/useIntervalTask'
import { useScrollLoop } from '@/composables/useScrollLoop'
import { useTimeoutTask } from '@/composables/useTimeoutTask'
import { normalizeRealtimeUsersResponse } from './realtime-helpers'

type RealtimeRow = Record<string, any>

interface SearchForm {
  name: string
  dept: string
  status: string
}

interface RealtimeSummary {
  onlineCount: number
  normalCount: number
  warningCount: number
  staleCount: number
  noDataCount: number
  onlineWindowMinutes: number
  freshnessMinutes: number
}

interface RealtimeUsersPage {
  list: RealtimeRow[]
  total: number
  page?: number
  size?: number
  stale?: boolean
  refreshedAt?: string
  summary: RealtimeSummary
  departments: string[]
  warningPreview: RealtimeRow[]
}

interface VoiceTemplate {
  id: string
  name: string
  [key: string]: unknown
}

const createEmptyPage = (): RealtimeUsersPage => ({
  list: [],
  total: 0,
  page: 1,
  size: 50,
  stale: false,
  refreshedAt: '',
  summary: {
    onlineCount: 0,
    normalCount: 0,
    warningCount: 0,
    staleCount: 0,
    noDataCount: 0,
    onlineWindowMinutes: 15,
    freshnessMinutes: 5
  },
  departments: [],
  warningPreview: []
})

export function useRealtimePage() {
  const router = useRouter()
  const realtimeRoot = ref<HTMLElement | null>(null)
  const onlineUsers = ref<RealtimeUsersPage>(createEmptyPage())
  const searchForm = ref<SearchForm>({ name: '', dept: '', status: '' })
  const currentPage = ref(1)
  const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440)
  const pageSize = ref(viewportWidth.value < 992 ? 20 : 50)
  const autoScrollEnabled = ref(false)
  const scrollPaused = ref(false)
  const detailUser = ref<RealtimeRow | null>(null)
  const detailVisible = ref(false)
  const messageTarget = ref<RealtimeRow | null>(null)
  const messageDialogVisible = ref(false)
  const messageText = ref('')
  const voiceTarget = ref<RealtimeRow | null>(null)
  const voiceDialogVisible = ref(false)
  const voiceTemplateId = ref('')
  const voiceTemplates = ref<VoiceTemplate[]>([])
  const isLoading = ref(false)
  const isRefreshing = ref(false)
  const refreshError = ref('')
  const isStale = ref(false)
  const lastSuccessfulRefresh = ref('')
  let fetching = false
  let pendingFetch = false
  let loaded = false

  const tblHeadStyle = {
    background: 'rgba(0,40,90,0.9)',
    color: '#00d4ff',
    borderColor: 'rgba(0,212,255,0.3)',
    fontSize: '13px',
    fontWeight: 'bold',
    padding: '10px 0'
  }
  const tblCellStyle = {
    background: 'transparent',
    borderColor: 'rgba(0,212,255,0.15)',
    color: '#a8c5e6',
    fontSize: '13px',
    padding: '8px 0',
    cursor: 'pointer'
  }

  const isMobile = computed(() => viewportWidth.value < 992)
  const allUsers = computed(() => onlineUsers.value.list || [])
  const summary = computed(() => onlineUsers.value.summary || createEmptyPage().summary)
  const warningUsers = computed(() => onlineUsers.value.warningPreview || [])
  const dataIssueCount = computed(() => {
    return Number(summary.value.staleCount || 0) + Number(summary.value.noDataCount || 0)
  })
  const totalPages = computed(() => {
    return Math.max(1, Math.ceil((onlineUsers.value.total || 0) / pageSize.value))
  })
  const refreshLabel = computed(() => {
    if (refreshError.value) return refreshError.value
    if (!lastSuccessfulRefresh.value) return '正在加载实时数据'
    const time = dayjs(lastSuccessfulRefresh.value)
    return time.isValid() ? `${time.format('HH:mm:ss')} 更新` : '已更新'
  })

  async function fetchOnlineUsers() {
    if (fetching) {
      pendingFetch = true
      return
    }
    fetching = true
    isRefreshing.value = true
    const isFirst = !loaded
    if (isFirst) isLoading.value = true
    try {
      const response = await getOnlineUsers({
        page: currentPage.value,
        size: pageSize.value,
        name: searchForm.value.name.trim(),
        dept: searchForm.value.dept,
        status: searchForm.value.status
      }, 20000)
      if (!response || response.code !== 200) throw new Error(response?.message || 'request failed')

      const data = normalizeRealtimeUsersResponse(response.data) as RealtimeUsersPage
      onlineUsers.value = data
      currentPage.value = data.page || currentPage.value
      isStale.value = Boolean(data.stale)
      lastSuccessfulRefresh.value = data.refreshedAt || new Date().toISOString()
      refreshError.value = data.stale ? '数据服务异常，当前显示缓存' : ''
    } catch {
      refreshError.value = onlineUsers.value.list.length
        ? '刷新失败，当前显示上次数据'
        : '实时数据加载失败'
    } finally {
      loaded = true
      fetching = false
      isRefreshing.value = false
      if (isFirst) isLoading.value = false
      if (pendingFetch) {
        pendingFetch = false
        void nextTick(() => fetchOnlineUsers())
      }
    }
  }

  const manualRefresh = () => void fetchOnlineUsers()
  const { start: startRefreshTask, stop: stopRefreshTask } = useIntervalTask(fetchOnlineUsers, 15000)
  const { start: scheduleSearch, stop: stopSearch } = useTimeoutTask(() => {
    currentPage.value = 1
    return fetchOnlineUsers()
  }, 250)

  function goToPage(page: number) {
    const next = Math.max(1, Math.min(totalPages.value, page))
    if (next === currentPage.value) return
    currentPage.value = next
    void fetchOnlineUsers()
  }

  const autoScrollLoop = useScrollLoop({
    getElement: () => realtimeRoot.value?.querySelector<HTMLElement>(
      '.el-table__body-wrapper .el-scrollbar__wrap, .el-scrollbar__wrap'
    ),
    intervalMs: 80,
    endPauseMs: 1500,
    shouldScroll: () => autoScrollEnabled.value && !scrollPaused.value && !isMobile.value,
    onReachEnd: () => {
      goToPage(currentPage.value < totalPages.value ? currentPage.value + 1 : 1)
      void nextTick(() => {
        const element = realtimeRoot.value?.querySelector<HTMLElement>(
          '.el-table__body-wrapper .el-scrollbar__wrap, .el-scrollbar__wrap'
        )
        if (element) element.scrollTop = 0
      })
      scrollPaused.value = false
    }
  })

  function startAutoScroll() {
    if (isMobile.value || !autoScrollEnabled.value) return
    autoScrollLoop.start()
  }

  function toggleAutoScroll() {
    autoScrollEnabled.value = !autoScrollEnabled.value
    if (autoScrollEnabled.value) startAutoScroll()
    else autoScrollLoop.stop()
  }

  const pauseAutoScroll = () => { scrollPaused.value = true }
  const resumeAutoScroll = () => { scrollPaused.value = false }
  const handleSearch = () => scheduleSearch()
  const handleReset = () => {
    stopSearch()
    searchForm.value = { name: '', dept: '', status: '' }
    currentPage.value = 1
    void nextTick(() => fetchOnlineUsers())
  }
  const showAllWarnings = () => {
    searchForm.value = { ...searchForm.value, status: 'warning' }
    currentPage.value = 1
    void nextTick(() => fetchOnlineUsers())
  }
  const openWarningCenter = () => void router.push('/alert-management/notifications')
  const showUserDetail = (row: RealtimeRow) => {
    detailUser.value = row
    detailVisible.value = true
  }
  const handleSendMessage = (row: RealtimeRow) => {
    messageTarget.value = row
    messageText.value = ''
    messageDialogVisible.value = true
  }

  async function handleSendVoice(row: RealtimeRow) {
    voiceTarget.value = row
    voiceTemplateId.value = ''
    if (!voiceTemplates.value.length) {
      try {
        const response = await getVoiceTemplates()
        if (response.code === 200) voiceTemplates.value = response.data || []
      } catch {}
    }
    voiceDialogVisible.value = true
  }

  async function confirmSendVoice() {
    if (!voiceTemplateId.value || !voiceTarget.value?.imei) return
    try {
      const response = await sendVoiceMessage(voiceTarget.value.imei, voiceTemplateId.value)
      if (response.code === 200) {
        ElMessage.success('语音提醒已推送，手表将在数秒内播放')
        voiceDialogVisible.value = false
      } else {
        ElMessage.error(response.message || '推送失败')
      }
    } catch {
      ElMessage.error('推送失败')
    }
  }

  async function confirmSendMessage() {
    const text = messageText.value.trim()
    if (!text || !messageTarget.value?.imei) return
    try {
      const response = await sendWatchMessage(messageTarget.value.imei, text)
      if (response.code === 200) {
        ElMessage.success('消息已推送到手表')
        messageDialogVisible.value = false
      } else {
        ElMessage.error(response.message || '推送失败')
      }
    } catch {
      ElMessage.error('推送失败')
    }
  }

  const onVisibilityChange = () => {
    if (document.hidden) {
      stopRefreshTask()
      autoScrollLoop.stop()
      return
    }
    void fetchOnlineUsers()
    startRefreshTask()
    startAutoScroll()
  }
  const onViewportResize = () => {
    const wasMobile = isMobile.value
    viewportWidth.value = window.innerWidth
    const nextSize = isMobile.value ? 20 : 50
    if (wasMobile !== isMobile.value || pageSize.value !== nextSize) {
      pageSize.value = nextSize
      currentPage.value = 1
      void fetchOnlineUsers()
    }
  }

  onMounted(() => {
    void fetchOnlineUsers()
    startRefreshTask()
    startAutoScroll()
    document.addEventListener('visibilitychange', onVisibilityChange)
    window.addEventListener('resize', onViewportResize)
  })

  onBeforeUnmount(() => {
    document.removeEventListener('visibilitychange', onVisibilityChange)
    window.removeEventListener('resize', onViewportResize)
  })

  return {
    allUsers,
    autoScrollEnabled,
    confirmSendMessage,
    confirmSendVoice,
    currentPage,
    dataIssueCount,
    detailUser,
    detailVisible,
    goToPage,
    handleReset,
    handleSearch,
    handleSendMessage,
    handleSendVoice,
    isLoading,
    isMobile,
    isRefreshing,
    isStale,
    manualRefresh,
    messageDialogVisible,
    messageTarget,
    messageText,
    onlineUsers,
    openWarningCenter,
    pageSize,
    pauseAutoScroll,
    realtimeRoot,
    refreshError,
    refreshLabel,
    resumeAutoScroll,
    searchForm,
    showAllWarnings,
    showUserDetail,
    summary,
    tblCellStyle,
    tblHeadStyle,
    toggleAutoScroll,
    totalPages,
    voiceDialogVisible,
    voiceTarget,
    voiceTemplateId,
    voiceTemplates,
    warningUsers
  }
}
