import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import ChangePasswordView from '../views/ChangePasswordView.vue'
import { auth } from '../api/client'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/login', component: LoginView },
    { path: '/home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/password', component: ChangePasswordView, meta: { requiresAuth: true } }
  ]
})

router.beforeEach((to) => {
  if (to.meta.requiresAuth && !auth.token()) return '/login'
  // 初期パスワードのユーザーは、パスワード変更が済むまで他の画面に入れない
  if (to.meta.requiresAuth && auth.user()?.mustChangePassword && to.path !== '/password') return '/password'
})

export default router
