<template>
  <div class="dm-dispatch-shell">
    <div :class="['dm-dispatch-mission', `tone-${dispatchPriority.tone}`]">
      <div class="dm-dispatch-mission-copy">
        <div class="dm-dispatch-mission-label">当前值班优先级</div>
        <div class="dm-dispatch-mission-title">{{ dispatchPriority.title }}</div>
        <div class="dm-dispatch-mission-sub">{{ dispatchPriority.sub }}</div>
      </div>
      <button class="dm-dispatch-mission-cta" @click="$emit('navigate', primaryActionPath)">
        {{ primaryActionLabel }}
      </button>
    </div>
    <div class="dm-dispatch-workspace">
      <section class="dm-dispatch-assist-card dm-dispatch-assist-card--summary">
          <div class="dm-dispatch-section-head">
            <span class="dm-dispatch-section-title">AI 值班摘要</span>
            <button class="dm-dispatch-link" @click="$emit('toggle-ai')">
              {{ mineAiLoading ? '分析中…' : mineAiReport ? '查看全文' : '生成分析' }}
            </button>
          </div>
          <p class="dm-dispatch-ai-text">{{ dashboardAiSummary }}</p>
          <div class="dm-dispatch-tags">
            <span v-if="riskDeptList[0]" class="dm-dispatch-tag">重点部门：{{ riskDeptList[0].name }}</span>
            <span v-if="latestDangerEvent?.userName" class="dm-dispatch-tag">重点人员：{{ latestDangerEvent.userName }}</span>
          </div>
      </section>

      <section class="dm-dispatch-assist-card dm-dispatch-assist-card--focus">
          <div class="dm-dispatch-section-head">
            <span class="dm-dispatch-section-title">重点人员</span>
            <span class="dm-dispatch-section-note">{{ focusWarningEvents.length }} 人</span>
          </div>
          <div v-if="focusWarningEvents.length" class="dm-dispatch-focus-list">
            <button
              v-for="(item, idx) in focusWarningEvents"
              :key="item.id || idx"
              class="dm-dispatch-person"
              @click="$emit('person-click', item)"
            >
              <span class="dm-dispatch-person-name">{{ item.userName || '--' }}</span>
              <span class="dm-dispatch-person-type">{{ item.type || item.warningType || '预警' }}</span>
              <span class="dm-dispatch-person-val">{{ item.value || item.warningValue || '--' }}</span>
            </button>
          </div>
          <div v-else class="dm-dispatch-empty">当前没有待处理重点人员。</div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue'

interface DispatchPriority {
  tone?: string
  title?: string
  sub?: string
}

interface DispatchActionItem {
  path?: string
  cta?: string
  [key: string]: unknown
}

interface DashboardWarningEvent {
  id?: number | string
  userName?: string
  type?: string
  warningType?: string
  value?: string | number
  warningValue?: string | number
  [key: string]: unknown
}

interface RiskDepartment {
  name?: string
  [key: string]: unknown
}

const props = defineProps({
  dispatchPriority: { type: Object as PropType<DispatchPriority>, required: true },
  dispatchActionItems: { type: Array as PropType<DispatchActionItem[]>, required: true },
  focusWarningEvents: { type: Array as PropType<DashboardWarningEvent[]>, default: () => [] },
  mineAiReport: { type: String, default: '' },
  mineAiLoading: { type: Boolean, default: false },
  dashboardAiSummary: { type: String, default: '' },
  riskDeptList: { type: Array as PropType<RiskDepartment[]>, default: () => [] },
  latestDangerEvent: { type: Object as PropType<DashboardWarningEvent | null>, default: null }
})

defineEmits<{
  navigate: [path: string]
  'person-click': [event: DashboardWarningEvent]
  'toggle-ai': []
}>()

const primaryActionPath = computed(() => props.dispatchActionItems[0]?.path || '/alert-management/notifications')
const primaryActionLabel = computed(() => props.dispatchActionItems[0]?.cta || '进入待处理')
</script>

<style scoped>
.dm-dispatch-shell {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  padding: 0;
}

.dm-dispatch-mission {
  --dispatch-tone: var(--accent-primary);
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  position: relative;
  overflow: hidden;
  min-height: 156px;
  padding: 18px;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--dispatch-tone) 24%, transparent);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--dispatch-tone) 11%, rgba(8, 15, 30, 0.88)), rgba(8, 15, 30, 0.88)),
    linear-gradient(135deg, rgba(255,255,255,0.04), transparent 48%);
  box-shadow:
    inset 0 1px 0 rgba(255,255,255,0.07),
    0 18px 36px -26px color-mix(in srgb, var(--dispatch-tone) 28%, transparent);
}
.dm-dispatch-mission::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 100% 0%, rgba(255,255,255,0.08), transparent 42%);
  pointer-events: none;
}
.dm-dispatch-mission.tone-danger { --dispatch-tone: var(--accent-danger); }
.dm-dispatch-mission.tone-warn { --dispatch-tone: var(--accent-warning); }
.dm-dispatch-mission.tone-accent { --dispatch-tone: var(--accent-primary); }
.dm-dispatch-mission.tone-calm { --dispatch-tone: var(--accent-success); }
.dm-dispatch-mission-copy { position: relative; z-index: 1; min-width: 0; }
.dm-dispatch-mission-label { font-size: 12px; color: rgba(139,166,200,0.84); }
.dm-dispatch-mission-title {
  margin-top: 10px;
  font-size: 21px;
  font-weight: 800;
  color: #eef7ff;
  line-height: 1.18;
  letter-spacing: -0.03em;
}
.dm-dispatch-mission-sub {
  max-width: 620px;
  margin-top: 10px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(153,190,220,0.92);
}
.dm-dispatch-mission-cta {
  position: relative;
  z-index: 1;
  width: 100%;
  min-height: 44px;
  padding: 0 18px;
  border: 1px solid color-mix(in srgb, var(--dispatch-tone) 40%, transparent);
  border-radius: 12px;
  background: color-mix(in srgb, var(--dispatch-tone) 16%, rgba(7, 19, 34, 0.96));
  color: #eef7ff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, background 0.18s ease;
}
.dm-dispatch-mission-cta:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--dispatch-tone) 58%, transparent);
  background: color-mix(in srgb, var(--dispatch-tone) 22%, rgba(7, 19, 34, 0.98));
}

.dm-dispatch-workspace {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
  align-items: start;
}

.dm-dispatch-assist-card {
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(255,255,255,0.03), rgba(255,255,255,0.02));
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.03);
}

.dm-dispatch-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.dm-dispatch-section-title {
  font-size: 13px;
  font-weight: 700;
  color: #eef7ff;
}
.dm-dispatch-section-note {
  font-size: 11px;
  color: rgba(139,166,200,0.78);
}
.dm-dispatch-assist-card {
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 14px;
}
.dm-dispatch-assist-card--summary {
  gap: 12px;
}
.dm-dispatch-link {
  border: 1px solid rgba(0,212,255,0.22);
  background: rgba(0,212,255,0.08);
  color: #b7e9ff;
  border-radius: 10px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}
.dm-dispatch-link:hover { background: rgba(0,212,255,0.16); }
.dm-dispatch-ai-text {
  margin: 0;
  color: #d5ebff;
  font-size: 12px;
  line-height: 1.85;
}
.dm-dispatch-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: auto;
}
.dm-dispatch-tag {
  display: inline-flex;
  align-items: center;
  color: #a9dfff;
  background: rgba(0,212,255,0.06);
  border: 1px solid rgba(0,212,255,0.14);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 11px;
}
.dm-dispatch-focus-list {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  margin-top: 12px;
  min-height: 0;
}
.dm-dispatch-person {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding: 11px 12px;
  border-radius: 14px;
  border: 1px solid rgba(0, 200, 255, 0.04);
  background: rgba(255, 255, 255, 0.02);
  cursor: pointer;
  text-align: left;
  transition: transform 0.2s ease, border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;
}
.dm-dispatch-person:hover {
  background: rgba(255, 59, 59, 0.06);
  border-color: rgba(255, 59, 59, 0.2);
  box-shadow: 0 4px 16px -6px rgba(255, 59, 59, 0.4);
  transform: translateY(-1px);
}
.dm-dispatch-person-name { font-size: 13px; font-weight: 700; color: #e8f4ff; }
.dm-dispatch-person-type,
.dm-dispatch-person-val { font-size: 12px; color: #8fb5d3; }
.dm-dispatch-empty {
  margin-top: 12px;
  color: rgba(139,166,200,0.74);
  font-size: 12px;
  line-height: 1.7;
}

@media (max-width: 768px) {
  .dm-dispatch-shell {
    gap: 10px;
    padding: 10px 10px 12px;
  }

  .dm-dispatch-mission {
    gap: 12px;
    min-height: auto;
    padding: 16px;
  }

  .dm-dispatch-workspace {
    grid-template-columns: 1fr;
  }

  .dm-dispatch-focus-list {
    grid-template-columns: 1fr;
  }

  .dm-dispatch-person {
    grid-template-columns: 1fr;
    align-items: flex-start;
  }

}
</style>
