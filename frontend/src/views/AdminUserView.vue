<script setup>
// ユーザー管理（管理者専用）
// 使用 API: GET /api/admin/users?keyword=&role=&status=&departmentId= / GET /api/admin/users/{id}
//           POST /api/admin/users / PUT /api/admin/users/{id} / PUT /api/admin/users/{id}/password
import { ref, reactive, computed, onMounted } from 'vue'
import AppLayout from '../components/AppLayout.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { api } from '../api/client'
import { fmtDate, ROLE, USER_STATUS } from '../utils/format'

const DEPARTMENTS = ref([]) // GET /api/departments から取得
async function loadDepartments() {
  try { DEPARTMENTS.value = (await api('GET', '/api/departments')).departments } catch (e) { /* 一覧が出ないだけなので握りつぶす */ }
}

const users = ref([])
const loading = ref(false)
const error = ref('')
const message = ref('')
const managers = ref([])

const search = reactive({ keyword: '', role: '', status: '', departmentId: '' })

// フォーム（null=非表示 / 'create' / 'edit'）
const formMode = ref(null)
const editingId = ref(null)
const form = reactive(blankForm())
const saving = ref(false)
const formError = ref('')

// パスワード再設定
const pwTarget = ref(null)
const pwValue = ref('')
const pwBusy = ref(false)
const pwError = ref('')

// 無効化確認
const confirmTarget = ref(null)
const confirmBusy = ref(false)
// 編集フォームで状態を INACTIVE に変えて保存するときの確認
const confirmSave = ref(false)

function blankForm() {
  return { name: '', email: '', role: 'EMPLOYEE', departmentId: '', managerId: '', hireDate: '', status: 'ACTIVE', initialPassword: '' }
}

const managerOptions = computed(() => managers.value.filter(m => m.id !== editingId.value))

function qs() {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(search)) if (v !== '' && v != null) p.set(k, v)
  const s = p.toString()
  return s ? `?${s}` : ''
}

async function load() {
  loading.value = true; error.value = ''
  try {
    const res = await api('GET', `/api/admin/users${qs()}`)
    users.value = res.users ?? []
  } catch (e) { error.value = e.message } finally { loading.value = false }
}

async function loadManagers() {
  try {
    const [m, a] = await Promise.all([
      api('GET', '/api/admin/users?role=MANAGER'),
      api('GET', '/api/admin/users?role=ADMIN')
    ])
    const all = [...(m.users ?? []), ...(a.users ?? [])].filter(u => u.status === 'ACTIVE')
    managers.value = all
  } catch (e) { error.value = e.message }
}

function resetSearch() {
  Object.assign(search, { keyword: '', role: '', status: '', departmentId: '' })
  load()
}

function openCreate() {
  Object.assign(form, blankForm())
  editingId.value = null; formError.value = ''; message.value = ''
  formMode.value = 'create'
}

async function openEdit(u) {
  formError.value = ''; message.value = ''; error.value = ''
  try {
    const res = await api('GET', `/api/admin/users/${u.id}`)
    const d = res.user
    Object.assign(form, {
      name: d.name, email: d.email, role: d.role,
      departmentId: d.departmentId ?? '', managerId: d.managerId ?? '',
      hireDate: d.hireDate ?? '', status: d.status, initialPassword: ''
    })
    editingId.value = d.id
    formMode.value = 'edit'
  } catch (e) { error.value = e.message }
}

function closeForm() { formMode.value = null; editingId.value = null; formError.value = '' }

function validate() {
  if (!form.name.trim()) return '氏名は必須入力です。'
  if (!form.email.trim()) return 'メールアドレスは必須入力です。'
  if (!form.hireDate) return '入社日は必須入力です。'
  if (formMode.value === 'create' && !form.initialPassword) return '初期パスワードは必須入力です。'
  return ''
}

function payload() {
  const base = {
    name: form.name.trim(),
    email: form.email.trim(),
    role: form.role,
    departmentId: form.departmentId === '' ? null : Number(form.departmentId),
    managerId: form.managerId === '' ? null : Number(form.managerId),
    hireDate: form.hireDate
  }
  if (formMode.value === 'create') return { ...base, initialPassword: form.initialPassword }
  return { ...base, status: form.status }
}

function submit() {
  formError.value = validate()
  if (formError.value) return
  if (formMode.value === 'edit' && form.status === 'INACTIVE') {
    const cur = users.value.find(u => u.id === editingId.value)
    if (!cur || cur.status !== 'INACTIVE') { confirmSave.value = true; return }
  }
  doSave()
}

async function doSave() {
  saving.value = true; formError.value = ''
  try {
    if (formMode.value === 'create') {
      await api('POST', '/api/admin/users', payload())
      message.value = `ユーザー「${form.name.trim()}」を登録しました。`
    } else {
      await api('PUT', `/api/admin/users/${editingId.value}`, payload())
      message.value = `ユーザー「${form.name.trim()}」を更新しました。`
    }
    confirmSave.value = false
    closeForm()
    await Promise.all([load(), loadManagers()])
  } catch (e) { formError.value = e.message; confirmSave.value = false } finally { saving.value = false }
}

function askDeactivate(u) { confirmTarget.value = u; message.value = ''; error.value = '' }

async function doDeactivate() {
  const u = confirmTarget.value
  confirmBusy.value = true
  try {
    await api('PUT', `/api/admin/users/${u.id}`, {
      name: u.name, email: u.email, role: u.role,
      departmentId: u.departmentId ?? null, managerId: u.managerId ?? null,
      hireDate: u.hireDate, status: 'INACTIVE'
    })
    message.value = `ユーザー「${u.name}」を無効化しました。`
    confirmTarget.value = null
    await Promise.all([load(), loadManagers()])
  } catch (e) { error.value = e.message; confirmTarget.value = null } finally { confirmBusy.value = false }
}

function openPw(u) { pwTarget.value = u; pwValue.value = ''; pwError.value = ''; message.value = '' }

async function doResetPw() {
  if (!pwValue.value) { pwError.value = '新しいパスワードは必須入力です。'; return }
  pwBusy.value = true; pwError.value = ''
  try {
    await api('PUT', `/api/admin/users/${pwTarget.value.id}/password`, { newPassword: pwValue.value })
    message.value = `ユーザー「${pwTarget.value.name}」のパスワードを再設定しました。`
    pwTarget.value = null
  } catch (e) { pwError.value = e.message } finally { pwBusy.value = false }
}

onMounted(() => { loadDepartments(); load(); loadManagers() })
</script>

<template>
  <AppLayout>
    <div class="page-head">
      <h2>ユーザー管理</h2>
      <button class="btn" @click="openCreate">新規登録</button>
    </div>

    <p v-if="message" class="alert alert-ok" role="status">{{ message }}</p>
    <p v-if="error" class="alert alert-error" role="alert">{{ error }}</p>

    <section v-if="formMode" class="card">
      <h3>{{ formMode === 'create' ? 'ユーザー新規登録' : 'ユーザー編集' }}</h3>
      <form @submit.prevent="submit">
        <div class="form-grid">
          <label class="field">氏名<span class="req">必須</span>
            <input v-model="form.name" type="text" maxlength="100" />
          </label>
          <label class="field">メールアドレス<span class="req">必須</span>
            <input v-model="form.email" type="email" maxlength="255" />
          </label>
          <label class="field">ロール<span class="req">必須</span>
            <select v-model="form.role">
              <option v-for="(label, key) in ROLE" :key="key" :value="key">{{ label }}</option>
            </select>
          </label>
          <label class="field">部署
            <select v-model="form.departmentId">
              <option value="">（未設定）</option>
              <option v-for="d in DEPARTMENTS" :key="d.id" :value="d.id">{{ d.name }}</option>
            </select>
          </label>
          <label class="field">上長
            <select v-model="form.managerId">
              <option value="">（未設定）</option>
              <option v-for="m in managerOptions" :key="m.id" :value="m.id">{{ m.name }}（{{ ROLE[m.role] }}）</option>
            </select>
          </label>
          <label class="field">入社日<span class="req">必須</span>
            <input v-model="form.hireDate" type="date" />
          </label>
          <label v-if="formMode === 'edit'" class="field">状態
            <select v-model="form.status">
              <option v-for="(label, key) in USER_STATUS" :key="key" :value="key">{{ label }}</option>
            </select>
          </label>
          <label v-else class="field">初期パスワード<span class="req">必須</span>
            <input v-model="form.initialPassword" type="text" autocomplete="off" maxlength="72" />
            <span class="hint">12〜72文字の半角英数字・記号（英字と数字を両方含める）。初回ログイン時に変更が必要です。</span>
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
      <form class="toolbar" @submit.prevent="load">
        <label class="field">キーワード
          <input v-model="search.keyword" type="text" placeholder="氏名・メール" />
        </label>
        <label class="field">ロール
          <select v-model="search.role">
            <option value="">すべて</option>
            <option v-for="(label, key) in ROLE" :key="key" :value="key">{{ label }}</option>
          </select>
        </label>
        <label class="field">状態
          <select v-model="search.status">
            <option value="">すべて</option>
            <option v-for="(label, key) in USER_STATUS" :key="key" :value="key">{{ label }}</option>
          </select>
        </label>
        <label class="field">部署
          <select v-model="search.departmentId">
            <option value="">すべて</option>
            <option v-for="d in DEPARTMENTS" :key="d.id" :value="d.id">{{ d.name }}</option>
          </select>
        </label>
        <button class="btn" type="submit" :disabled="loading">検索</button>
        <button class="btn btn-ghost" type="button" :disabled="loading" @click="resetSearch">クリア</button>
      </form>

      <p v-if="loading" class="muted">読み込み中…</p>
      <div class="table-wrap">
        <table class="table">
          <thead>
            <tr><th>氏名</th><th>メール</th><th>ロール</th><th>部署</th><th>上長</th><th>入社日</th><th>状態</th><th>操作</th></tr>
          </thead>
          <tbody>
            <tr v-for="u in users" :key="u.id">
              <td>{{ u.name }}</td>
              <td>{{ u.email }}</td>
              <td>{{ ROLE[u.role] ?? u.role }}</td>
              <td>{{ u.departmentName ?? '—' }}</td>
              <td>{{ u.managerName ?? '—' }}</td>
              <td>{{ fmtDate(u.hireDate) }}</td>
              <td><span class="badge" :class="u.status === 'ACTIVE' ? 'badge-green' : 'badge-gray'">{{ USER_STATUS[u.status] ?? u.status }}</span></td>
              <td>
                <div class="actions-row">
                  <button class="btn btn-ghost btn-sm" @click="openEdit(u)">編集</button>
                  <button class="btn btn-ghost btn-sm" @click="openPw(u)">パスワード再設定</button>
                  <button v-if="u.status === 'ACTIVE'" class="btn btn-danger btn-sm" @click="askDeactivate(u)">無効化</button>
                </div>
              </td>
            </tr>
            <tr v-if="!loading && !users.length"><td colspan="8" class="empty">該当するユーザーはいません。</td></tr>
          </tbody>
        </table>
      </div>
    </section>

    <div v-if="pwTarget" class="modal-back" @click.self="pwTarget = null">
      <form class="modal" role="dialog" aria-modal="true" @submit.prevent="doResetPw">
        <h3>パスワード再設定</h3>
        <p class="modal-msg">{{ pwTarget.name }}（{{ pwTarget.email }}）の新しいパスワードを入力してください。</p>
        <label class="field">新しいパスワード<span class="req">必須</span>
          <input v-model="pwValue" type="text" autocomplete="off" maxlength="72" />
          <span class="hint">12〜72文字の半角英数字・記号（英字と数字を両方含める）</span>
        </label>
        <p v-if="pwError" class="alert alert-error" role="alert" style="margin-top: 12px">{{ pwError }}</p>
        <div class="modal-actions">
          <button class="btn btn-ghost" type="button" :disabled="pwBusy" @click="pwTarget = null">キャンセル</button>
          <button class="btn" type="submit" :disabled="pwBusy">再設定する</button>
        </div>
      </form>
    </div>

    <ConfirmDialog
      v-if="confirmTarget"
      title="ユーザーの無効化"
      :message="`「${confirmTarget.name}」を無効化します。無効化したユーザーはログインできなくなります。よろしいですか？`"
      ok-label="無効化する"
      danger
      :busy="confirmBusy"
      @ok="doDeactivate"
      @cancel="confirmTarget = null"
    />
    <ConfirmDialog
      v-if="confirmSave"
      title="ユーザーの無効化"
      message="状態を「無効」にして保存します。無効化したユーザーはログインできなくなります。よろしいですか？"
      ok-label="保存する"
      danger
      :busy="saving"
      @ok="doSave"
      @cancel="confirmSave = false"
    />
  </AppLayout>
</template>
