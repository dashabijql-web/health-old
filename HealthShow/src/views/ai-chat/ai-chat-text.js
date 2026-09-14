export function escapeHtml(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

export function formatChatMessage(text, parser) {
  if (!text) return ''
  const processed = text.replace(/([^\n])(\s{0,2})(\d{1,2}\.\s*\*\*)/g, (_, before, _sp, item) => {
    return before + '\n' + item
  })
  if (parser) return parser.parse(processed)
  return escapeHtml(processed).replace(/\n/g, '<br>')
}
