// 画面共通の表示用ヘルパー（日付・時間・各種ラベル）

/** 分 → "H:MM"（null は "—"） */
export const fmtMinutes = (m) => (m == null ? '—' : `${Math.floor(m / 60)}:${String(m % 60).padStart(2, '0')}`)

/** "2026-10-03T09:00:00+09:00" → "09:00" */
export const fmtHm = (iso) => (iso ? iso.substring(11, 16) : '—')

/** "2026-10-03" → "2026/10/03" */
export const fmtDate = (d) => (d ? String(d).substring(0, 10).replaceAll('-', '/') : '—')

/** "2026-10-03T09:00:00+09:00" → "2026/10/03 09:00" */
export const fmtDateTime = (iso) => (iso ? `${fmtDate(iso)} ${fmtHm(iso)}` : '—')

/** 日数（BigDecimal が 1 → "1.0"）→ "1.0日" */
export const fmtDays = (d) => (d == null ? '—' : `${Number(d).toFixed(1)}日`)

export const dowOf = (s) => '日月火水木金土'[new Date(String(s).substring(0, 10) + 'T00:00:00').getDay()]

export const ymOf = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
export const dateOf = (d) => `${ymOf(d)}-${String(d.getDate()).padStart(2, '0')}`

export const REQUEST_TYPE = { LEAVE: '休暇申請', CLOCK_CORRECTION: '打刻修正申請' }
export const UNIT = { FULL: '1日', AM: '午前半休', PM: '午後半休' }
export const ROLE = { EMPLOYEE: '社員', MANAGER: '上長', ADMIN: '管理者' }
export const USER_STATUS = { ACTIVE: '在籍', INACTIVE: '無効' }

/** 申請状態 → { label, tone }。tone は style.css の .badge-* に対応 */
export const REQUEST_STATUS = {
  DRAFT: { label: '下書き', tone: 'gray' },
  PENDING: { label: '承認待ち', tone: 'blue' },
  RETURNED: { label: '差戻し', tone: 'orange' },
  REJECTED: { label: '却下', tone: 'red' },
  APPROVED: { label: '承認済み', tone: 'green' },
  WITHDRAWN: { label: '取下げ', tone: 'gray' }
}

export const STEP_STATUS = {
  WAITING: { label: '承認待ち', tone: 'blue' },
  APPROVED: { label: '承認', tone: 'green' },
  RETURNED: { label: '差戻し', tone: 'orange' },
  REJECTED: { label: '却下', tone: 'red' }
}

/** 申請の期間表示（同日なら1日だけ） */
export function fmtPeriod(start, end) {
  if (!end || start === end) return fmtDate(start)
  return `${fmtDate(start)} 〜 ${fmtDate(end)}`
}
