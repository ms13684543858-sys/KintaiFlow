import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../api/client'

// 画面は遅延読み込み（初回表示を軽くする）。roles を指定した画面は、その権限が無ければ /home へ戻す。
const ALL = ['EMPLOYEE', 'MANAGER', 'ADMIN']
const APPROVER = ['MANAGER', 'ADMIN']
const route = (path, view, roles = ALL) => ({
  path, component: () => import(`../views/${view}.vue`), meta: { requiresAuth: true, roles }
})

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', component: () => import('../views/LoginView.vue') },
    route('/home', 'HomeView'),
    route('/password', 'ChangePasswordView'),
    route('/requests', 'RequestListView'),
    route('/requests/new', 'RequestFormView'),
    route('/requests/:id', 'RequestDetailView'),
    route('/leave', 'LeaveBalanceView'),
    route('/notifications', 'NotificationListView'),
    route('/approvals', 'ApprovalListView', APPROVER),
    route('/manager/summary', 'ManagerSummaryView', ['MANAGER']),
    route('/admin/reports', 'AdminReportView', ['ADMIN']),
    route('/admin/users', 'AdminUserView', ['ADMIN']),
    route('/admin/leave-grants', 'AdminLeaveGrantView', ['ADMIN']),
    route('/admin/leave-types', 'AdminLeaveTypeView', ['ADMIN']),
    route('/admin/holidays', 'AdminHolidayView', ['ADMIN']),
    { path: '/:pathMatch(.*)*', redirect: '/home' }
  ]
})

router.beforeEach((to) => {
  if (!to.meta.requiresAuth) return
  if (!auth.token()) return '/login'
  const user = auth.user()
  // 初期パスワードのユーザーは、パスワード変更が済むまで他の画面に入れない
  if (user?.mustChangePassword && to.path !== '/password') return '/password'
  if (!to.meta.roles.includes(user?.role)) return '/home'
})

export default router
