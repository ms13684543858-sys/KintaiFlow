// API 呼び出し共通部品。JWT は localStorage に保存し、Authorization ヘッダーに付与する。
const TOKEN_KEY = 'kf_token'
const USER_KEY = 'kf_user'

export const auth = {
  save(res) {
    localStorage.setItem(TOKEN_KEY, res.token)
    localStorage.setItem(USER_KEY, JSON.stringify({
      userId: res.userId, name: res.name, role: res.role, mustChangePassword: res.mustChangePassword === true
    }))
  },
  markPasswordChanged() {
    const u = this.user()
    if (u) localStorage.setItem(USER_KEY, JSON.stringify({ ...u, mustChangePassword: false }))
  },
  clear() { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY) },
  token() { return localStorage.getItem(TOKEN_KEY) },
  user() { try { return JSON.parse(localStorage.getItem(USER_KEY)) } catch { return null } }
}

// 失敗時は { status, code, message } を throw する（code は E-001 等）
export async function api(method, path, body) {
  const headers = { 'Content-Type': 'application/json' }
  if (auth.token()) headers.Authorization = 'Bearer ' + auth.token()
  let res
  try {
    res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  } catch {
    throw { status: 0, code: 'E-012', message: 'サーバーに接続できません。' }
  }
  const text = await res.text()
  const data = text ? JSON.parse(text) : null
  if (!res.ok) {
    // ログイン済みなのに 401 = トークン期限切れ。ログイン画面へ戻す（ログイン API 自体の 401 は除く）
    if (res.status === 401 && auth.token() && path !== '/api/auth/login') {
      auth.clear()
      window.location.href = '/login'
    }
    throw { status: res.status, code: data?.code ?? 'E-012', message: data?.message ?? 'エラーが発生しました。' }
  }
  return data
}
