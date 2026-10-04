<script setup>
// 申請詳細。使用 API: GET /api/requests/{id}, POST /api/requests/{id}/submit|withdraw,
// GET /api/approvals/pending（承認者のみ）, POST /api/approvals/{stepId}/approve|return|reject
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import StatusBadge from '../components/StatusBadge.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { api, auth } from '../api/client'
import { fmtPeriod, fmtDays, fmtDateTime, REQUEST_TYPE, UNIT, STEP_STATUS } from '../utils/format'

const route = useRoute()
const id = computed(() => route.params.id)
const me = auth.user()

const detail = ref(null)
const pendingStep = ref(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const message = ref('')
const dlg = ref(null) // 'withdraw' | 'approve' | 'return' | 'reject'

const req = computed(() => detail.value?.request ?? null)
const steps = computed(() => detail.value?.steps ?? [])
const isOwner = computed(() => req.value && me && req.value.applicantId === me.userId)
const canSubmit = computed(() => isOwner.value && ['DRAFT', 'RETURNED'].includes(req.value?.status))
const canWithdraw = computed(() => isOwner.value && ['DRAFT', 'RETURNED', 'PENDING'].includes(req.value?.status))
const returnedComment = computed(() => {
  if (req.value?.status !== 'RETURNED') return null
  const list = steps.value.filter(s => s.status === 'RETURNED' && s.comment)
  return list.length ? list[list.length - 1] : null
})

const ACTION = {
  withdraw: { title: '申請を取り下げますか？', ok: '取り下げる', danger: true },
  approve: { title: '承認しますか？', ok: '承認する', danger: false, label: 'コメント（任意）', required: false, path: 'approve', done: '承認しました。' },
  return: { title: '差戻しますか？', ok: '差戻す', danger: true, label: 'コメント', required: true, path: 'return', done: '差戻しました。' },
  reject: { title: '却下しますか？', ok: '却下する', danger: true, label: 'コメント', required: true, path: 'reject', done: '却下しました。' }
}

async function load() {
  error.value = ''
  try {
    detail.value = await api('GET', `/api/requests/${id.value}`)
  } catch (e) { error.value = e.message; loading.value = false; return }
  await loadPending()
  loading.value = false
}

// 承認者として処理可能なステップを探す（MANAGER/ADMIN のみ。403 などは無視）
async function loadPending() {
  pendingStep.value = null
  if (!me || !['MANAGER', 'ADMIN'].includes(me.role)) return
  if (req.value?.status !== 'PENDING') return
  try {
    const res = await api('GET', '/api/approvals/pending')
    pendingStep.value = (res?.items ?? []).find(it => String(it.requestId) === String(id.value)) ?? null
  } catch { pendingStep.value = null }
}

async function doSubmit() {
  busy.value = true; error.value = ''; message.value = ''
  try {
    await api('POST', `/api/requests/${id.value}/submit`)
    message.value = '申請を提出しました。'
    await load()
  } catch (e) { error.value = e.message } finally { busy.value = false }
}

async function onOk(comment) {
  const kind = dlg.value
  busy.value = true; error.value = ''; message.value = ''
  try {
    if (kind === 'withdraw') {
      await api('POST', `/api/requests/${id.value}/withdraw`)
      message.value = '申請を取り下げました。'
    } else {
      await api('POST', `/api/approvals/${pendingStep.value.stepId}/${ACTION[kind].path}`, { comment: comment || null })
      message.value = ACTION[kind].done
    }
    dlg.value = null
    await load()
  } catch (e) { error.value = e.message; dlg.value = null } finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="page-head">
      <h2>申請詳細</h2>
      <router-link :to="isOwner === false && pendingStep ? '/approvals' : '/requests'" class="btn btn-ghost">一覧へ戻る</router-link>
    </div>

    <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
    <p v-if="loading" class="muted">読み込み中…</p>

    <template v-if="req">
      <section v-if="returnedComment" class="card returned">
        <h3>差戻しコメント</h3>
        <p class="returned-body">{{ returnedComment.comment }}</p>
        <p class="muted">{{ returnedComment.approverName }}（{{ returnedComment.stepName }}）／{{ fmtDateTime(returnedComment.actedAt) }}</p>
      </section>

      <section class="card">
        <h3>申請内容 <StatusBadge :status="req.status" /></h3>
        <dl class="kv">
          <dt>申請種別</dt><dd>{{ REQUEST_TYPE[req.requestType] ?? req.requestType }}</dd>
          <dt>申請者</dt><dd>{{ req.applicantName }}<span v-if="req.departmentName" class="muted">（{{ req.departmentName }}）</span></dd>
          <template v-if="req.requestType === 'LEAVE'">
            <dt>休暇種別</dt><dd>{{ req.leaveTypeName ?? '—' }}</dd>
            <dt>取得単位</dt><dd>{{ UNIT[req.unit] ?? req.unit ?? '—' }}</dd>
            <dt>期間</dt><dd>{{ fmtPeriod(req.startDate, req.endDate) }}</dd>
            <dt>日数</dt><dd>{{ fmtDays(req.days) }}</dd>
            <template v-if="detail.applicantBalance != null">
              <dt>申請者の残日数</dt><dd>{{ fmtDays(detail.applicantBalance) }}</dd>
            </template>
          </template>
          <template v-else>
            <dt>対象日</dt><dd>{{ fmtPeriod(req.startDate, req.startDate) }}</dd>
            <dt>修正後 出勤</dt><dd>{{ req.correctedClockIn ?? '—' }}</dd>
            <dt>修正後 退勤</dt><dd>{{ req.correctedClockOut ?? '—' }}</dd>
          </template>
          <dt>理由</dt><dd class="pre">{{ req.reason || '—' }}</dd>
          <dt>作成日時</dt><dd>{{ fmtDateTime(req.createdAt) }}</dd>
          <dt>提出日時</dt><dd>{{ req.submittedAt ? fmtDateTime(req.submittedAt) : '未提出' }}</dd>
        </dl>

        <div v-if="canSubmit || canWithdraw" class="actions-row ops">
          <button v-if="canSubmit" class="btn" :disabled="busy" @click="doSubmit">提出する</button>
          <button v-if="canWithdraw" class="btn btn-danger" :disabled="busy" @click="dlg = 'withdraw'">取下げ</button>
        </div>
        <div v-if="pendingStep" class="actions-row ops">
          <button class="btn" :disabled="busy" @click="dlg = 'approve'">承認</button>
          <button class="btn btn-ghost" :disabled="busy" @click="dlg = 'return'">差戻し</button>
          <button class="btn btn-danger" :disabled="busy" @click="dlg = 'reject'">却下</button>
        </div>
      </section>

      <section class="card">
        <h3>承認ステップ</h3>
        <div class="table-wrap">
          <table class="table">
            <thead><tr><th>段</th><th>名称</th><th>承認者</th><th>状態</th><th>コメント</th><th>処理日時</th></tr></thead>
            <tbody>
              <tr v-for="s in steps" :key="s.stepNo" :class="{ current: s.current }">
                <td>{{ s.stepNo }}</td>
                <td>{{ s.stepName }}<span v-if="s.current" class="now">処理中</span></td>
                <td>{{ s.approverName ?? '—' }}</td>
                <td><StatusBadge :status="s.status" :map="STEP_STATUS" /></td>
                <td class="cmt">{{ s.comment || '—' }}</td>
                <td>{{ s.actedAt ? fmtDateTime(s.actedAt) : '—' }}</td>
              </tr>
              <tr v-if="!steps.length"><td colspan="6" class="empty">承認ステップはまだありません。</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>

    <ConfirmDialog
      v-if="dlg"
      :title="ACTION[dlg].title"
      :message="dlg === 'withdraw' ? '取り下げた申請は元に戻せません。' : ''"
      :ok-label="ACTION[dlg].ok"
      :danger="ACTION[dlg].danger"
      :comment-label="ACTION[dlg].label ?? ''"
      :comment-required="ACTION[dlg].required ?? false"
      :busy="busy"
      @ok="onOk"
      @cancel="dlg = null"
    />
  </AppLayout>
</template>

<style scoped>
a.btn { text-decoration: none; }
.ops { margin-top: 20px; }
.pre { white-space: pre-wrap; word-break: break-word; }
.returned { border-color: #f0b46a; background: #fff7ea; }
.returned h3 { color: #b45309; }
.returned-body { margin: 0 0 6px; font-size: 16px; font-weight: 600; white-space: pre-wrap; }
.current { background: var(--navy-100); }
.current td { font-weight: 600; }
.now { margin-left: 8px; padding: 0 8px; font-size: 11px; color: #fff; background: var(--navy-500); border-radius: 10px; }
.cmt { white-space: normal; min-width: 160px; }
@media (max-width: 600px) { .kv { grid-template-columns: 100px 1fr; } }
</style>
