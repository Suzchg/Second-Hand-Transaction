import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('./views/Login.vue') },
  { path: '/', component: () => import('./views/Home.vue') },
  { path: '/products/:id', component: () => import('./views/ProductDetail.vue') },
  { path: '/sell', component: () => import('./views/Sell.vue') },
  { path: '/my-products', component: () => import('./views/MyProducts.vue') },
  { path: '/my-orders', component: () => import('./views/MyOrders.vue') },
  { path: '/switch-account', component: () => import('./views/SwitchAccount.vue') },
  { path: '/orders/:id', component: () => import('./views/Order.vue') },
  { path: '/profile', component: () => import('./views/Profile.vue') },
  { path: '/profile/address/new', component: () => import('./views/AddressForm.vue') },
  { path: '/profile/address/:id/edit', component: () => import('./views/AddressForm.vue') },
  // Admin routes
  {
    path: '/admin',
    component: () => import('./views/admin/AdminLayout.vue'),
    meta: { requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', component: () => import('./views/admin/AdminDashboard.vue') },
      { path: 'users', component: () => import('./views/admin/AdminUsers.vue') },
      { path: 'products', component: () => import('./views/admin/AdminProducts.vue') },
      { path: 'orders', component: () => import('./views/admin/AdminOrders.vue') },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!token && to.path !== '/login') {
    return '/login'
  }
  // Admin guard
  if (to.meta.requiresAdmin) {
    const role = localStorage.getItem('role')
    if (role !== 'ADMIN') {
      return '/'
    }
  }
})

export default router
