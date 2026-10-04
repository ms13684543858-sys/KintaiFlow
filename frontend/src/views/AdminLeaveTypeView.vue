<script setup>
// 休暇種別管理（管理者専用）
// 使用 API: GET /api/admin/leave-types / POST /api/admin/leave-types / PUT /api/admin/leave-types/{id}
import { ref, reactive, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import { api } from '../api/client'

const RULE = { LIMITED: '上限あり', UNLIMITED: '上限なし' }

const types = ref([])
const loading = ref(false)
const error = ref('')
const message = ref('')

const formMode = ref(null)
const editingId = ref(null)
const form = reactive(blank())
const saving = ref(false)
const formError = ref('')

function blank() {
  return { name: '', legalBasis: '', isPaid: true, maxDaysRule: 'LIMITED', allowHalfDay: false, isActive: true }
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const res = await api('GET', '/api/admin/leave-types')
    types.value = res.leaveTypes ?? []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

function openCreate() {
  Object.assign(form, blank())
  editingId.value = null; formError.value = ''; message.value = ''
  formMode.value = 'create'
}

function openEdit(t) {
  Object.assign(form, {
    name: t.name, legalBasis: t.legalBasis ?? '', isPaid: !!t.isPaid,
    maxDaysRule: t.maxDaysRule, allowHalfDay: !!t.allowHalfDay, isActive: !!t.isActive
  })
  editingId.value = t.id; formError.value = ''; message.value = ''
  formMode.value = 'edit'
}

function closeForm() { formMode.value = null; editingId.value = null; formError.value = '' }

async function submit() {
  if (!form.name.trim()) { formError.value = '名称は必須入力です。'; return }
  saving.value = true; formError.value = ''
  const body = {
    name: form.name.trim(),
    legalBasis: form.legalBasis.trim() || null,
    isPaid: form.isPaid,
    maxDaysRule: form.maxDaysRule,
    allowHalfDay: form.allowHalfDay,
    isActive: form.isActive
  }
  try {
    if (formMode.value === 'create') {
      await api('POST', '/api/admin/leave-types', body)
      message.value = `休暇種別「${body.name}」を登録しました。`
    } else {
      await api('PUT', `/api/admin/leave-types/${editingId.value}`, body)
      message.value = `休暇種別「${body.name}」を更新しました。`
    }
    closeForm()
    await load()
  } catch (e) { formError.value = e.message } finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <AppLayout>
    <div class="page-head">
      <h2>休暇種別</h2>
      <button class="btn" @click="openCreate">新規登録</button>
    </div>

    <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <section v-if="formMode" class="card">
      <h3>{{ formMode === 'create' ? '休暇種別の新規登録' : '休暇種別の編集' }}</h3>
      <form @submit.prevent="submit">
        <div class="form-grid">
          <label class="field">名称<span class="req">必須</span>
            <input v-model="form.name" type="text" maxlength="100" />
          </label>
          <label class="field">法的根拠
            <input v-model="form.legalBasis" type="text" maxlength="100" placeholder="例：労働基準法第39条" />
          </label>
          <label class="field">有給・無給
            <select v-model="form.isPaid">
              <option :value="true">有給</option>
              <option :value="false">無給</option>
            </select>
          </label>
          <label class="field">日数ルール
            <select v-model="form.maxDaysRule">
              <option v-for="(label, key) in RULE" :key="key" :value="key">{{ label }}</option>
            </select>
          </label>
          <label class="field">半日取得
            <select v-model="form.allowHalfDay">
              <option :value="true">可</option>
              <option :value="false">不可</option>
            </select>
          </label>
          <label class="field">有効・無効
            <select v-model="form.isActive">
              <option :value="true">有効</option>
              <option :value="false">無効</option>
            </select>
          </label>
        </div>
        <p v-if="formError" class="alert alert-error" role="alert" style="margin-top: 16px">{{ formError }}</p>
        <div class="actions-row" style="margin-top: 16px">
          <button class="btn" type="submit" :disabled="saving">{{ formMode === 'create' ? '登録する' : '更新する' }}</button>
          <button class="btn btn-ghost" type="button" :disabled="saving" @click="closeForm">キャンセル</button>
        </div>
      </form>
    </section>

    <section class="card">
      <p v-if="loading" class="muted">読み込み中…</p>
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr><th>名称</th><th>法的根拠</th><th>有給・無給</th><th>日数ルール</th><th>半日取得</th><th>有効・無効</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="t in types" :key="t.id">
              <td>{{ t.name }}</td>
              <td>{{ t.legalBasis || '—' }}</td>
              <td>{{ t.isPaid ? '有給' : '無給' }}</td>
              <td>{{ RULE[t.maxDaysRule] ?? t.maxDaysRule }}</td>
              <td>{{ t.allowHalfDay ? '可' : '不可' }}</td>
              <td><span class="badge" :class="t.isActive ? 'badge-green' : 'badge-gray'">{{ t.isActive ? '有効' : '無効' }}</span></td>
              <td><button class="btn btn-ghost btn-sm" @click="openEdit(t)">編集</button></td>
            </tr>
            <tr v-if="!loading && !types.length"><td colspan="7" class="empty">休暇種別が登録されていません。</td></tr>
          </tbody>
        </table>
      </div>
    </section>
  </AppLayout>
</template>
