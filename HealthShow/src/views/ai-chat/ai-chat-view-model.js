export function getAiChatSessionStateLabel({ loading, messageCount }) {
  if (loading) return 'AI 正在推理'
  if (messageCount > 0) return '会话已连接'
  return '等待新指令'
}

export function buildAiChatSummaryMetricItems({
  loading,
  messageCount,
  userMessageCount,
  assistantMessageCount,
  queryResultCount,
  quickQuestionCount
}) {
  return [
    {
      key: 'session',
      label: '会话状态',
      value: loading ? '推理中' : messageCount > 0 ? '在线' : '待命',
      note: loading ? '流式应答正在返回' : messageCount > 0 ? `已完成 ${assistantMessageCount} 次回复` : '等待第一条问题',
      tone: loading ? 'warning' : messageCount > 0 ? 'success' : 'primary'
    },
    {
      key: 'messages',
      label: '消息总数',
      value: messageCount,
      note: `${userMessageCount} 次提问 / ${assistantMessageCount} 次回复`,
      tone: 'primary'
    },
    {
      key: 'results',
      label: '数据结果',
      value: queryResultCount,
      note: queryResultCount > 0 ? '已生成表格或图表' : '当前仅文本对话',
      tone: queryResultCount > 0 ? 'warning' : 'primary'
    },
    {
      key: 'shortcuts',
      label: '快捷提问',
      value: quickQuestionCount,
      note: '可直接发起预设查询',
      tone: 'success'
    }
  ]
}
