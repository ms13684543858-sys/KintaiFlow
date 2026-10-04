<script setup>
// 承認待ち一覧。使用 API: GET /api/approvals/pending, POST /api/approvals/{stepId}/approve|return|reject
import { ref, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { api } from '../api/client'
import { fmtPeriod, fmtDays, fmtDateTime, REQUEST_TYPE } from '../utils/format'

const requestType = ref('')
const stepNo = ref('')
const items = ref([])
const loading = ref(true)
const error = ref('')
const message = ref('')
const busy = ref(false)
const dlg = ref(null) // { kind: 'approve'|'return'|'reject', item }

const KIND = {
  approve: { path: 'approve', title: '承認しますか？', ok: '承認する', done: '承認しました。', required: false, danger: false },
  return: { path: 'return', title: '差戻しますか？', ok: '差戻す', done: '差戻しました。', required: true, danger: true },
  reject: { path: 'reject', title: '却下しますか？', ok: '却下する', done: '却下しました。', required: true, danger: true }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const p = new URLSearchParams()
    if (requestType.value) p.set('requestType', requestType.value)
    if (stepNo.value) p.set('stepNo', stepNo.value)
    const q = p.toString() ? `?${p}` : ''
    const res = await api('GET', `/api/approvals/pending${q}`)
    items.value = res?.items ?? []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function act(comment) {
  const { kind, item } = dlg.value
  busy.value = true; error.value = ''; message.value = ''
  try {
    await api('POST', `/api/approvals/${item.stepId}/${KIND[kind].path}`, { comment: comment || null })
    message.value = `${item.applicantName} さんの申請を${KIND[kind].done}`
    dlg.value = null
    await load()
  } catch (e) { error.value = e.message; dlg.value = null } finally { busy.value = false }
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="page-head"><h2>承認待ち一覧</h2></div>

    <section class="card">
      <div class="toolbar">
        <label class="field">
          申請種別
          <select v-model="requestType" @change="load">
            <option value="">すべて</option>
            <option v-for="(v, k) in REQUEST_TYPE" :key="k" :value="k">{{ v }}</option>
          </select>
        </label>
        <label class="field">
          承認段階
          <select v-model="stepNo" @change="load">
            <option value="">すべて</option>
            <option value="1">1段目</option>
            <option value="2">2段目</option>
          </select>
        </label>
        <button class="btn btn-ghost" :disabled="loading" @click="load">再読込</button>
      </div>

      <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
      <p class="hint">差戻し・却下はコメントが必須です。内容を確認する場合は「詳細」から開いてください。</p>
      <p v-if="loading" class="muted">読み込み中…</p>

      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>申請者</th><th>部署</th><th>種別</th><th>期間</th><th class="num">日数</th>
              <th>提出日時</th><th>段階</th><th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="it in items" :key="it.stepId">
              <td>{{ it.applicantName }}</td>
              <td>{{ it.departmentName ?? '—' }}</td>
              <td>{{ REQUEST_TYPE[it.requestType] ?? it.requestType }}</td>
              <td>{{ fmtPeriod(it.startDate, it.endDate) }}</td>
              <td class="num">{{ it.requestType === 'LEAVE' ? fmtDays(it.days) : '—' }}</td>
              <td>{{ fmtDateTime(it.submittedAt) }}</td>
              <td>{{ it.stepNo }}段目</td>
              <td>
                <div class="actions-row">
                  <router-link class="link" :to="`/requests/${it.requestId}`">詳細</router-link>
                  <button class="btn btn-sm" :disabled="busy" @click="dlg = { kind: 'approve', item: it }">承認</button>
                  <button class="btn btn-sm btn-ghost" :disabled="busy" @click="dlg = { kind: 'return', item: it }">差戻し</button>
                  <button class="btn btn-sm btn-danger" :disabled="busy" @click="dlg = { kind: 'reject', item: it }">却下</button>
                </div>
              </td>
            </tr>
            <tr v-if="!items.length"><td colspan="8" class="empty">承認待ちの申請はありません。</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <ConfirmDialog
      v-if="dlg"
      :title="KIND[dlg.kind].title"
      :message="`${dlg.item.applicantName} さんの${REQUEST_TYPE[dlg.item.requestType] ?? ''}（${fmtPeriod(dlg.item.startDate, dlg.item.endDate)}）`"
      :ok-label="KIND[dlg.kind].ok"
      :danger="KIND[dlg.kind].danger"
      :comment-label="KIND[dlg.kind].required ? 'コメント' : 'コメント（任意）'"
      :comment-required="KIND[dlg.kind].required"
      :busy="busy"
      @ok="act"
      @cancel="dlg = null"
    />
  </AppLayout>
</template>
