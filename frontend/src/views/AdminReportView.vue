<script setup>
// 月次集計（管理者専用）
// 使用 API: GET /api/admin/reports/monthly?month=yyyy-MM&departmentId=&format=json
//           同 format=csv（UTF-8 BOM 付き・CRLF。ファイル名は Content-Disposition の kintai_summary_yyyyMM.csv）
import { ref, computed, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { api, auth } from '../api/client'
import { fmtMinutes, ymOf } from '../utils/format'

const DEPARTMENTS = ref([]) // GET /api/departments から取得
async function loadDepartments() {
  try { DEPARTMENTS.value = (await api('GET', '/api/departments')).departments } catch (e) { /* 一覧が出ないだけなので握りつぶす */ }
}
const OVERTIME_LIMIT = 2700 // 45時間（分）

const month = ref(ymOf(new Date()))
const departmentId = ref('')
const rows = ref([])
const loading = ref(false)
const downloading = ref(false)
const error = ref('')
const loaded = ref(false)

const totals = computed(() => rows.value.reduce((t, r) => ({
  workDays: t.workDays + Number(r.workDays ?? 0),
  workMinutes: t.workMinutes + Number(r.workMinutes ?? 0),
  overtimeMinutes: t.overtimeMinutes + Number(r.overtimeMinutes ?? 0),
  leaveDays: t.leaveDays + Number(r.leaveDays ?? 0)
}), { workDays: 0, workMinutes: 0, overtimeMinutes: 0, leaveDays: 0 }))

function query(format) {
  const p = new URLSearchParams({ month: month.value, format })
  if (departmentId.value !== '') p.set('departmentId', departmentId.value)
  return p.toString()
}

async function load() {
  if (!month.value) { error.value = '対象月を選択してください。'; return }
  loading.value = true; error.value = ''
  try {
    const res = await api('GET', `/api/admin/reports/monthly?${query('json')}`)
    rows.value = res.rows ?? []
    loaded.value = true
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function downloadCsv() {
  if (!month.value) { error.value = '対象月を選択してください。'; return }
  downloading.value = true; error.value = ''
  try {
    let res
    try {
      res = await fetch(`/api/admin/reports/monthly?${query('csv')}`, {
        headers: { Authorization: 'Bearer ' + auth.token() }
      })
    } catch { throw { message: 'サーバーに接続できません。' } }
    if (!res.ok) {
      if (res.status === 401) { auth.clear(); window.location.href = '/login'; return }
      let msg = 'CSVのダウンロードに失敗しました。'
      try { msg = (await res.json()).message ?? msg } catch { /* 本文なし */ }
      throw { message: msg }
    }
    const blob = await res.blob()
    // ファイル名は Content-Disposition 優先、無ければ既定名（kintai_summary_yyyyMM.csv）
    let name = `kintai_summary_${month.value.replace('-', '')}.csv`
    const cd = res.headers.get('Content-Disposition') || ''
    const m = /filename\*=UTF-8''([^;]+)/i.exec(cd) || /filename="?([^";]+)"?/i.exec(cd)
    if (m) { try { name = decodeURIComponent(m[1]) } catch { name = m[1] } }
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = name
    document.body.appendChild(a); a.click(); a.remove()
    URL.revokeObjectURL(url)
  } catch (e) { error.value = e.message } finally { downloading.value = false }
}

onMounted(() => { loadDepartments(); load() })
</script>

<template>
  <AppLayout>
    <div class="page-head">
      <h2>月次集計</h2>
      <button class="btn" :disabled="downloading || loading" @click="downloadCsv">CSVダウンロード</button>
    </div>

    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <section class="card">
      <form class="toolbar" @submit.prevent="load">
        <label class="field">対象月
          <input v-model="month" type="month" />
        </label>
        <label class="field">部署
          <select v-model="departmentId">
            <option value="">全部署</option>
            <option v-for="d in DEPARTMENTS" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="loading">集計</button>
      </form>

      <p v-if="loading" class="muted">読み込み中…</p>
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr>
              <th>氏名</th><th>部署</th>
              <th class="num">出勤日数</th><th class="num">勤務時間</th><th class="num">残業時間</th><th class="num">休暇日数</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="r in rows" :key="r.userId">
              <td>{{ r.userName }}</td>
              <td>{{ r.departmentName || '—' }}</td>
              <td class="num">{{ r.workDays }}日</td>
              <td class="num">{{ fmtMinutes(r.workMinutes) }}</td>
              <td class="num" :class="{ over: r.overtimeMinutes > OVERTIME_LIMIT }">
                {{ fmtMinutes(r.overtimeMinutes) }}<span v-if="r.overtimeMinutes > OVERTIME_LIMIT">（45時間超）</span>
              </td>
              <td class="num">{{ Number(r.leaveDays).toFixed(1) }}日</td>
            </tr>
            <tr v-if="!loading && !rows.length"><td colspan="6" class="empty">{{ loaded ? '該当するデータはありません。' : '対象月を選んで集計してください。' }}</td></tr>
          </tbody>
          <tfoot v-if="rows.length">
            <tr class="total">
              <th colspan="2">合計（{{ rows.length }}名）</th>
              <th class="num">{{ totals.workDays }}日</th>
              <th class="num">{{ fmtMinutes(totals.workMinutes) }}</th>
              <th class="num">{{ fmtMinutes(totals.overtimeMinutes) }}</th>
              <th class="num">{{ totals.leaveDays.toFixed(1) }}日</th>
            </tr>
          </tfoot>
        </table>
      </div>
      <p class="hint" style="margin-top: 8px">残業時間が45時間を超える社員は赤字で表示します。</p>
    </section>
  </AppLayout>
</template>

<style scoped>
.over { color: var(--danger); font-weight: 700; }
.total th { padding: 10px 12px; background: #f8fafd; border-top: 2px solid var(--border); }
</style>
