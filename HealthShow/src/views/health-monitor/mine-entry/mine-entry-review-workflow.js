import dayjs from 'dayjs'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applyPreShiftReviewAction } from '@/api/command-center'

export function normalizeMineEntryStatus(value) {
  return ['pass', 'fail', 'review', 'overdue'].includes(value) ? value : ''
}

export function buildReviewMap(reviewList) {
  return new Map((reviewList || []).map(item => [item.empCode, item]))
}

export function summarizeReviews(reviewList) {
  const reviews = reviewList || []
  return {
    awaitingReview: reviews.filter(item => ['PENDING', 'IN_REVIEW'].includes(item.reviewStatus)).length,
    retestOverdue: reviews.filter(item => item.overdue).length
  }
}

export function matchesReviewFilter(item, filterStatus, reviewMap) {
  if (filterStatus !== 'review' && filterStatus !== 'overdue') return true
  const review = reviewMap.get(item.empCode)
  if (item.qualified || !review) return false
  return filterStatus === 'overdue'
    ? Boolean(review.overdue)
    : ['PENDING', 'IN_REVIEW'].includes(review.reviewStatus)
}

export function attachReviews(items, reviewMap) {
  return items.map(item => ({ ...item, review: reviewMap.get(item.empCode) || null }))
}

export function reviewStatusLabel(review) {
  if (review.reviewStatus === 'COMPLETED' && review.reviewResult === 'PROHIBITED') return '已确认禁入'
  if (review.reviewStatus === 'COMPLETED' && review.reviewResult === 'PASSED') return '复检通过'
  if (review.reviewResult === 'RETEST_REQUIRED') return review.overdue ? '复检已超时' : '等待复检'
  if (review.reviewStatus === 'IN_REVIEW') return review.overdue ? '处置已超时' : '复检处理中'
  return review.overdue ? '待复检已超时' : '待复检'
}

export function formatReviewDeadline(value) {
  return value ? dayjs(value).format('HH:mm') : '--'
}

export function createReviewActionHandler(reload) {
  return async (item, action) => {
    if (!item.review) return
    const remark = await requestActionRemark(item, action)
    if (remark === null) return
    try {
      const response = await applyPreShiftReviewAction(item.empCode, {
        reviewDate: item.review.reviewDate,
        sourceRecordTime: item.review.sourceRecordTime,
        action,
        remark
      })
      if (response.code !== 200) {
        ElMessage.error(response.message || '复检状态更新失败')
        return
      }
      ElMessage.success(action === 'CLAIM' ? '已接手复检任务' : action === 'REQUEST_RETEST' ? '已记录复检要求' : '已确认禁止入井')
      await reload()
    } catch {
      ElMessage.error('复检状态更新失败，请刷新后重试')
    }
  }
}

async function requestActionRemark(item, action) {
  if (action === 'CLAIM') return ''
  const title = action === 'REQUEST_RETEST' ? '要求重新检测' : '确认禁止入井'
  const placeholder = action === 'REQUEST_RETEST' ? '填写复检安排或通知情况' : '填写确认依据和后续安排'
  try {
    const result = await ElMessageBox.prompt(`${item.empName} · ${item.empCode}`, title, {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      inputPlaceholder: placeholder,
      inputValidator: value => !!value?.trim() || '处置说明不能为空'
    })
    return result.value.trim()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('复检操作未完成')
    return null
  }
}
