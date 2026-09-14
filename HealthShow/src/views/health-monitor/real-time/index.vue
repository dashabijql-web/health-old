<template>
  <div ref="realtimeRoot" class="hm-page-shell rt-root">
    <RealtimeHeader
      :summary="summary"
      :data-issue-count="dataIssueCount"
      :refresh-label="refreshLabel"
      :refresh-error="refreshError"
      :stale="isStale"
      :loading="isRefreshing"
      @refresh="manualRefresh"
    />

    <section class="rt-bd">
      <main class="rt-main">
        <RealtimeUserTable
          v-model:search-form="searchForm"
          :total-count="onlineUsers.total"
          :dept-list="onlineUsers.departments"
          :auto-scroll-enabled="autoScrollEnabled"
          :is-mobile="isMobile"
          :paginated-user-list="allUsers"
          :is-loading="isLoading"
          :current-page="currentPage"
          :page-size="pageSize"
          :tbl-head-style="tblHeadStyle"
          :tbl-cell-style="tblCellStyle"
          @search="handleSearch"
          @reset="handleReset"
          @toggle-auto-scroll="toggleAutoScroll"
          @pause-auto-scroll="pauseAutoScroll"
          @resume-auto-scroll="resumeAutoScroll"
          @show-user-detail="showUserDetail"
          @send-message="handleSendMessage"
          @send-voice="handleSendVoice"
        />

        <div v-if="totalPages > 1" class="rt-pagination">
          <button class="rt-pg-btn" :disabled="currentPage <= 1" aria-label="上一页" @click="goToPage(currentPage - 1)">&lsaquo;</button>
          <span class="rt-pg-info">{{ currentPage }} / {{ totalPages }}</span>
          <button class="rt-pg-btn" :disabled="currentPage >= totalPages" aria-label="下一页" @click="goToPage(currentPage + 1)">&rsaquo;</button>
          <span class="rt-pg-total">共 {{ onlineUsers.total }} 条</span>
        </div>
      </main>

      <RealtimeWarningSidebar
        :warning-users="warningUsers"
        :warning-count="summary.warningCount || 0"
        @send-message="handleSendMessage"
        @send-voice="handleSendVoice"
        @show-detail="showUserDetail"
        @show-all="showAllWarnings"
        @open-warning-center="openWarningCenter"
      />
    </section>

    <el-dialog
      v-model="messageDialogVisible"
      title="发送消息到手表"
      class="rt-dialog"
      width="420px"
      :append-to-body="true"
      :close-on-click-modal="false"
    >
      <div v-if="messageTarget" class="rt-msg-meta">
        <span>{{ messageTarget.userName }}</span>
        <span class="rt-msg-dept">{{ messageTarget.deptName }}</span>
        <span class="rt-msg-imei">{{ messageTarget.imei }}</span>
      </div>
      <el-input
        v-model="messageText"
        type="textarea"
        :rows="4"
        placeholder="请输入要推送到手表的消息内容（最多 50 个字符）"
        :maxlength="50"
        show-word-limit
        resize="none"
      />
      <template #footer>
        <el-button @click="messageDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!messageText.trim()" @click="confirmSendMessage">发送</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="voiceDialogVisible"
      title="发送语音提醒"
      class="rt-dialog"
      width="400px"
      :append-to-body="true"
      :close-on-click-modal="false"
    >
      <div v-if="voiceTarget" class="rt-msg-meta">
        <span>{{ voiceTarget.userName }}</span>
        <span class="rt-msg-dept">{{ voiceTarget.deptName }}</span>
        <span class="rt-msg-imei">{{ voiceTarget.imei }}</span>
      </div>
      <div class="rt-voice-template-list">
        <div
          v-for="template in voiceTemplates"
          :key="template.id"
          :class="['rt-voice-tpl', voiceTemplateId === template.id ? 'rt-voice-tpl--active' : '']"
          @click="voiceTemplateId = template.id"
        >
          {{ template.name }}
        </div>
      </div>
      <template #footer>
        <el-button @click="voiceDialogVisible = false">取消</el-button>
        <el-button type="warning" :disabled="!voiceTemplateId" @click="confirmSendVoice">
          发送提醒
        </el-button>
      </template>
    </el-dialog>

    <RealtimeDetailDialog
      v-model:visible="detailVisible"
      :user="detailUser"
      @send-message="handleSendMessage"
      @send-voice="handleSendVoice"
    />
  </div>
</template>

<script setup lang="ts">
import RealtimeDetailDialog from './components/RealtimeDetailDialog.vue'
import RealtimeHeader from './components/RealtimeHeader.vue'
import RealtimeUserTable from './components/RealtimeUserTable.vue'
import RealtimeWarningSidebar from './components/RealtimeWarningSidebar.vue'
import { useRealtimePage } from './use-realtime-page'

defineOptions({ name: 'RealtimeMonitor' })

const {
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
} = useRealtimePage()
</script>

<style lang="scss" src="./realtime.scss"></style>
