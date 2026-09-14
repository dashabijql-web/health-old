import { PERIOD_OPTIONS } from '@/constants/periods'

const DEFAULT_WARNING_TYPES = ['心率异常', '血氧偏低', '血氧过低', '体温异常', '血压偏高', '压力偏高', '跌倒报警', 'SOS报警']

export default {
  data() {
    return {
      currentTime: '',
      warningStats: [
        { label: '心率预警', value: 0, color: '#ef4444' },
        { label: '血氧预警', value: 0, color: '#f97316' },
        { label: '体温预警', value: 0, color: '#22c55e' },
        { label: '压力预警', value: 0, color: '#00d4ff' }
      ],
      warningList: [],
      totalWarnings: 0,
      warningTypeOptions: DEFAULT_WARNING_TYPES,
      currentPage: 1,
      pageSize: 50,
      filterName: '',
      filterLevel: '',
      filterType: '',
      trendData: { dates: [], series: { heartRate: [], bloodOxygen: [], temperature: [], pressure: [] } },
      deptData: [],
      activePeriod: 'month',
      periodOptions: PERIOD_OPTIONS,
      charts: {},
      detailVisible: false, detailRow: null,
      handleNote: '', handling: false,
      selectedKeys: [], batchHandling: false,
      autoScrollPaused: false,
      scrollTop: 0,
      // 体征曲线
      vitalLoading: false,
      vitalEmpty: false,
      vitalChartRange: '',
      vitalSummary: [],
      vitalChartInst: null
    }
  },
}

