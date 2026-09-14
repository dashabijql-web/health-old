import { escapeHtml } from './ai-chat-text.js'

export function printChatTranscript(messages, parser) {
  const now = new Date().toLocaleString('zh-CN')
  const html = buildChatExportHtml(messages, parser, now)
  const win = window.open('', '_blank')

  if (!win) {
    alert('请允许弹出窗口以导出对话')
    return
  }

  win.document.write(html)
  win.document.close()
  setTimeout(() => win.print(), 600)
}

function buildChatExportHtml(messages, parser, now) {
  const rows = messages.map(msg => {
    const role = msg.role === 'user' ? '用户' : 'AI助手'
    const cls = msg.role === 'user' ? 'user' : 'ai'
    const content = msg.role === 'assistant'
      ? parser.parse(msg.content || '')
      : `<p>${escapeHtml(msg.content || '')}</p>`
    const sqlPart = msg.sql ? `<details><summary>生成的 SQL</summary><pre>${escapeHtml(msg.sql)}</pre></details>` : ''
    return `<div class="msg ${cls}"><div class="role">${role}</div><div class="content">${content}${sqlPart}</div></div>`
  }).join('')

  return `<!DOCTYPE html>
<html lang="zh-CN"><head>
<meta charset="UTF-8">
<title>AI健康助手对话记录 ${now}</title>
<style>
  body { font-family: 'Microsoft YaHei', sans-serif; max-width: 800px; margin: 0 auto; padding: 24px; color: #222; }
  h1 { font-size: 18px; color: #0066cc; border-bottom: 2px solid #0066cc; padding-bottom: 8px; }
  .meta { font-size: 12px; color: #666; margin-bottom: 20px; }
  .msg { display: flex; gap: 12px; margin-bottom: 16px; }
  .role { min-width: 54px; font-weight: 700; font-size: 13px; padding-top: 3px; }
  .msg.user .role { color: #0066cc; }
  .msg.ai .role { color: #cc6600; }
  .content { flex: 1; background: #f5f8ff; border-radius: 6px; padding: 10px 14px; font-size: 14px; line-height: 1.7; }
  .msg.user .content { background: #e8f0ff; }
  details { margin-top: 8px; }
  summary { cursor: pointer; font-size: 12px; color: #888; }
  pre { background: #f0f0f0; padding: 8px; border-radius: 4px; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
  p { margin: 4px 0; }
  strong { font-weight: 700; }
  @media print { body { padding: 0; } }
</style>
</head><body>
<h1>AI 健康助手 · 对话记录</h1>
<div class="meta">导出时间：${now}　共 ${messages.filter(msg => msg.role === 'user').length} 轮对话</div>
${rows}
</body></html>`
}
