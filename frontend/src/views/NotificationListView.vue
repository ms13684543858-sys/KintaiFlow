<script setup>
// 通知一覧画面。使用 API: GET /api/notifications, POST /api/notifications/{id}/read
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import { api } from '../api/client'
import { fmtDateTime } from '../utils/format'

const router = useRouter()
const items = ref([])
const loading = ref(true)
const error = ref('')
const busyId = ref(null)

const TYPE = {
  REQUEST_SUBMITTED: { label: '承認依頼', tone: 'blue' },
  REQUEST_APPROVED: { label: '承認', tone: 'green' },
  REQUEST_RETURNED: { label: '差戻し', tone: 'orange' },
  REQUEST_REJECTED: { label: '却下', tone: 'red' },
  REQUEST_WITHDRAWN: { label: '取下げ', tone: 'gray' },
  LEAVE_GRANTED: { label: '休暇付与', tone: 'green' },
  LEAVE_ALERT: { label: '年5日アラート', tone: 'red' }
}
const typeOf = (t) => TYPE[t] ?? { label: t, tone: 'gray' }

async function load() {
  loading.value = true; error.value = ''
  try {
    const res = await api('GET', '/api/notifications')
    items.value = res.notifications
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function open(n) {
  if (busyId.value != null) return
  error.value = ''
  if (!n.isRead) {
    busyId.value = n.id
    try {
      const res = await api('POST', `/api/notifications/${n.id}/read`)
      n.isRead = res?.isRead ?? true
    } catch (e) { error.value = e.message; busyId.value = null; return }
    busyId.value = null
  }
  if (n.requestId) router.push(`/requests/${n.requestId}`)
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="page-head"><h2>通知</h2></div>
    <p class="muted note">最新50件を新しい順に表示します。クリックすると既読になります。</p>

    <p v-if="loading" class="muted">読み込み中…</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <section v-if="!loading" class="card list">
      <p v-if="!items.length" class="muted empty">通知はありません。</p>
      <ul v-else>
        <li v-for="n in items" :key="n.id">
          <button type="button" class="item" :class="{ unread: !n.isRead }" :disabled="busyId === n.id" @click="open(n)">
            <span class="dot" :aria-label="n.isRead ? '既読' : '未読'"></span>
            <span class="badge" :class="'badge-' + typeOf(n.type).tone">{{ typeOf(n.type).label }}</span>
            <span class="msg">{{ n.message }}</span>
            <span class="at">{{ fmtDateTime(n.createdAt) }}</span>
          </button>
        </li>
      </ul>
    </section>
  </AppLayout>
</template>

<style scoped>
.note { margin: 0 0 12px; font-size: 13px; }
.list { padding: 0; overflow: hidden; }
.list ul { list-style: none; margin: 0; padding: 0; }
.list li + li { border-top: 1px solid var(--border); }
.empty { padding: 28px; text-align: center; margin: 0; }
.item { display: flex; align-items: center; gap: 12px; width: 100%; padding: 14px 20px; font: inherit; text-align: left; color: inherit; background: #fff; border: 0; cursor: pointer; }
.item:hover:not(:disabled) { background: #f8fafd; }
.item.unread { background: var(--navy-100); font-weight: 600; }
.item.unread:hover:not(:disabled) { background: #dbe6f6; }
.dot { flex: none; width: 8px; height: 8px; border-radius: 50%; background: transparent; }
.item.unread .dot { background: var(--danger); }
.badge { flex: none; }
.msg { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.at { flex: none; font-size: 12px; font-weight: 400; color: var(--muted); }
@media (max-width: 700px) {
  .item { flex-wrap: wrap; padding: 12px 14px; }
  .msg { flex-basis: 100%; order: 3; }
}
</style>
