<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    :title="event ? event.type + ' - 事件处理' : '事件处理'"
    width="560px"
    :close-on-click-modal="false"
    class="handle-dlg"
  >
    <div v-if="event" class="handle-body">
      <!-- 事件信息 -->
      <div class="evt-info">
        <div class="ei-row">
          <span class="ei-icon">{{ event.icon }}</span>
          <div class="ei-main">
            <div class="ei-r1">
              <span class="ei-type">{{ event.type }}</span>
              <span :class="['ei-level', 'lv-' + event.level]">{{ getLevelText(event.level) }}</span>
            </div>
            <div class="ei-r2">
              <span>{{ event.user }}</span>
              <span class="dim">{{ event.dept }}</span>
              <span class="dim">{{ event.location }}</span>
              <span class="dim">{{ event.time }}</span>
            </div>
          </div>
          <div v-if="event.durationMinutes" :class="['ei-dur', { crit: event.durationMinutes > 5 }]">
            {{ event.durationMinutes }}分钟
          </div>
        </div>
      </div>

      <!-- 处理表单 -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" class="handle-form">
        <el-form-item label="处理状态">
          <el-tag type="success" effect="plain">完成闭环</el-tag>
        </el-form-item>

        <el-form-item label="处理说明" prop="handleNote">
          <el-input
            v-model="form.handleNote"
            type="textarea"
            :rows="3"
            placeholder="请填写处理说明..."
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="通知能力">
          <span class="switch-hint">当前环境未配置通知接口，本次处理不会发送通知。</span>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">确认处理</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, type PropType } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { resolveCommandCenterIncident } from '@/api/command-center'

interface HandleEvent {
  id: number
  occurredAt?: string
  type?: string
  icon?: string
  level?: string
  user?: string
  dept?: string
  location?: string
  time?: string
  durationMinutes?: number
  [key: string]: unknown
}

const props = defineProps({
  visible: { type: Boolean, default: false },
  event: { type: Object as PropType<HandleEvent | null>, default: null }
})

const emit = defineEmits<{
  'update:visible': [value: boolean]
  handled: []
}>()

const formRef = ref<FormInstance | null>(null)
const submitting = ref(false)

const form = reactive({
  handleNote: ''
})

const rules: FormRules = {
  handleNote: [{ required: true, message: '请填写处理说明', trigger: 'blur' }]
}

const getLevelText = (level?: string) => ({ critical: '特急', high: '紧急', medium: '一般', low: '轻微' }[level || ''] || level)

const onSubmit = async () => {
  if (!formRef.value || !props.event) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    await resolveCommandCenterIncident(props.event.id, {
      occurredAt: props.event.occurredAt,
      remark: form.handleNote
    })
    ElMessage.success('处理成功')
    emit('handled')
    emit('update:visible', false)
    // Reset form
    form.handleNote = ''
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : '未知错误'
    ElMessage.error('处理失败: ' + message)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
$blue: #1890ff;
$red: #ff5252;
$orange: #ff9800;
$yellow: #faad14;

.handle-body {
  .evt-info {
    background: rgba($blue, .06);
    border: 1px solid rgba($blue, .2);
    border-radius: 6px;
    padding: 10px 12px;
    margin-bottom: 16px;
  }
  .ei-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .ei-icon { font-size: 24px; }
  .ei-main { flex: 1; }
  .ei-r1 {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }
  .ei-type { font-size: 14px; font-weight: bold; }
  .ei-level {
    font-size: 10px;
    padding: 1px 6px;
    border-radius: 3px;
    &.lv-critical { background: rgba($red, .2); color: $red; font-weight: bold; }
    &.lv-high { background: rgba($orange, .2); color: $orange; }
    &.lv-medium { background: rgba($yellow, .2); color: $yellow; }
    &.lv-low { background: rgba($blue, .15); color: $blue; }
  }
  .ei-r2 {
    font-size: 12px;
    color: #999;
    display: flex;
    gap: 8px;
    .dim { color: #bbb; }
  }
  .ei-dur {
    font-size: 14px;
    font-weight: bold;
    color: $orange;
    &.crit { color: $red; }
  }
}

.handle-form {
  margin-top: 8px;
}

.switch-hint {
  margin-left: 8px;
  font-size: 12px;
  color: #999;
}
</style>
