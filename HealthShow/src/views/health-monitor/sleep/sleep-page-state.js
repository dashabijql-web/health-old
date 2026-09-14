export default {
  data() {
    return {
      currentTime: '',
      yesterdayDate: '',
      overview: {
        uploadRate: 0,
        greenLineRate: 0,
        avgSleepTime: '--',
        avgScore: 0,
        totalCount: 0
      },
      stageLegend: [],
      durationLegend: [],
      durationTips: [
        { label: '<4小时', color: '#ff5252', desc: '严重不足，影响认知' },
        { label: '4-6小时', color: '#FFB84D', desc: '偏少，易疲劳' },
        { label: '6-8小时', color: '#4FC3F7', desc: '建议范围' },
        { label: '>8小时', color: '#52c41a', desc: '充足，状态最佳' }
      ],
      bedtimeTips: [
        { time: '21-22时', icon: '★', color: '#52c41a', desc: '最佳入睡时间' },
        { time: '22-23时', icon: 'OK', color: '#4FC3F7', desc: '良好，顺应生物钟' },
        { time: '23-24时', icon: '!', color: '#FFB84D', desc: '偏晚，影响深睡' },
        { time: '0时以后', icon: '✕', color: '#ff5252', desc: '过晚，损害健康' }
      ],
      lateBedPct: 5,
      deptUploadList: [],
      alertList: [],
      detailList: [],
      currentPage: 1,
      pageSize: 20,
      charts: {},
      recordDialog: { visible: false, item: null }
    }
  }
}
