<script setup>
// 休暇残日数画面。使用 API: GET /api/leave-balances
import { ref, computed, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { api } from '../api/client'
import { fmtDate, fmtDays } from '../utils/format'

const data = ref(null)
const loading = ref(true)
const error = ref('')

const balances = computed(() => data.value?.balances ?? [])
const m5 = computed(() => data.value?.mandatory5 ?? null)
const m5Percent = computed(() => {
  const m = m5.value
  if (!m || !Number(m.requiredDays)) return 0
  return Math.min(100, Math.round((Number(m.takenDays) / Number(m.requiredDays)) * 100))
})

async function load() {
  loading.value = true; error.value = ''
  try { data.value = await api('GET', '/api/leave-balances') }
  catch (e) { error.value = e.message } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="page-head">
      <h2>休暇残日数</h2>
      <router-link to="/requests/new" class="btn">休暇を申請する</router-link>
    </div>

    <p v-if="loading" class="muted">読み込み中…</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <template v-if="data">
      <p class="muted asof">基準日：{{ fmtDate(data.asOf) }}</p>

      <section v-if="data.nextGrantDate || (m5 && m5.target)" class="card highlight">
        <div v-if="m5 && m5.target" class="m5">
          <h3>年5日の年次有給休暇取得義務
            <span v-if="m5.achieved" class="badge badge-green">達成</span>
            <span v-else class="badge badge-orange">未達成</span>
          </h3>
          <p class="muted">対象期間：{{ fmtDate(m5.periodStart) }} 〜 {{ fmtDate(m5.periodEnd) }}</p>
          <div class="progress" role="progressbar" :aria-valuenow="m5Percent" aria-valuemin="0" aria-valuemax="100">
            <div class="bar" :class="{ done: m5.achieved }" :style="{ width: m5Percent + '%' }"></div>
          </div>
          <p class="m5-text">
            取得済 <strong>{{ fmtDays(m5.takenDays) }}</strong> / 必要 {{ fmtDays(m5.requiredDays) }}
            <span v-if="!m5.achieved" class="short">（あと {{ fmtDays(m5.shortageDays) }}）</span>
          </p>
        </div>
        <p v-if="data.nextGrantDate" class="next">
          次回付与予定日：<strong>{{ fmtDate(data.nextGrantDate) }}</strong>
        </p>
      </section>

      <section v-for="b in balances" :key="b.leaveTypeId" class="card">
        <div class="type-head">
          <h3>{{ b.leaveTypeName }}</h3>
          <span v-if="b.annual" class="badge badge-blue">年次有給休暇</span>
        </div>
        <div class="figures">
          <div><span class="lbl">付与</span><span class="val">{{ fmtDays(b.grantedDays) }}</span></div>
          <div><span class="lbl">使用</span><span class="val">{{ fmtDays(b.usedDays) }}</span></div>
          <div><span class="lbl">残</span><span class="val remain">{{ fmtDays(b.remainingDays) }}</span></div>
          <div><span class="lbl">失効日</span><span class="val small">{{ b.expiresOn ? fmtDate(b.expiresOn) : '—' }}</span></div>
        </div>
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>付与日</th><th>失効日</th><th class="num">付与</th><th class="num">使用</th><th class="num">残</th></tr>
            </thead>
            <tbody>
              <tr v-for="g in b.grants" :key="g.balanceId">
                <td>{{ fmtDate(g.grantedOn) }}</td>
                <td>{{ fmtDate(g.expiresOn) }}</td>
                <td class="num">{{ fmtDays(g.grantedDays) }}</td>
                <td class="num">{{ fmtDays(g.usedDays) }}</td>
                <td class="num">{{ fmtDays(g.remainingDays) }}</td>
              </tr>
              <tr v-if="!b.grants || !b.grants.length"><td colspan="5" class="empty">有効な付与はありません。</td></tr>
            </tbody>
          </table>
        </div>
      </section>

      <p v-if="!balances.length" class="card empty-card muted">表示できる休暇がありません。</p>
    </template>
  </AppLayout>
</template>

<style scoped>
.asof { margin: 0 0 12px; font-size: 13px; }
.highlight { border-color: var(--navy-700); background: #f5f8fd; }
.m5 h3 { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.m5 p { margin: 6px 0; }
.progress { height: 14px; background: #e5e7eb; border-radius: 7px; overflow: hidden; margin: 10px 0; }
.bar { height: 100%; background: #b45309; transition: width .3s; }
.bar.done { background: var(--ok); }
.m5-text strong { font-size: 20px; }
.short { color: #b45309; font-weight: 600; }
.next { margin: 12px 0 0; font-size: 16px; }
.type-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.type-head h3 { margin: 0; font-size: 16px; }
.figures { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.figures > div { padding: 10px 12px; background: #f8fafd; border-radius: 8px; }
.lbl { display: block; font-size: 12px; color: var(--muted); }
.val { font-size: 20px; font-weight: 700; font-variant-numeric: tabular-nums; }
.val.small { font-size: 16px; }
.val.remain { color: var(--navy-900); }
.empty-card { text-align: center; }
@media (max-width: 700px) { .figures { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
</style>
