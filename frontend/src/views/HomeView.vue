<script setup>
// SCR-102 打刻 + SCR-103 月次勤怠一覧（1画面にまとめる）。時刻の記録は常にサーバー側で行う。
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { auth, api } from '../api/client'

const user = auth.user()
const now = ref(new Date())
let timer
const month = ref(ymOf(new Date()))
const records = ref([])
const totals = ref({ work: 0, overtime: 0 })
const busy = ref(false)
const message = ref('')
const error = ref('')

function ymOf(d) { return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}` }
function dateOf(d) { return `${ymOf(d)}-${String(d.getDate()).padStart(2, '0')}` }
const currentYm = ymOf(new Date())

const todayRecord = computed(() => records.value.find(r => r.workDate === dateOf(new Date())))
const canClockIn = computed(() => month.value === currentYm && !todayRecord.value?.clockIn)
const canClockOut = computed(() => month.value === currentYm && todayRecord.value?.clockIn && !todayRecord.value?.clockOut)

const clockText = computed(() => now.value.toLocaleTimeString('ja-JP', { hour12: false }))
const dateText = computed(() => now.value.toLocaleDateString('ja-JP', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }))

const hm = (iso) => iso ? iso.substring(11, 16) : '—'
const dur = (m) => m == null ? '—' : `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')}`
const dow = (s) => '日月火水木金土'[new Date(s + 'T00:00:00').getDay()]

async function load() {
  error.value = ''
  try {
    const res = await api('GET', `/api/attendance?month=${month.value}`)
    records.value = res.records
    totals.value = { work: res.totalWorkMinutes, overtime: res.totalOvertimeMinutes }
  } catch (e) { error.value = e.message }
}

async function stamp(kind) {
  busy.value = true; message.value = ''; error.value = ''
  try {
    const res = await api('POST', `/api/attendance/${kind}`)
    message.value = kind === 'clock-in'
      ? `出勤を記録しました（${hm(res.clockIn)}）。`
      : `退勤を記録しました（${hm(res.clockOut)}／勤務 ${dur(res.workMinutes)}）。`
    await load()
  } catch (e) { error.value = e.message } finally { busy.value = false }
}

function shift(delta) {
  const [y, m] = month.value.split('-').map(Number)
  const d = new Date(y, m - 1 + delta, 1)
  const next = ymOf(d)
  if (next > currentYm) return
  month.value = next
  load()
}

onMounted(() => { timer = setInterval(() => { now.value = new Date() }, 1000); load() })
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <AppLayout>
    <section class="card stamp">
      <div class="date">{{ dateText }}</div>
      <div class="time">{{ clockText }}</div>
      <p class="who">{{ user?.name }} さん</p>
      <div class="actions">
        <button class="btn big" :disabled="busy || !canClockIn" @click="stamp('clock-in')">出勤</button>
        <button class="btn big out" :disabled="busy || !canClockOut" @click="stamp('clock-out')">退勤</button>
      </div>
      <p class="today">
        本日：出勤 {{ hm(todayRecord?.clockIn) }}　退勤 {{ hm(todayRecord?.clockOut) }}
      </p>
      <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
      <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>
      <p class="hint">記録される時刻はサーバーの時刻（日本時間）です。打刻の訂正は「打刻修正申請」から行います。</p>
    </section>

    <section class="card">
      <div class="head">
        <button class="btn btn-ghost" @click="shift(-1)">‹ 前月</button>
        <h3>{{ month.replace('-', '年') }}月の勤怠</h3>
        <button class="btn btn-ghost" :disabled="month >= currentYm" @click="shift(1)">翌月 ›</button>
      </div>
      <table>
        <thead><tr><th>日付</th><th>出勤</th><th>退勤</th><th>勤務時間</th><th>残業</th></tr></thead>
        <tbody>
          <tr v-for="r in records" :key="r.workDate">
            <td>{{ r.workDate.substring(5).replace('-', '/') }}（{{ dow(r.workDate) }}）</td>
            <td>{{ hm(r.clockIn) }}</td><td>{{ hm(r.clockOut) }}</td>
            <td>{{ dur(r.workMinutes) }}</td><td>{{ dur(r.overtimeMinutes) }}</td>
          </tr>
          <tr v-if="!records.length"><td colspan="5" class="empty">この月の打刻はありません。</td></tr>
        </tbody>
        <tfoot><tr><th colspan="3">月合計</th><th>{{ dur(totals.work) }}</th><th>{{ dur(totals.overtime) }}</th></tr></tfoot>
      </table>
    </section>
  </AppLayout>
</template>

<style scoped>
.card { padding: 24px; margin-bottom: 24px; background: #fff; border: 1px solid var(--border); border-radius: 12px; }
.stamp { text-align: center; }
.date { color: var(--muted); }
.time { font-size: 56px; font-weight: 700; letter-spacing: .04em; font-variant-numeric: tabular-nums; color: var(--navy-900); }
.who { margin: 0 0 16px; color: var(--muted); }
.actions { display: flex; gap: 16px; justify-content: center; }
.big { min-width: 160px; padding: 16px 0; font-size: 20px; }
.out { background: #b45309; }
.out:hover:not(:disabled) { background: #92400e; }
.today { margin: 16px 0 8px; font-weight: 600; }
.stamp .alert { margin: 8px auto; max-width: 480px; }
.head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
table { width: 100%; border-collapse: collapse; font-variant-numeric: tabular-nums; }
th, td { padding: 8px 10px; text-align: right; border-bottom: 1px solid var(--border); }
th:first-child, td:first-child { text-align: left; }
thead th { color: var(--muted); font-size: 13px; }
tfoot th { border-bottom: 0; }
.empty { text-align: center; color: var(--muted); }
@media (max-width: 600px) { .time { font-size: 40px; } .big { min-width: 130px; } }
</style>
