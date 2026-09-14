<template>
  <div class="ai-chat-page">
    <header class="ac-hd">
      <div class="ac-hd-left">
        <span class="ac-live-dot" :class="{ 'is-active': loading, 'is-idle': !loading && messages.length === 0 }"></span>
        <h1 class="ac-hd-title">AI 健康助手</h1>
      </div>
      <div class="ac-hd-kpis">
        <div class="ac-kpi">
          <span class="ac-kpi-n">{{ messages.length }}</span>
          <span class="ac-kpi-l">消息数</span>
        </div>
        <div class="ac-kpi">
          <span class="ac-kpi-n">{{ userMessageCount }}</span>
          <span class="ac-kpi-l">提问</span>
        </div>
        <div class="ac-kpi">
          <span class="ac-kpi-n">{{ assistantMessageCount }}</span>
          <span class="ac-kpi-l">回答</span>
        </div>
        <div class="ac-kpi">
          <span class="ac-kpi-n">{{ queryResultCount }}</span>
          <span class="ac-kpi-l">数据查询</span>
        </div>
      </div>
      <div class="ac-hd-status">
        <span :class="['ac-status-chip', loading ? 'is-loading' : messages.length ? 'is-active' : '']">
          {{ sessionStateLabel }}
        </span>
      </div>
      <div class="ac-hd-actions">
        <button type="button" class="ac-action-btn" @click="exportChat" :disabled="messages.length === 0">
          导出对话
        </button>
        <button type="button" class="ac-action-btn ac-action-btn--primary" @click="newChat" :disabled="loading">
          新对话
        </button>
      </div>
    </header>

    <div class="chat-stage">
      <div class="message-list" ref="messageListRef">
        <div v-if="messages.length === 0" class="ac-empty-state">
          <div class="ac-empty-icon">AI</div>
          <div class="ac-empty-title">准备开始新的健康分析会话</div>
          <div class="ac-empty-desc">可以直接提问，或先从下方快捷问题开始，快速查看部门趋势、个体画像和预警统计。</div>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role]"
        >
          <div class="avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
          <div class="msg-body">
            <div class="bubble" v-html="formatMessage(msg.content)"></div>
            <div v-if="msg.sql" class="sql-debug">
              <div class="sql-toggle" @click="msg.sqlOpen = !msg.sqlOpen">
                <span>{{ msg.sqlOpen ? '收起生成的 SQL' : '查看生成的 SQL' }}</span>
              </div>
              <pre v-if="msg.sqlOpen" class="sql-block">{{ msg.sql }}</pre>
            </div>
            <template v-if="msg.queryData && msg.queryData.length >= 2">
              <div v-if="getVizType(msg.queryData) === 'table'" class="viz-table-wrap">
                <table class="viz-table">
                  <thead>
                    <tr><th v-for="col in getQueryColumns(msg)" :key="col">{{ col }}</th></tr>
                  </thead>
                  <tbody>
                    <tr v-for="(row, ri) in msg.queryData" :key="ri">
                      <td v-for="col in getQueryColumns(msg)" :key="col">{{ formatCell(row[col]) }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div v-else-if="getVizType(msg.queryData) !== 'none'"
                   :id="'viz-chart-' + index"
                   :style="{ height: getVizType(msg.queryData) === 'bar' ? Math.min(msg.queryData.length * 28 + 20, 420) + 'px' : '220px' }"
                   class="viz-chart">
              </div>
            </template>
          </div>
        </div>
        <div class="message assistant" v-if="loading">
          <div class="avatar">AI</div>
          <div class="bubble loading">
            <span class="dot"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
        </div>
      </div>
    </div>

    <div class="chat-console">
      <div class="input-area">
        <el-input
          v-model="inputText"
          placeholder="输入你的问题，按 Enter 发送..."
          :disabled="loading"
          @keyup.enter="sendMessage"
          class="chat-input"
        />
        <el-button
          type="primary"
          :loading="loading"
          @click="sendMessage"
          class="send-btn"
        >
          发送
        </el-button>
      </div>
      <div class="quick-questions">
        <span class="label">快捷提问：</span>
        <el-tag
          v-for="q in quickQuestions"
          :key="q"
          @click="askQuick(q)"
          class="quick-tag"
          :class="{ disabled: loading }"
        >{{ q }}</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { clearAiSession } from '@/api/ai'
import request from '@/utils/request'
import { getToken } from '@/utils/auth'
import { getMarked } from '@/utils/lazy-vendors'
import { createAiChatChartRegistry } from './ai-chat-chart.js'
import { printChatTranscript } from './ai-chat-export.js'
import { getAiChatSessionStateLabel } from './ai-chat-view-model.js'
import {
  decodeBase64Utf8,
  formatCell,
  getQueryColumns,
  getVizType,
  normalizeQueryPayload
} from './ai-chat-query-result.js'
import { DEFAULT_QUICK_QUESTIONS, buildDynamicQuickQuestions, generateSessionId } from './ai-chat-session.js'
import { formatChatMessage } from './ai-chat-text.js'

const inputText = ref('')
const messages = ref([])
const loading = ref(false)
const messageListRef = ref(null)
const markdownParser = ref(null)

const sessionId = ref(generateSessionId())
const chartRegistry = createAiChatChartRegistry()
const quickQuestions = ref([...DEFAULT_QUICK_QUESTIONS])
const userMessageCount = computed(() => messages.value.filter((msg) => msg.role === 'user').length)
const assistantMessageCount = computed(() => messages.value.filter((msg) => msg.role === 'assistant').length)
const queryResultCount = computed(() => messages.value.filter((msg) => Array.isArray(msg.queryData) && msg.queryData.length > 0).length)
const sessionStateLabel = computed(() => getAiChatSessionStateLabel({ loading: loading.value, messageCount: messages.value.length }))

async function loadDynamicQuickQuestions() {
  try {
    const res = await request({ url: '/department/list', method: 'get' })
    const questions = buildDynamicQuickQuestions(res.data || [])
    if (questions) quickQuestions.value = questions
  } catch (e) {}
}

onMounted(() => {
  loadDynamicQuickQuestions()
  ensureMarkdownParser()
})

async function ensureMarkdownParser() {
  if (!markdownParser.value) {
    markdownParser.value = await getMarked()
  }
  return markdownParser.value
}

async function sendMessage() {
  const question = inputText.value.trim()
  if (!question || loading.value) return

  messages.value.push({ role: 'user', content: question })
  inputText.value = ''
  loading.value = true

  const aiMsgIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', sql: null, sqlOpen: false, queryData: null, queryColumns: null, queryResult: null })
  scrollToBottom()

  try {
    const response = await fetch('/dev-api/ai/chat/stream', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        satoken: getToken() || ''
      },
      body: JSON.stringify({ question, sessionId: sessionId.value })
    })

    if (!response.ok) {
      messages.value[aiMsgIndex].content = '请求失败，请稍后重试。'
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const data = line.slice(5).trim()

        if (data === '[DONE]') {
          loading.value = false
          scrollToBottom()
          break
        }

        if (data.startsWith('[SESSION]:')) {
          sessionId.value = data.slice(10)
          continue
        }

        if (data.startsWith('[DATA]:')) {
          try {
            const queryResult = normalizeQueryPayload(JSON.parse(decodeBase64Utf8(data.slice(7))))
            messages.value[aiMsgIndex].queryResult = queryResult
            messages.value[aiMsgIndex].queryData = queryResult.rows
            messages.value[aiMsgIndex].queryColumns = queryResult.columns
            const vizType = getVizType(queryResult.rows)
            if (vizType === 'bar' || vizType === 'line') {
              nextTick(() => chartRegistry.initChart(aiMsgIndex, queryResult.rows, vizType))
            }
          } catch (e) {}
          continue
        }

        if (data.startsWith('[SQL]:')) {
          messages.value[aiMsgIndex].sql = decodeBase64Utf8(data.slice(6))
          continue
        }

        if (data.startsWith('[ERROR]')) {
          messages.value[aiMsgIndex].content = data.slice(7)
          continue
        }

        messages.value[aiMsgIndex].content += data
        scrollToBottom()
      }
    }
  } catch (e) {
    messages.value[aiMsgIndex].content = '网络错误，请稍后重试。'
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

async function exportChat() {
  if (messages.value.length === 0) return
  const parser = await ensureMarkdownParser()
  printChatTranscript(messages.value, parser)
}

onUnmounted(() => {
  chartRegistry.disposeAll()
})

async function newChat() {
  if (loading.value) return
  try {
    await clearAiSession(sessionId.value)
  } catch (e) {}
  chartRegistry.disposeAll()
  sessionId.value = generateSessionId()
  messages.value = []
}

function askQuick(question) {
  if (loading.value) return
  inputText.value = question
  sendMessage()
}

function formatMessage(text) {
  return formatChatMessage(text, markdownParser.value)
}

async function scrollToBottom() {
  await nextTick()
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}
</script>

<style scoped lang="scss">
@import './ai-chat.scss';
</style>
