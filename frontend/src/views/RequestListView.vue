<script setup>
// 申請一覧（自分の申請）。使用 API: GET /api/requests?status=
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AppLayout from '../components/AppLayout.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { api } from '../api/client'
import { fmtPeriod, fmtDays, fmtDateTime, REQUEST_TYPE, REQUEST_STATUS } from '../utils/format'

const router = useRouter()
const status = ref('')
const rows = ref([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true; error.value = ''
  try {
    const q = status.value ? `?status=${encodeURIComponent(status.value)}` : ''
    const res = await api('GET', `/api/requests${q}`)
    rows.value = res?.requests ?? []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

const open = (r) => router.push(`/requests/${r.requestId}`)
onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="page-head">
      <h2>申請一覧</h2>
      <router-link to="/requests/new" class="btn">新規申請</router-link>
    </div>

    <section class="card">
      <div class="toolbar">
        <label class="field">
          状態
          <select v-model="status" @change="load">
            <option value="">すべて</option>
            <option v-for="(v, k) in REQUEST_STATUS" :key="k" :value="k">{{ v.label }}</option>
          </select>
        </label>
        <button class="btn btn-ghost" :disabled="loading" @click="load">再読込</button>
      </div>

      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
      <p v-if="loading" class="muted">読み込み中…</p>

      <div v-else class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>種別</th><th>休暇種別</th><th>期間</th><th class="num">日数</th>
              <th>状態</th><th>提出日時</th><th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.requestId" class="row" @click="open(r)">
              <td>{{ REQUEST_TYPE[r.requestType] ?? r.requestType }}</td>
              <td>{{ r.leaveTypeName ?? '—' }}</td>
              <td>{{ fmtPeriod(r.startDate, r.endDate) }}</td>
              <td class="num">{{ r.requestType === 'LEAVE' ? fmtDays(r.days) : '—' }}</td>
              <td><StatusBadge :status="r.status" /></td>
              <td>{{ r.submittedAt ? fmtDateTime(r.submittedAt) : '未提出' }}</td>
              <td><router-link class="link" :to="`/requests/${r.requestId}`" @click.stop>詳細</router-link></td>
            </tr>
            <tr v-if="!rows.length"><td colspan="7" class="empty">申請はありません。</td></tr>
          </tbody>
        </table>
      </div>
    </section>
  </AppLayout>
</template>

<style scoped>
.row { cursor: pointer; }
a.btn { text-decoration: none; }
</style>
